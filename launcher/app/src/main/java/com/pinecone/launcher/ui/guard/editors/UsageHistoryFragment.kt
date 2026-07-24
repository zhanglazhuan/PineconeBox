package com.pinecone.launcher.ui.guard.editors

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.pinecone.launcher.ui.guard.BaseSettingsActivity

class UsageHistoryFragment : BaseSettingsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        listOf("一" to "1h 50m", "二" to "1h 35m", "三" to "2h 05m (+15m)",
            "四" to "1h 10m", "五" to "2h 30m (满)", "六" to "1h 20m 🟢", "日" to "2h 10m (+10m)"
        ).forEach { (day, dur) ->
            layout.addView(TextView(this).apply {
                text = "$day  $dur"; textSize = 18f; setTextColor(Color.WHITE)
                setPadding(0, 4, 0, 4)
            })
        }
        layout.addView(TextView(this).apply {
            text = "\n按分类: 🔤英语 5h20m | 📚绘本 3h10m | 🎬纪录 4h | 🧩App 2h30m"
            textSize = 16f; setTextColor(Color.GRAY); gravity = Gravity.CENTER
        })

        setSettingsContent("📊 使用统计", layout)
    }
}
