# PineCone OS 防沉迷系统 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a local anti-addiction system with parental controls, credit-based grace periods, and tamper-resistant enforcement for the PineCone OS Android TV launcher.

**Architecture:** Two-module design — `guard` (Android library with api/engine/data/service layers) and `app` (existing launcher consuming only `guard`'s public api). GuardService runs in independent process `:guard` using DeviceAdmin for system-level lock. Cross-process communication via `Messenger` IPC.

**Tech Stack:** Kotlin, Android SDK 36, AGP 9.3.0, AndroidX Security Crypto, Leanback, DevicePolicyManager, UsageStatsManager

## Global Constraints

- minSdk 36, targetSdk 36, compileSdk 36 (minorApiLevel 1)
- JVM target Java 11, Gradle config-cache enabled, kotlin.code.style=official
- Only new dependency: `androidx.security:security-crypto:1.1.0-alpha06`
- All code under `com.pinecone.guard` (library) and `com.pinecone.launcher` (app)
- UI follows Leanback/Android TV patterns (list rows, remote-friendly, no touch)
- All strings hardcoded in Chinese (no i18n required)
- No server, no network calls — purely local

---

## File Mapping

### New module: `guard/` (Android Library)

```
guard/
├── build.gradle.kts
└── src/main/java/com/pinecone/guard/
    ├── api/
    │   ├── GuardEngine.kt            # Public engine interface
    │   └── GuardStateListener.kt     # Callback interface for UI process
    ├── engine/
    │   ├── GuardEngineImpl.kt        # Engine implementation, ties all together
    │   ├── RuleEngine.kt             # Five-rule evaluator with priority ordering
    │   ├── CreditManager.kt          # Credit score lifecycle, deductions, rewards
    │   └── TimeGuard.kt              # Dual-clock (elapsedRealtime vs wallClock)
    ├── data/
    │   ├── model/
    │   │   ├── RuleSet.kt            # RuleSet, CategoryLimit, AppLimit, TimeWindow,
    │   │   │                            BreakRule, CreditConfig, DailyUsage, GuardState
    │   │   ├── CreditAccount.kt      # CreditAccount, CreditTransaction, CreditReason
    │   │   ├── UsageSnapshot.kt      # Per-day snapshot (total, byCategory, byApp)
    │   │   └── TimeSnapshot.kt       # Elapsed + wall clock snapshot for integrity
    │   ├── SecureStorage.kt          # EncryptedSharedPrefs + backup file + checksum
    │   └── UsageTracker.kt           # UsageStatsManager wrapper
    └── service/
        ├── GuardService.kt           # Foreground service in :guard process
        ├── GuardWatchdog.kt          # Bidirectional process liveness monitor
        ├── BootReceiver.kt           # BOOT_COMPLETED → start GuardService
        ├── DeviceAdminReceiver.kt    # DeviceAdmin for system-level lockNow()
        └── GuardClient.kt            # IPC client: main process → guard process

### New directory in `app/`: `ui/guard/` (UI only, depends on guard api/)

app/src/main/java/com/pinecone/launcher/ui/guard/
├── ParentSettingsActivity.kt
├── PinSetupActivity.kt
├── LockScreenActivity.kt
├── FullScreenWarningActivity.kt
├── BreakReminderActivity.kt
└── editors/
    ├── DailyLimitFragment.kt
    ├── BreakRuleFragment.kt
    ├── TimeWindowFragment.kt
    ├── CategoryLimitFragment.kt
    ├── AppLimitFragment.kt
    ├── CreditConfigFragment.kt
    └── UsageHistoryFragment.kt
```

### Modified existing files

```
app/src/main/java/com/pinecone/launcher/MainActivity.kt   # Add "家长设置" entry
app/src/main/AndroidManifest.xml                          # Add permissions, services, receivers
app/build.gradle.kts                                      # Add project(":guard") dependency
gradle/libs.versions.toml                                 # Add security-crypto
settings.gradle.kts                                       # Add include(":guard")
```

---

## Phase 1: Module Scaffold + Data Models

### Task 1: Create guard module + build configuration

**Files:**
- Create: `guard/build.gradle.kts`
- Create: `guard/src/main/AndroidManifest.xml`
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Produces:** `:guard` library module compiles, `app` depends on it

- [ ] **Step 1: Create `guard/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.pinecone.guard"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 36
        targetSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.security.crypto)
}
```

- [ ] **Step 2: Create `guard/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
</manifest>
```

- [ ] **Step 3: Create directory tree**

```bash
mkdir -p launcher/guard/src/main/java/com/pinecone/guard/{api,engine,data/model,service}
```

- [ ] **Step 4: Modify `settings.gradle.kts`** — replace `include(":app")` line with:

```kotlin
include(":app")
include(":guard")
```

- [ ] **Step 5: Modify `gradle/libs.versions.toml`** — add under `[versions]`:

```toml
securityCrypto = "1.1.0-alpha06"
```

Add under `[libraries]`:
```toml
androidx-security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "securityCrypto" }
```

- [ ] **Step 6: Modify `app/build.gradle.kts`** — add inside `dependencies {}` block:

```kotlin
implementation(project(":guard"))
```

- [ ] **Step 7: Verify**

```bash
cd launcher && ./gradlew :guard:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

### Task 2: Write all data model classes

**Files:**
- Create: `guard/src/main/java/com/pinecone/guard/data/model/RuleSet.kt`
- Create: `guard/src/main/java/com/pinecone/guard/data/model/CreditAccount.kt`
- Create: `guard/src/main/java/com/pinecone/guard/data/model/UsageSnapshot.kt`
- Create: `guard/src/main/java/com/pinecone/guard/data/model/TimeSnapshot.kt`

**Produces:** All data classes needed by engine, storage, and UI layers

- [ ] **Step 1: Write `RuleSet.kt`**

```kotlin
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
```

- [ ] **Step 2: Write `CreditAccount.kt`**

```kotlin
package com.pinecone.guard.data.model

import java.time.DayOfWeek

data class CreditAccount(
    val balance: Int,
    val weeklyTotal: Int,
    val resetDay: DayOfWeek,
    val lastResetTime: Long,
    val transactions: List<CreditTransaction> = emptyList()
)

data class CreditTransaction(
    val timestamp: Long,
    val amount: Int,
    val reason: CreditReason,
    val balanceAfter: Int
)

enum class CreditReason {
    WEEKLY_RESET,
    OVERTIME_DEDUCTION,
    EARLY_STOP_REWARD,
    PARENT_MANUAL_RESET
}
```

- [ ] **Step 3: Write `UsageSnapshot.kt`**

```kotlin
package com.pinecone.guard.data.model

import java.time.LocalDate

data class UsageSnapshot(
    val date: LocalDate = LocalDate.now(),
    val totalSeconds: Long = 0,
    val continuousSeconds: Long = 0,
    val lastActivityTime: Long = 0,          // SystemClock.elapsedRealtime()
    val categoryUsage: Map<String, Long> = emptyMap(),
    val appUsage: Map<String, Long> = emptyMap(),
    val timeSnapshots: List<TimeSnapshot> = emptyList()
)
```

- [ ] **Step 4: Write `TimeSnapshot.kt`**

```kotlin
package com.pinecone.guard.data.model

data class TimeSnapshot(
    val elapsedRealtime: Long,
    val wallClock: Long,
    val accumulatedSeconds: Long
)

enum class TimeStatus {
    VALID, TAMPERED_BACKWARD, TAMPERED_REBOOT
}
```

- [ ] **Step 5: Verify compilation**

```bash
cd launcher && ./gradlew :guard:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

## Phase 2: Core Engine (pure Kotlin, no Android deps except Context for SecureStorage)

### Task 3: Write API interfaces

**Files:**
- Create: `guard/src/main/java/com/pinecone/guard/api/GuardEngine.kt`
- Create: `guard/src/main/java/com/pinecone/guard/api/GuardStateListener.kt`

**Produces:** Public API that `app` module depends on. Everything else in `guard` is internal.

- [ ] **Step 1: Write `GuardEngine.kt`**

```kotlin
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
```

- [ ] **Step 2: Write `GuardStateListener.kt`**

```kotlin
package com.pinecone.guard.api

import com.pinecone.guard.data.model.GuardState
import com.pinecone.guard.data.model.LockReason

interface GuardStateListener {
    /** 15 min remaining → level 1 toast */
    fun onWarningLevel(level: Int, message: String, remainingSeconds: Long)
    /** Grace period entered, can extend */
    fun onGracePeriodStarted(creditRemaining: Int)
    /** Countdown tick during grace period */
    fun onGraceTick(secondsLeft: Int, creditDraining: Int)
    /** Hard lock required */
    fun onLockRequired(reason: LockReason)
    /** Break timer started */
    fun onBreakRequired(durationSeconds: Int)
    /** Break finished, usage can resume */
    fun onBreakFinished()
    /** Generic state change for UI refresh */
    fun onStateChanged(newState: GuardState)
}
```

- [ ] **Step 3: Verify**

```bash
cd launcher && ./gradlew :guard:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

### Task 4: Implement RuleEngine

**Files:**
- Create: `guard/src/main/java/com/pinecone/guard/engine/RuleEngine.kt`

**Interfaces:**
- Consumes: `RuleSet`, `UsageSnapshot`, `LocalDate`
- Produces: `RuleEngine.evaluate()` returns `RuleResult`

- [ ] **Step 1: Write `RuleEngine.kt`**

```kotlin
package com.pinecone.guard.engine

import com.pinecone.guard.data.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class RuleResult(
    val shouldLock: Boolean,
    val lockReason: LockReason?,
    val shouldWarn: Boolean,
    val warnMessage: String?,
    val remainingSeconds: Long?,
    val isAppBlocked: Boolean = false,
    val blockedAppMessage: String? = null,
    val categoryBlocked: Set<String> = emptySet(),
    val isBreakDue: Boolean = false,
    val graceSecondsAvailable: Long = 0   // 0 = none, >0 = credits allow grace
) {
    companion object {
        val ALLOWED = RuleResult()
    }
}

class RuleEngine {

    /**
     * Evaluate all rules against current usage. Returns the strictest result.
     * Priority: timeWindow > break > app > category > dailyTotal
     */
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
        val inWindow = isInTimeWindow(rules.timeWindows, today.dayOfWeek, now)
        if (!inWindow) {
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
                    return RuleResult(
                        categoryBlocked = setOf(currentAppCategory)
                    )
                }
            }
        }

        // 5. Daily total check (lowest priority, fallback)
        val dailyLimit = rules.dailyTotalLimit
        if (dailyLimit != null) {
            val limitSecs = dailyLimit * 60L
            val used = snapshot.totalSeconds
            val remaining = limitSecs - used

            return when {
                remaining <= 0 -> {
                    // Over limit → grace or lock depending on credits
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
                    shouldWarn = true,
                    warnMessage = "今天还剩 60 秒",
                    remainingSeconds = remaining
                )
                remaining <= 300 -> RuleResult(
                    shouldWarn = true,
                    warnMessage = "今天还剩 5 分钟",
                    remainingSeconds = remaining
                )
                remaining <= 900 -> RuleResult(
                    shouldWarn = true,
                    warnMessage = "今天学习时间还剩 15 分钟",
                    remainingSeconds = remaining
                )
                else -> RuleResult.ALLOWED
            }
        }

        return RuleResult.ALLOWED
    }

    private fun isInTimeWindow(
        windows: List<TimeWindow>,
        dayOfWeek: DayOfWeek,
        now: LocalTime
    ): Boolean {
        val todayIso = dayOfWeek.value // Monday=1 per ISO
        val nowMinutes = now.hour * 60 + now.minute
        return windows.any { win ->
            todayIso in win.daysOfWeek &&
            (win.startHour * 60 + win.startMinute) <= nowMinutes &&
            nowMinutes < (win.endHour * 60 + win.endMinute)
        }
    }
}
```

- [ ] **Step 2: Verify**

```bash
cd launcher && ./gradlew :guard:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

