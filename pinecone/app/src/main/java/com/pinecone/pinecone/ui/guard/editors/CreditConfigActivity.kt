package com.pinecone.pinecone.ui.guard.editors

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.guard.service.GuardClientHolder
import com.pinecone.pinecone.ui.guard.GuardSettingsScaffold
import com.pinecone.pinecone.ui.theme.*

class CreditConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PineconeTheme {
                GuardSettingsScaffold(title = "信用积分设置", onBack = { finish() }) {
                    CreditConfigEditor()
                }
            }
        }
    }
}

@Composable
private fun CreditConfigEditor() {
    val context = LocalContext.current
    var weeklyTotal by remember { mutableIntStateOf(GuardClientHolder.cachedRules.creditConfig.weeklyTotal) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "每日时长用尽后进入宽限期，每超出 1 分钟消耗积分。" +
                   "积分一旦耗尽，设备将立即锁屏，当天无法继续使用。" +
                   "每周一凌晨自动重置为全额积分。" +
                   "孩子主动按 Home 键点击「✅ 今天够了」可赚取积分奖励。",
            fontSize = 16.sp, color = PineTextSecondary,
            modifier = Modifier.padding(bottom = 24.dp),
            lineHeight = 22.sp
        )

        Text("每周积分总额", fontSize = 20.sp, color = Color.Gray,
            modifier = Modifier.padding(bottom = 12.dp))

        Text("$weeklyTotal 分", fontSize = 48.sp, color = PineWarning,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0, 50, 75, 100, 150, 200).forEach { v ->
                PineChipButton(
                    label = "$v",
                    selected = weeklyTotal == v,
                    onClick = {
                        weeklyTotal = v
                        val config = GuardClientHolder.cachedRules.creditConfig.copy(weeklyTotal = weeklyTotal)
                        GuardClientHolder.updateCreditConfig(config)
                    }
                )
            }
        }

        Text("当前积分: 100/100", fontSize = 20.sp, color = Color.Gray,
            modifier = Modifier.padding(top = 32.dp, bottom = 16.dp))

        PineSecondaryButton(
            label = "立即重置为 $weeklyTotal",
            onClick = {
                GuardClientHolder.resetCredits()
                Toast.makeText(context, "积分已重置", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
