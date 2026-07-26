package com.pinecone.pinecone.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.pinecone.data.CategoryData
import com.pinecone.pinecone.data.ResourceData
import com.pinecone.pinecone.data.TabData
import com.pinecone.pinecone.ui.theme.*

/**
 * Apple-style sidebar panel with:
 * - Clean hierarchy (no emoji, colored dot indicators)
 * - Animated expand/collapse for groups
 * - Vertical accent bar on active item
 * - Glass background
 */
@Composable
fun SidebarPanel(
    tab: TabData,
    isWebTab: Boolean,
    expandedGroup: Int,
    highlightedCategory: Int,
    onGroupToggle: (Int) -> Unit,
    onCategorySelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        PineSidebar,
                        PineBackground
                    )
                )
            )
            .padding(vertical = 12.dp)
    ) {
        if (isWebTab) {
            WebSidebar(
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

// ═══════════════════════════════════════════
//  Web Sidebar (grouped: L1 groups → L2 categories)
// ═══════════════════════════════════════════

@Composable
private fun WebSidebar(
    expandedGroup: Int,
    highlightedCategory: Int,
    onGroupToggle: (Int) -> Unit,
    onCategorySelect: (Int) -> Unit
) {
    val groups = ResourceData.webGroups
    val scrollState = rememberScrollState()
    val isHomeActive = highlightedCategory == -1

    Column(modifier = Modifier.verticalScroll(scrollState)) {
        // ── Home (always top) ──
        SidebarHomeItem(
            isActive = isHomeActive,
            onClick = { onCategorySelect(-1) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Section divider ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(0.5.dp)
                .background(PineGlassBorder)
        )

        Spacer(modifier = Modifier.height(8.dp))

        var globalCatIdx = 0
        groups.forEachIndexed { gi, group ->
            val isExpanded = expandedGroup == gi

            // L1: Group header
            SidebarGroupHeader(
                name = cleanGroupName(group.name),
                isExpanded = isExpanded,
                onClick = { onGroupToggle(gi) }
            )

            // L2: Categories (animated expand/collapse)
            if (isExpanded) {
                group.categories.forEachIndexed { ci, cat ->
                    val catAbsoluteIdx = globalCatIdx + ci
                    val isHighlighted = catAbsoluteIdx == highlightedCategory

                    SidebarCategoryItem(
                        name = cat.name,
                        isHighlighted = isHighlighted,
                        onClick = { onCategorySelect(catAbsoluteIdx) }
                    )
                }
            }
            globalCatIdx += group.categories.size
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ═══════════════════════════════════════════
//  Flat Sidebar (App / Settings tabs)
// ═══════════════════════════════════════════

@Composable
private fun FlatSidebar(
    categories: List<CategoryData>,
    highlightedCategory: Int,
    onCategorySelect: (Int) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.verticalScroll(scrollState)) {
        categories.forEachIndexed { index, cat ->
            val isHighlighted = index == highlightedCategory
            SidebarCategoryItem(
                name = cat.name,
                isHighlighted = isHighlighted,
                onClick = { onCategorySelect(index) }
            )
        }
    }
}

// ═══════════════════════════════════════════
//  Individual item composables
// ═══════════════════════════════════════════

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
        // Accent indicator bar
        if (isActive) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PinePrimary)
            )
            Spacer(modifier = Modifier.width(12.dp))
        } else {
            Spacer(modifier = Modifier.width(15.dp))
        }

        Text(
            text = "首页",
            fontSize = 16.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive) PineTextPrimary else PineTextSecondary
        )
    }
}

@Composable
private fun SidebarGroupHeader(
    name: String,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        // Expand indicator
        Text(
            text = if (isExpanded) "▾" else "▸",
            fontSize = 11.sp,
            color = PineTextMuted,
            modifier = Modifier.width(14.dp)
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = PineTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SidebarCategoryItem(
    name: String,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .then(
                if (isHighlighted) Modifier.background(PineHighlight)
                else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        // Accent bar
        if (isHighlighted) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PinePrimary)
            )
            Spacer(modifier = Modifier.width(10.dp))
        } else {
            Spacer(modifier = Modifier.width(13.dp))
        }

        Text(
            text = name,
            fontSize = 14.sp,
            fontWeight = if (isHighlighted) FontWeight.Medium else FontWeight.Normal,
            color = if (isHighlighted) PineTextPrimary else PineTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ═══════════════════════════════════════════
//  Helper: strip emoji from group names
// ═══════════════════════════════════════════

private fun cleanGroupName(name: String): String {
    // Remove leading emoji sequences (e.g. "🏛 国字号" → "国字号")
    return name.replace(Regex("^[\\p{So}\\p{Sk}]+\\s*"), "")
        .replace(Regex("^[\\uD800-\\uDBFF][\\uDC00-\\uDFFF]\\s*"), "")
        .replace(Regex("^[\\u2600-\\u27BF]\\s*"), "")
        .trimStart()
}
