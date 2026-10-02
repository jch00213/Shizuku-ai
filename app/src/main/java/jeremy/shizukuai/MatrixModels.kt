package com.jeremy.shizukuai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MatrixSyncResponse(
    @SerialName("next_batch") val nextBatch: String? = null,
    val rooms: MatrixRoomsSync? = null
)

@Serializable
data class MatrixRoomsSync(
    val join: Map<String, MatrixJoinedRoom>? = null
)

@Serializable
data class MatrixJoinedRoom(
    val timeline: MatrixTimeline? = null
)

@Serializable
data class MatrixTimeline(
    val events: List<MatrixEvent>? = emptyList()
)

@Serializable
data class MatrixEvent(
    @SerialName("event_id") val eventId: String? = null,
    val sender: String? = null,
    val type: String? = null,
    val content: MatrixEventContent? = null
)

@Serializable
data class MatrixEventContent(
    val msgtype: String? = null,
    val body: String? = null
)

@Serializable
data class MatrixSendMessageRequest(
    val msgtype: String = "m.text",
    val body: String
)
