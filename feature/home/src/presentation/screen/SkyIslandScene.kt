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
import android.os.SystemClock
import android.provider.Settings
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

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

/** 岛上的一个浮石入口点。坐标同 [SkyPlace]：图比例。 */
@Immutable
data class SkyPackSpot(val id: String, val name: String, val at: Offset)

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
                // 齿轮工坊：字落在齿轮右下草沿，避开工坊棚顶（用户 2026-11-05：原位压棚顶像浮在旗帜上）
                SkyPlace(BottomBarDestination.Config, Offset(0.493f, 0.368f), 0.075f, Offset(0.545f, 0.415f)),
                // 巨树：点击圈罩树冠+树干，字落在树脚左前的草径上，避开树根树皮（用户 2026-11-05）
                SkyPlace(BottomBarDestination.Setting, Offset(0.260f, 0.270f), 0.150f, Offset(0.217f, 0.381f)),
            ),
    )

// ───────────────────────────── 场景 ─────────────────────────────

private val StoneCyan = Color(0xFF7FE3FF)
private val StoneMoteDx = floatArrayOf(-0.045f, -0.026f, -0.008f, 0.012f, 0.030f, 0.046f)
private val StoneMoteDy = floatArrayOf(0.012f, -0.004f, 0.016f, 0f, 0.010f, -0.008f)
private val FlareOrigin = Offset(0.640f, 0.232f)
private val FlareCtrl = Offset(0.630f, 0.140f)
private val FlareApex = Offset(0.545f, 0.105f)
private const val FlareMs = 5000f

