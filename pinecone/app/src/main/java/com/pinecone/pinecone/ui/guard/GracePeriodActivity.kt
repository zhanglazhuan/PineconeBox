package com.pinecone.pinecone.ui.guard

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
import com.pinecone.pinecone.ui.theme.*
import kotlinx.coroutines.delay

class GracePeriodActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val creditBalance = intent?.getIntExtra("credit_balance", 100) ?: 100
        setContent {
            PineconeTheme {
                GracePeriodScreen(creditBalance = creditBalance, onFinish = { finish() })
            }
        }
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() { /* consume */ }
}

@Composable
private fun GracePeriodScreen(creditBalance: Int, onFinish: () -> Unit) {
    val totalSeconds = 300 // 5 minutes
    var remaining by remember { mutableIntStateOf(totalSeconds) }
    var isGraceActive by remember { mutableStateOf(false) }

    LaunchedEffect(isGraceActive) {
        if (isGraceActive) {
            while (remaining > 0) {
                delay(1000L)
                remaining--
            }
            onFinish()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PineBackground.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📢", fontSize = 80.sp)
            Text("今天的时间到了", fontSize = 36.sp, fontWeight = FontWeight.Bold,
                color = Color.White, modifier = Modifier.padding(vertical = 16.dp))
            Text(
                text = "信用积分: $creditBalance\n可继续使用 5 分钟\n（将扣除 ${5 * 5} 积分）",
                fontSize = 24.sp, color = PineTextSecondary,
                textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 32.dp)
            )

            LinearProgressIndicator(
                progress = { remaining / totalSeconds.toFloat() },
                modifier = Modifier.fillMaxWidth(0.6f).height(24.dp)
            )

            val mins = remaining / 60; val secs = remaining % 60
            Text(
                text = "$mins:%02d".format(secs), fontSize = 20.sp,
                color = if (remaining <= 60) Color.Red else Color.White
            )

            Row(modifier = Modifier.padding(top = 48.dp)) {
                Button(onClick = onFinish, modifier = Modifier.padding(8.dp)) {
                    Text("立即休息", fontSize = 20.sp)
                }
                Button(
                    onClick = { isGraceActive = true },
                    enabled = !isGraceActive,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(if (isGraceActive) "宽限中..." else "继续使用", fontSize = 20.sp)
                }
            }
        }
    }
}
