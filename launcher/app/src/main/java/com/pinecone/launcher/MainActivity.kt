package com.pinecone.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import com.pinecone.guard.service.GuardClientHolder

// 课程卡片数据结构
data class CourseItem(val id: Long, val title: String, val category: String, val actionUrl: String)

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize anti-addiction guard system
        GuardClientHolder.initialize(this)

        val browseFragment = supportFragmentManager
            .findFragmentById(R.id.main_browse_fragment) as BrowseSupportFragment

        // 设置大屏标题与视觉属性
        browseFragment.title = " PineCone 松果智学"
        browseFragment.headersState = BrowseSupportFragment.HEADERS_ENABLED
        browseFragment.isHeadersTransitionOnBackEnabled = true

        // 加载顶部/侧边 Tab 与卡片数据
        setupUIElements(browseFragment)
    }

    private fun setupUIElements(fragment: BrowseSupportFragment) {
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        val cardPresenter = CourseCardPresenter()

        // 1. 🔤 英语专区
        val englishHeader = HeaderItem(0, "🔤 英语专区")
        val englishAdapter = ArrayObjectAdapter(cardPresenter).apply {
            add(CourseItem(1, "新概念英语 第一册", "英语", "pkg:com.example.english1"))
            add(CourseItem(2, "剑桥少儿英语", "英语", "pkg:com.example.cambridge"))
            add(CourseItem(3, "AI 语音口语伴学", "英语", "action:ai_talk"))
        }
        rowsAdapter.add(ListRow(englishHeader, englishAdapter))

        // 2. 📚 绘本阅读
        val readingHeader = HeaderItem(1, "📚 绘本阅读")
        val readingAdapter = ArrayObjectAdapter(cardPresenter).apply {
            add(CourseItem(4, "中国古代神话", "阅读", "action:read_1"))
            add(CourseItem(5, "少年科普百科", "阅读", "action:read_2"))
        }
        rowsAdapter.add(ListRow(readingHeader, readingAdapter))

        // 3. 🎬 纪录片
        val docHeader = HeaderItem(2, "🎬 纪录片")
        val docAdapter = ArrayObjectAdapter(cardPresenter).apply {
            add(CourseItem(6, "蓝色星球 4K", "纪录片", "action:doc_1"))
            add(CourseItem(7, "中国通史", "纪录片", "action:doc_2"))
        }
        rowsAdapter.add(ListRow(docHeader, docAdapter))

        // 4. 🧩 学习 App
        val appHeader = HeaderItem(3, "🧩 学习 App")
        val appAdapter = ArrayObjectAdapter(cardPresenter).apply {
            add(CourseItem(8, "网易有道词典", "App", "pkg:com.youdao.dict"))
            add(CourseItem(9, "哔哩哔哩动画 TV", "App", "pkg:tv.danmaku.bili"))
        }
        rowsAdapter.add(ListRow(appHeader, appAdapter))

        // 5. 🤖 AI 工具
        val aiHeader = HeaderItem(5, "🤖 AI 工具")
        val aiAdapter = ArrayObjectAdapter(cardPresenter).apply {
            add(CourseItem(20, "豆包", "字节跳动 AI 助手", "action:web:https://www.doubao.com"))
            add(CourseItem(21, "Kimi", "月之暗面 AI 对话", "action:web:https://kimi.moonshot.cn"))
            add(CourseItem(22, "通义千问", "阿里 AI 大模型", "action:web:https://tongyi.aliyun.com"))
            add(CourseItem(23, "文心一言", "百度 AI 助手", "action:web:https://yiyan.baidu.com"))
            add(CourseItem(24, "智谱清言", "ChatGLM 大模型", "action:web:https://chatglm.cn"))
        }
        rowsAdapter.add(ListRow(aiHeader, aiAdapter))

        // 6. 📚 在线学习
        val learnHeader = HeaderItem(6, "📚 在线学习")
        val learnAdapter = ArrayObjectAdapter(cardPresenter).apply {
            add(CourseItem(25, "国家中小学智慧教育", "官方学习平台", "action:web:https://www.smartedu.cn"))
            add(CourseItem(26, "学堂在线", "清华 MOOC 平台", "action:web:https://www.xuetangx.com"))
            add(CourseItem(27, "中国大学 MOOC", "高等教育课程", "action:web:https://www.icourse163.org"))
            add(CourseItem(28, "网易公开课", "国际名校课程", "action:web:https://open.163.com"))
            add(CourseItem(29, "多邻国", "免费学外语", "action:web:https://www.duolingo.com"))
        }
        rowsAdapter.add(ListRow(learnHeader, learnAdapter))

        // 7. 🎬 纪录片/视频
        val videoHeader = HeaderItem(7, "🎬 纪录片/视频")
        val videoAdapter = ArrayObjectAdapter(cardPresenter).apply {
            add(CourseItem(30, "B站知识区", "科普·人文·历史", "action:web:https://www.bilibili.com/v/knowledge"))
            add(CourseItem(31, "央视纪实", "CCTV 纪录片", "action:web:https://tv.cctv.com"))
            add(CourseItem(32, "中国纪录片网", "国产纪录片库", "action:web:https://www.docuchina.cn"))
        }
        rowsAdapter.add(ListRow(videoHeader, videoAdapter))

        // 8. 🧩 编程启蒙
        val codeHeader = HeaderItem(8, "🧩 编程启蒙")
        val codeAdapter = ArrayObjectAdapter(cardPresenter).apply {
            add(CourseItem(33, "Scratch", "MIT 可视化编程", "action:web:https://scratch.mit.edu"))
            add(CourseItem(34, "Code.org", "一小时编程入门", "action:web:https://code.org"))
            add(CourseItem(35, "Mind+", "国产 Scratch 平台", "action:web:https://mindplus.cc"))
        }
        rowsAdapter.add(ListRow(codeHeader, codeAdapter))

        // 9. 🔍 百科/工具
        val wikiHeader = HeaderItem(9, "🔍 百科/工具")
        val wikiAdapter = ArrayObjectAdapter(cardPresenter).apply {
            add(CourseItem(36, "维基百科", "自由百科全书", "action:web:https://www.wikipedia.org"))
            add(CourseItem(37, "古诗文网", "经典诗词文库", "action:web:https://www.gushiwen.cn"))
            add(CourseItem(38, "百度百科", "中文百科全书", "action:web:https://baike.baidu.com"))
        }
        rowsAdapter.add(ListRow(wikiHeader, wikiAdapter))

        // 10. ⚙️ 系统设置
        val settingsHeader = HeaderItem(10, "⚙️ 系统设置")
        val settingsAdapter = ArrayObjectAdapter(cardPresenter).apply {
            add(CourseItem(11, "系统设置", "设置", "action:system_settings"))
            add(CourseItem(12, "🔒 家长设置", "设置", "action:parent_settings"))
        }
        rowsAdapter.add(ListRow(settingsHeader, settingsAdapter))

        fragment.adapter = rowsAdapter

        // 遥控器确认键 / 鼠标点击监听
        fragment.onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is CourseItem) {
                when {
                    // 启动已安装应用
                    item.actionUrl.startsWith("pkg:") -> {
                        val pkgName = item.actionUrl.removePrefix("pkg:")
                        val intent = packageManager.getLaunchIntentForPackage(pkgName)
                        if (intent != null) {
                            startActivity(intent)
                        } else {
                            Toast.makeText(this, "未安装应用: $pkgName", Toast.LENGTH_SHORT).show()
                        }
                    }
                    // 打开网页资源（在浏览器中）
                    item.actionUrl.startsWith("action:web:") -> {
                        val url = item.actionUrl.removePrefix("action:web:")
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: Exception) {
                            Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                        }
                    }
                    // 系统设置（Android TV Leanback 设置）
                    item.actionUrl == "action:system_settings" -> {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                    // 家长设置入口
                    item.actionUrl == "action:parent_settings" -> {
                        startActivity(Intent(this, com.pinecone.launcher.ui.guard.ParentSettingsActivity::class.java))
                    }
                    // 其他课程入口
                    else -> {
                        Toast.makeText(this, "进入课程: ${item.title}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
