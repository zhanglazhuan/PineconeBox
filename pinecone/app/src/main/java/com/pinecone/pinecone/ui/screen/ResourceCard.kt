package com.pinecone.pinecone.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.pinecone.data.CourseItem
import com.pinecone.pinecone.ui.theme.*

/**
 * Apple-style card with:
 * - Category-color gradient header area
 * - Glass base with subtle border
 * - Focus animation (scale + shadow + glow ring)
 * - Clean typography hierarchy
 */
@Composable
fun ResourceCard(
    item: CourseItem,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    cardWidth: Dp = 160.dp,
    modifier: Modifier = Modifier
) {
    // Focus animation values
    val targetScale = if (isHighlighted) SCALE_FOCUS else SCALE_REST
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tweenOut(),
        label = "cardScale"
    )
    val shadowDp by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isHighlighted) SHADOW_FOCUS else SHADOW_REST,
        animationSpec = tweenOut(),
        label = "cardShadow"
    )

    val categoryColor = categoryGradientFor(item.category)

    Column(
        modifier = modifier
            .width(cardWidth)
            .scale(animatedScale)
            .shadow(
                elevation = shadowDp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = PineFocusRing.copy(alpha = 0.15f),
                spotColor = Color.Black.copy(alpha = 0.3f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(PineSurface)
            .border(
                width = if (isHighlighted) 2.dp else 1.dp,
                color = if (isHighlighted) PineFocusRing.copy(alpha = 0.6f) else PineGlassBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Header: gradient cover image area ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((cardWidth.value * 0.6f).dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            categoryColor,
                            categoryColor.copy(alpha = 0.6f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Category icon: first character as large glyph
            Text(
                text = item.title.first().toString(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
        }

        // ── Body: text area ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = item.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = PineTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.category,
                fontSize = 13.sp,
                color = PineTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  Category → color mapping for gradient header
// ═══════════════════════════════════════════════════════════

private fun categoryGradientFor(category: String): Color {
    val key = category.lowercase()
    return when {
        key.contains("数学") || key.contains("理科") || key.contains("编程") -> PineCatBlue
        key.contains("生物") || key.contains("自然") || key.contains("地理") || key.contains("天文") -> PineCatGreen
        key.contains("语文") || key.contains("历史") || key.contains("古文") || key.contains("文字") || key.contains("英语") -> PineCatPurple
        key.contains("纪录") || key.contains("探索") || key.contains("科普") -> PineCatOrange
        key.contains("ai") || key.contains("科技") || key.contains("科创") || key.contains("实验") -> PineCatPink
        key.contains("教育") || key.contains("教材") || key.contains("课程") || key.contains("欧美") -> PineCatTeal
        key.contains("安全") || key.contains("德育") || key.contains("心理") -> PineSecondary
        key.contains("高校") || key.contains("慕课") || key.contains("公开课") -> PinePrimary
        key.contains("防治") || key.contains("设置") || key.contains("通用") || key.contains("关于") -> Color(0xFF636366)
        else -> PineCatDefault
    }
}
