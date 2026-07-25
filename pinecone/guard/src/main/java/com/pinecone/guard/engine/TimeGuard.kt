package com.pinecone.guard.engine

import android.os.SystemClock
import com.pinecone.guard.data.model.TimeSnapshot
import com.pinecone.guard.data.model.TimeStatus

class TimeGuard {
    companion object {
        private const val ALLOWED_DRIFT_MS = 120_000L

        fun takeSnapshot(accumulatedSeconds: Long): TimeSnapshot = TimeSnapshot(
            elapsedRealtime = SystemClock.elapsedRealtime(),
            wallClock = System.currentTimeMillis(),
            accumulatedSeconds = accumulatedSeconds
        )

        fun validate(last: TimeSnapshot?, current: TimeSnapshot): TimeStatus {
            if (last == null) return TimeStatus.VALID

            val elapsedDelta = current.elapsedRealtime - last.elapsedRealtime
            val wallDelta = current.wallClock - last.wallClock

            if (elapsedDelta < 0) return TimeStatus.TAMPERED_REBOOT
            if (wallDelta < -ALLOWED_DRIFT_MS) return TimeStatus.TAMPERED_BACKWARD
            if (wallDelta > elapsedDelta + ALLOWED_DRIFT_MS * 2) return TimeStatus.TAMPERED_BACKWARD

            return TimeStatus.VALID
        }

        fun realUsageDelta(last: TimeSnapshot, now: TimeSnapshot): Long {
            if (last.elapsedRealtime > now.elapsedRealtime) return 0L
            return now.elapsedRealtime - last.elapsedRealtime
        }
    }
}
