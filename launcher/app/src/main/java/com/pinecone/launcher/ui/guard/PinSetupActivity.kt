package com.pinecone.launcher.ui.guard

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import com.pinecone.guard.service.GuardClientHolder

class PinSetupActivity : BaseSettingsActivity() {

    private var pin1 = StringBuilder()
    private var pin2 = StringBuilder()
    private var activeField = 0  // 0 = input, 1 = confirm
    private var keyboardMode = 0  // 0=ABC, 1=123, 2=#$!

    private lateinit var display1: TextView
    private lateinit var display2: TextView
    private lateinit var field1Label: TextView
    private lateinit var field2Label: TextView
    private lateinit var keyboardContainer: LinearLayout

    private val isVerifyMode by lazy { intent?.getStringExtra("mode") == "verify" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // ── PIN input fields ──

        // Field 1: input PIN
        field1Label = TextView(this).apply {
            text = "输入 PIN"; textSize = 18f; setTextColor(Color.GRAY)
            setPadding(0, 8, 0, 4)
        }
        root.addView(field1Label)
        display1 = TextView(this).apply {
            text = dots(pin1); textSize = 28f; setTextColor(Color.YELLOW)
            setPadding(0, 0, 0, 4)
            setTypeface(Typeface.MONOSPACE)
            setOnClickListener { activeField = 0; refreshFocus() }
        }
        root.addView(display1)

        // Field 2: confirm PIN (hidden in verify mode)
        field2Label = TextView(this).apply {
            text = "确认 PIN"; textSize = 18f; setTextColor(Color.GRAY)
            setPadding(0, 16, 0, 4)
            visibility = if (isVerifyMode) View.GONE else View.VISIBLE
        }
        root.addView(field2Label)
        display2 = TextView(this).apply {
            text = dots(pin2); textSize = 28f; setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 0, 4)
            setTypeface(Typeface.MONOSPACE)
            visibility = if (isVerifyMode) View.GONE else View.VISIBLE
            setOnClickListener { if (!isVerifyMode) { activeField = 1; refreshFocus() } }
        }
        root.addView(display2)

        // error message
        val errorText = TextView(this).apply {
            textSize = 16f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#FF6666")); visibility = View.GONE
            setPadding(0, 8, 0, 4)
        }
        root.addView(errorText)

        // spacer
        root.addView(Space(this).apply { minimumHeight = 12 })

        // ── keyboard ──

        keyboardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(keyboardContainer)

        buildKeyboard()

        setSettingsContent(
            if (isVerifyMode) "验证 PIN" else "设置 PIN", root
        )
        refreshFocus()
    }

    // ═══════════════════════════════════════════
    //  Keyboard
    // ═══════════════════════════════════════════

    private fun buildKeyboard() {
        keyboardContainer.removeAllViews()

        val keys: List<List<String>> = when (keyboardMode) {
            0 -> listOf( // ABC
                listOf("A","B","C","D","E","F","G"),
                listOf("H","I","J","K","L","M","N"),
                listOf("O","P","Q","R","S","T","U"),
                listOf("V","W","X","Y","Z")
            )
            1 -> listOf( // 123
                listOf("1","2","3","4","5"),
                listOf("6","7","8","9","0")
            )
            else -> listOf( // #$!
                listOf("!","@","#","$","%","^"),
                listOf("&","*","(",")","-","_"),
                listOf("+","=","/","?",".",",")
            )
        }

        keys.forEach { row ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            }
            row.forEach { key ->
                rowLayout.addView(keyButton(key))
            }
            keyboardContainer.addView(rowLayout)
        }

        // mode switch row
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }
        listOf("ABC", "123", "#$!").forEachIndexed { idx, label ->
            modeRow.addView(Button(this).apply {
                text = label; textSize = 15f; minWidth = 100; minHeight = 48
                setPadding(8, 0, 8, 0)
                if (idx == keyboardMode) {
                    setTextColor(Color.YELLOW)
                    setBackgroundColor(Color.parseColor("#333355"))
                }
                setOnClickListener {
                    keyboardMode = idx; buildKeyboard()
                }
            })
        }
        // spacer
        modeRow.addView(Space(this).apply { minimumWidth = 32 })
        // space key
        modeRow.addView(Button(this).apply {
            text = "空格"; textSize = 15f; minWidth = 80; minHeight = 48
            setOnClickListener { onKeyPress(" ") }
        })
        // backspace
        modeRow.addView(Button(this).apply {
            text = "⌫"; textSize = 18f; minWidth = 80; minHeight = 48
            setOnClickListener { onBackspace() }
        })
        // clear
        modeRow.addView(Button(this).apply {
            text = "清空"; textSize = 15f; minWidth = 80; minHeight = 48
            setTextColor(Color.parseColor("#FF6666"))
            setOnClickListener { onClear() }
        })

        keyboardContainer.addView(modeRow)
    }

    private fun keyButton(label: String): Button = Button(this).apply {
        text = label; textSize = 20f; minWidth = 110; minHeight = 64
        setPadding(4, 0, 4, 0)
        setOnClickListener { onKeyPress(label) }
    }

    // ═══════════════════════════════════════════
    //  Input handling
    // ═══════════════════════════════════════════

    private fun onKeyPress(key: String) {
        val sb = if (activeField == 0) pin1 else pin2
        if (sb.length < 20) {
            sb.append(key)
            refreshDisplays()
            autoCheck()
        }
    }

    private fun onBackspace() {
        val sb = if (activeField == 0) pin1 else pin2
        if (sb.isNotEmpty()) {
            sb.deleteCharAt(sb.length - 1)
            refreshDisplays()
        }
    }

    private fun onClear() {
        val sb = if (activeField == 0) pin1 else pin2
        sb.clear()
        refreshDisplays()
    }

    private fun autoCheck() {
        if (isVerifyMode) {
            // verify: just need the PIN, caller handles comparison
            return
        }
        if (pin1.length >= 4 && pin1.toString() == pin2.toString()) {
            // both match and at least 4 chars → auto-save
            GuardClientHolder.client?.let {
                // Store PIN via guard service
                GuardClientHolder.updatePin(pin1.toString())
            }
            Toast.makeText(this, "PIN 设置成功", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // ═══════════════════════════════════════════
    //  Display
    // ═══════════════════════════════════════════

    private fun dots(sb: StringBuilder): String {
        if (sb.isEmpty()) return "···"
        return "●".repeat(sb.length)
    }

    private fun refreshDisplays() {
        display1.text = dots(pin1)
        display2.text = dots(pin2)

        // If in verify mode and pin reaches 4+ chars, auto-submit
        if (isVerifyMode && pin1.length >= 4) {
            val intent = intent
            intent.putExtra("pin", pin1.toString())
            setResult(RESULT_OK, intent)
            finish()
        }
    }

    private fun refreshFocus() {
        if (activeField == 0) {
            field1Label.setTextColor(Color.WHITE)
            display1.setTextColor(Color.YELLOW)
            field2Label.setTextColor(Color.GRAY)
            display2.setTextColor(Color.parseColor("#888888"))
        } else {
            field1Label.setTextColor(Color.GRAY)
            display1.setTextColor(Color.parseColor("#888888"))
            field2Label.setTextColor(Color.WHITE)
            display2.setTextColor(Color.YELLOW)
        }
    }
}
