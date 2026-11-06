package com.mockloc.assistant.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mockloc.assistant.data.model.AppSettings
import com.mockloc.assistant.data.model.HistoryEntry
import com.mockloc.assistant.data.model.MockPoint
import com.mockloc.assistant.data.model.PrerequisiteState
import com.mockloc.assistant.data.model.SavedPlace
import com.mockloc.assistant.data.prefs.AppRepository
import com.mockloc.assistant.mock.MockPrerequisites
import com.mockloc.assistant.mock.MockSessionState
import com.mockloc.assistant.mock.MockSessionStateStore
import com.mockloc.assistant.mock.service.MockLocationService
import com.mockloc.assistant.ui.state.HomeUiState
import com.mockloc.assistant.ui.state.RealLocationReader
import com.mockloc.assistant.ui.state.RealLocationUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 首页 ViewModel。
 *
 * 职责边界：
 * - 管理选中位置、运行状态、前置条件检查结果。
 * - 处理启动 / 停止模拟定位，并在启动前做前置条件校验，避免出现「点了没反应」。
 * - 与前台服务通过 MockSessionStateStore 通信。
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val app = application

    private val transient = MutableStateFlow<String?>(null)
    private val prerequisites = MutableStateFlow(PrerequisiteState())
    private val realLocation = MutableStateFlow<RealLocationUi?>(null)
    private val loadingReal = MutableStateFlow(false)
    private val realError = MutableStateFlow<String?>(null)
    private val starting = MutableStateFlow(false)
    private val point = MutableStateFlow<MockPoint?>(null)

    val savedPlaces: StateFlow<List<SavedPlace>> = repository.savedPlaces
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<HistoryEntry>> = repository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settingsFlow: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val uiState: StateFlow<HomeUiState> = combine(
        point,
        MockSessionStateStore.state,
        prerequisites,
        repository.settings,
        realLocation,
        loadingReal,
        realError,
        transient,
        starting,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        HomeUiState(
            point = values[0] as MockPoint?,
            session = values[1] as MockSessionState,
            prerequisites = values[2] as PrerequisiteState,
            settings = values[3] as AppSettings,
            realLocation = values[4] as RealLocationUi?,
            loadingRealLocation = values[5] as Boolean,
            realLocationError = values[6] as String?,
            transientMessage = values[7] as String?,
            starting = values[8] as Boolean,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(settings = AppSettings()),
    )

    init {
        viewModelScope.launch {
            val snapshot = repository.session
            snapshot.collect { s ->
                if (point.value == null && s.point != null) {
                    point.value = s.point
                }
            }
        }
        refreshPrerequisites()
        refreshRealLocation()
    }

    fun refreshPrerequisites() {
        prerequisites.value = PrerequisiteState(
            locationPermission = MockPrerequisites.hasLocationPermission(app),
            developerOptions = MockPrerequisites.developerOptionsEnabled(app),
            mockAppSelected = MockPrerequisites.isMockLocationAllowed(app),
            mockSelectionUnknown = !MockPrerequisites.isMockLocationAllowed(app) &&
                MockPrerequisites.hasLocationPermission(app),
            batteryOptimized = !MockPrerequisites.isBatteryOptimizationIgnored(app),
        )
    }

    fun refreshRealLocation() {
        if (!MockPrerequisites.hasLocationPermission(app)) {
            realError.value = "未授予定位权限"
            realLocation.value = null
            return
        }
        loadingReal.value = true
        realError.value = null
        try {
            val found = RealLocationReader.lastKnown(app)
            realLocation.value = found
            if (found == null) {
                realError.value = "系统暂无最近定位，请到空旷处或开启 GPS 后再试"
            }
        } catch (t: SecurityException) {
            realLocation.value = null
            realError.value = "定位权限不可用，请重新授权"
        } finally {
            loadingReal.value = false
        }
    }

    fun selectPoint(next: MockPoint) {
        point.value = next
        viewModelScope.launch {
            repository.setLastPoint(next)
            repository.addHistory(
                HistoryEntry(
                    id = UUID.randomUUID().toString(),
                    latitude = next.latitude,
                    longitude = next.longitude,
                    name = next.displayName,
                    address = next.address,
                    usedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun clearSelectedPoint() {
        point.value = null
    }

    fun startMocking() {
        val target = point.value
        if (target == null) {
            emit("请先选择要模拟的位置")
            return
        }
        refreshPrerequisites()
        val pre = prerequisites.value

        if (!pre.locationPermission) {
            emit("请先授予定位权限")
            return
        }
        if (!pre.developerOptions) {
            emit("请先开启开发者选项")
            return
        }
        if (MockSessionStateStore.state.value is MockSessionState.Running) {
            emit("模拟定位已在运行")
            return
        }

        starting.value = true
        viewModelScope.launch {
            try {
                repository.saveSession(target, running = true)
                MockLocationService.start(app, target, settingsFlow.value)
                emit("已启动模拟定位")
            } catch (t: Throwable) {
                emit("启动失败：${t.message ?: "未知错误"}")
                MockSessionStateStore.publish(MockSessionState.Idle)
            } finally {
                starting.value = false
            }
        }
    }

    fun stopMocking() {
        starting.value = true
        viewModelScope.launch {
            try {
                MockLocationService.stop(app)
                emit("已停止模拟定位")
            } finally {
                starting.value = false
            }
        }
    }

    fun toggleMocking() {
        if (MockSessionStateStore.state.value is MockSessionState.Running) stopMocking() else startMocking()
    }

    fun saveCurrentPlace(name: String) {
        val target = point.value ?: run {
            emit("请先选择位置")
            return
        }
        viewModelScope.launch {
            repository.addPlace(
                SavedPlace(
                    id = UUID.randomUUID().toString(),
                    latitude = target.latitude,
                    longitude = target.longitude,
                    name = name.ifBlank { target.displayName },
                    address = target.address,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            emit("已保存到常用地点")
        }
    }

    fun removePlace(id: String) {
        viewModelScope.launch {
            repository.removePlace(id)
            emit("已删除")
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            emit("已清空历史记录")
        }
    }

    fun completeGuide() {
        viewModelScope.launch {
            repository.updateGuideCompleted(true)
            refreshPrerequisites()
        }
    }

    fun updateAutoResume(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAutoResume(enabled)
        }
    }

    fun updateGcj02Compensation(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateGcj02Compensation(enabled)
        }
    }

    fun consumeMessage() {
        transient.value = null
    }

    fun showMessage(text: String) {
        transient.value = text
    }

    private fun emit(text: String) {
        transient.value = text
    }
}
