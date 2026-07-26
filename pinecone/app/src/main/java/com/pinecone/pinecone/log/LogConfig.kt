package com.pinecone.pinecone.log

import android.content.Context
import android.content.SharedPreferences
import com.pinecone.pinecone.data.AccountStorage
import java.util.UUID

/**
 * Configuration for the logging system, backed by SharedPreferences.
 */
class LogConfig private constructor(context: Context) {

    private val appCtx = context.applicationContext
    private val prefs: SharedPreferences =
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Device identity (lazy, generated once) ──

    val deviceId: String by lazy {
        prefs.getString(KEY_DEVICE_ID, null) ?: run {
            val id = "pinecone-${UUID.randomUUID().toString().take(8)}"
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            id
        }
    }

    // ── Upload settings ──

    var uploadEnabled: Boolean
        get() = prefs.getBoolean(KEY_UPLOAD_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_UPLOAD_ENABLED, value).apply()

    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    // ── Server config ──

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var deviceToken: String?
        get() = prefs.getString(KEY_DEVICE_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_TOKEN, value).apply()

    // ── Upload state ──

    var lastBatchSeq: Int
        get() = prefs.getInt(KEY_LAST_BATCH_SEQ, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_BATCH_SEQ, value).apply()

    // ── Account UUID ──

    fun getAccountUuid(): String {
        return try {
            AccountStorage.getInstance(appCtx).accountUuid
        } catch (_: Exception) { "" }
    }

    companion object {
        private const val PREFS_NAME = "pinecone_log_config"
        private const val KEY_DEVICE_ID = "log_device_id"
        private const val KEY_UPLOAD_ENABLED = "log_upload_enabled"
        private const val KEY_WIFI_ONLY = "log_wifi_only"
        private const val KEY_SERVER_URL = "log_server_url"
        private const val KEY_DEVICE_TOKEN = "log_device_token"
        private const val KEY_LAST_BATCH_SEQ = "log_last_batch_seq"
        private const val DEFAULT_SERVER_URL = "https://api.pineconeos.com/api/v1"

        @Volatile
        private var instance: LogConfig? = null

        fun getInstance(context: Context): LogConfig {
            return instance ?: synchronized(this) {
                instance ?: LogConfig(context.applicationContext).also { instance = it }
            }
        }
    }
}
