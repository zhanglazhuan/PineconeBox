package com.pinecone.launcher.ui.guard

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.pinecone.guard.api.UpdateResult
import com.pinecone.guard.engine.UpdateChecker
import com.pinecone.guard.engine.UpdateInstaller

class UpdateActivity : BaseSettingsActivity() {

    private lateinit var titleText: TextView
    private lateinit var detailText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var actionButton: Button
    private var latestUpdate: UpdateResult.Available? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
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
            text = "检查更新"; textSize = 20f; minWidth = 300; minHeight = 72
            setOnClickListener { onActionClick() }
        }
        layout.addView(actionButton)

        setSettingsContent("检查更新", layout)
        checkForUpdate()
    }

    private fun onActionClick() {
        latestUpdate?.let { downloadUpdate(it) } ?: checkForUpdate()
    }

    private fun checkForUpdate() {
        val checker = UpdateChecker(this)
        actionButton.isEnabled = false
        titleText.text = "正在检查..."

        checker.checkForUpdate { result ->
            runOnUiThread {
                when (result) {
                    is UpdateResult.UpToDate -> {
                        titleText.text = "已是最新版本"
                        detailText.text = "当前版本 ${
                            packageManager.getPackageInfo(packageName, 0).versionName
                        } · 无需更新"
                        actionButton.text = "重新检查"; actionButton.isEnabled = true
                    }
                    is UpdateResult.Available -> {
                        latestUpdate = result
                        titleText.text = "发现新版本 ${result.versionName}"
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
    }

    private fun downloadUpdate(update: UpdateResult.Available) {
        actionButton.isEnabled = false
        actionButton.text = "下载中..."
        progressBar.visibility = View.VISIBLE

        val installer = UpdateInstaller(this)
        installer.downloadAndInstall(
            update,
            onProgress = { p ->
                runOnUiThread { progressBar.progress = (p * 100).toInt() }
            },
            onComplete = { result ->
                runOnUiThread {
                    result.onSuccess {
                        titleText.text = "更新完成"
                        detailText.text = "正在重启桌面..."
                        progressBar.visibility = View.GONE
                    }.onFailure { e ->
                        titleText.text = "更新失败"
                        detailText.text = e.message
                        actionButton.text = "重试"; actionButton.isEnabled = true
                        progressBar.visibility = View.GONE
                    }
                }
            }
        )
    }
}
