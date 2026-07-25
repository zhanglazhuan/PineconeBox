package com.pinecone.pinecone.data

import android.content.Context
import org.json.JSONObject

data class CourseItem(val id: Long, val title: String, val category: String, val actionUrl: String)
data class CategoryData(val name: String, val items: List<CourseItem>)
data class TabData(val name: String, val categories: List<CategoryData>)

/**
 * Web resource data sourced from resource/web/ JSON files.
 * App data sourced from resource/apk/apps.json.
 */
object ResourceData {

    private var _id = 100L
    private fun id() = _id++

    private fun web(title: String, subtitle: String, url: String) =
        CourseItem(id(), title, subtitle, "action:web:$url")

    private fun pkg(title: String, subtitle: String, pkgName: String) =
        CourseItem(id(), title, subtitle, "pkg:$pkgName")

    private fun action(title: String, subtitle: String, action: String) =
        CourseItem(id(), title, subtitle, action)

    private fun placeholder(title: String, subtitle: String) =
        CourseItem(id(), title, subtitle, "action:placeholder")

    // ═══════════════════════════════════════════
    //  domestic.json  →  国字号官方免费学习网站
    // ═══════════════════════════════════════════

    private val domesticCategories = listOf(
        CategoryData("教育部｜国家智慧教育体系", listOf(
            web("国家智慧教育公共服务平台（总入口）", "国家级教育平台集群总站", "https://www.smartedu.cn"),
            web("国家中小学智慧教育平台", "小初高同步课程、电子教材、精品课", "https://basic.smartedu.cn"),
            web("国家智慧教育读书平台", "青少年电子书、名著导读、科普读物", "https://reading.smartedu.cn"),
            web("基础教育精品课专区", "部级获奖名师公开课，同步课本重难点", "https://jpk.basic.smartedu.cn/publicity"),
            web("全国中小学虚拟实验教学服务系统", "理化生科学线上虚拟仿真实验", "https://vlab.eduyun.cn"),
            web("中小学人工智能教育服务平台", "信息科技、编程、AI 科普课程", "https://ai.eduyun.cn")
        )),
        CategoryData("科普科学类", listOf(
            web("中国数字科技馆", "科学动画、3D虚拟科技馆、天文地理", "https://www.cdstm.cn"),
            web("中国科普网", "自然科学、航天、环境、应急科普", "https://www.kepu.gov.cn")
        )),
        CategoryData("文史、阅读、语言文字", listOf(
            web("国家图书馆·开放资源（国图）", "古籍、近现代图书、历史讲座", "https://open.nlc.cn"),
            web("中国语言文字数字博物馆", "汉字演变、成语、古诗文、普通话", "https://language.smartedu.cn")
        )),
        CategoryData("教材官方平台", listOf(
            web("人教网（人民教育出版社官网）", "电子课本、课文音频、配套学习素材", "https://www.pep.com.cn")
        )),
        CategoryData("名校公开课、人文拓展", listOf(
            web("央视网科教频道（CCTV-10）", "百家讲坛、地理中国、走近科学", "https://tv.cctv.com/cctv10/"),
            web("学习强国（教育强国板块）", "名校公开课、纪录片、人文历史、美育", "https://www.xuexi.cn"),
            web("国家数字图书馆公开课", "国图在线教育客户端", "https://open.nlc.cn/onlineedu/client/index.htm")
        )),
        CategoryData("高校慕课", listOf(
            web("国家高等教育智慧教育平台", "教育部官方大学公开课，数学物理历史生物", "https://higher.smartedu.cn"),
            web("中国大学 MOOC（高教司指导）", "国内顶尖高校免费公开课", "https://www.icourse163.org")
        )),
        CategoryData("德育、安全、素质教育", listOf(
            web("全国青少年普法网", "中小学生法治教育、宪法学习", "https://qspfw.moe.gov.cn"),
            web("中国教育电视台空中课堂", "官方电视同步课堂，各学段课程", "https://www.centv.cn"),
            placeholder("国家教育营", "综合实践、国防教育、劳动教育")
        ))
    )

    // ═══════════════════════════════════════════
    //  quality.json  →  非国字号优质学习网站
    // ═══════════════════════════════════════════

