package com.deyu.share.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

/**
 * 高级材质(HyperOS 风格)组件层。
 *
 * 三件套:
 * 1. [DiffuseBackground] —— 全屏弥散光背景,给毛玻璃提供"背后有东西可模糊"的内容;
 * 2. [Modifier.glassPanel] —— 基于 haze(RenderEffect)的真毛玻璃,半透明 + 背景模糊 + 细颗粒;
 * 3. [Modifier.hairlineStroke] —— 渐变高光描边,面板上缘提亮,是高级材质最关键的一笔。
 *
 * 全局 [LocalHazeState] 由根容器提供:根节点 hazeSource,各面板 hazeEffect。
 */

/**
 * 全局 HazeState 分两个:
 *
 * - [LocalHazeState]:模糊「弥散光背景」用,给顶栏/浮层等**不遮挡内容**的玻璃面板;
 * - [LocalContentHazeState]:模糊「页面内容」用,给底栏/输入条等**浮在内容之上**的玻璃面板。
 *
 * 为什么要分开:haze 的效果节点会跳过**正在绘制的祖先源**区域。
 * 若把内容源放在根节点,底栏玻璃在内容源内部 → 取不到样本,只剩色罩(糊但不模糊)。
 * 所以内容源必须下沉到「内容区」,让底栏成为它的兄弟节点;而顶栏要模糊的是背景,
 * 用另一个 state 指向背景源,背景源不是顶栏的祖先,不受影响。
 */
val LocalHazeState: ProvidableCompositionLocal<HazeState> =
    compositionLocalOf { error("LocalHazeState 未初始化:请在根容器 CompositionLocalProvider 中提供") }

/** 内容源专用:底栏/输入条等浮在内容之上的玻璃面板用它 */
val LocalContentHazeState: ProvidableCompositionLocal<HazeState> =
    compositionLocalOf { error("LocalContentHazeState 未初始化:请在页面 CompositionLocalProvider 中提供") }

/** 记住一个 HazeState(haze 1.2.2 无 rememberHazeState,直接构造) */
@Composable
fun rememberMaterialState(): HazeState = remember { HazeState() }

/** 材质参数:统一一处,避免各页面各写一套 */
object Material {
    /** 玻璃模糊半径 */
    val blur: Dp = 32.dp
    /** 玻璃细颗粒强度(高级材质的"磨砂"质感来源) */
    const val noise: Float = 0.045f
    /** 浮起卡片圆角 */
    val cardShape: RoundedCornerShape = RoundedCornerShape(20.dp)
    /** 小控件圆角 */
    val chipShape: RoundedCornerShape = RoundedCornerShape(14.dp)
}

/** 当前是否深色(由背景色亮度推断,不依赖 isSystemInDarkTheme) */
@Composable
fun isDarkMaterial(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * 全屏弥散光背景:HyperOS 的标志性观感——底色之上浮着几团大尺度柔和色斑。
 * 毛玻璃面板模糊的正是这些色斑,所以玻璃里会透出有层次的彩色,而不是一片灰。
 */
@Composable
fun DiffuseBackground(modifier: Modifier = Modifier) {
    val dark = isDarkMaterial()
    val blobA = if (dark) Color(0xFF16304F) else Color(0xFFCFDEF7)   // 顶部左:品牌蓝
    val blobB = if (dark) Color(0xFF2A2144) else Color(0xFFE9DCF6)   // 中部右:柔紫
    val blobC = if (dark) Color(0xFF12333A) else Color(0xFFD2EDF1)   // 底部:青
    val blobD = if (dark) Color(0xFF33261A) else Color(0xFFFAE7D2)   // 右下:暖橙
    val a = if (dark) 0.55f else 0.85f
    Box(
        modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(blobA.copy(alpha = a), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.04f),
                        radius = size.width * 0.95f,
                    ))
                drawRect(
                    Brush.radialGradient(
                        listOf(blobB.copy(alpha = a * 0.8f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.26f),
                        radius = size.width * 0.85f,
                    ))
                drawRect(
                    Brush.radialGradient(
                        listOf(blobC.copy(alpha = a * 0.7f), Color.Transparent),
                        center = Offset(size.width * 0.28f, size.height * 0.92f),
                        radius = size.width * 1.05f,
                    ))
                drawRect(
                    Brush.radialGradient(
                        listOf(blobD.copy(alpha = a * 0.55f), Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.78f),
                        radius = size.width * 0.7f,
                    ))
            }
    )
}

/**
 * 玻璃面板样式。
 * @param tint 半透明色罩(决定玻璃的"底色"),不是纯白——这样弥散光能透上来
 */
@Composable
fun glassStyle(
    tint: Color = MaterialTheme.colorScheme.surface,
    blur: Dp = Material.blur,
    noise: Float = Material.noise,
): HazeStyle {
    val dark = isDarkMaterial()
    val alpha = if (dark) 0.58f else 0.72f
    val t = tint.copy(alpha = alpha)
    return HazeStyle(
        backgroundColor = Color.Transparent,
        tints = listOf(HazeDefaults.tint(t)),
        blurRadius = blur,
        noiseFactor = noise,
        fallbackTint = HazeDefaults.tint(t),
    )
}

