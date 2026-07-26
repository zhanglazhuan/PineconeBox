package com.pinecone.pinecone.ui.guard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.guard.service.GuardClientHolder
import com.pinecone.pinecone.ui.theme.*

@Composable
fun PinSetupScreen(isVerifyMode: Boolean = false, onComplete: (pin: String?) -> Unit) {
    val context = LocalContext.current
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var activeField by remember { mutableIntStateOf(0) }
    var keyboardMode by remember { mutableIntStateOf(0) }

    val keys: List<List<String>> = when (keyboardMode) {
        0 -> listOf(
            listOf("A","B","C","D","E","F","G"),
            listOf("H","I","J","K","L","M","N"),
            listOf("O","P","Q","R","S","T","U"),
            listOf("V","W","X","Y","Z")
        )
        1 -> listOf(
            listOf("1","2","3","4","5"),
            listOf("6","7","8","9","0")
        )
        else -> listOf(
            listOf("!","@","#","$","%","^"),
            listOf("&","*","(",")","-","_"),
            listOf("+","=","/","?",".",",")
        )
    }

    // Auto-check for verify mode
    LaunchedEffect(pin1) {
        if (isVerifyMode && pin1.length >= 6) {
            onComplete(pin1)
        }
    }

    // Auto-check for setup mode
    LaunchedEffect(pin1, pin2) {
        if (!isVerifyMode && pin1.length >= 6 && pin1 == pin2) {
            GuardClientHolder.updatePin(pin1)
            Toast.makeText(context, "PIN 设置成功", Toast.LENGTH_SHORT).show()
            onComplete(pin1)
        }
    }

    GuardSettingsScaffold(
        title = if (isVerifyMode) "验证 PIN" else "设置 PIN",
        onBack = { onComplete(null) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Field 1
            Text(
                text = "输入 PIN",
                fontSize = 18.sp,
                color = if (activeField == 0) Color.White else Color.Gray,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = dots(pin1),
                fontSize = 28.sp,
                color = if (activeField == 0) PineWarning else PineTextMuted,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clickable { activeField = 0 }
                    .padding(bottom = 4.dp)
            )

            // Field 2 (hidden in verify mode)
            if (!isVerifyMode) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "确认 PIN",
                    fontSize = 18.sp,
                    color = if (activeField == 1) Color.White else Color.Gray,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = dots(pin2),
                    fontSize = 28.sp,
                    color = if (activeField == 1) PineWarning else PineTextMuted,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable { activeField = 1 }
                        .padding(bottom = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Keyboard
            keys.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(56.dp)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(PineCardBorder)
                                .clickable {
                                    val sb = if (activeField == 0) pin1 else pin2
                                    if (sb.length < 20) {
                                        val newVal = sb + key
                                        if (activeField == 0) pin1 = newVal else pin2 = newVal
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = key, fontSize = 18.sp, color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mode switch row
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("ABC" to 0, "123" to 1, "#$!" to 2).forEach { (label, mode) ->
                    TextButton(onClick = { keyboardMode = mode }) {
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            color = if (keyboardMode == mode) PineWarning else Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                TextButton(onClick = {
                    val sb = if (activeField == 0) pin1 else pin2
                    if (activeField == 0) pin1 = "$sb " else pin2 = "$sb "
                }) { Text("空格", fontSize = 15.sp, color = Color.White) }
                TextButton(onClick = {
                    val sb = if (activeField == 0) pin1 else pin2
                    if (sb.isNotEmpty()) {
                        val newVal = sb.dropLast(1)
                        if (activeField == 0) pin1 = newVal else pin2 = newVal
                    }
                }) { Text("⌫", fontSize = 18.sp, color = Color.White) }
                TextButton(onClick = {
                    if (activeField == 0) pin1 = "" else pin2 = ""
                }) { Text("清空", fontSize = 15.sp, color = PineError) }
            }
        }
    }
}

private fun dots(value: String): String {
    if (value.isEmpty()) return "···"
    return "●".repeat(value.length)
}
