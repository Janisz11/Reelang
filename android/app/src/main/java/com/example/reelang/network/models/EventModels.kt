package com.example.reelang.network.models

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class EventEnvelopeRequest(
    @SerializedName("event_id") val eventId: String,
    @SerializedName("event_type") val eventType: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("reel_id") val reelId: String,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("platform") val platform: String,
    @SerializedName("client_timestamp") val clientTimestamp: String,
    @SerializedName("payload") val payload: JsonObject
)

data class EventBatchRequest(
    @SerializedName("events") val events: List<EventEnvelopeRequest>
)

data class EventBatchResponse(
    @SerializedName("accepted") val accepted: Int
)
