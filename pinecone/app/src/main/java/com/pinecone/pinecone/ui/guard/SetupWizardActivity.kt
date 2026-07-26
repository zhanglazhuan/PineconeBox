package com.pinecone.pinecone.ui.guard

import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.guard.data.SecureStorage
import com.pinecone.guard.service.GuardClientHolder
import com.pinecone.guard.service.GuardDeviceAdminReceiver
import com.pinecone.pinecone.ui.theme.*

class SetupWizardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PineconeTheme {
                SetupWizardScreen(
                    isUsageStatsGranted = isUsageStatsGranted(),
                    isDeviceAdminActive = GuardDeviceAdminReceiver.isAdminActive(this),
                    onRequestUsageStats = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    onRequestDeviceAdmin = {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                GuardDeviceAdminReceiver.getComponentName(this@SetupWizardActivity))
                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                "松果智学需要设备管理器权限来在超时时锁定屏幕，保护孩子的用眼健康。")
                        }
                        startActivity(intent)
                    },
                    onSkip = {
                        checkAndProceed()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh Compose state on resume (permissions might have changed)
        setContent {
            PineconeTheme {
                SetupWizardScreen(
                    isUsageStatsGranted = isUsageStatsGranted(),
                    isDeviceAdminActive = GuardDeviceAdminReceiver.isAdminActive(this),
                    onRequestUsageStats = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    onRequestDeviceAdmin = {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                GuardDeviceAdminReceiver.getComponentName(this@SetupWizardActivity))
                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                "松果智学需要设备管理器权限来在超时时锁定屏幕，保护孩子的用眼健康。")
                        }
                        startActivity(intent)
                    },
                    onSkip = { checkAndProceed() }
                )
            }
        }
    }

    private fun checkAndProceed() {
        if (!GuardClientHolder.isInitialized) {
            GuardClientHolder.initialize(this)
        }
        val storage = SecureStorage(this)
        if (!storage.isPinSetup()) {
            startActivity(Intent(this, PinSetupActivity::class.java))
        }
        finish()
    }

    private fun isUsageStatsGranted(): Boolean {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000, now)
        return stats.isNotEmpty()
    }
}

@Composable
private fun SetupWizardScreen(
    isUsageStatsGranted: Boolean,
    isDeviceAdminActive: Boolean,
    onRequestUsageStats: () -> Unit,
    onRequestDeviceAdmin: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🛡️ 防沉迷初始化向导", fontSize = 30.sp,
            fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.padding(bottom = 48.dp))

        when {
            !isUsageStatsGranted -> {
                Text("步骤 1/2:\n需要授权「使用情况访问权限」\n才能统计应用使用时间",
                    fontSize = 22.sp, color = PineWarning,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 32.dp))
                Button(onClick = onRequestUsageStats) {
                    Text("前往授权", fontSize = 20.sp)
                }
            }
            !isDeviceAdminActive -> {
                Text("步骤 2/2:\n需要激活「设备管理器」\n才能在超时时锁定屏幕",
                    fontSize = 22.sp, color = PineWarning,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 32.dp))
                Button(onClick = onRequestDeviceAdmin) {
                    Text("激活设备管理器", fontSize = 20.sp)
                }
            }
            else -> {
                Text("✅ 所有权限已就绪\n防沉迷系统开始守护",
                    fontSize = 22.sp, color = Color(0xFF00FF00),
                    textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 32.dp))
                onSkip()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onSkip) { Text("跳过，稍后设置", fontSize = 18.sp) }
    }
}
