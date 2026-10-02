package com.jeremy.shizukuai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MatrixAgentBridge(private val scope: CoroutineScope) {

    private var workerJob: Job? = null
    @Volatile private var isRunning = false
    private var nextBatchToken: String? = null

    fun startListening(
        homeserver: String,
        accessToken: String,
        roomId: String,
        onMessageReceived: suspend (sender: String, message: String) -> String
    ) {
        if (isRunning) return
        isRunning = true

        val cleanHomeserver = homeserver.trimEnd('/')

        workerJob = scope.launch(Dispatchers.IO) {
            // Initial sync to get the latest batch token (avoid processing historical messages)
            nextBatchToken = fetchInitialSyncBatch(cleanHomeserver, accessToken)

            while (isActive && isRunning) {
                try {
                    val syncResult = performLongPollSync(cleanHomeserver, accessToken, nextBatchToken)
                    if (syncResult != null) {
                        nextBatchToken = syncResult.optString("next_batch", nextBatchToken)

                        val rooms = syncResult.optJSONObject("rooms")?.optJSONObject("join")
                        val targetRoom = rooms?.optJSONObject(roomId)

                        if (targetRoom != null) {
                            val events = targetRoom.optJSONObject("timeline")?.optJsonArray("events")
                            if (events != null) {
                                for (i in 0 until events.length()) {
                                    val event = events.getJSONObject(i)
                                    val type = event.optString("type")
                                    val sender = event.optString("sender")

                                    if (type == "m.room.message") {
                                        val content = event.optJSONObject("content")
                                        val msgType = content?.optString("msgtype")
                                        val body = content?.optString("body")

                                        // Ignore empty bodies or messages sent by the bot itself
                                        if (msgType == "m.text" && !body.isNullOrBlank()) {
                                            val responseText = onMessageReceived(sender, body)
                                            sendMatrixMessage(cleanHomeserver, accessToken, roomId, responseText)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Backoff on connection/timeout errors before retrying
                    delay(5000)
                }
            }
        }
    }

    private fun fetchInitialSyncBatch(homeserver: String, token: String): String? {
        return try {
            val url = URL("$homeserver/_matrix/client/v3/sync?timeout=0&filter={\"room\":{\"timeline\":{\"limit\":1}}}")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 10000
                readTimeout = 10000
            }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(response).optString("next_batch", null)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun performLongPollSync(homeserver: String, token: String, since: String?): JSONObject? {
        val sinceParam = if (since != null) "&since=${URLEncoder.encode(since, "UTF-8")}" else ""
        val url = URL("$homeserver/_matrix/client/v3/sync?timeout=30000$sinceParam")

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 35000
            readTimeout = 35000
        }

        return if (conn.responseCode == 200) {
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(response)
        } else {
            null
        }
    }

    private fun sendMatrixMessage(homeserver: String, token: String, roomId: String, messageText: String) {
        try {
            val txnId = System.currentTimeMillis()
            val encodedRoomId = URLEncoder.encode(roomId, "UTF-8")
            val url = URL("$homeserver/_matrix/client/v3/rooms/$encodedRoomId/send/m.room.message/$txnId")

            val jsonBody = JSONObject().apply {
                put("msgtype", "m.text")
                put("body", messageText)
            }

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            conn.responseCode // Execute HTTP call
        } catch (_: Exception) {}
    }

    fun stop() {
        isRunning = false
        workerJob?.cancel()
        workerJob = null
    }
}

// Extension helper for safer JSON array parsing
private fun JSONObject.optJsonArray(name: String): org.json.JSONArray? {
    return try {
        this.getJSONArray(name)
    } catch (_: Exception) {
        null
    }
}
