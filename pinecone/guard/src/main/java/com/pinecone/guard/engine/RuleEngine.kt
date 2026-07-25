package com.pinecone.guard.engine

import com.pinecone.guard.data.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class RuleResult(
    val shouldLock: Boolean = false,
    val lockReason: LockReason? = null,
    val shouldWarn: Boolean = false,
    val warnMessage: String? = null,
    val remainingSeconds: Long? = null,
    val isAppBlocked: Boolean = false,
    val blockedAppMessage: String? = null,
    val categoryBlocked: Set<String> = emptySet(),
    val isBreakDue: Boolean = false,
    val graceSecondsAvailable: Long = 0
) {
    companion object {
        val ALLOWED = RuleResult()
    }
}

class RuleEngine {

    fun evaluate(
        rules: RuleSet,
        snapshot: UsageSnapshot,
        currentAppPkg: String?,
        currentAppCategory: String?,
        creditBalance: Int,
        now: LocalTime = LocalTime.now(),
        today: LocalDate = LocalDate.now()
    ): RuleResult {
        if (rules.pausedForToday != null) return RuleResult.ALLOWED

        // 1. Time window check (highest priority)
        if (!isInTimeWindow(rules.timeWindows, today.dayOfWeek, now)) {
            return RuleResult(
                shouldLock = true,
                lockReason = LockReason.OUTSIDE_TIME_WINDOW
            )
        }

        // 2. Break check
        val breakRule = rules.breakRule
        if (breakRule != null) {
            val continuousMin = snapshot.continuousSeconds / 60
            if (continuousMin >= breakRule.usageMinutes) {
                return RuleResult(isBreakDue = true)
            }
        }

        // 3. App-level check
        if (currentAppPkg != null) {
            val appLimit = rules.appLimits.find { it.packageName == currentAppPkg }
            if (appLimit != null) {
                val appSecs = snapshot.appUsage[currentAppPkg] ?: 0
                val limitSecs = appLimit.dailyMinutes * 60L
                if (appSecs >= limitSecs) {
                    return RuleResult(
                        isAppBlocked = true,
                        blockedAppMessage = "${appLimit.appLabel} 今日时间已用完（${appLimit.dailyMinutes}/${appLimit.dailyMinutes}分钟）"
                    )
                }
            }
        }

        // 4. Category-level check
        if (currentAppCategory != null) {
            val catLimit = rules.categoryLimits.find { it.categoryId == currentAppCategory }
            if (catLimit != null && catLimit.dailyMinutes != null) {
                val catSecs = snapshot.categoryUsage[currentAppCategory] ?: 0
                val limitSecs = catLimit.dailyMinutes * 60L
                if (catSecs >= limitSecs) {
                    return RuleResult(categoryBlocked = setOf(currentAppCategory))
                }
            }
        }

        // 5. Daily total check (lowest priority)
        val dailyLimit = rules.dailyTotalLimit
        if (dailyLimit != null) {
            val limitSecs = dailyLimit * 60L
            val used = snapshot.totalSeconds
            val remaining = limitSecs - used

            return when {
                remaining <= 0 -> {
                    if (creditBalance > 0) {
                        val graceSecs = (creditBalance / rules.creditConfig.overtimeCostPerMin) * 60L
                        RuleResult(
                            shouldWarn = true,
                            warnMessage = "今天的屏幕时间已用完",
                            remainingSeconds = 0,
                            graceSecondsAvailable = graceSecs
                        )
                    } else {
                        RuleResult(
                            shouldLock = true,
                            lockReason = LockReason.DAILY_LIMIT_REACHED
                        )
                    }
                }
                remaining <= 60 -> RuleResult(
                    shouldWarn = true, warnMessage = "今天还剩 60 秒",
                    remainingSeconds = remaining
                )
                remaining <= 300 -> RuleResult(
                    shouldWarn = true, warnMessage = "今天还剩 5 分钟",
                    remainingSeconds = remaining
                )
                remaining <= 900 -> RuleResult(
                    shouldWarn = true, warnMessage = "今天学习时间还剩 15 分钟",
                    remainingSeconds = remaining
                )
                else -> RuleResult.ALLOWED
            }
        }

        return RuleResult.ALLOWED
    }

    private fun isInTimeWindow(
        windows: List<TimeWindow>, dayOfWeek: DayOfWeek, now: LocalTime
    ): Boolean {
        val todayIso = dayOfWeek.value
        val nowMinutes = now.hour * 60 + now.minute
        return windows.any { win ->
            todayIso in win.daysOfWeek &&
            (win.startHour * 60 + win.startMinute) <= nowMinutes &&
            nowMinutes < (win.endHour * 60 + win.endMinute)
        }
    }
}
