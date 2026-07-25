package com.pinecone.guard.data.model

data class TimeSnapshot(
    val elapsedRealtime: Long,
    val wallClock: Long,
    val accumulatedSeconds: Long
)

enum class TimeStatus {
    VALID, TAMPERED_BACKWARD, TAMPERED_REBOOT
}
