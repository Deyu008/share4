package com.deyu.share.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.deyu.share.data.SettingsStore

// Share 配色:白底 + 微博蓝主操作,夜间深蓝灰(原版 Share 的 Material 蓝系观感)
private val WeiboBlue = Color(0xFF2861AD)
private val Ink = Color(0xFF2B3038)              // Share 的正文墨色
private val InkSecondary = Color(0xFF7C8593)    // 次级灰

// 浅色:背景带一点冷调蓝,容器层级逐级加深(高级材质靠这组层级拉开纵深)
private val LightScheme = lightColorScheme(
    primary = WeiboBlue,
    primaryContainer = Color(0xFFDCE7F5),
    onPrimary = Color.White,
    onPrimaryContainer = Color(0xFF12345F),
    inversePrimary = Color(0xFFA8C8F0),
    secondary = Color(0xFFE8821E),   // 微博橙保留给点赞/热搜等强调位
    onSecondary = Color.White,
    // secondaryContainer 决定底栏/胶囊选中态;不定义会落回 M3 默认紫
    secondaryContainer = Color(0xFFE3EDFA),
    onSecondaryContainer = WeiboBlue,
    tertiary = Color(0xFFE2413E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE1DF),
    onTertiaryContainer = Color(0xFF6B0F0D),
    background = Color(0xFFF2F5FA),
    onBackground = Ink,
    surface = Color(0xFFFCFDFF),
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDF1F8),
    onSurfaceVariant = InkSecondary,
    surfaceTint = WeiboBlue,
    inverseSurface = Color(0xFF2A2F37),
    inverseOnSurface = Color(0xFFF0F2F6),
    // 容器层级:卡片用 Lowest,浮层用 High/Highest
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainer = Color(0xFFF1F4FA),
    surfaceContainerHigh = Color(0xFFE9EDF6),
    surfaceContainerHighest = Color(0xFFE1E7F2),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDCE1EA),
    outline = Color(0xFFB9C1CD),
    outlineVariant = Color(0xFFDDE3EC),
    error = Color(0xFFE2413E),
    onError = Color.White,
    errorContainer = Color(0xFFFFE1DF),
    onErrorContainer = Color(0xFF6B0F0D),
    scrim = Color(0xFF000000),
)

// 深色:Share 夜间深蓝灰而非纯黑,容器层级同样逐级抬升
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7FA6E0),
    primaryContainer = Color(0xFF2C3A52),
    onPrimary = Color(0xFF0B1626),
    onPrimaryContainer = Color(0xFFD6E3F7),
    inversePrimary = Color(0xFF2861AD),
    secondary = Color(0xFFFF9E2C),
    onSecondary = Color(0xFF1A1000),
    secondaryContainer = Color(0xFF23344C),
    onSecondaryContainer = Color(0xFFCFE0F7),
    tertiary = Color(0xFFFF6B66),
    onTertiary = Color(0xFF3A0908),
    tertiaryContainer = Color(0xFF6B2220),
    onTertiaryContainer = Color(0xFFFFDAD7),
    background = Color(0xFF0B0E13),
    onBackground = Color(0xFFDCE1E8),
    surface = Color(0xFF13171E),
    onSurface = Color(0xFFDCE1E8),
    surfaceVariant = Color(0xFF1C222C),
    onSurfaceVariant = Color(0xFF8B93A1),
    surfaceTint = Color(0xFF7FA6E0),
    inverseSurface = Color(0xFFDCE1E8),
    inverseOnSurface = Color(0xFF23282F),
    surfaceContainerLowest = Color(0xFF0A0D11),
    surfaceContainerLow = Color(0xFF11151B),
    surfaceContainer = Color(0xFF161A21),
    surfaceContainerHigh = Color(0xFF1D222B),
    surfaceContainerHighest = Color(0xFF262C36),
    surfaceBright = Color(0xFF323945),
    surfaceDim = Color(0xFF0B0E13),
    outline = Color(0xFF464E5B),
    outlineVariant = Color(0xFF2A303A),
    error = Color(0xFFF4212E),
    onError = Color(0xFF3A0908),
    errorContainer = Color(0xFF6B2220),
    onErrorContainer = Color(0xFFFFDAD7),
    scrim = Color(0xFF000000),
)

private val Type = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 0.1.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    // 不写死颜色:深色模式下继承 onSurface,否则次级灰在深底上对比度不足
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp),
)

/**
 * 应用主题:夜间模式三态(跟随系统/浅色/深色,对应原版“默认夜间主题”设置组)。
 *
 * 注意:状态栏/导航栏图标的明暗必须跟着 **App 自己的** 三态走。
 * `enableEdgeToEdge()` 默认按**系统**深浅决定图标色,当系统是浅色而 App 切到深色时,
 * 深色图标会画在深色背景上直接看不见(真机必现,模拟器上也能复现)。
 */
@Composable
fun Share4Theme(content: @Composable () -> Unit) {
    val darkTheme = when (SettingsStore.darkMode) {
        SettingsStore.DarkMode.LIGHT -> false
        SettingsStore.DarkMode.DARK -> true
        SettingsStore.DarkMode.SYSTEM -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Type,
        content = content,
    )
}

/** 从 Context 里剥出 Activity(Compose 的 LocalContext 可能是 ContextWrapper) */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
