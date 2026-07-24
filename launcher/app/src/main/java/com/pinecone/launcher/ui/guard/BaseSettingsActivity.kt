package com.pinecone.launcher.ui.guard

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

abstract class BaseSettingsActivity : Activity() {

    protected fun setSettingsContent(title: String, contentView: View) {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FF1A1A2E"))
            setPadding(32, 24, 32, 32)
        }

        // Header bar: back button + title + spacer for balance
        val headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 24)
        }

        val backButton = Button(this).apply {
            text = "← 返回"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            minHeight = 48
            setPadding(16, 8, 16, 8)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { finish() }
        }
        headerBar.addView(backButton)

        val titleView = TextView(this).apply {
            text = title
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        headerBar.addView(titleView)

        // Invisible spacer to balance the back button width for title centering
        val spacer = View(this).apply {
            minimumWidth = 120
            layoutParams = LinearLayout.LayoutParams(
                120, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        headerBar.addView(spacer)

        rootLayout.addView(headerBar)
        rootLayout.addView(contentView)

        setContentView(rootLayout)
    }
}
