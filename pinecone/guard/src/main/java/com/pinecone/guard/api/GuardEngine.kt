package com.pinecone.guard.api

import com.pinecone.guard.data.model.*

interface GuardEngine {
    // --- Lifecycle ---
    fun start()
    fun stop()
    fun isActive(): Boolean

    // --- Rules ---
    fun getRules(): RuleSet
    fun updateRules(rules: RuleSet)

    // --- Current state ---
    fun getCurrentState(): GuardState
    fun getUsageHistory(days: Int): List<DailyUsage>

    // --- Credit ---
    fun getCreditAccount(): CreditAccount
    fun manualResetCredits()

    // --- Parent operations ---
    fun isPinSetup(): Boolean
    fun setupPin(pin: String)
    fun verifyPin(pin: String): Boolean
    fun changePin(oldPin: String, newPin: String): Boolean
    fun pauseForToday(reason: String)
    fun resumeForToday()

    // --- Child operations ---
    fun requestEarlyStop(): Int            // returns: credits rewarded
    fun requestGraceExtension(): Boolean   // returns: true = granted
}
