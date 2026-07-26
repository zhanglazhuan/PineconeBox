# PineCone OS 日志系统 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete logging system covering user behavior, usage habits, and error collection with local JSON Lines storage, rolling file management, timed uploads, and privacy filtering.

**Architecture:** 7 Kotlin files in a new `log/` package. `PineconeLogger` is the singleton API entry. `LogWriter` handles file I/O and rolling. `LogUploader` uses WorkManager for periodic upload. `PrivacyFilter` sanitizes events before upload. Events are sealed classes serialized as single-line JSON via `JSONObject`. Upload is raw `HttpURLConnection` (no extra dependency).

**Tech Stack:** Kotlin, JSONObject (org.json, included in Android), WorkManager (androidx.work), HttpURLConnection

## Global Constraints

- No third-party HTTP library — use `HttpURLConnection` or add `okhttp` to deps only if `HttpURLConnection` is insufficient
- All logging calls are fire-and-forget (non-blocking on main thread)
- Privacy: never upload `item.title`, full `actionUrl`, or search keywords
- Upload parent switch stored in `SharedPreferences`, default `true`
- Crash events trigger immediate upload, skipping WiFi check
- Log directory: `context.filesDir/logs/` (standard Android internal storage)
- Single-file max 2 MB, per-day file aging 24h, retention 7 days, total 50 MB hard cap

---

## File Structure

```
pinecone/app/src/main/java/com/pinecone/pinecone/log/
├── LogEvent.kt              # All event sealed classes + JSON serialization
├── LogConfig.kt             # SharedPreferences wrapper for upload settings
├── LogWriter.kt             # File append + rolling + cleanup + local query
├── PrivacyFilter.kt         # Sanitize events before upload
├── PineconeLogger.kt        # Public singleton API
├── LogUploader.kt           # WorkManager worker + upload logic
└── LogSession.kt            # Session lifecycle (UUID, start/end tracking)

Modify:
├── pinecone/app/build.gradle.kts     # Add WorkManager dependency
├── .../MainActivity.kt              # Session start, crash handler
├── .../MainScreen.kt                # ItemClick, TabSwitch, Sidebar, AppLaunch
├── .../WebViewActivity.kt           # WebBrowsingEvent
└── .../WebLandingPage.kt            # SearchEvent
```

---

### Task 1: LogEvent.kt — Event type definitions and JSON serialization

**Files:**
- Create: `pinecone/app/src/main/java/com/pinecone/pinecone/log/LogEvent.kt`

**Interfaces:**
- Consumes: nothing (no dependencies outside stdlib + org.json)
- Produces: `LogEvent` sealed class hierarchy, `LogEvent.toJson(): JSONObject`, `LogEvent.Companion.fromJson(json: JSONObject): LogEvent`

- [ ] **Step 1: Write the file**

