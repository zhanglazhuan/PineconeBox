# PineCone OS OTA 自动更新系统 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build HTTP-based OTA update system that checks for updates daily, downloads new Launcher APK, verifies integrity, installs in-place, and restarts launcher.

**Architecture:** Python HTTP server serves `manifest.json` + APK files. GuardService runs `UpdateChecker` daily. When update found, parent sees notification in settings page. `UpdateInstaller` handles download + SHA256 verification + file replacement + launcher restart.

**Tech Stack:** Kotlin (Android), Python 3 (server), Android SDK 36, GuardService

## Global Constraints

- minSdk 36, targetSdk 36, compileSdk 36
- JVM target Java 11, kotlin.code.style=official
- No new Gradle dependencies (HTTP via java.net.URL, JSON via org.json)
- All new code: `com.pinecone.guard` (library) and `com.pinecone.launcher.ui.guard` (app)
- Server: Python 3 stdlib only, no frameworks
- Launcher APK installed as system app at `/system/app/PineConeLauncher/`

---

## File Mapping

### New files

```
update-server/
├── server.py                          # Dev HTTP server
├── files/
│   └── manifest.json                  # Version manifest

guard/src/main/java/com/pinecone/guard/
├── api/
│   └── UpdateResult.kt                # Sealed class for update check result
└── engine/
    ├── UpdateChecker.kt               # HTTP manifest fetch + version compare
    └── UpdateInstaller.kt             # Download + SHA256 + install + restart

app/src/main/java/com/pinecone/launcher/ui/guard/
└── UpdateActivity.kt                  # Update details + progress UI
```

### Modified files

```
app/src/main/AndroidManifest.xml       # Add UpdateActivity
app/src/main/java/com/pinecone/launcher/ui/guard/ParentSettingsActivity.kt  # Add OTA entry
guard/src/main/java/com/pinecone/guard/service/GuardService.kt             # Add daily OTA check
```

---

## Phase 1: Server

### Task 1: Create update server + manifest

**Files:**
- Create: `update-server/server.py`
- Create: `update-server/files/manifest.json`

- [ ] **Step 1: Create directory**

```bash
mkdir -p update-server/files
```

- [ ] **Step 2: Write `update-server/files/manifest.json`**

```json
{
  "launcher": {
    "versionCode": 1,
    "versionName": "1.0.0",
    "url": "/files/app-release.apk",
    "size": 15728640,
    "sha256": "PLACEHOLDER",
    "changelog": "- 初始版本"
  },
  "minApiLevel": 36,
  "minAppVersion": 1
}
```

- [ ] **Step 3: Write `update-server/server.py`**

```python
"""PineCone Update Server — minimal HTTP server for development."""

import json
import os
from http.server import HTTPServer, SimpleHTTPRequestHandler

PORT = 8080
MANIFEST_PATH = "files/manifest.json"


class Handler(SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/api/manifest.json":
            if not os.path.exists(MANIFEST_PATH):
                self.send_error(404, "No manifest")
                return
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            with open(MANIFEST_PATH, "rb") as f:
                self.wfile.write(f.read())
        else:
            super().do_GET()

    def log_message(self, fmt, *args):
        print("[%s] %s" % (self.client_address[0], args[0] % args[1:]))


if __name__ == "__main__":
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    print("PineCone Update Server")
    print("  http://0.0.0.0:%d" % PORT)
    print("  Manifest: http://localhost:%d/api/manifest.json" % PORT)
    HTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
```

- [ ] **Step 4: Verify server starts**

```bash
cd update-server && python3 server.py &
sleep 1
curl -s http://localhost:8080/api/manifest.json | python3 -m json.tool
kill %1
```

Expected: Valid JSON output with `"versionCode": 1`

---

## Phase 2: Guard module — engine

### Task 2: UpdateResult + UpdateChecker

**Files:**
- Create: `guard/src/main/java/com/pinecone/guard/api/UpdateResult.kt`
- Create: `guard/src/main/java/com/pinecone/guard/engine/UpdateChecker.kt`

**Produces:** `UpdateChecker.checkForUpdate()` returns `UpdateResult` — `Available`, `UpToDate`, or `Error`

- [ ] **Step 1: Write `UpdateResult.kt`**