    private val qualityCategories = listOf(
        CategoryData("语文 / 古诗文 / 文字工具", listOf(
            web("古诗文网", "诗词文言文原文+注释+译文+赏析，课内全覆盖", "https://www.gushiwen.cn"),
            web("搜韵", "诗词检索、平仄检测、词谱韵书", "https://sou-yun.cn"),
            web("汉典", "汉字字典、字形演变、古音、说文解字", "https://www.zdic.net"),
            web("识典古籍", "数万部古籍原文+校注+书影，免费无广告", "https://www.shidianguji.com"),
            web("全历史", "时间地图、历史关系图谱、古迹人物史", "https://www.allhistory.com")
        )),
        CategoryData("理科 / 实验 / 数理可视化", listOf(
            web("PhET 模拟实验（中文版）", "诺贝尔奖项目，理化生数学互动仿真实验", "https://phet.colorado.edu/zh_CN"),
            web("洋葱学园（网页版）", "小初高数理化微课，动画讲解重难点", "https://yangcong345.com"),
            web("乐乐课堂", "短视频数理化知识点，节奏轻快", "https://www.leleketang.com"),
            web("Cool Math Games", "全球最受欢迎数学游戏站", "https://www.coolmathgames.com"),
            placeholder("Learn Everything 学习助手", "AI 驱动全科学习助手，数学解题、语法纠错、作文润色")
        )),
        CategoryData("英语学习", listOf(
            web("Vocabulary.com", "自适应背单词，例句地道", "https://www.vocabulary.com"),
            web("Quizlet", "在线自制单词闪卡，多种复习模式", "https://quizlet.com"),
            web("英语兔（B站配套工具）", "免费音标、语法系统教程", "https://www.yingyutu.com"),
            web("中华文化英语知识库", "用英语讲中国传统文化", "https://chinese-culture.net")
        )),
        CategoryData("题库 / 试卷 / 课后练习", listOf(
            web("菁优网", "全国真题、同步练习题、详细解析", "https://www.jyeoo.com"),
            web("第一试卷网", "中小学单元卷、期中期末试卷免费下载PDF", "https://www.shijuan1.com"),
            web("教研云", "菁优网旗下教师备课平台", "https://www.jiaoyanyun.com")
        )),
        CategoryData("编程 / 信息科技 / 科创", listOf(
            web("Scratch 官方", "MIT图形化编程，全球最大少儿编程社区", "https://scratch.mit.edu"),
            web("编程猫社区", "图形化编程、Python入门，中文友好", "https://coding.codemao.cn")
        )),
        CategoryData("通识科普 / 纪录片 / 课外阅读", listOf(
            web("纪录片天地", "海量纪录片索引，历史地理自然科学", "https://www.jlpcn.net"),
            web("National Geographic Kids", "英文科普图文，动物自然知识", "https://kids.nationalgeographic.com")
        )),
        CategoryData("思维导图 / 学习工具", listOf(
            web("ProcessOn", "在线思维导图、流程图，协作方便", "https://www.processon.com"),
            web("Canva 可画（教育版）", "手抄报、课件海报设计，教育版免费", "https://www.canva.cn/education")
        )),
        CategoryData("轻量无广告学生益智站点", listOf(
            web("Kiddie Worksheets", "免费可打印练习题，数学英语书写拼图PDF", "https://www.kiddoworksheets.com"),
            web("WithoutAD", "数独、逻辑游戏、成语接龙，零广告纯净站", "https://withoutad.com")
        ))
    )

    // ═══════════════════════════════════════════
    //  visual.json  →  可视化/交互式学习网站
    // ═══════════════════════════════════════════

    private val visualCategories = listOf(
        CategoryData("数理化交互式仿真", listOf(
            web("PhET 互动仿真实验室", "诺贝尔团队开发，拖动参数实时观察变化", "https://phet.colorado.edu/zh_CN"),
            web("GeoGebra", "动态几何全能工具，2D/3D几何函数图像", "https://www.geogebra.org"),
            web("Desmos 图形计算器", "界面极简，函数极坐标参数方程绘图", "https://www.desmos.com/calculator?lang=zh-CN"),
            web("网络画板", "张景中院士团队，百万份国内教师动态课件", "https://www.netpad.net.cn"),
            web("Mathigon", "最美交互式数学教材，Polypad虚拟教具", "https://mathigon.org")
        )),
        CategoryData("化学 / 微观结构可视化", listOf(
            web("Chemix", "在线绘制化学实验器材装置图", "https://chemix.org"),
            web("MolView", "输入化学式查看分子三维结构", "https://molview.org")
        )),
        CategoryData("地理 / 天文可视化", listOf(
            web("Solar System Scope", "3D太阳系实时模拟，行星轨道月相日食", "https://www.solarsystemscope.com")
        )),
        CategoryData("编程与信息科技可视化", listOf(
            web("Scratch 官方", "MIT图形化编程，逻辑流程可视化", "https://scratch.mit.edu")
        )),
        CategoryData("互动课程网站", listOf(
            web("洋葱学园（网页版）", "动画短视频讲解小初高数理化重难点", "https://yangcong345.com"),
            web("乐乐课堂", "超短动画微课，小学初中数理化生", "https://www.leleketang.com"),
            web("可汗学院（中文站）", "全球知名免费教育平台，动态示意图+互动练习", "https://zh.khanacademy.org"),
            web("全历史", "时间轴、历史地图、关系图谱", "https://www.allhistory.com")
        ))
    )