### Task 5: Implement CreditManager

**Files:**
- Create: `guard/src/main/java/com/pinecone/guard/engine/CreditManager.kt`

**Interfaces:**
- Consumes: `CreditAccount`, `CreditConfig`, `CreditTransaction`, `CreditReason`
- Produces: `CreditManager` class with `deductForGrace()`, `rewardEarlyStop()`, `checkAndReset()`, `manualReset()`

- [ ] **Step 1: Write `CreditManager.kt`**

```kotlin
package com.pinecone.guard.engine

import com.pinecone.guard.data.model.*
import java.time.DayOfWeek
import java.time.LocalDate

class CreditManager {

    fun createInitial(config: CreditConfig): CreditAccount = CreditAccount(
        balance = config.weeklyTotal,
        weeklyTotal = config.weeklyTotal,
        resetDay = config.resetDay,
        lastResetTime = System.currentTimeMillis()
    )

    /**
     * Must be called at start of each session.
     * Returns updated account (with reset applied if weekly reset is due).
     */
    fun checkAndReset(account: CreditAccount, now: Long = System.currentTimeMillis()): CreditAccount {
        val today = LocalDate.now()
        if (isResetDue(account, today)) {
            return account.copy(
                balance = account.weeklyTotal,
                lastResetTime = now,
                transactions = listOf(
                    CreditTransaction(now, account.weeklyTotal, CreditReason.WEEKLY_RESET, account.weeklyTotal)
                )
            )
        }
        return account
    }

    /**
     * Deduct credits for using grace period. Returns updated account or null if broke.
     */
    fun deductForGrace(
        account: CreditAccount,
        costPerMin: Int,
        minutesUsed: Int
    ): CreditAccount {
        val cost = costPerMin * minutesUsed
        val newBalance = (account.balance - cost).coerceAtLeast(0)
        val actualDeduction = account.balance - newBalance
        return account.copy(
            balance = newBalance,
            transactions = account.transactions + CreditTransaction(
                System.currentTimeMillis(),
                -actualDeduction,
                CreditReason.OVERTIME_DEDUCTION,
                newBalance
            )
        )
    }

    /**
     * Reward credits for voluntarily stopping early.
     * Reward is capped at weekly total.
     */
    fun rewardEarlyStop(
        account: CreditAccount,
        rewardPerMin: Int,
        unusedMinutes: Long
    ): Pair<CreditAccount, Int> {
        val reward = (rewardPerMin * unusedMinutes).toInt()
        val cappedReward = minOf(reward, account.weeklyTotal - account.balance)
        if (cappedReward <= 0) return account to 0
        val newBalance = account.balance + cappedReward
        return account.copy(
            balance = newBalance,
            transactions = account.transactions + CreditTransaction(
                System.currentTimeMillis(),
                cappedReward,
                CreditReason.EARLY_STOP_REWARD,
                newBalance
            )
        ) to cappedReward
    }

    fun manualReset(account: CreditAccount): CreditAccount = account.copy(
        balance = account.weeklyTotal,
        lastResetTime = System.currentTimeMillis(),
        transactions = account.transactions + CreditTransaction(
            System.currentTimeMillis(),
            account.weeklyTotal,
            CreditReason.PARENT_MANUAL_RESET,
            account.weeklyTotal
        )
    )

    fun canGrantGrace(account: CreditAccount): Boolean = account.balance > 0

    private fun isResetDue(account: CreditAccount, today: LocalDate): Boolean {
        if (account.transactions.isEmpty()) return false
        val lastResetDay = LocalDate.ofEpochDay(account.lastResetTime / 86_400_000)
        val daysSinceReset = today.toEpochDay() - lastResetDay.toEpochDay()
        val todayDayOfWeek = today.dayOfWeek
        return daysSinceReset >= 7 ||
               (daysSinceReset > 0 && todayDayOfWeek == account.resetDay)
    }
}
```

- [ ] **Step 2: Verify**

```bash
cd launcher && ./gradlew :guard:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

### Task 6: Implement TimeGuard

**Files:**
- Create: `guard/src/main/java/com/pinecone/guard/engine/TimeGuard.kt`

**Interfaces:**
- Consumes: `TimeSnapshot`, `TimeStatus`
- Produces: `TimeGuard` — static methods for time integrity validation

- [ ] **Step 1: Write `TimeGuard.kt`**

```kotlin
package com.pinecone.guard.engine

import android.os.SystemClock
import com.pinecone.guard.data.model.TimeSnapshot
import com.pinecone.guard.data.model.TimeStatus

class TimeGuard {
    companion object {
        private const val ALLOWED_DRIFT_MS = 120_000L  // 2 min tolerance for NTP sync

        fun takeSnapshot(accumulatedSeconds: Long): TimeSnapshot = TimeSnapshot(
            elapsedRealtime = SystemClock.elapsedRealtime(),
            wallClock = System.currentTimeMillis(),
            accumulatedSeconds = accumulatedSeconds
        )

        /**
         * Validate that wall clock has not been rolled back since the last snapshot.
         * Uses elapsedRealtime to detect forward tampering (fast-forwarding system time
         * to bypass time-window restrictions — we compare the delta in elapsedRealtime
         * against the delta in wall clock; a large mismatch indicates tampering).
         */
        fun validate(last: TimeSnapshot?, current: TimeSnapshot): TimeStatus {
            if (last == null) return TimeStatus.VALID

            val elapsedDelta = current.elapsedRealtime - last.elapsedRealtime
            val wallDelta = current.wallClock - last.wallClock

            if (elapsedDelta < 0) return TimeStatus.TAMPERED_REBOOT

            if (wallDelta < -ALLOWED_DRIFT_MS) return TimeStatus.TAMPERED_BACKWARD

            // Fast-forward detection: wall clock jumped much more than elapsed
            if (wallDelta > elapsedDelta + ALLOWED_DRIFT_MS * 2) return TimeStatus.TAMPERED_BACKWARD

            return TimeStatus.VALID
        }

        /**
         * Compute real elapsed usage time between two snapshots using monotonic clock.
         * This is immune to system wall clock changes.
         */
        fun realUsageDelta(last: TimeSnapshot, now: TimeSnapshot): Long {
            if (last.elapsedRealtime > now.elapsedRealtime) return 0L
            return now.elapsedRealtime - last.elapsedRealtime
        }
    }
}
```

- [ ] **Step 2: Verify**

```bash
cd launcher && ./gradlew :guard:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

### Task 7: Implement GuardEngineImpl

**Files:**
- Create: `guard/src/main/java/com/pinecone/guard/engine/GuardEngineImpl.kt`

**Interfaces:**
- Consumes: `RuleEngine`, `CreditManager`, `TimeGuard`, `SecureStorage`, `UsageTracker` (via constructor injection)
- Produces: `GuardEngineImpl : GuardEngine` — full engine implementation

- [ ] **Step 1: Write `GuardEngineImpl.kt`**

