package com.pinecone.pinecone.ui.guard

import android.os.Bundle
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
import com.pinecone.guard.api.UpdateResult
import com.pinecone.guard.engine.UpdateChecker
import com.pinecone.guard.engine.UpdateInstaller
import com.pinecone.pinecone.ui.theme.PineAccent
import com.pinecone.pinecone.ui.theme.PineconeTheme

class UpdateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PineconeTheme {
                GuardSettingsScaffold(title = "检查更新", onBack = { finish() }) {
                    UpdateScreen()
                }
            }
        }
    }
}

@Composable
private fun UpdateScreen() {
    val context = LocalContext.current
    var titleText by remember { mutableStateOf("检查更新") }
    var detailText by remember { mutableStateOf("") }
    var actionLabel by remember { mutableStateOf("检查更新") }
    var progress by remember { mutableFloatStateOf(0f) }
    var showProgress by remember { mutableStateOf(false) }
    var latestUpdate by remember { mutableStateOf<UpdateResult.Available?>(null) }
    var buttonEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        checkForUpdate(context) { result ->
            when (result) {
                is UpdateResult.UpToDate -> {
                    titleText = "已是最新版本"
                    detailText = "当前版本 ${context.packageManager.getPackageInfo(context.packageName, 0).versionName} · 无需更新"
                    actionLabel = "重新检查"; buttonEnabled = true
                }
                is UpdateResult.Available -> {
                    latestUpdate = result
                    titleText = "发现新版本 ${result.versionName}"
                    detailText = "${result.size / 1048576} MB\n\n${result.changelog}"
                    actionLabel = "立即更新"; buttonEnabled = true
                }
                is UpdateResult.Error -> {
                    titleText = "检查失败"
                    detailText = result.message
                    actionLabel = "重试"; buttonEnabled = true
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(titleText, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            color = Color.White, textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp))
        Text(detailText, fontSize = 22.sp, color = Color(0xFF8B949E),
            textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 32.dp))

        if (showProgress) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(0.6f).height(24.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = {
                latestUpdate?.let { update ->
                    buttonEnabled = false; actionLabel = "下载中..."; showProgress = true
                    val installer = UpdateInstaller(context as android.content.Context)
                    installer.downloadAndInstall(
                        update,
                        onProgress = { p -> progress = p.toFloat() },
                        onComplete = { r ->
                            r.onSuccess {
                                titleText = "更新完成"; detailText = "正在重启桌面..."
                                showProgress = false
                            }.onFailure { e ->
                                titleText = "更新失败"; detailText = e.message ?: ""
                                actionLabel = "重试"; buttonEnabled = true; showProgress = false
                            }
                        }
                    )
                } ?: checkForUpdate(context) { result ->
                    // Re-check
                }
            },
            enabled = buttonEnabled
        ) {
            Text(actionLabel, fontSize = 20.sp)
        }
    }
}

private fun checkForUpdate(context: android.content.Context, callback: (UpdateResult) -> Unit) {
    val checker = UpdateChecker(context)
    checker.checkForUpdate { result ->
        android.os.Handler(android.os.Looper.getMainLooper()).post { callback(result) }
    }
}
