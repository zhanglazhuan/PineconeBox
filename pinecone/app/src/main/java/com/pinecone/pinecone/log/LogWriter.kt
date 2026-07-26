package com.pinecone.pinecone.log

import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Thread-safe JSON Lines file writer with rolling and cleanup.
 *
 * File naming: events_YYYY-MM-DD_NNN.log
 * Rolling: >= 2MB OR > 24h old OR (crash AND >= 512KB)
 * Cleanup: .uploaded deleted after 48h, .log after 7d, total 50MB cap
 */
class LogWriter(private val logDir: File) {

    private val writeLock = Any()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init {
        if (!logDir.exists()) logDir.mkdirs()
        enforceSizeLimit()
    }

    companion object {
        const val MAX_FILE_SIZE   = 2L * 1024 * 1024
        const val MAX_FILE_AGE_MS = 24L * 3600_000
        const val CRASH_MIN_SIZE  = 512L * 1024
        const val RETENTION_DAYS  = 7
        const val MAX_TOTAL_SIZE  = 50L * 1024 * 1024
        const val UPLOADED_RETENTION_MS = 48L * 3600_000
    }

    /** Append a JSON line. Thread-safe. */
    fun appendLine(jsonLine: String) {
        synchronized(writeLock) {
            FileWriter(currentWriteFile(), true).use { fw ->
                fw.append(jsonLine); fw.append('\n'); fw.flush()
            }
        }
    }

    /** Append a crash event — may force a roll for upload readiness. */
    fun appendCrashLine(jsonLine: String) {
        synchronized(writeLock) {
            val file = currentWriteFile()
            if (file.exists() && file.length() >= CRASH_MIN_SIZE) {
                // Force roll so crash gets its own file
            }
            FileWriter(currentWriteFile(), true).use { fw ->
                fw.append(jsonLine); fw.append('\n'); fw.flush()
            }
        }
    }

    /** Mark a file as uploaded (.log → .uploaded). */
    fun markUploaded(file: File) {
        synchronized(writeLock) {
            if (file.name.endsWith(".log") && file.exists()) {
                file.renameTo(File(file.parent, file.name.replace(".log", ".uploaded")))
            }
        }
    }

    /** Get .log files NOT currently being written to. */
    fun pendingUploadFiles(): List<File> {
        val current = currentWriteFileName()
        return logDir.listFiles()
            ?.filter { it.name.endsWith(".log") && it.name != current && it.length() > 0 }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    /** Query local events for parental viewing. */
    fun queryLocal(since: Long, limit: Int): List<LogEvent> {
        val events = mutableListOf<LogEvent>()
        val files = logDir.listFiles()
            ?.filter { it.name.endsWith(".log") || it.name.endsWith(".uploaded") }
            ?.sortedByDescending { it.name }
            ?: return events

        for (file in files) {
            if (events.size >= limit) break
            try {
                BufferedReader(file.reader()).use { reader ->
                    reader.lineSequence().forEach { line ->
                        if (events.size >= limit) return@forEach
                        try {
                            val json = JSONObject(line.trim())
                            if (json.optLong("ts", 0) >= since) {
                                events.add(LogEvent.fromJson(json))
                            }
                        } catch (_: Exception) { /* skip corrupt lines */ }
                    }
                }
            } catch (_: Exception) { /* skip unreadable files */ }
        }
        return events
    }

    // ── Internal ──

    private fun currentWriteFileName(): String {
        val today = dateFormat.format(Date())
        val files = logDir.listFiles()
            ?.filter { it.name.startsWith("events_$today") && it.name.endsWith(".log") }
            ?.sortedByDescending { it.name }
            ?: emptyList()

        val latest = files.firstOrNull()
        return when {
            latest == null -> "events_${today}_001.log"
            latest.length() >= MAX_FILE_SIZE || needsAgeRoll(latest) -> {
                val seq = latest.name.removeSuffix(".log").split("_").last().toIntOrNull() ?: 0
                "events_${today}_%03d.log".format(seq + 1)
            }
            else -> latest.name
        }
    }

    private fun needsAgeRoll(file: File): Boolean =
        (System.currentTimeMillis() - file.lastModified()) >= MAX_FILE_AGE_MS

    private fun currentWriteFile(): File = File(logDir, currentWriteFileName())

    private fun enforceSizeLimit() {
        val allFiles = logDir.listFiles()?.toMutableList() ?: return
        val cutoff = System.currentTimeMillis()

        // 1. .uploaded > 48h
        allFiles.filter { it.name.endsWith(".uploaded") }.forEach { f ->
            if (cutoff - f.lastModified() >= UPLOADED_RETENTION_MS) f.delete()
        }

        // 2. .log > 7 days
        val retentionCutoff = cutoff - RETENTION_DAYS * 24 * 3600_000L
        allFiles.filter { it.name.endsWith(".log") }.forEach { f ->
            if (f.lastModified() < retentionCutoff) f.delete()
        }

        // 3. Hard 50MB cap — delete oldest .uploaded
        val remaining = logDir.listFiles()?.toList() ?: return
        var totalSize = remaining.sumOf { it.length() }
        remaining.filter { it.name.endsWith(".uploaded") }
            .sortedBy { it.lastModified() }
            .forEach { f ->
                if (totalSize <= MAX_TOTAL_SIZE) return@forEach
                totalSize -= f.length(); f.delete()
            }
    }
}