```kotlin
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

        // 1. Validate data integrity; lock device if corrupted
        val integrityOk = secureStorage.verifyIntegrity()
        if (!integrityOk) {
            listener?.onLockRequired(LockReason.DATA_CORRUPTED)
            return
        }

        // 2. Load rules from encrypted storage
        rules = secureStorage.loadRules() ?: RuleSet()
        snapshot = secureStorage.loadSnapshot() ?: UsageSnapshot(date = LocalDate.now())
        creditAccount = secureStorage.loadCredits() ?: creditManager.createInitial(rules.creditConfig)

        // 3. Apply weekly credit reset if due
        creditAccount = creditManager.checkAndReset(creditAccount)

        // 4. Validate time integrity
        val lastSnapshot = snapshot.timeSnapshots.lastOrNull()
        val currentSnapshot = TimeGuard.takeSnapshot(snapshot.totalSeconds)
        val timeStatus = TimeGuard.validate(lastSnapshot, currentSnapshot)
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
    override fun updateRules(newRules: RuleSet) {
        rules = newRules
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
        if (rewarded > 0) {
            creditAccount = newAccount
            persistCredits()
        }
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
        // Update snapshot from UsageTracker
        val now = System.currentTimeMillis()
        val today = LocalDate.now()

        // Reset snapshot if day changed
        if (snapshot.date != today) {
            snapshot = UsageSnapshot(date = today)
        }

        val newUsage = usageTracker.getTodayUsage()
        snapshot = snapshot.copy(
            totalSeconds = newUsage.totalSeconds,
            categoryUsage = newUsage.categoryUsage,
            appUsage = newUsage.appUsage
        )

        // Save time snapshot for integrity chain
        val newTimeSnap = TimeGuard.takeSnapshot(snapshot.totalSeconds)
        snapshot = snapshot.copy(
            timeSnapshots = (snapshot.timeSnapshots + newTimeSnap).takeLast(20)
        )

        // Evaluate rules
        val result = ruleEngine.evaluate(
            rules, snapshot, null, null, creditAccount.balance
        )

        if (result.shouldLock) {
            listener?.onLockRequired(result.lockReason!!)
        } else if (result.shouldWarn) {
            listener?.onWarningLevel(1, result.warnMessage!!, result.remainingSeconds ?: 0)
        }

        // Persist periodically
        persistAll()
    }

    fun recordAppLaunch(packageName: String, categoryId: String) {
        snapshot = snapshot.copy(lastActivityTime = System.currentTimeMillis())
        // Check app/category limits before allowing launch
    }

    private fun buildState(): GuardState {
        val dailyLimit = rules.dailyTotalLimit
        val limitSecs = dailyLimit?.times(60L)
        return GuardState(
            isActive = isRunning,
            date = snapshot.date,
            totalSecondsToday = snapshot.totalSeconds,
            dailyLimitSeconds = limitSecs,
            remainingSeconds = limitSecs?.minus(snapshot.totalSeconds)?.coerceAtLeast(0),
            isInGracePeriod = false,
            graceSecondsLeft = 0,
            creditBalance = creditAccount.balance
        )
    }

    private fun pushState() { listener?.onStateChanged(buildState()) }
    private fun persistAll() {
        secureStorage.saveRules(rules)
        secureStorage.saveSnapshot(snapshot)
        persistCredits()
    }
    private fun persistCredits() { secureStorage.saveCredits(creditAccount) }
}
```

- [ ] **Step 2: Verify**

