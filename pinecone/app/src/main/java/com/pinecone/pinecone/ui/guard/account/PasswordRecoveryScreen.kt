package com.pinecone.pinecone.ui.guard.account

import android.widget.Toast
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
import com.pinecone.pinecone.network.DevSmsService
import com.pinecone.pinecone.ui.guard.GuardSettingsScaffold
import kotlinx.coroutines.launch

@Composable
fun PasswordRecoveryScreen(onComplete: (success: Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val smsService = remember { DevSmsService() }
    val storage = remember { AccountStorage.getInstance(context) }

    // Mode: sms or recovery_code
    var recoveryMode by remember { mutableIntStateOf(0) } // 0=sms, 1=recovery code

    // SMS mode
    var smsCode by remember { mutableStateOf("") }
    var sentCode by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    // Recovery code mode
    var recoveryCodeInput by remember { mutableStateOf("") }

    // New password (both modes)
    var showPasswordStep by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var codeVerified by remember { mutableStateOf(false) }

    // Countdown timer
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
        }
    }

    GuardSettingsScaffold(
        title = "找回密码",
        onBack = { onComplete(false) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!showPasswordStep) {
                // ── Verification step ──
                Text("找回密码", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("账号: ${storage.maskedPhone}", fontSize = 14.sp, color = Color(0xFF8888AA),
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))

                // Mode toggle
                if (recoveryMode == 0) {
                    // SMS mode
                    // Auto-send
                    LaunchedEffect(Unit) {
                        isLoading = true
                        val result = smsService.sendVerificationCode(storage.phoneNumber ?: "")
                        if (result.isSuccess) {
                            sentCode = result.getOrDefault("")
                            Toast.makeText(context, "验证码: $sentCode（开发模式）", Toast.LENGTH_LONG).show()
                            countdown = 60
                        } else {
                            errorMsg = result.exceptionOrNull()?.message ?: "发送失败"
                        }
                        isLoading = false
                    }

                    Text("短信验证码已发送至注册手机号", fontSize = 14.sp, color = Color(0xFFB0B0C0),
                        modifier = Modifier.padding(bottom = 16.dp))

                    NumericKeyboard(value = smsCode, onValueChange = { smsCode = it }, maxLength = 6)

                    Spacer(modifier = Modifier.height(20.dp))

                    if (errorMsg.isNotEmpty()) {
                        Text(errorMsg, fontSize = 14.sp, color = Color(0xFFFF6B6B), modifier = Modifier.padding(bottom = 8.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    val result = smsService.sendVerificationCode(storage.phoneNumber ?: "")
                                    if (result.isSuccess) {
                                        sentCode = result.getOrDefault("")
                                        Toast.makeText(context, "验证码: $sentCode", Toast.LENGTH_LONG).show()
                                        countdown = 60
                                        errorMsg = ""
                                    } else {
                                        errorMsg = result.exceptionOrNull()?.message ?: "发送失败"
                                    }
                                    isLoading = false
                                }
                            },
                            enabled = countdown == 0 && !isLoading
                        ) {
                            Text(if (countdown > 0) "${countdown}s" else "重发")
                        }

                        Button(
                            onClick = {
                                val verified = smsService.verifyCode(storage.phoneNumber ?: "", smsCode)
                                if (verified) {
                                    codeVerified = true
                                    showPasswordStep = true
                                } else {
                                    errorMsg = "验证码错误或已过期"
                                }
                            },
                            enabled = smsCode.length == 6,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FC3F7))
                        ) {
                            Text("验证", color = Color(0xFF1A1A2E))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { recoveryMode = 1 }) {
                        Text("使用恢复码找回", fontSize = 14.sp, color = Color(0xFF8888AA))
                    }
                } else {
                    // Recovery code mode
                    Text("输入 12 位恢复码", fontSize = 14.sp, color = Color(0xFFB0B0C0),
                        modifier = Modifier.padding(bottom = 16.dp))

                    Text(
                        text = if (recoveryCodeInput.isEmpty()) "XXXX-XXXX-XXXX" else {
                            val digits = recoveryCodeInput.filter { it.isDigit() }
                            buildString {
                                for (i in digits.indices) {
                                    if (i == 4 || i == 8) append('-')
                                    append(digits[i])
                                }
                            }
                        },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4FC3F7),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )

                    NumericKeyboard(
                        value = recoveryCodeInput,
                        onValueChange = { recoveryCodeInput = it },
                        maxLength = 12
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (errorMsg.isNotEmpty()) {
                        Text(errorMsg, fontSize = 14.sp, color = Color(0xFFFF6B6B), modifier = Modifier.padding(bottom = 8.dp))
                    }

                    Button(
                        onClick = {
                            val rawCode = recoveryCodeInput.filter { it.isDigit() }
                            if (rawCode.length != 12) {
                                errorMsg = "请输入完整的 12 位恢复码"
                                return@Button
                            }
                            val formatted = "${rawCode.substring(0,4)}-${rawCode.substring(4,8)}-${rawCode.substring(8,12)}"
                            if (storage.verifyRecoveryCode(formatted)) {
                                codeVerified = true
                                showPasswordStep = true
                            } else {
                                errorMsg = "恢复码错误"
                            }
                        },
                        enabled = recoveryCodeInput.length >= 12,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FC3F7))
                    ) {
                        Text("验证", color = Color(0xFF1A1A2E))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { recoveryMode = 0 }) {
                        Text("使用短信验证码找回", fontSize = 14.sp, color = Color(0xFF8888AA))
                    }
                }
            } else {
                // ── Set new password ──
                Text("设置新密码", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("6 位数字密码", fontSize = 14.sp, color = Color(0xFF8888AA),
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))

                val isConfirming = newPassword.length == 6 && newPassword.isNotEmpty()
                val label = if (isConfirming) "再次输入确认" else "输入 6 位新密码"
                val currentValue = if (isConfirming) passwordConfirm else newPassword

                Text(label, fontSize = 14.sp, color = Color(0xFFB0B0C0), modifier = Modifier.padding(bottom = 16.dp))

                NumericKeyboard(
                    value = currentValue,
                    onValueChange = { v ->
                        if (isConfirming) passwordConfirm = v else newPassword = v
                    },
                    maxLength = 6
                )

                if (newPassword.length == 6 && passwordConfirm.length == 6) {
                    LaunchedEffect(Unit) {
                        if (newPassword == passwordConfirm) {
                            val newRecoveryCode = if (codeVerified && recoveryMode == 1 && recoveryCodeInput.isNotEmpty()) {
                                val rawCode = recoveryCodeInput.filter { it.isDigit() }
                                val formatted = "${rawCode.substring(0,4)}-${rawCode.substring(4,8)}-${rawCode.substring(8,12)}"
                                storage.resetPassword(newPassword, formatted)
                            } else {
                                storage.resetPasswordBySms(newPassword)
                            }
                            val msg = if (newRecoveryCode != null) {
                                "密码重置成功！新恢复码: $newRecoveryCode"
                            } else {
                                "密码重置成功"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            onComplete(true)
                        } else {
                            errorMsg = "两次密码不一致"
                            newPassword = ""
                            passwordConfirm = ""
                        }
                    }
                }

                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, fontSize = 14.sp, color = Color(0xFFFF6B6B), modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}
