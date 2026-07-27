package com.pinecone.pinecone.ui.screen

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pinecone.pinecone.data.CourseItem
import com.pinecone.pinecone.data.FaviconCache
import com.pinecone.pinecone.data.ResourceData
import com.pinecone.pinecone.log.*
import com.pinecone.pinecone.ui.WebViewActivity
import com.pinecone.pinecone.ui.theme.*

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val tabs = remember { ResourceData.buildTabs(context) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var expandedGroup by remember { mutableIntStateOf(-1) }
    var highlightedCategory by remember { mutableIntStateOf(-1) }
    var scrollToCategory by remember { mutableIntStateOf(-1) }

    // Recently browsed (Web) / launched (App) items, max 10
    val recentlyBrowsed = remember { mutableStateListOf<CourseItem>() }
    val recentlyLaunched = remember { mutableStateListOf<CourseItem>() }

    val currentTab = tabs[selectedTab]
    val isWebTab = selectedTab == 0
    val isAppTab = selectedTab == 1
    val isSettingsTab = selectedTab == 2

    // Auto-select first category when entering Settings tab with none highlighted
    if (isSettingsTab && highlightedCategory == -1) {
        highlightedCategory = 0
    }

    fun onItemClick(item: CourseItem) {
        // Log item click
        try {
            PineconeLogger.log(ItemClickEvent(
                System.currentTimeMillis(),
                PineconeLogger.getSession()?.sessionId ?: "",
                item.id, item.category, tabs[selectedTab].name, -1
            ))
            PineconeLogger.getSession()?.recordClick(item.category)
        } catch (_: Exception) {}

        if (item.actionUrl.startsWith("action:web:")) {
            // Refresh favicon on click (background, won't block UI)
            val url = item.actionUrl.removePrefix("action:web:")
            FaviconCache.refresh(url)
            recentlyBrowsed.removeAll { it.id == item.id }
            recentlyBrowsed.add(0, item)
            if (recentlyBrowsed.size > 10) recentlyBrowsed.removeAt(recentlyBrowsed.lastIndex)
        }
        if (item.actionUrl.startsWith("pkg:")) {
            recentlyLaunched.removeAll { it.id == item.id }
            recentlyLaunched.add(0, item)
            if (recentlyLaunched.size > 10) recentlyLaunched.removeAt(recentlyLaunched.lastIndex)
        }
        handleItemClick(context, item)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PineBackground)
            .statusBarsPadding()
    ) {
        // ── Tab Bar ──
        HomeTabRow(
            tabs = tabs.map { it.name },
            selectedIndex = selectedTab,
            onTabSelected = { index ->
                val oldTab = tabs[selectedTab].name
                selectedTab = index
                expandedGroup = if (index == 0) -1 else expandedGroup
                highlightedCategory = -1
                try {
                    PineconeLogger.getSession()?.recordTabSwitch(oldTab, tabs[index].name)
                    PineconeLogger.log(TabSwitchEvent(
                        System.currentTimeMillis(),
                        PineconeLogger.getSession()?.sessionId ?: "",
                        oldTab, tabs[index].name, -1L
                    ))
                } catch (_: Exception) {}
            }
        )

        // ── Hairline divider ──
        HorizontalDivider(
            color = PineGlassBorder,
            thickness = 0.5.dp
        )

        // ── Sidebar + Content ──
        Row(modifier = Modifier.fillMaxSize()) {
            val sidebarGroups = when {
                isWebTab -> ResourceData.webGroups
                isSettingsTab -> emptyList()
                else -> ResourceData.appGroups
            }
            SidebarPanel(
                tab = currentTab,
                groups = sidebarGroups,
                useGroups = !isSettingsTab,
                expandedGroup = expandedGroup,
                highlightedCategory = highlightedCategory,
                onGroupToggle = { group ->
                    expandedGroup = if (expandedGroup == group) -1 else group
                },
                onCategorySelect = { catIndex ->
                    highlightedCategory = catIndex
                    scrollToCategory = catIndex
                    try {
                        val gs = if (isWebTab) ResourceData.webGroups else ResourceData.appGroups
                        var accumulated = 0
                        var groupName = ""
                        for (g in gs) {
                            if (catIndex < accumulated + g.subcategories.size) { groupName = g.name; break }
                            accumulated += g.subcategories.size
                        }
                        val catName = if (catIndex >= 0) currentTab.categories.getOrNull(catIndex)?.name ?: "" else "首页"
                        PineconeLogger.log(SidebarSelectEvent(
                            System.currentTimeMillis(), PineconeLogger.getSession()?.sessionId ?: "",
                            catName, groupName, if (catIndex == -1) 0 else 2
                        ))
                    } catch (_: Exception) {}
                },
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(PineSidebar)
            )

            // ── Subtle separator ──
            Box(
                modifier = Modifier
                    .width(0.5.dp)
                    .fillMaxHeight()
                    .background(PineGlassBorder)
            )

            // ── Content area with crossfade transition ──
            val contentKey = "$selectedTab-$highlightedCategory"

            AppleCrossfade(
                targetState = contentKey,
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                // Read guard version inside content lambda to trigger recomposition
                // when user returns from guard editor activities with new rules
                @Suppress("UNUSED_VARIABLE")
                val guardVer = com.pinecone.guard.service.GuardClientHolder.rulesVersion

                when {
                    isWebTab && highlightedCategory == -1 -> {
                        WebLandingPage(
                            recentlyBrowsed = recentlyBrowsed.toList(),
                            onItemClick = { onItemClick(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    isAppTab && highlightedCategory == -1 -> {
                        AppLandingPage(
                            recentlyLaunched = recentlyLaunched.toList(),
                            onItemClick = { onItemClick(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    isSettingsTab && highlightedCategory >= 0 -> {
                        val cat = currentTab.categories.getOrNull(highlightedCategory)
                        if (cat != null) {
                            SettingsList(
                                category = cat,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    else -> {
                        ContentGrid(
                            tab = currentTab,
                            scrollToCategory = scrollToCategory,
                            onScrollDone = { scrollToCategory = -1 },
                            onItemClick = { onItemClick(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

private fun handleItemClick(context: android.content.Context, item: CourseItem) {
    when {
        item.actionUrl.startsWith("pkg:") -> {
            val pkgName = item.actionUrl.removePrefix("pkg:")
            try {
                PineconeLogger.log(AppLaunchEvent(
                    System.currentTimeMillis(),
                    PineconeLogger.getSession()?.sessionId ?: "",
                    pkgName, "桌面卡片"
                ))
                PineconeLogger.getSession()?.recordAppLaunch()
            } catch (_: Exception) {}
            val intent = context.packageManager.getLaunchIntentForPackage(pkgName)
            if (intent != null) {
                context.startActivity(intent)
            } else {
                com.pinecone.pinecone.util.AppStoreHelper.installByPackage(context, pkgName, item.title)
            }
        }
        item.actionUrl.startsWith("action:web:") -> {
            val url = item.actionUrl.removePrefix("action:web:")
            context.startActivity(Intent(context, WebViewActivity::class.java).putExtra("url", url))
        }
        item.actionUrl == "action:system_settings" ->
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        item.actionUrl.startsWith("action:download:") -> {
            val payload = item.actionUrl.removePrefix("action:download:")
            val parts = payload.split("|")
            val url = parts[0]
            val filename = parts.getOrElse(1) { "app.apk" }
            com.pinecone.pinecone.util.ApkDownloader.download(context, url, filename, item.title)
        }
        item.actionUrl == "action:placeholder" ->
            com.pinecone.pinecone.util.AppStoreHelper.searchByName(context, item.title)
        else ->
            Toast.makeText(context, "进入课程: ${item.title}", Toast.LENGTH_SHORT).show()
    }
}
