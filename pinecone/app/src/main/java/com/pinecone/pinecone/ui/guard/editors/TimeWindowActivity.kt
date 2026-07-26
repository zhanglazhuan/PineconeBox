package com.pinecone.pinecone.ui.guard.editors

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import com.pinecone.pinecone.ui.theme.*

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

// Mode presets for quick selection
private data class TimePreset(
    val label: String,
    val wdStartH: Int, val wdStartM: Int, val wdEndH: Int, val wdEndM: Int,
    val weStartH: Int, val weStartM: Int, val weEndH: Int, val weEndM: Int
)

private val timePresets = listOf(
    TimePreset("上学", 16, 0, 21, 0, 8, 0, 21, 0),
    TimePreset("假期",  8, 0, 21, 0, 8, 0, 21, 0),
    TimePreset("严格", 18, 0, 20, 0, 10, 0, 17, 0)
)

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

    fun matches(p: TimePreset): Boolean =
        wdStartH == p.wdStartH && wdStartM == p.wdStartM &&
        wdEndH == p.wdEndH && wdEndM == p.wdEndM &&
        weStartH == p.weStartH && weStartM == p.weStartM &&
        weEndH == p.weEndH && weEndM == p.weEndM

    Column(modifier = Modifier.fillMaxWidth()) {
        // Quick modes
        Text("快速模式:", fontSize = 18.sp, color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            timePresets.forEach { preset ->
                PineChipButton(
                    label = preset.label,
                    selected = matches(preset),
                    onClick = {
                        wdStartH = preset.wdStartH; wdStartM = preset.wdStartM
                        wdEndH = preset.wdEndH; wdEndM = preset.wdEndM
                        weStartH = preset.weStartH; weStartM = preset.weStartM
                        weEndH = preset.weEndH; weEndM = preset.weEndM
                        save()
                    }
                )
            }
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PineStepperButton("▲") {
            val total = (valueH * 60 + valueM + 30) % 1440
            onChange(total / 60, total % 60)
        }

        Text(fmt(valueH, valueM), fontSize = 22.sp,
            color = PineWarning, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(vertical = 4.dp))

        PineStepperButton("▼") {
            val total = (valueH * 60 + valueM - 30 + 1440) % 1440
            onChange(total / 60, total % 60)
        }
    }
}