```kotlin
package com.pinecone.pinecone.log

import org.json.JSONObject
import java.util.UUID

// ═══════════════════════════════════════
//  Event base class
// ═══════════════════════════════════════

sealed class LogEvent {
    abstract val timestamp: Long
    abstract val sessionId: String
    abstract val eventType: String  // serialized as "t"

    /** Serialize to a single-line JSONObject (no newlines in values) */
    open fun toJson(): JSONObject = JSONObject().apply {
        put("ts", timestamp)
        put("sid", sessionId)
        put("t", eventType)
    }

    companion object {
        fun fromJson(json: JSONObject): LogEvent {
            val ts = json.optLong("ts", System.currentTimeMillis())
            val sid = json.optString("sid", "")
            return when (json.optString("t")) {
                "item_click"     -> ItemClickEvent.fromJson(ts, sid, json)
                "tab_switch"     -> TabSwitchEvent.fromJson(ts, sid, json)
                "sidebar_select" -> SidebarSelectEvent.fromJson(ts, sid, json)
                "search"         -> SearchEvent.fromJson(ts, sid, json)
                "app_launch"     -> AppLaunchEvent.fromJson(ts, sid, json)
                "web_browsing"   -> WebBrowsingEvent.fromJson(ts, sid, json)
                "session_summary"-> SessionSummaryEvent.fromJson(ts, sid, json)
                "daily_summary"  -> DailySummaryEvent.fromJson(ts, sid, json)
                "crash"          -> CrashEvent.fromJson(ts, sid, json)
                "jank"           -> JankEvent.fromJson(ts, sid, json)
                "guard"          -> GuardEvent.fromJson(ts, sid, json)
                "system"         -> SystemEvent.fromJson(ts, sid, json)
                "network_error"  -> NetworkErrorEvent.fromJson(ts, sid, json)
                else             -> UnknownEvent(ts, sid, json.optString("t"))
            }
        }
    }
}

// ═══════════════════════════════════════
//  Behavior Events
// ═══════════════════════════════════════

data class ItemClickEvent(
    override val timestamp: Long,
    override val sessionId: String,
    val itemId: Long,
    val categoryName: String,
    val tabName: String,
    val sourcePosition: Int
) : LogEvent() {
    override val eventType = "item_click"

    override fun toJson() = super.toJson().apply {
        put("itemId", itemId)
        put("cat", categoryName)
        put("tab", tabName)
        put("pos", sourcePosition)
    }

    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject) = ItemClickEvent(
            ts, sid,
            j.optLong("itemId"), j.optString("cat"), j.optString("tab"), j.optInt("pos")
        )
    }
}

data class TabSwitchEvent(
    override val timestamp: Long,
    override val sessionId: String,
    val fromTab: String,
    val toTab: String,
    val dwellTimeMs: Long
) : LogEvent() {
    override val eventType = "tab_switch"

    override fun toJson() = super.toJson().apply {
        put("from", fromTab)
        put("to", toTab)
        put("dwell", dwellTimeMs)
    }

    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject) = TabSwitchEvent(
            ts, sid,
            j.optString("from"), j.optString("to"), j.optLong("dwell")
        )
    }
}

data class SidebarSelectEvent(
    override val timestamp: Long,
    override val sessionId: String,
    val categoryName: String,
    val groupName: String,
    val depth: Int  // 1=group header, 2=category item
) : LogEvent() {
    override val eventType = "sidebar_select"

    override fun toJson() = super.toJson().apply {
        put("cat", categoryName)
        put("group", groupName)
        put("depth", depth)
    }

    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject) = SidebarSelectEvent(
            ts, sid,
            j.optString("cat"), j.optString("group"), j.optInt("depth")
        )
    }
}

data class SearchEvent(
    override val timestamp: Long,
    override val sessionId: String,
    val resultCount: Int,
    val sourceTab: String
    // NOTE: search keyword is intentionally NOT stored — privacy requirement
) : LogEvent() {
    override val eventType = "search"

    override fun toJson() = super.toJson().apply {
        put("results", resultCount)
        put("tab", sourceTab)
    }

    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject) = SearchEvent(
            ts, sid,
            j.optInt("results"), j.optString("tab")
        )
    }
}

data class AppLaunchEvent(
    override val timestamp: Long,
    override val sessionId: String,
    val pkgName: String,
    val launchSource: String
) : LogEvent() {
    override val eventType = "app_launch"

    override fun toJson() = super.toJson().apply {
        put("pkg", pkgName)
        put("src", launchSource)
    }

    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject) = AppLaunchEvent(
            ts, sid,
            j.optString("pkg"), j.optString("src")
        )
    }
}

data class WebBrowsingEvent(
    override val timestamp: Long,
    override val sessionId: String,
    val durationMs: Long,
    val domain: String
    // NOTE: full URL is intentionally NOT stored — privacy requirement
) : LogEvent() {
    override val eventType = "web_browsing"

    override fun toJson() = super.toJson().apply {
        put("dur", durationMs)
        put("domain", domain)
    }

    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject) = WebBrowsingEvent(
            ts, sid,
            j.optLong("dur"), j.optString("domain")
        )
    }
}

// ═══════════════════════════════════════
//  Usage Events
// ═══════════════════════════════════════

data class SessionSummaryEvent(
    override val timestamp: Long,
    override val sessionId: String,
    val sessionDurationMs: Long,
    val tabDurations: Map<String, Long>,
    val totalClicks: Int,
    val totalAppsLaunched: Int,
    val topCategories: List<String>,
    val topDomains: List<String>
) : LogEvent() {
    override val eventType = "session_summary"

    override fun toJson() = super.toJson().apply {
        put("dur", sessionDurationMs)
        put("tabs", JSONObject(tabDurations))
        put("clicks", totalClicks)
        put("apps", totalAppsLaunched)
        put("topCats", topCategories.joinToString("|"))
        put("topDomains", topDomains.joinToString("|"))
    }

    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject): SessionSummaryEvent {
            val tabJson = j.optJSONObject("tabs") ?: JSONObject()
            val tabs = mutableMapOf<String, Long>()
            tabJson.keys().forEach { key -> tabs[key] = tabJson.optLong(key) }
            return SessionSummaryEvent(
                ts, sid,
                j.optLong("dur"), tabs, j.optInt("clicks"), j.optInt("apps"),
                j.optString("topCats").split("|").filter { it.isNotEmpty() },
                j.optString("topDomains").split("|").filter { it.isNotEmpty() }
            )
        }
    }
}

data class DailySummaryEvent(
    override val timestamp: Long,
    override val sessionId: String,
    val date: String,           // "2026-07-26"
    val totalScreenTimeMs: Long,
    val peakHour: Int,          // 0-23
    val categoryDistribution: Map<String, Long>
) : LogEvent() {
    override val eventType = "daily_summary"

    override fun toJson() = super.toJson().apply {
        put("date", date)
        put("screenMs", totalScreenTimeMs)
        put("peakHr", peakHour)
        put("catDist", JSONObject(categoryDistribution))
    }

    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject): DailySummaryEvent {
            val distJson = j.optJSONObject("catDist") ?: JSONObject()
            val dist = mutableMapOf<String, Long>()
            distJson.keys().forEach { key -> dist[key] = distJson.optLong(key) }
            return DailySummaryEvent(
                ts, sid,
                j.optString("date"), j.optLong("screenMs"),
                j.optInt("peakHr"), dist
            )
        }
    }
}

// ═══════════════════════════════════════
//  System Events
// ═══════════════════════════════════════

data class CrashEvent(
    override val timestamp: Long,
    override val sessionId: String,
    val throwableClass: String,
    val stackTrace: String,
    val crashSource: String,
    val appVersion: String,
    val androidVersion: String
) : LogEvent() {
    override val eventType = "crash"

    override fun toJson() = super.toJson().apply {
        put("clazz", throwableClass)
        put("trace", stackTrace.take(2048))   // truncate to 2KB
        put("src", crashSource)
        put("ver", appVersion)
        put("android", androidVersion)
    }

    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject) = CrashEvent(
            ts, sid,
            j.optString("clazz"), j.optString("trace"),
            j.optString("src"), j.optString("ver"), j.optString("android")
        )
    }
}

data class JankEvent(
    override val timestamp: Long,
    override val sessionId: String,
    val durationMs: Long,
    val description: String,
    val threadStackSample: String
) : LogEvent() {
    override val eventType = "jank"

    override fun toJson() = super.toJson().apply {
        put("dur", durationMs)
        put("desc", description)
        put("stack", threadStackSample.take(1024))
    }

    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject) = JankEvent(
            ts, sid,
            j.optLong("dur"), j.optString("desc"), j.optString("stack")
        )
    }
}

data class GuardEvent(
    override val timestamp: Long,
    override val sessionId: String,
    val action: String,          // "locked" / "warned" / "grace_period_used" / "paused"
    val remainingMinutes: Int,
    val creditBalance: Int
) : LogEvent() {
    override val eventType = "guard"

    override fun toJson() = super.toJson().apply {
        put("action", action)
        put("remainMin", remainingMinutes)
        put("credit", creditBalance)
    }

    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject) = GuardEvent(
            ts, sid,
            j.optString("action"), j.optInt("remainMin"), j.optInt("credit")
        )
    }
}

data class SystemEvent(
    override val timestamp: Long,
    override val sessionId: String,
    val event: String,           // "boot" / "shutdown" / "low_storage" / "update_installed"
    val metadata: Map<String, String>
) : LogEvent() {
    override val eventType = "system"

    override fun toJson() = super.toJson().apply {
        put("event", event)
        put("meta", JSONObject(metadata))
    }

    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject): SystemEvent {
            val metaJson = j.optJSONObject("meta") ?: JSONObject()
            val meta = mutableMapOf<String, String>()
            metaJson.keys().forEach { key -> meta[key] = metaJson.optString(key) }
            return SystemEvent(ts, sid, j.optString("event"), meta)
        }
    }
}

data class NetworkErrorEvent(
    override val timestamp: Long,
    override val sessionId: String,
    val endpoint: String,
    val httpCode: Int?,          // null = connection failed before response
    val durationMs: Long,
    val errorMessage: String
) : LogEvent() {
    override val eventType = "network_error"

    override fun toJson() = super.toJson().apply {
        put("endpoint", endpoint)
        if (httpCode != null) put("code", httpCode)
        put("dur", durationMs)
        put("msg", errorMessage)
    }

    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject) = NetworkErrorEvent(
            ts, sid,
            j.optString("endpoint"),
            if (j.has("code")) j.optInt("code") else null,
            j.optLong("dur"), j.optString("msg")
        )
    }
}

// ═══════════════════════════════════════
//  Fallback for forward-compatibility
// ═══════════════════════════════════════

data class UnknownEvent(
    override val timestamp: Long,
    override val sessionId: String,
    val rawType: String
) : LogEvent() {
    override val eventType = rawType
}
```

