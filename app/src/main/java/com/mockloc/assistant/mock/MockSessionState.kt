package com.mockloc.assistant.mock

import com.mockloc.assistant.data.model.MockPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface MockSessionState {
    data object Idle : MockSessionState

    data class Running(
        val point: MockPoint,
        val injectedLatitude: Double,
        val injectedLongitude: Double,
        val verified: Boolean,
        val lastUpdateMillis: Long,
    ) : MockSessionState

    data class Error(val reason: String) : MockSessionState
}

/**
 * 进程内单例状态仓库，供 UI 与前台服务共享运行状态。
 */
object MockSessionStateStore {
    private val _state = MutableStateFlow<MockSessionState>(MockSessionState.Idle)
    val state: StateFlow<MockSessionState> = _state.asStateFlow()

    fun publish(next: MockSessionState) {
        _state.value = next
    }

    val isRunning: Boolean get() = _state.value is MockSessionState.Running
}
