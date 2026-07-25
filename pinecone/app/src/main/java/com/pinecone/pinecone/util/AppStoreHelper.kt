package com.pinecone.pinecone.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Opens 腾讯应用宝 to install or search for an app.
 */
object AppStoreHelper {

    /**
     * Open the app detail page in 应用宝.
     * Uses the web URL which Android will offer to open in 应用宝 if installed.
     */
    fun installByPackage(context: Context, pkgName: String, appName: String) {
        // 应用宝 app detail page — https://a.app.qq.com/o/simple.jsp?pkgname=xxx
        // 1. Try to open directly (应用宝 will intercept the URL if installed)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://a.app.qq.com/o/simple.jsp?pkgname=$pkgName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStart(context, intent)) return

        // 2. Fallback: sj.qq.com app detail page
        val sjIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://sj.qq.com/appdetail/$pkgName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStart(context, sjIntent)) return

        // 3. Nothing worked — search by name
        searchByName(context, appName)
    }

    /**
     * Search the app by name in 应用宝.
     */
    fun searchByName(context: Context, appName: String) {
        val encoded = Uri.encode(appName)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://sj.qq.com/search?query=$encoded")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (!tryStart(context, intent)) {
            Toast.makeText(context, "请安装腾讯应用宝后重试\n搜索: $appName", Toast.LENGTH_LONG).show()
        }
    }

    private fun tryStart(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
