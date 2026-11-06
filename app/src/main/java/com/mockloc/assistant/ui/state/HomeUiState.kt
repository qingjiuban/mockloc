package com.mockloc.assistant.ui.state

import android.content.Context
import com.mockloc.assistant.data.model.AppSettings
import com.mockloc.assistant.data.model.MockPoint
import com.mockloc.assistant.data.model.PrerequisiteState
import com.mockloc.assistant.mock.MockSessionState
import com.mockloc.assistant.mock.MockSessionStateStore

/**
 * 首页所需的所有展示状态，派生自持久层 + 服务运行状态 + 实时前置条件。
 */
data class HomeUiState(
    val point: MockPoint? = null,
    val session: MockSessionState = MockSessionState.Idle,
    val prerequisites: PrerequisiteState = PrerequisiteState(),
    val settings: AppSettings = AppSettings(),
    val realLocation: RealLocationUi? = null,
    val loadingRealLocation: Boolean = false,
    val realLocationError: String? = null,
    val transientMessage: String? = null,
    val starting: Boolean = false,
) {
    val running: Boolean get() = session is MockSessionState.Running
    val error: String? get() = (session as? MockSessionState.Error)?.reason
    val canStart: Boolean get() = point != null && !running && !starting
}

data class RealLocationUi(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val provider: String,
    val timestamp: Long,
)

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
}

/** 读取系统当前真实位置（仅在已授权时） */
object RealLocationReader {

    @Suppress("MissingPermission")
    fun lastKnown(context: Context): RealLocationUi? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            ?: return null
        val location = runCatching { lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) }
            .getOrNull()
            ?: runCatching { lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER) }.getOrNull()
            ?: return null
        return RealLocationUi(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            provider = location.provider ?: "unknown",
            timestamp = location.time,
        )
    }
}
