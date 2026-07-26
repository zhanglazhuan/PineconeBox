package com.pinecone.pinecone.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.pinecone.data.CourseItem
import com.pinecone.pinecone.data.TabData
import com.pinecone.pinecone.ui.theme.PineTextPrimary
import com.pinecone.pinecone.ui.theme.rememberStaggerAlpha

private const val CARDS_PER_ROW = 4
private const val CARD_SPACING = 16
private const val CONTENT_PADDING = 16

@Composable
fun ContentGrid(
    tab: TabData,
    scrollToCategory: Int,
    onScrollDone: () -> Unit,
    onItemClick: (CourseItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val contentWidthDp = screenWidthDp - 220 - 1 - 32
    val cardWidthDp = (contentWidthDp - CARD_SPACING * (CARDS_PER_ROW - 1)) / CARDS_PER_ROW

    // Stagger animation trigger
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    LaunchedEffect(scrollToCategory) {
        if (scrollToCategory >= 0) {
            listState.animateScrollToItem(scrollToCategory * 2)
            onScrollDone()
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(CONTENT_PADDING.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        tab.categories.forEachIndexed { catIndex, cat ->
            item(key = "title_$catIndex") {
                val alpha = rememberStaggerAlpha(catIndex, isVisible)
                Text(
                    text = cat.name,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PineTextPrimary.copy(alpha = alpha),
                    modifier = Modifier.padding(
                        top = if (catIndex > 0) 8.dp else 0.dp,
                        bottom = 12.dp
                    )
                )
            }

            item(key = "grid_$catIndex") {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(CARDS_PER_ROW),
                    horizontalArrangement = Arrangement.spacedBy(CARD_SPACING.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    userScrollEnabled = false
                ) {
                    itemsIndexed(cat.items) { idx, item ->
                        ResourceCard(
                            item = item,
                            isHighlighted = false,
                            cardWidth = cardWidthDp.dp,
                            onClick = { onItemClick(item) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
