package com.mockloc.assistant.data.map

import android.content.Context
import android.util.Log
import com.mockloc.assistant.data.model.MapStyle
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import java.io.File

object MapTileSources {

    private const val TAG = "MapTileSources"
    private const val USER_AGENT = "MockLocAssistant/1.0"

    val OSM_DE: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "OSM DE Mirror",
        0, 19, 256, ".png",
        arrayOf(
            "https://tile.openstreetmap.de/",
        ),
        "© OpenStreetMap contributors",
        TileSourcePolicy(2, TileSourcePolicy.FLAG_NO_BULK),
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            return "${baseUrl}${MapTileIndex.getZoom(pMapTileIndex)}/${MapTileIndex.getX(pMapTileIndex)}/${MapTileIndex.getY(pMapTileIndex)}.png"
        }
    }

    val OSM_STANDARD: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "OSM Standard",
        0, 19, 256, ".png",
        arrayOf(
            "https://a.tile.openstreetmap.org/",
            "https://b.tile.openstreetmap.org/",
            "https://c.tile.openstreetmap.org/",
        ),
        "© OpenStreetMap contributors",
        TileSourcePolicy(2, TileSourcePolicy.FLAG_NO_BULK),
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            return "${baseUrl}${MapTileIndex.getZoom(pMapTileIndex)}/${MapTileIndex.getX(pMapTileIndex)}/${MapTileIndex.getY(pMapTileIndex)}.png"
        }
    }

    val OSM_FR: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "OSM FR Mirror",
        0, 19, 256, ".png",
        arrayOf(
            "https://a.tile.openstreetmap.fr/osmfr/",
            "https://b.tile.openstreetmap.fr/osmfr/",
            "https://c.tile.openstreetmap.fr/osmfr/",
        ),
        "© OpenStreetMap contributors",
        TileSourcePolicy(2, TileSourcePolicy.FLAG_NO_BULK),
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            return "${baseUrl}${MapTileIndex.getZoom(pMapTileIndex)}/${MapTileIndex.getX(pMapTileIndex)}/${MapTileIndex.getY(pMapTileIndex)}.png"
        }
    }

    val CARTO_LIGHT: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "Carto Light", 0, 19, 256, ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/light_all/",
            "https://b.basemaps.cartocdn.com/light_all/",
            "https://c.basemaps.cartocdn.com/light_all/",
            "https://d.basemaps.cartocdn.com/light_all/",
        ),
        "© OpenStreetMap contributors © CARTO",
        TileSourcePolicy(2, TileSourcePolicy.FLAG_NO_BULK),
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            return "${baseUrl}${MapTileIndex.getZoom(pMapTileIndex)}/${MapTileIndex.getX(pMapTileIndex)}/${MapTileIndex.getY(pMapTileIndex)}.png"
        }
    }

    val CARTO_DARK: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "Carto Dark", 0, 19, 256, ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/dark_all/",
            "https://b.basemaps.cartocdn.com/dark_all/",
            "https://c.basemaps.cartocdn.com/dark_all/",
            "https://d.basemaps.cartocdn.com/dark_all/",
        ),
        "© OpenStreetMap contributors © CARTO",
        TileSourcePolicy(2, TileSourcePolicy.FLAG_NO_BULK),
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            return "${baseUrl}${MapTileIndex.getZoom(pMapTileIndex)}/${MapTileIndex.getX(pMapTileIndex)}/${MapTileIndex.getY(pMapTileIndex)}.png"
        }
    }

    fun forStyle(style: MapStyle): OnlineTileSourceBase = when (style) {
        MapStyle.STANDARD -> CARTO_LIGHT
        MapStyle.LIGHT -> CARTO_LIGHT
        MapStyle.DARK -> CARTO_DARK
    }

    fun allSources(): List<OnlineTileSourceBase> = listOf(
        CARTO_LIGHT, CARTO_DARK, OSM_DE, OSM_STANDARD, OSM_FR,
    )

    fun initialize(context: Context) {
        try {
            val prefs = context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
            val config = Configuration.getInstance()
            config.load(context, prefs)

            config.userAgentValue = USER_AGENT
            config.setMapTileDownloaderFollowRedirects(true)
            config.tileDownloadThreads = 4.toShort()

            val tileCacheDir = File(context.cacheDir, "osmdroid_tiles")
            if (!tileCacheDir.exists()) tileCacheDir.mkdirs()
            config.osmdroidTileCache = tileCacheDir

            val tileArchiveDir = File(context.filesDir, "osmdroid_archive")
            if (!tileArchiveDir.exists()) tileArchiveDir.mkdirs()
            config.osmdroidBasePath = tileArchiveDir

            Log.i(TAG, "osmdroid initialized: UA=$USER_AGENT, cache=${tileCacheDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "osmdroid init failed", e)
        }
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
