package com.mockloc.assistant.data.search

import com.mockloc.assistant.core.GeoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 地点搜索。使用 OpenStreetMap Nominatim 公共接口，无需 API Key。
 *
 * 设计要点：
 * - 网络不可用 / 请求失败时自动回退到内置离线城市库，保证搜索框永远有结果。
 * - 所有请求带超时与 UA，符合公共接口使用规范。
 * - 返回结果统一转换为 WGS-84 坐标。
 */
class PlaceSearchService(
    private val offlineIndex: OfflinePlaceIndex = OfflinePlaceIndex,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun search(query: String, limit: Int = 8): SearchOutcome = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext SearchOutcome.Success(emptyList(), fromOffline = false)

        val offlineResults = offlineIndex.search(trimmed, limit)

        val remote = runCatching { searchRemote(trimmed, limit) }.getOrNull()
        if (remote.isNullOrEmpty()) {
            return@withContext if (offlineResults.isEmpty()) {
                SearchOutcome.Failure("未找到匹配地点，请尝试输入更具体的城市或地址，或直接输入经纬度")
            } else {
                SearchOutcome.Success(offlineResults, fromOffline = true)
            }
        }

        // 合并离线与在线结果，按名称去重
        val merged = LinkedHashMap<String, PlaceResult>()
        for (item in remote) merged[item.dedupeKey()] = item
        for (item in offlineResults) merged.putIfAbsent(item.dedupeKey(), item)
        SearchOutcome.Success(merged.values.take(limit).toList(), fromOffline = false)
    }

    private fun searchRemote(query: String, limit: Int): List<PlaceResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL(
            "https://nominatim.openstreetmap.org/search?format=jsonv2&q=$encoded" +
                "&limit=$limit&addressdetails=1&accept-language=zh-CN,zh,en",
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 6_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MockLocAssistant/1.0 (Android; mock location testing tool)")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) return emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val items = runCatching { json.decodeFromString<List<NominatimItem>>(body) }.getOrNull()
                ?: return emptyList()
            return items.mapNotNull { item ->
                val lat = item.lat.toDoubleOrNull()
                val lon = item.lon.toDoubleOrNull()
                if (lat == null || lon == null || !GeoUtils.isValidPoint(lat, lon)) return@mapNotNull null
                PlaceResult(
                    name = item.name?.takeIf { it.isNotBlank() }
                        ?: item.displayName.substringBefore(','),
                    displayName = item.displayName,
                    latitude = lat,
                    longitude = lon,
                    source = PlaceSource.ONLINE,
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    @Serializable
    private data class NominatimItem(
        val lat: String = "",
        val lon: String = "",
        @SerialName("display_name") val displayName: String = "",
        val name: String? = null,
        val type: String? = null,
    )
}

enum class PlaceSource { ONLINE, OFFLINE }

data class PlaceResult(
    val name: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val source: PlaceSource,
) {
    fun dedupeKey(): String =
        "${name.lowercase()}|${"%.3f".format(java.util.Locale.US, latitude)}|${"%.3f".format(java.util.Locale.US, longitude)}"
}

sealed interface SearchOutcome {
    data class Success(val results: List<PlaceResult>, val fromOffline: Boolean) : SearchOutcome
    data class Failure(val message: String) : SearchOutcome
}
