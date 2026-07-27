package com.pinecone.guard.service

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.pinecone.guard.data.model.*

/**
 * Singleton holder for GuardClient. Initialized by the launcher app on startup.
 * Provides synchronous access to rules/config via local cache.
 */
object GuardClientHolder {

    @Volatile
    var client: GuardClient? = null
        private set

    // Local cache updated via IPC callbacks
    @Volatile
    var cachedRules: RuleSet = RuleSet()
        private set

    @Volatile
    var cachedCredits: CreditAccount = CreditAccount(
        balance = 100, weeklyTotal = 100,
        resetDay = java.time.DayOfWeek.MONDAY, lastResetTime = 0
    )
        private set

    @Volatile
    var isPinSet: Boolean = false

    @Volatile
    var isInitialized: Boolean = false

    // Compose-observable version counter — reading this triggers recomposition when rules change
    var rulesVersion by mutableIntStateOf(0)
        private set

    // Count of open settings/editor pages — tick pauses accumulation when > 0
    @Volatile
    var settingsPageCount: Int = 0

    fun enterSettings() { settingsPageCount++ }
    fun leaveSettings() { settingsPageCount-- }

    // Prevent duplicate lock-flow launches (countdown → lock screen)
    @Volatile
    var isLockFlowActive: Boolean = false

    fun initialize(context: Context) {
        if (isInitialized) return
        client = GuardClient(context)
        client?.bind()
        isInitialized = true
    }

    fun updateRules(rules: RuleSet) {
        cachedRules = rules
        rulesVersion++
        isLockFlowActive = false  // rules changed → allow lock flow again
        client?.sendRules(rules)
    }

    fun updateDailyLimit(minutes: Int?) {
        updateRules(cachedRules.copy(dailyTotalLimit = minutes))
    }

    fun updateBreakRule(usageMin: Int, breakMin: Int) {
        val rule = if (usageMin > 0) BreakRule(usageMin, breakMin) else null
        updateRules(cachedRules.copy(breakRule = rule))
    }

    fun updateCategoryLimits(limits: List<CategoryLimit>) {
        updateRules(cachedRules.copy(categoryLimits = limits))
    }

    fun updateAppLimits(limits: List<AppLimit>) {
        updateRules(cachedRules.copy(appLimits = limits))
    }

    fun updateCreditConfig(config: CreditConfig) {
        updateRules(cachedRules.copy(creditConfig = config))
    }

    fun updateTimeWindows(windows: List<TimeWindow>) {
        updateRules(cachedRules.copy(timeWindows = windows))
    }

    fun updatePin(pin: String) {
        client?.setupPin(pin)
    }

    fun pauseToday() {
        client?.pauseToday("家长手动暂停")
    }

    fun resetCredits() {
        client?.manualResetCredits()
    }

    fun shutdown() {
        client?.unbind()
        client = null
        isInitialized = false
    }
}
