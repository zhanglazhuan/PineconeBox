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

        // ── App 导航 ──

        // 10. 📺 视频影音
        val videoAppHeader = HeaderItem(10, "📺 视频影音")
        val videoAppAdapter = ArrayObjectAdapter(cardPresenter).apply {
            add(CourseItem(40, "B站 TV", "弹幕视频平台", "pkg:tv.danmaku.bili"))
            add(CourseItem(41, "央视频", "CCTV 官方视频", "pkg:com.cctv.yangshipin"))
            add(CourseItem(42, "芒果TV", "湖南卫视综艺", "pkg:com.hunantv.imgo.activity"))
            add(CourseItem(43, "西瓜视频", "短视频 & 影视", "pkg:com.ss.android.article.video"))
        }
        rowsAdapter.add(ListRow(videoAppHeader, videoAppAdapter))

        // 11. 📖 阅读听书
        val readAppHeader = HeaderItem(11, "📖 阅读听书")
        val readAppAdapter = ArrayObjectAdapter(cardPresenter).apply {
            add(CourseItem(44, "微信读书", "海量电子书", "pkg:com.tencent.weread"))
            add(CourseItem(45, "喜马拉雅", "有声书 & 播客", "pkg:com.ximalaya.ting.android.tv"))
            add(CourseItem(46, "得到", "知识音频课程", "pkg:com.luojilab.dedao"))
            add(CourseItem(47, "凯叔讲故事", "儿童有声内容", "pkg:com.kaishu.story"))
        }
        rowsAdapter.add(ListRow(readAppHeader, readAppAdapter))

        // 12. 🧠 知识学习
        val knowledgeAppHeader = HeaderItem(12, "🧠 知识学习")
        val knowledgeAppAdapter = ArrayObjectAdapter(cardPresenter).apply {
            add(CourseItem(48, "有道词典", "英语翻译学习", "pkg:com.youdao.dict"))
            add(CourseItem(49, "百度翻译", "多语种翻译", "pkg:com.baidu.translate"))
            add(CourseItem(50, "全历史", "历史知识图谱", "pkg:com.allhistory.app"))
            add(CourseItem(51, "每日故宫", "故宫藏品欣赏", "pkg:cn.edu.dailypalace"))
        }
        rowsAdapter.add(ListRow(knowledgeAppHeader, knowledgeAppAdapter))

        // 13. 🎨 创意启蒙
        val creativeAppHeader = HeaderItem(13, "🎨 创意启蒙")
        val creativeAppAdapter = ArrayObjectAdapter(cardPresenter).apply {
            add(CourseItem(52, "画吧", "手机绘画社区", "pkg:com.huaba.app"))
            add(CourseItem(53, "Simply Piano", "钢琴自学入门", "pkg:com.joytunes.simplypiano"))
            add(CourseItem(54, "贝乐虎儿歌", "儿童动画儿歌", "pkg:com.beilehu.erge"))
            add(CourseItem(55, "小火箭编程", "幼儿编程启蒙", "pkg:com.xiaohuojian.code"))
        }
        rowsAdapter.add(ListRow(creativeAppHeader, creativeAppAdapter))

        // 14. ♟️ 益智休闲
        val puzzleAppHeader = HeaderItem(14, "♟️ 益智休闲")
        val puzzleAppAdapter = ArrayObjectAdapter(cardPresenter).apply {
            add(CourseItem(56, "中国象棋", "经典棋类对战", "pkg:com.cnvcs.xq"))
            add(CourseItem(57, "围棋", "人机 & 在线对弈", "pkg:com.tencent.wgo"))
            add(CourseItem(58, "数独", "逻辑推理训练", "pkg:com.sudoku.game"))
            add(CourseItem(59, "纪念碑谷", "视觉解谜艺术", "pkg:com.ustwo.monumentvalley"))
        }
        rowsAdapter.add(ListRow(puzzleAppHeader, puzzleAppAdapter))

        // 15. 🛠 实用工具
        val toolsAppHeader = HeaderItem(15, "🛠 实用工具")
        val toolsAppAdapter = ArrayObjectAdapter(cardPresenter).apply {
            add(CourseItem(60, "文件管理器", "系统文件浏览", "pkg:com.android.documentsui"))
            add(CourseItem(61, "浏览器", "网页浏览", "pkg:com.android.chrome"))
            add(CourseItem(62, "计算器", "科学计算器", "pkg:com.android.calculator2"))
            add(CourseItem(63, "应用商店", "安装 & 更新应用", "pkg:com.android.vending"))
        }
        rowsAdapter.add(ListRow(toolsAppHeader, toolsAppAdapter))

        // 16. ⚙️ 系统设置
        val settingsHeader = HeaderItem(16, "⚙️ 系统设置")
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
