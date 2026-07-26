package com.pinecone.pinecone.log

import org.json.JSONObject

// ═══════════════════════════════════════
//  Event base class
// ═══════════════════════════════════════

sealed class LogEvent {
    abstract val timestamp: Long
    abstract val sessionId: String
    abstract val eventType: String

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
                "item_click"      -> ItemClickEvent.fromJson(ts, sid, json)
                "tab_switch"      -> TabSwitchEvent.fromJson(ts, sid, json)
                "sidebar_select"  -> SidebarSelectEvent.fromJson(ts, sid, json)
                "search"          -> SearchEvent.fromJson(ts, sid, json)
                "app_launch"      -> AppLaunchEvent.fromJson(ts, sid, json)
                "web_browsing"    -> WebBrowsingEvent.fromJson(ts, sid, json)
                "session_summary" -> SessionSummaryEvent.fromJson(ts, sid, json)
                "daily_summary"   -> DailySummaryEvent.fromJson(ts, sid, json)
                "crash"           -> CrashEvent.fromJson(ts, sid, json)
                "jank"            -> JankEvent.fromJson(ts, sid, json)
                "guard"           -> GuardEvent.fromJson(ts, sid, json)
                "system"          -> SystemEvent.fromJson(ts, sid, json)
                "network_error"   -> NetworkErrorEvent.fromJson(ts, sid, json)
                else              -> UnknownEvent(ts, sid, json.optString("t"))
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
        put("itemId", itemId); put("cat", categoryName)
        put("tab", tabName); put("pos", sourcePosition)
    }
    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject) = ItemClickEvent(
            ts, sid, j.optLong("itemId"), j.optString("cat"),
            j.optString("tab"), j.optInt("pos")
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
        put("from", fromTab); put("to", toTab); put("dwell", dwellTimeMs)
    }
    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject) = TabSwitchEvent(
            ts, sid, j.optString("from"), j.optString("to"), j.optLong("dwell")
        )
    }
}

data class SidebarSelectEvent(
    override val timestamp: Long,
    override val sessionId: String,
    val categoryName: String,
    val groupName: String,
    val depth: Int
) : LogEvent() {
    override val eventType = "sidebar_select"
    override fun toJson() = super.toJson().apply {
        put("cat", categoryName); put("group", groupName); put("depth", depth)
    }
    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject) = SidebarSelectEvent(
            ts, sid, j.optString("cat"), j.optString("group"), j.optInt("depth")
        )
    }
}

data class SearchEvent(
    override val timestamp: Long,
    override val sessionId: String,
    val resultCount: Int,
    val sourceTab: String
) : LogEvent() {
    override val eventType = "search"
    override fun toJson() = super.toJson().apply {
        put("results", resultCount); put("tab", sourceTab)
    }
    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject) = SearchEvent(
            ts, sid, j.optInt("results"), j.optString("tab")
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
        put("pkg", pkgName); put("src", launchSource)
    }
    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject) = AppLaunchEvent(
            ts, sid, j.optString("pkg"), j.optString("src")
        )
    }
}

data class WebBrowsingEvent(
    override val timestamp: Long,
    override val sessionId: String,
    val durationMs: Long,
    val domain: String
) : LogEvent() {
    override val eventType = "web_browsing"
    override fun toJson() = super.toJson().apply {
        put("dur", durationMs); put("domain", domain)
    }
    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject) = WebBrowsingEvent(
            ts, sid, j.optLong("dur"), j.optString("domain")
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
        put("clicks", totalClicks); put("apps", totalAppsLaunched)
        put("topCats", topCategories.joinToString("|"))
        put("topDomains", topDomains.joinToString("|"))
    }
    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject): SessionSummaryEvent {
            val tabJson = j.optJSONObject("tabs") ?: JSONObject()
            val tabs = mutableMapOf<String, Long>()
            tabJson.keys().forEach { key -> tabs[key] = tabJson.optLong(key) }
            return SessionSummaryEvent(
                ts, sid, j.optLong("dur"), tabs, j.optInt("clicks"),
                j.optInt("apps"),
                j.optString("topCats").split("|").filter { it.isNotEmpty() },
                j.optString("topDomains").split("|").filter { it.isNotEmpty() }
            )
        }
    }
}

data class DailySummaryEvent(
    override val timestamp: Long,
    override val sessionId: String,
    val date: String,
    val totalScreenTimeMs: Long,
    val peakHour: Int,
    val categoryDistribution: Map<String, Long>
) : LogEvent() {
    override val eventType = "daily_summary"
    override fun toJson() = super.toJson().apply {
        put("date", date); put("screenMs", totalScreenTimeMs)
        put("peakHr", peakHour); put("catDist", JSONObject(categoryDistribution))
    }
    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject): DailySummaryEvent {
            val distJson = j.optJSONObject("catDist") ?: JSONObject()
            val dist = mutableMapOf<String, Long>()
            distJson.keys().forEach { key -> dist[key] = distJson.optLong(key) }
            return DailySummaryEvent(ts, sid, j.optString("date"),
                j.optLong("screenMs"), j.optInt("peakHr"), dist)
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
        put("trace", stackTrace.take(2048))
        put("src", crashSource); put("ver", appVersion)
        put("android", androidVersion)
    }
    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject) = CrashEvent(
            ts, sid, j.optString("clazz"), j.optString("trace"),
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
        put("dur", durationMs); put("desc", description)
        put("stack", threadStackSample.take(1024))
    }
    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject) = JankEvent(
            ts, sid, j.optLong("dur"), j.optString("desc"), j.optString("stack")
        )
    }
}

data class GuardEvent(
    override val timestamp: Long,
    override val sessionId: String,
    val action: String,
    val remainingMinutes: Int,
    val creditBalance: Int
) : LogEvent() {
    override val eventType = "guard"
    override fun toJson() = super.toJson().apply {
        put("action", action); put("remainMin", remainingMinutes)
        put("credit", creditBalance)
    }
    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject) = GuardEvent(
            ts, sid, j.optString("action"), j.optInt("remainMin"), j.optInt("credit")
        )
    }
}

data class SystemEvent(
    override val timestamp: Long,
    override val sessionId: String,
    val event: String,
    val metadata: Map<String, String>
) : LogEvent() {
    override val eventType = "system"
    override fun toJson() = super.toJson().apply {
        put("event", event); put("meta", JSONObject(metadata))
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
    val httpCode: Int?,
    val durationMs: Long,
    val errorMessage: String
) : LogEvent() {
    override val eventType = "network_error"
    override fun toJson() = super.toJson().apply {
        put("endpoint", endpoint)
        if (httpCode != null) put("code", httpCode)
        put("dur", durationMs); put("msg", errorMessage)
    }
    companion object {
        fun fromJson(ts: Long, sid: String, j: JSONObject) = NetworkErrorEvent(
            ts, sid, j.optString("endpoint"),
            if (j.has("code")) j.optInt("code") else null,
            j.optLong("dur"), j.optString("msg")
        )
    }
}

data class UnknownEvent(
    override val timestamp: Long,
    override val sessionId: String,
    val rawType: String
) : LogEvent() {
    override val eventType = rawType
}
