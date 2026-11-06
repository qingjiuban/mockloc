package com.mockloc.assistant.data.map

import android.content.Context
import android.util.Log
import com.mockloc.assistant.data.model.MapStyle
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView

/**
 * 无需 API Key 的地图瓦片源。
 *
 * 选择理由：
 * - OpenStreetMap 官方瓦片：全球可用，无需密钥，使用时有 UA 与频率要求。
 * - CARTO 底图：提供浅色/深色两套低饱和度样式，无需密钥，适合作为科技感 UI 底图。
 * 两者都遵循各自的公开使用政策；应用内保留版权标识。
 *
 * 国内网络环境若无法访问，osmdroid 会退回本地瓦片缓存，界面显示「离线」提示，
 * 用户仍可通过经纬度输入与搜索（离线城市库）完成选点。
 */
object MapTileSources {

    private const val USER_AGENT_SUFFIX = "MockLocAssistant/1.0 (Android)"

    val OSM_STANDARD: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "OSM Standard",
        0,
        19,
        256,
        ".png",
        arrayOf(
            "https://tile.openstreetmap.org/",
            "https://a.tile.openstreetmap.org/",
            "https://b.tile.openstreetmap.org/",
            "https://c.tile.openstreetmap.org/",
        ),
        "© OpenStreetMap contributors",
        TileSourcePolicy(2, TileSourcePolicy.FLAG_NO_BULK),
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "${baseUrl}$zoom/$x/$y.png"
        }
    }

    val CARTO_LIGHT: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "Carto Light",
        0,
        19,
        256,
        ".png",
        arrayOf("https://a.basemaps.cartocdn.com/light_all/", "https://b.basemaps.cartocdn.com/light_all/"),
        "© OpenStreetMap contributors © CARTO",
        TileSourcePolicy(2, TileSourcePolicy.FLAG_NO_BULK),
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "${baseUrl}$zoom/$x/$y.png"
        }
    }

    val CARTO_DARK: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "Carto Dark",
        0,
        19,
        256,
        ".png",
        arrayOf("https://a.basemaps.cartocdn.com/dark_all/", "https://b.basemaps.cartocdn.com/dark_all/"),
        "© OpenStreetMap contributors © CARTO",
        TileSourcePolicy(2, TileSourcePolicy.FLAG_NO_BULK),
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "${baseUrl}$zoom/$x/$y.png"
        }
    }

    fun forStyle(style: MapStyle): OnlineTileSourceBase = when (style) {
        MapStyle.STANDARD -> OSM_STANDARD
        MapStyle.LIGHT -> CARTO_LIGHT
        MapStyle.DARK -> CARTO_DARK
    }

    fun baseUrlForStyle(style: MapStyle): String = when (style) {
        MapStyle.STANDARD -> "https://tile.openstreetmap.org/"
        MapStyle.LIGHT -> "https://a.basemaps.cartocdn.com/light_all/"
        MapStyle.DARK -> "https://a.basemaps.cartocdn.com/dark_all/"
    }

    /** 全局初始化，必须在使用 MapView 之前调用 */
    fun initialize(context: Context) {
        runCatching {
            val config = Configuration.getInstance()
            config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            config.userAgentValue = context.packageName + USER_AGENT_SUFFIX
            config.setMapTileDownloaderFollowRedirects(true)
            config.tileDownloadThreads = 4.toShort()
        }.onFailure { Log.w("MapTileSources", "osmdroid init failed", it) }
    }

    fun configureMapView(mapView: MapView, style: MapStyle) {
        mapView.setTileSource(forStyle(style))
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(false)
        mapView.setTilesScaledToDpi(true)
        mapView.setUseDataConnection(true)
        mapView.setHorizontalMapRepetitionEnabled(false)
        mapView.setVerticalMapRepetitionEnabled(false)
        mapView.setMinZoomLevel(3.0)
        mapView.setMaxZoomLevel(19.0)
    }
}
