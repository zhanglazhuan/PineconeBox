package com.pinecone.pinecone.ui.guard.editors

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.pinecone.ui.guard.GuardSettingsScaffold
import com.pinecone.pinecone.ui.theme.PineconeTheme

class UsageHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PineconeTheme {
                GuardSettingsScaffold(title = "📊 使用统计", onBack = { finish() }) {
                    UsageHistoryScreen()
                }
            }
        }
    }
}

@Composable
private fun UsageHistoryScreen() {
    val weekData = listOf(
        "一" to "1h 50m", "二" to "1h 35m", "三" to "2h 05m (+15m)",
        "四" to "1h 10m", "五" to "2h 30m (满)", "六" to "1h 20m 🟢", "日" to "2h 10m (+10m)"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        weekData.forEach { (day, dur) ->
            Text(
                text = "$day  $dur",
                fontSize = 18.sp, color = Color.White,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        Text(
            text = "\n按分类: 🔤英语 5h20m | 📚绘本 3h10m | 🎬纪录 4h | 🧩App 2h30m",
            fontSize = 16.sp, color = Color.Gray, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}
