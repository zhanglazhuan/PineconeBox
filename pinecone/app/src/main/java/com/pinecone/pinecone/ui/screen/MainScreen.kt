package com.pinecone.pinecone.ui.screen

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pinecone.pinecone.data.CourseItem
import com.pinecone.pinecone.data.ResourceData
import com.pinecone.pinecone.ui.WebViewActivity
import com.pinecone.pinecone.ui.theme.PineBackground

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val tabs = remember { ResourceData.buildTabs(context) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var expandedGroup by remember { mutableIntStateOf(-1) }
    var highlightedCategory by remember { mutableIntStateOf(-1) }
    var scrollToCategory by remember { mutableIntStateOf(-1) }

    // Recently browsed items (Web tab only, max 10, most recent first)
    val recentlyBrowsed = remember { mutableStateListOf<CourseItem>() }

    val currentTab = tabs[selectedTab]
    val isWebTab = selectedTab == 0
    val isSettingsTab = selectedTab == 2

    // Auto-select first category when entering Settings tab with none highlighted
    if (isSettingsTab && highlightedCategory == -1) {
        highlightedCategory = 0
    }

    fun onItemClick(item: CourseItem) {
        // Track web items in recently browsed
        if (item.actionUrl.startsWith("action:web:")) {
            recentlyBrowsed.removeAll { it.id == item.id }
            recentlyBrowsed.add(0, item)
            if (recentlyBrowsed.size > 10) {
                recentlyBrowsed.removeAt(recentlyBrowsed.lastIndex)
            }
        }
        handleItemClick(context, item)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PineBackground)
            .statusBarsPadding()
    ) {
        HomeTabRow(
            tabs = tabs.map { it.name },
            selectedIndex = selectedTab,
            onTabSelected = { index ->
                selectedTab = index
                expandedGroup = if (index == 0) -1 else expandedGroup
                highlightedCategory = -1
            }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 2.dp)

        Row(modifier = Modifier.fillMaxSize()) {
            SidebarPanel(
                tab = currentTab,
                isWebTab = isWebTab,
                expandedGroup = expandedGroup,
                highlightedCategory = highlightedCategory,
                onGroupToggle = { group ->
                    expandedGroup = if (expandedGroup == group) -1 else group
                },
                onCategorySelect = { catIndex ->
                    highlightedCategory = catIndex
                    scrollToCategory = catIndex
                },
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
            )

            VerticalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

            // Content area
            when {
                isWebTab && highlightedCategory == -1 -> {
                    WebLandingPage(
                        recentlyBrowsed = recentlyBrowsed.toList(),
                        onItemClick = { onItemClick(it) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                isSettingsTab && highlightedCategory >= 0 -> {
                    val cat = currentTab.categories.getOrNull(highlightedCategory)
                    if (cat != null) {
                        SettingsList(
                            category = cat,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
                else -> {
                    ContentGrid(
                        tab = currentTab,
                        scrollToCategory = scrollToCategory,
                        onScrollDone = { scrollToCategory = -1 },
                        onItemClick = { onItemClick(it) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

private fun handleItemClick(context: android.content.Context, item: CourseItem) {
    when {
        item.actionUrl.startsWith("pkg:") -> {
            val pkgName = item.actionUrl.removePrefix("pkg:")
            val intent = context.packageManager.getLaunchIntentForPackage(pkgName)
            if (intent != null) {
                context.startActivity(intent)
            } else {
                // Not installed → open app store
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
