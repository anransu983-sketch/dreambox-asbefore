/*
 * 天空共用件：各页自己的整屏场景画 + 画上光字。
 * 设计口径与 feature/home 的 SkyIslandScene 一致：程序只在画上叠光与雾；
 * 底图与叠层共用同一套坐标（SkyShotMap），按构造零对位偏差。
 * 每页一张自己的画（assetUri + aspect 各自传），首页文件零改动。
 */
package com.suanran.dreambox.presentation.component.sky

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.github.panpf.sketch.rememberAsyncImagePainter
import com.github.panpf.sketch.request.ImageRequest
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

const val SKY_NIGHT_URI = "file:///android_asset/sky_island_night.webp"

/** 首页底图宽高比（1120×2240），SkyShotMap 的默认 aspect。 */
const val SKY_HOME_ASPECT = 1120f / 2240f

val SkyNightBase = Color(0xFF0B1636)
val SkyDeepBase = Color(0xFF0D1B40)
val SkyMistColor = Color(0xFF050B1F)
val SkyDuskTint = Color(0xFF141B3D)
val SkyLabelCore = Color(0xFFEAF3FF)
val SkyLabelGlow = Color(0xFF8FC8FF)
val SkyStateOk = Color(0xFFA6EBCB)
val SkyStateOff = Color(0xFFB9C3D9)
val SkyBadgeGold = Color(0xFFF2C46B)

/** 延迟三色（同首页 latencyColor）：≤150 冷白蓝 / ≤400 暖金 / 以上柔珊瑚。 */
fun skyLatencyColor(ms: Int): Color =
    when {
        ms <= 150 -> Color(0xFFBFE6FF)
        ms <= 400 -> Color(0xFFF2C46B)
        else -> Color(0xFFFF8F7A)
    }

/**
 * 镜头坐标：图比例 → 容器内像素。缩放以整屏（refW×refH）为基准按 aspect 算 Crop 比例，
 * 再按 (cx, cy) 取景；图片本体按同一 map 摆放，叠层与底图按构造对齐。
 */
class SkyShotMap(
    w: Float,
    h: Float,
    refW: Float,
    refH: Float,
    zoom: Float,
    cxIn: Float,
    cyIn: Float,
    aspect: Float = SKY_HOME_ASPECT,
) {
    val iwS: Float
    val ihS: Float
    private val ox: Float
    private val oy: Float
    val cx: Float
    val cy: Float

    init {
        val s = max(refW / aspect, refH / 1f) * zoom
        iwS = aspect * s
        ihS = s
        val hw = (w / 2f) / iwS
        val hh = (h / 2f) / ihS
        cx = cxIn.coerceIn(hw, 1f - hw)
        cy = cyIn.coerceIn(hh, 1f - hh)
        ox = w / 2f - cx * iwS
        oy = h / 2f - cy * ihS
    }

    fun at(f: Offset) = Offset(ox + f.x * iwS, oy + f.y * ihS)

    val imageOffset: IntOffset
        get() = IntOffset(ox.roundToInt(), oy.roundToInt())
}

/** 把自己的中心放到屏幕坐标 p（同首页 centeredAt）；alpha 在 layer 阶段求值。 */
fun Modifier.skyCenteredAt(p: Offset, alpha: () -> Float = { 1f }): Modifier =
    this.offset { IntOffset(p.x.roundToInt(), p.y.roundToInt()) }
        .graphicsLayer {
            translationX = -size.width / 2f
            translationY = -size.height / 2f
            this.alpha = alpha()
        }

/** 题字时钟（同首页）：active 时亮起 4 秒再淡出；返回值在 draw/layer 阶段读。 */
@Composable
fun rememberSkyCaption(active: Boolean): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            anim.snapTo(1f)
            delay(4000)
            anim.animateTo(0f, tween(600))
        } else {
            anim.snapTo(0f)
        }
    }
    return anim
}

/**
 * 环境时钟（同首页 ambientClock）：120s 一圈、33ms 步进，只在 active 且非 reduceMotion 时走；
 * 值只在 draw/layer 阶段读，不触发重组。非当前页自动停走，省电。
 */
@Composable
fun rememberSkyClock(active: Boolean, reduceMotion: Boolean = false): MutableFloatState {
    val clock = remember { mutableFloatStateOf(23f) }
    LaunchedEffect(active, reduceMotion) {
        if (active && !reduceMotion) {
            var last = SystemClock.elapsedRealtime()
            while (true) {
                delay(33)
                val now = SystemClock.elapsedRealtime()
                clock.floatValue = (clock.floatValue + (now - last) / 1000f) % 120f
                last = now
            }
        }
    }
    return clock
}

/** 整页是深色画时把状态栏图标改浅色（同首页 LightStatusBarIcons）。 */
@Composable
fun SkyStatusBarIcons(active: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(active) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        if (active) controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (active && previous != null) controller?.isAppearanceLightStatusBars = previous
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/**
 * 整屏底画：按 map 摆图（zoom=1 时与整屏取景一致），可叠饱和度与暮色（连接态联动用）。
 * onReadyChange 在 painter 拿到尺寸时回调 true，供题字门闩用。
 */
@Composable
fun SkyBackdropFull(
    map: SkyShotMap,
    assetUri: String,
    modifier: Modifier = Modifier,
    sat: Float = 1f,
    dusk: Float = 0f,
    onReadyChange: (Boolean) -> Unit = {},
) {
    val painter = rememberAsyncImagePainter(request = ImageRequest(LocalContext.current, assetUri))
    val density = LocalDensity.current
    val ready = painter.intrinsicSize != Size.Unspecified
    LaunchedEffect(ready) { onReadyChange(ready) }
    Box(modifier.fillMaxSize().clipToBounds().background(SkyNightBase)) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier =
                Modifier.offset { map.imageOffset }
                    .size(with(density) { map.iwS.toDp() }, with(density) { map.ihS.toDp() }),
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(sat) }),
        )
        if (dusk > 0f) {
            Box(Modifier.fillMaxSize().drawBehind { drawRect(SkyDuskTint.copy(alpha = dusk)) })
        }
    }
}

