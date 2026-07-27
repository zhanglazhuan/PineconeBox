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
import androidx.compose.ui.platform.LocalContext
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
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val contentWidthDp = screenWidthDp - 220 - 1 - 32
    val cardWidthDp = (contentWidthDp - CARD_SPACING * (CARDS_PER_ROW - 1)) / CARDS_PER_ROW

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    // Long-press menu state
    var menuItem by remember { mutableStateOf<CourseItem?>(null) }
    // Rating dialog state
    var ratingItem by remember { mutableStateOf<CourseItem?>(null) }
    // Delete confirm state
    var deleteItem by remember { mutableStateOf<CourseItem?>(null) }
    // Deleted items set (in-memory for current session)
    var deletedIds by remember { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(scrollToCategory) {
        if (scrollToCategory >= 0) {
            listState.animateScrollToItem(scrollToCategory * 2)
            onScrollDone()
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(CONTENT_PADDING.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            tab.categories.forEachIndexed { catIndex, cat ->
                // Filter out deleted items
                val visibleItems = cat.items.filter { it.id !in deletedIds && !CardPrefs.isDeleted(ctx, it.id) }
                if (visibleItems.isEmpty()) return@forEachIndexed

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
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 1200.dp),
                        userScrollEnabled = false
                    ) {
                        itemsIndexed(visibleItems) { _, item ->
                            ResourceCard(
                                item = item,
                                isHighlighted = false,
                                cardWidth = cardWidthDp.dp,
                                onClick = { onItemClick(item) },
                                onLongClick = { menuItem = item }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // ── Context menu ──
        menuItem?.let { item ->
            CardContextMenu(
                onDismiss = { menuItem = null },
                onFavorite = {
                    val fav = !CardPrefs.isFavorite(ctx, item.id)
                    CardPrefs.setFavorite(ctx, item.id, fav)
                    menuItem = null
                },
                onRate = {
                    menuItem = null
                    ratingItem = item
                },
                onDelete = {
                    menuItem = null
                    deleteItem = item
                },
                isFav = CardPrefs.isFavorite(ctx, item.id)
            )
        }
    }

    // ── Rating dialog ──
    ratingItem?.let { RatingDialog(item = it, onDismiss = { ratingItem = null }) }

    // ── Delete confirmation ──
    deleteItem?.let { item ->
        DeleteConfirmDialog(
            item = item,
            onDismiss = { deleteItem = null },
            onConfirm = {
                deletedIds = deletedIds + item.id
                deleteItem = null
            }
        )
    }
}