@Immutable
private class FlareShot(val id: Long, val latencyMs: Int?, val down: String?, val up: String?)

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
    /** 岛上的浮石入口点（接入方生成；左石插件、右石流量统计）；点了走 [onOpenPack] 按 id 分流。 */
    packSpots: List<SkyPackSpot> = emptyList(),
    onOpenPack: (String) -> Unit = {},
) {
    val mainPager = LocalMainPagerState.current
    val handlePageChange = LocalHandlePageChange.current
    val active = mainPager.selectedPage == BottomBarDestination.Home.ordinal

    // 信号焰（飞艇灯笼放出的链路报告）：触发与节流都在场景内，闲时零开销
    val ctx = LocalContext.current
    val reduceMotion =
        remember {
            Settings.Global.getFloat(
                ctx.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }
    var flare by remember { mutableStateOf<FlareShot?>(null) }
    var lastFireAt by remember { mutableLongStateOf(0L) }
    var latencyFlareDone by remember { mutableStateOf(false) } // 本次连接是否已放过带延迟数字的焰火
    val stateNow by rememberUpdatedState(state)
    val activeNow by rememberUpdatedState(active)
    val tryFire = { minGapMs: Long ->
        val now = SystemClock.elapsedRealtime()
        val s = stateNow
        // 内容门：延迟与上下行全空时不放空焰、也不占节流，等数字到了再放带字的
        val hasContent = s.latencyMs != null || s.downText != null || s.upText != null
        if (s.connected && activeNow && flare == null && hasContent && now - lastFireAt >= minGapMs) {
            lastFireAt = now
            if (s.latencyMs != null) latencyFlareDone = true
            flare = FlareShot(now, s.latencyMs, s.downText, s.upText)
        }
    }
    val fireNow by rememberUpdatedState(tryFire)

    // 等延迟测出：连接初期内核还没给主节点测速，latencyMs 是空的；最多等 30 秒，
    // 等到再缓 400ms 让数字稳定；等不到也试放一次，放不放由 tryFire 的内容门决定。
    suspend fun awaitPing() {
        val got =
            withTimeoutOrNull(30_000) { snapshotFlow { stateNow.latencyMs }.first { it != null } } != null
        if (got) delay(400)
    }
    LaunchedEffect(state.connected) {
        if (state.connected) {
            awaitPing()
            fireNow(5_000L)
        }
    }
    LaunchedEffect(active) {
        if (active && stateNow.connected) {
            awaitPing()
            fireNow(60_000L)
        }
    }

    // 断开时清零带字焰火标记，下次连接重新等数字
    LaunchedEffect(state.connected) {
        if (!state.connected) latencyFlareDone = false
    }
    // 延迟首达补焰：等了 30 秒还没测出、后来才到的延迟，第一次出现时补放一发带数字的
    LaunchedEffect(state.latencyMs) {
        if (state.latencyMs != null && !latencyFlareDone) fireNow(5_000L)
    }

    // 全场题字（城堡状态/代理/配置/设置/插件）：进首页或连接状态变化时亮 4 秒，按压时也亮，
    // 平时隐去、只剩本体光晕指路（用户 2026-11-05 拍板：城堡状态也同此规矩，不常驻）
    var captionOn by remember { mutableStateOf(false) }
    LaunchedEffect(active, state.connected) {
        if (active) {
            captionOn = true
            delay(4000)
            captionOn = false
        } else {
            captionOn = false
        }
    }

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
    // 整岛浮沉：14s 一周期，只动底图（放大 3% 锁边），锚点漂移 ≤ 画高 0.35%，题字/光晕不跟随也看不出错位
    val floatPhase by
        breathe.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
            label = "skyFloat",
        )


    // 环境层驱动（灯火/炊烟/云雾/瀑布/航灯，见 SkyAmbientLayer.kt）：
    // 120s 时钟只在首页走、动画值只在 draw 阶段被读，不触发重组；未连接时灯火只剩零星几点。
    val ambientClock = remember { mutableFloatStateOf(23f) }
    LaunchedEffect(active, reduceMotion) {
        if (active && !reduceMotion) {
            var last = SystemClock.elapsedRealtime()
            while (true) {
                delay(33)
                val now = SystemClock.elapsedRealtime()
                ambientClock.floatValue = (ambientClock.floatValue + (now - last) / 1000f) % 120f
                last = now
            }
        }
    }
    val lively by
        animateFloatAsState(if (state.connected) 1f else 0f, tween(5000), label = "skyLively")
    val bellPulse = remember { Animatable(0f) }
    val chimePulse = remember { Animatable(0f) }
    LaunchedEffect(active, state.connected, reduceMotion) {
        if (active && state.connected && !reduceMotion) {
            while (true) {
                delay(kotlin.random.Random.nextLong(40_000, 75_000))
                bellPulse.snapTo(1f)
                bellPulse.animateTo(0f, tween(3000, easing = LinearEasing))
            }
        }
    }
    LaunchedEffect(active, state.connected, reduceMotion) {
        if (active && state.connected && !reduceMotion) {
            while (true) {
                delay(kotlin.random.Random.nextLong(25_000, 50_000))
                chimePulse.snapTo(0.8f)
                chimePulse.animateTo(0f, tween(2200, easing = LinearEasing))
            }
        }
    }
    var farSpec by remember { mutableStateOf(FarSpec(true, 0.09f)) }
    val farClock = remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(active, state.connected, reduceMotion) {
        if (active && state.connected && !reduceMotion) {
            var fromLeft = true
            while (true) {
                delay(kotlin.random.Random.nextLong(20_000, 45_000))
                fromLeft = !fromLeft
                farSpec = FarSpec(fromLeft, 0.06f + kotlin.random.Random.nextFloat() * 0.09f)
                val start = SystemClock.elapsedRealtime()
                while (true) {
                    val fp = (SystemClock.elapsedRealtime() - start) / 26_000f
                    if (fp >= 1f) break
                    farClock.floatValue = fp
                    delay(100)
                }
                farClock.floatValue = -1f
            }
        }
    }



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

        SkyBackdrop(
            painter = painter,
            sat = sat,
            dusk = dusk,
            floatY = { if (reduceMotion) 0f else sin(2.0 * PI * floatPhase).toFloat() * 0.0035f * map.ihS },
        )

        // 环境层：灯火渐次亮起、炊烟、云雾横漂、瀑布微光、远空航灯（只叠光与雾）
        SkyAmbientOverlay(
            map = map,
            fade = { labelsFade },
            t = { ambientClock.floatValue },
            lively = { lively },
            bell = { bellPulse.value },
            chime = { chimePulse.value },
            far = { if (farClock.floatValue < 0f) 0f else farClock.floatValue },
            farSpec = { farSpec },
        )

        // 天空垫：点岛顶空天放信号焰（已连接才响应）；无语义，不拦翻页手势
        val padSrc = remember { MutableInteractionSource() }
        Box(
            Modifier.centeredAt(map.at(Offset(0.460f, 0.103f)))
                .size(
                    with(density) { (0.32f * map.iwS).toDp() },
                    with(density) { (0.115f * map.ihS).toDp() },
                )
                .skyTap(padSrc) { fireNow(5_000L) }
                .clearAndSetSemantics {}
        )

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
                            // 本体光晕：题字隐去时靠它指路，贴着本体呼吸（同浮石口径，只叠光）
                            val hb = 0.5f + 0.5f * sin(2.0 * PI * (phase + index * 0.25)).toFloat()
                            val hr = 0.055f * map.iwS
                            drawCircle(
                                brush =
                                    Brush.radialGradient(
                                        listOf(
                                            LabelGlow.copy(alpha = (0.06f + 0.07f * hb) * labelsFade),
                                            Color.Transparent,
                                        ),
                                        center = center,
                                        radius = hr,
                                    ),
                                radius = hr,
                                center = center,
                                blendMode = BlendMode.Plus,
                            )
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

                val capA by
                    animateFloatAsState(
                        targetValue = if (captionOn || pressed) 1f else 0f,
                        animationSpec = tween(if (captionOn || pressed) 300 else 800),
                        label = "placeCaption",
                    )
                // 光字（纯视觉，点它也能跳；读屏只读点击圈）
                val labelPos = map.at(overrides[dest] ?: place.label)
                val labelModifier =
                    Modifier.centeredAt(labelPos) {
                            labelsFade * capA * (if (pressed) 1f else breatheAlpha(phase, index))
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
                            // 信号焰亮着时延迟归焰火独占，飞艇题字不再重复一遍
                            line2 = if (flare == null) proxyLine(state) else null,
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

        // 浮石入口：两块发光浮石（飞行石），不画新东西，只在原有光晕上叠光；左石=插件、右石=流量统计，两块都是正式焦点、各带自己的题字
        packSpots.forEachIndexed { index, spot ->
            key(spot.id) {
                val src = remember { MutableInteractionSource() }
                val pressed by src.collectIsPressedAsState()
                val bloom by
                    animateFloatAsState(
                        targetValue = if (pressed) 0.5f else 0f,
                        animationSpec = tween(if (pressed) 90 else 280),
                        label = "stoneBloom",
                    )
                val center = map.at(spot.at)
                val hitPx = max(0.15f * map.iwS, with(density) { 48.dp.toPx() })
                val dpPx = density.density
                Box(
                    Modifier.centeredAt(center)
                        .size(with(density) { hitPx.toDp() })
                        .drawBehind {
                            drawStone(phase, if (index == 0) 0f else 0.5f, bloom, labelsFade, map, dpPx)
                        }
                        .skyTap(src) { onOpenPack(spot.id) }
                        .semantics {
                            role = Role.Button
                            contentDescription = spot.name
                            onClick(action = { onOpenPack(spot.id); true })
                        }
                )
                run {
                    val capA by
                        animateFloatAsState(
                            targetValue = if (captionOn || pressed) 0.9f else 0f,
                            animationSpec = tween(if (captionOn || pressed) 300 else 800),
                            label = "stoneCaption",
                        )
                    LightLabel(
                        line1 = spot.name,
                        line1Color = LabelCore,
                        line1Size = 11.sp,
                        line2 = null,
                        modifier =
                            Modifier.centeredAt(map.at(spot.at + Offset(0.002f, 0.062f))) {
                                    capA * labelsFade
                                }
                                .clearAndSetSemantics {},
                    )
                }
            }
        }

        flare?.let { shot -> FlareLayer(shot, map, reduceMotion) { if (flare?.id == shot.id) flare = null } }


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

/** 飞艇第二行：只留延迟（上下行改由信号焰绽放时呈现，避免长行被屏幕右缘裁切）。 */
private fun proxyLine(s: SkyHomeState): AnnotatedString? =
    s.latencyMs?.let {
        buildAnnotatedString {
            withStyle(SpanStyle(color = latencyColor(it))) { append("$it ms") }
        }
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
internal class SkyMap(w: Float, h: Float, iw: Float, ih: Float) {
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
private fun SkyBackdrop(
    painter: androidx.compose.ui.graphics.painter.Painter,
    sat: Float,
    dusk: Float,
    floatY: () -> Float = { 0f },
) {
    Image(
        painter = painter,
        contentDescription = null,
        modifier =
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1.03f
                scaleY = 1.03f
                translationY = floatY()
            },
        contentScale = ContentScale.Crop,
        alignment = Alignment.Center, // 必须与 SkyMap 的居中裁切一致
        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(sat) }),
    )
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer {
                scaleX = 1.03f
                scaleY = 1.03f
                translationY = floatY()
            }
            .drawBehind { drawRect(DuskTint.copy(alpha = dusk)) }
    )
}

