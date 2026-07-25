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
