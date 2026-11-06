package com.mockloc.assistant.data.model

import kotlinx.serialization.Serializable

/** 选中的目标位置。坐标为 WGS-84。 */
@Serializable
data class MockPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 5f,
    val name: String = "",
    val address: String = "",
) {
    val hasLabel: Boolean get() = name.isNotBlank()

    val displayName: String
        get() = when {
            name.isNotBlank() -> name
            address.isNotBlank() -> address
            else -> "未命名位置"
        }
}

@Serializable
data class SavedPlace(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val address: String = "",
    val createdAt: Long = 0L,
)

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class MapStyle { STANDARD, LIGHT, DARK }

@Serializable
enum class InjectionChannel { GPS_AND_NETWORK, GPS_ONLY, ALL }

data class AppSettings(
    val intervalMillis: Long = 1_000L,
    val accuracyMeters: Float = 5f,
    val altitudeMeters: Double = 0.0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val mapStyle: MapStyle = MapStyle.STANDARD,
    val injectionChannel: InjectionChannel = InjectionChannel.GPS_AND_NETWORK,
    val autoResume: Boolean = true,
    val gcjCompensation: Boolean = false,
    val guideCompleted: Boolean = false,
)

/** 一次模拟会话的持久化快照，用于崩溃/重启恢复 */
data class SessionSnapshot(
    val point: MockPoint? = null,
    val wasRunning: Boolean = false,
    val updatedAt: Long = 0L,
)

enum class Prerequisite {
    LOCATION_PERMISSION,
    DEVELOPER_OPTIONS,
    MOCK_APP_SELECTED,
}

data class PrerequisiteState(
    val locationPermission: Boolean = false,
    val developerOptions: Boolean = false,
    val mockAppSelected: Boolean = false,
    val mockSelectionUnknown: Boolean = false,
    val batteryOptimized: Boolean = false,
) {
    val allSatisfied: Boolean
        get() = locationPermission && developerOptions && (mockAppSelected || mockSelectionUnknown)

    val missing: List<Prerequisite>
        get() = buildList {
            if (!locationPermission) add(Prerequisite.LOCATION_PERMISSION)
            if (!developerOptions) add(Prerequisite.DEVELOPER_OPTIONS)
            if (!mockAppSelected && !mockSelectionUnknown) add(Prerequisite.MOCK_APP_SELECTED)
        }
}