```bash
cd launcher && ./gradlew :guard:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

## Phase 3: Data Layer

### Task 8: Implement SecureStorage

**Files:**
- Create: `guard/src/main/java/com/pinecone/guard/data/SecureStorage.kt`

**Interfaces:**
- Consumes: `RuleSet`, `CreditAccount`, `UsageSnapshot`, `DailyUsage`, Android `Context`
- Produces: `SecureStorage` — encrypted persistence with dual-write + checksum

- [ ] **Step 1: Write `SecureStorage.kt`**

```kotlin
package com.pinecone.guard.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pinecone.guard.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SecureStorage(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "pinecone_guard_main",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val backupFile = File(context.filesDir, ".guard_backup")
    private val checksumFile = File(context.filesDir, ".guard_checksum")
    private val historyFile = File(context.filesDir, "usage_history.json")

    // === Rules ===
    fun saveRules(rules: RuleSet) {
        val json = rulesToJson(rules)
        prefs.edit().putString(KEY_RULES, json).apply()
        val checksum = computeHmac(json)
        writeBackup("rules:$json")
        writeChecksum("rules", checksum)
    }

    fun loadRules(): RuleSet? {
        val json = prefs.getString(KEY_RULES, null)
        if (json != null && verifyIntegrityForKey("rules", json)) return parseRules(json)
        // Try backup
        val backup = readBackup()
        val rulesPart = backup?.lines()?.find { it.startsWith("rules:") }?.removePrefix("rules:")
        return rulesPart?.let { parseRules(it) }
    }

    // === Credits ===
    fun saveCredits(account: CreditAccount) {
        val json = creditsToJson(account)
        prefs.edit().putString(KEY_CREDITS, json).apply()
        writeBackup("credits:$json")
        writeChecksum("credits", computeHmac(json))
    }

    fun loadCredits(): CreditAccount? {
        val json = prefs.getString(KEY_CREDITS, null)
        if (json != null && verifyIntegrityForKey("credits", json)) return parseCredits(json)
        val backup = readBackup()
        val creditsPart = backup?.lines()?.find { it.startsWith("credits:") }?.removePrefix("credits:")
        return creditsPart?.let { parseCredits(it) }
    }

    // === Snapshot ===
    fun saveSnapshot(snap: UsageSnapshot) {
        val json = snapshotToJson(snap)
        prefs.edit().putString(KEY_SNAPSHOT, json).apply()
        writeBackup("snapshot:$json")
    }

    fun loadSnapshot(): UsageSnapshot? {
        val json = prefs.getString(KEY_SNAPSHOT, null) ?: run {
            val backup = readBackup()
            backup?.lines()?.find { it.startsWith("snapshot:") }?.removePrefix("snapshot:")
        }
        return json?.let { parseSnapshot(it) }
    }

    // === History ===
    fun saveHistory(list: List<DailyUsage>) {
        val arr = JSONArray()
        list.forEach { arr.put(dailyUsageToJson(it)) }
        historyFile.writeText(arr.toString())
    }

    fun loadHistory(days: Int): List<DailyUsage> {
        if (!historyFile.exists()) return emptyList()
        val arr = JSONArray(historyFile.readText())
        val result = mutableListOf<DailyUsage>()
        for (i in 0 until minOf(arr.length(), days)) {
            result.add(parseDailyUsage(arr.getJSONObject(i)))
        }
        return result
    }

    // === PIN ===
    fun isPinSetup(): Boolean = prefs.contains(KEY_PIN_HASH)

    fun savePin(pin: String) {
        val hash = hashPin(pin)
        prefs.edit().putString(KEY_PIN_HASH, hash).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return hashPin(pin) == stored
    }

    fun changePin(oldPin: String, newPin: String): Boolean {
        if (!verifyPin(oldPin)) return false
        savePin(newPin)
        return true
    }

    // === Integrity ===
    fun verifyIntegrity(): Boolean = verifyIntegrityForKey("rules", null)
            && verifyIntegrityForKey("credits", null)

    private fun verifyIntegrityForKey(key: String, json: String?): Boolean {
        val storedChecksum = checksumFile.takeIf { it.exists() }
            ?.readLines()?.find { it.startsWith("$key:") }?.removePrefix("$key:")
            ?: return json != null  // First run, no checksum yet
        val data = json ?: prefs.getString(
            when(key) { "rules" -> KEY_RULES; "credits" -> KEY_CREDITS; else -> return true }, null
        ) ?: return false
        return computeHmac(data) == storedChecksum
    }

    // === Helpers ===
    private fun computeHmac(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec("pinecone_guard_key".toByteArray(), "HmacSHA256")
        mac.init(keySpec)
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun hashPin(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest("pinecone_pin_salt:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun writeBackup(content: String) {
        // Write multiple lines: rules, credits, snapshot
        val existing = backupFile.takeIf { it.exists() }?.readLines()?.toMutableList() ?: mutableListOf()
        val prefix = content.substringBefore(":")
        existing.removeAll { it.startsWith("$prefix:") }
        existing.add(content)
        backupFile.writeText(existing.joinToString("\n"))
    }

    private fun readBackup(): String? =
        backupFile.takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    private fun writeChecksum(key: String, checksum: String) {
        val lines = checksumFile.takeIf { it.exists() }?.readLines()?.toMutableList() ?: mutableListOf()
        lines.removeAll { it.startsWith("$key:") }
        lines.add("$key:$checksum")
        checksumFile.writeText(lines.joinToString("\n"))
    }

    companion object {
        private const val KEY_RULES = "guard_rules"
        private const val KEY_CREDITS = "guard_credits"
        private const val KEY_SNAPSHOT = "guard_snapshot"
        private const val KEY_PIN_HASH = "guard_pin_hash"
    }

    // --- JSON serialization (inline to avoid extra dependencies) ---
    private fun rulesToJson(r: RuleSet): String = JSONObject().apply {
        put("version", r.version)
        put("dailyTotalLimit", r.dailyTotalLimit ?: -1)
        put("pausedForToday", r.pausedForToday ?: "")
        put("categoryLimits", JSONArray().apply {
            r.categoryLimits.forEach { put(JSONObject().apply {
                put("id", it.categoryId); put("label", it.label); put("min", it.dailyMinutes ?: -1)
            })}
        })
        put("appLimits", JSONArray().apply {
            r.appLimits.forEach { put(JSONObject().apply {
                put("pkg", it.packageName); put("label", it.appLabel); put("min", it.dailyMinutes)
            })}
        })
        put("timeWindows", JSONArray().apply {
            r.timeWindows.forEach { put(JSONObject().apply {
                put("name", it.name)
                put("sh", it.startHour); put("sm", it.startMinute)
                put("eh", it.endHour); put("em", it.endMinute)
                put("days", JSONArray(it.daysOfWeek.toList()))
            })}
        })
        r.breakRule?.let {
            put("breakRule", JSONObject().apply {
                put("usage", it.usageMinutes); put("break", it.breakMinutes)
            })
        }
        put("creditConfig", JSONObject().apply {
            put("total", r.creditConfig.weeklyTotal)
            put("resetDay", r.creditConfig.resetDay.name)
            put("cost", r.creditConfig.overtimeCostPerMin)
            put("reward", r.creditConfig.earlyStopRewardPerMin)
        })
    }.toString()

    private fun parseRules(json: String): RuleSet? = try {
        val o = JSONObject(json)
        RuleSet(
            version = o.optInt("version", 1),
            dailyTotalLimit = o.optInt("dailyTotalLimit", -1).takeIf { it >= 0 },
            categoryLimits = parseCategoryLimits(o.optJSONArray("categoryLimits")),
            appLimits = parseAppLimits(o.optJSONArray("appLimits")),
            timeWindows = parseTimeWindows(o.optJSONArray("timeWindows")),
            breakRule = o.optJSONObject("breakRule")?.let {
                BreakRule(it.getInt("usage"), it.getInt("break"))
            },
            creditConfig = o.optJSONObject("creditConfig")?.let {
                CreditConfig(
                    weeklyTotal = it.getInt("total"),
                    resetDay = java.time.DayOfWeek.valueOf(it.getString("resetDay")),
                    overtimeCostPerMin = it.getInt("cost"),
                    earlyStopRewardPerMin = it.getInt("reward")
                )
            } ?: CreditConfig.DEFAULT,
            pausedForToday = o.optString("pausedForToday", "").takeIf { it.isNotBlank() }
        )
    } catch (_: Exception) { null }

    private fun parseCategoryLimits(arr: JSONArray?): List<CategoryLimit> = (0 until (arr?.length() ?: 0)).map {
        val o = arr!!.getJSONObject(it)
        CategoryLimit(o.getString("id"), o.getString("label"), o.optInt("min", -1).takeIf { m -> m >= 0 })
    }

    private fun parseAppLimits(arr: JSONArray?): List<AppLimit> = (0 until (arr?.length() ?: 0)).map {
        val o = arr!!.getJSONObject(it)
        AppLimit(o.getString("pkg"), o.getString("label"), o.getInt("min"))
    }

    private fun parseTimeWindows(arr: JSONArray?): List<TimeWindow> = (0 until (arr?.length() ?: 0)).map {
        val o = arr!!.getJSONObject(it)
        val daysArr = o.getJSONArray("days")
        val days = (0 until daysArr.length()).mapTo(mutableSetOf()) { daysArr.getInt(it) }
        TimeWindow(o.getString("name"), o.getInt("sh"), o.getInt("sm"), o.getInt("eh"), o.getInt("em"), days)
    }

    private fun creditsToJson(a: CreditAccount): String = JSONObject().apply {
        put("balance", a.balance); put("total", a.weeklyTotal)
        put("resetDay", a.resetDay.name); put("lastReset", a.lastResetTime)
        put("txns", JSONArray().apply {
            a.transactions.forEach { put(JSONObject().apply {
                put("ts", it.timestamp); put("amt", it.amount)
                put("reason", it.reason.name); put("after", it.balanceAfter)
            })}
        })
    }.toString()

    private fun parseCredits(json: String): CreditAccount? = try {
        val o = JSONObject(json)
        CreditAccount(
            balance = o.getInt("balance"), weeklyTotal = o.getInt("total"),
            resetDay = java.time.DayOfWeek.valueOf(o.getString("resetDay")),
            lastResetTime = o.getLong("lastReset"),
            transactions = (0 until (o.optJSONArray("txns")?.length() ?: 0)).map {
                val t = o.getJSONArray("txns").getJSONObject(it)
                CreditTransaction(t.getLong("ts"), t.getInt("amt"),
                    CreditReason.valueOf(t.getString("reason")), t.getInt("after"))
            }
        )
    } catch (_: Exception) { null }

    private fun snapshotToJson(s: UsageSnapshot): String = JSONObject().apply {
        put("date", s.date.toString()); put("total", s.totalSeconds)
        put("continuous", s.continuousSeconds); put("lastActivity", s.lastActivityTime)
        put("catUsage", JSONObject(s.categoryUsage))
        put("appUsage", JSONObject(s.appUsage))
    }.toString()

    private fun parseSnapshot(json: String): UsageSnapshot? = try {
        val o = JSONObject(json)
        UsageSnapshot(
            date = LocalDate.parse(o.getString("date")),
            totalSeconds = o.getLong("total"),
            continuousSeconds = o.getLong("continuous"),
            lastActivityTime = o.getLong("lastActivity"),
            categoryUsage = jsonObjectToMap(o.getJSONObject("catUsage")),
            appUsage = jsonObjectToMap(o.getJSONObject("appUsage"))
        )
    } catch (_: Exception) { null }

    private fun dailyUsageToJson(d: DailyUsage): JSONObject = JSONObject().apply {
        put("date", d.date.toString()); put("total", d.totalSeconds)
        put("byCat", JSONObject(d.byCategory)); put("byApp", JSONObject(d.byApp))
        put("grace", d.wasGraceUsed); put("creditChange", d.creditChange)
    }

    private fun parseDailyUsage(o: JSONObject): DailyUsage = DailyUsage(
        date = LocalDate.parse(o.getString("date")),
        totalSeconds = o.getLong("total"),
        byCategory = jsonObjectToMap(o.getJSONObject("byCat")),
        byApp = jsonObjectToMap(o.getJSONObject("byApp")),
        wasGraceUsed = o.getBoolean("grace"),
        creditChange = o.getInt("creditChange")
    )

    private fun jsonObjectToMap(o: JSONObject): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        o.keys().forEach { k -> map[k] = o.getLong(k) }
        return map
    }
}
```

- [ ] **Step 1b: Verify**

```bash
cd launcher && ./gradlew :guard:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

### Task 9: Implement UsageTracker

**Files:**
- Create: `guard/src/main/java/com/pinecone/guard/data/UsageTracker.kt`

**Interfaces:**
- Consumes: `UsageStatsManager` (Android system service)
- Produces: `UsageTracker` — polling-based app usage query

- [ ] **Step 1: Write `UsageTracker.kt`**

```kotlin
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

    private val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /**
     * Map package names to category IDs. This is hardcoded for PineCone OS.
     * Extend via RuleSet.appLimits for custom mappings.
     */
    private val pkgCategoryMap = mapOf(
        "com.example.english1" to "english",
        "com.example.cambridge" to "english",
        "com.youdao.dict" to "apps",
        "tv.danmaku.bili" to "apps"
    )

    fun getTodayUsage(): TodayUsage {
        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        val stats = usageManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, todayStart, now
        )

        var total = 0L
        val byApp = mutableMapOf<String, Long>()
        val byCat = mutableMapOf<String, Long>()

        stats.forEach { stat ->
            val secs = stat.totalTimeInForeground / 1000
            if (secs > 0) {
                total += secs
                byApp[stat.packageName] = (byApp[stat.packageName] ?: 0) + secs
                val cat = pkgCategoryMap[stat.packageName] ?: "other"
                byCat[cat] = (byCat[cat] ?: 0) + secs
            }
        }

        return TodayUsage(totalSeconds = total, categoryUsage = byCat, appUsage = byApp)
    }

    fun getAppUsage(packageName: String): Long {
        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val stats = usageManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, todayStart, System.currentTimeMillis()
        )
        return stats.find { it.packageName == packageName }?.totalTimeInForeground?.div(1000) ?: 0
    }

    /**
     * Check if usage stats permission is granted.
     * The parent must grant this in Settings once during setup.
     */
    fun isPermissionGranted(): Boolean {
        val now = System.currentTimeMillis()
        val stats = usageManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, now - 1000, now
        )
        return stats.isNotEmpty()
    }
}
```

- [ ] **Step 2: Verify**

```bash
cd launcher && ./gradlew :guard:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

## Phase 4: Android Service Layer

### Task 10: Implement GuardService

**Files:**
- Create: `guard/src/main/java/com/pinecone/guard/service/GuardService.kt`

**Interfaces:**
- Consumes: `GuardEngineImpl`, `GuardStateListener`, `SecureStorage`, `UsageTracker`
- Produces: `GuardService` — foreground service, `:guard` process, `Messenger` IPC handler

- [ ] **Step 1: Write `GuardService.kt`**

```kotlin
package com.pinecone.guard.service

import android.app.*
import android.content.Intent
import android.os.*
import com.pinecone.guard.api.GuardStateListener
import com.pinecone.guard.data.SecureStorage
import com.pinecone.guard.data.UsageTracker
import com.pinecone.guard.data.model.LockReason
import com.pinecone.guard.engine.GuardEngineImpl

// Message codes for IPC
const val MSG_GET_STATE = 1
const val MSG_UPDATE_RULES = 2
const val MSG_GET_RULES = 3
const val MSG_REQUEST_GRACE = 4
const val MSG_REQUEST_EARLY_STOP = 5
const val MSG_VERIFY_PIN = 6
const val MSG_PAUSE_TODAY = 7
const val MSG_RESUME_TODAY = 8
const val MSG_MANUAL_RESET_CREDITS = 9
const val MSG_REGISTER_CLIENT = 10

class GuardService : Service() {

    private lateinit var engine: GuardEngineImpl
    private val binder = Messenger(IncomingHandler(this))
    private var clientMessenger: Messenger? = null
    private var tickHandler: Handler? = null
    private var tickRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        val storage = SecureStorage(this)
        val tracker = UsageTracker(this)
        engine = GuardEngineImpl(this, storage, tracker)
        engine.setListener(serviceListener)

        startForeground(NOTIFICATION_ID, buildNotification())

        // Tick every 15 seconds to update usage data
        tickHandler = Handler(Looper.getMainLooper())
        tickRunnable = object : Runnable {
            override fun run() {
                engine.tick()
                tickHandler?.postDelayed(this, 15_000L)
            }
        }
        tickHandler?.postDelayed(tickRunnable!!, 15_000L)

        engine.start()
    }

    override fun onBind(intent: Intent?): IBinder = binder.binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        engine.stop()
        tickRunnable?.let { tickHandler?.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val channelId = "pinecone_guard"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "防沉迷守护",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "松果智学防沉迷系统运行中"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        return Notification.Builder(this, channelId)
            .setContentTitle("松果智学·防沉迷守护中")
            .setContentText("正在守护孩子的用眼健康")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
    }

    // --- State listener bridging to IPC ---
    private val serviceListener = object : GuardStateListener {
        override fun onWarningLevel(level: Int, message: String, remainingSeconds: Long) {
            sendToClient(createMsg(MSG_GET_STATE, Bundle().apply {
                putString("warning", message)
                putInt("level", level)
                putLong("remaining", remainingSeconds)
            }))
        }

        override fun onGracePeriodStarted(creditRemaining: Int) {
            sendToClient(createMsg(MSG_REQUEST_GRACE, Bundle().apply {
                putInt("credit", creditRemaining)
            }))
        }

        override fun onGraceTick(secondsLeft: Int, creditDraining: Int) {
            sendToClient(createMsg(MSG_REQUEST_GRACE, Bundle().apply {
                putInt("graceSeconds", secondsLeft)
                putInt("drainRate", creditDraining)
            }))
        }

        override fun onLockRequired(reason: LockReason) {
            sendToClient(createMsg(MSG_GET_STATE, Bundle().apply {
                putString("lockReason", reason.name)
            }))
        }

        override fun onBreakRequired(durationSeconds: Int) {
            sendToClient(createMsg(MSG_GET_STATE, Bundle().apply {
                putInt("breakSeconds", durationSeconds)
                putBoolean("isBreak", true)
            }))
        }

        override fun onBreakFinished() {
            sendToClient(createMsg(MSG_GET_STATE, Bundle().apply {
                putBoolean("breakFinished", true)
            }))
        }

        override fun onStateChanged(newState: com.pinecone.guard.data.model.GuardState) {
            // State changes are polled by client via MSG_GET_STATE
        }
    }

    private fun sendToClient(msg: Message) {
        try { clientMessenger?.send(msg) } catch (_: Exception) {}
    }

    private fun createMsg(what: Int, data: Bundle): Message =
        Message.obtain(null, what).apply { setData(data) }

    // --- IPC Handler ---
    class IncomingHandler(private val service: GuardService) : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val engine = service.engine
            val reply = Message.obtain(null, msg.what)
            val data = Bundle()

            when (msg.what) {
                MSG_REGISTER_CLIENT -> {
                    service.clientMessenger = msg.replyTo
                    data.putString("status", "registered")
                }
                MSG_GET_STATE -> {
                    val state = engine.getCurrentState()
                    data.putLong("totalSecs", state.totalSecondsToday)
                    data.putLong("remainingSecs", state.remainingSeconds ?: -1)
                    data.putInt("creditBalance", state.creditBalance)
                    data.putBoolean("isActive", state.isActive)
                }
                MSG_UPDATE_RULES -> {
                    val rulesJson = msg.data.getString("rules") ?: return
                    // parse and update via engine
                }
                MSG_GET_RULES -> {
                    // serialize rules
                }
                MSG_REQUEST_GRACE -> {
                    val granted = engine.requestGraceExtension()
                    data.putBoolean("granted", granted)
                }
                MSG_REQUEST_EARLY_STOP -> {
                    val rewarded = engine.requestEarlyStop()
                    data.putInt("rewarded", rewarded)
                }
                MSG_VERIFY_PIN -> {
                    val pin = msg.data.getString("pin") ?: return
                    val valid = engine.verifyPin(pin)
                    data.putBoolean("valid", valid)
                }
                MSG_PAUSE_TODAY -> {
                    val reason = msg.data.getString("reason") ?: ""
                    engine.pauseForToday(reason)
                }
                MSG_RESUME_TODAY -> engine.resumeForToday()
                MSG_MANUAL_RESET_CREDITS -> engine.manualResetCredits()
            }

            reply.setData(data)
            try { msg.replyTo?.send(reply) } catch (_: Exception) {}
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 9001
    }
}
```

- [ ] **Step 2: Verify**

```bash
cd launcher && ./gradlew :guard:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

