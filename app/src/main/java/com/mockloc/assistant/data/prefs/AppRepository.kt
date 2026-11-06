package com.mockloc.assistant.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mockloc.assistant.data.model.AppSettings
import com.mockloc.assistant.data.model.HistoryEntry
import com.mockloc.assistant.data.model.InjectionChannel
import com.mockloc.assistant.data.model.MapStyle
import com.mockloc.assistant.data.model.MockPoint
import com.mockloc.assistant.data.model.SavedPlace
import com.mockloc.assistant.data.model.SessionSnapshot
import com.mockloc.assistant.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mockloc_prefs")

/**
 * 全局持久化。使用 DataStore 保存设置、常用地点、历史记录与最近会话快照。
 * 所有读取都做了异常兜底，避免损坏数据导致启动崩溃。
 */
class AppRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val interval = longPreferencesKey("interval_ms")
        val accuracy = floatPreferencesKey("accuracy_m")
        val altitude = floatPreferencesKey("altitude_m")
        val theme = stringPreferencesKey("theme_mode")
        val mapStyle = stringPreferencesKey("map_style")
        val channel = stringPreferencesKey("injection_channel")
        val autoResume = booleanPreferencesKey("auto_resume")
        val keepAlive = booleanPreferencesKey("keep_alive")
        val gcj = booleanPreferencesKey("gcj_compensation")
        val guideDone = booleanPreferencesKey("guide_completed")
        val places = stringPreferencesKey("saved_places")
        val history = stringPreferencesKey("history")
        val lastPoint = stringPreferencesKey("last_point")
        val session = stringPreferencesKey("session")
        val launchCount = intPreferencesKey("launch_count")
    }

    private fun <T> Flow<T>.safe(default: T): Flow<T> = catch { emit(default) }

    val settings: Flow<AppSettings> = store.data
        .safe(androidx.datastore.preferences.core.emptyPreferences())
        .map { p ->
            AppSettings(
                intervalMillis = (p[Keys.interval] ?: 1_000L).coerceIn(MIN_INTERVAL, MAX_INTERVAL),
                accuracyMeters = (p[Keys.accuracy] ?: 5f).coerceIn(1f, 100f),
                altitudeMeters = (p[Keys.altitude] ?: 0f).toDouble(),
                themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
                mapStyle = p[Keys.mapStyle]?.let { runCatching { MapStyle.valueOf(it) }.getOrNull() } ?: MapStyle.STANDARD,
                injectionChannel = p[Keys.channel]?.let { runCatching { InjectionChannel.valueOf(it) }.getOrNull() }
                    ?: InjectionChannel.GPS_AND_NETWORK,
                autoResume = p[Keys.autoResume] ?: true,
                gcjCompensation = p[Keys.gcj] ?: false,
                guideCompleted = p[Keys.guideDone] ?: false,
            )
        }

    val keepAlive: Flow<Boolean> = store.data
        .safe(androidx.datastore.preferences.core.emptyPreferences())
        .map { it[Keys.keepAlive] ?: true }

    val savedPlaces: Flow<List<SavedPlace>> = store.data
        .safe(androidx.datastore.preferences.core.emptyPreferences())
        .map { decodeList(it[Keys.places]) }

    val history: Flow<List<HistoryEntry>> = store.data
        .safe(androidx.datastore.preferences.core.emptyPreferences())
        .map { decodeList(it[Keys.history]) }

    val lastPoint: Flow<MockPoint?> = store.data
        .safe(androidx.datastore.preferences.core.emptyPreferences())
        .map { p -> p[Keys.lastPoint]?.let { decode(it) } }

    val session: Flow<SessionSnapshot> = store.data
        .safe(androidx.datastore.preferences.core.emptyPreferences())
        .map { p ->
            val snapshot = p[Keys.session]?.let { decode<SessionSnapshot>(it) }
            snapshot ?: SessionSnapshot(
                point = p[Keys.lastPoint]?.let { decode<MockPoint>(it) },
                wasRunning = false,
            )
        }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val current = currentSettings()
        val next = transform(current)
        writeSettings(next)
    }

    private suspend fun writeSettings(s: AppSettings) {
        store.edit { p ->
            p[Keys.interval] = s.intervalMillis
            p[Keys.accuracy] = s.accuracyMeters
            p[Keys.altitude] = s.altitudeMeters.toFloat()
            p[Keys.theme] = s.themeMode.name
            p[Keys.mapStyle] = s.mapStyle.name
            p[Keys.channel] = s.injectionChannel.name
            p[Keys.autoResume] = s.autoResume
            p[Keys.gcj] = s.gcjCompensation
            p[Keys.guideDone] = s.guideCompleted
        }
    }

    suspend fun currentSettings(): AppSettings {
        var result = AppSettings()
        store.data.safe(androidx.datastore.preferences.core.emptyPreferences())
            .map { p ->
                AppSettings(
                    intervalMillis = (p[Keys.interval] ?: 1_000L).coerceIn(MIN_INTERVAL, MAX_INTERVAL),
                    accuracyMeters = (p[Keys.accuracy] ?: 5f).coerceIn(1f, 100f),
                    altitudeMeters = (p[Keys.altitude] ?: 0f).toDouble(),
                    themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
                    mapStyle = p[Keys.mapStyle]?.let { runCatching { MapStyle.valueOf(it) }.getOrNull() } ?: MapStyle.STANDARD,
                    injectionChannel = p[Keys.channel]?.let { runCatching { InjectionChannel.valueOf(it) }.getOrNull() }
                        ?: InjectionChannel.GPS_AND_NETWORK,
                    autoResume = p[Keys.autoResume] ?: true,
                    gcjCompensation = p[Keys.gcj] ?: false,
                    guideCompleted = p[Keys.guideDone] ?: false,
                )
            }
            .collect { result = it }
        return result
    }

    suspend fun setKeepAlive(enabled: Boolean) = store.edit { it[Keys.keepAlive] = enabled }

    suspend fun setLastPoint(point: MockPoint) = store.edit {
        it[Keys.lastPoint] = json.encodeToString(point)
        it[Keys.session] = json.encodeToString(SessionSnapshot(point = point, wasRunning = false, updatedAt = System.currentTimeMillis()))
    }

    suspend fun saveSession(point: MockPoint, running: Boolean) = store.edit {
        it[Keys.lastPoint] = json.encodeToString(point)
        it[Keys.session] = json.encodeToString(
            SessionSnapshot(point = point, wasRunning = running, updatedAt = System.currentTimeMillis()),
        )
    }

    suspend fun clearSession() = store.edit {
        it[Keys.session] = json.encodeToString(SessionSnapshot(point = null, wasRunning = false))
    }

    suspend fun addPlace(place: SavedPlace) = store.edit { p ->
        val list = decodeList<SavedPlace>(p[Keys.places]).toMutableList()
        val existing = list.indexOfFirst {
            kotlin.math.abs(it.latitude - place.latitude) < 1e-7 &&
                kotlin.math.abs(it.longitude - place.longitude) < 1e-7
        }
        if (existing >= 0) list.removeAt(existing)
        list.add(0, place)
        p[Keys.places] = json.encodeToString(list.take(MAX_PLACES))
    }

    suspend fun removePlace(id: String) = store.edit { p ->
        val list = decodeList<SavedPlace>(p[Keys.places]).filterNot { it.id == id }
        p[Keys.places] = json.encodeToString(list)
    }

    suspend fun renamePlace(id: String, name: String) = store.edit { p ->
        val list = decodeList<SavedPlace>(p[Keys.places]).map {
            if (it.id == id) it.copy(name = name) else it
        }
        p[Keys.places] = json.encodeToString(list)
    }

    suspend fun addHistory(entry: HistoryEntry) = store.edit { p ->
        val list = decodeList<HistoryEntry>(p[Keys.history]).toMutableList()
        list.removeAll {
            kotlin.math.abs(it.latitude - entry.latitude) < 1e-7 &&
                kotlin.math.abs(it.longitude - entry.longitude) < 1e-7
        }
        list.add(0, entry)
        p[Keys.history] = json.encodeToString(list.take(MAX_HISTORY))
    }

    suspend fun clearHistory() = store.edit { it.remove(Keys.history) }

    suspend fun updateGuideCompleted(done: Boolean) = store.edit { it[Keys.guideDone] = done }

    suspend fun updateAutoResume(enabled: Boolean) = store.edit { it[Keys.autoResume] = enabled }

    suspend fun updateGcj02Compensation(enabled: Boolean) = store.edit { it[Keys.gcj] = enabled }

    suspend fun bumpLaunchCount(): Int {
        var count = 0
        store.edit { p ->
            count = (p[Keys.launchCount] ?: 0) + 1
            p[Keys.launchCount] = count
        }
        return count
    }

    private inline fun <reified T> decode(raw: String?): T? = raw?.let {
        runCatching { json.decodeFromString<T>(it) }.getOrNull()
    }

    private inline fun <reified T> decodeList(raw: String?): List<T> = raw?.let {
        runCatching { json.decodeFromString<List<T>>(it) }.getOrNull()
    } ?: emptyList()

    companion object {
        const val MIN_INTERVAL = 200L
        const val MAX_INTERVAL = 5_000L
        private const val MAX_PLACES = 200
        private const val MAX_HISTORY = 30
    }
}
