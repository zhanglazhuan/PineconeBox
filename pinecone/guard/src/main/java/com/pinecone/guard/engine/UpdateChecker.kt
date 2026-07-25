package com.pinecone.guard.engine

import android.content.Context
import com.pinecone.guard.api.UpdateResult
import org.json.JSONObject
import java.net.URL

class UpdateChecker(private val context: Context) {

    private val baseUrl = "http://192.168.1.100:8080"

    fun checkForUpdate(callback: (UpdateResult) -> Unit) {
        Thread {
            val result = try {
                val manifest = fetchManifest()
                    ?: return@Thread callback(UpdateResult.Error("无法连接服务器"))
                val latest = manifest.launcherVersionCode
                val current = currentVersionCode()

                if (latest > current) {
                    UpdateResult.Available(
                        versionCode = latest,
                        versionName = manifest.launcherVersionName,
                        url = "$baseUrl${manifest.launcherUrl}",
                        size = manifest.launcherSize,
                        sha256 = manifest.launcherSha256,
                        changelog = manifest.launcherChangelog
                    )
                } else {
                    UpdateResult.UpToDate
                }
            } catch (e: Exception) {
                UpdateResult.Error(e.message ?: "未知错误")
            }
            callback(result)
        }.start()
    }

    private fun currentVersionCode(): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return info.versionCode
    }

    private fun fetchManifest(): ManifestData? {
        val json = URL("$baseUrl/api/manifest.json").readText()
        val o = JSONObject(json).getJSONObject("launcher")
        return ManifestData(
            launcherVersionCode = o.getInt("versionCode"),
            launcherVersionName = o.getString("versionName"),
            launcherUrl = o.getString("url"),
            launcherSize = o.getLong("size"),
            launcherSha256 = o.getString("sha256"),
            launcherChangelog = o.getString("changelog")
        )
    }

    private data class ManifestData(
        val launcherVersionCode: Int,
        val launcherVersionName: String,
        val launcherUrl: String,
        val launcherSize: Long,
        val launcherSha256: String,
        val launcherChangelog: String
    )
}
