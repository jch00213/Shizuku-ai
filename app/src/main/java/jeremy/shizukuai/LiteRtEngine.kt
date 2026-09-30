package com.jeremy.shizukuai

import android.content.Context
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LiteRtEngine private constructor(
    private val engine: Engine,
    private val conversation: Conversation
) : AutoCloseable {

    companion object {
        suspend fun create(context: Context, modelPath: String): LiteRtEngine = withContext(Dispatchers.IO) {
            val config = EngineConfig(
                modelPath = modelPath,
                maxTokens = 512,
                temperature = 0.2f
            )
            
            val engine = Engine(config)
            engine.initialize()
            val conversation = engine.createConversation()
            
            LiteRtEngine(engine, conversation)
        }
    }

    suspend fun generateCommand(userPrompt: String): String = withContext(Dispatchers.Default) {
        val systemContext = """
            You are ShizukuAI, an assistant running on Android.
            Generate a raw bash/adb shell command to answer the request.
            Wrap the command inside a ```bash ... ``` code block.
            Do not prepend 'adb shell'.
        """.trimIndent()

        val fullPrompt = "$systemContext\n\nUser: $userPrompt\nAssistant:"

        try {
            val rawResponse = conversation.sendMessage(fullPrompt)
            extractCommand(rawResponse) ?: rawResponse
        } catch (e: Exception) {
            "Error executing LiteRT inference: ${e.localizedMessage}"
        }
    }

    private fun extractCommand(text: String): String? {
        val regex = "```(?:bash|sh)?\\s*([\\s\\S]*?)\\s*```".toRegex()
        return regex.find(text)?.groupValues?.get(1)?.trim()
    }

    override fun close() {
        conversation.close()
        engine.close()
    }
}
