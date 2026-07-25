package com.pinecone.guard.engine

import com.pinecone.guard.data.model.*

class CreditManager {

    fun createInitial(config: CreditConfig): CreditAccount = CreditAccount(
        balance = config.weeklyTotal,
        weeklyTotal = config.weeklyTotal,
        resetDay = config.resetDay,
        lastResetTime = System.currentTimeMillis()
    )

    fun checkAndReset(account: CreditAccount, now: Long = System.currentTimeMillis()): CreditAccount {
        val today = java.time.LocalDate.now()
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

    fun deductForGrace(account: CreditAccount, costPerMin: Int, minutesUsed: Int): CreditAccount {
        val cost = costPerMin * minutesUsed
        val newBalance = (account.balance - cost).coerceAtLeast(0)
        val actualDeduction = account.balance - newBalance
        return account.copy(
            balance = newBalance,
            transactions = account.transactions + CreditTransaction(
                System.currentTimeMillis(), -actualDeduction,
                CreditReason.OVERTIME_DEDUCTION, newBalance
            )
        )
    }

    fun rewardEarlyStop(
        account: CreditAccount, rewardPerMin: Int, unusedMinutes: Long
    ): Pair<CreditAccount, Int> {
        val reward = (rewardPerMin * unusedMinutes).toInt()
        val cappedReward = minOf(reward, account.weeklyTotal - account.balance)
        if (cappedReward <= 0) return account to 0
        val newBalance = account.balance + cappedReward
        return account.copy(
            balance = newBalance,
            transactions = account.transactions + CreditTransaction(
                System.currentTimeMillis(), cappedReward,
                CreditReason.EARLY_STOP_REWARD, newBalance
            )
        ) to cappedReward
    }

    fun manualReset(account: CreditAccount): CreditAccount = account.copy(
        balance = account.weeklyTotal,
        lastResetTime = System.currentTimeMillis(),
        transactions = account.transactions + CreditTransaction(
            System.currentTimeMillis(), account.weeklyTotal,
            CreditReason.PARENT_MANUAL_RESET, account.weeklyTotal
        )
    )

    fun canGrantGrace(account: CreditAccount): Boolean = account.balance > 0

    private fun isResetDue(account: CreditAccount, today: java.time.LocalDate): Boolean {
        if (account.transactions.isEmpty()) return false
        val lastResetDay = java.time.LocalDate.ofEpochDay(account.lastResetTime / 86_400_000)
        val daysSinceReset = today.toEpochDay() - lastResetDay.toEpochDay()
        val todayDayOfWeek = today.dayOfWeek
        return daysSinceReset >= 7 ||
               (daysSinceReset > 0 && todayDayOfWeek == account.resetDay)
    }
}