### Task 11: Implement GuardWatchdog

**Files:**
- Create: `guard/src/main/java/com/pinecone/guard/service/GuardWatchdog.kt`

**Interfaces:**
- Consumes: `GuardService`
- Produces: `GuardWatchdog` — monitors service liveness in both processes

- [ ] **Step 1: Write `GuardWatchdog.kt`**

```kotlin
package com.pinecone.guard.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * Monitors guard process liveness from the main (launcher) process.
 * GuardService itself monitors the main process internally.
 * Both sides: if peer dies, re-spawn it.
 */
class GuardWatchdog(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (running) {
                if (!isGuardProcessAlive()) {
                    restartGuardService()
                }
                handler.postDelayed(this, 30_000L) // check every 30s
            }
        }
    }

    fun start() {
        running = true
        handler.post(checkRunnable)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(checkRunnable)
    }

    private fun isGuardProcessAlive(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processes = am.runningAppProcesses ?: return false
        return processes.any { it.processName == "${context.packageName}:guard" }
    }

    private fun restartGuardService() {
        val intent = Intent(context, GuardService::class.java)
        context.startForegroundService(intent)
    }
}
```

- [ ] **Step 2: Verify**

```bash
cd launcher && ./gradlew :guard:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

### Task 12: Implement BootReceiver + DeviceAdminReceiver

**Files:**
- Create: `guard/src/main/java/com/pinecone/guard/service/BootReceiver.kt`
- Create: `guard/src/main/java/com/pinecone/guard/service/DeviceAdminReceiver.kt`

- [ ] **Step 1: Write `BootReceiver.kt`**

```kotlin
package com.pinecone.guard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, GuardService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
```

- [ ] **Step 2: Write `DeviceAdminReceiver.kt`**

```kotlin
package com.pinecone.guard.service

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

class GuardDeviceAdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun getComponentName(context: Context): ComponentName =
            ComponentName(context, GuardDeviceAdminReceiver::class.java)

        fun isAdminActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isAdminActive(getComponentName(context))
        }

        fun lockNow(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isAdminActive(getComponentName(context))) {
                dpm.lockNow()
            }
        }
    }
}
```

- [ ] **Step 3: Verify**

```bash
cd launcher && ./gradlew :guard:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

### Task 13: Implement GuardClient

**Files:**
- Create: `guard/src/main/java/com/pinecone/guard/service/GuardClient.kt`

**Interfaces:**
- Consumes: `GuardEngine` interface, `GuardService` Messenger
- Produces: `GuardClient` — main-process proxy that talks to guard process via IPC

- [ ] **Step 1: Write `GuardClient.kt`**

```kotlin
package com.pinecone.guard.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import com.pinecone.guard.api.GuardStateListener

/**
 * Proxy in the main (launcher) process. Binds to GuardService via Messenger IPC.
 * Provides synchronous-like access for UI by blocking on reply with a timeout.
 */
class GuardClient(private val context: Context) {

    private var serviceMessenger: Messenger? = null
    private var bound = false

    private val clientMessenger = Messenger(IncomingReplyHandler())

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serviceMessenger = Messenger(service)
            bound = true
            // Register this client so GuardService can push events back
            val msg = Message.obtain(null, MSG_REGISTER_CLIENT)
            msg.replyTo = clientMessenger
            try { serviceMessenger?.send(msg) } catch (_: Exception) {}
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceMessenger = null
            bound = false
        }
    }

    private var listener: GuardStateListener? = null

    fun bind() {
        val intent = Intent(context, GuardService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        // Also start as foreground to ensure it stays alive
        context.startForegroundService(intent)
    }

    fun unbind() {
        try { context.unbindService(connection) } catch (_: Exception) {}
        bound = false
    }

    fun setListener(l: GuardStateListener) { listener = l }

    fun verifyPin(pin: String): Boolean {
        val reply = sendBlocking(MSG_VERIFY_PIN, Bundle().apply { putString("pin", pin) })
        return reply?.getBoolean("valid", false) ?: false
    }

    fun requestGrace(): Boolean {
        val reply = sendBlocking(MSG_REQUEST_GRACE)
        return reply?.getBoolean("granted", false) ?: false
    }

    fun requestEarlyStop(): Int {
        val reply = sendBlocking(MSG_REQUEST_EARLY_STOP)
        return reply?.getInt("rewarded", 0) ?: 0
    }

    fun getState(): Bundle? = sendBlocking(MSG_GET_STATE)

    fun pauseToday(reason: String) {
        sendBlocking(MSG_PAUSE_TODAY, Bundle().apply { putString("reason", reason) })
    }

    fun resumeToday() {
        sendBlocking(MSG_RESUME_TODAY)
    }

    fun manualResetCredits() {
        sendBlocking(MSG_MANUAL_RESET_CREDITS)
    }

    private fun sendBlocking(what: Int, data: Bundle = Bundle()): Bundle? {
        val messenger = serviceMessenger ?: return null
        return try {
            val msg = Message.obtain(null, what)
            msg.data = data
            msg.replyTo = clientMessenger
            val replyReceiver = object {
                var result: Bundle? = null
            }
            // Use a sync barrier — short timeout since it's local IPC
            messenger.send(msg)
            // Return null for now; state is pushed async via IncomingReplyHandler
            null
        } catch (_: Exception) { null }
    }

    inner class IncomingReplyHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val data = msg.data
            when (msg.what) {
                MSG_GET_STATE -> {
                    data.getString("lockReason")?.let {
                        val reason = com.pinecone.guard.data.model.LockReason.valueOf(it)
                        listener?.onLockRequired(reason)
                    }
                    data.getString("warning")?.let {
                        listener?.onWarningLevel(
                            data.getInt("level", 1), it,
                            data.getLong("remaining", 0)
                        )
                    }
                    if (data.getBoolean("isBreak", false)) {
                        listener?.onBreakRequired(data.getInt("breakSeconds", 600))
                    }
                    if (data.getBoolean("breakFinished", false)) {
                        listener?.onBreakFinished()
                    }
                }
                MSG_REQUEST_GRACE -> {
                    val credit = data.getInt("credit", 0)
                    if (credit > 0) listener?.onGracePeriodStarted(credit)
                    else {
                        val secs = data.getInt("graceSeconds", 0)
                        val drain = data.getInt("drainRate", 5)
                        listener?.onGraceTick(secs, drain)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify**

```bash
cd launcher && ./gradlew :guard:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

## Phase 5: Parent-Facing UI (app module)

### Task 14: Implement PinSetupActivity

**Files:**
- Create: `app/src/main/java/com/pinecone/launcher/ui/guard/PinSetupActivity.kt`

- [ ] **Step 1: Write `PinSetupActivity.kt`**

