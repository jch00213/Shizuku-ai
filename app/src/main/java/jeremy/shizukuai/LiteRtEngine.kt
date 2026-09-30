package com.jeremy.shizukuai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LiteRtEngine private constructor(private val llmInference: LlmInference) {

    companion object {
        fun create(context: Context, modelPath: String): LiteRtEngine {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath) // Local path to Gemma/LiteRT-LM task model file
                .setMaxTokens(512)
                .setTemperature(0.2f)
                .build()

            val instance = LlmInference.createFromOptions(context, options)
            return LiteRtEngine(instance)
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
            val rawResponse = llmInference.generateResponse(fullPrompt)
            extractCommand(rawResponse) ?: rawResponse
        } catch (e: Exception) {
            "Error executing LiteRT inference: ${e.localizedMessage}"
        }
    }

    private fun extractCommand(text: String): String? {
        val regex = "```(?:bash|sh)?\\s*([\\s\\S]*?)\\s*```".toRegex()
        return regex.find(text)?.groupValues?.get(1)?.trim()
    }
}
