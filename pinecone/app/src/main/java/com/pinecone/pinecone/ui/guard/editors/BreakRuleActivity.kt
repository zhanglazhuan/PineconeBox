package com.pinecone.pinecone.ui.guard.editors

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
                GuardSettingsScaffold(title = "强制休息间隔", onBack = { finish() }) {
                    BreakRuleEditor()
                }
            }
        }
    }
}

@Composable
private fun BreakRuleEditor() {
    var usageMin by remember { mutableIntStateOf(GuardClientHolder.cachedRules.breakRule?.usageMinutes ?: 40) }
    var breakMin by remember { mutableIntStateOf(GuardClientHolder.cachedRules.breakRule?.breakMinutes ?: 10) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (usageMin > 0) "连续使用 $usageMin 分钟 → 休息 $breakMin 分钟" else "不限制",
            fontSize = 24.sp, color = PineWarning, textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            listOf(
                Triple(30, 10, "30 分 / 10 分"),
                Triple(40, 10, "40 分 / 10 分"),
                Triple(45, 15, "45 分 / 15 分"),
                Triple(0, 0, "不限制")
            ).forEach { (u, b, label) ->
                PineChipButton(
                    label = label,
                    selected = usageMin == u && breakMin == b,
                    onClick = {
                        usageMin = u; breakMin = b
                        GuardClientHolder.updateBreakRule(usageMin, breakMin)
                    },
                    modifier = Modifier.fillMaxWidth(0.6f)
                )
            }
        }
    }
}
