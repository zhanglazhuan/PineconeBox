package com.pinecone.guard.data

import android.app.usage.UsageStatsManager
import android.content.Context
import java.time.LocalDate
import java.time.ZoneId

data class TodayUsage(
    val totalSeconds: Long,
    val categoryUsage: Map<String, Long>,
    val appUsage: Map<String, Long>
)

class UsageTracker(context: Context) {

    private val usageManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    private val pkgCategoryMap = mapOf(
        "com.example.english1" to "english",
        "com.example.cambridge" to "english",
        "com.youdao.dict" to "apps",
        "tv.danmaku.bili" to "apps"
    )

    fun getTodayUsage(): TodayUsage {
        val todayStart = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        val stats = usageManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, todayStart, now
        )

        var total = 0L
        val byApp = mutableMapOf<String, Long>()
        val byCat = mutableMapOf<String, Long>()

        var pkgCount = 0
        stats.forEach { stat ->
            val secs = stat.totalTimeInForeground / 1000
            if (secs > 0) {
                total += secs
                byApp[stat.packageName] = (byApp[stat.packageName] ?: 0) + secs
                val cat = pkgCategoryMap[stat.packageName] ?: "other"
                byCat[cat] = (byCat[cat] ?: 0) + secs
                pkgCount++
            }
        }

        android.util.Log.d("UsageTracker", "getTodayUsage | statsCount=${stats.size} | pkgWithUsage=$pkgCount | totalSecs=$total | topPkgs=${byApp.entries.sortedByDescending { it.value }.take(3)}")

        return TodayUsage(totalSeconds = total, categoryUsage = byCat, appUsage = byApp)
    }

    fun getAppUsage(packageName: String): Long {
        val todayStart = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val stats = usageManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, todayStart, System.currentTimeMillis()
        )
        return stats.find { it.packageName == packageName }?.totalTimeInForeground?.div(1000) ?: 0
    }

    fun isPermissionGranted(): Boolean {
        val now = System.currentTimeMillis()
        val stats = usageManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, now - 1000, now
        )
        return stats.isNotEmpty()
    }
}
