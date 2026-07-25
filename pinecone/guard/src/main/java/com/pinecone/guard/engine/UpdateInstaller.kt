package com.pinecone.guard.engine

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.pinecone.guard.api.UpdateResult
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class UpdateInstaller(private val context: Context) {

    companion object {
        private const val SYSTEM_APK_PATH = "/system/app/PineConeLauncher/PineConeLauncher.apk"
    }

    fun downloadAndInstall(
        update: UpdateResult.Available,
        onProgress: (Float) -> Unit,
        onComplete: (Result<Unit>) -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        Thread {
            try {
                val apkFile = File(context.filesDir, "update.apk")

                // 1. Download
                downloadWithProgress(update.url, apkFile, update.size, { p ->
                    handler.post { onProgress(p) }
                })

                // 2. Verify SHA256
                if (!verifySha256(apkFile, update.sha256)) {
                    apkFile.delete()
                    handler.post { onComplete(Result.failure(SecurityException("APK 校验失败"))) }
                    return@Thread
                }

                // 3. Replace system APK
                val systemApk = File(SYSTEM_APK_PATH)
                apkFile.copyTo(systemApk, overwrite = true)
                Runtime.getRuntime().exec(arrayOf("chmod", "644", SYSTEM_APK_PATH)).waitFor()

                // 4. Restart launcher on main thread
                handler.post {
                    restartLauncher()
                    onComplete(Result.success(Unit))
                }
            } catch (e: Exception) {
                handler.post { onComplete(Result.failure(e)) }
            }
        }.start()
    }

    private fun downloadWithProgress(
        urlStr: String, dest: File, totalSize: Long, onProgress: (Float) -> Unit
    ): Long {
        val connection = URL(urlStr).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000

        val existing = if (dest.exists()) dest.length() else 0L
        if (existing > 0 && existing < totalSize) {
            connection.setRequestProperty("Range", "bytes=$existing-")
        }
        connection.connect()

        val responseCode = connection.responseCode
        if (responseCode !in 200..299 && responseCode != 206) {
            throw RuntimeException("HTTP $responseCode")
        }

        val contentLength = connection.contentLength.toLong()
        val effectiveTotal = if (contentLength > 0) existing + contentLength else totalSize

        val input = connection.inputStream
        val output = dest.outputStream()
        val buffer = ByteArray(8192)
        var downloaded = existing

        try {
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                downloaded += read
                if (effectiveTotal > 0) {
                    onProgress(downloaded.toFloat() / effectiveTotal)
                }
            }
        } finally {
            input.close(); output.close(); connection.disconnect()
        }
        return downloaded
    }

    private fun verifySha256(file: File, expected: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = fis.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expected, ignoreCase = true)
    }

    private fun restartLauncher() {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launchIntent)
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