// ───────────────────────────── 飞行石与信号焰 ─────────────────────────────

/** 浮石光晕 + 光尘：只叠光，不画形。phase/off 决定呼吸相位，bloom 是按压绽光，fade 是图未就绪门控。 */
private fun DrawScope.drawStone(
    phase: Float,
    off: Float,
    bloom: Float,
    fade: Float,
    map: SkyMap,
    dp: Float,
) {
    val c = center
    val b = 0.5f + 0.5f * sin(2.0 * PI * (phase + off)).toFloat()
    val r = 0.060f * map.iwS
    drawCircle(
        Brush.radialGradient(
            listOf(
                StoneCyan.copy(alpha = ((0.12f + 0.16f * b + bloom * 0.4f) * fade).coerceIn(0f, 0.8f)),
                Color.Transparent,
            ),
            c,
            r,
        ),
        r,
        c,
        blendMode = BlendMode.Plus,
    )
    for (i in 0 until 6) {
        val t = (phase + i / 6f + off * 0.37f) % 1f
        val s = sin(PI * t).toFloat()
        val p =
            Offset(
                c.x + StoneMoteDx[i] * map.iwS + sin(2.0 * PI * (t + i * 0.2)).toFloat() * 0.004f * map.iwS,
                c.y + (StoneMoteDy[i] - t * 0.035f) * map.ihS,
            )
        val al = s * s * 0.85f * fade
        drawCircle(StoneCyan.copy(alpha = al * 0.35f), 3.2f * dp, p, blendMode = BlendMode.Plus)
        drawCircle(Color.White.copy(alpha = al), 1.1f * dp, p, blendMode = BlendMode.Plus)
    }
}

