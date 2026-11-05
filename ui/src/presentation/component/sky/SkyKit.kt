/*
 * 天空共用件：仿首页（天空之城浮岛）的镜头 Header、光字与雾海清单。
 * 设计口径与 feature/home 的 SkyIslandScene 一致：程序只在画上叠光与雾；
 * 底图与叠层共用同一套坐标（SkyShotMap），按构造零对位偏差。
 */
package com.suanran.dreambox.presentation.component.sky

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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

/** 底图宽高比（1120×2240）。 */
private const val SKY_ASPECT = 1120f / 2240f

val SkyNightBase = Color(0xFF0B1636)
val SkyDeepBase = Color(0xFF0D1B40)
val SkyMistColor = Color(0xFF050B1F)
val SkyLabelCore = Color(0xFFEAF3FF)
val SkyLabelGlow = Color(0xFF8FC8FF)
val SkyStateOk = Color(0xFFA6EBCB)
val SkyStateOff = Color(0xFFB9C3D9)

/** 延迟三色（同首页 latencyColor）：≤150 冷白蓝 / ≤400 暖金 / 以上柔珊瑚。 */
fun skyLatencyColor(ms: Int): Color =
    when {
        ms <= 150 -> Color(0xFFBFE6FF)
        ms <= 400 -> Color(0xFFF2C46B)
        else -> Color(0xFFFF8F7A)
    }

/**
 * 镜头坐标：图比例 → Header 内像素。缩放以整屏（refW×refH）为基准算 Crop 比例，
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
) {
    val iwS: Float
    val ihS: Float
    private val ox: Float
    private val oy: Float
    val cx: Float
    val cy: Float

    init {
        val s = max(refW / SKY_ASPECT, refH / 1f) * zoom
        iwS = SKY_ASPECT * s
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

/** 把自己的中心放到屏幕坐标 p（同首页 centeredAt）。 */
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
 * 镜头 Header：底图按镜头摆放 + 轻环境层（星光微闪、三团雾漂移，120s 时钟只在 draw 读）
 * + 底部 36% 渐隐到 NightBase。content 里用 map.at(图比例) 放光字与热区。
 */
@Composable
fun SkyShotHeader(
    height: Dp,
    zoom: Float,
    cx: Float,
    cy: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(SkyShotMap) -> Unit,
) {
    val painter = rememberAsyncImagePainter(request = ImageRequest(LocalContext.current, SKY_NIGHT_URI))
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val refW = with(density) { configuration.screenWidthDp.dp.toPx() }
    val refH = with(density) { configuration.screenHeightDp.dp.toPx() }
    val trans = rememberInfiniteTransition(label = "skyHeaderClock")
    val phaseState =
        trans.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(120_000, easing = LinearEasing)),
            label = "skyHeaderPhase",
        )
    BoxWithConstraints(
        modifier.fillMaxWidth().height(height).clipToBounds().background(SkyNightBase),
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val map = remember(w, h, refW, refH, zoom, cx, cy) { SkyShotMap(w, h, refW, refH, zoom, cx, cy) }
        Image(
            painter = painter,
            contentDescription = null,
            modifier =
                Modifier.offset { map.imageOffset }
                    .size(with(density) { map.iwS.toDp() }, with(density) { map.ihS.toDp() }),
            contentScale = ContentScale.FillBounds,
        )
        Canvas(Modifier.fillMaxSize()) {
            val ph = phaseState.value
            // 星光：固定星点（图比例），只画落在镜头内的
            for (i in 0 until 26) {
                val fx = ((i * 37 + 11) % 100) / 100f
                val fy = ((i * 23 + 5) % 42) / 100f
                val p = map.at(Offset(fx, fy))
                if (p.x < -20f || p.x > size.width + 20f || p.y < -20f || p.y > size.height + 20f) continue
                val tw = 0.25f + 0.55f * (0.5f + 0.5f * sin(2.0 * PI * (ph * 6f + i * 0.37f)).toFloat())
                drawCircle(SkyLabelGlow.copy(alpha = tw * 0.5f), 2.6f, p)
                drawCircle(Color.White.copy(alpha = tw), 1.1f, p)
            }
            // 雾：三团横漂
            val mistF = arrayOf(Offset(0.22f, 0.30f), Offset(0.55f, 0.40f), Offset(0.85f, 0.26f))
            for (k in mistF.indices) {
                val base = mistF[k]
                val drift = sin(2.0 * PI * (ph + k * 0.33f)).toFloat() * 0.012f
                val c = map.at(Offset(base.x + drift, base.y))
                val r = map.iwS * 0.16f
                withTransform({ scale(1f, 0.38f, pivot = c) }) {
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                listOf(SkyMistColor.copy(alpha = 0.20f), Color.Transparent),
                                center = c,
                                radius = r,
                            ),
                        radius = r,
                        center = c,
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxWidth()
                .fillMaxHeight(0.36f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, SkyNightBase)),
                ),
        )
        content(map)
    }
}

