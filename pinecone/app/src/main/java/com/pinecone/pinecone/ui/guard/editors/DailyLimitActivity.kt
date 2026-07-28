package com.pinecone.pinecone.ui.guard.editors

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.guard.service.GuardClientHolder
import com.pinecone.pinecone.ui.guard.GuardSettingsScaffold
import com.pinecone.pinecone.ui.theme.*

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

    val hours = (selectedMinutes ?: 0) / 60
    val minutes = (selectedMinutes ?: 0) % 60
    val isUnlimited = selectedMinutes == null

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hour / Minute steppers — layout: ▲ / value / ▼ / unit
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(top = 32.dp)
        ) {
            // ── Hours ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                WideStepperButton("▲") {
                    val base = (selectedMinutes ?: 0)
                    selectedMinutes = (base + 60).coerceAtMost(480)
                    GuardClientHolder.updateDailyLimit(selectedMinutes)
                }
                Text(
                    text = if (isUnlimited) "—" else "$hours",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                WideStepperButton("▼") {
                    if (isUnlimited) {
                        selectedMinutes = 480
                    } else {
                        val next = (selectedMinutes!! - 60).coerceAtLeast(0)
                        selectedMinutes = next.takeIf { it > 0 }
                    }
                    GuardClientHolder.updateDailyLimit(selectedMinutes)
                }
                Text(
                    text = "小时",
                    fontSize = 16.sp,
                    color = PineTextSecondary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // ── Minutes ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                WideStepperButton("▲") {
                    val base = (selectedMinutes ?: 0)
                    selectedMinutes = (base + 15).coerceAtMost(480)
                    GuardClientHolder.updateDailyLimit(selectedMinutes)
                }
                Text(
                    text = if (isUnlimited) "—" else "${minutes}",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                WideStepperButton("▼") {
                    if (isUnlimited) {
                        selectedMinutes = 480
                    } else {
                        val next = (selectedMinutes!! - 15).coerceAtLeast(0)
                        selectedMinutes = next.takeIf { it > 0 }
                    }
                    GuardClientHolder.updateDailyLimit(selectedMinutes)
                }
                Text(
                    text = "分钟",
                    fontSize = 16.sp,
                    color = PineTextSecondary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        Text("快速选择:", fontSize = 20.sp, color = Color.Gray,
            modifier = Modifier.padding(top = 48.dp, bottom = 16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(20 to "20 分钟", 45 to "45 分钟", 120 to "2 小时", 0 to "不限").forEach { (min, label) ->
                PineChipButton(
                    label = label,
                    selected = selectedMinutes == (min.takeIf { it > 0 }),
                    onClick = {
                        selectedMinutes = min.takeIf { it > 0 }
                        GuardClientHolder.updateDailyLimit(selectedMinutes)
                    }
                )
            }
        }
    }
}

// Wider stepper button for daily-limit editor (80dp vs 36dp default)
@Composable
private fun WideStepperButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(80.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(PineGlass)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = PineTextSecondary
        )
    }
}
