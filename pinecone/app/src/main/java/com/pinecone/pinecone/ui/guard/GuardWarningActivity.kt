package com.pinecone.pinecone.ui.guard

import android.os.Bundle
import android.widget.Toast
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
import com.pinecone.pinecone.ui.theme.*
import kotlinx.coroutines.delay

class GuardWarningActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val remaining = intent?.getLongExtra("remaining", 0) ?: 0
        val credits = intent?.getIntExtra("credits", 0) ?: 0
        val costPerMin = intent?.getIntExtra("cost_per_min", 5) ?: 5

        setContent {
            PineconeTheme(darkTheme = true, dynamicColor = false) {
                ExtensionDialog(
                    remainingSeconds = remaining,
                    creditBalance = credits,
                    costPerMin = costPerMin,
                    onExtend = { minutes ->
                        val currentLimit = GuardClientHolder.cachedRules.dailyTotalLimit ?: return@ExtensionDialog
                        GuardClientHolder.updateDailyLimit(currentLimit + minutes)
                        Toast.makeText(this, "延长 $minutes 分钟", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}

@Composable
fun ExtensionDialog(
    remainingSeconds: Long,
    creditBalance: Int,
    costPerMin: Int,
    onExtend: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var extensionMinutes by remember { mutableIntStateOf(10) }
    var showExtension by remember { mutableStateOf(false) }
    val hasCredits = creditBalance > 0
    val maxMinutes = if (hasCredits) creditBalance / costPerMin else 0
    val canAfford = hasCredits && extensionMinutes in 1..maxMinutes
    val step = 5
    val limitSecs = (GuardClientHolder.cachedRules.dailyTotalLimit ?: 6) * 60L
    val usedMin = ((limitSecs - remainingSeconds) / 60).coerceAtLeast(0)

    if (!hasCredits) {
        // ── 1-minute: top-right floating bar, no backdrop, 75% transparent ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.End)
                .padding(top = 48.dp, end = 32.dp)
                .width(320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PineSurface.copy(alpha = 0.75f))
                .clickable { onDismiss() }
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("⏰ 今天屏幕时长还剩 ${remainingSeconds / 60} 分钟", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = PineWarning)
            Spacer(modifier = Modifier.height(4.dp))
            Text("你已使用 $usedMin 分钟", fontSize = 13.sp, color = PineTextSecondary)
        }
        LaunchedEffect(Unit) {
            delay(30_000L)
            onDismiss()
        }
    } else {
        // ── 5-minute warning: centered dialog with extension ──
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.width(420.dp).clip(RoundedCornerShape(16.dp)).background(PineElevated).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("⏰", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("今天屏幕时长还剩 ${remainingSeconds / 60} 分钟", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("你已使用 $usedMin 分钟 · 剩余积分 $creditBalance 分", fontSize = 15.sp, color = PineTextSecondary, modifier = Modifier.padding(top = 4.dp))
                Spacer(modifier = Modifier.height(24.dp))

                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(PineSurface).clickable { showExtension = !showExtension }.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (showExtension) "▾" else "▸", fontSize = 14.sp, color = PineTextMuted)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("延长时长", fontSize = 15.sp, color = PineTextSecondary)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("${extensionMinutes} 分钟", fontSize = 15.sp, color = Color.White)
                    }
                }

                if (showExtension) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(PineGlass).clickable { extensionMinutes = (extensionMinutes - step).coerceAtLeast(1) }, contentAlignment = Alignment.Center) {
                            Text("−", fontSize = 22.sp, color = PineTextSecondary) }
                        Spacer(modifier = Modifier.width(24.dp))
                        Text("${extensionMinutes}", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = PineWarning, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Spacer(modifier = Modifier.width(24.dp))
                        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(PineGlass).clickable { extensionMinutes = (extensionMinutes + step).coerceAtMost(maxOf(1, maxMinutes)) }, contentAlignment = Alignment.Center) {
                            Text("+", fontSize = 22.sp, color = PineTextSecondary) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("分钟", fontSize = 16.sp, color = PineTextSecondary)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(PineGlass).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
                        Text("知道了", fontSize = 18.sp, color = PineTextSecondary) }
                    if (showExtension) {
                        Box(modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(if (canAfford) PinePrimary else PinePrimaryDim.copy(alpha = 0.3f)).then(if (canAfford) Modifier.clickable {
                            onExtend(extensionMinutes)
                        } else Modifier), contentAlignment = Alignment.Center) {
                            Text("延长", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = if (canAfford) PineTextOnAccent else PineTextMuted)
                        }
                    }
                }
            }
        }
    }
}