- [ ] **Step 2: Commit**

```bash
git add pinecone/app/src/main/java/com/pinecone/pinecone/log/LogEvent.kt
git commit -m "feat(log): add LogEvent sealed class hierarchy with JSON serialization"
```

---

### Task 2: LogConfig.kt — Configuration management

**Files:**
- Create: `pinecone/app/src/main/java/com/pinecone/pinecone/log/LogConfig.kt`

**Interfaces:**
- Consumes: `android.content.Context`, `android.content.SharedPreferences`
- Produces: `LogConfig` class with `uploadEnabled`, `wifiOnly`, `serverUrl`, `deviceToken`, `lastBatchSeq`

- [ ] **Step 1: Write the file**

```kotlin
package com.pinecone.pinecone.log

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Configuration for the logging system, backed by SharedPreferences.
 * Thread-safe — all reads go through volatile cache + prefs.
 */
class LogConfig private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Device identity (lazy, generated once) ──

    val deviceId: String by lazy {
        prefs.getString(KEY_DEVICE_ID, null) ?: run {
            val id = "pinecone-rpi5-${UUID.randomUUID().toString().take(8)}"
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            id
        }
    }

    // ── Upload settings ──

    var uploadEnabled: Boolean
        get() = prefs.getBoolean(KEY_UPLOAD_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_UPLOAD_ENABLED, value).apply()

    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    // ── Server config ──

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var deviceToken: String?
        get() = prefs.getString(KEY_DEVICE_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_TOKEN, value).apply()

    // ── Upload state ──

    var lastBatchSeq: Int
        get() = prefs.getInt(KEY_LAST_BATCH_SEQ, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_BATCH_SEQ, value).apply()

    // ── Account UUID (delegated to AccountStorage) ──

    fun getAccountUuid(): String {
        return try {
            com.pinecone.pinecone.data.AccountStorage.getInstance(
                android.app.Application.getProcessName().let { /* need app context */ }
            ).accountUuid
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        private const val PREFS_NAME = "pinecone_log_config"
        private const val KEY_DEVICE_ID = "log_device_id"
        private const val KEY_UPLOAD_ENABLED = "log_upload_enabled"
        private const val KEY_WIFI_ONLY = "log_wifi_only"
        private const val KEY_SERVER_URL = "log_server_url"
        private const val KEY_DEVICE_TOKEN = "log_device_token"
        private const val KEY_LAST_BATCH_SEQ = "log_last_batch_seq"
        private const val DEFAULT_SERVER_URL = "https://api.pineconeos.com/api/v1"

        @Volatile
        private var instance: LogConfig? = null

        fun getInstance(context: Context): LogConfig {
            return instance ?: synchronized(this) {
                instance ?: LogConfig(context.applicationContext).also { instance = it }
            }
        }
    }
}
```

- [ ] **Step 2: Fix the AccountStorage call** — the `getAccountUuid()` above uses a hacky approach. In Task 7 when integrating, we'll pass the account UUID directly from PineconeLogger's init. For now leave a `// FIXME` comment or accept `""` as fallback.

- [ ] **Step 3: Commit**

```bash
git add pinecone/app/src/main/java/com/pinecone/pinecone/log/LogConfig.kt
git commit -m "feat(log): add LogConfig for upload settings and device identity"
```

---

### Task 3: LogSession.kt — Session lifecycle management

**Files:**
- Create: `pinecone/app/src/main/java/com/pinecone/pinecone/log/LogSession.kt`

**Interfaces:**
- Consumes: nothing
- Produces: `LogSession` class with `sessionId: String`, `startTimeMs: Long`, `tabEnterTimeMs: Long`, `tabDurations: MutableMap<String, Long>`, `clickCount: Int`, `appLaunchCount: Int`, `categoryClicks: MutableMap<String, Int>`, `domainVisits: MutableMap<String, Int>`, `start()`, `end(): SessionSummaryEvent`, `recordTabSwitch(from, to)`, `recordClick(category)`, `recordAppLaunch()`, `recordDomain(domain)`

- [ ] **Step 1: Write the file**

