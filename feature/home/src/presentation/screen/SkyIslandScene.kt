/*
 * DreamBox home: full-bleed sky-island scene.
 *
 * The four BottomBarDestination modules become four places on the painting:
 *   Home = castle (label shows connection state), Proxy = airship,
 *   Config = gear workshop, Setting = giant tree.
 * Names are glow "light labels" (never drawn objects); state is carried by the scene itself.
 *
 * NOT compiled here (no Gradle in the authoring sandbox) – expect to fix an import or two.
 */
package com.suanran.dreambox.feature.home.presentation.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import com.github.panpf.sketch.rememberAsyncImagePainter
import com.github.panpf.sketch.request.ImageRequest
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.suanran.dreambox.presentation.component.navigation.BottomBarDestination
import com.suanran.dreambox.presentation.component.navigation.LocalHandlePageChange
import com.suanran.dreambox.presentation.component.navigation.LocalMainPagerState
import java.util.Locale
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// ───────────────────────────── 数据 ─────────────────────────────

/** 首页要显示的状态。字符串由首页按现有格式化/多语言准备好，场景只负责摆放。 */
@Immutable
data class SkyHomeState(
    val connected: Boolean,
    /** 「已连接 / 未连接」（FlyTxt 现有文案）。 */
    val statusText: String,
    /** 城堡第二行，如「DMIT-EB · 00:42:17」；没有就传 null。 */
    val detailText: String? = null,
    /** 毫秒；未知/未连接传 null。 */
    val latencyMs: Int? = null,
    /** 已格式化的速率，如「4.1 KB」；空闲传 null（该项不显示）。 */
    val downText: String? = null,
    val upText: String? = null,
)

/** 一处地点。所有坐标都是「图比例」（0..1，相对原图宽高），不是屏幕比例。 */
@Immutable
data class SkyPlace(
    val destination: BottomBarDestination,
    /** 点击圈中心。 */
    val hit: Offset,
    /** 点击圈半径，占图宽的比例（实际不小于 24dp）。 */
    val hitRadius: Float,
    /** 光字块中心（两行的话是整块的中心）。 */
    val label: Offset,
)

/** 一张图 + 这张图专属的锚点表。白天图以后单独做一份，别和夜景共用数字。 */
@Immutable
class SkyArt(val assetUri: String, val aspect: Float, val places: List<SkyPlace>)

/** 夜景图（1120×2240）锚点，按 v23 实图量得的值修订；用 debug=true 拖动微调。 */
fun skyNightArt(assetUri: String) =
    SkyArt(
        assetUri = assetUri,
        aspect = 1120f / 2240f,
        places =
            listOf(
                // 城堡：光字放在城堡上方的空天，避开下面的齿轮
                SkyPlace(BottomBarDestination.Home, Offset(0.50f, 0.287f), 0.075f, Offset(0.51f, 0.238f)),
                // 飞艇：点击圈罩住整个气囊+船身，光字放在气囊上方的空天
                SkyPlace(BottomBarDestination.Proxy, Offset(0.765f, 0.250f), 0.140f, Offset(0.765f, 0.098f)),
                // 齿轮工坊：字落在崖顶
                SkyPlace(BottomBarDestination.Config, Offset(0.493f, 0.368f), 0.075f, Offset(0.493f, 0.410f)),
                // 巨树：点击圈罩树冠+树干，字落在树根草地
                SkyPlace(BottomBarDestination.Setting, Offset(0.260f, 0.270f), 0.150f, Offset(0.270f, 0.347f)),
            ),
    )

// ───────────────────────────── 场景 ─────────────────────────────

private val NightBase = Color(0xFF0B1636)
private val DuskTint = Color(0xFF141B3D)
private val MistColor = Color(0xFF050B1F)
private val LabelCore = Color(0xFFEAF3FF)
private val LabelGlow = Color(0xFF8FC8FF)
private val StateOk = Color(0xFFA6EBCB)
private val StateOff = Color(0xFFB9C3D9)

