package com.pinecone.guard.data.model

import java.time.DayOfWeek
import java.time.LocalDate

typealias DurationMinutes = Int

data class RuleSet(
    val version: Int = 1,
    val dailyTotalLimit: DurationMinutes? = 150,
    val categoryLimits: List<CategoryLimit> = CategoryLimit.DEFAULTS,
    val appLimits: List<AppLimit> = emptyList(),
    val timeWindows: List<TimeWindow> = TimeWindow.DEFAULTS,
    val breakRule: BreakRule? = BreakRule.DEFAULT,
    val creditConfig: CreditConfig = CreditConfig.DEFAULT,
    val pausedForToday: String? = null
)

data class CategoryLimit(
    val categoryId: String,
    val label: String,
    val dailyMinutes: DurationMinutes?
) {
    companion object {
        val DEFAULTS = listOf(
            CategoryLimit("english", "英语学习", 60),
            CategoryLimit("reading", "绘本阅读", null),
            CategoryLimit("documentary", "纪录片", null),
            CategoryLimit("apps", "学习App", 45)
        )
    }
}

data class AppLimit(
    val packageName: String,
    val appLabel: String,
    val dailyMinutes: DurationMinutes
)

data class TimeWindow(
    val name: String,
    val startHour: Int, val startMinute: Int,
    val endHour: Int, val endMinute: Int,
    val daysOfWeek: Set<Int>     // 1=Monday (ISO)
) {
    companion object {
        val DEFAULTS = listOf(
            TimeWindow("周一至周五", 16, 0, 21, 0, setOf(1, 2, 3, 4, 5)),
            TimeWindow("周六日", 8, 0, 21, 0, setOf(6, 7))
        )
    }
}

data class BreakRule(
    val usageMinutes: Int,
    val breakMinutes: Int
) {
    companion object {
        val DEFAULT = BreakRule(40, 10)
    }
}

data class CreditConfig(
    val weeklyTotal: Int = 100,
    val resetDay: DayOfWeek = DayOfWeek.MONDAY,
    val overtimeCostPerMin: Int = 5,
    val earlyStopRewardPerMin: Int = 2
) {
    companion object {
        val DEFAULT = CreditConfig()
    }
}

data class DailyUsage(
    val date: LocalDate,
    val totalSeconds: Long,
    val byCategory: Map<String, Long>,
    val byApp: Map<String, Long>,
    val wasGraceUsed: Boolean,
    val creditChange: Int
)

data class GuardState(
    val isActive: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val totalSecondsToday: Long = 0,
    val dailyLimitSeconds: Long? = null,
    val remainingSeconds: Long? = null,
    val isInGracePeriod: Boolean = false,
    val graceSecondsLeft: Long = 0,
    val creditBalance: Int = 0,
    val currentAppPkg: String? = null,
    val currentAppCategory: String? = null,
    val isInBreak: Boolean = false,
    val breakSecondsLeft: Long = 0,
    val lockReason: LockReason? = null
)

enum class LockReason {
    DAILY_LIMIT_REACHED,
    OUTSIDE_TIME_WINDOW,
    TIME_TAMPERED,
    DATA_CORRUPTED,
    PARENT_LOCK,
    NOT_PROVISIONED
}
