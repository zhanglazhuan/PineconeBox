package com.pinecone.guard.api

import com.pinecone.guard.data.model.GuardState
import com.pinecone.guard.data.model.LockReason

interface GuardStateListener {
    fun onWarningLevel(level: Int, message: String, remainingSeconds: Long)
    fun onGracePeriodStarted(creditRemaining: Int)
    fun onGraceTick(secondsLeft: Int, creditDraining: Int)
    fun onLockRequired(reason: LockReason)
    fun onBreakRequired(durationSeconds: Int)
    fun onBreakFinished()
    fun onStateChanged(newState: GuardState)
}
