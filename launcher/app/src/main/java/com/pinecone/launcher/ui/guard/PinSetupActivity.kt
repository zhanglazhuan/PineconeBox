package com.pinecone.launcher.ui.guard

import android.os.Bundle
import android.view.Gravity
import android.widget.*

class PinSetupActivity : BaseSettingsActivity() {

    private var pin = StringBuilder()
    private var confirmPin: String? = null
    private var isConfirmMode = false
    private var isVerifyMode = false
    private lateinit var titleText: TextView
    private lateinit var dotsText: TextView
    private var onPinSet: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isVerifyMode = intent?.getStringExtra("mode") == "verify"

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        }

        titleText = TextView(this).apply {
            text = if (isVerifyMode) "请输入家长 PIN" else "设置家长 PIN"
            textSize = 28f; gravity = Gravity.CENTER; setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 48)
        }
        layout.addView(titleText)

        dotsText = TextView(this).apply {
            text = "[ _ ][ _ ][ _ ][ _ ][ _ ][ _ ]"
            textSize = 32f; textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setTextColor(0xFFFFFFFF.toInt()); setPadding(0, 0, 0, 24)
        }
        layout.addView(dotsText)

        val errorText = TextView(this).apply {
            textSize = 18f; gravity = Gravity.CENTER
            setTextColor(0xFFFF4444.toInt()); visibility = TextView.GONE
        }
        layout.addView(errorText)

        listOf(listOf("1","2","3"), listOf("4","5","6"),
            listOf("7","8","9"), listOf("清空","0","⌫")
        ).forEach { row ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            }
            row.forEach { label ->
                rowLayout.addView(Button(this).apply {
                    text = label; textSize = 22f; minWidth = 160; minHeight = 100
                    setOnClickListener { onKeyPress(label) }
                })
            }
            layout.addView(rowLayout)
        }

        setSettingsContent(if (isVerifyMode) "验证 PIN" else "设置 PIN", layout)
    }

    private fun onKeyPress(key: String) {
        when (key) {
            "清空" -> { pin.clear(); updateDots() }
            "⌫" -> { if (pin.isNotEmpty()) { pin.deleteCharAt(pin.length - 1); updateDots() } }
            else -> {
                if (pin.length < 6) {
                    pin.append(key); updateDots()
                    if (pin.length == 6) {
                        if (isVerifyMode) {
                            val intent = intent
                            intent.putExtra("pin", pin.toString())
                            setResult(RESULT_OK, intent)
                            finish()
                        } else if (isConfirmMode) {
                            if (pin.toString() == confirmPin) {
                                onPinSet?.invoke(pin.toString())
                                Toast.makeText(this, "PIN 设置成功", Toast.LENGTH_SHORT).show()
                                finish()
                            } else {
                                Toast.makeText(this, "两次输入不一致，请重试", Toast.LENGTH_LONG).show()
                                pin.clear(); confirmPin = null; isConfirmMode = false
                                titleText.text = "设置家长 PIN"
                            }
                        } else {
                            confirmPin = pin.toString()
                            pin.clear(); isConfirmMode = true
                            titleText.text = "请再次输入 PIN"
                        }
                        updateDots()
                    }
                }
            }
        }
    }

    private fun updateDots() {
        val sb = StringBuilder()
        for (i in 0 until 6) sb.append(if (i < pin.length) "[ ● ]" else "[ _ ]")
        dotsText.text = sb.toString()
    }
}