    // ═══════════════════════════════════════════
    //  western.json  →  欧美课标同步免费学习网站
    // ═══════════════════════════════════════════

    private val westernCategories = listOf(
        CategoryData("美国 K-12 同步课程（CCSS）", listOf(
            web("Khan Academy（可汗学院）", "非营利，完整覆盖K12全科，自适应练习", "https://www.khanacademy.org"),
            web("CK-12 Foundation", "免费电子教材FlexBook+微课+互动仿真", "https://www.ck12.org"),
            web("Easy Peasy All-in-One Homeschool", "学前到12年级全科免费美式课程体系", "https://allinonehomeschool.com"),
            web("TED-Ed", "短动画科普课程，自带配套思考题", "https://ed.ted.com"),
            web("Newsela", "同一篇文章切换5档蓝思难度，英文分级阅读", "https://newsela.com"),
            web("Illustrative Mathematics", "美国CCSS官方配套数学例题教案", "https://illustrativemathematics.org")
        )),
        CategoryData("英国体系（National Curriculum）", listOf(
            web("BBC Bitesize", "BBC官方，3-16岁全科，对标英格兰中小学课标", "https://www.bbc.co.uk/bitesize"),
            web("National Geographic Kids", "欧美小学标配科普，动物地理自然", "https://kids.nationalgeographic.com")
        )),
        CategoryData("欧盟体系（CEFR 欧标）", listOf(
            web("European School Education Platform", "欧盟委员会官方中小学教育总站", "https://school-education.ec.europa.eu/en"),
            web("European Youth Portal", "欧盟青少年门户，人文公民教育", "https://youth.europa.eu")
        )),
        CategoryData("通用理科可视化（欧美课堂标配）", listOf(
            web("PhET 仿真实验", "美国科罗拉多大学，全球中小学通用", "https://phet.colorado.edu/zh_CN"),
            web("GeoGebra", "欧洲开发，全球数学课堂标配", "https://www.geogebra.org"),
            web("Mathigon", "欧洲交互式数学教科书", "https://mathigon.org")
        ))
    )

    // ═══════════════════════════════════════════
    //  documentary.json  →  合规免费纪录片网站
    // ═══════════════════════════════════════════

    private val documentaryCategories = listOf(
        CategoryData("国内国家级官方站点", listOf(
            web("央视网纪实频道（CCTV官方）", "航拍中国、河西走廊、如果国宝会说话", "https://jishi.cctv.com"),
            web("中国纪录片网", "国家广电总局指导，优秀国产纪录片展播", "https://www.docuchina.cn"),
            web("央视频（网页版）", "总台全部纪录片资源同步，支持投屏", "https://www.yangshipin.cn"),
            web("学习强国（网页端）", "零广告，历史人文自然大国工程全覆盖", "https://www.xuexi.cn")
        )),
        CategoryData("国内正规平台纪录片专区", listOf(
            web("B站纪录片专区", "国产+BBC+国家地理+TED-Ed，资源最全", "https://www.bilibili.com/documentary"),
            web("1905电影网纪实板块", "电影频道官方，历史文献纪录片老科教片", "https://www.1905.com/documentary")
        )),
        CategoryData("海外免费教育向纪录片站", listOf(
            web("Internet Archive（互联网档案馆）", "非营利，公有领域经典纪录片免费在线播放", "https://archive.org"),
            web("PBS Video（美国公共广播）", "美国公立教育配套纪录片，自然历史科学人文", "https://video.pbs.org"),
            web("ARTE（法德公共电视台）", "欧洲高质量人文艺术自然纪录片", "https://www.arte.tv/en"),
            web("NFB（加拿大国家电影局）", "获奖动画纪录片科教短片，CC开放版权", "https://www.nfb.ca"),
            web("BBC Earth / Nat Geo / NASA", "YouTube官方频道，完整版科普纪录片", "https://www.youtube.com/@BBCEarth"),
            web("TED-Ed", "动画科教短片，自带配套思考题", "https://ed.ted.com")
        ))
    )

