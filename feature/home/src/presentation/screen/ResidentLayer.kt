package com.suanran.dreambox.feature.home.presentation.screen

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suanran.dreambox.presentation.component.sky.SkyGlowLabel
import java.util.Calendar
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * 底图的世界变换：放大 1.03 + 纵向浮沉。SkyBackdrop 用的就是这两个数。
 * 居民层必须和底图在「同一个变换」里，否则底图一浮沉人就相对地面滑动（隐形的飘感来源）。
 */
internal const val SKY_WORLD_SCALE = 1.03f

@Immutable
private class ResidentBubble(
    val id: Int,
    val who: Int,
    val line1: String,
    val line2: String?,
    val bornMs: Long,
    val holdMs: Long,
)

/**
 * 居民层。放在 SkyIslandScene 的世界里、飞行石之后、信号焰之前（见 residents-notes.md §3）。
 *
 * 结构：一个和底图同变换的 Box ──┬─ Canvas：影子 + 精灵（一遍画完，draw 阶段读时钟，零重组）
 *                               ├─ 每人一个小点击区（graphicsLayer 平移跟着脚走；只盖精灵本体）
 *                               └─ 气泡（光字 + 雾斑，不是卡片）
 */
@Composable
internal fun ResidentLayer(
    map: SkyMap,
    screenW: Float,
    state: SkyHomeState,
    active: Boolean,
    reduceMotion: Boolean,
    fade: () -> Float,
    sat: () -> Float,
    dusk: () -> Float,
    floatY: () -> Float,
    modifier: Modifier = Modifier,
    roster: List<ResidentSpec> = ResidentRoster.night,
    debug: Boolean = false,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val aspect = map.iwS / map.ihS
    val world = remember(roster, aspect) { ResidentWorld(roster, aspect) }
    val tick = remember { mutableLongStateOf(0L) }
    val talker = remember { ResidentTalker() }
    val talkNow by rememberUpdatedState(state.toTalk())

    // 按屏幕尺寸预算的常量，全部 FloatArray，draw 阶段不分配
    val bodyPx = remember(roster, map.ihS) { FloatArray(roster.size) { roster[it].gait.heightFrac * map.ihS } }
    val clipY =
        remember(roster, map.ihS) {
            FloatArray(roster.size) { i -> roster[i].occludeBelowY?.let { map.at(Offset(0f, it)).y } ?: -1f }
        }
    val shadowBrushes =
        remember(roster, map.ihS) {
            roster.mapIndexed { i, s ->
                Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = s.shadowAlpha), Color.Transparent),
                    center = Offset.Zero,
                    radius = s.shadowWidthBh * bodyPx[i] / 2f,
                )
            }
        }
    val tint = remember { TintCache() }

    // 精灵在 IO 线程解码并预缩放；缺哪个，那位居民就先不出现（不崩）
    val sprites by
        produceState<List<ResidentSprites?>>(emptyList(), roster, map.ihS) {
            value =
                withContext(Dispatchers.IO) {
                    roster.mapIndexed { i, s -> ResidentSpriteLoader.load(ctx, s.sprites, bodyPx[i], s.gait.walkFrames) }
                }
        }

    // 帧驱动：每个真实帧一次，dt 取真实间隔（封顶 50ms 防暂停后跳变）。
    // 不用场景那条 33ms 的 delay 时钟：30Hz 步进在 60/120Hz 屏上自带顿挫。页面不在前台时协程被取消。
    LaunchedEffect(world, active, reduceMotion) {
        if (reduceMotion) {
            world.settleAll()
            tick.longValue++
            return@LaunchedEffect
        }
        if (!active) return@LaunchedEffect
        var last = 0L
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            if (last != 0L) {
                world.advance(((now - last) / 1_000_000_000f).coerceAtMost(0.05f))
                tick.longValue++
            }
            last = now
        }
    }

    // ── 说话 ──
    var bubbles by remember { mutableStateOf(emptyList<ResidentBubble>()) }
    var nextId by remember { mutableIntStateOf(0) }
    val say: (Int, String) -> Unit = { who, text ->
        val (l1, l2) = splitBubble(text)
        val b = ResidentBubble(nextId++, who, l1, l2, SystemClock.elapsedRealtime(), bubbleHoldMs(text))
        bubbles = (bubbles.filter { it.who != who } + b).takeLast(2) // 同屏最多两个气泡
        world.walkers[who].interrupt(b.holdMs / 1000f) // 说话时站住，说完接着走
    }
    val sayNow by rememberUpdatedState(say)
    val onTap: (Int) -> Unit = { i -> sayNow(i, talker.report(roster[i].id, talkNow)) }
    val onTapNow by rememberUpdatedState(onTap)

    // 平时低频自发冒泡：9~18 秒一句，屏上已有气泡就让一让
    LaunchedEffect(world, active) {
        if (!active) return@LaunchedEffect
        delay(Random.nextLong(3500, 6500))
        while (true) {
            if (bubbles.isEmpty()) {
                val i = Random.nextInt(roster.size)
                sayNow(i, talker.chatter(roster[i].id, talkNow))
            }
            delay(Random.nextLong(9000, 18000))
        }
    }

    // ── 世界层：和 SkyBackdrop 同一个变换（放大 + 浮沉）；整层随底图淡入 ──
    Box(
        modifier.fillMaxSize().graphicsLayer {
            scaleX = SKY_WORLD_SCALE
            scaleY = SKY_WORLD_SCALE
            translationY = floatY()
            alpha = fade()
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            tick.longValue // 只在 draw 阶段订阅：每帧只重绘，不重组
            val sp = sprites
            if (sp.isEmpty()) return@Canvas
            val cf = tint.get(sat(), dusk())
            for (oi in world.drawOrder.indices) {
                val i = world.drawOrder[oi]
                val s = sp.getOrNull(i) ?: continue
                val w = world.walkers[i]
                val spec = roster[i]
                val body = bodyPx[i]
                val foot = map.at(Offset(w.footX, w.footY)) // 脚底 = 地面线上的点，唯一来源
                val cb = if (clipY[i] >= 0f) clipY[i] else size.height
                withTransform({ clipRect(0f, 0f, size.width, cb) }) {
                    // 接触影：钉在脚底地面点，只随位置变，不吃起伏
                    withTransform({
                        translate(foot.x, foot.y)
                        scale(1f, spec.shadowHeightBh / spec.shadowWidthBh, pivot = Offset.Zero)
                    }) {
                        drawCircle(shadowBrushes[i], radius = spec.shadowWidthBh * body / 2f, center = Offset.Zero)
                    }
                    val fr =
                        when (w.pose) {
                            Pose.Walk -> s.walkFrame(w.frame)
                            Pose.Action -> s.actionFrame(w.actionKey, w.frame)
                            Pose.Stand -> s.stand
                        }
                    // 精灵：原点 = 脚底中心（实测），按每张图自己的宽高锚定；翻转以脚底为轴；呼吸以脚底为轴
                    withTransform({
                        translate(foot.x + w.swayBh * body, foot.y - w.bobBh * body)
                        scale(if (w.facingLeft) -1f else 1f, w.breath, pivot = Offset.Zero)
                    }) {
                        drawImage(fr.image, topLeft = Offset(-fr.footX, -fr.h.toFloat()), colorFilter = cf)
                    }
                }
            }
            if (debug) {
                for (i in roster.indices) {
                    val g = roster[i].ground
                    for (k in 1 until g.size) {
                        drawLine(Color.Cyan, map.at(Offset(g[k - 1].x, g[k - 1].y)), map.at(Offset(g[k].x, g[k].y)), 2f)
                    }
                    roster[i].stops.forEach { drawCircle(Color.Yellow, 5f, map.at(Offset(it.at.x, it.at.y))) }
                    val w = world.walkers[i]
                    drawCircle(Color.Red, 4f, map.at(Offset(w.footX, w.footY)))
                    if (clipY[i] >= 0f) drawLine(Color.Magenta, Offset(0f, clipY[i]), Offset(size.width, clipY[i]), 1.5f)
                }
            }
        }

        // 点击区：只盖精灵本体（宽按实测帧宽，高按身高），不够 minDp 才补到 minDp
        roster.forEachIndexed { i, spec ->
            key(spec.id) {
                val spriteW = sprites.getOrNull(i)?.stand?.w?.toFloat() ?: (bodyPx[i] * 0.5f)
                val minPx = spec.hitMinDp * density.density
                val hitW = max(spriteW * 1.15f, minPx)
                val hitH = max(bodyPx[i] * 1.12f, minPx)
                val w = world.walkers[i]
                Box(
                    Modifier.size(with(density) { hitW.toDp() }, with(density) { hitH.toDp() })
                        .graphicsLayer {
                            tick.longValue
                            val p = map.at(Offset(w.footX, w.footY))
                            translationX = p.x - hitW / 2f
                            translationY = p.y - hitH + hitH * 0.06f
                        }
                        .pointerInput(spec.id) { detectTapGestures { onTapNow(i) } }
                        .semantics {
                            role = Role.Button
                            contentDescription = spec.label
                            onClick(action = { onTapNow(i); true })
                        }
                        .then(if (debug) Modifier.debugBox() else Modifier)
                )
            }
        }

        // 气泡：光字 + 雾斑（SkyGlowLabel），跟着人头走，不是矩形卡片、不是 Toast
        bubbles.forEach { b ->
            key(b.id) {
                LaunchedEffect(b.id) {
                    delay(b.holdMs + 800)
                    bubbles = bubbles.filterNot { it.id == b.id }
                }
                SkyGlowLabel(
                    line1 = b.line1,
                    line2 = b.line2?.let { AnnotatedString(it) },
                    line1Size = 12.sp,
                    line2Size = 12.sp,
                    modifier =
                        Modifier.graphicsLayer {
                                tick.longValue
                                val w = world.walkers[b.who]
                                val p = map.at(Offset(w.footX, w.footY))
                                val m = 10.dp.toPx()
                                translationX = (p.x - size.width / 2f).coerceIn(m, max(m, screenW - size.width - m))
                                translationY = p.y - bodyPx[b.who] * 1.25f - size.height
                                alpha =
                                    if (reduceMotion) 1f
                                    else bubbleAlpha(SystemClock.elapsedRealtime() - b.bornMs, b.holdMs)
                            }
                            .clearAndSetSemantics {},
                )
            }
        }

        if (debug) {
            val t = tick.longValue
            Column(Modifier.align(Alignment.BottomStart).padding(bottom = 24.dp, start = 8.dp)) {
                world.walkers.forEach { BasicText(it.debugLine(), style = TextStyle(color = Color.White, fontSize = 10.sp)) }
                BasicText("t=$t", style = TextStyle(color = Color.White, fontSize = 10.sp))
            }
        }
    }
}

