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

class BreakRuleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PineconeTheme {
                GuardSettingsScaffold(title = "息屏间隔", onBack = { finish() }) {
                    BreakRuleEditor()
                }
            }
        }
    }
}

@Composable
private fun BreakRuleEditor() {
    var usageMin by remember { mutableIntStateOf(GuardClientHolder.cachedRules.breakRule?.usageMinutes ?: 20) }
    var breakMin by remember { mutableIntStateOf(GuardClientHolder.cachedRules.breakRule?.breakMinutes ?: 2) }

    val isDisabled = usageMin == 0 && breakMin == 0

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Two stepper columns ──
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(top = 32.dp)
        ) {
            // ── Usage minutes ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Text("每观看", fontSize = 16.sp, color = PineTextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp))
                StepperButton("+") {
                    usageMin = (usageMin + 5).coerceAtMost(120)
                    GuardClientHolder.updateBreakRule(usageMin, breakMin)
                }
                Text(
                    text = if (isDisabled) "—" else "$usageMin",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                StepperButton("−") {
                    usageMin = (usageMin - 5).coerceAtLeast(0)
                    GuardClientHolder.updateBreakRule(usageMin, breakMin)
                }
                Text("分钟", fontSize = 16.sp, color = PineTextSecondary,
                    modifier = Modifier.padding(top = 6.dp))
            }

            // ── Break minutes ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Text("息屏", fontSize = 16.sp, color = PineTextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp))
                StepperButton("+") {
                    breakMin = (breakMin + 1).coerceAtMost(60)
                    GuardClientHolder.updateBreakRule(usageMin, breakMin)
                }
                Text(
                    text = if (isDisabled) "—" else "$breakMin",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                StepperButton("−") {
                    breakMin = (breakMin - 1).coerceAtLeast(0)
                    GuardClientHolder.updateBreakRule(usageMin, breakMin)
                }
                Text("分钟", fontSize = 16.sp, color = PineTextSecondary,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // ── Quick presets ──
        Text("快速设置:", fontSize = 20.sp, color = Color.Gray,
            modifier = Modifier.padding(bottom = 12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                Triple(20, 2, "20分 / 2分"),
                Triple(30, 4, "30分 / 4分"),
                Triple(40, 5, "40分 / 5分"),
                Triple(45, 10, "45分 / 10分"),
                Triple(0, 0, "不限制")
            ).forEach { (u, b, label) ->
                PineChipButton(
                    label = label,
                    selected = usageMin == u && breakMin == b,
                    modifier = Modifier.width(120.dp),
                    onClick = {
                        usageMin = u; breakMin = b
                        GuardClientHolder.updateBreakRule(usageMin, breakMin)
                    }
                )
            }
        }
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
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
