package com.deyu.share.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// X 式克制造型:白底、近黑文字、weibo 橙仅用于主操作,其余一律弱化
private val WeiboOrange = Color(0xFFE8821E)
private val WeiboOrangePressed = Color(0xFFD9730F)
private val Ink = Color(0xFF0F1419)          // X 的近黑
private val InkSecondary = Color(0xFF536471)  // X 的次级灰
private val Hairline = Color(0xFFEFF3F4)      // X 的分割线
private val SurfaceTint = Color(0xFFF7F9F9)   // X 的浅灰面

private val LightScheme = lightColorScheme(
    primary = WeiboOrange,
    onPrimary = Color.White,
    secondary = Color(0xFF1D9BF0),
    background = Color.White,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = SurfaceTint,
    onSurfaceVariant = InkSecondary,
    outlineVariant = Hairline,
    error = Color(0xFFF4212E),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF9E2C),
    onPrimary = Color(0xFF1A1000),
    secondary = Color(0xFF1D9BF0),
    background = Color(0xFF000000),   // X 真黑
    onBackground = Color(0xFFE7E9EA),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE7E9EA),
    surfaceVariant = Color(0xFF16181C),
    onSurfaceVariant = Color(0xFF71767B),
    outlineVariant = Color(0xFF2F3336),
    error = Color(0xFFF4212E),
)

private val Type = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 0.1.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, color = InkSecondary),
    labelSmall = TextStyle(fontSize = 12.sp),
)

@Composable
fun Share4Theme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    // 固定品牌配色(不用动态取色,保证微博橙主色与真黑夜间)
    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    MaterialTheme(colorScheme = colorScheme, typography = Type, content = content)
}