/** debug：把点击区描红，方便确认只盖精灵本体、没压到城堡/飞艇/齿轮/巨树的热区。 */
private fun Modifier.debugBox(): Modifier =
    this.drawBehind { drawRect(Color.Red.copy(alpha = 0.7f), style = Stroke(width = 2.dp.toPx())) }

/** 淡入 250ms → 保持 → 淡出 600ms。 */
private fun bubbleAlpha(ageMs: Long, holdMs: Long): Float =
    when {
        ageMs < 0 -> 0f
        ageMs < 250 -> ageMs / 250f
        ageMs <= holdMs -> 1f
        ageMs < holdMs + 600 -> 1f - (ageMs - holdMs) / 600f
        else -> 0f
    }

/** 居民和底图同色调：同样的饱和度（未连接降到 0.8）+ 同样的暮色压暗，否则连接/断开时人会比景「亮」一截。 */
private class TintCache {
    private var s = Float.NaN
    private var d = Float.NaN
    private var f: ColorFilter? = null

    fun get(sat: Float, dusk: Float): ColorFilter? {
        if (sat == s && dusk == d) return f
        s = sat
        d = dusk
        f =
            if (sat >= 0.999f && dusk <= 0.001f) {
                null
            } else {
                val sr = (1f - sat) * 0.213f
                val sg = (1f - sat) * 0.715f
                val sb = (1f - sat) * 0.072f
                val k = 1f - dusk
                // DuskTint = 0xFF141B3D
                ColorFilter.colorMatrix(
                    ColorMatrix(
                        floatArrayOf(
                            k * (sr + sat), k * sg, k * sb, 0f, dusk * 20f,
                            k * sr, k * (sg + sat), k * sb, 0f, dusk * 27f,
                            k * sr, k * sg, k * (sb + sat), 0f, dusk * 61f,
                            0f, 0f, 0f, 1f, 0f,
                        )
                    )
                )
            }
        return f
    }
}

/** SkyHomeState → 台词用的真实状态。 */
internal fun SkyHomeState.toTalk(): ResidentTalkState =
    ResidentTalkState(
        connected = connected,
        nodeName = nodeName,
        uptimeText = uptimeText,
        latencyMs = latencyMs,
        downText = downText,
        upText = upText,
        todayTrafficText = todayTrafficText,
        profileName = profileName,
        quotaLeftText = quotaLeftText,
        hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
    )
