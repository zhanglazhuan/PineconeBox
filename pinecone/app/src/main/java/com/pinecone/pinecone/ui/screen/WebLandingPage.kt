package com.pinecone.pinecone.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.pinecone.data.CourseItem
import com.pinecone.pinecone.data.ResourceData
import com.pinecone.pinecone.log.*
import com.pinecone.pinecone.ui.theme.*

private const val CARDS_PER_ROW_LP = 4
private const val CARD_SPACING_LP = 16
private const val CONTENT_PADDING_LP = 32

/**
 * Apple-style landing page for the Web tab.
 * - Search bar with focus animation
 * - Staggered fade-in sections
 * - Clean typography, no emoji labels
 */
@Composable
fun WebLandingPage(
    recentlyBrowsed: List<CourseItem>,
    onItemClick: (CourseItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val contentWidthDp = screenWidthDp - 220 - 1 - CONTENT_PADDING_LP * 2
    val cardWidthDp = (contentWidthDp - CARD_SPACING_LP * (CARDS_PER_ROW_LP - 1)) / CARDS_PER_ROW_LP

    val recommended = remember {
        ResourceData.allWebCategories
            .flatMap { it.items }
            .shuffled()
            .take(10)
    }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                focusManager.clearFocus()
            }
            .padding(CONTENT_PADDING_LP.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ═══════════════════════════════════
        //  Search Box
        // ═══════════════════════════════════
        var searchText by remember { mutableStateOf("") }

        // Log search events (debounced: only log when ≥2 chars)
        LaunchedEffect(searchText) {
            if (searchText.length >= 2) {
                try {
                    PineconeLogger.log(SearchEvent(
                        System.currentTimeMillis(),
                        PineconeLogger.getSession()?.sessionId ?: "",
                        0, "网站"
                    ))
                } catch (_: Exception) {}
            }
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = {
                Text("搜索学习资源…", color = PineTextMuted)
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = PineTextPrimary,
                unfocusedTextColor = PineTextPrimary,
                cursorColor = PinePrimary,
                focusedBorderColor = PinePrimary,
                unfocusedBorderColor = PineGlassBorder,
                focusedContainerColor = PineSurface,
                unfocusedContainerColor = PineSurface
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .padding(bottom = 48.dp)
        )

        // ═══════════════════════════════════
        //  Recently Browsed
        // ═══════════════════════════════════
        if (recentlyBrowsed.isNotEmpty()) {
            SectionHeader(
                title = "最近浏览",
                alpha = rememberStaggerAlpha(0, isVisible)
            )

            Spacer(modifier = Modifier.height(16.dp))

            CardRow(
                items = recentlyBrowsed.take(10),
                cardWidthDp = cardWidthDp,
                startIndex = 1,
                isVisible = isVisible,
                onItemClick = onItemClick
            )

            Spacer(modifier = Modifier.height(48.dp))
        }

        // ═══════════════════════════════════
        //  Recommended
        // ═══════════════════════════════════
        SectionHeader(
            title = "为你推荐",
            alpha = rememberStaggerAlpha(if (recentlyBrowsed.isNotEmpty()) 1 else 0, isVisible)
        )

        Spacer(modifier = Modifier.height(16.dp))

        CardRow(
            items = recommended.take(10),
            cardWidthDp = cardWidthDp,
            startIndex = if (recentlyBrowsed.isNotEmpty()) 2 else 1,
            isVisible = isVisible,
            onItemClick = onItemClick
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(title: String, alpha: Float) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = PineTextPrimary.copy(alpha = alpha)
        )
    }
}

@Composable
private fun CardRow(
    items: List<CourseItem>,
    cardWidthDp: Int,
    startIndex: Int,
    isVisible: Boolean,
    onItemClick: (CourseItem) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CARD_SPACING_LP.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.forEachIndexed { idx, item ->
            val staggerAlpha = rememberStaggerAlpha(startIndex + idx, isVisible)
            Box(modifier = Modifier.graphicsLayer { alpha = staggerAlpha }) {
                ResourceCard(
                    item = item,
                    isHighlighted = false,
                    cardWidth = cardWidthDp.dp,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}
