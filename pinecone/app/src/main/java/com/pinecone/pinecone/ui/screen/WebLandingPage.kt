package com.pinecone.pinecone.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.pinecone.data.CourseItem
import com.pinecone.pinecone.data.ResourceData
import com.pinecone.pinecone.ui.theme.PineAccent
import com.pinecone.pinecone.ui.theme.PineSurface

private const val CARDS_PER_ROW_LP = 4
private const val CARD_SPACING_LP = 16
private const val CONTENT_PADDING_LP = 32

@Composable
fun WebLandingPage(
    recentlyBrowsed: List<CourseItem>,
    onItemClick: (CourseItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    // Content area width = screen - sidebar(220) - divider(1) - padding(32*2)
    val contentWidthDp = screenWidthDp - 220 - 1 - CONTENT_PADDING_LP * 2
    val cardWidthDp = (contentWidthDp - CARD_SPACING_LP * (CARDS_PER_ROW_LP - 1)) / CARDS_PER_ROW_LP

    val recommended = remember {
        ResourceData.allWebCategories
            .flatMap { it.items }
            .take(10)
    }

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(CONTENT_PADDING_LP.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ═══════════════════════════════════
        //  Search Box
        // ═══════════════════════════════════
        var searchText by remember { mutableStateOf("") }

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = {
                Text("搜索学习资源…", color = Color(0xFF888888))
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = PineAccent,
                focusedBorderColor = PineAccent,
                unfocusedBorderColor = Color(0xFF3A3A5A),
                focusedContainerColor = PineSurface,
                unfocusedContainerColor = PineSurface
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .padding(bottom = 48.dp)
        )

        // ═══════════════════════════════════
        //  Recently Browsed
        // ═══════════════════════════════════
        if (recentlyBrowsed.isNotEmpty()) {
            Text(
                text = "🕐 最近浏览",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            CardRow(
                items = recentlyBrowsed.take(10),
                cardWidthDp = cardWidthDp,
                onItemClick = onItemClick
            )

            Spacer(modifier = Modifier.height(48.dp))
        }

        // ═══════════════════════════════════
        //  Recommended
        // ═══════════════════════════════════
        Text(
            text = "✨ 为你推荐",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        CardRow(
            items = recommended.take(10),
            cardWidthDp = cardWidthDp,
            onItemClick = onItemClick
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun CardRow(
    items: List<CourseItem>,
    cardWidthDp: Int,
    onItemClick: (CourseItem) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CARD_SPACING_LP.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.forEach { item ->
            ResourceCard(
                item = item,
                isHighlighted = false,
                cardWidth = cardWidthDp.dp,
                onClick = { onItemClick(item) }
            )
        }
    }
}
