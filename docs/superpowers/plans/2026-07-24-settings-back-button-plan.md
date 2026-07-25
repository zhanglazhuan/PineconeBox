# Settings Back Button Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a standardized "← 返回" back button header to all 11 settings activities by creating a shared `BaseSettingsActivity` base class.

**Architecture:** A single abstract base class (`BaseSettingsActivity`) provides `setSettingsContent(title, contentView)` which wraps any content view with a dark-background root layout + header bar (back button + centered title). All existing settings activities extend this base and delegate header concerns to it.

**Tech Stack:** Android SDK 36, Kotlin, Leanback Theme

## Global Constraints

- SDK: compileSdk 36, minSdk 36, targetSdk 36
- Must use existing dark theme colors (#FF1A1A2E background, white text)
- Back button must be focusable (D-pad) AND clickable (touch/mouse)
- Back button action: `finish()`
- Package: `com.pinecone.launcher.ui.guard`

---

### Task 1: Create `BaseSettingsActivity`

**Files:**
- Create: `launcher/app/src/main/java/com/pinecone/launcher/ui/guard/BaseSettingsActivity.kt`

**Interfaces:**
- Produces: `BaseSettingsActivity` (abstract, extends `Activity`), method `fun setSettingsContent(title: String, contentView: View)`

- [ ] **Step 1: Write the base class**

```kotlin
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

        // Header bar: back button + title
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

        // Spacer to balance the back button on the right side (keeps title centered)
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                backButton.layoutParams.width.takeIf { it > 0 } ?: 120,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        headerBar.addView(spacer)

        rootLayout.addView(headerBar)
        rootLayout.addView(contentView)

        setContentView(rootLayout)
    }
}
```

- [ ] **Step 2: Build to verify compilation**

```bash
cd launcher && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL (new file compiles, no usages yet)

- [ ] **Step 3: Commit**

```bash
git add launcher/app/src/main/java/com/pinecone/launcher/ui/guard/BaseSettingsActivity.kt
git commit -m "feat: add BaseSettingsActivity with back button header"
```

---

### Task 2: Refactor `ParentSettingsActivity`

**Files:**
- Modify: `launcher/app/src/main/java/com/pinecone/launcher/ui/guard/ParentSettingsActivity.kt`
- Delete: `launcher/app/src/main/res/layout/activity_parent_settings.xml` (optional, can leave)

**Interfaces:**
- Consumes: `BaseSettingsActivity.setSettingsContent(title, contentView)`
- Produces: Same public behavior — opens editor activities on item click

- [ ] **Step 1: Refactor to extend BaseSettingsActivity**

Replace the entire file content:

```kotlin
package com.pinecone.launcher.ui.guard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import com.pinecone.guard.service.GuardClientHolder

class ParentSettingsActivity : BaseSettingsActivity() {

    data class SettingsItem(val title: String, val subtitle: String, val action: () -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val items = buildSettingsItems()
        val listView = ListView(this).apply {
            adapter = object : ArrayAdapter<SettingsItem>(
                this@ParentSettingsActivity, android.R.layout.simple_list_item_2, android.R.id.text1, items
            ) {
                override fun getView(pos: Int, convertView: View?, parent: android.view.ViewGroup): View {
                    val view = super.getView(pos, convertView, parent)
                    view.findViewById<TextView>(android.R.id.text1).apply {
                        text = items[pos].title; textSize = 22f
                    }
                    view.findViewById<TextView>(android.R.id.text2).apply {
                        text = items[pos].subtitle; textSize = 16f
                    }
                    return view
                }
            }
            setOnItemClickListener { _, _, pos, _ -> items[pos].action() }
        }

        setSettingsContent("家长设置", listView)
    }

    private fun buildSettingsItems(): List<SettingsItem> = listOf(
        SettingsItem("⏱️ 每日累计时长", "2 小时 30 分") {
            startActivity(Intent(this@ParentSettingsActivity,
                com.pinecone.launcher.ui.guard.editors.DailyLimitFragment::class.java))
        },
        SettingsItem("🔄 强制休息间隔", "每 40 分 休 10 分") {
            startActivity(Intent(this@ParentSettingsActivity,
                com.pinecone.launcher.ui.guard.editors.BreakRuleFragment::class.java))
        },
        SettingsItem("📅 可用时段", "周一至周五 16:00-21:00") {
            startActivity(Intent(this@ParentSettingsActivity,
                com.pinecone.launcher.ui.guard.editors.TimeWindowFragment::class.java))
        },
        SettingsItem("📂 内容分类限制", "") {
            startActivity(Intent(this@ParentSettingsActivity,
                com.pinecone.launcher.ui.guard.editors.CategoryLimitFragment::class.java))
        },
        SettingsItem("📱 App 单独限制", "") {
            startActivity(Intent(this@ParentSettingsActivity,
                com.pinecone.launcher.ui.guard.editors.AppLimitFragment::class.java))
        },
        SettingsItem("⭐ 信用积分", "100分 / 每周一重置") {
            startActivity(Intent(this@ParentSettingsActivity,
                com.pinecone.launcher.ui.guard.editors.CreditConfigFragment::class.java))
        },
        SettingsItem("📊 使用统计", "") {
            startActivity(Intent(this@ParentSettingsActivity,
                com.pinecone.launcher.ui.guard.editors.UsageHistoryFragment::class.java))
        },
        SettingsItem("🔑 修改 PIN", "") {
            startActivity(Intent(this, PinSetupActivity::class.java))
        },
        SettingsItem("🆕 检查更新", "检查并安装新版本桌面") {
            startActivity(Intent(this@ParentSettingsActivity, UpdateActivity::class.java))
        },
        SettingsItem("⏸️ 暂停防沉迷（今天不限制）", "") {
            showPauseConfirmation()
        }
    )

    private fun showPauseConfirmation() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("暂停防沉迷")
        builder.setMessage("确认今天不限制使用时间？\n明天凌晨自动恢复规则。")
        builder.setPositiveButton("确认") { _, _ ->
            GuardClientHolder.pauseToday()
            Toast.makeText(this, "今天防沉迷已暂停", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("取消", null)
        builder.show()
    }
}
```

- [ ] **Step 2: Build to verify compilation**

```bash
cd launcher && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add launcher/app/src/main/java/com/pinecone/launcher/ui/guard/ParentSettingsActivity.kt
git commit -m "refactor: ParentSettingsActivity extends BaseSettingsActivity"
```

---

### Task 3: Refactor `DailyLimitFragment`

**Files:**
- Modify: `launcher/app/src/main/java/com/pinecone/launcher/ui/guard/editors/DailyLimitFragment.kt`

**Interfaces:**
- Consumes: `BaseSettingsActivity.setSettingsContent(title, contentView)`

- [ ] **Step 1: Refactor to extend BaseSettingsActivity**

Replace file:

```kotlin
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

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        }

        val currentText = TextView(this).apply {
            text = formatDuration(selectedMinutes); textSize = 48f
            setTextColor(Color.YELLOW); gravity = Gravity.CENTER; setPadding(0, 0, 0, 48)
        }
        content.addView(currentText)

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
                }
            })
            adjustRow.addView(col)
        }
        content.addView(adjustRow)

        content.addView(TextView(this).apply {
            text = "快速选择:"; textSize = 20f; setTextColor(Color.GRAY)
            setPadding(0, 48, 0, 16)
        })
        listOf(0 to "不限", 60 to "1 小时", 90 to "1.5 小时",
            120 to "2 小时", 150 to "2.5 小时", 180 to "3 小时"
        ).forEach { (min, label) ->
            content.addView(Button(this).apply {
                text = label; textSize = 20f; minWidth = 400; minHeight = 72
                setOnClickListener {
                    selectedMinutes = min.takeIf { it > 0 }
                    currentText.text = formatDuration(selectedMinutes)
                }
            })
        }

        content.addView(Button(this).apply {
            text = "保存"; textSize = 24f; setTextColor(Color.BLACK)
            setBackgroundColor(Color.GREEN); minWidth = 400; minHeight = 80
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = 48
            setOnClickListener {
                GuardClientHolder.updateDailyLimit(selectedMinutes)
                Toast.makeText(this@DailyLimitFragment, "已保存", Toast.LENGTH_SHORT).show()
                finish()
            }
        })

        setSettingsContent("每日累计时长", content)
    }

    private fun formatDuration(minutes: Int?): String =
        if (minutes == null) "不限" else "${minutes / 60} 小时 ${minutes % 60} 分"
}
```

- [ ] **Step 2: Build to verify compilation**

```bash
cd launcher && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add launcher/app/src/main/java/com/pinecone/launcher/ui/guard/editors/DailyLimitFragment.kt
git commit -m "refactor: DailyLimitFragment extends BaseSettingsActivity"
```

---

### Task 4: Refactor `BreakRuleFragment`

**Files:**
- Modify: `launcher/app/src/main/java/com/pinecone/launcher/ui/guard/editors/BreakRuleFragment.kt`

- [ ] **Step 1: Refactor to extend BaseSettingsActivity**

```kotlin
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

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        }

        displayText = TextView(this).apply {
            setTextColor(Color.YELLOW); textSize = 24f; gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
            refreshText()
        }
        content.addView(displayText)

        listOf(
            Triple(30, 10, "30 分 / 10 分"),
            Triple(40, 10, "40 分 / 10 分"),
            Triple(45, 15, "45 分 / 15 分"),
            Triple(0, 0, "不限制")
        ).forEach { (u, b, label) ->
            content.addView(Button(this).apply {
                text = label; textSize = 20f; minWidth = 400; minHeight = 72
                setOnClickListener { usageMin = u; breakMin = b; refreshText() }
            })
        }
        content.addView(Button(this).apply {
            text = "保存"; textSize = 24f; setTextColor(Color.BLACK)
            setBackgroundColor(Color.GREEN); minWidth = 400; minHeight = 80
            setOnClickListener {
                GuardClientHolder.updateBreakRule(usageMin, breakMin)
                Toast.makeText(this@BreakRuleFragment, "已保存", Toast.LENGTH_SHORT).show()
                finish()
            }
        })

        setSettingsContent("强制休息间隔", content)
    }

    private fun refreshText() {
        displayText.text = if (usageMin > 0)
            "连续使用 $usageMin 分钟 → 休息 $breakMin 分钟"
        else "不限制"
    }
}
```

- [ ] **Step 2: Build and commit**

```bash
cd launcher && ./gradlew assembleDebug
git add launcher/app/src/main/java/com/pinecone/launcher/ui/guard/editors/BreakRuleFragment.kt
git commit -m "refactor: BreakRuleFragment extends BaseSettingsActivity"
```

---

### Task 5: Refactor `TimeWindowFragment`

**Files:**
- Modify: `launcher/app/src/main/java/com/pinecone/launcher/ui/guard/editors/TimeWindowFragment.kt`

- [ ] **Step 1: Refactor to extend BaseSettingsActivity**

```kotlin
package com.pinecone.launcher.ui.guard.editors

