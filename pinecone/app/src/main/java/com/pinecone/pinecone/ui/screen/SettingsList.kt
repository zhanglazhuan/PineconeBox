package com.pinecone.pinecone.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.pinecone.pinecone.data.AccountStorage
import com.pinecone.pinecone.data.CategoryData
import com.pinecone.pinecone.data.CourseItem
import com.pinecone.pinecone.ui.guard.UpdateActivity
import com.pinecone.pinecone.ui.guard.account.LoginActivity
import com.pinecone.pinecone.ui.guard.account.RegisterActivity
import com.pinecone.pinecone.ui.guard.editors.*
import com.pinecone.pinecone.ui.theme.*

/**
 * iPad-style flat settings list: left sidebar L1 → right content shows rows.
 * For 防沉迷 category: requires account login before showing settings.
 */
@Composable
fun SettingsList(
    category: CategoryData,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val storage = remember { AccountStorage.getInstance(context) }
    var showPauseDialog by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }

    // Auth state for 防沉迷 category
    val isGuardCategory = category.name == "防沉迷"
    var isAuthenticated by remember(category.name) { mutableStateOf(!isGuardCategory) }

    // Register launcher
    val registerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            isAuthenticated = true
        }
    }

    // Login launcher
    val loginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            isAuthenticated = true
        }
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 24.dp),
        contentPadding = PaddingValues(bottom = 48.dp)
    ) {
        // Section header
        item(key = "header") {
            Text(
                text = category.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        if (isGuardCategory && !isAuthenticated) {
            // ── Guard category: show unlock prompt ──
            item(key = "auth_group") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PineElevated)
                ) {
                    if (!storage.isRegistered) {
                        // Not registered yet
                        AuthPromptRow(
                            icon = "📝",
                            title = "注册家长账号",
                            subtitle = "使用手机号注册，用于管理防沉迷规则",
                            onClick = {
                                registerLauncher.launch(Intent(context, RegisterActivity::class.java))
                            }
                        )
                    } else {
                        // Registered, needs login
                        AuthPromptRow(
                            icon = "🔒",
                            title = "输入密码解锁",
                            subtitle = "账号: ${storage.maskedPhone}",
                            onClick = {
                                loginLauncher.launch(Intent(context, LoginActivity::class.java))
                            }
                        )
                    }
                }
            }
        } else {
            // ── Normal: render all items ──
            item(key = "group") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PineElevated)
                ) {
                    category.items.forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = PineCardBorder,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                        SettingsRow(
                            item = item,
                            onClick = {
                                if (item.actionUrl == "action:pause_guard") {
                                    showPauseDialog = true
                                } else {
                                    handleSettingsAction(context, item, { showFeedback = true })
                                }
                            }
                        )
                    }
                }
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
                    try {
                        com.pinecone.pinecone.log.PineconeLogger.log(
                            com.pinecone.pinecone.log.GuardEvent(
                                System.currentTimeMillis(),
                                com.pinecone.pinecone.log.PineconeLogger.getSession()?.sessionId ?: "",
                                "paused", -1, -1
                            ))
                    } catch (_: Exception) {}
                    Toast.makeText(context, "今天防沉迷已暂停", Toast.LENGTH_SHORT).show()
                    showPauseDialog = false
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showPauseDialog = false }) { Text("取消") }
            }
        )
    }

    if (showFeedback) {
        FeedbackDialog(onDismiss = { showFeedback = false })
    }
}

@Composable
private fun FeedbackDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var type by remember { mutableStateOf("Bug") }
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提交反馈") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf("Bug" to "Bug", "需求" to "Feature").forEach { (label, value) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { type = value }
                                .padding(end = 24.dp)
                        ) {
                            RadioButton(
                                selected = type == value,
                                onClick = { type = value }
                            )
                            Text(label, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("请描述你遇到的问题或想要的功能…") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotBlank()) {
                    Toast.makeText(context, "感谢反馈！我们会尽快处理", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }) { Text("提交") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun AuthPromptRow(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 24.sp, modifier = Modifier.padding(end = 12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = Color.White)
            Text(subtitle, fontSize = 13.sp, color = PineTextSecondary)
        }
        Text("›", fontSize = 22.sp, color = PineTextMuted)
    }
}

@Composable
private fun SettingsRow(
    item: CourseItem,
    onClick: () -> Unit
) {
    val isActionable = item.actionUrl != "action:none"

    // Read version to trigger recomposition when guard rules change
    @Suppress("UNUSED_VARIABLE")
    val guardVersion = GuardClientHolder.rulesVersion

    // Compute dynamic subtitle for guard settings items
    fun guardSubtitle(actionUrl: String): String {
        val rules = GuardClientHolder.cachedRules
        return when (actionUrl) {
            "action:daily_limit" -> {
                val m = rules.dailyTotalLimit
                if (m == null) "不限" else "${m / 60} 小时 ${m % 60} 分"
            }
            "action:break_rule" -> {
                val br = rules.breakRule
                if (br != null) "每 ${br.usageMinutes} 分 休 ${br.breakMinutes} 分" else "不限制"
            }
            "action:time_window" -> {
                val wd = rules.timeWindows.find { it.daysOfWeek.contains(1) }
                if (wd != null) "${"%02d:%02d".format(wd.startHour, wd.startMinute)}-${"%02d:%02d".format(wd.endHour, wd.endMinute)}" else "未设置"
            }
            "action:credit_config" -> "${GuardClientHolder.cachedCredits.balance}分"
            else -> item.category
        }
    }

    val subtitle = guardSubtitle(item.actionUrl)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isActionable) Modifier.clickable { onClick() }
                else Modifier
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = item.title,
            fontSize = 17.sp,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 15.sp,
                    color = PineTextSecondary,
                    modifier = Modifier.padding(end = if (isActionable) 8.dp else 0.dp)
                )
            }
            if (isActionable) {
                Text("›", fontSize = 22.sp, color = PineTextMuted)
            }
        }
    }
}

private fun handleSettingsAction(context: android.content.Context, item: CourseItem, onFeedback: () -> Unit = {}) {
    when (item.actionUrl) {
        "action:system_settings" -> {
            context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        }
        "action:daily_limit" -> {
            context.startActivity(Intent(context, DailyLimitActivity::class.java))
        }
        "action:break_rule" -> {
            context.startActivity(Intent(context, BreakRuleActivity::class.java))
        }
        "action:time_window" -> {
            context.startActivity(Intent(context, TimeWindowActivity::class.java))
        }
        "action:category_limit" -> {
            context.startActivity(Intent(context, CategoryLimitActivity::class.java))
        }
        "action:app_limit" -> {
            context.startActivity(Intent(context, AppLimitActivity::class.java))
        }
        "action:browser_restrict" -> {
            context.startActivity(Intent(context, BrowserRestrictActivity::class.java))
        }
        "action:credit_config" -> {
            context.startActivity(Intent(context, CreditConfigActivity::class.java))
        }
        "action:usage_history" -> {
            context.startActivity(Intent(context, UsageHistoryActivity::class.java))
        }
        "action:pin_setup" -> {
            context.startActivity(Intent(context, com.pinecone.pinecone.ui.guard.PinSetupActivity::class.java))
        }
        "action:check_update" -> {
            context.startActivity(Intent(context, UpdateActivity::class.java))
        }
        "action:feedback" -> {
            onFeedback()
        }
        else -> {
            Toast.makeText(context, "${item.title} — 敬请期待", Toast.LENGTH_SHORT).show()
        }
    }
}
