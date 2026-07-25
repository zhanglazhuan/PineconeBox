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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.pinecone.data.CategoryData
import com.pinecone.pinecone.data.ResourceData
import com.pinecone.pinecone.data.TabData
import com.pinecone.pinecone.ui.theme.PineAccent
import com.pinecone.pinecone.ui.theme.PineHighlight
import com.pinecone.pinecone.ui.theme.PineSidebar

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
        modifier = modifier.background(PineSidebar).padding(vertical = 8.dp)
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
        // ── 首页 (always visible, returns to landing page) ──
        Text(
            text = "🏠 首页",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (isHomeActive) PineAccent else Color(0xFFE0E0E0),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .then(if (isHomeActive) Modifier.background(PineHighlight) else Modifier)
                .clickable { onCategorySelect(-1) }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )

        var globalCatIdx = 0
        groups.forEachIndexed { gi, group ->
            val isExpanded = expandedGroup == gi
            val arrow = if (isExpanded) "▾" else "▸"

            // Level 1: Group header
            Text(
                text = "$arrow ${group.name}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF8AB4F8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onGroupToggle(gi) }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )

            // Level 2: Sub-categories (visible when expanded)
            if (isExpanded) {
                group.categories.forEachIndexed { ci, cat ->
                    val catAbsoluteIdx = globalCatIdx + ci
                    val isHighlighted = catAbsoluteIdx == highlightedCategory

                    Text(
                        text = "    ${cat.name}",
                        fontSize = 14.sp,
                        color = if (isHighlighted) PineAccent else Color(0xFFD0D0E0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .then(
                                if (isHighlighted) Modifier.background(PineHighlight)
                                else Modifier
                            )
                            .clickable { onCategorySelect(catAbsoluteIdx) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
            globalCatIdx += group.categories.size
        }
    }
}

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

            Text(
                text = cat.name,
                fontSize = 16.sp,
                color = if (isHighlighted) PineAccent else Color(0xFFD0D0E0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .then(
                        if (isHighlighted) Modifier.background(PineHighlight)
                        else Modifier
                    )
                    .clickable { onCategorySelect(index) }
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }
    }
}