import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.pinecone.launcher.ui.guard.BaseSettingsActivity

class TimeWindowFragment : BaseSettingsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        }

        listOf("上学模式: 周一至五 16:00-21:00, 周末 8:00-21:00",
               "假期模式: 每天 8:00-21:00",
               "严格模式: 周一至五 18:00-20:00, 周末 10-12 + 14-17"
        ).forEach { label ->
            content.addView(Button(this).apply {
                text = label; textSize = 18f; minWidth = 600; minHeight = 72
                setOnClickListener {
                    Toast.makeText(this@TimeWindowFragment, "已选择: $label", Toast.LENGTH_SHORT).show()
                }
            })
        }
        content.addView(Button(this).apply {
            text = "保存"; textSize = 24f
            setTextColor(0xFF000000.toInt()); setBackgroundColor(0xFF00FF00.toInt())
            minWidth = 400; minHeight = 80
            setOnClickListener {
                Toast.makeText(this@TimeWindowFragment, "已保存", Toast.LENGTH_SHORT).show()
                finish()
            }
        })

        setSettingsContent("可用时段", content)
    }
}
```

- [ ] **Step 2: Build and commit**

```bash
cd launcher && ./gradlew assembleDebug
git add launcher/app/src/main/java/com/pinecone/launcher/ui/guard/editors/TimeWindowFragment.kt
git commit -m "refactor: TimeWindowFragment extends BaseSettingsActivity"
```

---

### Task 6: Refactor `CategoryLimitFragment`

**Files:**
- Modify: `launcher/app/src/main/java/com/pinecone/launcher/ui/guard/editors/CategoryLimitFragment.kt`

- [ ] **Step 1: Refactor to extend BaseSettingsActivity (remove manual back button)**

```kotlin
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

        val content = LinearLayout(this).apply {
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
            content.addView(row)
        }

        setSettingsContent("内容分类限制", content)
    }
}
```

- [ ] **Step 2: Build and commit**

```bash
cd launcher && ./gradlew assembleDebug
git add launcher/app/src/main/java/com/pinecone/launcher/ui/guard/editors/CategoryLimitFragment.kt
git commit -m "refactor: CategoryLimitFragment extends BaseSettingsActivity"
```

---

### Task 7: Refactor `AppLimitFragment`

**Files:**
- Modify: `launcher/app/src/main/java/com/pinecone/launcher/ui/guard/editors/AppLimitFragment.kt`

- [ ] **Step 1: Refactor to extend BaseSettingsActivity (remove manual back button)**

```kotlin
package com.pinecone.launcher.ui.guard.editors

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.pinecone.launcher.ui.guard.BaseSettingsActivity

