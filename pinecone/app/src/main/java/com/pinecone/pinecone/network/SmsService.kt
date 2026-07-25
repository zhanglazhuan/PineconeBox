package com.pinecone.pinecone.network

import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * SMS verification code service.
 *
 * Development: [DevSmsService] generates a code locally and shows it to the caller.
 * Production: implement with Alibaba Cloud / Tencent Cloud SMS API.
 */
interface SmsService {
    /** Send a verification code to the given phone. Returns the code (dev) or empty string (prod). */
    suspend fun sendVerificationCode(phoneNumber: String): Result<String>
}

/**
 * Development implementation — generates a random 6-digit code.
 * In production, this would call Alibaba Cloud SMS API.
 */
class DevSmsService : SmsService {

    private var lastSendTime = 0L
    private var lastCode = ""
    private var lastPhone = ""

    override suspend fun sendVerificationCode(phoneNumber: String): Result<String> {
        // Rate limit: 60 seconds between sends to same number
        val now = System.currentTimeMillis()
        if (phoneNumber == lastPhone && (now - lastSendTime) < 60_000) {
            val waitSeconds = 60 - (now - lastSendTime) / 1000
            return Result.failure(IllegalStateException("请 ${waitSeconds} 秒后再试"))
        }

        // Simulate network delay
        delay(500)

        // Generate 6-digit code
        val code = String.format("%06d", Random.nextInt(0, 1_000_000))
        lastCode = code
        lastPhone = phoneNumber
        lastSendTime = now

        return Result.success(code)
    }

    /** Verify a code against the last sent one. 5-minute expiry. */
    fun verifyCode(phoneNumber: String, code: String): Boolean {
        val now = System.currentTimeMillis()
        if (phoneNumber != lastPhone) return false
        if ((now - lastSendTime) > 5 * 60 * 1000) return false // 5 min expiry
        return code == lastCode
    }
}
