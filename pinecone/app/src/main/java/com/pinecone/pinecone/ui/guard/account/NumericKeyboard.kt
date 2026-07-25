package com.pinecone.pinecone.ui.guard.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A simple numeric on-screen keyboard for TV remote control.
 * Supports 0-9 digits plus backspace/clear.
 */
@Composable
fun NumericKeyboard(
    value: String,
    onValueChange: (String) -> Unit,
    maxLength: Int = 6,
    showDots: Boolean = true,
    modifier: Modifier = Modifier
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Display value or dots
        Text(
            text = if (showDots) dots(value, maxLength) else value.ifEmpty { "—".repeat(maxLength) },
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            letterSpacing = if (showDots) 8.sp else 4.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Key rows
        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(modifier = Modifier.width(KEY_WIDTH.dp).height(KEY_HEIGHT.dp))
                    } else {
                        KeyButton(key) {
                            when (key) {
                                "⌫" -> {
                                    if (value.isNotEmpty()) {
                                        onValueChange(value.dropLast(1))
                                    }
                                }
                                else -> {
                                    if (value.length < maxLength) {
                                        onValueChange(value + key)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Clear button — compact, right-aligned
        Row(modifier = Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
            Text(
                text = "清空",
                fontSize = 14.sp,
                color = Color(0xFFFF6666),
                modifier = Modifier
                    .clickable { onValueChange("") }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun KeyButton(key: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(KEY_WIDTH.dp)
            .height(KEY_HEIGHT.dp)
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2A2A4A))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = key,
            fontSize = if (key == "⌫") 16.sp else 24.sp,
            fontWeight = if (key == "⌫") FontWeight.Normal else FontWeight.Bold,
            color = if (key == "⌫") Color(0xFFFF8A80) else Color.White
        )
    }
}

fun dots(value: String, maxLength: Int = 6): String {
    if (value.isEmpty()) return "○".repeat(maxLength)
    return buildString {
        repeat(value.length) { append("●") }
        repeat(maxLength - value.length) { append("○") }
    }
}

private val KEY_WIDTH = 56
private val KEY_HEIGHT = 40