/** 发光字（同首页 LightLabel 配方）：雾斑垫底、光晕层 + 清晰层双层衬线字。 */
@Composable
fun SkyGlowLabel(
    line1: String,
    line2: AnnotatedString? = null,
    line1Color: Color = SkyLabelCore,
    line1Size: TextUnit = 15.sp,
    line2Size: TextUnit = 10.5.sp,
    mistAlpha: Float = 0.34f,
    glowDp: Dp = 7.dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val glowPx = with(density) { glowDp.toPx() }
    val minMistRadius = with(density) { 48.dp.toPx() }
    Box(
        modifier.drawBehind {
            val r = max(size.width * 0.8f, minMistRadius)
            val scaleY = (size.height * 2.2f) / (2f * r)
            withTransform({ scale(1f, scaleY, pivot = center) }) {
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            listOf(SkyMistColor.copy(alpha = mistAlpha), Color.Transparent),
                            center = center,
                            radius = r,
                        ),
                    radius = r,
                    center = center,
                )
            }
        },
    ) {
        SkyLabelLines(line1, line1Color, line1Size, line2, line2Size, glowLayer = true, glowPx = glowPx)
        SkyLabelLines(line1, line1Color, line1Size, line2, line2Size, glowLayer = false, glowPx = glowPx)
    }
}

@Composable
private fun SkyLabelLines(
    line1: String,
    line1Color: Color,
    line1Size: TextUnit,
    line2: AnnotatedString?,
    line2Size: TextUnit,
    glowLayer: Boolean,
    glowPx: Float,
) {
    val shadow = if (glowLayer) Shadow(SkyLabelGlow.copy(alpha = 0.7f), Offset.Zero, glowPx) else null
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            text = line1,
            style =
                TextStyle(
                    color = if (glowLayer) SkyLabelGlow.copy(alpha = 0.55f) else line1Color.copy(alpha = 0.95f),
                    fontSize = line1Size,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                    shadow = shadow,
                ),
            maxLines = 1,
        )
        if (line2 != null) {
            BasicText(
                text = if (glowLayer) line2.text else line2,
                modifier = Modifier.padding(top = 2.dp),
                style =
                    TextStyle(
                        color = if (glowLayer) SkyLabelGlow.copy(alpha = 0.5f) else SkyLabelCore.copy(alpha = 0.88f),
                        fontSize = line2Size,
                        fontFamily = FontFamily.Serif,
                        letterSpacing = 0.6.sp,
                        textAlign = TextAlign.Center,
                        shadow = shadow,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 画上题字入口：光字 + 无形热区 + 语义合一。
 * alpha = (底值 + (1−底值)·题字时钟) × 呼吸 × 入场，全部在 graphicsLayer 阶段求值；
 * 按压时 alpha 拉满、光晕扩散，并回调 onPressedChange 供场景点亮所挂的灯。
 * 中心 x 按自身宽度夹进屏内（边缘题字不溢出）。
 */
@Composable
fun SkyLabelEntry(
    center: Offset,
    title: String,
    sub: AnnotatedString? = null,
    index: Int = 0,
    baseAlpha: Float = 0.74f,
    caption: () -> Float,
    phase: () -> Float,
    parentSize: IntSize,
    contentDescription: String,
    active: Boolean = true,
    mistAlpha: Float = 0.34f,
    onPressedChange: (Boolean) -> Unit = {},
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed) { onPressedChange(pressed) }
    val entry = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            entry.snapTo(0f)
            delay(index * 28L)
            entry.animateTo(1f, tween(220))
        } else {
            entry.snapTo(0f)
        }
    }
    val glowDp by
        animateFloatAsState(
            targetValue = if (pressed) 12f else 7f,
            animationSpec = tween(if (pressed) 90 else 300),
            label = "skyEntryGlow",
        )
    var labelSize by remember { mutableStateOf(IntSize.Zero) }
    val clampedCenter =
        remember(center, labelSize, parentSize) {
            val halfW = labelSize.width / 2f
            val margin = with(density) { 10.dp.toPx() }
            val cx =
                if (parentSize.width > 0 && labelSize.width > 0) {
                    center.x.coerceIn(halfW + margin, parentSize.width - halfW - margin)
                } else {
                    center.x
                }
            Offset(cx, center.y)
        }
    Box(
        modifier
            .skyCenteredAt(clampedCenter) {
                val c = if (pressed) 1f else caption()
                val breathe = 1f + 0.06f * sin(2.0 * PI * (phase() / 6f + index * 0.17f)).toFloat()
                ((baseAlpha + (1f - baseAlpha) * c) * breathe * entry.value).coerceIn(0f, 1f)
            }
            .sizeIn(minWidth = 96.dp, minHeight = 48.dp)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                traversalIndex = index.toFloat()
            }
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.onSizeChanged { labelSize = it }) {
            SkyGlowLabel(
                line1 = title,
                line2 = sub,
                line1Size = 16.sp,
                mistAlpha = mistAlpha,
                glowDp = glowDp.dp,
            )
        }
    }
}