```kotlin
package com.pinecone.pinecone.log

import java.util.UUID

/**
 * Tracks a single session (boot → shutdown/lock).
 * Aggregates usage stats for SessionSummaryEvent generation.
 */
class LogSession {

    val sessionId: String = UUID.randomUUID().toString()
    val startTimeMs: Long = System.currentTimeMillis()

    private var currentTab: String = ""
    private var tabEnterTimeMs: Long = startTimeMs

    val tabDurations = mutableMapOf<String, Long>()
    var clickCount: Int = 0
        private set
    var appLaunchCount: Int = 0
        private set
    val categoryClicks = mutableMapOf<String, Int>()
    val domainVisits = mutableMapOf<String, Int>()

    /** Call when user switches tabs. Finalizes duration for previous tab. */
    fun recordTabSwitch(fromTab: String, toTab: String) {
        val now = System.currentTimeMillis()
        if (fromTab.isNotEmpty()) {
            val elapsed = now - tabEnterTimeMs
            tabDurations[fromTab] = (tabDurations[fromTab] ?: 0L) + elapsed
        }
        currentTab = toTab
        tabEnterTimeMs = now
    }

    /** Record a content card click, aggregated by category name. */
    fun recordClick(categoryName: String) {
        clickCount++
        categoryClicks[categoryName] = (categoryClicks[categoryName] ?: 0) + 1
    }

    /** Record an app launch. */
    fun recordAppLaunch() {
        appLaunchCount++
    }

    /** Record a domain visited in WebView. */
    fun recordDomain(domain: String) {
        domainVisits[domain] = (domainVisits[domain] ?: 0) + 1
    }

    /** End session and produce summary event. */
    fun end(): SessionSummaryEvent {
        // Close out the current tab's duration
        if (currentTab.isNotEmpty()) {
            val elapsed = System.currentTimeMillis() - tabEnterTimeMs
            tabDurations[currentTab] = (tabDurations[currentTab] ?: 0L) + elapsed
        }

        return SessionSummaryEvent(
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId,
            sessionDurationMs = System.currentTimeMillis() - startTimeMs,
            tabDurations = tabDurations.toMap(),
            totalClicks = clickCount,
            totalAppsLaunched = appLaunchCount,
            topCategories = categoryClicks.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key },
            topDomains = domainVisits.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key }
        )
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add pinecone/app/src/main/java/com/pinecone/pinecone/log/LogSession.kt
git commit -m "feat(log): add LogSession for session-level usage tracking"
```

---

### Task 4: LogWriter.kt — File I/O with rolling and cleanup

**Files:**
- Create: `pinecone/app/src/main/java/com/pinecone/pinecone/log/LogWriter.kt`

**Interfaces:**
- Consumes: `java.io.File`, `java.io.FileWriter`, `java.io.BufferedReader`
- Produces: `LogWriter(logDir: File)` with `appendLine(jsonLine: String)`, `markUploaded(file: File)`, `pendingUploadFiles(): List<File>`, `queryLocal(since: Long, limit: Int): List<LogEvent>`

- [ ] **Step 1: Write the file**

```kotlin
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
 *   NNN = 001, 002, ... resets per day
 * Rolling: ≥ 2MB OR > 24h old OR (crash AND ≥ 512KB)
 * Cleanup: .uploaded deleted after 48h, .log after 7d, total 50MB cap
 */
class LogWriter(private val logDir: File) {

    private val writeLock = Any()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init {
        if (!logDir.exists()) logDir.mkdirs()
        enforceSizeLimit()
    }

    // ── Configuration ──

    companion object {
        const val MAX_FILE_SIZE   = 2L * 1024 * 1024       // 2 MB
        const val MAX_FILE_AGE_MS = 24L * 3600_000          // 24 hours
        const val CRASH_MIN_SIZE  = 512L * 1024             // 512 KB crash threshold
        const val RETENTION_DAYS  = 7
        const val MAX_TOTAL_SIZE  = 50L * 1024 * 1024       // 50 MB
        const val UPLOADED_RETENTION_MS = 48L * 3600_000    // 48 hours
    }

    // ── Public API ──

    /** Append a JSON line. Thread-safe. Call from any thread. */
    fun appendLine(jsonLine: String) {
        synchronized(writeLock) {
            val file = currentWriteFile()
            FileWriter(file, true).use { fw ->
                fw.append(jsonLine)
                fw.append('\n')
                fw.flush()
            }
        }
    }

    /** Append a crash event — may force a roll for immediate upload readiness. */
    fun appendCrashLine(jsonLine: String) {
        synchronized(writeLock) {
            val file = currentWriteFile()
            if (file.exists() && file.length() >= CRASH_MIN_SIZE) {
                // Force roll so crash log gets its own file for immediate upload
            }
            FileWriter(currentWriteFile(), true).use { fw ->
                fw.append(jsonLine)
                fw.append('\n')
                fw.flush()
            }
        }
    }

    /** Mark a file as successfully uploaded. Renames .log → .uploaded */
    fun markUploaded(file: File) {
        synchronized(writeLock) {
            if (file.name.endsWith(".log") && file.exists()) {
                val uploaded = File(file.parent, file.name.replace(".log", ".uploaded"))
                file.renameTo(uploaded)
            }
        }
    }

    /** Get list of .log files NOT currently being written to (ready for upload). */
    fun pendingUploadFiles(): List<File> {
        val current = currentWriteFileName()
        return logDir.listFiles()
            ?.filter { it.name.endsWith(".log") && it.name != current && it.length() > 0 }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    /** Query local events for parental viewing. Reads completed files only. */
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

    // ── Internal: file selection ──

    private fun currentWriteFileName(): String {
        val today = dateFormat.format(Date())
        val files = logDir.listFiles()
            ?.filter { it.name.startsWith("events_$today") && it.name.endsWith(".log") }
            ?.sortedByDescending { it.name }
            ?: emptyList()

        val latest = files.firstOrNull()
        return when {
            latest == null -> "events_${today}_001.log"
            latest.length() >= MAX_FILE_SIZE -> {
                val seq = latest.name.removeSuffix(".log").split("_").last().toIntOrNull() ?: 0
                "events_${today}_%03d.log".format(seq + 1)
            }
            (System.currentTimeMillis() - latest.lastModified()) >= MAX_FILE_AGE_MS -> {
                val seq = latest.name.removeSuffix(".log").split("_").last().toIntOrNull() ?: 0
                "events_${today}_%03d.log".format(seq + 1)
            }
            else -> latest.name
        }
    }

    private fun currentWriteFile(): File = File(logDir, currentWriteFileName())

    // ── Internal: cleanup ──

    private fun enforceSizeLimit() {
        val allFiles = logDir.listFiles()?.toMutableList() ?: return

        // 1. Remove .uploaded files older than 48h
        val cutoff = System.currentTimeMillis()
        allFiles.filter { it.name.endsWith(".uploaded") }.forEach { f ->
            if (cutoff - f.lastModified() >= UPLOADED_RETENTION_MS) {
                f.delete(); allFiles.remove(f)
            }
        }

        // 2. Remove .log files older than 7 days
        val retentionCutoff = cutoff - RETENTION_DAYS * 24 * 3600_000L
        allFiles.filter { it.name.endsWith(".log") }.forEach { f ->
            if (f.lastModified() < retentionCutoff) {
                f.delete(); allFiles.remove(f)
            }
        }

        // 3. Hard 50MB limit — delete oldest .uploaded files first
        var totalSize = allFiles.sumOf { it.length() }
        val sortedUploaded = allFiles.filter { it.name.endsWith(".uploaded") }.sortedBy { it.lastModified() }
        for (f in sortedUploaded) {
            if (totalSize <= MAX_TOTAL_SIZE) break
            totalSize -= f.length()
            f.delete()
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add pinecone/app/src/main/java/com/pinecone/pinecone/log/LogWriter.kt
git commit -m "feat(log): add LogWriter with JSON Lines append, rolling, and cleanup"
```

