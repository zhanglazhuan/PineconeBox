package com.pinecone.launcher.ui.guard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import com.pinecone.launcher.R
import com.pinecone.guard.service.GuardClientHolder

class ParentSettingsActivity : BaseSettingsActivity() {

    data class SettingsItem(val title: String, val subtitle: String, val action: () -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val items = buildSettingsItems()
        val listView = ListView(this).apply {
            divider = null
            dividerHeight = 0
            isFocusable = true
            isFocusableInTouchMode = true
            selector = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            setPadding(0, 8, 0, 8)
            clipToPadding = false
            adapter = object : ArrayAdapter<SettingsItem>(
                this@ParentSettingsActivity, R.layout.item_parent_setting, R.id.setting_label, items
            ) {
                override fun getView(pos: Int, convertView: View?, parent: android.view.ViewGroup): View {
                    val view = super.getView(pos, convertView, parent)
                    view.findViewById<TextView>(R.id.setting_label).apply {
                        text = items[pos].title
                    }
                    view.findViewById<TextView>(R.id.setting_value).apply {
                        text = items[pos].subtitle
                        visibility = if (items[pos].subtitle.isEmpty()) View.GONE else View.VISIBLE
                    }
                    return view
                }
            }
            setOnItemClickListener { _, _, pos, _ -> items[pos].action() }
        }

        setSettingsContent("家长设置", listView)
    }

    private fun buildSettingsItems(): List<SettingsItem> = listOf(
        SettingsItem("⏱️ 每日总时长", "2 小时 30 分") {
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
