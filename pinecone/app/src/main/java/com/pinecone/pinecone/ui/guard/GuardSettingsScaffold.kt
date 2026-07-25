package com.pinecone.pinecone.ui.guard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.pinecone.ui.theme.PineAccent
import com.pinecone.pinecone.ui.theme.PineBackground

@Composable
fun GuardSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PineBackground)
            .statusBarsPadding()
    ) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← 返回", fontSize = 18.sp, color = Color.White)
            }
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            // Spacer to balance the back button
            Spacer(modifier = Modifier.width(80.dp))
        }

        HorizontalDivider(color = Color(0xFF2A2A4A), thickness = 1.dp)

        // Content area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            content()
        }
    }
}
