package com.example.reelang.events

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

private val gson = Gson()

fun encodeEventPayload(payload: Map<String, Any?>): String = gson.toJson(payload)

/** Reads the stored JSON back as a tree so numbers and booleans keep the type they were written with. */
fun decodeEventPayload(json: String): JsonObject =
    runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: JsonObject()
