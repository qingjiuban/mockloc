package com.mockloc.assistant.data.model

import kotlinx.serialization.Serializable

@Serializable
data class HistoryEntry(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val address: String = "",
    val usedAt: Long = 0L,
) {
    fun toMockPoint(accuracy: Float = 5f, altitude: Double = 0.0): MockPoint = MockPoint(
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        accuracy = accuracy,
        name = name,
        address = address,
    )
}
