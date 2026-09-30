package com.jeremy.shizukuai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AiExecutor {

    suspend fun captureAndParseScreen(
        service: IRemoteAiService,
        state: AiAgentState
    ): List<UiElementNode> = withContext(Dispatchers.IO) {
        state.isExecuting = true
        state.appendLog("[Action] Capturing screen hierarchy...")

        val xmlDump = service.dumpUiHierarchy()
        if (xmlDump.startsWith("Error") || xmlDump.startsWith("UI dump error")) {
            state.appendLog("[Error] $xmlDump")
            state.isExecuting = false
            return@withContext emptyList()
        }

        val nodes = UiNodeParser.parseXmlDump(xmlDump)
        state.parsedNodes = nodes
        state.appendLog("[Success] Extracted ${nodes.size} top-level nodes.")
        state.isExecuting = false
        return@withContext nodes
    }

    suspend fun executeShellCommand(
        service: IRemoteAiService,
        command: String,
        state: AiAgentState
    ): String = withContext(Dispatchers.IO) {
        state.isExecuting = true
        state.appendLog("$ $command")

        val result = service.execCommand(command)
        state.appendLog(result.trimEnd())
        state.isExecuting = false
        return@withContext result
    }

    suspend fun tapBoundsCenter(
        service: IRemoteAiService,
        boundsString: String,
        state: AiAgentState
    ) = withContext(Dispatchers.IO) {
        // Bounds format: "[left,top][right,bottom]" -> e.g., "[100,200][300,400]"
        val regex = Regex("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]")
        val match = regex.find(boundsString)

        if (match != null) {
            val (left, top, right, bottom) = match.destructured
            val x = (left.toInt() + right.toInt()) / 2
            val y = (top.toInt() + bottom.toInt()) / 2

            state.appendLog("[Input] Injecting tap at ($x, $y)")
            service.injectTap(x, y)
        } else {
            state.appendLog("[Error] Invalid bounds pattern: $boundsString")
        }
    }
}
