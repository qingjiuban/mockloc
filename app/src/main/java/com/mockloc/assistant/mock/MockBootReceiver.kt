package com.mockloc.assistant.mock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mockloc.assistant.data.prefs.AppRepository
import com.mockloc.assistant.mock.service.MockLocationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机或应用更新后恢复模拟会话。
 * Android 12+ 后台启动前台服务受限，因此这里只做「提示 + 由用户点击恢复」，
 * 绝不强行拉起服务，避免系统抛 ForegroundServiceStartNotAllowedException。
 */
class MockBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = AppRepository(appContext)
                val settings = repository.currentSettings()
                val snapshot = repository.session.first()
                if (settings.autoResume && snapshot.wasRunning && snapshot.point != null) {
                    // 弹通知引导用户打开应用恢复，而不是后台直接启动服务
                    RestoreNotifier.show(appContext, snapshot.point)
                }
            } catch (_: Throwable) {
                // 恢复失败不影响开机流程
            } finally {
                pending.finish()
            }
        }
    }
}
