package com.pinecone.pinecone.ui.guard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.pinecone.ui.theme.PineconeTheme
import kotlinx.coroutines.delay

class BreakReminderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val breakSeconds = intent?.getIntExtra("break_seconds", 600) ?: 600
        setContent {
            PineconeTheme {
                BreakReminderScreen(breakSeconds = breakSeconds, onFinish = { finish() })
            }
        }
    }
}

@Composable
private fun BreakReminderScreen(breakSeconds: Int, onFinish: () -> Unit) {
    var remaining by remember { mutableIntStateOf(breakSeconds) }

    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000L)
            remaining--
        }
        onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6005C4B)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🌿", fontSize = 80.sp)
            Text("该休息了", fontSize = 36.sp, fontWeight = FontWeight.Bold,
                color = Color.White, modifier = Modifier.padding(top = 32.dp, bottom = 8.dp))
            Text("起来走走，看看远方\n保护眼睛，从小做起", fontSize = 24.sp,
                color = Color(0xFFCCCCCC), textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp))
            Text(
                text = "${remaining / 60}:%02d".format(remaining % 60),
                fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Color.White
            )
        }
    }
}
