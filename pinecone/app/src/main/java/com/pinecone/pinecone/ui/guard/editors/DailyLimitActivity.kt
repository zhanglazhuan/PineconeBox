package com.pinecone.pinecone.ui.guard.editors

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.guard.service.GuardClientHolder
import com.pinecone.pinecone.ui.guard.GuardSettingsScaffold
import com.pinecone.pinecone.ui.theme.PineconeTheme

class DailyLimitActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PineconeTheme {
                GuardSettingsScaffold(title = "每日总时长", onBack = { finish() }) {
                    DailyLimitEditor()
                }
            }
        }
    }
}

@Composable
private fun DailyLimitEditor() {
    var selectedMinutes by remember { mutableStateOf(GuardClientHolder.cachedRules.dailyTotalLimit) }

    fun formatDuration(minutes: Int?): String =
        if (minutes == null) "不限" else "${minutes / 60} 小时 ${minutes % 60} 分"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(formatDuration(selectedMinutes), fontSize = 48.sp,
            color = Color(0xFFFFFF00), textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 48.dp))

        // Hour / Minute adjusters
        Row(horizontalArrangement = Arrangement.Center) {
            listOf("小时" to 60, "分钟" to 15).forEach { (label, step) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Button(onClick = {
                        selectedMinutes = ((selectedMinutes ?: 0) + step).coerceAtMost(480)
                        GuardClientHolder.updateDailyLimit(selectedMinutes)
                    }) { Text("▲", fontSize = 22.sp) }
                    Text(label, fontSize = 20.sp, color = Color.White,
                        modifier = Modifier.padding(vertical = 8.dp))
                    Button(onClick = {
                        selectedMinutes = ((selectedMinutes ?: step) - step).coerceAtLeast(0)
                        GuardClientHolder.updateDailyLimit(selectedMinutes)
                    }) { Text("▼", fontSize = 22.sp) }
                }
            }
        }

        Text("快速选择:", fontSize = 20.sp, color = Color.Gray,
            modifier = Modifier.padding(top = 48.dp, bottom = 16.dp))

        Row(horizontalArrangement = Arrangement.Center) {
            listOf(20 to "20 分钟", 45 to "45 分钟", 120 to "2 小时", 0 to "不限").forEach { (min, label) ->
                Button(
                    onClick = {
                        selectedMinutes = min.takeIf { it > 0 }
                        GuardClientHolder.updateDailyLimit(selectedMinutes)
                    },
                    modifier = Modifier.padding(4.dp)
                ) { Text(label, fontSize = 18.sp) }
            }
        }
    }
}