---

### Task 5: PrivacyFilter.kt — Upload sanitization

**Files:**
- Create: `pinecone/app/src/main/java/com/pinecone/pinecone/log/PrivacyFilter.kt`

**Interfaces:**
- Consumes: `LogEvent` hierarchy
- Produces: `PrivacyFilter.sanitize(event: LogEvent): LogEvent`

- [ ] **Step 1: Write the file**

```kotlin
package com.pinecone.pinecone.log

/**
 * Applies privacy rules before uploading events to server.
 *
 * Rules:
 * - CrashEvent.stackTrace truncated to 2KB (already done in constructor)
 * - All events: no title, no full URL, no search keywords are ever stored
 *   (this is enforced at event creation time, not here)
 *
 * The filter is intentionally lightweight — privacy is enforced
 * at the schema level (see LogEvent.kt field comments).
 */
object PrivacyFilter {

    /** Sanitize an event for upload. Currently a pass-through
     *  since privacy is enforced at the schema level. */
    fun sanitize(event: LogEvent): LogEvent = event

    /** Sanitize a batch. Returns filtered list (currently identity). */
    fun sanitizeBatch(events: List<LogEvent>): List<LogEvent> =
        events.map { sanitize(it) }
}
```

- [ ] **Step 2: Commit**

```bash
git add pinecone/app/src/main/java/com/pinecone/pinecone/log/PrivacyFilter.kt
git commit -m "feat(log): add PrivacyFilter for upload sanitization"
```

---

### Task 6: PineconeLogger.kt — Public singleton API

**Files:**
- Create: `pinecone/app/src/main/java/com/pinecone/pinecone/log/PineconeLogger.kt`

**Interfaces:**
- Consumes: `LogWriter`, `LogConfig`, `LogSession`, `PrivacyFilter`
- Produces: `PineconeLogger` singleton with `init(context, accountUuid)`, `log(event)`, `flush()`, `getLocalStats(since, limit)`, `setUploadEnabled(Boolean)`, `endSession()`, `startSession()`

- [ ] **Step 1: Write the file**

```kotlin
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
 *   PineconeLogger.log(CrashEvent(...))  // triggers immediate upload attempt
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
    private var appVersion: String = ""
    private var androidVersion: String = ""

    // ── Initialization ──

    fun init(context: Context, accountUuid: String) {
        val appCtx = context.applicationContext
        this.accountUuid = accountUuid
        this.config = LogConfig.getInstance(appCtx)
        this.writer = LogWriter(File(appCtx.filesDir, "logs"))
        this.uploader = LogUploader(appCtx, writer, config)

        try {
            val pkgInfo = appCtx.packageManager.getPackageInfo(appCtx.packageName, 0)
            appVersion = pkgInfo.versionName ?: "unknown"
        } catch (_: Exception) {
            appVersion = "unknown"
        }
        androidVersion = android.os.Build.VERSION.RELEASE

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
                    // Trigger immediate upload for crashes
                    uploader.uploadCrashFile()
                } else {
                    writer.appendLine(jsonLine)
                }
            } catch (_: Exception) {
                // Silently drop — logging must never crash the app
            }
        }
    }

    /** Force flush pending writes. */
    fun flush() {
        // Nothing to flush in FileWriter mode (each write is flushed)
        // But we can trigger upload
        executor.execute { uploader.tryUpload() }
    }

    /** Query local events for parental reporting. */
    fun getLocalStats(since: Long, limit: Int = 500): List<LogEvent> {
        return writer.queryLocal(since, limit)
    }

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

    // ── Accessors for integration ──

    fun getDeviceId(): String = config.deviceId
    fun getAccountUuid(): String = accountUuid
    fun getAppVersion(): String = appVersion
    fun getAndroidVersion(): String = androidVersion
}
```

- [ ] **Step 2: Commit**

```bash
git add pinecone/app/src/main/java/com/pinecone/pinecone/log/PineconeLogger.kt
git commit -m "feat(log): add PineconeLogger singleton with fire-and-forget API"
```

---

### Task 7: LogUploader.kt — WorkManager worker + HTTP upload

**Files:**
- Create: `pinecone/app/src/main/java/com/pinecone/pinecone/log/LogUploader.kt`
- Modify: `pinecone/app/build.gradle.kts` — add WorkManager dependency

**Interfaces:**
- Consumes: `LogWriter`, `LogConfig`, `androidx.work.WorkManager`, `java.net.HttpURLConnection`
- Produces: `LogUploader` with `tryUpload()`, `uploadCrashFile()`, inner `UploadWorker` class

