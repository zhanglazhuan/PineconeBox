package com.pinecone.pinecone.ui.guard.editors

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.guard.data.model.TimeWindow
import com.pinecone.guard.service.GuardClientHolder
import com.pinecone.pinecone.ui.guard.GuardSettingsScaffold
import com.pinecone.pinecone.ui.theme.PineSurface
import com.pinecone.pinecone.ui.theme.PineconeTheme

class TimeWindowActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PineconeTheme {
                GuardSettingsScaffold(title = "可用时段", onBack = { finish() }) {
                    TimeWindowEditor()
                }
            }
        }
    }
}

private fun fmt(h: Int, m: Int) = "%02d:%02d".format(h, m)

@Composable
private fun TimeWindowEditor() {
    val windows = GuardClientHolder.cachedRules.timeWindows
    val wd = windows.find { it.daysOfWeek.contains(1) }
    val we = windows.find { it.daysOfWeek.contains(6) }

    var wdStartH by remember { mutableIntStateOf(wd?.startHour ?: 16) }
    var wdStartM by remember { mutableIntStateOf(wd?.startMinute ?: 0) }
    var wdEndH by remember { mutableIntStateOf(wd?.endHour ?: 21) }
    var wdEndM by remember { mutableIntStateOf(wd?.endMinute ?: 0) }
    var weStartH by remember { mutableIntStateOf(we?.startHour ?: 8) }
    var weStartM by remember { mutableIntStateOf(we?.startMinute ?: 0) }
    var weEndH by remember { mutableIntStateOf(we?.endHour ?: 21) }
    var weEndM by remember { mutableIntStateOf(we?.endMinute ?: 0) }

    fun save() {
        val windows = mutableListOf<TimeWindow>()
        if (wdStartH * 60 + wdStartM != wdEndH * 60 + wdEndM)
            windows.add(TimeWindow("周一至周五", wdStartH, wdStartM, wdEndH, wdEndM, setOf(1, 2, 3, 4, 5)))
        if (weStartH * 60 + weStartM != weEndH * 60 + weEndM)
            windows.add(TimeWindow("周六日", weStartH, weStartM, weEndH, weEndM, setOf(6, 7)))
        GuardClientHolder.updateTimeWindows(windows)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Quick modes
        Text("快速模式:", fontSize = 18.sp, color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                wdStartH = 16; wdStartM = 0; wdEndH = 21; wdEndM = 0
                weStartH = 8; weStartM = 0; weEndH = 21; weEndM = 0; save()
            }) { Text("上学") }
            Button(onClick = {
                wdStartH = 8; wdStartM = 0; wdEndH = 21; wdEndM = 0
                weStartH = 8; weStartM = 0; weEndH = 21; weEndM = 0; save()
            }) { Text("假期") }
            Button(onClick = {
                wdStartH = 18; wdStartM = 0; wdEndH = 20; wdEndM = 0
                weStartH = 10; weStartM = 0; weEndH = 17; weEndM = 0; save()
            }) { Text("严格") }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Two cards side by side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TimeCard(
                title = "周一至周五",
                startH = wdStartH, startM = wdStartM,
                endH = wdEndH, endM = wdEndM,
                onStartChange = { h, m -> wdStartH = h; wdStartM = m; save() },
                onEndChange = { h, m -> wdEndH = h; wdEndM = m; save() },
                modifier = Modifier.weight(1f)
            )
            TimeCard(
                title = "周六日",
                startH = weStartH, startM = weStartM,
                endH = weEndH, endM = weEndM,
                onStartChange = { h, m -> weStartH = h; weStartM = m; save() },
                onEndChange = { h, m -> weEndH = h; weEndM = m; save() },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TimeCard(
    title: String,
    startH: Int, startM: Int,
    endH: Int, endM: Int,
    onStartChange: (Int, Int) -> Unit,
    onEndChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PineSurface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = Color.White, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("开始", fontSize = 13.sp, color = Color.Gray)
                TimeStepper(startH, startM, onStartChange)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("结束", fontSize = 13.sp, color = Color.Gray)
                TimeStepper(endH, endM, onEndChange)
            }
        }
    }
}

@Composable
private fun TimeStepper(
    valueH: Int, valueM: Int,
    onChange: (Int, Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = {
                val total = (valueH * 60 + valueM + 30) % 1440
                onChange(total / 60, total % 60)
            },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(32.dp)
        ) { Text("▲", fontSize = 14.sp) }

        Text(fmt(valueH, valueM), fontSize = 22.sp,
            color = Color(0xFFFFFF00), fontWeight = FontWeight.Medium)

        Button(
            onClick = {
                val total = (valueH * 60 + valueM - 30 + 1440) % 1440
                onChange(total / 60, total % 60)
            },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(32.dp)
        ) { Text("▼", fontSize = 14.sp) }
    }
}
