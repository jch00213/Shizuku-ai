package com.jeremy.shizukuai

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.annotation.Serializable
import kotlinx.serialization.json.*

@Serializable
data class MatrixLoginResponse(
    val access_token: String,
    val home_server: String? = null,
    val user_id: String
)

@Serializable
data class MatrixSyncResponse(
    val next_batch: String,
    val rooms: JsonObject? = null
)

class MatrixAgentBridge(
    private val homeserverUrl: String,
    private val accessToken: String,
    private val targetRoomId: String
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private var isListening = false
    private var nextBatchToken: String? = null

    fun startListening(
        onMessageReceived: suspend (sender: String, body: String) -> String?
    ): Job {
        isListening = true
        return CoroutineScope(Dispatchers.IO).launch {
            while (isListening) {
                try {
                    val url = buildString {
                        append("$homeserverUrl/_matrix/client/v3/sync?timeout=30000")
                        nextBatchToken?.let { append("&since=$it") }
                    }

                    val response = client.get(url) {
                        header(HttpHeaders.Authorization, "Bearer $accessToken")
                    }

                    if (response.status == HttpStatusCode.OK) {
                        val syncData = response.body<MatrixSyncResponse>()
                        nextBatchToken = syncData.next_batch
                        
                        // Parse incoming room events
                        processSyncRooms(syncData.rooms, onMessageReceived)
                    }
                } catch (e: Exception) {
                    delay(5000) // Retry backoff on connectivity drop
                }
            }
        }
    }

    private suspend fun processSyncRooms(
        roomsObj: JsonObject?,
        onMessageReceived: suspend (sender: String, body: String) -> String?
    ) {
        if (roomsObj == null) return
        val joinRooms = roomsObj["join"]?.jsonObject ?: return
        val roomData = joinRooms[targetRoomId]?.jsonObject ?: return
        val timelineEvents = roomData["timeline"]?.jsonObject?.get("events")?.jsonArray ?: return

        for (event in timelineEvents) {
            val eventObj = event.jsonObject
            val type = eventObj["type"]?.jsonPrimitive?.content ?: continue
            val sender = eventObj["sender"]?.jsonPrimitive?.content ?: continue

            // Filter for text messages
            if (type == "m.room.message") {
                val content = eventObj["content"]?.jsonObject ?: continue
                val msgType = content["msgtype"]?.jsonPrimitive?.content ?: continue
                val body = content["body"]?.jsonPrimitive?.content ?: continue

                if (msgType == "m.text" && !sender.contains("shizuku_bot")) {
                    val resultText = onMessageReceived(sender, body)
                    if (resultText != null) {
                        sendTextMessage(targetRoomId, resultText)
                    }
                }
            }
        }
    }

    suspend fun sendTextMessage(roomId: String, text: String) {
        val txnId = System.currentTimeMillis()
        val url = "$homeserverUrl/_matrix/client/v3/rooms/$roomId/send/m.room.message/$txnId"

        val payload = buildJsonObject {
            put("msgtype", "m.text")
            put("body", text)
        }

        client.put(url) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
    }

    fun stop() {
        isListening = false
        client.close()
    }
}