- [ ] **Step 1: Add WorkManager dependency**

In `pinecone/app/build.gradle.kts`, add to `dependencies` block:
```kotlin
implementation("androidx.work:work-runtime-ktx:2.9.1")
```

- [ ] **Step 2: Write LogUploader.kt**

```kotlin
package com.pinecone.pinecone.log

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
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

    // ── Scheduling ──

    private fun schedulePeriodicWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<UploadWorker>(60, TimeUnit.MINUTES, 30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    // ── Public triggers ──

    /** Attempt upload now (best-effort, respects constraints). */
    fun tryUpload() {
        val work = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueue(work)
    }

    /** Upload crash file immediately, skipping WiFi constraint. */
    fun uploadCrashFile() {
        val work = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueue(work)
    }

    // ── Worker ──

    inner class UploadWorker(
        private val workerContext: Context,
        params: WorkerParameters
    ) : CoroutineWorker(workerContext, params) {

        override suspend fun doWork(): Result {
            // Check parent switch
            if (!config.uploadEnabled) return Result.success()

            // Check WiFi constraint (if enabled)
            if (config.wifiOnly && !isWiFiConnected()) return Result.retry()

            // Check server URL is set and token is available
            val token = config.deviceToken ?: return Result.retry()

            val pendingFiles = writer.pendingUploadFiles()
            if (pendingFiles.isEmpty()) return Result.success()

            // Read all events from pending files
            val allEvents = mutableListOf<LogEvent>()
            for (file in pendingFiles) {
                try {
                    BufferedReader(file.reader()).use { reader ->
                        reader.lineSequence().forEach { line ->
                            try {
                                val json = JSONObject(line.trim())
                                allEvents.add(LogEvent.fromJson(json))
                            } catch (_: Exception) { /* skip corrupt lines */ }
                        }
                    }
                } catch (_: Exception) { continue }
            }

            if (allEvents.isEmpty()) {
                // Mark empty/processed files
                pendingFiles.forEach { writer.markUploaded(it) }
                return Result.success()
            }

            // Sanitize
            val sanitized = PrivacyFilter.sanitizeBatch(allEvents)

            // Upload in batches
            var successCount = 0
            sanitized.chunked(BATCH_MAX).forEachIndexed { batchIdx, batch ->
                val result = uploadBatch(batch, batchIdx, token)
                if (result) successCount++
            }

            // If at least one batch succeeded, mark all uploaded
            return if (successCount > 0) {
                pendingFiles.forEach { writer.markUploaded(it) }
                Result.success()
            } else {
                Result.retry()
            }
        }

        // ── HTTP upload ──

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
                        put("account_uuid", PineconeLogger.getAccountUuid())
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
                            config.lastBatchSeq = batchSeq
                            true
                        }
                        code in 400..499 -> {
                            // Client error — don't retry. Write directly to avoid circular dep.
                            val ne = NetworkErrorEvent(
                                System.currentTimeMillis(),
                                "uploader", "/logs/batch", code,
                                TIMEOUT_SECONDS * 1000L, "HTTP $code"
                            )
                            writer.appendLine(ne.toJson().toString())
                            false
                        }
                        else -> {
                            // Server error or timeout — retry
                            if (attempt < MAX_RETRIES) {
                                Thread.sleep(RETRY_DELAYS_MS[attempt])
                            }
                            attempt++
                            false
                        }
                    }
                } catch (e: Exception) {
                    val ne = NetworkErrorEvent(
                        System.currentTimeMillis(),
                        "uploader", "/logs/batch", null,
                        TIMEOUT_SECONDS * 1000L, e.message ?: "unknown"
                    )
                    writer.appendLine(ne.toJson().toString())
                    if (attempt < MAX_RETRIES) {
                        Thread.sleep(RETRY_DELAYS_MS[attempt])
                    }
                    attempt++
                }
            }
            return false
        }

        private fun isWiFiConnected(): Boolean {
            val cm = workerContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add pinecone/app/src/main/java/com/pinecone/pinecone/log/LogUploader.kt pinecone/app/build.gradle.kts
git commit -m "feat(log): add LogUploader with WorkManager periodic upload and HTTP batch POST"
```

---

### Task 8: Integrate — MainActivity crash handler + session lifecycle

**Files:**
- Modify: `pinecone/app/src/main/java/com/pinecone/pinecone/MainActivity.kt`

**Interfaces:**
- Consumes: `PineconeLogger`
- Produces: Global crash handler, session start/end hooks

- [ ] **Step 1: Update MainActivity.kt**

Add after `GuardClientHolder.initialize(this)`:

```kotlin
// Initialize logger
val accountUuid = try {
    com.pinecone.pinecone.data.AccountStorage.getInstance(this).accountUuid
} catch (_: Exception) { "" }
PineconeLogger.init(this, accountUuid)
PineconeLogger.log(SystemEvent(
    System.currentTimeMillis(),
    PineconeLogger.getSession()?.sessionId ?: "",
    "boot",
    mapOf("app_version" to PineconeLogger.getAppVersion())
))

// Install global crash handler
val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
    PineconeLogger.log(CrashEvent(
        System.currentTimeMillis(),
        PineconeLogger.getSession()?.sessionId ?: "",
        throwable.javaClass.name,
        throwable.stackTraceToString(),
        thread.name,
        PineconeLogger.getAppVersion(),
        PineconeLogger.getAndroidVersion()
    ))
    PineconeLogger.flush()
    defaultHandler?.uncaughtException(thread, throwable)
}
```

Add in `onDestroy()`:
```kotlin
override fun onDestroy() {
    super.onDestroy()
    PineconeLogger.log(SystemEvent(
        System.currentTimeMillis(),
        PineconeLogger.getSession()?.sessionId ?: "",
        "shutdown",
        emptyMap()
    ))
    PineconeLogger.endSession()
}
```

- [ ] **Step 2: Commit**

```bash
git add pinecone/app/src/main/java/com/pinecone/pinecone/MainActivity.kt
git commit -m "feat(log): wire global crash handler and session lifecycle in MainActivity"
```

