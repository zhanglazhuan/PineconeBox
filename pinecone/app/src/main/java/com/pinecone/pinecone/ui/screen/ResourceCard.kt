package com.pinecone.pinecone.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.pinecone.data.CourseItem
import com.pinecone.pinecone.ui.theme.PineAccent
import com.pinecone.pinecone.ui.theme.PineSurface

@Composable
fun ResourceCard(
    item: CourseItem,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    cardWidth: Dp = 160.dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(cardWidth)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isHighlighted) {
                    Modifier
                        .background(Color(0xFF3A6A9A))
                        .border(2.dp, PineAccent, RoundedCornerShape(12.dp))
                } else {
                    Modifier.background(PineSurface)
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF3A3A6A)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.title.first().toString(),
                fontSize = 22.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = item.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = item.category,
            fontSize = 11.sp,
            color = Color(0xFFB0B0C0),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
