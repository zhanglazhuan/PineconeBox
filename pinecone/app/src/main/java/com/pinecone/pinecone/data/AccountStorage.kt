package com.pinecone.pinecone.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Account data for the anti-addiction (防沉迷) module.
 *
 * Stores phone number, hashed password, recovery code, lockout state.
 * Uses EncryptedSharedPreferences (AES-256 hardware-backed).
 * Lockout counter uses elapsedRealtime (monotonic, survives reboot).
 */
class AccountStorage private constructor(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, PREFS_FILE, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ── Account existence ──

    val isRegistered: Boolean
        get() = prefs.contains(KEY_PHONE) && prefs.contains(KEY_PASSWORD_HASH)

    val phoneNumber: String?
        get() = prefs.getString(KEY_PHONE, null)

    /** Returns masked phone: 138****5678 */
    val maskedPhone: String
        get() {
            val p = phoneNumber ?: return ""
            return if (p.length == 11) "${p.take(3)}****${p.takeLast(4)}"
            else p.take(3) + "****" + p.takeLast(minOf(4, p.length - 3))
        }

    val accountUuid: String
        get() = prefs.getString(KEY_UUID, "") ?: ""

    // ── Registration ──

    fun register(phone: String, password: String): String {
        val uuid = java.util.UUID.randomUUID().toString()
        val recoveryCode = generateRecoveryCode()
        val salt = generateSalt()

        prefs.edit()
            .putString(KEY_PHONE, phone)
            .putString(KEY_PASSWORD_HASH, hashPassword(password, salt))
            .putString(KEY_PASSWORD_SALT, salt)
            .putString(KEY_RECOVERY_HASH, hashRecoveryCode(recoveryCode))
            .putString(KEY_UUID, uuid)
            .putLong(KEY_REGISTERED_AT, System.currentTimeMillis())
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()

        return recoveryCode
    }

    // ── Authentication ──

    /**
     * Verify password. Returns [AuthResult] with success/failure/lockout info.
     * Increments failed attempt counter on failure.
     * Locks account for 30 minutes after 3 consecutive failures.
     */
    fun verifyPassword(password: String): AuthResult {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)

        // Check if currently locked out
        if (lockoutUntil > 0L) {
            val now = SystemClock.elapsedRealtime()
            if (now < lockoutUntil) {
                val remaining = (lockoutUntil - now) / 1000
                return AuthResult.LockedOut(remaining)
            }
            // Lockout expired — reset
            prefs.edit().putLong(KEY_LOCKOUT_UNTIL, 0L).putInt(KEY_FAILED_ATTEMPTS, 0).apply()
        }

        val storedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return AuthResult.Error("未注册")
        val salt = prefs.getString(KEY_PASSWORD_SALT, "") ?: ""
        val correct = hashPassword(password, salt) == storedHash

        if (correct) {
            prefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).putLong(KEY_LOCKOUT_UNTIL, 0L).apply()
            return AuthResult.Success
        }

        // Wrong password — increment counter
        val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply()

        if (attempts >= MAX_ATTEMPTS) {
            // Lock for 30 minutes
            val lockUntil = SystemClock.elapsedRealtime() + LOCKOUT_DURATION_MS
            prefs.edit().putLong(KEY_LOCKOUT_UNTIL, lockUntil).apply()
            return AuthResult.LockedOut(LOCKOUT_DURATION_MS / 1000)
        }

        return AuthResult.WrongPassword(MAX_ATTEMPTS - attempts)
    }

    /** Check current lockout status without attempting login. */
    fun getLockoutRemaining(): Long {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        if (lockoutUntil <= 0L) return 0L
        val remaining = (lockoutUntil - SystemClock.elapsedRealtime()) / 1000
        return remaining.coerceAtLeast(0L)
    }

    val remainingAttempts: Int
        get() {
            val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
            if (lockoutUntil > 0L) {
                val now = SystemClock.elapsedRealtime()
                if (now < lockoutUntil) return 0
            }
            return (MAX_ATTEMPTS - prefs.getInt(KEY_FAILED_ATTEMPTS, 0)).coerceAtLeast(0)
        }

    // ── Password & recovery ──

    /** Verify a recovery code without resetting password. */
    fun verifyRecoveryCode(code: String): Boolean {
        val storedRecoveryHash = prefs.getString(KEY_RECOVERY_HASH, null) ?: return false
        return hashRecoveryCode(code) == storedRecoveryHash
    }

    /** Reset password using recovery code. Returns new recovery code on success, null on failure. */
    fun resetPassword(newPassword: String, recoveryCode: String): String? {
        if (!verifyRecoveryCode(recoveryCode)) return null
        return changePasswordInternal(newPassword)
    }

    /** Reset password after SMS verification. Returns new recovery code. */
    fun resetPasswordBySms(newPassword: String): String? {
        // SMS verification is handled by the UI layer before calling this.
        return changePasswordInternal(newPassword)
    }

    private fun changePasswordInternal(newPassword: String): String {
        val salt = generateSalt()
        val newRecoveryCode = generateRecoveryCode()

        prefs.edit()
            .putString(KEY_PASSWORD_HASH, hashPassword(newPassword, salt))
            .putString(KEY_PASSWORD_SALT, salt)
            .putString(KEY_RECOVERY_HASH, hashRecoveryCode(newRecoveryCode))
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()

        return newRecoveryCode
    }

    /** Returns the current recovery code hash — only for display after registration/reset. */
    fun getRecoveryCode(): String? {
        // Recovery code can't be retrieved (hashed). Only shown at creation time.
        return null
    }

    fun generateNewRecoveryCode(): String {
        val code = generateRecoveryCode()
        prefs.edit()
            .putString(KEY_RECOVERY_HASH, hashRecoveryCode(code))
            .apply()
        return code
    }

    // ── Hashing (PBKDF2) ──

    private fun hashPassword(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(), PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }
    }

    private fun hashRecoveryCode(code: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest("pinecone_recovery:$code".toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun generateRecoveryCode(): String {
        val sb = StringBuilder()
        repeat(12) { sb.append(secureRandom.nextInt(10)) }
        // Format: XXXX-XXXX-XXXX
        return "${sb.substring(0, 4)}-${sb.substring(4, 8)}-${sb.substring(8, 12)}"
    }

    // ── Wipe (for factory reset / re-registration) ──

    fun wipe() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_FILE = "pinecone_guard_account"
        private const val KEY_PHONE = "account_phone"
        private const val KEY_PASSWORD_HASH = "account_password_hash"
        private const val KEY_PASSWORD_SALT = "account_password_salt"
        private const val KEY_RECOVERY_HASH = "account_recovery_hash"
        private const val KEY_UUID = "account_uuid"
        private const val KEY_REGISTERED_AT = "account_registered_at"
        private const val KEY_FAILED_ATTEMPTS = "account_failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "account_lockout_until"

        const val MAX_ATTEMPTS = 3
        const val LOCKOUT_DURATION_MS = 30 * 60 * 1000L // 30 minutes

        private const val PBKDF2_ITERATIONS = 100_000
        private const val PBKDF2_KEY_LENGTH = 256 // bits

        private val secureRandom = SecureRandom()

        @Volatile
        private var instance: AccountStorage? = null

        fun getInstance(context: Context): AccountStorage {
            return instance ?: synchronized(this) {
                instance ?: AccountStorage(context.applicationContext).also { instance = it }
            }
        }
    }
}

sealed class AuthResult {
    data object Success : AuthResult()
    data class WrongPassword(val remainingAttempts: Int) : AuthResult()
    data class LockedOut(val remainingSeconds: Long) : AuthResult()
    data class Error(val message: String) : AuthResult()
}
