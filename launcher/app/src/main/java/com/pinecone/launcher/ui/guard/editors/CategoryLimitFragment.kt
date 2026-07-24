package com.pinecone.launcher.ui.guard.editors

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.pinecone.guard.service.GuardClientHolder
import com.pinecone.guard.data.model.CategoryLimit
import com.pinecone.launcher.ui.guard.BaseSettingsActivity

class CategoryLimitFragment : BaseSettingsActivity() {
    private val limits = mutableMapOf(
        "english" to 60, "reading" to null, "documentary" to null, "apps" to 45
    ).apply {
        GuardClientHolder.cachedRules.categoryLimits.forEach { cl ->
            this[cl.categoryId] = cl.dailyMinutes
        }
    }
    private val labels = mapOf(
        "english" to "🔤 英语学习", "reading" to "📚 绘本阅读",
        "documentary" to "🎬 纪录片", "apps" to "🧩 学习 App"
    )
    private val options = listOf(null to "不限", 15 to "15分钟", 30 to "30分钟",
        45 to "45分钟", 60 to "60分钟", 90 to "90分钟")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        limits.keys.forEach { id ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            }
            row.addView(TextView(this).apply {
                text = labels[id] ?: id; textSize = 22f; setTextColor(Color.WHITE)
                setPadding(0, 0, 24, 0)
            })
            var optIdx = options.indexOfFirst { it.first == limits[id] }
            val btn = Button(this).apply {
                text = options[optIdx].second; textSize = 18f; minWidth = 200; minHeight = 60
                setOnClickListener {
                    optIdx = (optIdx + 1) % options.size
                    limits[id] = options[optIdx].first
                    text = options[optIdx].second
                    val newLimits = limits.map { (cid, min) ->
                        CategoryLimit(cid, labels[cid] ?: cid, min)
                    }
                    GuardClientHolder.updateCategoryLimits(newLimits)
                }
            }
            row.addView(btn)
            layout.addView(row)
        }
        setSettingsContent("内容分类限制", layout)
    }
}