class AppLimitFragment : BaseSettingsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(TextView(this).apply {
            text = "暂无 App 限制\n点击 [+ 添加] 添加规则"; textSize = 20f
            setTextColor(Color.GRAY); gravity = Gravity.CENTER
        })
        content.addView(Button(this).apply {
            text = "+ 添加 App 限制"; textSize = 20f; minWidth = 400; minHeight = 72
            setOnClickListener {
                Toast.makeText(this@AppLimitFragment, "从已安装应用中选择", Toast.LENGTH_SHORT).show()
            }
        })

        setSettingsContent("App 单独限制", content)
    }
}
```

- [ ] **Step 2: Build and commit**

```bash
cd launcher && ./gradlew assembleDebug
git add launcher/app/src/main/java/com/pinecone/launcher/ui/guard/editors/AppLimitFragment.kt
git commit -m "refactor: AppLimitFragment extends BaseSettingsActivity"
```

---

### Task 8: Refactor `CreditConfigFragment`

**Files:**
- Modify: `launcher/app/src/main/java/com/pinecone/launcher/ui/guard/editors/CreditConfigFragment.kt`

- [ ] **Step 1: Refactor to extend BaseSettingsActivity (remove manual back button)**

```kotlin
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

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(TextView(this).apply {
            text = "每周积分总额"; textSize = 20f; setTextColor(Color.GRAY)
            setPadding(0, 0, 0, 16)
        })
        val totalText = TextView(this).apply {
            text = "$weeklyTotal 分"; textSize = 48f; setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER; setPadding(0, 0, 0, 32)
        }
        content.addView(totalText)
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
        content.addView(presetRow)
        content.addView(TextView(this).apply {
            text = "当前积分: 100/100"; textSize = 20f; setTextColor(Color.GRAY)
            setPadding(0, 32, 0, 16)
        })
        content.addView(Button(this).apply {
            text = "立即重置为 $weeklyTotal"; textSize = 18f; minWidth = 400; minHeight = 60
            setOnClickListener {
                GuardClientHolder.resetCredits()
                Toast.makeText(this@CreditConfigFragment, "积分已重置", Toast.LENGTH_SHORT).show()
            }
        })

        setSettingsContent("信用积分设置", content)
    }
}
```

- [ ] **Step 2: Build and commit**

```bash
cd launcher && ./gradlew assembleDebug
git add launcher/app/src/main/java/com/pinecone/launcher/ui/guard/editors/CreditConfigFragment.kt
git commit -m "refactor: CreditConfigFragment extends BaseSettingsActivity"
```

---

### Task 9: Refactor `UsageHistoryFragment`

**Files:**
- Modify: `launcher/app/src/main/java/com/pinecone/launcher/ui/guard/editors/UsageHistoryFragment.kt`

- [ ] **Step 1: Refactor to extend BaseSettingsActivity (remove manual back button)**

```kotlin
package com.pinecone.launcher.ui.guard.editors

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.pinecone.launcher.ui.guard.BaseSettingsActivity

