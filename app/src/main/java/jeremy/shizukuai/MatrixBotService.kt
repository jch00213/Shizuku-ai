package com.jeremy.shizukuai

import android.content.Context
import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.net.URLEncoder

class MatrixBotService(
    private val context: Context,
    private val baseUrl: String,       // e.g. "http://100.79.108.115:8082"
    private val accessToken: String,   // Access token from your homeserver
    private val botUserId: String,     // e.g. "@shizuku_bot:localhost"
    private val engineProvider: () -> LiteRtEngine?
) {

    private val tag = "MatrixBotService"
    private var isRunning = false
    private var syncJob: Job? = null
    private var sinceToken: String? = null

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        syncJob = CoroutineScope(Dispatchers.IO).launch {
            Log.i(tag, "Matrix Bot service started. Polling homeserver at $baseUrl...")
            runSyncLoop()
        }
    }

    fun stop() {
        isRunning = false
        syncJob?.cancel()
        Log.i(tag, "Matrix Bot service stopped.")
    }

    private suspend fun runSyncLoop() {
        while (isRunning) {
            try {
                val url = buildString {
                    append("$baseUrl/_matrix/client/v3/sync?timeout=30000")
                    sinceToken?.let { append("&since=$it") }
                }

                val response = client.get(url) {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                }

                if (response.status.isSuccess()) {
                    val syncData = response.body<MatrixSyncResponse>()
                    val previousSince = sinceToken
                    sinceToken = syncData.nextBatch

                    // Process timeline events only if this isn't the initial catchup sync
                    if (previousSince != null) {
                        processSyncEvents(syncData)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error during Matrix sync loop: ${e.localizedMessage}")
                delay(5000) // Backoff on network failures
            }
        }
    }

    private suspend fun processSyncEvents(syncData: MatrixSyncResponse) {
        val joinedRooms = syncData.rooms?.join ?: return

        for ((roomId, room) in joinedRooms) {
            val events = room.timeline?.events ?: continue
            for (event in events) {
                // Ignore non-text messages or messages sent by the bot itself
                if (event.type != "m.room.message" || event.sender == botUserId) continue

                val messageBody = event.content?.body?.trim() ?: continue
                if (messageBody.startsWith("!claw") || messageBody.startsWith("!exec") || messageBody.startsWith("!help") || messageBody == "!status") {
                    handleBotCommand(roomId, messageBody)
                }
            }
        }
    }

    private suspend fun handleBotCommand(roomId: String, commandText: String) {
        when {
            commandText == "!help" -> {
                sendTextMessage(roomId, "🤖 **OpenClaw Shizuku Agent Commands:**\n\n" +
                        "• `!claw <prompt>` - Process prompt using local LiteRtEngine.\n" +
                        "• `!exec <shizuku_cmd>` - Execute raw shell command via Shizuku.\n" +
                        "• `!status` - Check device system state.")
            }

            commandText.startsWith("!exec ") -> {
                val cmd = commandText.removePrefix("!exec ").trim()
                val service = ShizukuManager.remoteService.value
                val output = service?.execCommand(cmd) ?: "Error: Shizuku service not connected."
                sendTextMessage(roomId, "```\n$output\n```")
            }

            commandText == "!status" -> {
                val isLoaded = engineProvider() != null
                val shizukuActive = ShizukuManager.remoteService.value != null
                sendTextMessage(roomId, "📱 **Device Status:**\n- LiteRt Model: ${if (isLoaded) "Ready" else "Not Loaded"}\n- Shizuku Service: ${if (shizukuActive) "Connected" else "Disconnected"}")
            }

            commandText.startsWith("!claw ") -> {
                val prompt = commandText.removePrefix("!claw ").trim()
                val engine = engineProvider()

                if (engine == null) {
                    sendTextMessage(roomId, "⚠️ LiteRtEngine model is not loaded yet.")
                    return
                }

                sendTextMessage(roomId, "⏳ *Thinking...*")
                val response = withContext(Dispatchers.IO) {
                    engine.generateCommand(context, prompt)
                }
                sendTextMessage(roomId, "💡 **OpenClaw Output:**\n$response")
            }
        }
    }

    private suspend fun sendTextMessage(roomId: String, message: String): Boolean {
        return try {
            val encodedRoomId = URLEncoder.encode(roomId, "UTF-8")
            val txnId = System.currentTimeMillis().toString()
            val response = client.put("$baseUrl/_matrix/client/v3/rooms/$encodedRoomId/send/m.room.message/$txnId") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(MatrixSendMessageRequest(body = message))
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            Log.e(tag, "Failed to send message to room $roomId: ${e.localizedMessage}")
            false
        }
    }
}
