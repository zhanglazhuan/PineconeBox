package com.pinecone.launcher.ui.guard.editors

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.pinecone.guard.service.GuardClientHolder
import com.pinecone.launcher.ui.guard.BaseSettingsActivity

class DailyLimitFragment : BaseSettingsActivity() {

    private var selectedMinutes: Int? = GuardClientHolder.cachedRules.dailyTotalLimit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val currentText = TextView(this).apply {
            text = formatDuration(selectedMinutes); textSize = 48f
            setTextColor(Color.YELLOW); gravity = Gravity.CENTER; setPadding(0, 0, 0, 48)
        }
        layout.addView(currentText)

        val adjustRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        }
        listOf("小时" to 60, "分钟" to 15).forEach { (label, step) ->
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(32, 0, 32, 0)
            }
            col.addView(Button(this).apply {
                text = "▲"; textSize = 22f; minWidth = 120; minHeight = 60
                setOnClickListener {
                    selectedMinutes = ((selectedMinutes ?: 0) + step).coerceAtMost(480)
                    currentText.text = formatDuration(selectedMinutes)
                    GuardClientHolder.updateDailyLimit(selectedMinutes)
                }
            })
            col.addView(TextView(this).apply {
                text = label; textSize = 20f; setTextColor(Color.WHITE)
                gravity = Gravity.CENTER; setPadding(0, 8, 0, 8)
            })
            col.addView(Button(this).apply {
                text = "▼"; textSize = 22f; minWidth = 120; minHeight = 60
                setOnClickListener {
                    selectedMinutes = ((selectedMinutes ?: step) - step).coerceAtLeast(0)
                    currentText.text = formatDuration(selectedMinutes)
                    GuardClientHolder.updateDailyLimit(selectedMinutes)
                }
            })
            adjustRow.addView(col)
        }
        layout.addView(adjustRow)

        layout.addView(TextView(this).apply {
            text = "快速选择:"; textSize = 20f; setTextColor(Color.GRAY)
            setPadding(0, 48, 0, 16)
        })
        val quickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        }
        listOf(20 to "20 分钟", 45 to "45 分钟", 120 to "2 小时", 0 to "不限"
        ).forEach { (min, label) ->
            quickRow.addView(Button(this).apply {
                text = label; textSize = 18f; minWidth = 0; minHeight = 64
                setPadding(24, 0, 24, 0)
                setOnClickListener {
                    selectedMinutes = min.takeIf { it > 0 }
                    currentText.text = formatDuration(selectedMinutes)
                    GuardClientHolder.updateDailyLimit(selectedMinutes)
                }
            })
        }
        layout.addView(quickRow)

        setSettingsContent("每日总时长", layout)
    }

    private fun formatDuration(minutes: Int?): String =
        if (minutes == null) "不限" else "${minutes / 60} 小时 ${minutes % 60} 分"
}
