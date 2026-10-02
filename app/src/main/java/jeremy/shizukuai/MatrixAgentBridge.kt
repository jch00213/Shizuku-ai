package com.jeremy.shizukuai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bridge managing the Matrix agent connection state and background tasks.
 */
class MatrixAgentBridge(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    sealed interface BridgeState {
        object Idle : BridgeState
        object Connecting : BridgeState
        data class Connected(val info: String) : BridgeState
        data class Error(val message: String) : BridgeState
        object Stopped : BridgeState
    }

    private val _state = MutableStateFlow<BridgeState>(BridgeState.Idle)
    val state: StateFlow<BridgeState> = _state.asStateFlow()

    private var workerJob: Job? = null

    /**
     * Start the background bridge worker.
     */
    fun start(serverUrl: String, token: String, onResult: (Boolean, String) -> Unit) {
        if (_state.value is BridgeState.Connecting || _state.value is BridgeState.Connected) {
            return
        }

        _state.value = BridgeState.Connecting

        workerJob = scope.launch {
            try {
                runBridgeWorker(serverUrl, token)
                _state.value = BridgeState.Connected("Connected to $serverUrl")
                withContext(Dispatchers.Main) {
                    onResult(true, "Successfully connected")
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "Unknown connection error"
                _state.value = BridgeState.Error(errorMsg)
                withContext(Dispatchers.Main) {
                    onResult(false, errorMsg)
                }
            }
        }
    }

    /**
     * Stops active workers and releases resources. Called from MainActivity.
     */
    fun stop() {
        workerJob?.cancel()
        workerJob = null
        _state.value = BridgeState.Stopped
    }
}