class UsageHistoryFragment : BaseSettingsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        listOf("一" to "1h 50m", "二" to "1h 35m", "三" to "2h 05m (+15m)",
            "四" to "1h 10m", "五" to "2h 30m (满)", "六" to "1h 20m 🟢", "日" to "2h 10m (+10m)"
        ).forEach { (day, dur) ->
            content.addView(TextView(this).apply {
                text = "$day  $dur"; textSize = 18f; setTextColor(Color.WHITE)
                setPadding(0, 4, 0, 4)
            })
        }
        content.addView(TextView(this).apply {
            text = "\n按分类: 🔤英语 5h20m | 📚绘本 3h10m | 🎬纪录 4h | 🧩App 2h30m"
            textSize = 16f; setTextColor(Color.GRAY); gravity = Gravity.CENTER
        })

        setSettingsContent("📊 使用统计", content)
    }
}
```

- [ ] **Step 2: Build and commit**

```bash
cd launcher && ./gradlew assembleDebug
git add launcher/app/src/main/java/com/pinecone/launcher/ui/guard/editors/UsageHistoryFragment.kt
git commit -m "refactor: UsageHistoryFragment extends BaseSettingsActivity"
```

---

### Task 10: Refactor `PinSetupActivity`

**Files:**
- Modify: `launcher/app/src/main/java/com/pinecone/launcher/ui/guard/PinSetupActivity.kt`

- [ ] **Step 1: Refactor to extend BaseSettingsActivity**

```kotlin
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

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        }

        titleText = TextView(this).apply {
            text = if (isVerifyMode) "请输入家长 PIN" else "设置家长 PIN"
            textSize = 28f; gravity = Gravity.CENTER; setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 48)
        }
        content.addView(titleText)

        dotsText = TextView(this).apply {
            text = "[ _ ][ _ ][ _ ][ _ ][ _ ][ _ ]"
            textSize = 32f; textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setTextColor(0xFFFFFFFF.toInt()); setPadding(0, 0, 0, 24)
        }
        content.addView(dotsText)

        val errorText = TextView(this).apply {
            textSize = 18f; gravity = Gravity.CENTER
            setTextColor(0xFFFF4444.toInt()); visibility = TextView.GONE
        }
        content.addView(errorText)

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
            content.addView(rowLayout)
        }

        setSettingsContent(if (isVerifyMode) "验证 PIN" else "设置 PIN", content)
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
```

- [ ] **Step 2: Build and commit**

```bash
cd launcher && ./gradlew assembleDebug
git add launcher/app/src/main/java/com/pinecone/launcher/ui/guard/PinSetupActivity.kt
git commit -m "refactor: PinSetupActivity extends BaseSettingsActivity"
```

---

### Task 11: Refactor `UpdateActivity`

**Files:**
- Modify: `launcher/app/src/main/java/com/pinecone/launcher/ui/guard/UpdateActivity.kt`

- [ ] **Step 1: Refactor to extend BaseSettingsActivity**

```kotlin
package com.pinecone.launcher.ui.guard

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.pinecone.guard.api.UpdateResult
import com.pinecone.guard.engine.UpdateChecker
import com.pinecone.guard.engine.UpdateInstaller

