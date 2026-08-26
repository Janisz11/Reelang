package com.example.reelang.events

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.reelang.auth.model.UserSession
import com.example.reelang.data.local.ReelangDatabase
import com.example.reelang.network.ApiClient
import com.example.reelang.network.models.EventBatchRequest
import com.example.reelang.network.models.EventEnvelopeRequest
import java.util.concurrent.TimeUnit

class EventUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = ReelangDatabase.getInstance(applicationContext).eventOutboxDao()
        val sync = OutboxSync(
            dao = dao,
            uploader = ApiEventUploader,
            userId = { UserSession.userId }
        )

        val outcome = runCatching { sync.syncOnce() }
            .onFailure { Log.w(TAG, "Outbox sync failed", it) }
            .getOrNull()

        if (outcome != SyncOutcome.NOTHING_PENDING && runCatching { sync.hasPending() }.getOrDefault(false)) {
            scheduleNextSweep(applicationContext)
        }

        // Failures leave rows PENDING for the next sweep, so the worker itself never fails.
        return Result.success()
    }

    companion object {
        private const val TAG = "EventUploadWorker"
        const val PERIODIC_WORK_NAME = "event-outbox-periodic"
        const val ONE_TIME_WORK_NAME = "event-outbox-now"
        const val SWEEP_WORK_NAME = "event-outbox-sweep"
        const val SWEEP_INTERVAL_SECONDS = 30L

        /**
         * Watching a reel writes an event every couple of seconds. Uploading each one the
         * instant it lands would blow past the endpoint's 10/minute budget, so writes are
         * coalesced into one upload — still far short of a full sweep interval.
         */
        const val ENQUEUE_NOW_DELAY_SECONDS = 10L

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        private fun request(delaySeconds: Long = 0) =
            OneTimeWorkRequestBuilder<EventUploadWorker>()
                .setConstraints(constraints)
                .apply { if (delaySeconds > 0) setInitialDelay(delaySeconds, TimeUnit.SECONDS) }
                .build()

        /**
         * WorkManager refuses periods shorter than 15 minutes. The 30s cadence therefore comes
         * from [scheduleNextSweep], which keeps re-arming itself while the outbox is non-empty;
         * this periodic job only exists to restart that chain after the process is killed.
         */
        fun schedulePeriodic(context: Context) {
            val periodic = PeriodicWorkRequestBuilder<EventUploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )
        }

        /** Drain shortly after a write, so a connected device does not wait a full cycle. */
        fun enqueueNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request(ENQUEUE_NOW_DELAY_SECONDS)
            )
        }

        fun scheduleNextSweep(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                SWEEP_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request(SWEEP_INTERVAL_SECONDS)
            )
        }
    }
}

object ApiEventUploader : EventUploader {
    override suspend fun upload(events: List<EventEnvelopeRequest>): Boolean =
        ApiClient.api.postEvents(EventBatchRequest(events)).isSuccessful
}
