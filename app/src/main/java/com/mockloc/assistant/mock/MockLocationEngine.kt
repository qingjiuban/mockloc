package com.mockloc.assistant.mock

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import com.mockloc.assistant.core.GeoUtils
import com.mockloc.assistant.data.model.InjectionChannel
import com.mockloc.assistant.data.model.MockPoint

/**
 * 基于 Android 官方 Test Provider 的模拟定位引擎。
 *
 * 工作方式：
 * 1. 调用 [LocationManager.addTestProvider] 注册同名测试 provider（gps / network / passive），
 *    覆盖系统中对应的真实 provider。
 * 2. 周期性调用 [LocationManager.setTestProviderLocation] 注入坐标。
 * 3. 通过 [LocationManager.getLastKnownLocation] 回读，确认系统真的接收了坐标
 *    （这是真正验证注入生效的依据，而不是只看界面）。
 *
 * 只有被系统「选择模拟位置信息应用」授权的应用才能成功调用这些 API。
 */
class MockLocationEngine(private val context: Context) {

    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val registeredProviders = LinkedHashSet<String>()

    @Volatile
    private var active = false

    val isActive: Boolean get() = active

    fun providersFor(channel: InjectionChannel): List<String> = when (channel) {
        InjectionChannel.GPS_ONLY -> listOf(LocationManager.GPS_PROVIDER)
        InjectionChannel.GPS_AND_NETWORK -> listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        )
        InjectionChannel.ALL -> listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
    }

    /**
     * 注册测试 provider。若系统未授权模拟位置，部分机型不会抛异常而是静默失败，
     * 因此调用方需要通过 [inject] 的回读结果确认。
     */
    fun start(channel: InjectionChannel): Boolean {
        val lm = locationManager ?: return false
        stop()
        for (provider in providersFor(channel)) {
            try {
                addProvider(lm, provider)
                registeredProviders += provider
                runCatching { lm.setTestProviderEnabled(provider, true) }
            } catch (t: Throwable) {
                // 记录失败但继续尝试其他 provider
                lastFailure = t
            }
        }
        active = registeredProviders.isNotEmpty()
        return active
    }

    private fun addProvider(lm: LocationManager, provider: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val properties = ProviderProperties.Builder()
                .setHasAltitudeSupport(true)
                .setHasSpeedSupport(true)
                .setHasBearingSupport(true)
                .setHasCellRequirement(false)
                .setHasMonetaryCost(false)
                .setHasNetworkRequirement(provider != LocationManager.GPS_PROVIDER)
                .setHasSatelliteRequirement(provider == LocationManager.GPS_PROVIDER)
                .setPowerUsage(
                    if (provider == LocationManager.GPS_PROVIDER) {
                        ProviderProperties.POWER_USAGE_HIGH
                    } else {
                        ProviderProperties.POWER_USAGE_LOW
                    },
                )
                .setAccuracy(
                    if (provider == LocationManager.GPS_PROVIDER) {
                        ProviderProperties.ACCURACY_FINE
                    } else {
                        ProviderProperties.ACCURACY_COARSE
                    },
                )
                .build()
            lm.addTestProvider(provider, properties, emptySet())
        } else {
            @Suppress("DEPRECATION")
            lm.addTestProvider(
                provider,
                provider != LocationManager.GPS_PROVIDER,
                provider == LocationManager.GPS_PROVIDER,
                false,
                false,
                true,
                true,
                true,
                1,
                if (provider == LocationManager.GPS_PROVIDER) 1 else 2,
            )
        }
    }

    /** 注入一次坐标，返回回读校验结果 */
    fun inject(point: MockPoint, accuracy: Float, altitude: Double, gcjCompensation: Boolean): InjectionResult {
        val lm = locationManager ?: return InjectionResult.Failure("设备不支持定位服务", verified = false)
        if (!active) return InjectionResult.Failure("模拟位置尚未初始化", verified = false)

        val (lat, lon) = if (gcjCompensation) {
            GeoUtils.wgs84ToGcj02(point.latitude, point.longitude)
        } else {
            point.latitude to point.longitude
        }

        var injected = 0
        var verifyError: String? = null
        for (provider in registeredProviders) {
            try {
                val location = buildLocation(provider, lat, lon, accuracy, altitude)
                lm.setTestProviderLocation(provider, location)
                injected++
            } catch (t: Throwable) {
                lastFailure = t
                verifyError = t.message
            }
        }

        if (injected == 0) {
            return InjectionResult.Failure(
                reason = verifyError ?: "系统拒绝了模拟位置，请确认已在开发者选项中选择本应用",
                verified = false,
            )
        }

        // 回读验证：以系统 provider 中保存的位置为准。
        // 注意：部分 OEM ROM 不会给注入的位置打 isMock 标记，因此距离一致即视为成功，
        // isMock 仅作为辅助判断，避免误报「授权失效」。
        val readBack = registeredProviders.firstNotNullOfOrNull { provider ->
            runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
        }

        if (readBack == null) {
            return InjectionResult.Failure(
                reason = "系统尚未返回模拟位置，可能未授权本应用",
                verified = false,
            )
        }

        val distance = GeoUtils.distanceMeters(lat, lon, readBack.latitude, readBack.longitude)
        // 距离容忍度放在 accuracy 与 5 米之间，兼容精度较差的回读
        val tolerance = maxOf(5.0, accuracy.toDouble() + 2.0)
        val positionMatches = distance <= tolerance

        return if (positionMatches) {
            InjectionResult.Success(readBack.latitude, readBack.longitude)
        } else if (!readBack.isMock) {
            InjectionResult.Failure(
                reason = "系统返回的是真实定位，模拟授权已失效，请重新在开发者选项中选择本应用",
                verified = false,
            )
        } else {
            InjectionResult.Failure(
                reason = "系统位置与目标不一致，可能被其他应用接管",
                verified = false,
            )
        }
    }

    private fun buildLocation(
        provider: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        altitude: Double,
    ): Location = Location(provider).apply {
        this.latitude = latitude
        this.longitude = longitude
        this.accuracy = accuracy.coerceAtLeast(0.1f)
        this.altitude = altitude
        this.time = System.currentTimeMillis()
        this.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        this.speed = 0f
        this.bearing = 0f
        this.speedAccuracyMetersPerSecond = 0.1f
        this.bearingAccuracyDegrees = 1f
        @Suppress("DEPRECATION")
        runCatching { this.setMock(true) }
    }

    fun stop() {
        val lm = locationManager ?: return
        for (provider in registeredProviders.toList()) {
            runCatching { lm.setTestProviderEnabled(provider, false) }
            runCatching { lm.removeTestProvider(provider) }
        }
        registeredProviders.clear()
        active = false
    }

    var lastFailure: Throwable? = null
        private set
}

sealed interface InjectionResult {
    data class Success(val latitude: Double, val longitude: Double) : InjectionResult
    data class Failure(val reason: String, val verified: Boolean) : InjectionResult
}
