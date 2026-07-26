package com.pinecone.pinecone.ui.guard.account

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.pinecone.data.AccountStorage
import com.pinecone.pinecone.ui.guard.GuardSettingsScaffold
import com.pinecone.pinecone.network.DevSmsService
import com.pinecone.pinecone.ui.theme.*
import kotlinx.coroutines.launch

private data class StepInfo(val index: Int, val title: String, val desc: String)

private val REG_STEPS = listOf(
    StepInfo(0, "手机号", "输入家长手机号"),
    StepInfo(1, "验证码", "短信验证"),
    StepInfo(2, "设置密码", "6 位数字密码"),
    StepInfo(3, "完成", "生成恢复码")
)

@Composable
fun RegisterScreen(onComplete: (success: Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val smsService = remember { DevSmsService() }
    val storage = remember { AccountStorage.getInstance(context) }

    if (storage.isRegistered) {
        onComplete(true)
        return
    }

    var step by remember { mutableIntStateOf(0) }
    var phoneNumber by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var sentCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var recoveryCode by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
        }
    }

    GuardSettingsScaffold(
        title = "注册家长账号",
        onBack = { onComplete(false) }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // ── Left sidebar: step list ──
            Column(
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight()
                    .background(PineSidebar)
                    .padding(vertical = 12.dp)
            ) {
                REG_STEPS.forEach { s ->
                    val isCurrent = s.index == step
                    val isDone = s.index < step
                    val accentColor = when {
                        isCurrent -> PinePrimary
                        isDone    -> PineSuccess
                        else      -> PineTextMuted
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .then(
                                if (isCurrent) Modifier.background(PineHighlightSolid)
                                else Modifier
                            )
                            .clickable(enabled = s.index < step) {
                                step = s.index
                                errorMsg = ""
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isDone) "✓" else "${s.index + 1}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.width(24.dp)
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                text = s.title,
                                fontSize = 15.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent || isDone) Color.White else PineTextSecondary
                            )
                            Text(
                                text = s.desc,
                                fontSize = 12.sp,
                                color = PineTextSecondary
                            )
                        }
                    }
                }
            }

            // Divider
            VerticalDivider(color = PineCardBorder, thickness = 1.dp)

            // ── Right content area ──
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (step) {
                    // ── Step 0: Phone ──
                    0 -> {
                        Text("输入手机号", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("用于找回密码，保证账号唯一性", fontSize = 13.sp, color = PineTextSecondary,
                            modifier = Modifier.padding(bottom = 16.dp))

                        NumericKeyboard(value = phoneNumber, onValueChange = { phoneNumber = it }, maxLength = 11, showDots = false)

                        Spacer(modifier = Modifier.height(12.dp))

                        if (errorMsg.isNotEmpty()) {
                            Text(errorMsg, fontSize = 14.sp, color = PineError, modifier = Modifier.padding(bottom = 4.dp))
                        }

                        Button(
                            onClick = {
                                if (phoneNumber.length != 11 || !phoneNumber.startsWith("1")) {
                                    errorMsg = "请输入正确的 11 位手机号"
                                    return@Button
                                }
                                errorMsg = ""
                                step = 1
                            },
                            enabled = phoneNumber.length == 11,
                            colors = ButtonDefaults.buttonColors(containerColor = PinePrimary),
                            modifier = Modifier.height(48.dp).width(220.dp)
                        ) {
                            Text("下一步", fontSize = 16.sp, color = PineTextOnAccent)
                        }
                    }

                    // ── Step 1: SMS ──
                    1 -> {
                        Text("短信验证", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("验证码已发送至 ${
                            if (phoneNumber.length == 11) "${phoneNumber.take(3)}****${phoneNumber.takeLast(4)}"
                            else phoneNumber
                        }", fontSize = 13.sp, color = PineTextSecondary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))

                        LaunchedEffect(Unit) {
                            isLoading = true
                            val result = smsService.sendVerificationCode(phoneNumber)
                            if (result.isSuccess) {
                                sentCode = result.getOrDefault("")
                                Toast.makeText(context, "验证码: $sentCode（开发模式）", Toast.LENGTH_LONG).show()
                                countdown = 60
                            } else {
                                errorMsg = result.exceptionOrNull()?.message ?: "发送失败"
                            }
                            isLoading = false
                        }

                        Text("输入 6 位验证码", fontSize = 13.sp, color = PineTextSecondary,
                            modifier = Modifier.padding(bottom = 4.dp))

                        NumericKeyboard(value = smsCode, onValueChange = { smsCode = it }, maxLength = 6)

                        Spacer(modifier = Modifier.height(8.dp))

                        if (errorMsg.isNotEmpty()) {
                            Text(errorMsg, fontSize = 14.sp, color = PineError, modifier = Modifier.padding(bottom = 4.dp))
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        val result = smsService.sendVerificationCode(phoneNumber)
                                        if (result.isSuccess) {
                                            sentCode = result.getOrDefault("")
                                            Toast.makeText(context, "验证码: $sentCode（开发模式）", Toast.LENGTH_LONG).show()
                                            countdown = 60
                                            errorMsg = ""
                                        } else {
                                            errorMsg = result.exceptionOrNull()?.message ?: "发送失败"
                                        }
                                        isLoading = false
                                    }
                                },
                                enabled = countdown == 0 && !isLoading,
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text(if (countdown > 0) "${countdown}s 后重发" else "重新发送", fontSize = 14.sp)
                            }
                            Button(
                                onClick = {
                                    errorMsg = ""
                                    step = 2
                                },
                                enabled = smsCode.length == 6,
                                colors = ButtonDefaults.buttonColors(containerColor = PinePrimary),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("验证", fontSize = 15.sp, color = PineTextOnAccent)
                            }
                        }
                    }

                    // ── Step 2: Password ──
                    2 -> {
                        Text("设置家长密码", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("6 位数字密码，用于管理防沉迷规则", fontSize = 13.sp, color = PineTextSecondary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))

                        val isConfirming = password.length == 6 && password.isNotEmpty()
                        val label = if (isConfirming) "再次输入确认" else "输入 6 位新密码"
                        val currentValue = if (isConfirming) passwordConfirm else password

                        Text(label, fontSize = 13.sp, color = PineTextSecondary, modifier = Modifier.padding(bottom = 8.dp))

                        NumericKeyboard(
                            value = currentValue,
                            onValueChange = { newVal ->
                                if (isConfirming) passwordConfirm = newVal else password = newVal
                            },
                            maxLength = 6
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (errorMsg.isNotEmpty()) {
                            Text(errorMsg, fontSize = 14.sp, color = PineError, modifier = Modifier.padding(bottom = 4.dp))
                        }

                        if (password.length == 6 && passwordConfirm.length == 6) {
                            LaunchedEffect(Unit) {
                                if (password == passwordConfirm) {
                                    errorMsg = ""
                                    val code = storage.register(phoneNumber, password)
                                    recoveryCode = code
                                    step = 3
                                } else {
                                    errorMsg = "两次密码不一致，请重新输入"
                                    password = ""
                                    passwordConfirm = ""
                                }
                            }
                        }
                    }

                    // ── Step 3: Done ──
                    3 -> {
                        Text("注册成功", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PinePrimary)
                        Text("请妥善保管以下恢复码", fontSize = 13.sp, color = PineTextSecondary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))

                        Surface(
                            color = PineElevated,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = recoveryCode,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = PinePrimary,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                letterSpacing = 3.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                            )
                        }

                        Text(
                            text = "忘记密码时，可使用此恢复码重置密码\n请截图保存或抄写记录",
                            fontSize = 12.sp,
                            color = PineError.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        Button(
                            onClick = {
                                Toast.makeText(context, "账号注册成功", Toast.LENGTH_SHORT).show()
                                onComplete(true)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PinePrimary),
                            modifier = Modifier.height(48.dp).width(220.dp)
                        ) {
                            Text("完成", fontSize = 16.sp, color = PineTextOnAccent)
                        }
                    }
                }
            }
        }
    }
}
