package com.pinecone.pinecone.data

import android.content.Context
import org.json.JSONObject
import java.net.URI

data class CourseItem(val id: Long, val title: String, val category: String, val actionUrl: String)
data class CategoryData(val name: String, val items: List<CourseItem>)
data class TabData(val name: String, val categories: List<CategoryData>)

/**
 * All resource data loaded at runtime from assets/ JSON files.
 *
 * Web data:  assets/web/ (all .json files)
 * App data:  assets/apps.json
 *
 * Single source of truth — no hardcoded URLs anywhere else.
 */
object ResourceData {

    private val idCounter = java.util.concurrent.atomic.AtomicLong(100)
    private fun id() = idCounter.getAndIncrement()

    private fun web(title: String, desc: String, url: String) =
        CourseItem(id(), title, desc, "action:web:$url")

    private fun pkg(title: String, desc: String, pkgName: String) =
        CourseItem(id(), title, desc, "pkg:$pkgName")

    private fun action(title: String, desc: String, action: String) =
        CourseItem(id(), title, desc, action)

    private fun placeholder(title: String, desc: String) =
        CourseItem(id(), title, desc, "action:placeholder")

    // ── Caches ──

    private var cachedWebGroups: List<SidebarGroup>? = null
    private var cachedAppCategories: List<CategoryData>? = null
    private var cachedWhitelist: Set<String>? = null
    /** Maps website URL → logo path (from JSON "logo" field, relative or absolute) */
    private val customLogoMap = mutableMapOf<String, String>()

    data class SubCategory(val name: String, val items: List<CourseItem>)
    data class SidebarGroup(val name: String, val subcategories: List<SubCategory>) {
        val allItems: List<CourseItem> get() = subcategories.flatMap { it.items }
    }

    // ---
    //  Web Tab — loaded from assets/web/categories.json
    // ---

    private fun loadWebCategories(context: Context): List<SidebarGroup> {
        cachedWebGroups?.let { return it }

        val groups = mutableListOf<SidebarGroup>()
        try {
            val files = context.assets.list("web") ?: arrayOf()
            for (fileName in files.sorted()) {
                if (!fileName.endsWith(".json")) continue
                try {
                    val json = context.assets.open("web/$fileName")
                        .bufferedReader().use { it.readText() }
                    val root = JSONObject(json)
                    val groupName = root.getString("name")
                    val subObj = root.optJSONObject("subcategories") ?: continue

                    val subcategories = mutableListOf<SubCategory>()
                    for (subKey in subObj.keys()) {
                        val subCatObj = subObj.getJSONObject(subKey)
                        val subName = subCatObj.getString("name")
                        val arr = subCatObj.optJSONArray("items") ?: continue

                        val items = mutableListOf<CourseItem>()
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            val name = item.getString("name")
                            val url = item.optString("url", "")
                            val logo = item.optString("logo", "")
                            if (url.isNotBlank()) {
                                val desc = item.optString("description", "")
                                items.add(web(name, desc, url))
                                if (logo.isNotBlank()) customLogoMap[url] = logo
                            } else {
                                items.add(placeholder(name, ""))
                            }
                        }
                        if (items.isNotEmpty()) subcategories.add(SubCategory(subName, items))
                    }
                    if (subcategories.isNotEmpty()) groups.add(SidebarGroup(groupName, subcategories))
                } catch (_: Exception) { /* skip bad file */ }
            }
        } catch (_: Exception) { /* assets/web/ missing */ }