```kotlin
package com.pinecone.guard.api

sealed class UpdateResult {
    data object UpToDate : UpdateResult()

    data class Available(
        val versionCode: Int,
        val versionName: String,
        val url: String,
        val size: Long,
        val sha256: String,
        val changelog: String
    ) : UpdateResult()

    data class Error(val message: String) : UpdateResult()
}
```

- [ ] **Step 2: Write `UpdateChecker.kt`**

```kotlin
package com.pinecone.guard.engine

import android.content.Context
import com.pinecone.guard.api.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class UpdateChecker(private val context: Context) {

    private val baseUrl = "http://192.168.1.100:8080" // TODO: load from config

    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val manifest = fetchManifest() ?: return@withContext UpdateResult.Error("无法连接服务器")
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
```

- [ ] **Step 3: Verify compilation**

```bash
cd launcher && export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && ./gradlew :guard:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

---

### Task 3: UpdateInstaller

**Files:**
- Create: `guard/src/main/java/com/pinecone/guard/engine/UpdateInstaller.kt`

**Produces:** `UpdateInstaller.downloadAndInstall(update, onProgress)` — downloads, verifies, replaces APK

- [ ] **Step 1: Write `UpdateInstaller.kt`**

```kotlin
package com.pinecone.guard.engine

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.pinecone.guard.api.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class UpdateInstaller(private val context: Context) {

    companion object {
        private const val SYSTEM_APK_PATH = "/system/app/PineConeLauncher/PineConeLauncher.apk"
    }

    suspend fun downloadAndInstall(
        update: UpdateResult.Available,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val apkFile = File(context.filesDir, "update.apk")

            // 1. Download
            val totalSize = downloadWithProgress(update.url, apkFile, update.size, onProgress)

            // 2. Verify SHA256
            if (!verifySha256(apkFile, update.sha256)) {
                apkFile.delete()
                return@withContext Result.failure(SecurityException("APK 校验失败，文件可能被篡改"))
            }

            // 3. Replace system APK
            val systemApk = File(SYSTEM_APK_PATH)
            apkFile.copyTo(systemApk, overwrite = true)
            Runtime.getRuntime().exec(arrayOf("chmod", "644", SYSTEM_APK_PATH)).waitFor()

            // 4. Restart launcher
            restartLauncher()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
            input.close()
            output.close()
            connection.disconnect()
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
```

- [ ] **Step 2: Verify compilation**

```bash
cd launcher && export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && ./gradlew :guard:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

---

## Phase 3: GuardService integration

### Task 4: Wire OTA check into GuardService

**Files:**
- Modify: `guard/src/main/java/com/pinecone/guard/service/GuardService.kt`

**Produces:** GuardService runs `checkForUpdate()` daily at 3:00 AM, pushes result to UI via `GuardStateListener`

- [ ] **Step 1: Add OTA scheduled check to GuardService**

Add these fields to `GuardService`:

```kotlin
import com.pinecone.guard.engine.UpdateChecker
import com.pinecone.guard.api.UpdateResult
import kotlinx.coroutines.*

private val updateChecker by lazy { UpdateChecker(this) }
private var otaJob: Job? = null
private val otaScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
```

Add this method:

```kotlin
private fun scheduleOtaCheck() {
    otaJob?.cancel()
    otaJob = otaScope.launch {
        while (isActive) {
            delay(computeDelayUntil3AM())
            val result = updateChecker.checkForUpdate()
            if (result is UpdateResult.Available) {
                // Push notification to UI
                withContext(Dispatchers.Main) {
                    sendToClient(createMsg(MSG_GET_STATE, Bundle().apply {
                        putBoolean("updateAvailable", true)
                        putString("updateVersion", result.versionName)
                        putInt("updateSize", result.size.toInt())
                    }))
                }
            }
            delay(24 * 3600 * 1000L) // then every 24h
        }
    }
}

private fun computeDelayUntil3AM(): Long {
    val now = java.time.LocalTime.now()
    val target = java.time.LocalTime.of(3, 0)
    val delayMinutes = if (now.isBefore(target))
        java.time.Duration.between(now, target).toMinutes()
    else
        java.time.Duration.between(now, target).toMinutes() + 24 * 60
    return delayMinutes * 60 * 1000L
}
```

- [ ] **Step 2: Call `scheduleOtaCheck()` in `onCreate()`**

Add after `engine.start()`:

```kotlin
scheduleOtaCheck()
```

- [ ] **Step 3: Verify**

```bash
cd launcher && export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && ./gradlew :guard:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

---

## Phase 4: UI

### Task 5: UpdateActivity + wire ParentSettings

**Files:**
- Create: `app/src/main/java/com/pinecone/launcher/ui/guard/UpdateActivity.kt`
- Modify: `app/src/main/java/com/pinecone/launcher/ui/guard/ParentSettingsActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Write `UpdateActivity.kt`**

```kotlin
package com.pinecone.launcher.ui.guard

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.pinecone.guard.engine.UpdateChecker
import com.pinecone.guard.engine.UpdateInstaller
import com.pinecone.guard.api.UpdateResult
import kotlinx.coroutines.*

class UpdateActivity : Activity() {

    private lateinit var titleText: TextView
    private lateinit var detailText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var actionButton: Button
    private var update: UpdateResult.Available? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(64, 96, 64, 64)
            setBackgroundColor(0xFF1A1A2E.toInt())
        }

        titleText = TextView(this).apply {
            text = "检查更新"; textSize = 30f
            setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        layout.addView(titleText)

        detailText = TextView(this).apply {
            textSize = 22f; setTextColor(0xFF8B949E.toInt())
            gravity = Gravity.CENTER; setPadding(0, 0, 0, 32)
        }
        layout.addView(detailText)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0
            layoutParams = LinearLayout.LayoutParams(800, 24)
            visibility = View.GONE
        }
        layout.addView(progressBar)

        actionButton = Button(this).apply {
            text = "检查"; textSize = 20f; minWidth = 300; minHeight = 72
            setOnClickListener { checkOrInstall() }
        }
        layout.addView(actionButton)

        setContentView(layout)
        checkOrInstall()
    }

    private fun checkOrInstall() {
        val checker = UpdateChecker(this)
        actionButton.isEnabled = false
        titleText.text = "正在检查..."

        CoroutineScope(Dispatchers.Main).launch {
            when (val result = checker.checkForUpdate()) {
                is UpdateResult.UpToDate -> {
                    titleText.text = "已是最新版本"
                    val current = packageManager.getPackageInfo(packageName, 0).versionCode
                    detailText.text = "当前版本 ${packageManager.getPackageInfo(packageName, 0).versionName} · 无需更新"
                    actionButton.text = "重新检查"; actionButton.isEnabled = true
                }
                is UpdateResult.Available -> {
                    update = result
                    titleText.text = "🆕 发现新版本 ${result.versionName}"
                    detailText.text = "${result.size / 1048576} MB\n\n${result.changelog}"
                    actionButton.text = "立即更新"; actionButton.isEnabled = true
                }
                is UpdateResult.Error -> {
                    titleText.text = "检查失败"
                    detailText.text = result.message
                    actionButton.text = "重试"; actionButton.isEnabled = true
                }
            }
        }
    }

    private fun startUpdate(update: UpdateResult.Available) {
        actionButton.isEnabled = false
        actionButton.text = "下载中..."
        progressBar.visibility = View.VISIBLE

        val installer = UpdateInstaller(this)
        CoroutineScope(Dispatchers.Main).launch {
            installer.downloadAndInstall(update) { progress ->
                progressBar.progress = (progress * 100).toInt()
            }.onSuccess {
                titleText.text = "更新完成"
                detailText.text = "即将重启桌面..."
            }.onFailure { e ->
                titleText.text = "更新失败"
                detailText.text = e.message
                actionButton.text = "重试"; actionButton.isEnabled = true
                progressBar.visibility = View.GONE
            }
        }
    }
}
```

- [ ] **Step 2: Add "检查更新" entry to ParentSettingsActivity**

Append to `buildSettingsItems()` list:

```kotlin
SettingsItem("🆕 检查更新", "检查并安装新版本桌面") {
    startActivity(Intent(this, UpdateActivity::class.java))
}
```

- [ ] **Step 3: Register UpdateActivity in AndroidManifest.xml**

```xml
<activity
    android:name="com.pinecone.launcher.ui.guard.UpdateActivity"
    android:exported="false" />
```

- [ ] **Step 4: Full build + verify**

```bash
cd launcher && export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

---

## Implementation Order

```
Phase 1 (Task 1):  Server + manifest        ← can test standalone
Phase 2 (Task 2-3): Guard engine (checker + installer)
Phase 3 (Task 4):   GuardService wire-up
Phase 4 (Task 5):   UI + integration
```

Each phase builds on the previous. Phase 1 is testable standalone with curl.
