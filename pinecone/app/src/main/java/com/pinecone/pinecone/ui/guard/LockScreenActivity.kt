package com.pinecone.pinecone.ui.guard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import kotlinx.coroutines.delay

class LockScreenActivity : ComponentActivity() {

    private val pinLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        com.pinecone.guard.service.GuardClientHolder.leaveSettings()
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {
        // Consume back press — prevent escape
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
            "${breakUsageMinutes}分钟到了，休息一下吧",
            "连续使用 $breakUsageMinutes 分钟\n需要休息 ${breakDurationMinutes} 分钟才能继续",
            "⏳"
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

    // Break countdown timer
    var breakRemaining by remember(breakDurationMinutes) {
        mutableIntStateOf(breakDurationMinutes * 60)
    }
    val isBreakLock = reason == "BREAK_REQUIRED"

    if (isBreakLock && breakRemaining > 0) {
        LaunchedEffect(Unit) {
            while (breakRemaining > 0) {
                delay(1000L)
                breakRemaining--
            }
            // Auto-dismiss when break time ends
            onUnlock()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PineBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 80.sp)
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

            // Break countdown display
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

            Button(
                onClick = { onUnlock() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PinePrimary,
                    contentColor = PineTextOnAccent
                )
            ) {
                Text(if (isBreakLock) "跳过等待" else "我休息好了", fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onPinUnlock) {
                Text("家长解锁 (PIN)", fontSize = 16.sp, color = PineTextSecondary)
            }
        }
    }
}
