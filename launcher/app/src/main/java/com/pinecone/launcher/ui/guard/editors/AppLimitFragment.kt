package com.pinecone.launcher.ui.guard.editors

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.pinecone.launcher.ui.guard.BaseSettingsActivity

class AppLimitFragment : BaseSettingsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        layout.addView(TextView(this).apply {
            text = "暂无 App 限制\n点击 [+ 添加] 添加规则"; textSize = 20f
            setTextColor(Color.GRAY); gravity = Gravity.CENTER
        })
        layout.addView(Button(this).apply {
            text = "+ 添加 App 限制"; textSize = 20f; minWidth = 400; minHeight = 72
            setOnClickListener {
                Toast.makeText(this@AppLimitFragment, "从已安装应用中选择", Toast.LENGTH_SHORT).show()
            }
        })

        setSettingsContent("App 单独限制", layout)
    }
}