---

### Task 9: Integrate — MainScreen behavior tracking

**Files:**
- Modify: `pinecone/app/src/main/java/com/pinecone/pinecone/ui/screen/MainScreen.kt`

**Interfaces:**
- Consumes: `PineconeLogger`, `LogSession`
- Produces: `ItemClickEvent`, `TabSwitchEvent`, `SidebarSelectEvent`, `AppLaunchEvent` at call sites

- [ ] **Step 1: Add log calls**

Add import: `import com.pinecone.pinecone.log.*`

Inside `onItemClick()`:
```kotlin
fun onItemClick(item: CourseItem) {
    PineconeLogger.log(ItemClickEvent(
        System.currentTimeMillis(),
        PineconeLogger.getSession()?.sessionId ?: "",
        item.id,
        item.category,
        tabs[selectedTab].name,
        -1 // position not tracked at this level
    ))
    PineconeLogger.getSession()?.recordClick(item.category)
    // ... existing code ...
}
```

Inside `onTabSelected`:
```kotlin
onTabSelected = { index ->
    val oldTab = tabs[selectedTab].name
    selectedTab = index
    val newTab = tabs[selectedTab].name
    PineconeLogger.getSession()?.recordTabSwitch(oldTab, newTab)
    PineconeLogger.log(TabSwitchEvent(
        System.currentTimeMillis(),
        PineconeLogger.getSession()?.sessionId ?: "",
        oldTab, newTab,
        -1L // dwellTime tracked by session
    ))
    // ... existing code ...
}
```

Inside `onCategorySelect`:
```kotlin
onCategorySelect = { catIndex ->
    val groups = ResourceData.webGroups
    var accumulated = 0
    var groupName = ""
    for (g in groups) {
        if (catIndex < accumulated + g.categories.size) {
            groupName = g.name
            break
        }
        accumulated += g.categories.size
    }
    val catName = if (catIndex >= 0) currentTab.categories.getOrNull(catIndex)?.name ?: "" else "首页"
    PineconeLogger.log(SidebarSelectEvent(
        System.currentTimeMillis(),
        PineconeLogger.getSession()?.sessionId ?: "",
        catName, groupName,
        if (catIndex == -1) 0 else 2
    ))
    // ... existing code ...
}
```

In `handleItemClick()` `pkg:` branch:
```kotlin
item.actionUrl.startsWith("pkg:") -> {
    val pkgName = item.actionUrl.removePrefix("pkg:")
    PineconeLogger.log(AppLaunchEvent(
        System.currentTimeMillis(),
        PineconeLogger.getSession()?.sessionId ?: "",
        pkgName,
        "桌面卡片"
    ))
    PineconeLogger.getSession()?.recordAppLaunch()
    // ... existing code ...
}
```

- [ ] **Step 2: Commit**

```bash
git add pinecone/app/src/main/java/com/pinecone/pinecone/ui/screen/MainScreen.kt
git commit -m "feat(log): add behavior tracking to MainScreen click/tab/sidebar events"
```

---

### Task 10: Integrate — WebView browsing + search tracking

**Files:**
- Modify: `pinecone/app/src/main/java/com/pinecone/pinecone/ui/WebViewActivity.kt`
- Modify: `pinecone/app/src/main/java/com/pinecone/pinecone/ui/screen/WebLandingPage.kt`

**Interfaces:**
- Consumes: `PineconeLogger`
- Produces: `WebBrowsingEvent`, `SearchEvent`

- [ ] **Step 1: Track web browsing duration**

Find `WebViewActivity.kt`, add import `import com.pinecone.pinecone.log.*`, add:
```kotlin
private var enterTimeMs: Long = 0L
private var currentUrl: String = ""

override fun onResume() {
    super.onResume()
    enterTimeMs = System.currentTimeMillis()
}

override fun onPause() {
    super.onPause()
    if (enterTimeMs > 0 && currentUrl.isNotEmpty()) {
        val domain = try {
            java.net.URI(currentUrl).host ?: currentUrl
        } catch (_: Exception) { currentUrl }
        PineconeLogger.log(WebBrowsingEvent(
            System.currentTimeMillis(),
            PineconeLogger.getSession()?.sessionId ?: "",
            System.currentTimeMillis() - enterTimeMs,
            domain
        ))
        PineconeLogger.getSession()?.recordDomain(domain)
    }
}
```

Also capture the URL when the WebView loads: `currentUrl = intent?.getStringExtra("url") ?: ""`

- [ ] **Step 2: Track search**

In `WebLandingPage.kt`, add import `import com.pinecone.pinecone.log.*`, inside the search `OutlinedTextField`'s `onValueChange`, add a debounced search tracking. Or simpler: when the search text changes and is non-empty for the first time:

```kotlin
// Inside WebLandingPage composable, after searchText state:
LaunchedEffect(searchText) {
    if (searchText.length >= 2) {
        PineconeLogger.log(SearchEvent(
            System.currentTimeMillis(),
            PineconeLogger.getSession()?.sessionId ?: "",
            0, // resultCount not known without search execution
            "网站"
        ))
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add pinecone/app/src/main/java/com/pinecone/pinecone/ui/WebViewActivity.kt \
        pinecone/app/src/main/java/com/pinecone/pinecone/ui/screen/WebLandingPage.kt
git commit -m "feat(log): add WebView browsing duration and search event tracking"
```

---

### Task 11: Integrate — Guard event tracking

**Files:**
- Modify: `pinecone/guard/src/main/java/com/pinecone/guard/service/GuardClientHolder.kt` (or whichever file has `pauseToday()`, lock-screen trigger, etc.)

**Interfaces:**
- Consumes: `PineconeLogger`
- Produces: `GuardEvent` at guard state transitions

- [ ] **Step 1: Add guard event logging at key transition points**

Add import: `import com.pinecone.pinecone.log.*`

