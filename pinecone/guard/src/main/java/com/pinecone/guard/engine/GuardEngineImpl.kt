package com.pinecone.guard.engine

import android.content.Context
import com.pinecone.guard.api.GuardEngine
import com.pinecone.guard.api.GuardStateListener
import com.pinecone.guard.data.SecureStorage
import com.pinecone.guard.data.UsageTracker
import com.pinecone.guard.data.model.*
import java.time.LocalDate

class GuardEngineImpl(
    private val context: Context,
    private val secureStorage: SecureStorage,
    private val usageTracker: UsageTracker
) : GuardEngine {

    private val ruleEngine = RuleEngine()
    private val creditManager = CreditManager()
    private var rules: RuleSet = RuleSet()
    private var snapshot: UsageSnapshot = UsageSnapshot()
    private var creditAccount: CreditAccount = CreditAccount(
        balance = 100, weeklyTotal = 100,
        resetDay = java.time.DayOfWeek.MONDAY, lastResetTime = 0
    )
    private var isRunning = false
    private var listener: GuardStateListener? = null

    // --- Lifecycle ---
    override fun start() {
        if (isRunning) return

        val integrityOk = secureStorage.verifyIntegrity()
        if (!integrityOk) {
            listener?.onLockRequired(LockReason.DATA_CORRUPTED)
            return
        }

        rules = secureStorage.loadRules() ?: RuleSet()
        snapshot = secureStorage.loadSnapshot() ?: UsageSnapshot(date = LocalDate.now())
        creditAccount = secureStorage.loadCredits() ?: creditManager.createInitial(rules.creditConfig)

        creditAccount = creditManager.checkAndReset(creditAccount)

        val lastTimeSnap = snapshot.timeSnapshots.lastOrNull()
        val currentTimeSnap = TimeGuard.takeSnapshot(snapshot.totalSeconds)
        val timeStatus = TimeGuard.validate(lastTimeSnap, currentTimeSnap)
        if (timeStatus == TimeStatus.TAMPERED_BACKWARD || timeStatus == TimeStatus.TAMPERED_REBOOT) {
            listener?.onLockRequired(LockReason.TIME_TAMPERED)
            return
        }

        isRunning = true
        pushState()
    }

    override fun stop() {
        isRunning = false
        persistAll()
    }

    override fun isActive(): Boolean = isRunning

    // --- Rules ---
    override fun getRules(): RuleSet = rules
    override fun updateRules(rules: RuleSet) {
        this.rules = rules
        persistAll()
    }

    // --- State ---
    override fun getCurrentState(): GuardState = buildState()
    override fun getUsageHistory(days: Int): List<DailyUsage> = secureStorage.loadHistory(days)

    // --- Credit ---
    override fun getCreditAccount(): CreditAccount = creditAccount
    override fun manualResetCredits() {
        creditAccount = creditManager.manualReset(creditAccount)
        persistCredits()
    }

    // --- Parent operations ---
    override fun isPinSetup(): Boolean = secureStorage.isPinSetup()
    override fun setupPin(pin: String) { secureStorage.savePin(pin) }
    override fun verifyPin(pin: String): Boolean = secureStorage.verifyPin(pin)
    override fun changePin(oldPin: String, newPin: String): Boolean =
        secureStorage.changePin(oldPin, newPin)
    override fun pauseForToday(reason: String) {
        rules = rules.copy(pausedForToday = reason)
        persistAll()
    }
    override fun resumeForToday() {
        rules = rules.copy(pausedForToday = null)
        persistAll()
    }

    // --- Child operations ---
    override fun requestEarlyStop(): Int {
        val dailyLimit = rules.dailyTotalLimit ?: return 0
        val limitSecs = dailyLimit * 60L
        val unused = limitSecs - snapshot.totalSeconds
        if (unused <= 0) return 0
        val unusedMin = unused / 60
        val (newAccount, rewarded) = creditManager.rewardEarlyStop(
            creditAccount, rules.creditConfig.earlyStopRewardPerMin, unusedMin
        )
        if (rewarded > 0) { creditAccount = newAccount; persistCredits() }
        listener?.onLockRequired(LockReason.DAILY_LIMIT_REACHED)
        return rewarded
    }

    override fun requestGraceExtension(): Boolean {
        if (!creditManager.canGrantGrace(creditAccount)) return false
        listener?.onGracePeriodStarted(creditAccount.balance)
        return true
    }

    // --- Internal ---
    fun setListener(l: GuardStateListener) { listener = l }

    fun tick() {
        if (!isRunning) return
        val today = LocalDate.now()
        if (snapshot.date != today) snapshot = UsageSnapshot(date = today)

        val newUsage = usageTracker.getTodayUsage()
        snapshot = snapshot.copy(
            totalSeconds = newUsage.totalSeconds,
            categoryUsage = newUsage.categoryUsage,
            appUsage = newUsage.appUsage
        )

        val newTimeSnap = TimeGuard.takeSnapshot(snapshot.totalSeconds)
        snapshot = snapshot.copy(
            timeSnapshots = (snapshot.timeSnapshots + newTimeSnap).takeLast(20)
        )

        val result = ruleEngine.evaluate(rules, snapshot, null, null, creditAccount.balance)
        if (result.shouldLock) listener?.onLockRequired(result.lockReason!!)
        else if (result.shouldWarn) listener?.onWarningLevel(1, result.warnMessage!!, result.remainingSeconds ?: 0)

        persistAll()
    }

    private fun buildState(): GuardState {
        val dailyLimit = rules.dailyTotalLimit
        val limitSecs = dailyLimit?.times(60L)
        return GuardState(
            isActive = isRunning, date = snapshot.date,
            totalSecondsToday = snapshot.totalSeconds,
            dailyLimitSeconds = limitSecs,
            remainingSeconds = limitSecs?.minus(snapshot.totalSeconds)?.coerceAtLeast(0),
            creditBalance = creditAccount.balance
        )
    }

    private fun pushState() { listener?.onStateChanged(buildState()) }
    private fun persistAll() { secureStorage.saveRules(rules); secureStorage.saveSnapshot(snapshot); persistCredits() }
    private fun persistCredits() { secureStorage.saveCredits(creditAccount) }
}