/**
 * 信号焰：从飞艇灯笼升空、绽放成一次链路报告（延迟层级色 + 光字三行）。
 * 单个 Animatable 驱动 5 秒，只在 draw/layer 阶段读值；同时只有一发。
 */
@Composable
private fun FlareLayer(shot: FlareShot, map: SkyMap, reduceMotion: Boolean, onDone: () -> Unit) {
    val clock = remember(shot.id) { Animatable(0f) }
    LaunchedEffect(shot.id) {
        clock.animateTo(FlareMs, tween(FlareMs.toInt(), easing = LinearEasing))
        onDone()
    }
    val density = LocalDensity.current
    val dpPx = density.density
    val ms = shot.latencyMs
    val tier = ms?.let { latencyColor(it) } ?: LabelGlow
    val core = lerp(tier, Color.White, 0.55f)
    val (n, droop, rK) =
        when {
            ms == null -> Triple(28, 0f, 1f)
            ms <= 150 -> Triple(40, 0f, 1f)
            ms <= 400 -> Triple(32, 0.02f, 1f)
            else -> Triple(24, 0.04f, 0.9f)
        }
    val ang =
        remember(shot.id) {
            FloatArray(n) { i ->
                val inner = i % 3 == 2
                (2.0 * PI * i / n).toFloat() +
                    ((i * 37 % 11) / 11f - 0.5f) * 0.18f +
                    if (inner) (PI / n).toFloat() else 0f
            }
        }
    val rk =
        remember(shot.id) {
            FloatArray(n) { i -> if (i % 3 == 2) 0.55f else 0.92f + (i * 13 % 7) / 7f * 0.08f }
        }
    val textA: () -> Float = {
        val t = clock.value
        when {
            t < 1000f -> 0f
            t < 1350f -> (t - 1000f) / 350f
            t < 4300f -> 1f
            else -> (1f - (t - 4300f) / 700f).coerceIn(0f, 1f)
        }
    }
    val a = map.at(FlareApex)

    Canvas(Modifier.fillMaxSize()) {
        val t = clock.value
        val o = map.at(FlareOrigin)
        val c = map.at(FlareCtrl)
        val dp = dpPx
        if (!reduceMotion && t < 700f) {
            // 升空：二次贝塞尔 + 9 点彗尾
            val p = 1f - (1f - t / 700f).let { it * it * it }
            for (k in 0 until 9) {
                val u = (p - k * 0.04f).coerceAtLeast(0f)
                val w = 1f - u
                val pt =
                    Offset(
                        w * w * o.x + 2 * w * u * c.x + u * u * a.x,
                        w * w * o.y + 2 * w * u * c.y + u * u * a.y,
                    )
                val f = 1f - k / 9f
                drawCircle(tier.copy(alpha = 0.30f * f), (5f - k * 0.4f) * dp, pt, blendMode = BlendMode.Plus)
                drawCircle(
                    core.copy(alpha = 0.9f * f),
                    (1.6f - k * 0.1f).coerceAtLeast(0.6f) * dp,
                    pt,
                    blendMode = BlendMode.Plus,
                )
            }
        }
        val tb = (t - 700f) / 1900f
        if (!reduceMotion && tb in 0f..1f) {
            // 绽放：双层牡丹
            if (tb < 0.13f) {
                val fr = 0.06f * map.iwS
                drawCircle(
                    Brush.radialGradient(
                        listOf(core.copy(alpha = 0.55f * (1f - tb / 0.13f)), Color.Transparent),
                        a,
                        fr,
                    ),
                    fr,
                    a,
                    blendMode = BlendMode.Plus,
                )
            }
            val e = 1f - (1f - tb).let { it * it * it }
            val fade = if (tb < 0.12f) tb / 0.12f else (1f - (tb - 0.12f) / 0.88f).let { it * sqrt(it) }
            val rr = 0.14f * rK * map.iwS
            val drop = droop * map.ihS * tb * tb
            for (i in 0 until n) {
                val al = fade * (0.78f + 0.22f * sin(t * 0.018f + i * 1.7f))
                val pt = Offset(a.x + cos(ang[i]) * rr * rk[i] * e, a.y + sin(ang[i]) * rr * rk[i] * e + drop)
                drawCircle(tier.copy(alpha = 0.28f * al), 4.2f * dp, pt, blendMode = BlendMode.Plus)
                drawCircle(core.copy(alpha = 0.95f * al), 1.5f * dp, pt, blendMode = BlendMode.Plus)
            }
        }
        val ta = textA()
        if (ta > 0f) {
            // 余光衬字
            val hr = 0.09f * map.iwS
            drawCircle(
                Brush.radialGradient(listOf(tier.copy(alpha = 0.16f * ta), Color.Transparent), a, hr),
                hr,
                a,
                blendMode = BlendMode.Plus,
            )
        }
    }

    val speeds = listOfNotNull(shot.down?.let { "↓ $it" }, shot.up?.let { "↑ $it" }).joinToString("\n")
    if (ms != null || speeds.isNotEmpty()) {
        LightLabel(
            line1 = ms?.let { "$it ms" } ?: speeds.substringBefore("\n"),
            line1Color = tier,
            line1Size = 20.sp,
            line2 =
                (if (ms != null) speeds else speeds.substringAfter("\n", ""))
                    .takeIf { it.isNotEmpty() }
                    ?.let { AnnotatedString(it) },
            modifier = Modifier.centeredAt(a) { textA() * 0.95f }.clearAndSetSemantics {},
        )
    }
}
