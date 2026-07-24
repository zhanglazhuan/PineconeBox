package com.pinecone.launcher.ui.guard

import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import com.pinecone.guard.service.GuardClientHolder
import com.pinecone.guard.service.GuardDeviceAdminReceiver

class SetupWizardActivity : BaseSettingsActivity() {

    private lateinit var statusText: TextView
    private lateinit var actionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            textSize = 22f; setTextColor(0xFFFFFF00.toInt())
            gravity = Gravity.CENTER; setPadding(0, 0, 0, 32)
        }
        layout.addView(statusText)

        actionButton = Button(this).apply {
            textSize = 20f; minWidth = 400; minHeight = 80
            setTextColor(0xFF000000.toInt()); setBackgroundColor(0xFF00FF00.toInt())
        }
        layout.addView(actionButton)

        layout.addView(Button(this).apply {
            text = "跳过，稍后设置"; textSize = 18f; minWidth = 300; minHeight = 60
            setOnClickListener { checkAndProceed() }
        })

        setSettingsContent("🛡️ 防沉迷初始化向导", layout)
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        when {
            !isUsageStatsGranted() -> {
                statusText.text = "步骤 1/2:\n需要授权「使用情况访问权限」\n才能统计应用使用时间"
                actionButton.text = "前往授权"; actionButton.visibility = Button.VISIBLE
                actionButton.setOnClickListener {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
            !isDeviceAdminActive() -> {
                statusText.text = "步骤 2/2:\n需要激活「设备管理器」\n才能在超时时锁定屏幕"
                actionButton.text = "激活设备管理器"; actionButton.visibility = Button.VISIBLE
                actionButton.setOnClickListener {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            GuardDeviceAdminReceiver.getComponentName(this@SetupWizardActivity))
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "松果智学需要设备管理器权限来在超时时锁定屏幕，保护孩子的用眼健康。")
                    }
                    startActivity(intent)
                }
            }
            else -> {
                statusText.text = "✅ 所有权限已就绪\n防沉迷系统开始守护"
                actionButton.visibility = Button.GONE
                checkAndProceed()
            }
        }
    }

    private fun checkAndProceed() {
        if (!GuardClientHolder.isInitialized) {
            GuardClientHolder.initialize(this)
        }
        val storage = com.pinecone.guard.data.SecureStorage(this)
        if (!storage.isPinSetup()) {
            val intent = Intent(this, PinSetupActivity::class.java)
            startActivity(intent)
        }
        finish()
    }

    private fun isUsageStatsGranted(): Boolean {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000, now)
        return stats.isNotEmpty()
    }

    private fun isDeviceAdminActive(): Boolean =
        GuardDeviceAdminReceiver.isAdminActive(this)
}
