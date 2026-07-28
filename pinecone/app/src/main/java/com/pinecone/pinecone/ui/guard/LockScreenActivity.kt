package com.pinecone.pinecone.ui.guard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import com.pinecone.pinecone.ui.theme.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

class LockScreenActivity : ComponentActivity() {

    private val pinLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Consume back press — prevent escape from lock screen
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* consumed */ }
        })

        com.pinecone.guard.service.GuardClientHolder.enterSettings()
        val reason = intent?.getStringExtra("lock_reason") ?: "DAILY_LIMIT_REACHED"
        val creditBalance = intent?.getIntExtra("credit_balance", 0) ?: 0
        val breakUsageMinutes = intent?.getIntExtra("break_usage_minutes", 0) ?: 0
        val breakDurationMinutes = intent?.getIntExtra("break_duration_minutes", 0) ?: 0

        setContent {
            PineconeTheme {
                LockScreen(
                    reason = reason,
                    creditBalance = creditBalance,
                    breakUsageMinutes = breakUsageMinutes,
                    breakDurationMinutes = breakDurationMinutes,
                    onUnlock = { finish() },
                    onPinUnlock = {
                        val intent = Intent(this, PinSetupActivity::class.java).apply {
                            putExtra("mode", "verify")
                        }
                        pinLauncher.launch(intent)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        com.pinecone.guard.service.GuardClientHolder.isLockFlowActive = false
        com.pinecone.guard.service.GuardClientHolder.leaveSettings()
    }
}

@Composable
private fun LockScreen(
    reason: String,
    creditBalance: Int,
    breakUsageMinutes: Int,
    breakDurationMinutes: Int,
    onUnlock: () -> Unit,
    onPinUnlock: () -> Unit
) {
    val (title, subtitle, emoji) = when (reason) {
        "DAILY_LIMIT_REACHED" -> Triple(
            "今天的屏幕时间已用完",
            "信用积分: $creditBalance\n下周一会自动重置",
            "🔒"
        )
        "BREAK_REQUIRED" -> Triple(
            "已连续观看 ${breakUsageMinutes} 分钟，让眼睛休息一下吧~",
            "",
            ""
        )
        "OUTSIDE_TIME_WINDOW" -> Triple(
            "当前不在可用时段",
            "开放时间：周一至周五 16:00-21:00\n周六日 08:00-21:00",
            "🔒"
        )
        "TIME_TAMPERED" -> Triple(
            "系统时间异常",
            "设备已锁定\n请联系家长解锁并检查时间设置",
            "🔒"
        )
        "DATA_CORRUPTED" -> Triple(
            "系统数据异常",
            "设备已锁定\n请联系家长解锁",
            "🔒"
        )
        else -> Triple(
            "设备已锁定",
            "请联系家长解锁",
            "🔒"
        )
    }

    // ── Quote ──
    val context = LocalContext.current
    var quote by remember { mutableStateOf(QuoteCache.quote) }
    var quoteFrom by remember { mutableStateOf(QuoteCache.quoteFrom) }

    LaunchedEffect(Unit) {
        QuoteCache.preFetch(context)
        quote = QuoteCache.quote
        quoteFrom = QuoteCache.quoteFrom
    }

    // ── Break countdown ──
    var breakRemaining by remember(breakDurationMinutes) {
        mutableIntStateOf(breakDurationMinutes * 60)
    }
    val isBreakLock = reason == "BREAK_REQUIRED"
    val totalBreakSecs = breakDurationMinutes * 60
    val elapsedSecs = totalBreakSecs - breakRemaining
    val minWaitSecs = 60.coerceAtMost(totalBreakSecs)
    val canSkip = !isBreakLock || elapsedSecs >= minWaitSecs

    if (isBreakLock && breakRemaining > 0) {
        LaunchedEffect(Unit) {
            while (breakRemaining > 0) {
                delay(1.seconds)
                breakRemaining--
            }
            onUnlock()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PineBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Quote at top, 12% margin ──
            Spacer(modifier = Modifier.fillMaxHeight(0.12f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PineSurface.copy(alpha = 0.92f))
                    .padding(horizontal = 24.dp, vertical = 18.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "「${quote}」",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = PineTextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 44.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (quoteFrom.isNotEmpty()) {
                        Text(
                            text = "—— $quoteFrom",
                            fontSize = 16.sp,
                            color = PineTextSecondary,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 10.dp)
                        )
                    }
                }
            }

            // ── Center content — below quote, shifted slightly up ──
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(0.30f))

                if (emoji.isNotEmpty()) {
                    Text(emoji, fontSize = 80.sp)
                }

                if (isBreakLock) {
                    Text(
                        text = title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Normal,
                        color = PineTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 24.dp, bottom = 20.dp)
                    )
                } else {
                    Text(
                        text = title, fontSize = 36.sp,
                        fontWeight = FontWeight.Bold, color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
                    )
                    Text(
                        text = subtitle, fontSize = 24.sp,
                        color = PineTextSecondary, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }

                // Break countdown
                if (isBreakLock && breakRemaining > 0) {
                    val mins = breakRemaining / 60
                    val secs = breakRemaining % 60
                    Text(
                        text = "剩余 ${"%02d:%02d".format(mins, secs)}",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = PinePrimary,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )
                }

                // Skip / Unlock button
                val waitRemaining = (minWaitSecs - elapsedSecs).coerceAtLeast(0)
                Button(
                    onClick = { onUnlock() },
                    enabled = canSkip,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PinePrimary,
                        contentColor = PineTextOnAccent,
                        disabledContainerColor = PinePrimary.copy(alpha = 0.35f),
                        disabledContentColor = PineTextOnAccent.copy(alpha = 0.45f)
                    )
                ) {
                    Text(
                        text = when {
                            isBreakLock && !canSkip -> "再等 ${"%d".format(waitRemaining)}s..."
                            isBreakLock -> "跳过等待"
                            else -> "我休息好了"
                        },
                        fontSize = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onPinUnlock) {
                    Text("家长解锁 (PIN)", fontSize = 16.sp, color = PineTextSecondary)
                }

                Spacer(Modifier.weight(0.70f))
            }
        }
    }
}