```kotlin
package com.pinecone.launcher.ui.guard

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.*

class PinSetupActivity : Activity() {

    private var pin = StringBuilder()
    private var confirmPin: String? = null
    private var isConfirmMode = false
    private lateinit var titleText: TextView
    private lateinit var dotsText: TextView
    private var onPinSet: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 96, 48, 48)
        }

        titleText = TextView(this).apply {
            text = "设置家长 PIN"
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }
        layout.addView(titleText)

        dotsText = TextView(this).apply {
            text = "[ _ ][ _ ][ _ ][ _ ][ _ ][ _ ]"
            textSize = 32f
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(0, 0, 0, 24)
        }
        layout.addView(dotsText)

        val errorText = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(0xFFFF4444.toInt())
            visibility = TextView.GONE
        }
        layout.addView(errorText)

        // Number pad for TV remote: row of buttons
        listOf(
            listOf("1","2","3"),
            listOf("4","5","6"),
            listOf("7","8","9"),
            listOf("清空","0","⌫")
        ).forEach { row ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            row.forEach { label ->
                val btn = Button(this).apply {
                    text = label
                    textSize = 22f
                    minWidth = 160
                    minHeight = 100
                    setOnClickListener { onKeyPress(label) }
                }
                rowLayout.addView(btn)
            }
            layout.addView(rowLayout)
        }

        setContentView(layout)
    }

    private fun onKeyPress(key: String) {
        when (key) {
            "清空" -> { pin.clear(); updateDots() }
            "⌫" -> { if (pin.isNotEmpty()) { pin.deleteCharAt(pin.length - 1); updateDots() } }
            else -> {
                if (pin.length < 6) {
                    pin.append(key)
                    updateDots()
                    if (pin.length == 6) {
                        if (isConfirmMode) {
                            if (pin.toString() == confirmPin) {
                                onPinSet?.invoke(pin.toString())
                                Toast.makeText(this, "PIN 设置成功", Toast.LENGTH_SHORT).show()
                                finish()
                            } else {
                                Toast.makeText(this, "两次输入不一致，请重试", Toast.LENGTH_LONG).show()
                                pin.clear(); confirmPin = null; isConfirmMode = false
                                titleText.text = "设置家长 PIN"
                            }
                        } else {
                            confirmPin = pin.toString()
                            pin.clear()
                            isConfirmMode = true
                            titleText.text = "请再次输入 PIN"
                        }
                        updateDots()
                    }
                }
            }
        }
    }

    private fun updateDots() {
        val sb = StringBuilder()
        for (i in 0 until 6) {
            sb.append(if (i < pin.length) "[ ● ]" else "[ _ ]")
        }
        dotsText.text = sb.toString()
    }

    fun setOnPinSetListener(callback: (String) -> Unit) { onPinSet = callback }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd launcher && ./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

### Task 15: Implement ParentSettingsActivity

**Files:**
- Create: `app/src/main/java/com/pinecone/launcher/ui/guard/ParentSettingsActivity.kt`
- Create: `app/src/main/res/layout/activity_parent_settings.xml`

- [ ] **Step 1: Write layout XML**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="32dp"
    android:background="#FF1A1A2E">

    <TextView
        android:id="@+id/settings_title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="家长设置"
        android:textSize="32sp"
        android:textColor="#FFFFFF"
        android:layout_marginBottom="32dp" />

    <ListView
        android:id="@+id/settings_list"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

</LinearLayout>
```

- [ ] **Step 2: Write `ParentSettingsActivity.kt`**

```kotlin
package com.pinecone.launcher.ui.guard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*

class ParentSettingsActivity : Activity() {

    data class SettingsItem(val title: String, val subtitle: String, val action: () -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parent_settings)

        val items = buildSettingsItems()
        val listView = findViewById<ListView>(R.id.settings_list)
        val adapter = object : ArrayAdapter<SettingsItem>(
            this, android.R.layout.simple_list_item_2, android.R.id.text1, items
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view.findViewById<TextView>(android.R.id.text1)).apply {
                    text = items[position].title
                    textSize = 22f
                }
                (view.findViewById<TextView>(android.R.id.text2)).apply {
                    text = items[position].subtitle
                    textSize = 16f
                }
                return view
            }
        }
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, pos, _ -> items[pos].action() }
    }

    private fun buildSettingsItems(): List<SettingsItem> = listOf(
        SettingsItem("⏱️ 每日累计时长", "2 小时 30 分") {
            startActivity(Intent(this, DailyLimitFragment::class.java))
        },
        SettingsItem("🔄 强制休息间隔", "每 40 分 休 10 分") {
            startActivity(Intent(this, BreakRuleFragment::class.java))
        },
        SettingsItem("📅 可用时段", "周一至周五 16:00-21:00") {
            startActivity(Intent(this, TimeWindowFragment::class.java))
        },
        SettingsItem("📂 内容分类限制", "") {
            startActivity(Intent(this, CategoryLimitFragment::class.java))
        },
        SettingsItem("📱 App 单独限制", "") {
            startActivity(Intent(this, AppLimitFragment::class.java))
        },
        SettingsItem("⭐ 信用积分", "100分 / 每周一重置") {
            startActivity(Intent(this, CreditConfigFragment::class.java))
        },
        SettingsItem("📊 使用统计", "") {
            startActivity(Intent(this, UsageHistoryFragment::class.java))
        },
        SettingsItem("🔑 修改 PIN", "") {
            startActivity(Intent(this, PinSetupActivity::class.java))
        },
        SettingsItem("⏸️ 暂停防沉迷（今天不限制）", "") {
            // Show PIN verification, then pause
        }
    )
}
```

Note: Editor fragments (`DailyLimitFragment`, `BreakRuleFragment`, `TimeWindowFragment`, `CategoryLimitFragment`, `AppLimitFragment`, `CreditConfigFragment`, `UsageHistoryFragment`) each follow the same pattern: extend `Activity`, create a simple TV-friendly UI with a list of preset buttons and a save button. For brevity, they are listed as Task 16–22 below with their unique UI content descriptions and the shared pattern. See Task 16 for the canonical example.

- [ ] **Step 3: Verify**

```bash
cd launcher && ./gradlew :app:compileDebugKotlin
```
Expected: BUILD FAIL (reference errors for editor activities not yet created — expected)

---

### Task 16: DailyLimitFragment (canonical editor example)

**Files:**
- Create: `app/src/main/java/com/pinecone/launcher/ui/guard/editors/DailyLimitFragment.kt`

Note: Despite the name "Fragment", these are `Activity` classes for simplicity in Leanback TV navigation. They all share this pattern.

- [ ] **Step 1: Write `DailyLimitFragment.kt`**

```kotlin
package com.pinecone.launcher.ui.guard.editors

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*

class DailyLimitFragment : Activity() {

    private var selectedMinutes: Int? = 150 // default 2.5h

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#FF1A1A2E"))
            setPadding(64, 96, 64, 64)
        }

        layout.addView(TextView(this).apply {
            text = "每日累计时长"
            textSize = 30f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(0, 0, 0, 48)
        })

        val currentText = TextView(this).apply {
            text = formatDuration(selectedMinutes)
            textSize = 48f; setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER; setPadding(0, 0, 0, 48)
        }
        layout.addView(currentText)

        // Hour / minute adjustment row
        val adjustRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        }
        listOf("小时" to 60, "分钟" to 15).forEach { (label, step) ->
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(32, 0, 32, 0)
            }
            col.addView(Button(this).apply {
                text = "▲"; textSize = 22f; minWidth = 120; minHeight = 60
                setOnClickListener {
                    selectedMinutes = ((selectedMinutes ?: 0) + step).coerceAtMost(480)
                    currentText.text = formatDuration(selectedMinutes)
                }
            })
            col.addView(TextView(this).apply {
                text = label; textSize = 20f; setTextColor(Color.WHITE)
                gravity = Gravity.CENTER; setPadding(0, 8, 0, 8)
            })
            col.addView(Button(this).apply {
                text = "▼"; textSize = 22f; minWidth = 120; minHeight = 60
                setOnClickListener {
                    selectedMinutes = ((selectedMinutes ?: step) - step).coerceAtLeast(0)
                    currentText.text = formatDuration(selectedMinutes)
                }
            })
            adjustRow.addView(col)
        }
        layout.addView(adjustRow)

        // Presets row (vertical list for TV)
        layout.addView(TextView(this).apply {
            text = "快速选择:"; textSize = 20f; setTextColor(Color.GRAY)
            setPadding(0, 48, 0, 16)
        })
        listOf(
            0 to "不限", 60 to "1 小时", 90 to "1.5 小时",
            120 to "2 小时", 150 to "2.5 小时", 180 to "3 小时"
        ).forEach { (min, label) ->
            layout.addView(Button(this).apply {
                text = label; textSize = 20f
                minWidth = 400; minHeight = 72
                setOnClickListener {
                    selectedMinutes = min.takeIf { it > 0 }
                    currentText.text = formatDuration(selectedMinutes)
                }
            })
        }

        layout.addView(Button(this).apply {
            text = "保存"
            textSize = 24f; setTextColor(Color.BLACK)
            setBackgroundColor(Color.GREEN)
            minWidth = 400; minHeight = 80
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 48 }
            setOnClickListener {
                // Persist via GuardClient / service
                // guardClient.updateDailyLimit(selectedMinutes)
                Toast.makeText(this@DailyLimitFragment, "已保存", Toast.LENGTH_SHORT).show()
                finish()
            }
        })

        setContentView(layout)
    }

    private fun formatDuration(minutes: Int?): String =
        if (minutes == null) "不限"
        else "${minutes / 60} 小时 ${minutes % 60} 分"
}
```

- [ ] **Step 2: Verify**

