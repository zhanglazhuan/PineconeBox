package com.pinecone.pinecone.ui.guard

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.guard.service.GuardClientHolder
import com.pinecone.pinecone.ui.guard.editors.DailyLimitActivity
import com.pinecone.pinecone.ui.guard.editors.BreakRuleActivity
import com.pinecone.pinecone.ui.guard.editors.TimeWindowActivity
import com.pinecone.pinecone.ui.guard.editors.CategoryLimitActivity
import com.pinecone.pinecone.ui.guard.editors.AppLimitActivity
import com.pinecone.pinecone.ui.guard.editors.CreditConfigActivity
import com.pinecone.pinecone.ui.guard.editors.UsageHistoryActivity
import com.pinecone.pinecone.ui.theme.*

@Composable
fun ParentSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var showPauseDialog by remember { mutableStateOf(false) }

    val items = remember {
        listOf(
            SettingsItem("每日总时长", "2 小时 30 分") {
                context.startActivity(Intent(context, DailyLimitActivity::class.java))
            },
            SettingsItem("息屏间隔", "每 40 分 休 10 分") {
                context.startActivity(Intent(context, BreakRuleActivity::class.java))
            },
            SettingsItem("可用时段", "周一至周五 16:00-21:00") {
                context.startActivity(Intent(context, TimeWindowActivity::class.java))
            },
            SettingsItem("内容分类限制", "") {
                context.startActivity(Intent(context, CategoryLimitActivity::class.java))
            },
            SettingsItem("App 单独限制", "") {
                context.startActivity(Intent(context, AppLimitActivity::class.java))
            },
            SettingsItem("信用积分", "100分 / 每周一重置") {
                context.startActivity(Intent(context, CreditConfigActivity::class.java))
            },
            SettingsItem("使用统计", "") {
                context.startActivity(Intent(context, UsageHistoryActivity::class.java))
            },
            SettingsItem("修改 PIN", "") {
                context.startActivity(Intent(context, PinSetupActivity::class.java))
            },
            SettingsItem("检查更新", "检查并安装新版本桌面") {
                context.startActivity(Intent(context, UpdateActivity::class.java))
            }
        )
    }

    GuardSettingsScaffold(title = "家长设置", onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(items) { _, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { item.action() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            if (item.subtitle.isNotEmpty()) {
                                Text(
                                    text = item.subtitle,
                                    fontSize = 14.sp,
                                    color = PineTextSecondary
                                )
                            }
                        }
                        Text("›", fontSize = 24.sp, color = PineTextMuted)
                    }
                }
            }

            // ── Pause guard button — red, prominent, at page bottom ──
            Button(
                onClick = { showPauseDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PineError,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "暂停防沉迷（今天不限制）",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showPauseDialog) {
        AlertDialog(
            onDismissRequest = { showPauseDialog = false },
            title = { Text("暂停防沉迷") },
            text = { Text("确认今天不限制使用时间？\n明天凌晨自动恢复规则。") },
            confirmButton = {
                TextButton(onClick = {
                    GuardClientHolder.pauseToday()
                    Toast.makeText(context, "今天防沉迷已暂停", Toast.LENGTH_SHORT).show()
                    showPauseDialog = false
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showPauseDialog = false }) { Text("取消") }
            }
        )
    }
}

data class SettingsItem(
    val title: String,
    val subtitle: String,
    val action: () -> Unit
)
