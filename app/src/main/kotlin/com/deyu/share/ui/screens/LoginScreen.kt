package com.deyu.share.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.deyu.share.data.AppState
import com.deyu.share.ui.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** X 式登录:极简单列、主按钮饱满、错误条淡入淡出 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var smsMode by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var smsChallenge by remember { mutableStateOf<com.deyu.share4.protocol.WeiboClient.SmsChallenge?>(null) }

    LaunchedEffect(countdown) {
        if (countdown > 0) { delay(1000); countdown-- }
    }


    fun onSuccess() {
        nav.navigate(Routes.TIMELINE) { popUpTo(Routes.LOGIN) { inclusive = true } }
    }

    // 验证页完成(拿到 alt)→ 自动免密登录
    LaunchedEffect(VerifyNav.altResult) {
        val alt = VerifyNav.altResult ?: return@LaunchedEffect
        scope.launch {
            busy = true; error = null
            val r = AppState.client.altLogin(alt, phone.ifBlank { null })
            busy = false
            VerifyNav.altResult = null
            if (r.session != null) {
                AppState.login(r.session!!, ctx)
                nav.navigate(Routes.TIMELINE) { popUpTo(Routes.LOGIN) { inclusive = true } }
            } else {
                error = "验证后登录失败:${r.errmsg ?: "?"}(${r.errno ?: "?"})"
            }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(88.dp))
            Text("登录微博", style = MaterialTheme.typography.titleLarge, fontSize = 30.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (smsMode) "未注册手机号验证后将自动创建账号" else "使用手机号或邮箱继续",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(36.dp))

            AnimatedContent(targetState = smsMode, transitionSpec = {
                (slideInVertically(tween(220)) { it / 6 } + fadeIn(tween(220)))
                    .togetherWith(slideOutVertically(tween(180)) { -it / 8 } + fadeOut(tween(180)))
            }) { sms ->
                Column {
                    if (!sms) {
                        Field(value = account, onValueChange = { account = it },
                            label = "手机号 / 邮箱", keyboardType = KeyboardType.Email)
                        Spacer(Modifier.height(14.dp))
                        Field(value = password, onValueChange = { password = it },
                            label = "密码", secret = true,
                            keyboardType = KeyboardType.Password)
                    } else {
                        Field(value = phone, onValueChange = { phone = it },
                            label = "手机号", keyboardType = KeyboardType.Phone)
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                Field(value = smsCode, onValueChange = { smsCode = it },
                                    label = "验证码", keyboardType = KeyboardType.Number)
                            }
                            Spacer(Modifier.width(12.dp))
                            TextButton(
                                enabled = countdown == 0 && !busy,
                                onClick = {
                                    scope.launch {
                                        busy = true; error = null
                                        // 先确保 aid
                                        if (AppState.client.aid == null) {
                                            runCatching { AppState.client.guestLogin() }
                                        }
                                        val r = AppState.client.smsSendCode(phone)
                                        busy = false
                                        android.util.Log.d("Share4Login", "sendcode raw: ${r.raw.take(800)}")
                                        if (r.errno == null || r.errno == 0) {
                                            smsChallenge = r.sms
                                            countdown = 60
                                            if (r.sms?.code?.isEmpty() != false) {
                                                error = "验证码已发送(未取到回执字段,继续输入短信码)"
                                            }
                                        } else {
                                            if (r.errurl != null) {
                                                VerifyNav.url = r.errurl!!
                                                VerifyNav.altResult = null
                                                nav.navigate(Routes.VERIFY)
                                            } else {
                                                error = when (r.errno) {
                                                    8599 -> "发送频繁被限流(8599),可稍后再试"
                                                    else -> r.errmsg ?: "发送失败(${r.errno ?: "?"})"
                                                }
                                            }
                                        }
                                    }
                                },
                            ) {
                                Text(if (countdown > 0) "${countdown}s" else "获取验证码",
                                    color = if (countdown > 0) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // 错误条(淡入)
            AnimatedVisibility(visible = error != null, enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200))) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp))
            }

            PrimaryButton(busy = busy, text = "登录") {
                scope.launch {
                    busy = true; error = null
                    if (AppState.client.aid == null) {
                        runCatching { AppState.client.guestLogin() }
                    }
                    val r = if (!smsMode) {
                        AppState.client.accountLogin(account.trim(), password)
                    } else {
                        val ch = smsChallenge
                        if (ch == null) { busy = false; error = "请先获取验证码"; return@launch }
                        AppState.client.smsVerify(ch, smsCode.trim())
                    }
                    busy = false
                    android.util.Log.d("Share4Login",
                        "login raw(${if (!smsMode) "pwd" else "sms"}): ${r.raw.take(3800)}")
                    runCatching {
                        java.io.File(ctx.filesDir, "login_raw.json").writeText(r.raw)
                    }
                    if (r.session != null) {
                        AppState.login(r.session!!, ctx)
                        onSuccess()
                    } else {
                        // errno=0 但未解析出会话:展示原始响应便于诊断字段名
                        error = when (r.errno) {
                            null -> "网络异常,请重试"
                            -105 -> "客户端身份校验失败(-105)"
                            104 -> "账号或密码不正确(104)"
                            8523 -> "手机号格式有误(8523)"
                            0 -> "服务端返回成功但未识别到登录态, raw=${r.raw.take(80)}"
                            else -> "${r.errmsg ?: "登录失败"}(${r.errno})"
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { smsMode = !smsMode }) {
                Text(if (smsMode) "使用密码登录" else "使用短信验证码登录",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.weight(1f))
            TextButton(onClick = { nav.navigate(Routes.TIMELINE) }) {
                Text("先逛逛(未登录态)", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { nav.navigate(Routes.PROTOCOL) }) {
                Text("协议验证(开发者)", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Field(value: String, onValueChange: (String) -> Unit, label: String,
                  secret: Boolean = false, keyboardType: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        visualTransformation = if (secret) PasswordVisualTransformation() else
            androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/** 主按钮:按下微缩(spring 回弹) */
@Composable
private fun PrimaryButton(busy: Boolean, text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsItemValue(target = if (pressed) 0.97f else 1f)
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(52.dp).scale(scale),
        shape = MaterialTheme.shapes.extraLarge,
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        ),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp,
                color = Color.White, trackColor = Color.Transparent)
        } else {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun animateFloatAsItemValue(target: Float) =
    androidx.compose.animation.core.animateFloatAsState(
        targetValue = target, animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "pressScale")
