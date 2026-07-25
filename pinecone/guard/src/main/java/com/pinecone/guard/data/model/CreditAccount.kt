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