class UpdateActivity : BaseSettingsActivity() {

    private lateinit var titleText: TextView
    private lateinit var detailText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var actionButton: Button
    private var latestUpdate: UpdateResult.Available? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        }

        titleText = TextView(this).apply {
            text = "检查更新"; textSize = 30f
            setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        content.addView(titleText)

        detailText = TextView(this).apply {
            textSize = 22f; setTextColor(0xFF8B949E.toInt())
            gravity = Gravity.CENTER; setPadding(0, 0, 0, 32)
        }
        content.addView(detailText)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0
            layoutParams = LinearLayout.LayoutParams(800, 24)
            visibility = View.GONE
        }
        content.addView(progressBar)

        actionButton = Button(this).apply {
            text = "检查更新"; textSize = 20f; minWidth = 300; minHeight = 72
            setOnClickListener { onActionClick() }
        }
        content.addView(actionButton)

        setSettingsContent("检查更新", content)
        checkForUpdate()
    }

    private fun onActionClick() {
        latestUpdate?.let { downloadUpdate(it) } ?: checkForUpdate()
    }

    private fun checkForUpdate() {
        val checker = UpdateChecker(this)
        actionButton.isEnabled = false
        titleText.text = "正在检查..."

        checker.checkForUpdate { result ->
            runOnUiThread {
                when (result) {
                    is UpdateResult.UpToDate -> {
                        titleText.text = "已是最新版本"
                        detailText.text = "当前版本 ${
                            packageManager.getPackageInfo(packageName, 0).versionName
                        } · 无需更新"
                        actionButton.text = "重新检查"; actionButton.isEnabled = true
                    }
                    is UpdateResult.Available -> {
                        latestUpdate = result
                        titleText.text = "发现新版本 ${result.versionName}"
                        detailText.text = "${result.size / 1048576} MB\n\n${result.changelog}"
                        actionButton.text = "立即更新"; actionButton.isEnabled = true
                    }
                    is UpdateResult.Error -> {
                        titleText.text = "检查失败"
                        detailText.text = result.message
                        actionButton.text = "重试"; actionButton.isEnabled = true
                    }
                }
            }
        }
    }

    private fun downloadUpdate(update: UpdateResult.Available) {
        actionButton.isEnabled = false
        actionButton.text = "下载中..."
        progressBar.visibility = View.VISIBLE

        val installer = UpdateInstaller(this)
        installer.downloadAndInstall(
            update,
            onProgress = { p ->
                runOnUiThread { progressBar.progress = (p * 100).toInt() }
            },
            onComplete = { result ->
                runOnUiThread {
                    result.onSuccess {
                        titleText.text = "更新完成"
                        detailText.text = "正在重启桌面..."
                        progressBar.visibility = View.GONE
                    }.onFailure { e ->
                        titleText.text = "更新失败"
                        detailText.text = e.message
                        actionButton.text = "重试"; actionButton.isEnabled = true
                        progressBar.visibility = View.GONE
                    }
                }
            }
        )
    }
}
```

- [ ] **Step 2: Build and commit**

```bash
cd launcher && ./gradlew assembleDebug
git add launcher/app/src/main/java/com/pinecone/launcher/ui/guard/UpdateActivity.kt
git commit -m "refactor: UpdateActivity extends BaseSettingsActivity"
```

---

### Task 12: Refactor `SetupWizardActivity`

**Files:**
- Modify: `launcher/app/src/main/java/com/pinecone/launcher/ui/guard/SetupWizardActivity.kt`

- [ ] **Step 1: Refactor to extend BaseSettingsActivity**

```kotlin
package com.pinecone.launcher.ui.guard

