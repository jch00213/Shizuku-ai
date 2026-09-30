package com.jeremy.shizukuai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AiAgentState {
    var isExecuting by mutableStateOf(false)
    var activeCommand by mutableStateOf("")
    var parsedNodes by mutableStateOf<List<UiElementNode>>(emptyList())
    var rawLogs by mutableStateOf("// System initialized. Awaiting Shizuku connection...\n")

    fun appendLog(message: String) {
        rawLogs += "$message\n"
    }

    fun clearLogs() {
        rawLogs = "// Logs cleared.\n"
    }
}
