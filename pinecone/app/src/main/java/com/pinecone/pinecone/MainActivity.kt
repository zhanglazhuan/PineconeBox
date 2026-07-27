package com.pinecone.pinecone

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pinecone.guard.api.GuardStateListener
import com.pinecone.guard.data.model.LockReason
import com.pinecone.guard.service.GuardClientHolder
import com.pinecone.pinecone.data.FaviconCache
import com.pinecone.pinecone.log.*
import com.pinecone.pinecone.ui.guard.GuardCountdownActivity
import com.pinecone.pinecone.ui.guard.GuardWarningActivity
import com.pinecone.pinecone.ui.guard.LockScreenActivity
import com.pinecone.pinecone.ui.screen.MainScreen
import com.pinecone.pinecone.ui.theme.PineconeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        GuardClientHolder.initialize(this)

        // Init favicon cache on SD card
        FaviconCache.init(this)

        // Global guard listener — launches overlay activities for warnings/lock
        GuardClientHolder.client?.setListener(object : GuardStateListener {
            private var warned5Min = false

            override fun onLockRequired(reason: LockReason, breakUsageMinutes: Int, breakDurationMinutes: Int) {
                if (GuardClientHolder.isLockFlowActive) return
                warned5Min = false
                // Bump limit to trigger snapshot reset in engine, then restore
                val orig = GuardClientHolder.cachedRules.dailyTotalLimit ?: 6
                GuardClientHolder.updateDailyLimit(orig + 1)
                GuardClientHolder.updateDailyLimit(orig)
                GuardClientHolder.isLockFlowActive = true
                startActivity(Intent(this@MainActivity, GuardCountdownActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("lock_reason", reason.name)
                    putExtra("break_usage_minutes", breakUsageMinutes)
                    putExtra("break_duration_minutes", breakDurationMinutes)
                })
            }
            override fun onBreakFinished() {}
            override fun onWarningLevel(level: Int, message: String, remainingSeconds: Long) {
                // 5-min: extension dialog (Activity, global)
                if (remainingSeconds in 61..300 && !warned5Min) {
                    warned5Min = true
                    val rules = GuardClientHolder.cachedRules
                    startActivity(Intent(this@MainActivity, GuardWarningActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("remaining", remainingSeconds)
                        putExtra("credits", GuardClientHolder.cachedCredits.balance)
                        putExtra("cost_per_min", rules.creditConfig.overtimeCostPerMin)
                    })
                }
                // 1-min: top-right overlay bar (Activity, global)
                if (remainingSeconds in 1..60) {
                    startActivity(Intent(this@MainActivity, GuardWarningActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("remaining", remainingSeconds)
                        putExtra("credits", 0)  // no credits = compact top-right bar
                        putExtra("cost_per_min", 0)
                    })
                }
            }
            override fun onGracePeriodStarted(creditRemaining: Int) {}
            override fun onGraceTick(secondsLeft: Int, creditDraining: Int) {}
            override fun onBreakRequired(durationSeconds: Int) {}
            override fun onStateChanged(newState: com.pinecone.guard.data.model.GuardState) {}
        })

        // Initialize logging system
        val accountUuid = try {
            com.pinecone.pinecone.data.AccountStorage.getInstance(this).accountUuid
        } catch (_: Exception) { "" }
        PineconeLogger.init(this, accountUuid)
        PineconeLogger.log(SystemEvent(
            System.currentTimeMillis(),
            PineconeLogger.getSession()?.sessionId ?: "",
            "boot",
            mapOf("app_version" to PineconeLogger.getAppVersion())
        ))

        // Install global crash handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                PineconeLogger.log(CrashEvent(
                    System.currentTimeMillis(),
                    PineconeLogger.getSession()?.sessionId ?: "",
                    throwable.javaClass.name,
                    throwable.stackTraceToString(),
                    thread.name,
                    PineconeLogger.getAppVersion(),
                    PineconeLogger.getAndroidVersion()
                ))
                Thread.sleep(200)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        enableEdgeToEdge()
        setContent {
            PineconeTheme(darkTheme = true, dynamicColor = false) {
                MainScreen()
            }
        }
    }

    /**
     * Convert mouse scroll wheel events to D-Pad UP/DOWN key events.
     *
     * Jetpack Compose's LazyColumn / verticalScroll handle touch-drag and
     * D-Pad key events natively, but do NOT handle generic motion scroll
     * (mouse wheel ACTION_SCROLL). This override bridges that gap by
     * translating each scroll notch into one D-Pad key press, which
     * is then handled by all Compose scroll containers automatically.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_SCROLL) {
            val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (vScroll != 0f) {
                val keyCode = if (vScroll > 0) KeyEvent.KEYCODE_DPAD_UP
                              else KeyEvent.KEYCODE_DPAD_DOWN
                // One key event per notch (1 notch ≈ 1 click of the wheel)
                val notches = kotlin.math.abs(vScroll).toInt().coerceIn(1, 10)
                repeat(notches) {
                    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                }
                return true
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            PineconeLogger.log(SystemEvent(
                System.currentTimeMillis(),
                PineconeLogger.getSession()?.sessionId ?: "",
                "shutdown",
                emptyMap()
            ))
            PineconeLogger.endSession()
        } catch (_: Exception) {}
    }
}