/**
 * 满幅铺在首页（pager 第 0 页）。放进去时不要吃 Scaffold 的 contentPadding，也不要再用
 * rememberBottomBarReservedHeight()——这一页没有底栏了。
 *
 * 点击走和底栏同一条路：LocalHandlePageChange.current(index)。
 */
@Composable
fun SkyIslandScene(
    state: SkyHomeState,
    art: SkyArt,
    modifier: Modifier = Modifier,
    debug: Boolean = false,
    /** 已在首页时点城堡的动作（首页接入方接启停）；其它地点仍走翻页。 */
    onHomeClick: (() -> Unit)? = null,
) {
    val mainPager = LocalMainPagerState.current
    val handlePageChange = LocalHandlePageChange.current
    val active = mainPager.selectedPage == BottomBarDestination.Home.ordinal

    LightStatusBarIcons(active)

    // 状态由画面表达：未连接 → 饱和度降到 0.8 并压一层 10% 暮色
    val sat by animateFloatAsState(if (state.connected) 1f else 0.8f, tween(700), label = "skySat")
    val dusk by animateFloatAsState(if (state.connected) 0f else 0.10f, tween(700), label = "skyDusk")

    // 标签呼吸：整个场景一条时间线，各标签错相位
    val breathe = rememberInfiniteTransition(label = "skyBreathe")
    val phase by
        breathe.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(4500, easing = LinearEasing)),
            label = "skyPhase",
        )

    // debug：拖动后的光字位置（图比例），屏幕左上角实时读数，抄回 skyNightArt
    val overrides = remember { mutableStateMapOf<BottomBarDestination, Offset>() }
    val painter = rememberAsyncImagePainter(request = ImageRequest(LocalContext.current, art.assetUri))

    // 光字门控只管「加载中」：图就绪即放行；1.2s 超时也放行，图缺失/加载失败时退到 NightBase 底色+光字，不让首页失联
    var timedOut by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1200)
        timedOut = true
    }

    BoxWithConstraints(modifier.fillMaxSize().background(NightBase)) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val sz = painter.intrinsicSize
        val artReady = sz.isSpecified && sz.width > 0f
        val labelsReady = artReady || timedOut
        val labelsFade by
            animateFloatAsState(
                targetValue = if (labelsReady) 1f else 0f,
                animationSpec = tween(300),
                label = "skyLabelsFade",
            )
        val iw = art.aspect
        val ih = 1f
        val map = remember(w, h, iw, ih) { SkyMap(w, h, iw, ih) }

        SkyBackdrop(painter = painter, sat = sat, dusk = dusk)

        art.places.forEachIndexed { index, place ->
            key(place.destination) {
                val dest = place.destination
                val src = remember { MutableInteractionSource() }
                val pressed by src.collectIsPressedAsState()
                val bloom by
                    animateFloatAsState(
                        targetValue = if (pressed) 0.5f else 0f,
                        animationSpec = tween(if (pressed) 90 else 280),
                        label = "skyBloom",
                    )
                val go: () -> Unit = {
                    if (dest.ordinal != mainPager.selectedPage) {
                        handlePageChange(dest.ordinal)
                    } else if (dest == BottomBarDestination.Home) {
                        onHomeClick?.invoke()
                    }
                }
                val goState = rememberUpdatedState(go)

                // 点击圈（也是 TalkBack 的唯一焦点）
                val hitCenter = map.at(place.hit)
                val hitDiameterPx =
                    max(place.hitRadius * map.iwS * 2f, with(density) { 48.dp.toPx() })
                val description =
                    if (dest == BottomBarDestination.Home) {
                        state.statusText + (state.detailText?.let { "，$it" } ?: "")
                    } else {
                        dest.label
                    }
                Box(
                    Modifier.centeredAt(hitCenter)
                        .size(with(density) { hitDiameterPx.toDp() })
                        .drawBehind {
                            val a = bloom
                            if (a > 0.01f) {
                                val r = size.minDimension * 0.75f
                                drawCircle(
                                    brush =
                                        Brush.radialGradient(
                                            listOf(LabelGlow.copy(alpha = a), Color.Transparent),
                                            center = center,
                                            radius = r,
                                        ),
                                    radius = r,
                                )
                            }
                            if (debug) {
                                drawCircle(
                                    Color.Red.copy(alpha = 0.7f),
                                    radius = size.minDimension / 2f,
                                    style = Stroke(width = 2.dp.toPx()),
                                )
                            }
                        }
                        .skyTap(src) { goState.value() }
                        .semantics {
                            role = Role.Button
                            contentDescription = description
                            onClick(action = { goState.value(); true })
                        }
                )

                // 光字（纯视觉，点它也能跳；读屏只读点击圈）
                val labelPos = map.at(overrides[dest] ?: place.label)
                val labelModifier =
                    Modifier.centeredAt(labelPos) {
                            labelsFade * (if (pressed) 1f else breatheAlpha(phase, index))
                        }
                        .then(
                            if (debug) {
                                Modifier.pointerInput(map) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        val cur = overrides[dest] ?: place.label
                                        overrides[dest] =
                                            cur + Offset(drag.x / map.iwS, drag.y / map.ihS)
                                    }
                                }
                            } else {
                                Modifier.skyTap(src) { goState.value() }
                            }
                        )
                        .clearAndSetSemantics {}

                when (dest) {
                    BottomBarDestination.Home ->
                        LightLabel(
                            line1 = state.statusText,
                            line1Color = if (state.connected) StateOk else StateOff,
                            line1Size = 15.sp,
                            line2 = state.detailText?.let { AnnotatedString(it) },
                            modifier = labelModifier,
                        )
                    BottomBarDestination.Proxy ->
                        LightLabel(
                            line1 = dest.label,
                            line1Color = LabelCore,
                            line1Size = 13.sp,
                            line2 = proxyLine(state),
                            modifier = labelModifier,
                        )
                    else ->
                        LightLabel(
                            line1 = dest.label,
                            line1Color = LabelCore,
                            line1Size = 13.sp,
                            line2 = null,
                            modifier = labelModifier,
                        )
                }
            }
        }

        if (debug) {
            Column(
                Modifier.align(Alignment.TopStart)
                    .padding(top = 56.dp, start = 8.dp)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(6.dp)
            ) {
                art.places.forEach { p ->
                    val f = overrides[p.destination] ?: p.label
                    BasicText(
                        text =
                            "${p.destination.name} label=Offset(" +
                                "${"%.3f".format(Locale.US, f.x)}f, ${"%.3f".format(Locale.US, f.y)}f)",
                        style = TextStyle(color = Color.White, fontSize = 11.sp),
                    )
                }
            }
        }
    }
}

