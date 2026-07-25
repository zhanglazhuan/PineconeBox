package com.pinecone.guard.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pinecone.guard.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SecureStorage(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, "pinecone_guard_main", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val backupFile = File(context.filesDir, ".guard_backup")
    private val checksumFile = File(context.filesDir, ".guard_checksum")
    private val historyFile = File(context.filesDir, "usage_history.json")

    // === Rules ===
    fun saveRules(rules: RuleSet) {
        val json = rulesToJson(rules)
        prefs.edit().putString(KEY_RULES, json).apply()
        writeBackup("rules:$json")
        writeChecksum("rules", computeHmac(json))
    }

    fun loadRules(): RuleSet? {
        val json = prefs.getString(KEY_RULES, null)
        if (json != null && verifyChecksumForKey("rules", json)) return parseRules(json)
        val backup = readBackup()
        val rulesPart = backup?.lines()?.find { it.startsWith("rules:") }?.removePrefix("rules:")
        return rulesPart?.let { parseRules(it) }
    }

    // === Credits ===
    fun saveCredits(account: CreditAccount) {
        val json = creditsToJson(account)
        prefs.edit().putString(KEY_CREDITS, json).apply()
        writeBackup("credits:$json")
        writeChecksum("credits", computeHmac(json))
    }

    fun loadCredits(): CreditAccount? {
        val json = prefs.getString(KEY_CREDITS, null)
        if (json != null && verifyChecksumForKey("credits", json)) return parseCredits(json)
        val backup = readBackup()
        val creditsPart = backup?.lines()?.find { it.startsWith("credits:") }?.removePrefix("credits:")
        return creditsPart?.let { parseCredits(it) }
    }

    // === Snapshot ===
    fun saveSnapshot(snap: UsageSnapshot) {
        val json = snapshotToJson(snap)
        prefs.edit().putString(KEY_SNAPSHOT, json).apply()
        writeBackup("snapshot:$json")
    }

    fun loadSnapshot(): UsageSnapshot? {
        val json = prefs.getString(KEY_SNAPSHOT, null) ?: run {
            val backup = readBackup()
            backup?.lines()?.find { it.startsWith("snapshot:") }?.removePrefix("snapshot:")
        }
        return json?.let { parseSnapshot(it) }
    }

    // === History ===
    fun saveHistory(list: List<DailyUsage>) {
        val arr = JSONArray()
        list.forEach { arr.put(dailyUsageToJson(it)) }
        historyFile.writeText(arr.toString())
    }

    fun loadHistory(days: Int): List<DailyUsage> {
        if (!historyFile.exists()) return emptyList()
        val arr = JSONArray(historyFile.readText())
        val result = mutableListOf<DailyUsage>()
        for (i in 0 until minOf(arr.length(), days)) {
            result.add(parseDailyUsage(arr.getJSONObject(i)))
        }
        return result
    }

    // === PIN ===
    fun isPinSetup(): Boolean = prefs.contains(KEY_PIN_HASH)
    fun savePin(pin: String) {
        prefs.edit().putString(KEY_PIN_HASH, hashPin(pin)).apply()
    }
    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return hashPin(pin) == stored
    }
    fun changePin(oldPin: String, newPin: String): Boolean {
        if (!verifyPin(oldPin)) return false
        savePin(newPin)
        return true
    }

    // === Integrity ===
    fun verifyIntegrity(): Boolean =
        verifyChecksumForKey("rules", null) && verifyChecksumForKey("credits", null)

    private fun verifyChecksumForKey(key: String, json: String?): Boolean {
        val storedChecksum = checksumFile.takeIf { it.exists() }
            ?.readLines()?.find { it.startsWith("$key:") }?.removePrefix("$key:")
            ?: return json != null
        val data = json ?: prefs.getString(
            when(key) { "rules" -> KEY_RULES; "credits" -> KEY_CREDITS; else -> return true }, null
        ) ?: return false
        return computeHmac(data) == storedChecksum
    }

    private fun computeHmac(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec("pinecone_guard_key".toByteArray(), "HmacSHA256")
        mac.init(keySpec)
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun hashPin(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest("pinecone_pin_salt:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun writeBackup(content: String) {
        val existing = backupFile.takeIf { it.exists() }?.readLines()?.toMutableList() ?: mutableListOf()
        val prefix = content.substringBefore(":")
        existing.removeAll { it.startsWith("$prefix:") }
        existing.add(content)
        backupFile.writeText(existing.joinToString("\n"))
    }

    private fun readBackup(): String? =
        backupFile.takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    private fun writeChecksum(key: String, checksum: String) {
        val lines = checksumFile.takeIf { it.exists() }?.readLines()?.toMutableList() ?: mutableListOf()
        lines.removeAll { it.startsWith("$key:") }
        lines.add("$key:$checksum")
        checksumFile.writeText(lines.joinToString("\n"))
    }

    companion object {
        private const val KEY_RULES = "guard_rules"
        private const val KEY_CREDITS = "guard_credits"
        private const val KEY_SNAPSHOT = "guard_snapshot"
        private const val KEY_PIN_HASH = "guard_pin_hash"
    }

    // === JSON serialization ===
    private fun rulesToJson(r: RuleSet): String = JSONObject().apply {
        put("version", r.version)
        put("dailyTotalLimit", r.dailyTotalLimit ?: -1)
        put("pausedForToday", r.pausedForToday ?: "")
        put("categoryLimits", JSONArray().apply {
            r.categoryLimits.forEach { put(JSONObject().apply {
                put("id", it.categoryId); put("label", it.label); put("min", it.dailyMinutes ?: -1)
            })}
        })
        put("appLimits", JSONArray().apply {
            r.appLimits.forEach { put(JSONObject().apply {
                put("pkg", it.packageName); put("label", it.appLabel); put("min", it.dailyMinutes)
            })}
        })
        put("timeWindows", JSONArray().apply {
            r.timeWindows.forEach { put(JSONObject().apply {
                put("name", it.name); put("sh", it.startHour); put("sm", it.startMinute)
                put("eh", it.endHour); put("em", it.endMinute)
                put("days", JSONArray(it.daysOfWeek.toList()))
            })}
        })
        r.breakRule?.let { put("breakRule", JSONObject().apply {
            put("usage", it.usageMinutes); put("break", it.breakMinutes)
        })}
        put("creditConfig", JSONObject().apply {
            put("total", r.creditConfig.weeklyTotal)
            put("resetDay", r.creditConfig.resetDay.name)
            put("cost", r.creditConfig.overtimeCostPerMin)
            put("reward", r.creditConfig.earlyStopRewardPerMin)
        })
    }.toString()

    private fun parseRules(json: String): RuleSet? = try {
        val o = JSONObject(json)
        RuleSet(
            version = o.optInt("version", 1),
            dailyTotalLimit = o.optInt("dailyTotalLimit", -1).takeIf { it >= 0 },
            categoryLimits = parseCategoryLimits(o.optJSONArray("categoryLimits")),
            appLimits = parseAppLimits(o.optJSONArray("appLimits")),
            timeWindows = parseTimeWindows(o.optJSONArray("timeWindows")),
            breakRule = o.optJSONObject("breakRule")?.let {
                BreakRule(it.getInt("usage"), it.getInt("break"))
            },
            creditConfig = o.optJSONObject("creditConfig")?.let {
                CreditConfig(
                    weeklyTotal = it.getInt("total"),
                    resetDay = java.time.DayOfWeek.valueOf(it.getString("resetDay")),
                    overtimeCostPerMin = it.getInt("cost"),
                    earlyStopRewardPerMin = it.getInt("reward")
                )
            } ?: CreditConfig.DEFAULT,
            pausedForToday = o.optString("pausedForToday", "").takeIf { it.isNotBlank() }
        )
    } catch (_: Exception) { null }

    private fun parseCategoryLimits(arr: JSONArray?): List<CategoryLimit> =
        (0 until (arr?.length() ?: 0)).map {
            val o = arr!!.getJSONObject(it)
            CategoryLimit(o.getString("id"), o.getString("label"),
                o.optInt("min", -1).takeIf { m -> m >= 0 })
        }

    private fun parseAppLimits(arr: JSONArray?): List<AppLimit> =
        (0 until (arr?.length() ?: 0)).map {
            val o = arr!!.getJSONObject(it)
            AppLimit(o.getString("pkg"), o.getString("label"), o.getInt("min"))
        }

    private fun parseTimeWindows(arr: JSONArray?): List<TimeWindow> =
        (0 until (arr?.length() ?: 0)).map {
            val o = arr!!.getJSONObject(it)
            val daysArr = o.getJSONArray("days")
            val days = (0 until daysArr.length()).mapTo(mutableSetOf()) { daysArr.getInt(it) }
            TimeWindow(o.getString("name"), o.getInt("sh"), o.getInt("sm"),
                o.getInt("eh"), o.getInt("em"), days)
        }

    private fun creditsToJson(a: CreditAccount): String = JSONObject().apply {
        put("balance", a.balance); put("total", a.weeklyTotal)
        put("resetDay", a.resetDay.name); put("lastReset", a.lastResetTime)
        put("txns", JSONArray().apply {
            a.transactions.forEach { put(JSONObject().apply {
                put("ts", it.timestamp); put("amt", it.amount)
                put("reason", it.reason.name); put("after", it.balanceAfter)
            })}
        })
    }.toString()

    private fun parseCredits(json: String): CreditAccount? = try {
        val o = JSONObject(json)
        CreditAccount(
            balance = o.getInt("balance"), weeklyTotal = o.getInt("total"),
            resetDay = java.time.DayOfWeek.valueOf(o.getString("resetDay")),
            lastResetTime = o.getLong("lastReset"),
            transactions = (0 until (o.optJSONArray("txns")?.length() ?: 0)).map {
                val t = o.getJSONArray("txns").getJSONObject(it)
                CreditTransaction(t.getLong("ts"), t.getInt("amt"),
                    CreditReason.valueOf(t.getString("reason")), t.getInt("after"))
            }
        )
    } catch (_: Exception) { null }

    private fun snapshotToJson(s: UsageSnapshot): String = JSONObject().apply {
        put("date", s.date.toString()); put("total", s.totalSeconds)
        put("continuous", s.continuousSeconds); put("lastActivity", s.lastActivityTime)
        put("catUsage", JSONObject(s.categoryUsage))
        put("appUsage", JSONObject(s.appUsage))
    }.toString()

    private fun parseSnapshot(json: String): UsageSnapshot? = try {
        val o = JSONObject(json)
        UsageSnapshot(
            date = java.time.LocalDate.parse(o.getString("date")),
            totalSeconds = o.getLong("total"),
            continuousSeconds = o.getLong("continuous"),
            lastActivityTime = o.getLong("lastActivity"),
            categoryUsage = jsonObjectToMap(o.getJSONObject("catUsage")),
            appUsage = jsonObjectToMap(o.getJSONObject("appUsage"))
        )
    } catch (_: Exception) { null }

    private fun dailyUsageToJson(d: DailyUsage): JSONObject = JSONObject().apply {
        put("date", d.date.toString()); put("total", d.totalSeconds)
        put("byCat", JSONObject(d.byCategory)); put("byApp", JSONObject(d.byApp))
        put("grace", d.wasGraceUsed); put("creditChange", d.creditChange)
    }

    private fun parseDailyUsage(o: JSONObject): DailyUsage = DailyUsage(
        date = java.time.LocalDate.parse(o.getString("date")),
        totalSeconds = o.getLong("total"),
        byCategory = jsonObjectToMap(o.getJSONObject("byCat")),
        byApp = jsonObjectToMap(o.getJSONObject("byApp")),
        wasGraceUsed = o.getBoolean("grace"),
        creditChange = o.getInt("creditChange")
    )

    private fun jsonObjectToMap(o: JSONObject): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        o.keys().forEach { k -> map[k] = o.getLong(k) }
        return map
    }

}
