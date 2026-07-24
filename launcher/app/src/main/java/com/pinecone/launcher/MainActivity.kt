package com.pinecone.launcher

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.FragmentActivity
import com.pinecone.guard.service.GuardClientHolder

data class CourseItem(val id: Long, val title: String, val category: String, val actionUrl: String)
data class CategoryData(val name: String, val items: List<CourseItem>)
data class TabData(val name: String, val categories: List<CategoryData>)

class MainActivity : FragmentActivity() {

    private lateinit var tabBar: LinearLayout
    private lateinit var sidebar: LinearLayout
    private lateinit var sidebarScroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var contentScroll: ScrollView
    private val tabButtons = mutableListOf<Button>()
    private val sidebarButtons = mutableListOf<Button>()
    private val cardViews = mutableListOf<View>()
    private val tabIds = mutableListOf<Int>()
    private var firstSidebarBtnId = 0
    private var currentTab = 0

    private val tabs = listOf(
        TabData("🌐 资源", listOf(
            CategoryData("🔤 英语专区", listOf(
                CourseItem(1, "新概念英语 第一册", "英语", "pkg:com.example.english1"),
                CourseItem(2, "剑桥少儿英语", "英语", "pkg:com.example.cambridge"),
                CourseItem(3, "AI 语音口语伴学", "英语", "action:ai_talk"))),
            CategoryData("📚 绘本阅读", listOf(
                CourseItem(4, "中国古代神话", "阅读", "action:read_1"),
                CourseItem(5, "少年科普百科", "阅读", "action:read_2"))),
            CategoryData("🎬 纪录片", listOf(
                CourseItem(6, "蓝色星球 4K", "纪录片", "action:doc_1"),
                CourseItem(7, "中国通史", "纪录片", "action:doc_2"))),
            CategoryData("🧩 学习 App", listOf(
                CourseItem(8, "网易有道词典", "App", "pkg:com.youdao.dict"),
                CourseItem(9, "哔哩哔哩动画 TV", "App", "pkg:tv.danmaku.bili"))),
            CategoryData("🤖 AI 工具", listOf(
                CourseItem(20, "豆包", "字节跳动 AI 助手", "action:web:https://www.doubao.com"),
                CourseItem(21, "Kimi", "月之暗面 AI 对话", "action:web:https://kimi.moonshot.cn"),
                CourseItem(22, "通义千问", "阿里 AI 大模型", "action:web:https://tongyi.aliyun.com"),
                CourseItem(23, "文心一言", "百度 AI 助手", "action:web:https://yiyan.baidu.com"),
                CourseItem(24, "智谱清言", "ChatGLM 大模型", "action:web:https://chatglm.cn"))),
            CategoryData("📚 在线学习", listOf(
                CourseItem(25, "国家中小学智慧教育", "官方学习平台", "action:web:https://www.smartedu.cn"),
                CourseItem(26, "学堂在线", "清华 MOOC 平台", "action:web:https://www.xuetangx.com"),
                CourseItem(27, "中国大学 MOOC", "高等教育课程", "action:web:https://www.icourse163.org"),
                CourseItem(28, "网易公开课", "国际名校课程", "action:web:https://open.163.com"),
                CourseItem(29, "多邻国", "免费学外语", "action:web:https://www.duolingo.com"))),
            CategoryData("🎬 纪录片/视频", listOf(
                CourseItem(30, "B站知识区", "科普·人文·历史", "action:web:https://www.bilibili.com/v/knowledge"),
                CourseItem(31, "央视纪实", "CCTV 纪录片", "action:web:https://tv.cctv.com"),
                CourseItem(32, "中国纪录片网", "国产纪录片库", "action:web:https://www.docuchina.cn"))),
            CategoryData("🧩 编程启蒙", listOf(
                CourseItem(33, "Scratch", "MIT 可视化编程", "action:web:https://scratch.mit.edu"),
                CourseItem(34, "Code.org", "一小时编程入门", "action:web:https://code.org"),
                CourseItem(35, "Mind+", "国产 Scratch 平台", "action:web:https://mindplus.cc"))),
            CategoryData("🔍 百科/工具", listOf(
                CourseItem(36, "维基百科", "自由百科全书", "action:web:https://www.wikipedia.org"),
                CourseItem(37, "古诗文网", "经典诗词文库", "action:web:https://www.gushiwen.cn"),
                CourseItem(38, "百度百科", "中文百科全书", "action:web:https://baike.baidu.com")))
        )),
        TabData("📱 App", listOf(
            CategoryData("📺 视频影音", listOf(
                CourseItem(40, "B站 TV", "弹幕视频平台", "pkg:tv.danmaku.bili"),
                CourseItem(41, "央视频", "CCTV 官方视频", "pkg:com.cctv.yangshipin"),
                CourseItem(42, "芒果TV", "湖南卫视综艺", "pkg:com.hunantv.imgo.activity"),
                CourseItem(43, "西瓜视频", "短视频 & 影视", "pkg:com.ss.android.article.video"))),
            CategoryData("📖 阅读听书", listOf(
                CourseItem(44, "微信读书", "海量电子书", "pkg:com.tencent.weread"),
                CourseItem(45, "喜马拉雅", "有声书 & 播客", "pkg:com.ximalaya.ting.android.tv"),
                CourseItem(46, "得到", "知识音频课程", "pkg:com.luojilab.dedao"),
                CourseItem(47, "凯叔讲故事", "儿童有声内容", "pkg:com.kaishu.story"))),
            CategoryData("🧠 知识学习", listOf(
                CourseItem(48, "有道词典", "英语翻译学习", "pkg:com.youdao.dict"),
                CourseItem(49, "百度翻译", "多语种翻译", "pkg:com.baidu.translate"),
                CourseItem(50, "全历史", "历史知识图谱", "pkg:com.allhistory.app"),
                CourseItem(51, "每日故宫", "故宫藏品欣赏", "pkg:cn.edu.dailypalace"))),
            CategoryData("🎨 创意启蒙", listOf(
                CourseItem(52, "画吧", "手机绘画社区", "pkg:com.huaba.app"),
                CourseItem(53, "Simply Piano", "钢琴自学入门", "pkg:com.joytunes.simplypiano"),
                CourseItem(54, "贝乐虎儿歌", "儿童动画儿歌", "pkg:com.beilehu.erge"),
                CourseItem(55, "小火箭编程", "幼儿编程启蒙", "pkg:com.xiaohuojian.code"))),
            CategoryData("♟️ 益智休闲", listOf(
                CourseItem(56, "中国象棋", "经典棋类对战", "pkg:com.cnvcs.xq"),
                CourseItem(57, "围棋", "人机 & 在线对弈", "pkg:com.tencent.wgo"),
                CourseItem(58, "数独", "逻辑推理训练", "pkg:com.sudoku.game"),
                CourseItem(59, "纪念碑谷", "视觉解谜艺术", "pkg:com.ustwo.monumentvalley"))),
            CategoryData("🛠 实用工具", listOf(
                CourseItem(60, "文件管理器", "系统文件浏览", "pkg:com.android.documentsui"),
                CourseItem(61, "浏览器", "网页浏览", "pkg:com.android.chrome"),
                CourseItem(62, "计算器", "科学计算器", "pkg:com.android.calculator2"),
                CourseItem(63, "应用商店", "安装 & 更新应用", "pkg:com.android.vending")))
        )),
        TabData("⚙️ 设置", listOf(
            CategoryData("⚙️ 系统设置", listOf(
                CourseItem(11, "系统设置", "Android TV 设置", "action:system_settings"))),
            CategoryData("🔒 家长设置", listOf(
                CourseItem(12, "家长设置", "防沉迷 & PIN", "action:parent_settings")))
        ))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        GuardClientHolder.initialize(this)

        tabBar = findViewById(R.id.tab_bar)
        sidebar = findViewById(R.id.sidebar)
        sidebarScroll = findViewById(R.id.sidebar_scroll)
        content = findViewById(R.id.content)
        contentScroll = findViewById(R.id.content_scroll)

        buildTabBar()
        switchTab(0)
    }