// ───────────────────────────── 光字 ─────────────────────────────

/**
 * 发光字：底层带光晕的模糊字 + 上层清晰字，字后垫一团「雾斑」（氛围阴影，不是物体），
 * 保证在较亮的云/草地上也读得出。
 */
@Composable
private fun LightLabel(
    line1: String,
    line1Color: Color,
    line1Size: TextUnit,
    line2: AnnotatedString?,
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
                            listOf(MistColor.copy(alpha = 0.34f), Color.Transparent),
                            center = center,
                            radius = r,
                        ),
                    radius = r,
                    center = center,
                )
            }
        }
    ) {
        Box(Modifier.clearAndSetSemantics {}) {
            LabelLines(line1, line1Color, line1Size, line2, glowLayer = true, glowPx = glowPx)
        }
        LabelLines(line1, line1Color, line1Size, line2, glowLayer = false, glowPx = glowPx)
    }
}

@Composable
private fun LabelLines(
    line1: String,
    line1Color: Color,
    line1Size: TextUnit,
    line2: AnnotatedString?,
    glowLayer: Boolean,
    glowPx: Float,
) {
    val shadow = if (glowLayer) Shadow(LabelGlow.copy(alpha = 0.7f), Offset.Zero, glowPx) else null
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            text = line1,
            style =
                TextStyle(
                    color =
                        if (glowLayer) LabelGlow.copy(alpha = 0.55f)
                        else line1Color.copy(alpha = 0.95f),
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
                text = if (glowLayer) AnnotatedString(line2.text) else line2,
                modifier = Modifier.padding(top = 2.dp),
                style =
                    TextStyle(
                        color =
                            if (glowLayer) LabelGlow.copy(alpha = 0.5f)
                            else LabelCore.copy(alpha = 0.88f),
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

/** 飞艇第二行：延迟（按阈值染色）+ 上下行；都没有就整行不显示。 */
private fun proxyLine(s: SkyHomeState): AnnotatedString? {
    val text =
        buildAnnotatedString {
            s.latencyMs?.let {
                withStyle(SpanStyle(color = latencyColor(it))) { append("$it ms") }
            }
            if (s.downText != null || s.upText != null) {
                if (length > 0) append("  ")
                append("↓ ${s.downText ?: "0"}  ↑ ${s.upText ?: "0"}")
            }
        }
    return text.takeIf { it.isNotEmpty() }
}

private fun latencyColor(ms: Int): Color =
    when {
        ms <= 150 -> Color(0xFFBFE6FF) // 冷白蓝
        ms <= 400 -> Color(0xFFF2C46B) // 暖金
        else -> Color(0xFFFF8F7A) // 柔珊瑚
    }

private fun breatheAlpha(phase: Float, index: Int): Float =
    0.835f + 0.115f * sin(2.0 * PI * (phase + index * 0.25)).toFloat()

// ───────────────────────────── 坐标与手势 ─────────────────────────────

/** 图比例 → 屏幕像素，和 ContentScale.Crop + Alignment.Center 完全同一套算法。 */
private class SkyMap(w: Float, h: Float, iw: Float, ih: Float) {
    private val s = max(w / iw, h / ih)
    val iwS = iw * s
    val ihS = ih * s
    private val ox = (w - iwS) / 2f
    private val oy = (h - ihS) / 2f

    fun at(f: Offset) = Offset(ox + f.x * iwS, oy + f.y * ihS)
}

/** 把自己的中心放到屏幕坐标 p；先 offset 再 graphicsLayer 平移，命中区域跟着走。 */
private fun Modifier.centeredAt(p: Offset, alpha: () -> Float = { 1f }): Modifier =
    this.offset { IntOffset(p.x.roundToInt(), p.y.roundToInt()) }
        .graphicsLayer {
            translationX = -size.width / 2f
            translationY = -size.height / 2f
            this.alpha = alpha()
        }

/** 不带 clickable 语义的点击，按下/松开会发给 src，驱动光晕。 */
private fun Modifier.skyTap(src: MutableInteractionSource, onTap: () -> Unit): Modifier =
    pointerInput(src) {
        detectTapGestures(
            onPress = { pos ->
                val press = PressInteraction.Press(pos)
                src.emit(press)
                src.emit(
                    if (tryAwaitRelease()) PressInteraction.Release(press)
                    else PressInteraction.Cancel(press)
                )
            },
            onTap = { onTap() },
        )
    }

// ───────────────────────────── 状态栏 ─────────────────────────────

/** 整页是深色画，浅色主题下状态栏图标要改成浅色；离开首页还原。 */
@Composable
private fun LightStatusBarIcons(active: Boolean) {
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

// ───────────────────────────── 底图 ─────────────────────────────

/** 底图单独一层：饱和度（未连接降到 0.8）+ 暮色压暗；上层光字每秒更新不碰它。 */
@Composable
private fun SkyBackdrop(painter: androidx.compose.ui.graphics.painter.Painter, sat: Float, dusk: Float) {
    Image(
        painter = painter,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        alignment = Alignment.Center, // 必须与 SkyMap 的居中裁切一致
        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(sat) }),
    )
    Box(Modifier.fillMaxSize().drawBehind { drawRect(DuskTint.copy(alpha = dusk)) })
}
