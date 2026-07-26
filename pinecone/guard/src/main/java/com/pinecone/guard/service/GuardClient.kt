package com.pinecone.guard.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import com.pinecone.guard.api.GuardStateListener

class GuardClient(private val context: Context) {

    private var serviceMessenger: Messenger? = null
    private var bound = false
    private val clientMessenger = Messenger(IncomingReplyHandler())
    private var listener: GuardStateListener? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serviceMessenger = Messenger(service)
            bound = true
            val msg = Message.obtain(null, MSG_REGISTER_CLIENT)
            msg.replyTo = clientMessenger
            try { serviceMessenger?.send(msg) } catch (_: Exception) {}
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceMessenger = null; bound = false
        }
    }

    fun bind() {
        val intent = Intent(context, GuardService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        context.startForegroundService(intent)
    }

    fun unbind() {
        try { context.unbindService(connection) } catch (_: Exception) {}
        bound = false
    }

    fun setListener(l: GuardStateListener) { listener = l }

    fun verifyPin(pin: String): Boolean {
        val data = Bundle().apply { putString("pin", pin) }
        sendAsync(MSG_VERIFY_PIN, data)
        return true  // Result comes async; UI should listen for callback
    }

    fun setupPin(pin: String) {
        sendAsync(MSG_SETUP_PIN, Bundle().apply { putString("pin", pin) })
    }

    fun requestGrace() = sendAsync(MSG_REQUEST_GRACE)
    fun requestEarlyStop() = sendAsync(MSG_REQUEST_EARLY_STOP)
    fun getState() = sendAsync(MSG_GET_STATE)

    fun pauseToday(reason: String) {
        sendAsync(MSG_PAUSE_TODAY, Bundle().apply { putString("reason", reason) })
    }

    fun resumeToday() = sendAsync(MSG_RESUME_TODAY)
    fun manualResetCredits() = sendAsync(MSG_MANUAL_RESET_CREDITS)

    fun sendRules(rules: com.pinecone.guard.data.model.RuleSet) {
        val json = rulesToJson(rules)
        android.util.Log.d("GuardClient", "sendRules | dailyTotalLimit=${rules.dailyTotalLimit} | bound=$bound | messenger=${serviceMessenger != null}")
        sendAsync(MSG_UPDATE_RULES, Bundle().apply { putString("rules", json) })
    }

    private fun sendAsync(what: Int, data: Bundle = Bundle()) {
        val msg = Message.obtain(null, what).apply { this.data = data; replyTo = clientMessenger }
        try { serviceMessenger?.send(msg) } catch (_: Exception) {}
    }

    companion object {
        fun rulesToJson(r: com.pinecone.guard.data.model.RuleSet): String =
            org.json.JSONObject().apply {
                put("version", r.version)
                put("dailyTotalLimit", r.dailyTotalLimit ?: -1)
                put("pausedForToday", r.pausedForToday ?: "")
                put("categoryLimits", org.json.JSONArray().apply {
                    r.categoryLimits.forEach { put(org.json.JSONObject().apply {
                        put("id", it.categoryId); put("label", it.label)
                        put("min", it.dailyMinutes ?: -1)
                    })}
                })
                put("appLimits", org.json.JSONArray().apply {
                    r.appLimits.forEach { put(org.json.JSONObject().apply {
                        put("pkg", it.packageName); put("label", it.appLabel)
                        put("min", it.dailyMinutes)
                    })}
                })
                put("timeWindows", org.json.JSONArray().apply {
                    r.timeWindows.forEach { put(org.json.JSONObject().apply {
                        put("name", it.name)
                        put("sh", it.startHour); put("sm", it.startMinute)
                        put("eh", it.endHour); put("em", it.endMinute)
                        put("days", org.json.JSONArray(it.daysOfWeek.toList()))
                    })}
                })
                r.breakRule?.let { put("breakRule", org.json.JSONObject().apply {
                    put("usage", it.usageMinutes); put("break", it.breakMinutes)
                })}
                put("creditConfig", org.json.JSONObject().apply {
                    put("total", r.creditConfig.weeklyTotal)
                    put("resetDay", r.creditConfig.resetDay.name)
                    put("cost", r.creditConfig.overtimeCostPerMin)
                    put("reward", r.creditConfig.earlyStopRewardPerMin)
                })
            }.toString()
    }

    inner class IncomingReplyHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val data = msg.data
            when (msg.what) {
                MSG_GET_STATE -> {
                    data.getString("lockReason")?.let {
                        val reason = com.pinecone.guard.data.model.LockReason.valueOf(it)
                        val bu = data.getInt("breakUsageMinutes", 0)
                        val bd = data.getInt("breakDurationMinutes", 0)
                        listener?.onLockRequired(reason, bu, bd)
                    }
                    data.getString("warning")?.let {
                        listener?.onWarningLevel(data.getInt("level", 1), it,
                            data.getLong("remaining", 0))
                    }
                    if (data.getBoolean("isBreak", false))
                        listener?.onBreakRequired(data.getInt("breakSeconds", 600))
                    if (data.getBoolean("breakFinished", false))
                        listener?.onBreakFinished()
                }
                MSG_REQUEST_GRACE -> {
                    val credit = data.getInt("credit", 0)
                    if (credit > 0) listener?.onGracePeriodStarted(credit)
                    else {
                        val secs = data.getInt("graceSeconds", 0)
                        val drain = data.getInt("drainRate", 5)
                        listener?.onGraceTick(secs, drain)
                    }
                }
            }
        }
    }
}