    // ═══════════════════════════════════════════
    //  ai.json  →  AI 通识学习网站
    // ═══════════════════════════════════════════

    private val aiCategories = listOf(
        CategoryData("AI 通识学习", listOf(
            web("Hour of AI", "AI 编程一小时，面向青少年的 AI 入门课程", "https://hourofcode.com/ai"),
            web("AI Quest", "AI 探究式学习项目", "https://www.aiquest.org"),
            placeholder("Open AI Mac", "开源 AI 学习资源合集")
        ))
    )

    // ═══════════════════════════════════════════
    //  Settings Tab
    // ═══════════════════════════════════════════

    private val settingsCategories = listOf(
        CategoryData("通用", listOf(
            action("系统设置", "WiFi、显示、存储等 Android 原生设置", "action:system_settings")
        )),
        CategoryData("防沉迷", listOf(
            action("每日总时长", "2 小时 30 分", "action:daily_limit"),
            action("强制休息间隔", "每 40 分 休 10 分", "action:break_rule"),
            action("可用时段", "周一至周五 16:00–21:00", "action:time_window"),
            action("App 限制", "限制指定 App 的使用时长", "action:app_limit"),
            action("浏览器限制", "是否允许自由输入网址", "action:browser_restrict"),
            action("信用积分", "100 分 / 每周一重置", "action:credit_config"),
            action("使用统计", "查看每日使用报告", "action:usage_history"),
            action("修改 PIN", "更换家长防沉迷密码", "action:pin_setup"),
            action("暂停防沉迷", "今天不限制", "action:pause_guard")
        )),
        CategoryData("关于", listOf(
            action("检查更新", "检查并安装新版本桌面", "action:check_update"),
            action("反馈", "提交 Bug 或功能需求", "action:feedback"),
            action("版本号", "v0.1.0-dev", "action:none"),
            action("平台", "Android TV · LineageOS 23.2 · Raspberry Pi 5", "action:none"),
            action("开源协议", "MIT License", "action:none"),
            action("资源声明", "所有资源版权归原作者 / 平台所有", "action:none")
        ))
    )

    // ═══════════════════════════════════════════
    //  Sidebar groups for "Web" tab
    // ═══════════════════════════════════════════

    data class SidebarGroup(val name: String, val categories: List<CategoryData>)

    val webGroups = listOf(
        SidebarGroup("🏛 国字号", domesticCategories),
        SidebarGroup("🌟 优质站", qualityCategories),
        SidebarGroup("🔬 可视化", visualCategories),
        SidebarGroup("🌍 欧美", westernCategories),
        SidebarGroup("🎬 纪录片", documentaryCategories),
        SidebarGroup("🤖 AI 通识", aiCategories)
    )

    val allWebCategories: List<CategoryData> = webGroups.flatMap { it.categories }

    // ═══════════════════════════════════════════
    //  App Tab — loaded from resource/apk/apps.json
    // ═══════════════════════════════════════════

    private var cachedAppCategories: List<CategoryData>? = null

    fun loadAppCategories(context: Context): List<CategoryData> {
        cachedAppCategories?.let { return it }

        val categories = mutableListOf<CategoryData>()
        try {
            val json = context.assets.open("apps.json")
                .bufferedReader().use { it.readText() }
            val root = JSONObject(json)

            for (key in root.keys()) {
                if (key == "精选推荐") continue

                val arr = root.optJSONArray(key) ?: continue
                val items = mutableListOf<CourseItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val name = obj.getString("name")
                    val desc = obj.getString("description")
                    val pkgname = obj.optString("pkgname", "")

                    val item = if (pkgname.isNotEmpty()) {
                        pkg(name, desc, pkgname)
                    } else {
                        placeholder(name, desc)
                    }
                    items.add(item)
                }
                if (items.isNotEmpty()) {
                    categories.add(CategoryData(key, items))
                }
            }
        } catch (_: Exception) { }

        cachedAppCategories = categories
        return categories
    }

    // ═══════════════════════════════════════════
    //  Tabs
    // ═══════════════════════════════════════════

    fun buildTabs(context: Context): List<TabData> = listOf(
        TabData("🌐 网站", allWebCategories),
        TabData("📱 App", loadAppCategories(context)),
        TabData("⚙️ 设置", settingsCategories)
    )
}
