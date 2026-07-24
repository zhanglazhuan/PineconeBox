package com.pinecone.launcher.ui.guard.editors

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.pinecone.guard.service.GuardClientHolder
import com.pinecone.guard.data.model.TimeWindow
import com.pinecone.launcher.ui.guard.BaseSettingsActivity

class TimeWindowFragment : BaseSettingsActivity() {

    // weekday window
    private var wdStartH = 16; private var wdStartM = 0
    private var wdEndH = 21; private var wdEndM = 0
    // weekend window
    private var weStartH = 8; private var weStartM = 0
    private var weEndH = 21; private var weEndM = 0

    // display TextViews to refresh on mode change
    private val displays = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadFromRules()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // mode presets
        root.addView(TextView(this).apply {
            text = "快速模式:"; textSize = 18f; setTextColor(Color.GRAY)
            setPadding(0, 8, 0, 8)
        })
        root.addView(horizontalRow {
            listOf(
                "上学" to Runnable { applySchool(); refreshDisplays(); save() },
                "假期" to Runnable { applyHoliday(); refreshDisplays(); save() },
                "严格" to Runnable { applyStrict(); refreshDisplays(); save() }
            ).forEach { (label, action) ->
                addView(Button(this@TimeWindowFragment).apply {
                    text = label; textSize = 18f; minWidth = 0; minHeight = 56
                    setPadding(28, 0, 28, 0)
                    setOnClickListener { action.run() }
                })
            }
        })

        // weekday section
        root.addView(TextView(this).apply {
            text = "周一至周五"; textSize = 22f; setTextColor(Color.WHITE)
            setPadding(0, 24, 0, 8)
        })
        root.addView(timeRangeRow(
            getStart = { wdStartH to wdStartM }, setStart = { wdStartH = it.first; wdStartM = it.second },
            getEnd = { wdEndH to wdEndM }, setEnd = { wdEndH = it.first; wdEndM = it.second }
        ))

        // weekend section
        root.addView(TextView(this).apply {
            text = "周六日"; textSize = 22f; setTextColor(Color.WHITE)
            setPadding(0, 24, 0, 8)
        })
        root.addView(timeRangeRow(
            getStart = { weStartH to weStartM }, setStart = { weStartH = it.first; weStartM = it.second },
            getEnd = { weEndH to weEndM }, setEnd = { weEndH = it.first; weEndM = it.second }
        ))

        setSettingsContent("可用时段", root)
    }

    // ── time range row: [▼] 开始 HH:MM [▲]  ...  [▼] 结束 HH:MM [▲] ──

    private fun timeRangeRow(
        getStart: () -> Pair<Int, Int>, setStart: (Pair<Int, Int>) -> Unit,
        getEnd: () -> Pair<Int, Int>, setEnd: (Pair<Int, Int>) -> Unit
    ): LinearLayout = horizontalRow {
        addTimeAdjuster("开始", getStart, setStart)
        addView(Space(this@TimeWindowFragment).apply { minimumWidth = 48 })
        addTimeAdjuster("结束", getEnd, setEnd)
    }

    private fun LinearLayout.addTimeAdjuster(
        label: String,
        getter: () -> Pair<Int, Int>,
        setter: (Pair<Int, Int>) -> Unit
    ) {
        val display = TextView(this@TimeWindowFragment).apply {
            text = fmt(getter()); textSize = 26f; setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER; minWidth = 110
        }
        displays.add(display)

        addView(Button(this@TimeWindowFragment).apply {
            text = "▼"; textSize = 16f; minWidth = 60; minHeight = 52
            setOnClickListener { adjust(getter, setter, -30, display) }
        })
        addView(LinearLayout(this@TimeWindowFragment).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(TextView(this@TimeWindowFragment).apply {
                text = label; textSize = 13f; setTextColor(Color.GRAY); gravity = Gravity.CENTER
            })
            addView(display)
        })
        addView(Button(this@TimeWindowFragment).apply {
            text = "▲"; textSize = 16f; minWidth = 60; minHeight = 52
            setOnClickListener { adjust(getter, setter, 30, display) }
        })
    }

    private fun adjust(
        getter: () -> Pair<Int, Int>,
        setter: (Pair<Int, Int>) -> Unit,
        delta: Int,
        display: TextView
    ) {
        val (h, m) = getter()
        val total = ((h * 60 + m + delta + 1440) % 1440)
        setter(total / 60 to total % 60)
        display.text = fmt(getter())
        save()
    }

    // ── helpers ──

    private fun horizontalRow(block: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            block()
        }

    private fun fmt(h: Int, m: Int) = "%02d:%02d".format(h, m)
    private fun fmt(p: Pair<Int, Int>) = fmt(p.first, p.second)

    // ── persistence ──

    private fun save() {
        val windows = mutableListOf<TimeWindow>()
        if (wdStartH * 60 + wdStartM != wdEndH * 60 + wdEndM)
            windows.add(TimeWindow("周一至周五", wdStartH, wdStartM, wdEndH, wdEndM, setOf(1, 2, 3, 4, 5)))
        if (weStartH * 60 + weStartM != weEndH * 60 + weEndM)
            windows.add(TimeWindow("周六日", weStartH, weStartM, weEndH, weEndM, setOf(6, 7)))
        GuardClientHolder.updateTimeWindows(windows)
    }

    // ── mode presets ──

    private fun applySchool() {
        wdStartH = 16; wdStartM = 0; wdEndH = 21; wdEndM = 0
        weStartH = 8; weStartM = 0; weEndH = 21; weEndM = 0
    }

    private fun applyHoliday() {
        wdStartH = 8; wdStartM = 0; wdEndH = 21; wdEndM = 0
        weStartH = 8; weStartM = 0; weEndH = 21; weEndM = 0
    }

    private fun applyStrict() {
        wdStartH = 18; wdStartM = 0; wdEndH = 20; wdEndM = 0
        weStartH = 10; weStartM = 0; weEndH = 17; weEndM = 0
    }

    // ── load / refresh ──

    private fun loadFromRules() {
        val windows = GuardClientHolder.cachedRules.timeWindows
        val wd = windows.find { it.daysOfWeek.contains(1) }
        if (wd != null) {
            wdStartH = wd.startHour; wdStartM = wd.startMinute
            wdEndH = wd.endHour; wdEndM = wd.endMinute
        }
        val we = windows.find { it.daysOfWeek.contains(6) }
        if (we != null) {
            weStartH = we.startHour; weStartM = we.startMinute
            weEndH = we.endHour; weEndM = we.endMinute
        }
    }

    private fun refreshDisplays() {
        val values = listOf(
            fmt(wdStartH, wdStartM), fmt(wdEndH, wdEndM),
            fmt(weStartH, weStartM), fmt(weEndH, weEndM)
        )
        displays.take(4).forEachIndexed { i, tv -> tv.text = values[i] }
    }
}
