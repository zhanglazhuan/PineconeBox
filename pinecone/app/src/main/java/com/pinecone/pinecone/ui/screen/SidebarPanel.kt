package com.pinecone.pinecone.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.pinecone.data.CategoryData
import com.pinecone.pinecone.data.ResourceData
import com.pinecone.pinecone.data.TabData
import com.pinecone.pinecone.ui.theme.*

@Composable
fun SidebarPanel(
    tab: TabData,
    groups: List<ResourceData.SidebarGroup>,
    useGroups: Boolean,
    expandedGroup: Int,
    highlightedCategory: Int,
    onGroupToggle: (Int) -> Unit,
    onCategorySelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Brush.horizontalGradient(colors = listOf(PineSidebar, PineBackground)))
            .padding(vertical = 12.dp)
    ) {
        if (useGroups) {
            GroupedSidebar(
                groups = groups,
                expandedGroup = expandedGroup,
                highlightedCategory = highlightedCategory,
                onGroupToggle = onGroupToggle,
                onCategorySelect = onCategorySelect
            )
        } else {
            FlatSidebar(
                categories = tab.categories,
                highlightedCategory = highlightedCategory,
                onCategorySelect = onCategorySelect
            )
        }
    }
}

// --- Grouped Sidebar (L1 groups -> L2 subcategories, used by Web & App tabs) ---

@Composable
private fun GroupedSidebar(
    groups: List<ResourceData.SidebarGroup>,
    expandedGroup: Int,
    highlightedCategory: Int,
    onGroupToggle: (Int) -> Unit,
    onCategorySelect: (Int) -> Unit
) {
    val scrollState = rememberScrollState()

    // Treat Home and L1 groups as a single radio group: expandedGroup = -1 means Home
    val isHomeActive = expandedGroup == -1

    Column(modifier = Modifier.verticalScroll(scrollState)) {
        SidebarHomeItem(
            isActive = isHomeActive,
            onClick = {
                onGroupToggle(-1)
                onCategorySelect(-1)
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(0.5.dp).background(PineGlassBorder))
        Spacer(modifier = Modifier.height(8.dp))

        var globalIdx = 0
        groups.forEachIndexed { gi, group ->
            val isGroupActive = expandedGroup == gi

            SidebarGroupHeader(
                name = group.name,
                isExpanded = isGroupActive,
                isActive = isGroupActive,
                onClick = { onGroupToggle(gi) }
            )
            if (isGroupActive) {
                group.subcategories.forEachIndexed { si, sub ->
                    val idx = globalIdx + si
                    SidebarCategoryItem(
                        name = sub.name,
                        isHighlighted = idx == highlightedCategory,
                        onClick = { onCategorySelect(idx) }
                    )
                }
            }
            globalIdx += group.subcategories.size
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// --- Flat Sidebar (App / Settings tabs) ---

@Composable
private fun FlatSidebar(
    categories: List<CategoryData>,
    highlightedCategory: Int,
    onCategorySelect: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.verticalScroll(scrollState)) {
        categories.forEachIndexed { index, cat ->
            SidebarCategoryItem(
                name = cat.name,
                isHighlighted = index == highlightedCategory,
                onClick = { onCategorySelect(index) }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  Individual item composables
//
//  X-axis alignment:
//    Home:  3dp(bar) + 25dp(gap) = 28dp
//    L1:   12dp(gap) + 16dp(arrow) = 28dp → text aligns with Home
//    L2:   44dp indent → clearly subordinate
// ═══════════════════════════════════════════════════════════

@Composable
private fun SidebarHomeItem(isActive: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        if (isActive) {
            Box(
                modifier = Modifier.width(3.dp).height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PinePrimary)
            )
            Spacer(modifier = Modifier.width(25.dp))
        } else {
            Spacer(modifier = Modifier.width(28.dp))
        }
        Text(
            text = "首页",
            fontSize = 16.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isActive) PineTextPrimary else PineTextSecondary
        )
    }
}

@Composable
private fun SidebarGroupHeader(
    name: String,
    isExpanded: Boolean,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        if (isActive) {
            Box(
                modifier = Modifier.width(3.dp).height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PinePrimary)
            )
            Spacer(modifier = Modifier.width(9.dp))
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = if (isExpanded) "▾" else "▸",
            fontSize = 12.sp,
            color = PinePrimary.copy(alpha = 0.6f),
            modifier = Modifier.width(16.dp)
        )
        Text(
            text = name,
            fontSize = 16.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isActive) PineTextPrimary else PineTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SidebarCategoryItem(name: String, isHighlighted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(6.dp))
            .then(if (isHighlighted) Modifier.background(PineHighlight) else Modifier)
            .clickable { onClick() }
            .padding(start = 44.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = name,
            fontSize = 14.sp,
            fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isHighlighted) PinePrimary else PineTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
