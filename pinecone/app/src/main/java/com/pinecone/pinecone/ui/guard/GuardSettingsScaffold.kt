package com.pinecone.pinecone.ui.guard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.guard.service.GuardClientHolder
import com.pinecone.pinecone.ui.theme.*

@Composable
fun GuardSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    onEnter: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    // Pause guard tick accumulation while on settings/editor pages
    DisposableEffect(Unit) {
        GuardClientHolder.enterSettings()
        onDispose { GuardClientHolder.leaveSettings() }
    }

    // Intercept Enter key at the scaffold level — the common ancestor of ALL
    // focusable elements on the screen. This guarantees Enter is handled
    // regardless of which child (back button, key button, etc.) has focus.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PineBackground)
            .statusBarsPadding()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && onEnter != null) {
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            onEnter.invoke()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PineGhostButton(
                label = "← 返回",
                onClick = onBack
            )
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(80.dp))
        }

        HorizontalDivider(color = PineCardBorder, thickness = 1.dp)

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
