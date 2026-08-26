package com.example.reelang.events

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.reelang.data.local.ReelangDatabase
import com.example.reelang.data.local.entities.EventOutboxEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Front door for instrumentation: every call is a local Room write that cannot fail on a
 * missing network, plus a nudge for the uploader.
 */
object EventTracker {

    private const val TAG = "EventTracker"

    @SuppressLint("StaticFieldLeak")
    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @SuppressLint("SimpleDateFormat")
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        EventUploadWorker.schedulePeriodic(context.applicationContext)
        // Drains whatever a previous run left behind before this session adds to it.
        EventUploadWorker.enqueueNow(context.applicationContext)
    }

    fun track(eventType: String, reelId: String, payload: Map<String, Any?> = emptyMap()) {
        val context = appContext
        if (context == null) {
            Log.w(TAG, "Dropping $eventType for $reelId: tracker not initialised")
            return
        }

        val entity = EventOutboxEntity(
            eventId = UUID.randomUUID().toString(),
            eventType = eventType,
            reelId = reelId,
            sessionId = EventSession.sessionId,
            clientTimestamp = synchronized(isoFormat) { isoFormat.format(System.currentTimeMillis()) },
            payload = encodeEventPayload(payload)
        )

        scope.launch {
            runCatching {
                ReelangDatabase.getInstance(context).eventOutboxDao().insert(entity)
                EventUploadWorker.enqueueNow(context)
            }.onFailure { Log.e(TAG, "Failed to queue $eventType for $reelId", it) }
        }
    }

    fun networkType(): String = appContext?.let { currentNetworkType(it) } ?: NETWORK_UNKNOWN
}
