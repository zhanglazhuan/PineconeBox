package com.pinecone.pinecone.ui.guard.account

import android.util.Log
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.pinecone.ui.theme.*

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
    onEnter: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )

    // ── Debug: focus state tracking ──
    val focusRequester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        Log.d(TAG, "requestFocus() called, maxLength=$maxLength")
    }

    // ── Fallback: intercept key events at the Android View level ──
    // The IME may consume hardware key events before they reach Compose's
    // onPreviewKeyEvent. A View.OnKeyListener on the root view intercepts
    // ALL hardware keys regardless of Compose focus or IME state.
    //
    // rememberUpdatedState keeps the listener's closures pointing at the
    // latest value/onValueChange, avoiding stale-capture bugs.
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentMaxLength by rememberUpdatedState(maxLength)
    val currentOnEnter by rememberUpdatedState(onEnter)

    val view = LocalView.current
    DisposableEffect(Unit) {
        val listener = android.view.View.OnKeyListener { _, keyCode, event ->
            if (event.action == AndroidKeyEvent.ACTION_DOWN) {
                Log.d(TAG, "View.OnKeyListener: keyCode=$keyCode, keyChar='${event.unicodeChar.toChar()}'")
                val v = currentValue
                when (keyCode) {
                    AndroidKeyEvent.KEYCODE_DEL -> {
                        if (v.isNotEmpty()) { currentOnValueChange(v.dropLast(1)) }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_0 -> { if (v.length < currentMaxLength) currentOnValueChange(v + "0"); true }
                    AndroidKeyEvent.KEYCODE_1 -> { if (v.length < currentMaxLength) currentOnValueChange(v + "1"); true }
                    AndroidKeyEvent.KEYCODE_2 -> { if (v.length < currentMaxLength) currentOnValueChange(v + "2"); true }
                    AndroidKeyEvent.KEYCODE_3 -> { if (v.length < currentMaxLength) currentOnValueChange(v + "3"); true }
                    AndroidKeyEvent.KEYCODE_4 -> { if (v.length < currentMaxLength) currentOnValueChange(v + "4"); true }
                    AndroidKeyEvent.KEYCODE_5 -> { if (v.length < currentMaxLength) currentOnValueChange(v + "5"); true }
                    AndroidKeyEvent.KEYCODE_6 -> { if (v.length < currentMaxLength) currentOnValueChange(v + "6"); true }
                    AndroidKeyEvent.KEYCODE_7 -> { if (v.length < currentMaxLength) currentOnValueChange(v + "7"); true }
                    AndroidKeyEvent.KEYCODE_8 -> { if (v.length < currentMaxLength) currentOnValueChange(v + "8"); true }
                    AndroidKeyEvent.KEYCODE_9 -> { if (v.length < currentMaxLength) currentOnValueChange(v + "9"); true }
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        Log.d(TAG, "→ Enter pressed")
                        currentOnEnter?.invoke()
                        true
                    }
                    else -> false
                }
            } else false
        }
        view.setOnKeyListener(listener)
        Log.d(TAG, "View.OnKeyListener installed")
        onDispose {
            view.setOnKeyListener(null)
            Log.d(TAG, "View.OnKeyListener removed")
        }
    }

    Column(
        modifier = modifier
            .focusable()
            .focusRequester(focusRequester)
            .onFocusChanged { hasFocus = it.isFocused; Log.d(TAG, "focus changed: isFocused=${it.isFocused}") }
            .onPreviewKeyEvent { event ->
                Log.d(TAG, "onPreviewKeyEvent: type=${event.type}, key=${event.key}, nativeKeyCode=${event.nativeKeyEvent?.keyCode}")
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Backspace, Key.Delete -> {
                            Log.d(TAG, "→ Backspace")
                            if (value.isNotEmpty()) { onValueChange(value.dropLast(1)); true } else false
                        }
                        Key.Zero, Key.NumPad0 -> { Log.d(TAG, "→ 0"); if (value.length < maxLength) onValueChange(value + "0"); true }
                        Key.One,  Key.NumPad1 -> { Log.d(TAG, "→ 1"); if (value.length < maxLength) onValueChange(value + "1"); true }
                        Key.Two,  Key.NumPad2 -> { Log.d(TAG, "→ 2"); if (value.length < maxLength) onValueChange(value + "2"); true }
                        Key.Three,Key.NumPad3 -> { Log.d(TAG, "→ 3"); if (value.length < maxLength) onValueChange(value + "3"); true }
                        Key.Four, Key.NumPad4 -> { Log.d(TAG, "→ 4"); if (value.length < maxLength) onValueChange(value + "4"); true }
                        Key.Five, Key.NumPad5 -> { Log.d(TAG, "→ 5"); if (value.length < maxLength) onValueChange(value + "5"); true }
                        Key.Six,  Key.NumPad6 -> { Log.d(TAG, "→ 6"); if (value.length < maxLength) onValueChange(value + "6"); true }
                        Key.Seven,Key.NumPad7 -> { Log.d(TAG, "→ 7"); if (value.length < maxLength) onValueChange(value + "7"); true }
                        Key.Eight,Key.NumPad8 -> { Log.d(TAG, "→ 8"); if (value.length < maxLength) onValueChange(value + "8"); true }
                        Key.Nine, Key.NumPad9 -> { Log.d(TAG, "→ 9"); if (value.length < maxLength) onValueChange(value + "9"); true }
                        Key.Enter, Key.NumPadEnter -> {
                            Log.d(TAG, "→ Enter (Compose)")
                            onEnter?.invoke()
                            true
                        }
                        else -> {
                            Log.d(TAG, "→ unhandled key: ${event.key}")
                            false
                        }
                    }
                } else false
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                color = PineError,
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
            .background(PineCardBorder)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = key,
            fontSize = if (key == "⌫") 16.sp else 24.sp,
            fontWeight = if (key == "⌫") FontWeight.Normal else FontWeight.Bold,
            color = if (key == "⌫") PineError else Color.White
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
private const val TAG = "NumericKeyboard"
