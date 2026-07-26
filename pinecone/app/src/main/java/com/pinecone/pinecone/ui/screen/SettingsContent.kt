package com.pinecone.pinecone.ui.screen

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
import com.pinecone.pinecone.ui.guard.PinSetupActivity
import com.pinecone.pinecone.ui.guard.UpdateActivity
import com.pinecone.pinecone.ui.theme.*

/**
 * Inline parent settings list shown in the content area
 * when "家长设置" is selected in the Settings tab sidebar.
 */
@Composable
fun SettingsContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showPauseDialog by remember { mutableStateOf(false) }

    // Read version to establish recomposition dependency on guard state changes
    @Suppress("UNUSED_VARIABLE")
    val guardVersion = GuardClientHolder.rulesVersion
    // Read latest values from guard cache (no remember — must reflect edits)
    val rules = GuardClientHolder.cachedRules

    fun formatDailyLimit(minutes: Int?): String =
        if (minutes == null) "不限" else "${minutes / 60} 小时 ${minutes % 60} 分"

    fun formatBreakRule(): String {
        val br = rules.breakRule
        return if (br != null) "每 ${br.usageMinutes} 分 休 ${br.breakMinutes} 分" else "不限制"
    }

    fun formatTimeWindow(): String {
        val wd = rules.timeWindows.find { it.daysOfWeek.contains(1) }
        val we = rules.timeWindows.find { it.daysOfWeek.contains(6) }
        return when {
            wd != null && we != null ->
                "周一至周五 ${"%02d:%02d".format(wd.startHour, wd.startMinute)}-${"%02d:%02d".format(wd.endHour, wd.endMinute)}"
            else -> "未设置"
        }
    }

    fun formatCredit(): String {
        val credits = GuardClientHolder.cachedCredits
        return "${credits.balance}分 / 每周${credits.resetDay.name.take(3)}重置"
    }

    val items = listOf(
        SettingsRow("每日总时长", formatDailyLimit(rules.dailyTotalLimit)) {
            context.startActivity(Intent(context, DailyLimitActivity::class.java))
        },
        SettingsRow("强制休息间隔", formatBreakRule()) {
            context.startActivity(Intent(context, BreakRuleActivity::class.java))
        },
        SettingsRow("可用时段", formatTimeWindow()) {
            context.startActivity(Intent(context, TimeWindowActivity::class.java))
        },
        SettingsRow("内容分类限制", "") {
            context.startActivity(Intent(context, CategoryLimitActivity::class.java))
        },
        SettingsRow("App 单独限制", "") {
            context.startActivity(Intent(context, AppLimitActivity::class.java))
        },
        SettingsRow("信用积分", formatCredit()) {
            context.startActivity(Intent(context, CreditConfigActivity::class.java))
        },
        SettingsRow("使用统计", "") {
            context.startActivity(Intent(context, UsageHistoryActivity::class.java))
        },
        SettingsRow("修改 PIN", "") {
            context.startActivity(Intent(context, PinSetupActivity::class.java))
        },
        SettingsRow("检查更新", "检查并安装新版本桌面") {
            context.startActivity(Intent(context, UpdateActivity::class.java))
        },
        SettingsRow("暂停防沉迷（今天不限制）", "") {
            showPauseDialog = true
        }
    )

    LazyColumn(
        modifier = modifier.padding(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Text(
                text = "家长设置",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

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

private data class SettingsRow(
    val title: String,
    val subtitle: String,
    val action: () -> Unit
)
