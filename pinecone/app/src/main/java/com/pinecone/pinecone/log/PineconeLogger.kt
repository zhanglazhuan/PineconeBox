package com.pinecone.pinecone.log

import android.content.Context
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Single entry point for all logging operations.
 * Fire-and-forget: log() returns immediately, writes on background thread.
 *
 * Usage:
 *   PineconeLogger.init(context, accountUuid)
 *   PineconeLogger.log(ItemClickEvent(...))
 *   PineconeLogger.log(CrashEvent(...))
 */
object PineconeLogger {

    private lateinit var writer: LogWriter
    private lateinit var config: LogConfig
    private lateinit var uploader: LogUploader
    private var session: LogSession? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "pinecone-logger").apply { priority = Thread.MIN_PRIORITY }
    }

    private var accountUuid: String = ""
    private var appVersion: String = "unknown"
    private var androidVersion: String = android.os.Build.VERSION.RELEASE

    // ── Initialization ──

    fun init(context: Context, accountUuid: String) {
        val appCtx = context.applicationContext
        this.accountUuid = accountUuid
        this.config = LogConfig.getInstance(appCtx)
        this.writer = LogWriter(File(appCtx.filesDir, "logs"))
        this.uploader = LogUploader(appCtx, writer, config)

        try {
            appVersion = appCtx.packageManager.getPackageInfo(appCtx.packageName, 0)
                .versionName ?: "unknown"
        } catch (_: Exception) {}

        startSession()
    }

    // ── Public API ──

    /** Log an event. Fire-and-forget: returns immediately. */
    fun log(event: LogEvent) {
        executor.execute {
            try {
                val jsonLine = event.toJson().toString()
                if (event is CrashEvent) {
                    writer.appendCrashLine(jsonLine)
                    uploader.uploadCrashFile()
                } else {
                    writer.appendLine(jsonLine)
                }
            } catch (_: Exception) { /* never crash from logging */ }
        }
    }

    /** Force flush pending writes and trigger upload. */
    fun flush() {
        executor.execute { uploader.tryUpload() }
    }

    /** Query local events for parental reporting. */
    fun getLocalStats(since: Long, limit: Int = 500): List<LogEvent> =
        writer.queryLocal(since, limit)

    /** Toggle upload permission (parental control). */
    fun setUploadEnabled(enabled: Boolean) {
        config.uploadEnabled = enabled
    }

    fun isUploadEnabled(): Boolean = config.uploadEnabled

    // ── Session management ──

    fun startSession() {
        session = LogSession()
    }

    fun endSession() {
        val s = session ?: return
        log(s.end())
        session = null
    }

    fun getSession(): LogSession? = session

    // ── Accessors ──

    fun getDeviceId(): String = config.deviceId
    fun getAccountUuid(): String = accountUuid
    fun getAppVersion(): String = appVersion
    fun getAndroidVersion(): String = androidVersion
    fun getConfig(): LogConfig = config
    fun getWriter(): LogWriter = writer
}