    // ═══════════════════════════════════════════
    //  Tab Bar
    // ═══════════════════════════════════════════

    private fun buildTabBar() {
        tabBar.removeAllViews()
        tabButtons.clear()
        tabIds.clear()

        tabs.forEachIndexed { index, tab ->
            val id = View.generateViewId()
            tabIds.add(id)
            val btn = Button(this).apply {
                this.id = id
                text = tab.name
                textSize = 20f
                setTextColor(Color.parseColor("#B0B0C0"))
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(32, 0, 32, 0)
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                )
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        setBackgroundColor(Color.parseColor("#3A3A5A"))
                    } else {
                        setBackgroundColor(Color.TRANSPARENT)
                    }
                }
                setOnClickListener { switchTab(index) }
            }
            tabBar.addView(btn)
            tabButtons.add(btn)
        }
    }

    private fun switchTab(index: Int) {
        currentTab = index
        val tab = tabs[index]

        tabButtons.forEachIndexed { i, btn ->
            if (i == index) {
                btn.setTextColor(Color.parseColor("#FF4FC3F7"))
                btn.textSize = 22f
            } else {
                btn.setTextColor(Color.parseColor("#B0B0C0"))
                btn.textSize = 20f
            }
        }

        buildSidebar(tab)
        buildContent(tab)

        // ── Wire ALL focus paths ──
        val activeTabId = tabIds[currentTab]

        // Tabs DOWN → first sidebar btn
        tabButtons.forEach { it.nextFocusDownId = firstSidebarBtnId }

        // ALL sidebar btns UP → active tab
        sidebarButtons.forEach { it.nextFocusUpId = activeTabId }

        // Per-category focus binding: sidebar btn ↔ its first card
        sidebarButtons.forEachIndexed { i, sidebarBtn ->
            val cardRow = content.getChildAt(i * 2 + 1) as? HorizontalScrollView
            val cardContainer = cardRow?.getChildAt(0) as? LinearLayout
            if (cardContainer != null && cardContainer.childCount > 0) {
                sidebarBtn.nextFocusRightId = cardContainer.getChildAt(0).id
                for (j in 0 until cardContainer.childCount) {
                    cardContainer.getChildAt(j).nextFocusLeftId = sidebarBtn.id
                }
            }
        }
    }

    // ═══════════════════════════════════════════
    //  Sidebar
    // ═══════════════════════════════════════════

    private fun buildSidebar(tab: TabData) {
        sidebar.removeAllViews()
        sidebarButtons.clear()

        tab.categories.forEachIndexed { index, cat ->
            val btn = Button(this).apply {
                text = cat.name
                textSize = 18f
                setTextColor(Color.parseColor("#D0D0E0"))
                setBackgroundColor(Color.TRANSPARENT)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(20, 16, 20, 16)
                minHeight = 56
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        setBackgroundColor(Color.parseColor("#3A3A5A"))
                        scrollToCategory(index)
                    } else {
                        setBackgroundColor(Color.TRANSPARENT)
                    }
                }
                setOnClickListener { scrollToCategory(index) }
            }
            sidebar.addView(btn)
            sidebarButtons.add(btn)
        }

        // Wire focus: first sidebar btn UP -> active tab
        if (sidebarButtons.isNotEmpty()) {
            firstSidebarBtnId = View.generateViewId()
            sidebarButtons.first().id = firstSidebarBtnId
            sidebarButtons.first().nextFocusUpId = tabIds[currentTab]
        }

        // Wire focus: each tab DOWN -> first sidebar btn
        tabButtons.forEach { it.nextFocusDownId = firstSidebarBtnId }
    }

    private fun scrollToCategory(catIndex: Int) {
        // Each category has 2 children in content: title (0) + card row (1)
        val section = content.getChildAt(catIndex * 2) ?: return
        val scrollY = section.top - content.paddingTop
        contentScroll.smoothScrollTo(0, scrollY)

        // Highlight sidebar button
        sidebarButtons.forEachIndexed { i, btn ->
            if (i == catIndex) {
                btn.setBackgroundColor(Color.parseColor("#2A2A4A"))
                btn.setTextColor(Color.parseColor("#FF4FC3F7"))
            } else {
                btn.setBackgroundColor(Color.TRANSPARENT)
                btn.setTextColor(Color.parseColor("#D0D0E0"))
            }
        }
    }

    // ═══════════════════════════════════════════
    //  Content (card rows)
    // ═══════════════════════════════════════════

    private fun buildContent(tab: TabData) {
        content.removeAllViews()
        cardViews.clear()

        tab.categories.forEach { cat ->
            // Section title
            val title = TextView(this).apply {
                text = cat.name
                textSize = 22f
                setTextColor(Color.WHITE)
                setPadding(0, 8, 0, 12)
            }
            content.addView(title)

            // Card row
            val cardRow = HorizontalScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 24 }
                isHorizontalScrollBarEnabled = false
                isFocusable = false
            }

            val cardContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, 4)
            }

            cat.items.forEach { item ->
                val card = buildCard(item)
                cardContainer.addView(card)
                cardViews.add(card)
            }

            cardRow.addView(cardContainer)
            content.addView(cardRow)
        }
    }

    private fun buildCard(item: CourseItem): View {
        val card = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(260, 160).apply {
                rightMargin = 16
            }
            isFocusable = true
            isClickable = true

            // Card background
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF252545"))
                cornerRadius = 12f
            }

            setOnFocusChangeListener { _, hasFocus ->
                background = GradientDrawable().apply {
                    setColor(if (hasFocus) Color.parseColor("#FF3A6A9A") else Color.parseColor("#FF252545"))
                    cornerRadius = 12f
                    if (hasFocus) {
                        setStroke(2, Color.parseColor("#FF4FC3F7"))
                    }
                }
            }

            setOnClickListener { handleItemClick(item) }
        }

        val icon = TextView(this).apply {
            text = item.title.first().toString()
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(48, 48).apply { bottomMargin = 8 }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF3A3A6A"))
                shape = GradientDrawable.OVAL
            }
        }
        card.addView(icon)

        val title = TextView(this).apply {
            text = item.title
            textSize = 16f
            setTextColor(Color.WHITE)
            maxLines = 1
            gravity = Gravity.CENTER
        }
        card.addView(title)

        val subtitle = TextView(this).apply {
            text = item.category
            textSize = 12f
            setTextColor(Color.parseColor("#B0B0C0"))
            maxLines = 1
            gravity = Gravity.CENTER
        }
        card.addView(subtitle)

        return card
    }

    // ═══════════════════════════════════════════
    //  Click handling
    // ═══════════════════════════════════════════

    private fun handleItemClick(item: CourseItem) {
        when {
            item.actionUrl.startsWith("pkg:") -> {
                val pkgName = item.actionUrl.removePrefix("pkg:")
                val intent = packageManager.getLaunchIntentForPackage(pkgName)
                if (intent != null) startActivity(intent)
                else Toast.makeText(this, "未安装应用: $pkgName", Toast.LENGTH_SHORT).show()
            }
            item.actionUrl.startsWith("action:web:") -> {
                val url = item.actionUrl.removePrefix("action:web:")
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                }
            }
            item.actionUrl == "action:system_settings" ->
                startActivity(Intent(Settings.ACTION_SETTINGS))
            item.actionUrl == "action:parent_settings" ->
                startActivity(Intent(this, com.pinecone.launcher.ui.guard.ParentSettingsActivity::class.java))
            else ->
                Toast.makeText(this, "进入课程: ${item.title}", Toast.LENGTH_SHORT).show()
        }
    }
}
