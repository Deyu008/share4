package com.deyu.share.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.deyu.share.data.SettingsStore

// Share 配色:白底 + 微博蓝主操作,夜间深蓝灰(原版 Share 的 Material 蓝系观感)
private val WeiboBlue = Color(0xFF2861AD)
private val Ink = Color(0xFF333333)          // Share 的正文墨色
private val InkSecondary = Color(0xFF8A8A8A)  // 次级灰
private val Hairline = Color(0xFFE9E9E9)
private val SurfaceTint = Color(0xFFF5F6F8)

private val LightScheme = lightColorScheme(
    primary = WeiboBlue,
    primaryContainer = Color(0xFFDCE7F5),
    onPrimary = Color.White,
    onPrimaryContainer = Color(0xFF12345F),
    secondary = Color(0xFFE8821E),   // 微博橙保留给点赞/热搜等强调位
    onSecondary = Color.White,
    // secondaryContainer 决定底栏/胶囊选中态;不定义会落回 M3 默认紫
    secondaryContainer = Color(0xFFE3EDFA),
    onSecondaryContainer = WeiboBlue,
    tertiary = Color(0xFFE2413E),
    background = Color(0xFFF7F8FA),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = SurfaceTint,
    onSurfaceVariant = InkSecondary,
    outlineVariant = Hairline,
    error = Color(0xFFE2413E),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7FA6E0),
    primaryContainer = Color(0xFF2C3A52),
    onPrimary = Color(0xFF0B1626),
    onPrimaryContainer = Color(0xFFD6E3F7),
    secondary = Color(0xFFFF9E2C),
    onSecondary = Color(0xFF1A1000),
    secondaryContainer = Color(0xFF23344C),
    onSecondaryContainer = Color(0xFFCFE0F7),
    tertiary = Color(0xFFFF6B66),
    background = Color(0xFF10141B),   // Share 夜间:深蓝灰而非纯黑
    onBackground = Color(0xFFDCE1E8),
    surface = Color(0xFF171C25),
    onSurface = Color(0xFFDCE1E8),
    surfaceVariant = Color(0xFF1F2530),
    onSurfaceVariant = Color(0xFF8B93A1),
    outlineVariant = Color(0xFF2A3140),
    error = Color(0xFFF4212E),
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
 */
@Composable
fun Share4Theme(content: @Composable () -> Unit) {
    val darkTheme = when (SettingsStore.darkMode) {
        SettingsStore.DarkMode.LIGHT -> false
        SettingsStore.DarkMode.DARK -> true
        SettingsStore.DarkMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Type,
        content = content,
    )
}