        cachedWebGroups = groups
        return groups
    }

    val webGroups: List<SidebarGroup>
        get() = cachedWebGroups ?: error("ResourceData not initialized. Call buildTabs(context) first.")

    val allWebCategories: List<CategoryData>
        get() = webGroups.flatMap { g -> g.subcategories.map { CategoryData(it.name, it.items) } }

    // ---
    //  App Tab — loaded from assets/apps.json
    // ---

    fun loadAppCategories(context: Context): List<CategoryData> {
        cachedAppCategories?.let { return it }
        // Force-load app groups
        loadAppGroups(context)
        return cachedAppCategories!!
    }

    private var cachedAppGroups: List<SidebarGroup>? = null
    val appGroups: List<SidebarGroup>
        get() = cachedAppGroups ?: error("ResourceData not initialized.")

    private fun loadAppGroups(context: Context) {
        cachedAppGroups?.let { return }

        val groups = mutableListOf<SidebarGroup>()
        val flatCategories = mutableListOf<CategoryData>()

        try {
            val files = context.assets.list("app") ?: arrayOf()
            for (fileName in files.sorted()) {
                if (!fileName.endsWith(".json")) continue
                try {
                    val json = context.assets.open("app/$fileName")
                        .bufferedReader().use { it.readText() }
                    val root = JSONObject(json)
                    val groupName = root.getString("name")
                    val subObj = root.optJSONObject("subcategories") ?: continue

                    val subcategories = mutableListOf<SubCategory>()
                    for (subKey in subObj.keys()) {
                        val subCatObj = subObj.getJSONObject(subKey)
                        val subName = subCatObj.getString("name")
                        val arr = subCatObj.optJSONArray("items") ?: continue

                        val items = mutableListOf<CourseItem>()
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            val name = item.getString("name")
                            val desc = item.optString("description", "")
                            val pkgname = item.optString("pkgname", "")
                            val itm = if (pkgname.isNotEmpty()) pkg(name, desc, pkgname)
                            else placeholder(name, desc)
                            items.add(itm)
                        }
                        if (items.isNotEmpty()) {
                            subcategories.add(SubCategory(subName, items))
                            flatCategories.add(CategoryData(subName, items))
                        }
                    }
                    if (subcategories.isNotEmpty()) groups.add(SidebarGroup(groupName, subcategories))
                } catch (_: Exception) { /* skip bad file */ }
            }
        } catch (_: Exception) { /* assets/app/ missing, try legacy */ }

        // Fallback: legacy apps.json
        if (groups.isEmpty()) {
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
                        val desc = obj.optString("description", "")
                        val pkgname = obj.optString("pkgname", "")
                        val itm = if (pkgname.isNotEmpty()) pkg(name, desc, pkgname)
                        else placeholder(name, desc)
                        items.add(itm)
                    }
                    if (items.isNotEmpty()) flatCategories.add(CategoryData(key, items))
                }
                if (flatCategories.isNotEmpty()) {
                    groups.add(SidebarGroup("推荐应用", flatCategories.map { SubCategory(it.name, it.items) }))
                }
            } catch (_: Exception) {}
        }

        cachedAppGroups = groups
        cachedAppCategories = flatCategories
    }

    // ---
    //  Settings Tab — hardcoded (no external data)
    // ---

    val settingsCategories: List<CategoryData>
        get() = _settingsCategories

    private val _settingsCategories: List<CategoryData> = listOf(
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

    // ---
    //  Tabs
    // ---

    fun buildTabs(context: Context): List<TabData> {
        loadWebCategories(context)
        loadAppGroups(context)
        buildWhitelist(context)
        return listOf(
            TabData("🌐 网站", allWebCategories),
            TabData("📱 App", allAppCategories),
            TabData("⚙️ 设置", settingsCategories)
        )
    }

    val allAppCategories: List<CategoryData>
        get() = appGroups.flatMap { g -> g.subcategories.map { CategoryData(it.name, it.items) } }

    // ---
    //  Domain Whitelist — auto-extracted from data
    // ---

    /** Base URL for logo images hosted on our own CDN/OSS. Change this when migrating. */
    var logoBaseUrl: String = "https://api.pineconeos.com/static/"

    /** Returns the set of allowed domains extracted from all configured resources. */
    fun getDomainWhitelist(): Set<String> {
        val wl = cachedWhitelist
        if (wl == null || wl.isEmpty()) {
            android.util.Log.w("ResourceData", "Whitelist empty — did buildTabs() run? Allowing all.")
            // Fallback: allow common educational domains to prevent blocking
            return setOf("about:blank")
        }
        return wl
    }

    private fun buildWhitelist(context: Context) {
        val domains = mutableSetOf<String>()

        // 1. Extract from all web/*.json files (groups → subcategories → items)
        try {
            val files = context.assets.list("web") ?: arrayOf()
            for (fileName in files) {
                if (!fileName.endsWith(".json")) continue
                try {
                    val json = context.assets.open("web/$fileName")
                        .bufferedReader().use { it.readText() }
                    val root = JSONObject(json)
                    val subObj = root.optJSONObject("subcategories") ?: continue
                    for (subKey in subObj.keys()) {
                        val arr = subObj.getJSONObject(subKey).optJSONArray("items") ?: continue
                        for (i in 0 until arr.length()) {
                            val url = arr.getJSONObject(i).optString("url", "")
                            url.toDomain()?.let { domains.add(it) }
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // 2. Extract from app JSON files (groups → subcategories → items)
        try {
            val appFiles = context.assets.list("app") ?: arrayOf()
            for (fileName in appFiles) {
                if (!fileName.endsWith(".json")) continue
                try {
                    val json = context.assets.open("app/$fileName")
                        .bufferedReader().use { it.readText() }
                    val root = JSONObject(json)
                    val subObj = root.optJSONObject("subcategories") ?: continue
                    for (subKey in subObj.keys()) {
                        val arr = subObj.getJSONObject(subKey).optJSONArray("items") ?: continue
                        for (i in 0 until arr.length()) {
                            val site = arr.getJSONObject(i).optString("site", "")
                            site.toDomain()?.let { domains.add(it) }
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // 2b. Legacy apps.json fallback
        try {
            val appJson = context.assets.open("apps.json")
                .bufferedReader().use { it.readText() }
            val root = JSONObject(appJson)
            for (key in root.keys()) {
                val arr = root.optJSONArray(key) ?: continue
                for (i in 0 until arr.length()) {
                    val site = arr.getJSONObject(i).optString("site", "")
                    site.toDomain()?.let { domains.add(it) }
                }
            }
        } catch (_: Exception) {}

        // 3. about:blank and system essentials
        domains.add("about:blank")

        cachedWhitelist = domains
    }

    /** Resolve the best available logo URL: JSON "logo" > Google favicon fallback. */
    fun logoUrl(websiteUrl: String): String {
        // 1. Custom logo from JSON (relative path → prepend base URL)
        customLogoMap[websiteUrl]?.let { path ->
            return if (path.startsWith("http")) path else logoBaseUrl.trimEnd('/') + "/" + path.trimStart('/')
        }
        // 2. Domain-level match
        val domain = websiteUrl.toDomain() ?: return googleFavicon(websiteUrl)
        for ((key, value) in customLogoMap) {
            if (key.toDomain() == domain) {
                return if (value.startsWith("http")) value else logoBaseUrl.trimEnd('/') + "/" + value.trimStart('/')
            }
        }
        // 3. Google favicon fallback
        return googleFavicon(websiteUrl)
    }

    private fun googleFavicon(websiteUrl: String): String {
        val domain = websiteUrl.toDomain() ?: return ""
        return "https://www.google.com/s2/favicons?domain=$domain&sz=128"
    }

    /** Extract the bare domain from a URL, stripping www. prefix. */
    private fun String.toDomain(): String? {
        if (isBlank()) return null
        try {
            return URI(this).host?.removePrefix("www.")?.lowercase()
        } catch (_: Exception) {
            // Fallback for malformed URLs
            val start = this.indexOf("://")
            if (start < 0) return null
            val hostPart = this.substring(start + 3)
            val end = hostPart.indexOf('/')
            val host = if (end > 0) hostPart.substring(0, end) else hostPart
            return host.removePrefix("www.").lowercase()
        }
    }
}
