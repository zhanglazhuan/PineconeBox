package com.pinecone.pinecone.log

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Periodic log upload via WorkManager.
 *
 * Triggers:
 * - Every 60 minutes (PeriodicWork)
 * - Immediately on crash events
 * - Only on WiFi (configurable)
 * - Respects parent upload_enabled switch
 */
class LogUploader(
    private val context: Context,
    private val writer: LogWriter,
    private val config: LogConfig
) {
    companion object {
        private const val WORK_NAME = "pinecone_log_upload"
        private const val BATCH_MAX = 500
        private const val TIMEOUT_SECONDS = 15
        private const val MAX_RETRIES = 3
        private val RETRY_DELAYS_MS = longArrayOf(1000, 2000, 4000, 8000)
    }

    init {
        schedulePeriodicWork()
    }

    private fun schedulePeriodicWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<UploadWorker>(
            60, TimeUnit.MINUTES, 30, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    /** Attempt upload now (best-effort). */
    fun tryUpload() {
        val work = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueue(work)
    }

    /** Upload crash file immediately, skipping WiFi constraint. */
    fun uploadCrashFile() {
        val work = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueue(work)
    }

    // ── Worker ──

    inner class UploadWorker(
        workerContext: Context,
        params: WorkerParameters
    ) : CoroutineWorker(workerContext, params) {

        override suspend fun doWork(): Result {
            if (!config.uploadEnabled) return Result.success()
            if (config.wifiOnly && !isWiFiConnected()) return Result.retry()
            val token = config.deviceToken ?: return Result.retry()

            val pendingFiles = writer.pendingUploadFiles()
            if (pendingFiles.isEmpty()) return Result.success()

            // Read all events
            val allEvents = mutableListOf<LogEvent>()
            for (file in pendingFiles) {
                try {
                    BufferedReader(file.reader()).use { reader ->
                        reader.lineSequence().forEach { line ->
                            try {
                                allEvents.add(LogEvent.fromJson(JSONObject(line.trim())))
                            } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) { continue }
            }

            if (allEvents.isEmpty()) {
                pendingFiles.forEach { writer.markUploaded(it) }
                return Result.success()
            }

            val sanitized = PrivacyFilter.sanitizeBatch(allEvents)
            var successCount = 0

            sanitized.chunked(BATCH_MAX).forEachIndexed { batchIdx, batch ->
                if (uploadBatch(batch, batchIdx, token)) successCount++
            }

            return if (successCount > 0) {
                pendingFiles.forEach { writer.markUploaded(it) }
                Result.success()
            } else {
                Result.retry()
            }
        }

        private fun uploadBatch(events: List<LogEvent>, batchIdx: Int, token: String): Boolean {
            var attempt = 0
            while (attempt <= MAX_RETRIES) {
                try {
                    val url = URL("${config.serverUrl}/logs/batch")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = TIMEOUT_SECONDS * 1000
                        readTimeout = TIMEOUT_SECONDS * 1000
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Authorization", "Bearer $token")
                        setRequestProperty("X-Device-ID", config.deviceId)
                        setRequestProperty("X-Client-Version", PineconeLogger.getAppVersion())
                        doOutput = true
                    }

                    val batchSeq = config.lastBatchSeq + batchIdx + 1
                    val body = JSONObject().apply {
                        put("device_id", config.deviceId)
                        put("account_uuid", config.getAccountUuid())
                        put("app_version", PineconeLogger.getAppVersion())
                        put("android_version", PineconeLogger.getAndroidVersion())
                        put("batch_seq", batchSeq)
                        put("events", JSONArray().apply {
                            events.forEach { put(it.toJson()) }
                        })
                    }

                    OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
                    val code = conn.responseCode

                    return when {
                        code == 200 -> {
                            config.lastBatchSeq = batchSeq; true
                        }
                        code in 400..499 -> {
                            val ne = NetworkErrorEvent(
                                System.currentTimeMillis(), "uploader",
                                "/logs/batch", code, TIMEOUT_SECONDS * 1000L, "HTTP $code"
                            )
                            writer.appendLine(ne.toJson().toString())
                            false
                        }
                        else -> {
                            if (attempt < MAX_RETRIES) Thread.sleep(RETRY_DELAYS_MS[attempt])
                            attempt++; false
                        }
                    }
                } catch (e: Exception) {
                    val ne = NetworkErrorEvent(
                        System.currentTimeMillis(), "uploader",
                        "/logs/batch", null, TIMEOUT_SECONDS * 1000L,
                        e.message ?: "unknown"
                    )
                    writer.appendLine(ne.toJson().toString())
                    if (attempt < MAX_RETRIES) Thread.sleep(RETRY_DELAYS_MS[attempt])
                    attempt++
                }
            }
            return false
        }

        private fun isWiFiConnected(): Boolean {
            val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
    }
}