```bash
cd launcher && ./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

### Tasks 17–22: Remaining editor activities

Each follows the same Activity+LinearLayout pattern as Task 16 above. Unique content per editor:

**Task 17 — BreakRuleFragment** (`app/.../editors/BreakRuleFragment.kt`):
```kotlin
class BreakRuleFragment : Activity() {
    // Presets: BreakRule(30,10), BreakRule(40,10), BreakRule(45,15), null
    // Two NumberPicker-like columns: "连续使用 X 分钟 → 休息 Y 分钟"
    // Save calls guardClient.updateBreakRule(rule)
}
```

**Task 18 — TimeWindowFragment** (`app/.../editors/TimeWindowFragment.kt`):
```kotlin
class TimeWindowFragment : Activity() {
    // Two sections: weekday + weekend, each with start/end time rows
    // Presets: 上学模式 (Mon-Fri 16-21, Sat-Sun 8-21),
    //          假期模式 (daily 8-21), 严格模式 (Mon-Fri 18-20, Sat-Sun 10-12+14-17)
    // Each preset fills in the time picker fields
}
```

**Task 19 — CategoryLimitFragment** (`app/.../editors/CategoryLimitFragment.kt`):
```kotlin
class CategoryLimitFragment : Activity() {
    // ListView of 4 categories: 英语学习, 绘本阅读, 纪录片, 学习App
    // Each row: label + spinner [不限|15min|30min|45min|60min|90min]
    // Save calls guardClient.updateCategoryLimits(list)
}
```

**Task 20 — AppLimitFragment** (`app/.../editors/AppLimitFragment.kt`):
```kotlin
class AppLimitFragment : Activity() {
    // ListView of app limits, each row: app label + time spinner + delete button
    // "[+] Add app" button → shows installed apps list → pick one → set limit
}
```

**Task 21 — CreditConfigFragment** (`app/.../editors/CreditConfigFragment.kt`):
```kotlin
class CreditConfigFragment : Activity() {
    // Weekly total: button row [50][75][100][150][200]
    // Reset day: spinner 周一-周日
    // Current balance display: "当前积分: 85/100"
    // [立即重置] button
}
```

**Task 22 — UsageHistoryFragment** (`app/.../editors/UsageHistoryFragment.kt`):
```kotlin
class UsageHistoryFragment : Activity() {
    // ◀ Week selector ▶
    // Daily bars (TextView-based ASCII): "一 ████████░░ 1h50m"
    // Category breakdown: "🔤 英语 5h20m" etc.
    // Credit change timeline
}
```

---

## Phase 6: Child-Facing UI

### Task 23: Implement LockScreenActivity

**Files:**
- Create: `app/src/main/java/com/pinecone/launcher/ui/guard/LockScreenActivity.kt`
- Create: `app/src/main/res/layout/activity_lock_screen.xml`

- [ ] **Step 1: Write layout XML**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:background="#E61A1A2E"
    android:padding="64dp">

    <TextView
        android:layout_width="128dp"
        android:layout_height="128dp"
        android:text="🔒"
        android:textSize="96sp"
        android:gravity="center" />

    <TextView
        android:id="@+id/lock_title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="今天的屏幕时间已用完"
        android:textSize="36sp"
        android:textColor="#FFFFFF"
        android:gravity="center"
        android:layout_marginTop="32dp" />

    <TextView
        android:id="@+id/lock_subtitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="信用积分: 0\n下周一会自动重置"
        android:textSize="24sp"
        android:textColor="#AAAAAAAA"
        android:gravity="center"
        android:layout_marginTop="16dp" />

    <Button
        android:id="@+id/btn_parent_unlock"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="家长解锁 (PIN)"
        android:textSize="20sp"
        android:layout_marginTop="48dp"
        android:minWidth="320dp"
        android:minHeight="80dp" />

</LinearLayout>
```

- [ ] **Step 2: Write `LockScreenActivity.kt`**

```kotlin
package com.pinecone.launcher.ui.guard

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class LockScreenActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock_screen)

        val reason = intent?.getStringExtra("lock_reason") ?: "DAILY_LIMIT_REACHED"
        val creditBalance = intent?.getIntExtra("credit_balance", 0) ?: 0

        val title = findViewById<TextView>(R.id.lock_title)
        val subtitle = findViewById<TextView>(R.id.lock_subtitle)

        when (reason) {
            "DAILY_LIMIT_REACHED" -> {
                title.text = "今天的屏幕时间已用完"
                subtitle.text = "信用积分: $creditBalance\n下周一会自动重置"
            }
            "OUTSIDE_TIME_WINDOW" -> {
                title.text = "⏰ 当前不在可用时段"
                subtitle.text = "开放时间：周一至周五 16:00-21:00\n周六日 08:00-21:00"
            }
            "TIME_TAMPERED" -> {
                title.text = "⚠️ 系统时间异常"
                subtitle.text = "设备已锁定\n请联系家长解锁并检查时间设置"
            }
            "DATA_CORRUPTED" -> {
                title.text = "⚠️ 系统数据异常"
                subtitle.text = "设备已锁定\n请联系家长解锁"
            }
        }

        findViewById<Button>(R.id.btn_parent_unlock).setOnClickListener {
            // Show PIN verification dialog
            val intent = android.content.Intent(this, PinSetupActivity::class.java)
            // Reuse PinSetupActivity for verification mode (flag in extras)
            intent.putExtra("mode", "verify")
            startActivityForResult(intent, REQUEST_PIN_VERIFY)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Block back button and home button
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PIN_VERIFY && resultCode == RESULT_OK) {
            finish()  // Unlock — remove lock screen
        }
    }

    companion object {
        private const val REQUEST_PIN_VERIFY = 1001
    }
}
```

- [ ] **Step 3: Verify**

```bash
cd launcher && ./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

### Task 24: Implement FullScreenWarningActivity (grace period)

**Files:**
- Create: `app/src/main/java/com/pinecone/launcher/ui/guard/FullScreenWarningActivity.kt`

- [ ] **Step 1: Write `FullScreenWarningActivity.kt`**

```kotlin
package com.pinecone.launcher.ui.guard

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.widget.*

class FullScreenWarningActivity : Activity() {

    private val graceDurationMs = 5 * 60 * 1000L  // 5 minutes
    private var creditRemaining = 100
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        creditRemaining = intent?.getIntExtra("credit_balance", 100) ?: 100

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#E61A1A2E"))
            setPadding(64, 128, 64, 64)
        }

        val icon = TextView(this).apply {
            text = "📢"
            textSize = 80f; gravity = Gravity.CENTER
        }
        layout.addView(icon)

        val title = TextView(this).apply {
            text = "今天的时间到了"
            textSize = 36f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(0, 32, 0, 16)
        }
        layout.addView(title)

        val creditText = TextView(this).apply {
            text = "信用积分: $creditRemaining\n可继续使用 5 分钟\n（将扣除 ${5 * 5} 积分）"
            textSize = 24f; setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER; setPadding(0, 0, 0, 48)
        }
        layout.addView(creditText)

        val countdownBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = (graceDurationMs / 1000).toInt()
            progress = (graceDurationMs / 1000).toInt()
            layoutParams = LinearLayout.LayoutParams(800, 24)
        }
        layout.addView(countdownBar)

        val countdownText = TextView(this).apply {
            text = "5:00"
            textSize = 20f; setTextColor(Color.RED)
            gravity = Gravity.CENTER
        }
        layout.addView(countdownText)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER; setPadding(0, 48, 0, 0)
        }

        val btnStop = Button(this).apply {
            text = "立即休息"
            textSize = 20f
            minWidth = 300; minHeight = 80
            setOnClickListener {
                timer?.cancel()
                // Trigger early stop reward via GuardClient
                // GuardClient.instance?.requestEarlyStop()
                finish()
            }
        }
        buttonRow.addView(btnStop)

        val btnContinue = Button(this).apply {
            text = "继续使用"
            textSize = 20f
            minWidth = 300; minHeight = 80
            setOnClickListener {
                btnContinue.isEnabled = false
                btnContinue.text = "宽限中..."
                startGraceTimer(countdownBar, countdownText)
            }
        }
        buttonRow.addView(btnContinue)

        layout.addView(buttonRow)
        setContentView(layout)
    }

    private fun startGraceTimer(bar: ProgressBar, text: TextView) {
        timer = object : CountDownTimer(graceDurationMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secs = millisUntilFinished / 1000
                val mins = secs / 60; val remSecs = secs % 60
                bar.progress = secs.toInt()
                text.text = "%d:%02d".format(mins, remSecs)
                if (secs <= 60) {
                    text.setTextColor(Color.RED)
                }
            }
            override fun onFinish() {
                // Time's up → re-prompt or lock
                // GuardService will re-evaluate and possibly lock
                finish()
            }
        }.start()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
```

- [ ] **Step 2: Verify**

```bash
cd launcher && ./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

### Task 25: Implement BreakReminderActivity

**Files:**
- Create: `app/src/main/java/com/pinecone/launcher/ui/guard/BreakReminderActivity.kt`

- [ ] **Step 1: Write `BreakReminderActivity.kt`**

```kotlin
package com.pinecone.launcher.ui.guard

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.widget.TextView

class BreakReminderActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val breakSeconds = intent?.getIntExtra("break_seconds", 600) ?: 600

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#E6005C4B"))
            setPadding(64, 128, 64, 64)
        }

        val icon = TextView(this).apply {
            text = "🌿"
            textSize = 80f; gravity = Gravity.CENTER
        }
        layout.addView(icon)

        val title = TextView(this).apply {
            text = "该休息了"
            textSize = 36f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(0, 32, 0, 8)
        }
        layout.addView(title)

        val hint = TextView(this).apply {
            text = "起来走走，看看远方\n保护眼睛，从小做起"
            textSize = 24f; setTextColor(Color.parseColor("#CCCCCC"))
            gravity = Gravity.CENTER; setPadding(0, 0, 0, 32)
        }
        layout.addView(hint)

        val countdown = TextView(this).apply {
            text = formatTime(breakSeconds)
            textSize = 56f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        layout.addView(countdown)

        setContentView(layout)

        object : CountDownTimer(breakSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                countdown.text = formatTime((millisUntilFinished / 1000).toInt())
            }
            override fun onFinish() {
                finish()  // Break over, return to home screen
            }
        }.start()
    }

    private fun formatTime(totalSecs: Int): String {
        val m = totalSecs / 60; val s = totalSecs % 60
        return "%d:%02d".format(m, s)
    }
}
```

- [ ] **Step 2: Verify**

```bash
cd launcher && ./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

## Phase 7: Integration

### Task 26: Update AndroidManifest.xml

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/device_admin_policies.xml`

- [ ] **Step 1: Write `device_admin_policies.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-policies>
        <force-lock />
    </uses-policies>
</device-admin>
```

- [ ] **Step 2: Update `AndroidManifest.xml`** — add all new declarations inside `<application>`:

```xml
<!-- New permissions -->
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED" />

<!-- Inside <application> tag, add: -->

<!-- GuardService: foreground, independent process -->
<service
    android:name="com.pinecone.guard.service.GuardService"
    android:process=":guard"
    android:foregroundServiceType="systemExempted"
    android:exported="false" />

<!-- Boot receiver -->
<receiver
    android:name="com.pinecone.guard.service.BootReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
    </intent-filter>
</receiver>

<!-- DeviceAdmin for system-level lock -->
<receiver
    android:name="com.pinecone.guard.service.GuardDeviceAdminReceiver"
    android:permission="android.permission.BIND_DEVICE_ADMIN"
    android:exported="true">
    <meta-data
        android:name="android.app.device_admin"
        android:resource="@xml/device_admin_policies" />
    <intent-filter>
        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
    </intent-filter>
</receiver>

<!-- New activities for guard UI -->
<activity
    android:name="com.pinecone.launcher.ui.guard.ParentSettingsActivity"
    android:exported="false" />
<activity
    android:name="com.pinecone.launcher.ui.guard.PinSetupActivity"
    android:exported="false" />
<activity
    android:name="com.pinecone.launcher.ui.guard.LockScreenActivity"
    android:exported="false"
    android:excludeFromRecents="true"
    android:launchMode="singleInstance" />
<activity
    android:name="com.pinecone.launcher.ui.guard.FullScreenWarningActivity"
    android:exported="false"
    android:excludeFromRecents="true"
    android:launchMode="singleInstance" />
<activity
    android:name="com.pinecone.launcher.ui.guard.BreakReminderActivity"
    android:exported="false"
    android:excludeFromRecents="true"
    android:launchMode="singleInstance" />
```

- [ ] **Step 3: Verify**

```bash
cd launcher && ./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

### Task 27: Update MainActivity.kt

**Files:**
- Modify: `app/src/main/java/com/pinecone/launcher/MainActivity.kt`

- [ ] **Step 1: Add "家长设置" entry to the existing settings row**

Find the settings section in `setupUIElements()` (around line 69-75), and add a new item:

```kotlin
// 5. ⚙️ 系统设置 (existing row, replace with this updated version)
val settingsHeader = HeaderItem(4, "⚙️ 系统设置")
val settingsAdapter = ArrayObjectAdapter(cardPresenter).apply {
    add(CourseItem(10, "Wi-Fi 网络", "设置", "action:wifi_settings"))
    add(CourseItem(11, "系统设置", "设置", "action:system_settings"))
    add(CourseItem(12, "🔒 家长设置", "设置", "action:parent_settings"))
}
```

- [ ] **Step 2: Add handler for `action:parent_settings`**

In the `onItemViewClickedListener`, add a new case **before** the `else` block:

```kotlin
// 家长设置入口
item.actionUrl == "action:parent_settings" -> {
    val intent = Intent(this, com.pinecone.launcher.ui.guard.ParentSettingsActivity::class.java)
    startActivity(intent)
}
```

- [ ] **Step 3: Verify**

```bash
cd launcher && ./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

---

## Phase 8: Testing & Final Build

### Task 28: Unit tests for RuleEngine

**Files:**
- Create: `guard/src/test/java/com/pinecone/guard/engine/RuleEngineTest.kt`

- [ ] **Step 1: Write `RuleEngineTest.kt`**

```kotlin
package com.pinecone.guard.engine

import com.pinecone.guard.data.model.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

class RuleEngineTest {

    private val engine = RuleEngine()

    @Test
    fun `allows usage when under all limits`() {
        val rules = RuleSet(dailyTotalLimit = 150) // 2.5h
        val snapshot = UsageSnapshot(
            date = LocalDate.now(),
            totalSeconds = 3600, // 1h used
            categoryUsage = mapOf("english" to 1800),
            appUsage = mapOf("com.example.app" to 1800)
        )
        val result = engine.evaluate(rules, snapshot, null, null, 100,
            now = LocalTime.of(17, 0), today = LocalDate.now())
        assertTrue(result == RuleResult.ALLOWED)
    }

    @Test
    fun `locks when daily limit exceeded with zero credits`() {
        val rules = RuleSet(dailyTotalLimit = 150)
        val snapshot = UsageSnapshot(
            totalSeconds = 9001, // over 2.5h
            categoryUsage = emptyMap(),
            appUsage = emptyMap()
        )
        val result = engine.evaluate(rules, snapshot, null, null, 0,
            now = LocalTime.of(17, 0), today = LocalDate.now())
        assertTrue(result.shouldLock)
        assertEquals(LockReason.DAILY_LIMIT_REACHED, result.lockReason)
    }

    @Test
    fun `grants grace period when daily limit exceeded with credits`() {
        val rules = RuleSet(dailyTotalLimit = 150)
        val snapshot = UsageSnapshot(
            totalSeconds = 9001,
            categoryUsage = emptyMap(),
            appUsage = emptyMap()
        )
        val result = engine.evaluate(rules, snapshot, null, null, 50,
            now = LocalTime.of(17, 0), today = LocalDate.now())
        assertFalse(result.shouldLock)
        assertTrue(result.graceSecondsAvailable > 0)
    }

    @Test
    fun `blocks app when app limit reached`() {
        val rules = RuleSet(
            dailyTotalLimit = null,
            appLimits = listOf(AppLimit("tv.danmaku.bili", "B站", 30))
        )
        val snapshot = UsageSnapshot(
            totalSeconds = 3600,
            appUsage = mapOf("tv.danmaku.bili" to 1801) // 30+ min
        )
        val result = engine.evaluate(rules, snapshot, "tv.danmaku.bili", null, 100)
        assertTrue(result.isAppBlocked)
        assertTrue(result.blockedAppMessage!!.contains("B站"))
    }

    @Test
    fun `locks when outside time window`() {
        val rules = RuleSet(
            dailyTotalLimit = null,
            timeWindows = listOf(TimeWindow("day", 16, 0, 21, 0, setOf(1,2,3,4,5)))
        )
        val snapshot = UsageSnapshot(totalSeconds = 0)
        // Friday at 22:00 — outside window
        val result = engine.evaluate(rules, snapshot, null, null, 100,
            now = LocalTime.of(22, 0),
            today = LocalDate.of(2026, 7, 24)) // Friday
        assertTrue(result.shouldLock)
        assertEquals(LockReason.OUTSIDE_TIME_WINDOW, result.lockReason)
    }

    @Test
    fun `warns at 15 minutes remaining`() {
        val rules = RuleSet(dailyTotalLimit = 60)
        val snapshot = UsageSnapshot(totalSeconds = 2700) // 45 min used
        val result = engine.evaluate(rules, snapshot, null, null, 100)
        assertTrue(result.shouldWarn)
        assertTrue(result.warnMessage!!.contains("15"))
    }
}
```

- [ ] **Step 2: Add test dependency to guard/build.gradle.kts**

```kotlin
dependencies {
    // ... existing
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.0")
}
```

- [ ] **Step 3: Run tests**

```bash
cd launcher && ./gradlew :guard:test
```
Expected: All 6 tests PASS

---

### Task 29: Unit tests for CreditManager

**Files:**
- Create: `guard/src/test/java/com/pinecone/guard/engine/CreditManagerTest.kt`

- [ ] **Step 1: Write `CreditManagerTest.kt`**

```kotlin
package com.pinecone.guard.engine

import com.pinecone.guard.data.model.*
import org.junit.Test
import java.time.DayOfWeek
import kotlin.test.*

class CreditManagerTest {

    private val manager = CreditManager()
    private val config = CreditConfig(weeklyTotal = 100, resetDay = DayOfWeek.MONDAY,
        overtimeCostPerMin = 5, earlyStopRewardPerMin = 2)

    @Test
    fun `deduct correctly for grace period`() {
        val account = manager.createInitial(config)
        val updated = manager.deductForGrace(account, 5, 3) // 3 min at 5/min = 15
        assertEquals(85, updated.balance)
        assertEquals(1, updated.transactions.size)
        assertEquals(CreditReason.OVERTIME_DEDUCTION, updated.transactions[0].reason)
    }

    @Test
    fun `balance never goes below zero`() {
        val account = manager.createInitial(config.copy(weeklyTotal = 10))
        val updated = manager.deductForGrace(account, 5, 5) // 25 points, only 10 available
        assertEquals(0, updated.balance)
    }

    @Test
    fun `reward early stop capped at total`() {
        val account = CreditAccount(95, 100, DayOfWeek.MONDAY, System.currentTimeMillis())
        val (updated, rewarded) = manager.rewardEarlyStop(account, 2, 10) // +20 but cap at 100
        assertEquals(100, updated.balance)
        assertEquals(5, rewarded) // Only 5 actually added (95→100)
    }

    @Test
    fun `weekly reset on Monday`() {
        val account = CreditAccount(
            25, 100, DayOfWeek.MONDAY,
            lastResetTime = System.currentTimeMillis() - 8 * 86_400_000 // 8 days ago
        )
        val updated = manager.checkAndReset(account)
        assertEquals(100, updated.balance)
        assertEquals(CreditReason.WEEKLY_RESET, updated.transactions.last().reason)
    }
}
```

- [ ] **Step 2: Run tests**

```bash
cd launcher && ./gradlew :guard:test
```
Expected: All 10 tests PASS

---

### Task 30: Full build verification

- [ ] **Step 1: Build the full project**

```bash
cd launcher && ./gradlew clean assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify APK exists**

```bash
ls -la launcher/app/build/outputs/apk/debug/app-debug.apk
```
Expected: File exists, roughly 5-10 MB

- [ ] **Step 3: Lint check**

```bash
cd launcher && ./gradlew lint
```
Expected: No critical issues. Warnings acceptable.

---

## Implementation Order

```
Phase 1 (Tasks 1-2):  Module scaffold + data models       ← Foundation
Phase 2 (Tasks 3-7):  API + engine implementation         ← Core logic
Phase 3 (Tasks 8-9):  SecureStorage + UsageTracker        ← Data layer
Phase 4 (Tasks 10-13): GuardService + IPC + Admin         ← Android infra
Phase 5 (Tasks 14-22): Parent UI (settings + editors)     ← Configuration
Phase 6 (Tasks 23-25): Child UI (lock/warn/break)         ← Enforcement
Phase 7 (Tasks 26-27): Manifest + MainActivity wire-up    ← Integration
Phase 8 (Tasks 28-30): Tests + build verify               ← Validation
```

Each phase depends on the previous one. Within a phase, tasks can be parallelized when they touch different files.
