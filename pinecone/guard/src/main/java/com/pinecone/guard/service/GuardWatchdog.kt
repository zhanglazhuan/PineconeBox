package com.pinecone.guard.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

class GuardWatchdog(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (running) {
                if (!isGuardProcessAlive()) restartGuardService()
                handler.postDelayed(this, 30_000L)
            }
        }
    }

    fun start() { running = true; handler.post(checkRunnable) }
    fun stop() { running = false; handler.removeCallbacks(checkRunnable) }

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