/**
 * 毛玻璃面板:模糊背后内容 + 半透明色罩 + 细颗粒,并裁到指定形状。
 * 玻璃本身不含描边,描边用 [hairlineStroke] 单独加(两层分开才可控)。
 *
 * @param state 要模糊的源。默认模糊弥散光背景(顶栏用);
 *              浮在内容之上的面板(底栏)要传内容源。
 */
@Composable
fun Modifier.glassPanel(
    state: HazeState = LocalHazeState.current,
    shape: Shape = RoundedCornerShape(0.dp),
    tint: Color = MaterialTheme.colorScheme.surface,
    blur: Dp = Material.blur,
    noise: Float = Material.noise,
): Modifier {
    val style = glassStyle(tint, blur, noise)
    return this
        .hazeEffect(state, style)
        .clip(shape)
}

/**
 * 渐变高光描边:上缘亮、下缘淡,是高级材质"面板浮起来"的关键一笔。
 * 深色下用白色低透明,浅色下用白→灰蓝。
 *
 * Modifier.border 沿 shape 轮廓描边且不参与布局,正合适。
 */
@Composable
fun Modifier.hairlineStroke(
    shape: Shape = RoundedCornerShape(0.dp),
    width: Dp = 1.dp,
): Modifier {
    val dark = isDarkMaterial()
    val colors = if (dark)
        listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))
    else
        listOf(Color.White.copy(alpha = 0.95f), Color(0xFF93A4BC).copy(alpha = 0.20f))
    return this.border(width, Brush.verticalGradient(colors), shape)
}

/**
 * 面板顶部边缘高光线:中间亮、两侧淡出,是高级材质"面板浮起来"的关键一笔。
 * 比整圈 border 更接近 HyperOS 的观感(底栏/顶栏只有上缘有一道光)。
 */
fun Modifier.topEdgeLine(color: Color, height: Dp = 0.8.dp): Modifier = drawWithContent {
    drawContent()
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(Color.Transparent, color, Color.Transparent),
            startX = 0f, endX = size.width,
        ),
        topLeft = Offset(0f, 0f),
        size = Size(size.width, height.toPx()),
    )
}

/**
 * 玻璃顶栏:统一各页面的标题栏(模糊弥散光背景 + 底缘高光 + 延伸进状态栏)。
 * 替换 M3 的 TopAppBar。
 */
@Composable
fun GlassTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth()
            .glassPanel()
            .bottomEdgeLine(edgeLineColor())
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (navigationIcon != null) {
                navigationIcon()
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { title() }
            actions()
        }
    }
}

/**
 * 玻璃底栏:浮在内容之上的输入条/操作条。
 * @param state 要模糊的内容源(浮在内容之上,必须是该源的兄弟节点)
 */
@Composable
fun GlassBottomBar(
    state: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxWidth()
            .glassPanel(state = state)
            .topEdgeLine(edgeLineColor())
    ) { content() }
}

/**
 * 面板底部边缘高光线:中间亮、两侧淡出(顶栏用它与内容区分隔)。
 */
fun Modifier.bottomEdgeLine(color: Color, height: Dp = 0.8.dp): Modifier = drawWithContent {
    drawContent()
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(Color.Transparent, color, Color.Transparent),
            startX = 0f, endX = size.width,
        ),
        topLeft = Offset(0f, size.height - height.toPx()),
        size = Size(size.width, height.toPx()),
    )
}

/** 顶缘高光线的默认颜色(深浅自动) */
@Composable
fun edgeLineColor(): Color =
    if (isDarkMaterial()) Color.White.copy(alpha = 0.22f) else Color(0xFF7C93B5).copy(alpha = 0.30f)

/** 向白色插值(做渐变高光用) */
fun Color.lighten(amount: Float): Color = lerp(this, Color.White, amount)

/** 向黑色插值(做渐变暗部用) */
fun Color.darken(amount: Float): Color = lerp(this, Color.Black, amount)

/**
 * 高级材质 FAB:品牌渐变 + 柔和投影 + 上缘高光。
 * M3 的 FloatingActionButton 只能纯色,这里自己画以拿到渐变与光。
 */
@Composable
fun MaterialFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(56.dp)
            .shadow(
                elevation = 8.dp, shape = shape, clip = false,
                ambientColor = Color(0x402861AD), spotColor = Color(0x662861AD),
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(scheme.primary.lighten(0.16f), scheme.primary.darken(0.06f))))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 上缘高光,让按钮有球面感
        Box(Modifier.fillMaxSize().topEdgeLine(Color.White.copy(alpha = 0.28f), 1.dp))
        icon()
    }
}

/**
 * 浮起卡片表面:圆角底色 + 柔和宽投影 + 渐变高光描边。
 * 挂在任意节点上即可(不改布局结构),深色下投影不可见,靠描边与容器色分层拉开纵深。
 */
@Composable
fun Modifier.cardSurface(shape: Shape = Material.cardShape): Modifier {
    val dark = isDarkMaterial()
    val scheme = MaterialTheme.colorScheme
    // 浅色:纯白卡片浮在冷调底色上;深色:卡片要比背景略亮才有"浮起"感
    val bg = if (dark) scheme.surfaceContainer else scheme.surfaceContainerLowest
    return this
        .shadow(
            elevation = if (dark) 0.dp else 2.dp,
            shape = shape,
            clip = false,
            ambientColor = Color(0x1A2A4A7A),
            spotColor = Color(0x26203C66),
        )
        .clip(shape)
        .background(bg)
        .hairlineStroke(shape)
}