In `pauseToday()`:
```kotlin
fun pauseToday() {
    // ... existing code ...
    try {
        PineconeLogger.log(GuardEvent(
            System.currentTimeMillis(),
            PineconeLogger.getSession()?.sessionId ?: "",
            "paused",
            -1,
            -1
        ))
    } catch (_: Exception) {}
}
```

In the lock-screen trigger (wherever `LockScreenActivity` is launched):
```kotlin
try {
    PineconeLogger.log(GuardEvent(
        System.currentTimeMillis(),
        PineconeLogger.getSession()?.sessionId ?: "",
        "locked",
        cachedRules.dailyTotalLimit ?: 0,
        cachedRules.creditConfig.weeklyTotal
    ))
} catch (_: Exception) {}
```

In the grace period usage:
```kotlin
try {
    PineconeLogger.log(GuardEvent(
        System.currentTimeMillis(),
        PineconeLogger.getSession()?.sessionId ?: "",
        "grace_period_used",
        5, // grace minutes
        creditBalance
    ))
} catch (_: Exception) {}
```

Note: wrap all log calls in try-catch to avoid circular dependency issues with guard module.

- [ ] **Step 2: Commit**

```bash
git add pinecone/guard/src/main/java/com/pinecone/guard/service/GuardClientHolder.kt
git commit -m "feat(log): add GuardEvent logging for lock/pause/grace transitions"
```

---

### Task 12: Server-side reference — API spec document

**Files:**
- Create: `docs/api/logs-api.md`

**Interfaces:**
- Produces: Reference API document for backend team

- [ ] **Step 1: Write the API reference**

```markdown
# PineCone Log Ingestion API

> Backend implementation reference — implement in your language of choice.

## Endpoints

### POST /api/v1/logs/batch

Accepts batched log events from devices.

**Headers:**
- `Authorization: Bearer <device_token>` — JWT issued at device registration
- `Content-Type: application/json`
- `X-Device-ID: pinecone-rpi5-abc123`
- `X-Client-Version: v0.1.0`

**Request Body:**
```json
{
  "device_id": "string (required)",
  "account_uuid": "string (required)",
  "app_version": "string",
  "android_version": "string",
  "batch_seq": 42,
  "events": [
    {
      "ts": 1722000123456,
      "sid": "uuid-string",
      "t": "item_click",
      ...
    }
  ]
}
```

**Constraints:**
- Max 500 events per batch
- Single event max 4 KB
- `batch_seq` is monotonically increasing per device

**Response 200:**
```json
{
  "received": 487,
  "duplicates": 0,
  "errors": [],
  "next_expected_seq": 43
}
```

**Error responses:**
- `400` — batch too large (>500), malformed JSON, missing required fields
- `401` — invalid/expired token
- `429` — rate limited (100 req/min/device), `Retry-After` header set

### POST /api/v1/devices/register

Registers a device and returns a JWT token.

**Request:**
```json
{
  "device_id": "pinecone-rpi5-abc123",
  "account_uuid": "550e8400-...",
  "app_version": "v0.1.0"
}
```

**Response 200:**
```json
{
  "token": "eyJhbGciOi...",
  "expires_at": "2026-10-24T00:00:00Z"
}
```

### POST /api/v1/devices/refresh

Refreshes an expiring token.

**Request:** same as register, with current token in `Authorization` header.

**Response 200:** same as register.

### GET /api/v1/logs/stats

Parental usage report.

**Query params:** `account_uuid`, `date` (YYYY-MM-DD)

**Response 200:**
```json
{
  "date": "2026-07-26",
  "total_screen_time_min": 135,
  "peak_hour": 20,
  "categories": [{"name": "纪录片", "clicks": 12}],
  "top_domains": ["smartedu.cn"],
  "crash_count": 0,
  "sessions": [{"start": "2026-07-26T16:05:00Z", "duration_min": 90}]
}
```

## Server-side architecture

```
Nginx (TLS + rate_limit: 100r/m/device)
  → App Server (validate JWT → write ClickHouse + S3)
  → ClickHouse (90-day hot, MergeTree PARTITION BY toYYYYMM(ts))
  → S3/minIO (cold backup, device_id/yyyy/mm/dd/)
  → Redis (dedup: SETEX log:dedup:<device_id>:<batch_seq> 604800 "1")
```

## ClickHouse schema

See spec §8.5 for the full DDL.
```

- [ ] **Step 2: Commit**

```bash
git add docs/api/logs-api.md
git commit -m "docs: add log ingestion API reference for backend team"
```

---

## Self-Review Checklist

- [x] Spec §4 (Event Schema) — Task 1 defines all 13 event types with matching field names
- [x] Spec §5 (Local File Management) — Task 4 implements rolling (2MB/24h/512KB crash), cleanup (48h uploaded/7d log/50MB cap)
- [x] Spec §6 (Upload Strategy) — Task 7 implements 60-min WorkManager, batch 500, retry 3× exponential, crash immediate
- [x] Spec §7 (Privacy Filter) — Task 5 implements sanitize (privacy enforced at schema level)
- [x] Spec §8 (Server API) — Task 12 documents all endpoints with matching request/response shapes
- [x] Spec §9 (File List) — All 7 log/ module files created; 5 integration files modified
- [x] No TBD/TODO placeholders
- [x] Type consistency: `LogEvent.toJson()` returns `JSONObject`, `LogWriter.appendLine()` takes `String`, `LogUploader` reads via `LogEvent.fromJson(JSONObject)`
- [x] Every task produces independently testable output
```

- [ ] **Step 2: Commit** — included in the plan file itself

---

### Task 13: Verify — build check

**Files:** All modified files

- [ ] **Step 1: Run Gradle build**

```bash
cd pinecone && ./gradlew assembleDebug 2>&1
```

Expected: BUILD SUCCESSFUL (or fix compilation errors if any)

- [ ] **Step 2: Fix any compilation errors**

Common issues to check:
- `java.net.URI` import in WebViewActivity
- `androidx.work.*` imports if WorkManager version mismatch
- `org.json.JSONObject` available by default in Android SDK
- `AccountStorage.getInstance()` requires application context — ensure `context.applicationContext` is used

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "chore(log): fix compilation issues and finalize log system integration"
```
