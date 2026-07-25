package com.pinecone.guard.engine

import com.pinecone.guard.data.model.*
import org.junit.Test
import java.time.DayOfWeek
import kotlin.test.*

class CreditManagerTest {

    private val manager = CreditManager()
    private val config = CreditConfig(
        weeklyTotal = 100, resetDay = DayOfWeek.MONDAY,
        overtimeCostPerMin = 5, earlyStopRewardPerMin = 2
    )

    @Test
    fun `create initial account`() {
        val account = manager.createInitial(config)
        assertEquals(100, account.balance)
        assertEquals(100, account.weeklyTotal)
        assertEquals(DayOfWeek.MONDAY, account.resetDay)
    }

    @Test
    fun `deduct correctly for grace period`() {
        val account = manager.createInitial(config)
        val updated = manager.deductForGrace(account, 5, 3)
        assertEquals(85, updated.balance)
        assertEquals(1, updated.transactions.size)
        assertEquals(CreditReason.OVERTIME_DEDUCTION, updated.transactions[0].reason)
        assertEquals(-15, updated.transactions[0].amount)
    }

    @Test
    fun `balance never goes below zero`() {
        val account = manager.createInitial(config.copy(weeklyTotal = 10))
        val updated = manager.deductForGrace(account, 5, 5)
        assertEquals(0, updated.balance)
    }

    @Test
    fun `reward early stop`() {
        val account = CreditAccount(50, 100, DayOfWeek.MONDAY, System.currentTimeMillis())
        val (updated, rewarded) = manager.rewardEarlyStop(account, 2, 10)
        assertEquals(70, updated.balance)
        assertEquals(20, rewarded)
        assertEquals(CreditReason.EARLY_STOP_REWARD, updated.transactions[0].reason)
    }

    @Test
    fun `reward early stop capped at weekly total`() {
        val account = CreditAccount(95, 100, DayOfWeek.MONDAY, System.currentTimeMillis())
        val (updated, rewarded) = manager.rewardEarlyStop(account, 2, 10)
        assertEquals(100, updated.balance)
        assertEquals(5, rewarded)
    }

    @Test
    fun `no reward when balance already at max`() {
        val account = CreditAccount(100, 100, DayOfWeek.MONDAY, System.currentTimeMillis())
        val (updated, rewarded) = manager.rewardEarlyStop(account, 2, 10)
        assertEquals(100, updated.balance)
        assertEquals(0, rewarded)
    }

    @Test
    fun `weekly reset triggered when due`() {
        val eightDaysAgo = System.currentTimeMillis() - 8 * 86_400_000L
        // Need at least one transaction for reset to trigger
        val account = CreditAccount(
            balance = 25, weeklyTotal = 100, resetDay = DayOfWeek.MONDAY,
            lastResetTime = eightDaysAgo,
            transactions = listOf(
                CreditTransaction(eightDaysAgo, 100, CreditReason.WEEKLY_RESET, 100)
            )
        )
        val updated = manager.checkAndReset(account)
        assertEquals(100, updated.balance)
        assertEquals(CreditReason.WEEKLY_RESET, updated.transactions.last().reason)
    }

    @Test
    fun `weekly reset not triggered when not due`() {
        val yesterday = System.currentTimeMillis() - 1 * 86_400_000L
        val account = CreditAccount(
            balance = 25, weeklyTotal = 100, resetDay = DayOfWeek.MONDAY,
            lastResetTime = yesterday,
            transactions = listOf(
                CreditTransaction(yesterday, 100, CreditReason.WEEKLY_RESET, 100)
            )
        )
        val updated = manager.checkAndReset(account)
        assertEquals(25, updated.balance)
    }

    @Test
    fun `manual reset restores full balance`() {
        val account = CreditAccount(10, 100, DayOfWeek.MONDAY, System.currentTimeMillis())
        val updated = manager.manualReset(account)
        assertEquals(100, updated.balance)
        assertEquals(CreditReason.PARENT_MANUAL_RESET, updated.transactions[0].reason)
    }

    @Test
    fun `canGrantGrace returns true when balance positive`() {
        val account = CreditAccount(50, 100, DayOfWeek.MONDAY, System.currentTimeMillis())
        assertTrue(manager.canGrantGrace(account))
    }

    @Test
    fun `canGrantGrace returns false when balance zero`() {
        val account = CreditAccount(0, 100, DayOfWeek.MONDAY, System.currentTimeMillis())
        assertFalse(manager.canGrantGrace(account))
    }

    @Test
    fun `multiple deductions accumulate transactions`() {
        val account = manager.createInitial(config)
        val after1 = manager.deductForGrace(account, 5, 2)
        val after2 = manager.deductForGrace(after1, 5, 3)
        assertEquals(75, after2.balance)
        assertEquals(2, after2.transactions.size)
    }
}
