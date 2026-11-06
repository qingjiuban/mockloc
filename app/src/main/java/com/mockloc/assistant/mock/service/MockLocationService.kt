package com.mockloc.assistant.mock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mockloc.assistant.MainActivity
import com.mockloc.assistant.R
import com.mockloc.assistant.core.GeoUtils
import com.mockloc.assistant.data.model.AppSettings
import com.mockloc.assistant.data.model.MockPoint
import com.mockloc.assistant.data.prefs.AppRepository
import com.mockloc.assistant.mock.InjectionResult
import com.mockloc.assistant.mock.MockLocationEngine
import com.mockloc.assistant.mock.MockSessionState
import com.mockloc.assistant.mock.MockSessionStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 前台服务：保证锁屏、切后台、系统清理内存时仍持续注入模拟位置。
 *
 * 稳定性设计：
 * - START_STICKY + 启动时读取持久化会话，进程被杀后可恢复。
 * - 注入失败会累计失败次数，连续失败时自动降级并广播错误，不静默失效。
 * - 释放时严格移除测试 provider，避免系统定位残留。
 */
class MockLocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var engine: MockLocationEngine
    private lateinit var repository: AppRepository
    private var injectionJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var currentPoint: MockPoint? = null

    @Volatile
    private var settings: AppSettings = AppSettings()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        engine = MockLocationEngine(this)
        repository = AppRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMocking(userInitiated = true)
                return START_NOT_STICKY
            }
            else -> Unit
        }

        val latitude = intent?.getDoubleExtra(EXTRA_LATITUDE, Double.NaN) ?: Double.NaN
        val longitude = intent?.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN) ?: Double.NaN

        startForegroundSafely()

        if (GeoUtils.isValidPoint(latitude, longitude)) {
            val point = MockPoint(
                latitude = latitude,
                longitude = longitude,
                name = intent?.getStringExtra(EXTRA_NAME).orEmpty(),
                address = intent?.getStringExtra(EXTRA_ADDRESS).orEmpty(),
            )
            scope.launch { beginMocking(point) }
        } else {
            // 系统重启服务（intent 为空）时，尝试恢复上次会话
            scope.launch { restoreIfNeeded() }
        }

        return START_STICKY
    }

    private suspend fun restoreIfNeeded() {
        val snapshot = repository.session.first()
        val point = snapshot.point
        if (point == null) {
            publish(MockSessionState.Idle)
            stopSelfSafely()
            return
        }
        val s = repository.currentSettings()
        if (snapshot.wasRunning || s.autoResume) {
            beginMocking(point)
        } else {
            publish(MockSessionState.Idle)
            stopSelfSafely()
        }
    }

    private suspend fun beginMocking(point: MockPoint) {
        settings = repository.currentSettings()
        currentPoint = point
        repository.saveSession(point, running = true)

        val started = withContext(Dispatchers.Default) {
            engine.start(settings.injectionChannel)
        }
        if (!started) {
            publish(
                MockSessionState.Error(
                    "无法注册模拟位置通道。请确认已在开发者选项中将「选择模拟位置信息应用」设为本应用。",
                ),
            )
            MockSessionStateStore.publish(MockSessionState.Error("模拟位置未授权"))
            stopSelfSafely()
            return
        }

        acquireWakeLock()
        updateNotification(point)
        startInjectionLoop()
    }

    private fun startInjectionLoop() {
        injectionJob?.cancel()
        injectionJob = scope.launch {
            var consecutiveFailures = 0
            var lastNotifiedError: String? = null
            while (true) {
                val point = currentPoint
                if (point == null) {
                    delay(settings.intervalMillis)
                    continue
                }

                val result = withContext(Dispatchers.Default) {
                    runCatching {
                        engine.inject(
                            point = point,
                            accuracy = settings.accuracyMeters,
                            altitude = settings.altitudeMeters,
                            gcjCompensation = settings.gcjCompensation,
                        )
                    }.getOrElse { t -> InjectionResult.Failure(t.message ?: "注入异常", false) }
                }

                runCatching {
                    when (result) {
                        is InjectionResult.Success -> {
                            consecutiveFailures = 0
                            lastNotifiedError = null
                            publish(
                                MockSessionState.Running(
                                    point = point,
                                    injectedLatitude = result.latitude,
                                    injectedLongitude = result.longitude,
                                    verified = true,
                                    lastUpdateMillis = System.currentTimeMillis(),
                                ),
                            )
                        }
                        is InjectionResult.Failure -> {
                            consecutiveFailures++
                            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                                if (result.reason != lastNotifiedError) {
                                    lastNotifiedError = result.reason
                                    handleInjectionFailure(result.reason)
                                }
                            } else {
                                publish(
                                    MockSessionState.Running(
                                        point = point,
                                        injectedLatitude = point.latitude,
                                        injectedLongitude = point.longitude,
                                        verified = false,
                                        lastUpdateMillis = System.currentTimeMillis(),
                                    ),
                                )
                            }
                        }
                    }
                }

                try {
                    delay(settings.intervalMillis)
                } catch (c: kotlinx.coroutines.CancellationException) {
                    throw c
                }
            }
        }
    }

    private fun handleInjectionFailure(reason: String) {
        publish(MockSessionState.Error(reason))
        updateNotification(currentPoint, error = reason)
    }

    private fun publish(state: MockSessionState) {
        MockSessionStateStore.publish(state)
    }

    private fun startForegroundSafely() {
        val notification = buildNotification(currentPoint, error = null)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            // Android 12+ 后台启动前台服务受限时会走到这里，主动结束避免崩溃
            publish(MockSessionState.Error("系统限制后台启动服务，请打开应用后重试"))
            stopSelfSafely()
        }
    }

    private fun updateNotification(point: MockPoint?, error: String? = null) {
        val notification = buildNotification(point, error)
        runCatching {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(point: MockPoint?, error: String?): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MockLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = when {
            error != null -> "模拟定位异常"
            point != null -> "模拟定位进行中"
            else -> "定位助手"
        }

        val text = when {
            error != null -> error
            point != null -> buildString {
                append(point.displayName)
                if (point.latitude != 0.0 || point.longitude != 0.0) {
                    append(" · ")
                    append(GeoUtils.formatCoordinate(point.latitude, point.longitude, 4))
                }
            }
            else -> "点击打开应用设置虚拟位置"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent)
            .setOngoing(error == null)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.notification_action_stop), stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = runCatching {
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MockLoc:injection").apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT)
            }
        }.getOrNull()
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun stopMocking(userInitiated: Boolean) {
        injectionJob?.cancel()
        injectionJob = null
        engine.stop()
        releaseWakeLock()
        currentPoint = null
        publish(MockSessionState.Idle)
        scope.launch { repository.clearSession() }
        if (userInitiated) {
            stopSelfSafely()
        } else {
            stopSelfSafely()
        }
    }

    private fun stopSelfSafely() {
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    override fun onDestroy() {
        injectionJob?.cancel()
        runCatching { engine.stop() }
        releaseWakeLock()
        if (MockSessionStateStore.state.value is MockSessionState.Running) {
            MockSessionStateStore.publish(MockSessionState.Idle)
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 从最近任务划掉时，前台服务继续运行，只有显式停止才结束
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        const val ACTION_START = "com.mockloc.assistant.action.START"
        const val ACTION_STOP = "com.mockloc.assistant.action.STOP"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_NAME = "name"
        const val EXTRA_ADDRESS = "address"

        private const val CHANNEL_ID = "mock_location_status"
        private const val NOTIFICATION_ID = 2101
        private const val MAX_CONSECUTIVE_FAILURES = 3
        private const val WAKE_LOCK_TIMEOUT = 6 * 60 * 60 * 1000L

        fun start(context: Context, point: MockPoint, settings: AppSettings) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LATITUDE, point.latitude)
                putExtra(EXTRA_LONGITUDE, point.longitude)
                putExtra(EXTRA_NAME, point.name)
                putExtra(EXTRA_ADDRESS, point.address)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MockLocationService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }
}
