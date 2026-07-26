package com.pinecone.pinecone.ui.screen

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.pinecone.ui.theme.*

/**
 * Apple-style capsule tab bar.
 * - Floating capsule indicator slides between tabs
 * - No emoji, clean text labels
 * - Glass background with bottom border
 */
@Composable
fun HomeTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Strip emoji prefix for clean display
    val cleanTabs = tabs.map { it.trimStart { c -> !c.isLetterOrDigit() } }

    // Capsule offset animation
    val indicatorOffset by animateDpAsState(
        targetValue = (selectedIndex * 180).dp, // approximate per-tab width
        animationSpec = tweenOut(),
        label = "tabIndicator"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PineTopBar,
                        PineTopBar.copy(alpha = 0.5f)
                    )
                )
            )
    ) {
        // Bottom hairline
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(PineGlassBorder)
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            cleanTabs.forEachIndexed { index, name ->
                val isSelected = index == selectedIndex

                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .then(
                            if (isSelected) {
                                Modifier.background(
                                    Color.White.copy(alpha = 0.15f)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onTabSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name,
                        fontSize = 18.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) Color.White else PineTextSecondary
                    )
                }
            }
        }
    }
}
