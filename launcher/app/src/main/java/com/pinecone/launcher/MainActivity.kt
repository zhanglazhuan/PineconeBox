package com.pinecone.launcher

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import com.pinecone.guard.service.GuardClientHolder

data class CourseItem(val id: Long, val title: String, val category: String, val actionUrl: String)

data class CategoryData(val header: HeaderItem, val items: List<CourseItem>)

data class TabData(val name: String, val categories: List<CategoryData>)

class MainActivity : FragmentActivity() {

    private lateinit var browseFragment: BrowseSupportFragment
    private lateinit var tabBar: LinearLayout
    private val tabButtons = mutableListOf<Button>()
    private var currentTab = 0

    private val tabs = listOf(
        TabData("🌐 资源", listOf(
            CategoryData(HeaderItem(0, "🔤 英语专区"), listOf(
                CourseItem(1, "新概念英语 第一册", "英语", "pkg:com.example.english1"),
                CourseItem(2, "剑桥少儿英语", "英语", "pkg:com.example.cambridge"),
                CourseItem(3, "AI 语音口语伴学", "英语", "action:ai_talk"))),
            CategoryData(HeaderItem(1, "📚 绘本阅读"), listOf(
                CourseItem(4, "中国古代神话", "阅读", "action:read_1"),
                CourseItem(5, "少年科普百科", "阅读", "action:read_2"))),
            CategoryData(HeaderItem(2, "🎬 纪录片"), listOf(
                CourseItem(6, "蓝色星球 4K", "纪录片", "action:doc_1"),
                CourseItem(7, "中国通史", "纪录片", "action:doc_2"))),
            CategoryData(HeaderItem(3, "🧩 学习 App"), listOf(
                CourseItem(8, "网易有道词典", "App", "pkg:com.youdao.dict"),
                CourseItem(9, "哔哩哔哩动画 TV", "App", "pkg:tv.danmaku.bili"))),
            CategoryData(HeaderItem(5, "🤖 AI 工具"), listOf(
                CourseItem(20, "豆包", "字节跳动 AI 助手", "action:web:https://www.doubao.com"),
                CourseItem(21, "Kimi", "月之暗面 AI 对话", "action:web:https://kimi.moonshot.cn"),
                CourseItem(22, "通义千问", "阿里 AI 大模型", "action:web:https://tongyi.aliyun.com"),
                CourseItem(23, "文心一言", "百度 AI 助手", "action:web:https://yiyan.baidu.com"),
                CourseItem(24, "智谱清言", "ChatGLM 大模型", "action:web:https://chatglm.cn"))),
            CategoryData(HeaderItem(6, "📚 在线学习"), listOf(
                CourseItem(25, "国家中小学智慧教育", "官方学习平台", "action:web:https://www.smartedu.cn"),
                CourseItem(26, "学堂在线", "清华 MOOC 平台", "action:web:https://www.xuetangx.com"),
                CourseItem(27, "中国大学 MOOC", "高等教育课程", "action:web:https://www.icourse163.org"),
                CourseItem(28, "网易公开课", "国际名校课程", "action:web:https://open.163.com"),
                CourseItem(29, "多邻国", "免费学外语", "action:web:https://www.duolingo.com"))),
            CategoryData(HeaderItem(7, "🎬 纪录片/视频"), listOf(
                CourseItem(30, "B站知识区", "科普·人文·历史", "action:web:https://www.bilibili.com/v/knowledge"),
                CourseItem(31, "央视纪实", "CCTV 纪录片", "action:web:https://tv.cctv.com"),
                CourseItem(32, "中国纪录片网", "国产纪录片库", "action:web:https://www.docuchina.cn"))),
            CategoryData(HeaderItem(8, "🧩 编程启蒙"), listOf(
                CourseItem(33, "Scratch", "MIT 可视化编程", "action:web:https://scratch.mit.edu"),
                CourseItem(34, "Code.org", "一小时编程入门", "action:web:https://code.org"),
                CourseItem(35, "Mind+", "国产 Scratch 平台", "action:web:https://mindplus.cc"))),
            CategoryData(HeaderItem(9, "🔍 百科/工具"), listOf(
                CourseItem(36, "维基百科", "自由百科全书", "action:web:https://www.wikipedia.org"),
                CourseItem(37, "古诗文网", "经典诗词文库", "action:web:https://www.gushiwen.cn"),
                CourseItem(38, "百度百科", "中文百科全书", "action:web:https://baike.baidu.com")))
        )),
        TabData("📱 App", listOf(
            CategoryData(HeaderItem(10, "📺 视频影音"), listOf(
                CourseItem(40, "B站 TV", "弹幕视频平台", "pkg:tv.danmaku.bili"),
                CourseItem(41, "央视频", "CCTV 官方视频", "pkg:com.cctv.yangshipin"),
                CourseItem(42, "芒果TV", "湖南卫视综艺", "pkg:com.hunantv.imgo.activity"),
                CourseItem(43, "西瓜视频", "短视频 & 影视", "pkg:com.ss.android.article.video"))),
            CategoryData(HeaderItem(11, "📖 阅读听书"), listOf(
                CourseItem(44, "微信读书", "海量电子书", "pkg:com.tencent.weread"),
                CourseItem(45, "喜马拉雅", "有声书 & 播客", "pkg:com.ximalaya.ting.android.tv"),
                CourseItem(46, "得到", "知识音频课程", "pkg:com.luojilab.dedao"),
                CourseItem(47, "凯叔讲故事", "儿童有声内容", "pkg:com.kaishu.story"))),
            CategoryData(HeaderItem(12, "🧠 知识学习"), listOf(
                CourseItem(48, "有道词典", "英语翻译学习", "pkg:com.youdao.dict"),
                CourseItem(49, "百度翻译", "多语种翻译", "pkg:com.baidu.translate"),
                CourseItem(50, "全历史", "历史知识图谱", "pkg:com.allhistory.app"),
                CourseItem(51, "每日故宫", "故宫藏品欣赏", "pkg:cn.edu.dailypalace"))),
            CategoryData(HeaderItem(13, "🎨 创意启蒙"), listOf(
                CourseItem(52, "画吧", "手机绘画社区", "pkg:com.huaba.app"),
                CourseItem(53, "Simply Piano", "钢琴自学入门", "pkg:com.joytunes.simplypiano"),
                CourseItem(54, "贝乐虎儿歌", "儿童动画儿歌", "pkg:com.beilehu.erge"),
                CourseItem(55, "小火箭编程", "幼儿编程启蒙", "pkg:com.xiaohuojian.code"))),
            CategoryData(HeaderItem(14, "♟️ 益智休闲"), listOf(
                CourseItem(56, "中国象棋", "经典棋类对战", "pkg:com.cnvcs.xq"),
                CourseItem(57, "围棋", "人机 & 在线对弈", "pkg:com.tencent.wgo"),
                CourseItem(58, "数独", "逻辑推理训练", "pkg:com.sudoku.game"),
                CourseItem(59, "纪念碑谷", "视觉解谜艺术", "pkg:com.ustwo.monumentvalley"))),
            CategoryData(HeaderItem(15, "🛠 实用工具"), listOf(
                CourseItem(60, "文件管理器", "系统文件浏览", "pkg:com.android.documentsui"),
                CourseItem(61, "浏览器", "网页浏览", "pkg:com.android.chrome"),
                CourseItem(62, "计算器", "科学计算器", "pkg:com.android.calculator2"),
                CourseItem(63, "应用商店", "安装 & 更新应用", "pkg:com.android.vending")))
        )),
        TabData("⚙️ 设置", listOf(
            CategoryData(HeaderItem(20, "⚙️ 系统设置"), listOf(
                CourseItem(11, "系统设置", "Android TV 设置", "action:system_settings"))),
            CategoryData(HeaderItem(21, "🔒 家长设置"), listOf(
                CourseItem(12, "家长设置", "防沉迷 & PIN", "action:parent_settings")))
        ))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        GuardClientHolder.initialize(this)

        browseFragment = supportFragmentManager
            .findFragmentById(R.id.main_browse_fragment) as BrowseSupportFragment

        browseFragment.title = " PineCone 松果智学"
        browseFragment.headersState = BrowseSupportFragment.HEADERS_ENABLED
        browseFragment.isHeadersTransitionOnBackEnabled = true

        tabBar = findViewById(R.id.tab_bar)
        buildTabBar()
        switchTab(0)
    }

