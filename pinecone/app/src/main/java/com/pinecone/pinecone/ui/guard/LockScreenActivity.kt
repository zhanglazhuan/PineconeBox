package com.pinecone.pinecone.ui.guard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.pinecone.ui.theme.PineBackground
import com.pinecone.pinecone.ui.theme.PineconeTheme

class LockScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reason = intent?.getStringExtra("lock_reason") ?: "DAILY_LIMIT_REACHED"
        val creditBalance = intent?.getIntExtra("credit_balance", 0) ?: 0

        setContent {
            PineconeTheme {
                LockScreen(reason = reason, creditBalance = creditBalance, onUnlock = { finish() })
            }
        }
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {
        // Consume back press — prevent escape
    }
}

@Composable
private fun LockScreen(reason: String, creditBalance: Int, onUnlock: () -> Unit) {
    val (title, subtitle) = when (reason) {
        "DAILY_LIMIT_REACHED" -> "今天的屏幕时间已用完" to "信用积分: $creditBalance\n下周一会自动重置"
        "OUTSIDE_TIME_WINDOW" -> "⏰ 当前不在可用时段" to "开放时间：周一至周五 16:00-21:00\n周六日 08:00-21:00"
        "TIME_TAMPERED" -> "⚠️ 系统时间异常" to "设备已锁定\n请联系家长解锁并检查时间设置"
        "DATA_CORRUPTED" -> "⚠️ 系统数据异常" to "设备已锁定\n请联系家长解锁"
        else -> "设备已锁定" to "请联系家长解锁"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PineBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔒", fontSize = 80.sp)
            Text(
                text = title, fontSize = 36.sp,
                fontWeight = FontWeight.Bold, color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
            )
            Text(
                text = subtitle, fontSize = 24.sp,
                color = Color(0xFFAAAAAA), textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 48.dp)
            )
            Button(onClick = {
                val intent = Intent(onUnlock as? android.content.Context ?: return@Button,
                    PinSetupActivity::class.java).apply { putExtra("mode", "verify") }
                (onUnlock as? android.content.Context)?.let {
                    (it as? android.app.Activity)?.startActivityForResult(intent, 1001)
                }
            }) {
                Text("🔑 家长解锁", fontSize = 20.sp)
            }
        }
    }
}
