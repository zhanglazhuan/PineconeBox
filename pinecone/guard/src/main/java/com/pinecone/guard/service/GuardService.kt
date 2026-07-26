package com.pinecone.guard.service

import android.app.*
import android.content.Intent
import android.os.*
import android.content.pm.ServiceInfo
import com.pinecone.guard.api.GuardStateListener
import com.pinecone.guard.data.SecureStorage
import com.pinecone.guard.data.UsageTracker
import com.pinecone.guard.data.model.LockReason
import com.pinecone.guard.engine.GuardEngineImpl

const val MSG_GET_STATE = 1
const val MSG_UPDATE_RULES = 2
const val MSG_GET_RULES = 3
const val MSG_REQUEST_GRACE = 4
const val MSG_REQUEST_EARLY_STOP = 5
const val MSG_VERIFY_PIN = 6
const val MSG_PAUSE_TODAY = 7
const val MSG_RESUME_TODAY = 8
const val MSG_MANUAL_RESET_CREDITS = 9
const val MSG_REGISTER_CLIENT = 10
const val MSG_SETUP_PIN = 11

class GuardService : Service() {

    private lateinit var engine: GuardEngineImpl
    private val binder = Messenger(IncomingHandler(this))
    private var clientMessenger: Messenger? = null
    private var tickHandler: Handler? = null
    private var tickRunnable: Runnable? = null
    private var otaHandler: Handler? = null
    private var otaRunnable: Runnable? = null
    private val updateChecker by lazy { com.pinecone.guard.engine.UpdateChecker(this) }

