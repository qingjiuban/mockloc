package com.mockloc.assistant.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 纯 Kotlin 地理计算工具，无 Android 依赖，便于单元测试。
 */
object GeoUtils {

    const val EARTH_RADIUS_METERS = 6_371_008.8

    // WGS-84 中国范围粗略外包框，用于判断是否需要 GCJ-02 偏移
    private const val CN_MIN_LAT = 0.8293
    private const val CN_MAX_LAT = 55.8271
    private const val CN_MIN_LON = 72.004
    private const val CN_MAX_LON = 137.8347

    private const val GCJ_A = 6378245.0
    private const val GCJ_EE = 0.00669342162296594323

    fun isValidLatitude(latitude: Double): Boolean =
        !latitude.isNaN() && !latitude.isInfinite() && latitude in -90.0..90.0

    fun isValidLongitude(longitude: Double): Boolean =
        !longitude.isNaN() && !longitude.isInfinite() && longitude in -180.0..180.0

    fun isValidPoint(latitude: Double, longitude: Double): Boolean =
        isValidLatitude(latitude) && isValidLongitude(longitude)

    /** 经纬度格式化：6 位小数约等于 0.1 米精度 */
    fun formatLatitude(latitude: Double, decimals: Int = 6): String =
        String.format(java.util.Locale.US, "%.${decimals}f°", latitude)

    fun formatLongitude(longitude: Double, decimals: Int = 6): String =
        String.format(java.util.Locale.US, "%.${decimals}f°", longitude)

    fun formatCoordinate(latitude: Double, longitude: Double, decimals: Int = 6): String =
        String.format(java.util.Locale.US, "%.${decimals}f, %.${decimals}f", latitude, longitude)

    fun formatCoordinatePair(latitude: Double, longitude: Double, decimals: Int = 6): String =
        "${formatLatitude(latitude, decimals)}, ${formatLongitude(longitude, decimals)}"

    fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }

    /**
     * 解析用户输入的坐标。支持：
     * 39.9042,116.4074 / 39.9042 116.4074 / 39.9042，116.4074
     * geo:39.9042,116.4074 / (39.9042, 116.4074)
     */
    fun parseCoordinateInput(raw: String): CoordinateParseResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return CoordinateParseResult.Empty

        val cleaned = trimmed
            .removePrefix("geo:")
            .removePrefix("GEO:")
            .trim()
            .removeSurrounding("(", ")")
            .replace('，', ',')
            .replace('；', ',')
            .replace(';', ',')
            .replace('、', ',')

        val parts = cleaned.split(Regex("[,\\s]+")).filter { it.isNotBlank() }
        if (parts.size < 2) return CoordinateParseResult.Invalid("请输入纬度和经度两个数值")

        val latitude = parts[0].toDoubleOrNull()
            ?: return CoordinateParseResult.Invalid("纬度格式不正确")
        val longitude = parts[1].toDoubleOrNull()
            ?: return CoordinateParseResult.Invalid("经度格式不正确")

        if (!isValidLatitude(latitude)) return CoordinateParseResult.Invalid("纬度需在 -90 ~ 90 之间")
        if (!isValidLongitude(longitude)) return CoordinateParseResult.Invalid("经度需在 -180 ~ 180 之间")

        return CoordinateParseResult.Success(latitude, longitude)
    }

    /** 是否位于 GCJ-02 适用范围内（中国大陆粗略范围） */
    fun isInChina(latitude: Double, longitude: Double): Boolean =
        latitude in CN_MIN_LAT..CN_MAX_LAT && longitude in CN_MIN_LON..CN_MAX_LON

    /**
     * WGS-84 -> GCJ-02。国内地图（高德、腾讯等）会对 WGS-84 坐标做偏移，
     * 开启兼容后注入偏移坐标，使这些地图上显示的位置与选点一致。
     */
    fun wgs84ToGcj02(latitude: Double, longitude: Double): Pair<Double, Double> {
        if (!isInChina(latitude, longitude)) return latitude to longitude
        var dLat = transformLat(longitude - 105.0, latitude - 35.0)
        var dLon = transformLon(longitude - 105.0, latitude - 35.0)
        val radLat = Math.toRadians(latitude)
        var magic = sin(radLat)
        magic = 1 - GCJ_EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((GCJ_A * (1 - GCJ_EE)) / (magic * sqrtMagic) * Math.PI)
        dLon = (dLon * 180.0) / (GCJ_A / sqrtMagic * cos(radLat) * Math.PI)
        return latitude + dLat to longitude + dLon
    }

    /** GCJ-02 -> WGS-84，使用迭代逼近，误差小于 1 米 */
    fun gcj02ToWgs84(latitude: Double, longitude: Double): Pair<Double, Double> {
        if (!isInChina(latitude, longitude)) return latitude to longitude
        var lat = latitude
        var lon = longitude
        repeat(8) {
            val (gcjLat, gcjLon) = wgs84ToGcj02(lat, lon)
            val dLat = gcjLat - latitude
            val dLon = gcjLon - longitude
            if (kotlin.math.abs(dLat) < 1e-9 && kotlin.math.abs(dLon) < 1e-9) return lat to lon
            lat -= dLat
            lon -= dLon
        }
        return lat to lon
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(kotlin.math.abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * Math.PI) + 40.0 * sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * Math.PI) + 320 * sin(y * Math.PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(kotlin.math.abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * Math.PI) + 40.0 * sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * Math.PI) + 300.0 * sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
        return ret
    }
}

sealed interface CoordinateParseResult {
    data class Success(val latitude: Double, val longitude: Double) : CoordinateParseResult
    data class Invalid(val message: String) : CoordinateParseResult
    data object Empty : CoordinateParseResult
}
