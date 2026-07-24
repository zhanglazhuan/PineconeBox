package com.pinecone.launcher.ui.guard.editors

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.pinecone.guard.service.GuardClientHolder
import com.pinecone.launcher.ui.guard.BaseSettingsActivity

class BreakRuleFragment : BaseSettingsActivity() {
    private var usageMin = GuardClientHolder.cachedRules.breakRule?.usageMinutes ?: 40
    private var breakMin = GuardClientHolder.cachedRules.breakRule?.breakMinutes ?: 10
    private lateinit var displayText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        displayText = TextView(this).apply {
            setTextColor(Color.YELLOW); textSize = 24f; gravity = Gravity.CENTER
        }
        refreshText()
        layout.addView(displayText)

        listOf(
            Triple(30, 10, "30 分 / 10 分"),
            Triple(40, 10, "40 分 / 10 分"),
            Triple(45, 15, "45 分 / 15 分"),
            Triple(0, 0, "不限制")
        ).forEach { (u, b, label) ->
            layout.addView(Button(this).apply {
                text = label; textSize = 20f; minWidth = 400; minHeight = 72
                setOnClickListener {
                    usageMin = u; breakMin = b; refreshText()
                    GuardClientHolder.updateBreakRule(usageMin, breakMin)
                }
            })
        }
        setSettingsContent("强制休息间隔", layout)
    }

    private fun refreshText() {
        displayText.text = if (usageMin > 0)
            "连续使用 $usageMin 分钟 → 休息 $breakMin 分钟"
        else "不限制"
    }
}