import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import com.pinecone.guard.service.GuardClientHolder
import com.pinecone.guard.service.GuardDeviceAdminReceiver

class SetupWizardActivity : BaseSettingsActivity() {

    private lateinit var statusText: TextView
    private lateinit var actionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            textSize = 22f; setTextColor(0xFFFFFF00.toInt())
            gravity = Gravity.CENTER; setPadding(0, 0, 0, 32)
        }
        content.addView(statusText)

        actionButton = Button(this).apply {
            textSize = 20f; minWidth = 400; minHeight = 80
            setTextColor(0xFF000000.toInt()); setBackgroundColor(0xFF00FF00.toInt())
        }
        content.addView(actionButton)

        content.addView(Button(this).apply {
            text = "跳过，稍后设置"; textSize = 18f; minWidth = 300; minHeight = 60
            setOnClickListener { checkAndProceed() }
        })

        setSettingsContent("🛡️ 防沉迷初始化向导", content)
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        when {
            !isUsageStatsGranted() -> {
                statusText.text = "步骤 1/2:\n需要授权「使用情况访问权限」\n才能统计应用使用时间"
                actionButton.text = "前往授权"; actionButton.visibility = Button.VISIBLE
                actionButton.setOnClickListener {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
            !isDeviceAdminActive() -> {
                statusText.text = "步骤 2/2:\n需要激活「设备管理器」\n才能在超时时锁定屏幕"
                actionButton.text = "激活设备管理器"; actionButton.visibility = Button.VISIBLE
                actionButton.setOnClickListener {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            GuardDeviceAdminReceiver.getComponentName(this@SetupWizardActivity))
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "松果智学需要设备管理器权限来在超时时锁定屏幕，保护孩子的用眼健康。")
                    }
                    startActivity(intent)
                }
            }
            else -> {
                statusText.text = "✅ 所有权限已就绪\n防沉迷系统开始守护"
                actionButton.visibility = Button.GONE
                checkAndProceed()
            }
        }
    }

    private fun checkAndProceed() {
        if (!GuardClientHolder.isInitialized) {
            GuardClientHolder.initialize(this)
        }
        val storage = com.pinecone.guard.data.SecureStorage(this)
        if (!storage.isPinSetup()) {
            val intent = Intent(this, PinSetupActivity::class.java)
            startActivity(intent)
        }
        finish()
    }

    private fun isUsageStatsGranted(): Boolean {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000, now)
        return stats.isNotEmpty()
    }

    private fun isDeviceAdminActive(): Boolean =
        GuardDeviceAdminReceiver.isAdminActive(this)
}
```

- [ ] **Step 2: Build and commit**

```bash
cd launcher && ./gradlew assembleDebug
git add launcher/app/src/main/java/com/pinecone/launcher/ui/guard/SetupWizardActivity.kt
git commit -m "refactor: SetupWizardActivity extends BaseSettingsActivity"
```

---

### Task 13: Final build verification

- [ ] **Step 1: Clean build from scratch**

```bash
cd launcher && ./gradlew clean assembleDebug
```

Expected: BUILD SUCCESSFUL with no warnings

- [ ] **Step 2: Verify all 11 activities reference BaseSettingsActivity**

```bash
grep -r "BaseSettingsActivity" launcher/app/src/main/java/
```

Expected: 11 files + the base class itself = 12 matches

- [ ] **Step 3: Commit any remaining cleanup**

```bash
git status
# If activity_parent_settings.xml is no longer used, remove it:
# git rm launcher/app/src/main/res/layout/activity_parent_settings.xml
git add -A
git commit -m "chore: final cleanup after settings back button refactor"
```
