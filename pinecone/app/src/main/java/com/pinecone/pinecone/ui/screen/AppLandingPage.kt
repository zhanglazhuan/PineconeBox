package com.pinecone.pinecone.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.pinecone.data.CourseItem
import com.pinecone.pinecone.data.ResourceData
import com.pinecone.pinecone.log.*
import com.pinecone.pinecone.ui.theme.*

private const val CARDS_PER_ROW = 4
private const val CARD_SPACING = 16
private const val PADDING = 32

@Composable
fun AppLandingPage(
    recentlyLaunched: List<CourseItem>,
    onItemClick: (CourseItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val contentWidthDp = screenWidthDp - 220 - 1 - PADDING * 2
    val cardWidthDp = (contentWidthDp - CARD_SPACING * (CARDS_PER_ROW - 1)) / CARDS_PER_ROW

    val recommended = remember {
        ResourceData.allAppCategories
            .flatMap { it.items }
            .filter { it.actionUrl.startsWith("pkg:") }
            .shuffled()
            .take(8)
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
            .padding(PADDING.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Search Box
        var searchText by remember { mutableStateOf("") }

        LaunchedEffect(searchText) {
            if (searchText.length >= 2) {
                try {
                    PineconeLogger.log(SearchEvent(
                        System.currentTimeMillis(), PineconeLogger.getSession()?.sessionId ?: "",
                        0, "App"
                    ))
                } catch (_: Exception) {}
            }
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = { Text("搜索应用…", color = PineTextMuted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = PineTextPrimary, unfocusedTextColor = PineTextPrimary,
                cursorColor = PinePrimary, focusedBorderColor = PinePrimary,
                unfocusedBorderColor = PineGlassBorder,
                focusedContainerColor = PineSurface, unfocusedContainerColor = PineSurface
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(0.6f).padding(bottom = 48.dp)
        )

        // Recently Launched
        if (recentlyLaunched.isNotEmpty()) {
            Text(
                text = "最近使用",
                fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                color = PineTextPrimary.copy(alpha = rememberStaggerAlpha(0, isVisible)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )
            CardRow(recentlyLaunched.take(8), cardWidthDp, 1, isVisible, onItemClick)
            Spacer(modifier = Modifier.height(48.dp))
        }

        // Recommended
        Text(
            text = "推荐应用",
            fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
            color = PineTextPrimary.copy(alpha = rememberStaggerAlpha(if (recentlyLaunched.isNotEmpty()) 1 else 0, isVisible)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )
        CardRow(recommended, cardWidthDp, if (recentlyLaunched.isNotEmpty()) 2 else 1, isVisible, onItemClick)
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun CardRow(
    items: List<CourseItem>, cardWidthDp: Int,
    startIndex: Int, isVisible: Boolean,
    onItemClick: (CourseItem) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CARD_SPACING.dp)
    ) {
        items.forEachIndexed { idx, item ->
            val a = rememberStaggerAlpha(startIndex + idx, isVisible)
            Box(modifier = Modifier.graphicsLayer(a).padding(bottom = 12.dp)) {
                ResourceCard(
                    item = item, isHighlighted = false,
                    cardWidth = Dp(cardWidthDp.toFloat()),
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}
