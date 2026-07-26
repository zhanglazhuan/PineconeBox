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
                .sortedByDescending { it.value }.take(5).map { it.key },
            topDomains = domainVisits.entries
                .sortedByDescending { it.value }.take(5).map { it.key }
        )
    }
}
