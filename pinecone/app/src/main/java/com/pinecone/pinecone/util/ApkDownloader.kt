package com.pinecone.pinecone.util

import android.app.DownloadManager
import android.app.DownloadManager.Request
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Downloads an APK via system DownloadManager, then triggers install on completion.
 *
 * Usage:
 *   ApkDownloader.download(context, "https://example.com/app.apk", "myapp.apk", "My App")
 */
object ApkDownloader {

    private const val DOWNLOAD_DIR = "PineconeBox"

    /**
     * Start downloading an APK. Shows a Toast immediately.
     * When download completes, the system will show a notification;
     * tapping it opens the install screen.
     */
    fun download(context: Context, url: String, filename: String, appName: String) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val request = Request(Uri.parse(url)).apply {
            setTitle("正在下载 $appName")
            setDescription("松果智学 · 应用安装")
            setNotificationVisibility(Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "$DOWNLOAD_DIR/$filename"
            )
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
        }

        try {
            val downloadId = dm.enqueue(request)
            registerReceiver(context, downloadId, filename, appName)
            Toast.makeText(context, "开始下载 $appName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun registerReceiver(
        context: Context,
        downloadId: Long,
        filename: String,
        appName: String
    ) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return

                // Query download status
                val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = dm.query(query)
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        installApk(ctx, filename)
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        Toast.makeText(ctx, "$appName 下载失败", Toast.LENGTH_SHORT).show()
                    }
                }
                cursor.close()
                ctx.unregisterReceiver(this)
            }
        }

        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED
        )
    }

    private fun installApk(context: Context, filename: String) {
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "$DOWNLOAD_DIR/$filename"
        )
        if (!file.exists()) {
            Toast.makeText(context, "安装包未找到", Toast.LENGTH_SHORT).show()
            return
        }

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开安装器: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
