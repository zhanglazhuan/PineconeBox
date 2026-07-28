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
    private var breakStartedAt: Long = 0  // SystemClock.elapsedRealtime when break lock started
    private var breakDurationSecs: Int = 0
    private var isInBreakLock: Boolean = false

    // --- Lifecycle ---
    override fun start() {
        if (isRunning) return

        val integrityOk = secureStorage.verifyIntegrity()
        if (!integrityOk) {
            listener?.onLockRequired(LockReason.DATA_CORRUPTED)
            return
        }

        rules = secureStorage.loadRules() ?: RuleSet()
        // Load persisted snapshot — survive app restarts within same day
        snapshot = secureStorage.loadSnapshot() ?: UsageSnapshot(date = LocalDate.now())
        // Reset if date changed (new day) or usage exceeds current limit
        if (snapshot.date != LocalDate.now()) {
            snapshot = UsageSnapshot(date = LocalDate.now())
        }
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
        // Always reset — any manual rules update means user is reconfiguring
        snapshot = snapshot.copy(totalSeconds = 0, continuousSeconds = 0)
        android.util.Log.d("GuardEngine", "updateRules | dailyLimit=${rules.dailyTotalLimit}min | snapshot reset")
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

        // If no PIN has been set, the device hasn't been provisioned by a
        // parent yet. Do NOT evaluate rules — the default time windows and
        // limits would lock out the parent during initial setup.
        if (!secureStorage.isPinSetup()) return

        val today = LocalDate.now()
        if (snapshot.date != today) snapshot = UsageSnapshot(date = today)

        val nowRealtime = android.os.SystemClock.elapsedRealtime()

        // Don't accumulate time while user is on settings/editor pages
        val inSettings = com.pinecone.guard.service.GuardClientHolder.settingsPageCount > 0

        val newUsage = usageTracker.getTodayUsage()

        if (inSettings) {
            // Just update timestamp to prevent jump on return, skip accumulation
            snapshot = snapshot.copy(lastActivityTime = nowRealtime,
                categoryUsage = newUsage.categoryUsage,
                appUsage = newUsage.appUsage)
            return
        }

        // Accumulate elapsed realtime as fallback (UsageStatsManager has multi-minute latency)
        val elapsedSinceLast = if (snapshot.lastActivityTime > 0)
            (nowRealtime - snapshot.lastActivityTime) / 1000 else 0L
        val continuousFromTick = snapshot.continuousSeconds + elapsedSinceLast

        // Effective total = max of system-reported usage and tick-accumulated time
        val effectiveTotal = maxOf(newUsage.totalSeconds, continuousFromTick)

        snapshot = snapshot.copy(
            totalSeconds = effectiveTotal,
            continuousSeconds = continuousFromTick.coerceAtLeast(0),
            lastActivityTime = nowRealtime,
            categoryUsage = newUsage.categoryUsage,
            appUsage = newUsage.appUsage
        )

        // If we're in a break lock, check if break time has elapsed
        if (isInBreakLock) {
            val elapsedBreak = (nowRealtime - breakStartedAt) / 1000
            if (elapsedBreak >= breakDurationSecs) {
                // Break finished — reset continuous usage and release lock
                snapshot = snapshot.copy(continuousSeconds = 0, lastActivityTime = nowRealtime)
                isInBreakLock = false
                breakStartedAt = 0
                breakDurationSecs = 0
                listener?.onBreakFinished()
                android.util.Log.d("GuardEngine", "TICK | break finished, lock released")
                persistAll()
                return
            }
            // Still in break lock — don't re-evaluate, skip
            android.util.Log.d("GuardEngine", "TICK | in break lock | elapsed=${elapsedBreak}s / ${breakDurationSecs}s")
            return
        }

        val newTimeSnap = TimeGuard.takeSnapshot(snapshot.totalSeconds)
        snapshot = snapshot.copy(
            timeSnapshots = (snapshot.timeSnapshots + newTimeSnap).takeLast(20)
        )

        val result = ruleEngine.evaluate(rules, snapshot, null, null, creditAccount.balance)

        // ── DEBUG: log every tick ──
        android.util.Log.d("GuardEngine", "TICK | " +
            "dailyLimit=${rules.dailyTotalLimit}min | " +
            "usedSecs=${snapshot.totalSeconds} | " +
            "remainingSecs=${(rules.dailyTotalLimit ?: -1) * 60 - snapshot.totalSeconds} | " +
            "credit=$creditAccount.balance | " +
            "shouldLock=${result.shouldLock} | " +
            "shouldWarn=${result.shouldWarn} | " +
            "warnMsg=${result.warnMessage} | " +
            "lockReason=${result.lockReason}")

        if (result.shouldLock) {
            if (result.lockReason == LockReason.BREAK_REQUIRED) {
                // Start break lock with countdown
                isInBreakLock = true
                breakStartedAt = nowRealtime
                breakDurationSecs = result.breakDurationMinutes * 60
                listener?.onLockRequired(result.lockReason!!)
                android.util.Log.d("GuardEngine", "TICK | break lock started | ${result.breakUsageMinutes}min usage → ${result.breakDurationMinutes}min break")
            } else {
                listener?.onLockRequired(result.lockReason!!)
            }
        } else if (result.shouldWarn) {
            listener?.onWarningLevel(1, result.warnMessage!!, result.remainingSeconds ?: 0)
        }

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
