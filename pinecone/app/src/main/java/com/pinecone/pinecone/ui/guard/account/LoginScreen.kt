package com.pinecone.pinecone.ui.guard.account

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.pinecone.data.AccountStorage
import com.pinecone.pinecone.data.AuthResult
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val storage = remember { AccountStorage.getInstance(context) }

    // If not registered, go to register
    if (!storage.isRegistered) {
        context.startActivity(Intent(context, RegisterActivity::class.java))
        onLoginSuccess()
        return
    }

    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var isLockedOut by remember { mutableStateOf(false) }
    var lockoutRemaining by remember { mutableLongStateOf(0L) }
    var remainingAttempts by remember { mutableIntStateOf(MAX_ATTEMPTS) }

    // Check initial lockout state
    LaunchedEffect(Unit) {
        val remaining = storage.getLockoutRemaining()
        if (remaining > 0) {
            isLockedOut = true
            lockoutRemaining = remaining
        }
    }

    // Countdown timer for lockout
    LaunchedEffect(isLockedOut) {
        if (isLockedOut && lockoutRemaining > 0) {
            while (lockoutRemaining > 0) {
                delay(1000)
                lockoutRemaining--
            }
            isLockedOut = false
            remainingAttempts = MAX_ATTEMPTS
            password = ""
            errorMsg = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "输入家长密码",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Text(
            "账号: ${storage.maskedPhone}",
            fontSize = 13.sp,
            color = Color(0xFF8888AA),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (isLockedOut) {
            // Lockout state
            Text(
                "🔒",
                fontSize = 56.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                "已锁定",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF6B6B),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "连续 3 次密码错误",
                fontSize = 14.sp,
                color = Color(0xFF8888AA),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                formatLockoutTime(lockoutRemaining),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4FC3F7),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        } else {
            // Password input
            Text(
                if (remainingAttempts < MAX_ATTEMPTS) "密码错误，还剩 ${remainingAttempts} 次机会"
                else "6 位数字密码",
                fontSize = 14.sp,
                color = if (remainingAttempts < MAX_ATTEMPTS) Color(0xFFFF6B6B) else Color(0xFFB0B0C0),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            NumericKeyboard(
                value = password,
                onValueChange = { password = it },
                maxLength = 6
            )

            // Auto-verify when 6 digits entered
            LaunchedEffect(password) {
                if (password.length == 6) {
                    val result = storage.verifyPassword(password)
                    when (result) {
                        is AuthResult.Success -> {
                            onLoginSuccess()
                        }
                        is AuthResult.WrongPassword -> {
                            errorMsg = "密码错误"
                            remainingAttempts = result.remainingAttempts
                            password = ""
                        }
                        is AuthResult.LockedOut -> {
                            isLockedOut = true
                            lockoutRemaining = result.remainingSeconds
                            password = ""
                            errorMsg = ""
                        }
                        is AuthResult.Error -> {
                            errorMsg = result.message
                            password = ""
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (errorMsg.isNotEmpty()) {
            Text(errorMsg, fontSize = 14.sp, color = Color(0xFFFF6B6B))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Forgot password button — always visible
        TextButton(
            onClick = {
                context.startActivity(Intent(context, PasswordRecoveryActivity::class.java))
            }
        ) {
            Text("忘记密码？", fontSize = 15.sp, color = Color(0xFF4FC3F7))
        }
    }
}

private fun formatLockoutTime(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d : %02d".format(minutes, seconds)
}

private const val MAX_ATTEMPTS = 3