/** 发光字（同首页 LightLabel 配方）：雾斑垫底、光晕层 + 清晰层双层衬线字。 */
@Composable
fun SkyGlowLabel(
    line1: String,
    line2: String? = null,
    line1Color: Color = SkyLabelCore,
    line1Size: TextUnit = 15.sp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val glowPx = with(density) { 7.dp.toPx() }
    val minMistRadius = with(density) { 48.dp.toPx() }
    Box(
        modifier.drawBehind {
            val r = max(size.width * 0.8f, minMistRadius)
            val scaleY = (size.height * 2.2f) / (2f * r)
            withTransform({ scale(1f, scaleY, pivot = center) }) {
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            listOf(SkyMistColor.copy(alpha = 0.34f), Color.Transparent),
                            center = center,
                            radius = r,
                        ),
                    radius = r,
                    center = center,
                )
            }
        },
    ) {
        SkyLabelLines(line1, line1Color, line1Size, line2, glowLayer = true, glowPx = glowPx)
        SkyLabelLines(line1, line1Color, line1Size, line2, glowLayer = false, glowPx = glowPx)
    }
}

@Composable
private fun SkyLabelLines(
    line1: String,
    line1Color: Color,
    line1Size: TextUnit,
    line2: String?,
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
        )
        if (line2 != null) {
            BasicText(
                text = line2,
                modifier = Modifier.padding(top = 2.dp),
                style =
                    TextStyle(
                        color = if (glowLayer) SkyLabelGlow.copy(alpha = 0.5f) else SkyLabelCore.copy(alpha = 0.88f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Serif,
                        letterSpacing = 0.6.sp,
                        textAlign = TextAlign.Center,
                        shadow = shadow,
                    ),
            )
        }
    }
}

/** 雾海清单区背景：NightBase → DeepBase 竖向渐变 + 三条整页横漂雾带（120s 时钟只在 draw 读）。 */
@Composable
fun SkyMistBackdrop(modifier: Modifier = Modifier) {
    val trans = rememberInfiniteTransition(label = "skyMistClock")
    val phaseState =
        trans.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(120_000, easing = LinearEasing)),
            label = "skyMistPhase",
        )
    Box(modifier.background(Brush.verticalGradient(listOf(SkyNightBase, SkyDeepBase)))) {
        Canvas(Modifier.fillMaxSize()) {
            val ph = phaseState.value
            for (k in 0..2) {
                val fy = size.height * (0.24f + 0.27f * k)
                val fx = size.width * (0.5f + 0.34f * sin(2.0 * PI * (ph + k * 0.33f)).toFloat())
                val r = size.width * 0.62f
                val ctr = Offset(fx, fy)
                withTransform({ scale(1f, 0.16f, pivot = ctr) }) {
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                listOf(SkyLabelGlow.copy(alpha = 0.05f), Color.Transparent),
                                center = ctr,
                                radius = r,
                            ),
                        radius = r,
                        center = ctr,
                    )
                }
            }
        }
    }
}

/**
 * 光字清单行（雾海版式）：无卡片，主名衬线 17sp、副行 12.5sp，
 * 行间 0.5dp 发丝线，按压是一道横向光带。
 */
@Composable
fun SkyLightRow(
    title: String,
    summary: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val band by
        animateFloatAsState(
            targetValue = if (pressed) 1f else 0f,
            animationSpec = tween(if (pressed) 90 else 300),
            label = "skyRowBand",
        )
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .drawBehind {
                if (band > 0f) {
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                SkyLabelGlow.copy(alpha = 0.10f * band),
                                Color.Transparent,
                            ),
                        ),
                    )
                }
                val inset = 20.dp.toPx()
                drawRect(
                    SkyLabelGlow.copy(alpha = 0.14f),
                    Offset(inset, size.height - 0.5.dp.toPx()),
                    Size(size.width - inset * 2, 0.5.dp.toPx()),
                )
            }
            .clickable(src, null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(
                    text = title,
                    style =
                        TextStyle(
                            color = SkyLabelCore.copy(alpha = 0.95f),
                            fontSize = 17.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (summary != null) {
                    BasicText(
                        text = summary,
                        modifier = Modifier.padding(top = 2.dp),
                        style =
                            TextStyle(
                                color = SkyLabelCore.copy(alpha = 0.62f),
                                fontSize = 12.5.sp,
                                fontFamily = FontFamily.Serif,
                                letterSpacing = 0.4.sp,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            BasicText(
                text = "›",
                style =
                    TextStyle(
                        color = SkyLabelCore.copy(alpha = 0.5f),
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Serif,
                    ),
            )
        }
    }
}
