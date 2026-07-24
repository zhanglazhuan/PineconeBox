package com.pinecone.launcher.ui.guard.editors

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.pinecone.guard.service.GuardClientHolder
import com.pinecone.launcher.ui.guard.BaseSettingsActivity

class CreditConfigFragment : BaseSettingsActivity() {
    private var weeklyTotal = GuardClientHolder.cachedRules.creditConfig.weeklyTotal

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 积分机制说明
        layout.addView(TextView(this).apply {
            text = "每日时长用尽后进入宽限期，每超出 1 分钟消耗积分。" +
                  "积分一旦耗尽，设备将立即锁屏，当天无法继续使用。" +
                  "每周一凌晨自动重置为全额积分。" +
                  "孩子主动按 Home 键点击「✅ 今天够了」可赚取积分奖励。"
            textSize = 16f; setTextColor(Color.parseColor("#B0B0C0"))
            setPadding(0, 0, 0, 24)
            setLineSpacing(4f, 1.2f)
        })

        layout.addView(TextView(this).apply {
            text = "每周积分总额"; textSize = 20f; setTextColor(Color.GRAY)
            setPadding(0, 0, 0, 12)
        })
        val totalText = TextView(this).apply {
            text = "$weeklyTotal 分"; textSize = 48f; setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER; setPadding(0, 0, 0, 32)
        }
        layout.addView(totalText)
        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        }
        listOf(50, 75, 100, 150, 200).forEach { v ->
            presetRow.addView(Button(this).apply {
                text = "$v"; textSize = 18f; minWidth = 100; minHeight = 60
                setOnClickListener {
                    weeklyTotal = v; totalText.text = "$v 分"
                    val config = GuardClientHolder.cachedRules.creditConfig.copy(weeklyTotal = weeklyTotal)
                    GuardClientHolder.updateCreditConfig(config)
                }
            })
        }
        layout.addView(presetRow)
        layout.addView(TextView(this).apply {
            text = "当前积分: 100/100"; textSize = 20f; setTextColor(Color.GRAY)
            setPadding(0, 32, 0, 16)
        })
        layout.addView(Button(this).apply {
            text = "立即重置为 $weeklyTotal"; textSize = 18f; minWidth = 400; minHeight = 60
            setOnClickListener {
                GuardClientHolder.resetCredits()
                Toast.makeText(this@CreditConfigFragment, "积分已重置", Toast.LENGTH_SHORT).show()
            }
        })

        setSettingsContent("信用积分设置", layout)
    }
}