    override fun onCreate() {
        super.onCreate()
        val storage = SecureStorage(this)
        val tracker = UsageTracker(this)
        engine = GuardEngineImpl(this, storage, tracker)
        engine.setListener(serviceListener)

        startForeground(NOTIFICATION_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        tickHandler = Handler(Looper.getMainLooper())
        tickRunnable = object : Runnable {
            private var tickCount = 0
            override fun run() {
                tickCount++
                if (tickCount % 4 == 1) android.util.Log.d("GuardService", "tick #$tickCount")
                engine.tick()
                tickHandler?.postDelayed(this, 15_000L)
            }
        }
        tickHandler?.postDelayed(tickRunnable!!, 15_000L)

        engine.start()
        scheduleOtaCheck()
    }

    override fun onBind(intent: Intent?): IBinder = binder.binder
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        engine.stop()
        tickRunnable?.let { tickHandler?.removeCallbacks(it) }
        otaRunnable?.let { otaHandler?.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun scheduleOtaCheck() {
        otaHandler = Handler(Looper.getMainLooper())
        otaRunnable = object : Runnable {
            override fun run() {
                updateChecker.checkForUpdate { result ->
                    if (result is com.pinecone.guard.api.UpdateResult.Available) {
                        sendToClient(createMsg(MSG_GET_STATE, Bundle().apply {
                            putBoolean("updateAvailable", true)
                            putString("updateVersion", result.versionName)
                            putInt("updateSize", result.size.toInt())
                        }))
                    }
                }
                otaHandler?.postDelayed(this, 24 * 3600 * 1000L) // every 24h
            }
        }
        // First check after 60 seconds (give system time to connect network)
        otaHandler?.postDelayed(otaRunnable!!, 60_000L)
    }

    private fun buildNotification(): Notification {
        val channelId = "pinecone_guard"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "防沉迷守护", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "松果智学防沉迷系统运行中" }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        return Notification.Builder(this, channelId)
            .setContentTitle("松果智学·防沉迷守护中")
            .setContentText("正在守护孩子的用眼健康")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
    }

    private val serviceListener = object : GuardStateListener {
        override fun onWarningLevel(level: Int, message: String, remainingSeconds: Long) {
            sendToClient(createMsg(MSG_GET_STATE, Bundle().apply {
                putString("warning", message); putInt("level", level)
                putLong("remaining", remainingSeconds)
            }))
        }
        override fun onGracePeriodStarted(creditRemaining: Int) {
            sendToClient(createMsg(MSG_REQUEST_GRACE, Bundle().apply {
                putInt("credit", creditRemaining)
            }))
        }
        override fun onGraceTick(secondsLeft: Int, creditDraining: Int) {
            sendToClient(createMsg(MSG_REQUEST_GRACE, Bundle().apply {
                putInt("graceSeconds", secondsLeft); putInt("drainRate", creditDraining)
            }))
        }
        override fun onLockRequired(reason: LockReason, breakUsageMinutes: Int, breakDurationMinutes: Int) {
            val bundle = Bundle().apply {
                putString("lockReason", reason.name)
                if (reason == LockReason.BREAK_REQUIRED) {
                    val rules = engine.getRules()
                    val br = rules.breakRule
                    putInt("breakUsageMinutes", br?.usageMinutes ?: 40)
                    putInt("breakDurationMinutes", br?.breakMinutes ?: 10)
                }
            }
            sendToClient(createMsg(MSG_GET_STATE, bundle))
        }
        override fun onBreakRequired(durationSeconds: Int) {
            sendToClient(createMsg(MSG_GET_STATE, Bundle().apply {
                putInt("breakSeconds", durationSeconds); putBoolean("isBreak", true)
            }))
        }
        override fun onBreakFinished() {
            sendToClient(createMsg(MSG_GET_STATE, Bundle().apply {
                putBoolean("breakFinished", true)
            }))
        }
        override fun onStateChanged(newState: com.pinecone.guard.data.model.GuardState) {}
    }

    private fun sendToClient(msg: Message) {
        try { clientMessenger?.send(msg) } catch (_: Exception) {}
    }

    private fun createMsg(what: Int, data: Bundle): Message =
        Message.obtain(null, what).apply { setData(data) }

    class IncomingHandler(private val service: GuardService) : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val engine = service.engine
            val reply = Message.obtain(null, msg.what)
            val data = Bundle()

            when (msg.what) {
                MSG_REGISTER_CLIENT -> {
                    service.clientMessenger = msg.replyTo
                    data.putString("status", "registered")
                }
                MSG_GET_STATE -> {
                    val state = engine.getCurrentState()
                    data.putLong("totalSecs", state.totalSecondsToday)
                    data.putLong("remainingSecs", state.remainingSeconds ?: -1)
                    data.putInt("creditBalance", state.creditBalance)
                    data.putBoolean("isActive", state.isActive)
                }
                MSG_REQUEST_GRACE -> {
                    val granted = engine.requestGraceExtension()
                    data.putBoolean("granted", granted)
                }
                MSG_REQUEST_EARLY_STOP -> {
                    val rewarded = engine.requestEarlyStop()
                    data.putInt("rewarded", rewarded)
                }
                MSG_VERIFY_PIN -> {
                    val pin = msg.data.getString("pin") ?: return
                    val valid = engine.verifyPin(pin)
                    data.putBoolean("valid", valid)
                }
                MSG_SETUP_PIN -> {
                    val pin = msg.data.getString("pin") ?: return
                    engine.setupPin(pin)
                    data.putBoolean("success", true)
                }
                MSG_UPDATE_RULES -> {
                    val json = msg.data.getString("rules") ?: return
                    val rules = parseRulesJson(json) ?: return
                    android.util.Log.d("GuardService", "IPC UPDATE_RULES received | dailyTotalLimit=${rules.dailyTotalLimit}")
                    engine.updateRules(rules)
                    data.putBoolean("updated", true)
                }
                MSG_GET_RULES -> {
                    val rules = engine.getRules()
                    data.putString("rules_json", engine.getRules().let {
                        android.util.Base64.encodeToString(
                            it.toString().toByteArray(), android.util.Base64.NO_WRAP
                        )
                    })
                }
                MSG_PAUSE_TODAY -> {
                    val reason = msg.data.getString("reason") ?: ""
                    engine.pauseForToday(reason)
                }
                MSG_RESUME_TODAY -> engine.resumeForToday()
                MSG_MANUAL_RESET_CREDITS -> engine.manualResetCredits()
            }

            reply.setData(data)
            try { msg.replyTo?.send(reply) } catch (_: Exception) {}
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 9001

        private fun parseRulesJson(json: String): com.pinecone.guard.data.model.RuleSet? = try {
            val o = org.json.JSONObject(json)
            com.pinecone.guard.data.model.RuleSet(
                version = o.optInt("version", 1),
                dailyTotalLimit = o.optInt("dailyTotalLimit", -1).takeIf { it >= 0 },
                categoryLimits = parseCategoryLimits(o.optJSONArray("categoryLimits")),
                appLimits = parseAppLimits(o.optJSONArray("appLimits")),
                timeWindows = parseTimeWindows(o.optJSONArray("timeWindows")),
                breakRule = o.optJSONObject("breakRule")?.let {
                    com.pinecone.guard.data.model.BreakRule(it.getInt("usage"), it.getInt("break"))
                },
                creditConfig = o.optJSONObject("creditConfig")?.let {
                    com.pinecone.guard.data.model.CreditConfig(
                        weeklyTotal = it.getInt("total"),
                        resetDay = java.time.DayOfWeek.valueOf(it.getString("resetDay")),
                        overtimeCostPerMin = it.getInt("cost"),
                        earlyStopRewardPerMin = it.getInt("reward")
                    )
                } ?: com.pinecone.guard.data.model.CreditConfig.DEFAULT,
                pausedForToday = o.optString("pausedForToday", "").takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) { null }

        private fun parseCategoryLimits(arr: org.json.JSONArray?): List<com.pinecone.guard.data.model.CategoryLimit> =
            (0 until (arr?.length() ?: 0)).map {
                val o = arr!!.getJSONObject(it)
                com.pinecone.guard.data.model.CategoryLimit(
                    o.getString("id"), o.getString("label"),
                    o.optInt("min", -1).takeIf { m -> m >= 0 })
            }

        private fun parseAppLimits(arr: org.json.JSONArray?): List<com.pinecone.guard.data.model.AppLimit> =
            (0 until (arr?.length() ?: 0)).map {
                val o = arr!!.getJSONObject(it)
                com.pinecone.guard.data.model.AppLimit(o.getString("pkg"), o.getString("label"), o.getInt("min"))
            }

        private fun parseTimeWindows(arr: org.json.JSONArray?): List<com.pinecone.guard.data.model.TimeWindow> =
            (0 until (arr?.length() ?: 0)).map {
                val o = arr!!.getJSONObject(it)
                val daysArr = o.getJSONArray("days")
                val days = (0 until daysArr.length()).mapTo(mutableSetOf()) { daysArr.getInt(it) }
                com.pinecone.guard.data.model.TimeWindow(
                    o.getString("name"), o.getInt("sh"), o.getInt("sm"),
                    o.getInt("eh"), o.getInt("em"), days)
            }
    }
}