    private fun buildTabBar() {
        tabBar.removeAllViews()
        tabButtons.clear()

        tabs.forEachIndexed { index, tab ->
            val btn = Button(this).apply {
                text = tab.name
                textSize = 18f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#2A2A4A"))
                minHeight = 0
                minWidth = 0
                setPadding(28, 12, 28, 12)
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                ).apply { gravity = Gravity.CENTER }
                setOnClickListener {
                    switchTab(index)
                }
            }
            tabBar.addView(btn)
            tabButtons.add(btn)
        }
    }

    private fun switchTab(index: Int) {
        currentTab = index
        val tab = tabs[index]

        // Update tab button styles
        tabButtons.forEachIndexed { i, btn ->
            if (i == index) {
                btn.setTextColor(Color.parseColor("#FF4FC3F7"))
                btn.textSize = 20f
            } else {
                btn.setTextColor(Color.parseColor("#B0B0C0"))
                btn.textSize = 18f
            }
        }

        // Rebuild adapter for this tab
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        val cardPresenter = CourseCardPresenter()

        tab.categories.forEach { cat ->
            val rowAdapter = ArrayObjectAdapter(cardPresenter)
            cat.items.forEach { rowAdapter.add(it) }
            rowsAdapter.add(ListRow(cat.header, rowAdapter))
        }

        browseFragment.adapter = rowsAdapter

        // Click handler
        browseFragment.onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is CourseItem) {
                handleItemClick(item)
            }
        }
    }

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
