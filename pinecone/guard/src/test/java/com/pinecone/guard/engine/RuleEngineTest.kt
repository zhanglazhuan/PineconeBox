package com.pinecone.guard.engine

import com.pinecone.guard.data.model.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

class RuleEngineTest {

    private val engine = RuleEngine()
    private val inWindowTime = LocalTime.of(17, 0)  // 5 PM, within default window
    private val wednesday = LocalDate.of(2026, 7, 22) // Wednesday
    private val friday = LocalDate.of(2026, 7, 24)    // Friday

    @Test
    fun `allows usage when under all limits`() {
        val rules = RuleSet(dailyTotalLimit = 150)
        val snapshot = UsageSnapshot(
            date = wednesday, totalSeconds = 3600,
            categoryUsage = mapOf("english" to 1800),
            appUsage = mapOf("com.example.app" to 1800)
        )
        val result = engine.evaluate(rules, snapshot, null, null, 100,
            now = inWindowTime, today = wednesday)
        assertEquals(RuleResult.ALLOWED, result)
    }

    @Test
    fun `locks when daily limit exceeded with zero credits`() {
        val rules = RuleSet(dailyTotalLimit = 150)
        val snapshot = UsageSnapshot(totalSeconds = 9001,
            date = wednesday)
        val result = engine.evaluate(rules, snapshot, null, null, 0,
            now = inWindowTime, today = wednesday)
        assertTrue(result.shouldLock)
        assertEquals(LockReason.DAILY_LIMIT_REACHED, result.lockReason)
    }

    @Test
    fun `grants grace period when daily limit exceeded with credits`() {
        val rules = RuleSet(dailyTotalLimit = 150)
        val snapshot = UsageSnapshot(totalSeconds = 9001,
            date = wednesday)
        val result = engine.evaluate(rules, snapshot, null, null, 50,
            now = inWindowTime, today = wednesday)
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
            date = wednesday, totalSeconds = 3600,
            appUsage = mapOf("tv.danmaku.bili" to 1801)
        )
        val result = engine.evaluate(rules, snapshot, "tv.danmaku.bili", null, 100,
            now = inWindowTime, today = wednesday)
        assertTrue(result.isAppBlocked)
        assertTrue(result.blockedAppMessage!!.contains("B站"))
    }

    @Test
    fun `locks when outside time window`() {
        val rules = RuleSet(
            dailyTotalLimit = null,
            timeWindows = listOf(TimeWindow("day", 16, 0, 21, 0, setOf(1, 2, 3, 4, 5)))
        )
        val snapshot = UsageSnapshot(date = friday, totalSeconds = 0)
        val result = engine.evaluate(rules, snapshot, null, null, 100,
            now = LocalTime.of(22, 0), today = friday)
        assertTrue(result.shouldLock)
        assertEquals(LockReason.OUTSIDE_TIME_WINDOW, result.lockReason)
    }

    @Test
    fun `warns at 15 minutes remaining`() {
        val rules = RuleSet(dailyTotalLimit = 60)
        val snapshot = UsageSnapshot(
            date = wednesday, totalSeconds = 2700
        ) // 45 min used, 15 remaining
        val result = engine.evaluate(rules, snapshot, null, null, 100,
            now = inWindowTime, today = wednesday)
        assertTrue(result.shouldWarn)
        assertTrue(result.warnMessage!!.contains("15"))
    }

    @Test
    fun `warns at 5 minutes remaining`() {
        val rules = RuleSet(dailyTotalLimit = 60)
        val snapshot = UsageSnapshot(
            date = wednesday, totalSeconds = 3300
        ) // 55 min used, 5 remaining
        val result = engine.evaluate(rules, snapshot, null, null, 100,
            now = inWindowTime, today = wednesday)
        assertTrue(result.shouldWarn)
        assertTrue(result.warnMessage!!.contains("5"))
    }

    @Test
    fun `allows within time window on weekday`() {
        val rules = RuleSet(dailyTotalLimit = null)
        val snapshot = UsageSnapshot(date = wednesday, totalSeconds = 0)
        val result = engine.evaluate(rules, snapshot, null, null, 100,
            now = inWindowTime, today = wednesday)
        assertEquals(RuleResult.ALLOWED, result)
    }

    @Test
    fun `paused rules allow everything`() {
        val rules = RuleSet(dailyTotalLimit = 60, pausedForToday = "测试暂停")
        val snapshot = UsageSnapshot(
            date = wednesday, totalSeconds = 99999
        )
        val result = engine.evaluate(rules, snapshot, null, null, 0,
            now = inWindowTime, today = wednesday)
        assertEquals(RuleResult.ALLOWED, result)
    }

    @Test
    fun `blocks category when category limit reached`() {
        val rules = RuleSet(
            dailyTotalLimit = null,
            categoryLimits = listOf(CategoryLimit("english", "英语学习", 60))
        )
        val snapshot = UsageSnapshot(
            date = wednesday, totalSeconds = 4000,
            categoryUsage = mapOf("english" to 3600) // exactly 60 min
        )
        val result = engine.evaluate(
            rules, snapshot, null, "english", 100,
            now = inWindowTime, today = wednesday
        )
        assertTrue(result.categoryBlocked.contains("english"))
    }
}
