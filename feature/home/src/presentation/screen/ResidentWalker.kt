package com.suanran.dreambox.feature.home.presentation.screen

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * 居民行走引擎（纯 Kotlin，不依赖 Compose / Android，可直接 JVM 单测）。
 *
 * 唯一的设计原则：脚在哪、哪一帧、身体起不起伏，全部是「沿地面线走过的距离 d」的函数。
 *
 *   d  ──►  相位 φ = d / 步幅          （走了几步）
 *           帧号   = f(floor φ, frac φ)  （φ 过整数 = 脚落地 = 换帧）
 *           起伏   = sin(π·frac φ)       （φ 为整数时恰为 0 = 最低点 = 换帧那一刻）
 *           脚底   = 地面线.sample(s0 ± d)（y 只来自折线，别处不许加 y）
 *
 * 速度 v = 步幅 × 步频 由构造保证：步频不是一个独立参数，它就是 v / 步幅。
 * 每段路开始前按路长取整步数 n，把步幅微调成 路长/n（±10% 内），所以每段路都恰好在落脚点收步。
 *
 * 所有长度单位：「图宽」。图比例里 y 方向的 1 个单位 = 1/aspect 个图宽（1120×2240 时 1 个图高 = 2 个图宽）。
 */

internal enum class ResidentId { Keeper, Courier, Smith, Lantern, Lookout }

/** 图比例点：x 占图宽，y 占图高。 */
internal class RPoint(val x: Float, val y: Float)

// ───────────────────────────── 地面线 ─────────────────────────────

/**
 * 地面线：图比例折线。脚底只许落在这条线上。
 * 内部量距一律折算成图宽单位，所以斜坡、曲线上走的「真实路程」是对的。
 */
internal class GroundLine(points: List<RPoint>, private val aspect: Float) {
    private val n = points.size
    private val xs = FloatArray(n) { points[it].x }
    private val ys = FloatArray(n) { points[it].y }
    private val cum = FloatArray(n)
    val length: Float

    init {
        require(n >= 2) { "地面线至少两个点" }
        for (i in 1 until n) cum[i] = cum[i - 1] + hypot(xs[i] - xs[i - 1], (ys[i] - ys[i - 1]) / aspect)
        length = cum[n - 1]
    }

    /** 弧长 s（图宽单位）处的点，写进 out[0]=x out[1]=y，不分配。 */
    fun sample(s: Float, out: FloatArray) {
        val c = s.coerceIn(0f, length)
        var i = 1
        while (i < n - 1 && cum[i] < c) i++
        val seg = cum[i] - cum[i - 1]
        val t = if (seg > 1e-6f) (c - cum[i - 1]) / seg else 0f
        out[0] = xs[i - 1] + (xs[i] - xs[i - 1]) * t
        out[1] = ys[i - 1] + (ys[i] - ys[i - 1]) * t
    }

    /** 把图比例点投影到地面线上，返回弧长；站点因此永远在线上。 */
    fun project(p: RPoint): Float {
        var bestS = 0f
        var bestD = Float.MAX_VALUE
        for (i in 1 until n) {
            val ax = xs[i - 1]
            val ay = ys[i - 1] / aspect
            val bx = xs[i]
            val by = ys[i] / aspect
            val px = p.x
            val py = p.y / aspect
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 > 1e-12f) (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f) else 0f
            val d = hypot(px - (ax + dx * t), py - (ay + dy * t))
            if (d < bestD) {
                bestD = d
                bestS = cum[i - 1] + sqrt(len2) * t
            }
        }
        return bestS
    }
}

// ───────────────────────────── 配置 ─────────────────────────────

/** 走路参数。长度全用「身高」(body height, bh) 当单位，所以换屏幕、换精灵大小都不用重调。 */
internal class GaitSpec(
    /** 站姿精灵高度 / 底图高度。 */
    val heightFrac: Float,
    /** 巡航速度，身高/秒。0.5 悠闲，0.9 赶路。 */
    val speedBh: Float = 0.7f,
    /** 一步（一次落脚到另一脚落脚）的长度，身高倍数。0.38~0.45 之间最自然。 */
    val strideBh: Float = 0.40f,
    /** 身体起伏幅度，身高倍数（只动精灵，不动影子）。 */
    val bobBh: Float = 0.03f,
    /** 左右重心摆幅，身高倍数。脚被挡住（甲板栏杆后）的人才用，其余 0。 */
    val swayBh: Float = 0f,
    /** 走帧数：2 = 每步一个接触姿势（A/B）；4 = 每步 接触+过渡 两帧（A接触/A过渡/B接触/B过渡）。 */
    val walkFrames: Int = 2,
    /** 起步加速的距离（步数）。停步用同一个量。 */
    val accelStrides: Float = 1.3f,
) {
    init {
        require(walkFrames == 2 || walkFrames == 4) { "walkFrames 只支持 2 或 4" }
    }
}

internal enum class Face { Keep, Left, Right }

/** 一个站点。at 会被投影到地面线上，所以只要在线附近（±0.005）就行。 */
internal class StopSpec(
    val at: RPoint,
    /** 停留秒数范围。 */
    val dwell: ClosedFloatingPointRange<Float>,
    /** 停留时做的动作（ActionSpec.key），null = 站立呼吸。 */
    val action: String? = null,
    val face: Face = Face.Keep,
)

internal class ActionFrame(
    /** 该动作在 SpriteManifest.actions[key] 里的第几张。 */
    val frame: Int,
    val sec: Float,
)

/** 站点上的循环动作，如 铁匠 举锤(0.6s)→落锤(0.16s)。 */
internal class ActionSpec(val key: String, val frames: List<ActionFrame>) {
    val total: Float = frames.fold(0f) { acc, f -> acc + f.sec }
}

internal sealed interface RouteOrder {
    /** 站点 0→1→…→n-1→…→0 来回。 */
    object PingPong : RouteOrder

    /** 每次随机去另一个站点。 */
    object Shuffle : RouteOrder

    /** 以 index 号站点为岗位，去别处看看再回来。 */
    class Home(val index: Int) : RouteOrder
}

internal class SpriteManifest(
    /** 站姿（双脚并拢、着地）。 */
    val stand: String,
    /** 走帧：2 张 [A,B] 或 4 张 [A接触,A过渡,B接触,B过渡]。所有走帧脚底 = 图片最底一行。 */
    val walk: List<String>,
    /** 动作帧：key → 若干张。 */
    val actions: Map<String, List<String>> = emptyMap(),
)

internal class ResidentSpec(
    val id: ResidentId,
    /** 读屏/调试用的名字。 */
    val label: String,
    /** 地面线（图比例折线）。 */
    val ground: List<RPoint>,
    val stops: List<StopSpec>,
    val order: RouteOrder,
    val gait: GaitSpec,
    val sprites: SpriteManifest,
    val actions: List<ActionSpec> = emptyList(),
    /** 栏杆/岩石遮挡线（图比例 y）：这条线以下的精灵和影子不画。null = 不遮。 */
    val occludeBelowY: Float? = null,
    val shadowWidthBh: Float = 0.62f,
    val shadowHeightBh: Float = 0.13f,
    val shadowAlpha: Float = 0.45f,
    /** 点击区最小边长 dp。 */
    val hitMinDp: Float = 36f,
)

// ───────────────────────────── 行走状态机 ─────────────────────────────

internal enum class Pose { Stand, Walk, Action }

/**
 * 一位居民。advance(dt) 之后，下面这些 var 就是这一帧要画的全部事实；
 * 绘制只读它们，不许再自己算位置/帧/起伏。advance 内不分配对象。
 */
internal class ResidentWalker(val spec: ResidentSpec, aspect: Float, seed: Long) {
    private enum class State { Dwell, Walk }

    private val rnd = Random(seed)
    private val line = GroundLine(spec.ground, aspect)
    private val g = spec.gait

    /** 身高（图宽单位）。 */
    private val bhW = g.heightFrac / aspect
    private val vMax = g.speedBh * bhW
    private val vMin = vMax * 0.22f
    private val stopS = FloatArray(spec.stops.size) { line.project(spec.stops[it].at) }
    private val tmp = FloatArray(2)
    private val actionByKey = HashMap<String, ActionSpec>().also { m -> spec.actions.forEach { m[it.key] = it } }

    // ── 输出 ──
    var footX = 0f; private set
    var footY = 0f; private set
    var facingLeft = false; private set
    var pose = Pose.Stand; private set

    /** Walk：走帧号（0 until walkFrames）；Action：动作帧号。 */
    var frame = 0; private set
    var actionKey: String? = null; private set

    /** 身体上抬（身高倍数 ≥ 0）。影子不吃。 */
    var bobBh = 0f; private set

    /** 左右重心摆（身高倍数）。影子不吃。 */
    var swayBh = 0f; private set

    /** 站立呼吸：以脚底为轴的纵向缩放。 */
    var breath = 1f; private set
    val isWalking: Boolean get() = state == State.Walk

    // ── 内部状态 ──
    private var state = State.Dwell
    private var sNow = 0f
    private var stopIdx = 0
    private var pingDir = 1
    private var dwellLeft = 0f
    private var actT = 0f
    private var breathT = rnd.nextFloat() * 6f
    private var curAction: ActionSpec? = null

    // 当前这段路
    private var legFrom = 0f
    private var legSign = 1f
    private var legLen = 0f
    private var legStride = 0f
    private var legSteps = 1
    private var d = 0f
    private var v = 0f
    private var targetIdx = 0
    private var interrupted = false
    private var holdAfterArrive = 0f

    init {
        stopIdx = (spec.order as? RouteOrder.Home)?.index ?: 0
        targetIdx = stopIdx
        sNow = stopS[stopIdx]
        applyFace(spec.stops[stopIdx].face)
        curAction = spec.stops[stopIdx].action?.let { actionByKey[it] }
        dwellLeft = rnd.nextFloat() * 2.5f + 0.5f // 五个人别同时起步
        actT = rnd.nextFloat() * 2f
        place()
    }

    fun advance(dt: Float) {
        when (state) {
            State.Dwell -> dwell(dt)
            State.Walk -> walk(dt)
        }
        place()
    }

    /** 静态摆位（减少动画模式）：站在岗位上，不动。 */
    fun settle() {
        state = State.Dwell
        sNow = stopS[stopIdx]
        place()
    }

    /**
     * 被点到 / 要说话：站着的就多站 holdSec 秒；走着的在「下一次脚落地」收步，站 holdSec 秒后接着走原来的目标。
     * 收步点按当前速度算好刹车距离，所以不会急停。
     */
    fun interrupt(holdSec: Float) {
        if (state == State.Dwell) {
            dwellLeft = max(dwellLeft, holdSec)
            return
        }
        val a = accel()
        val stopDist = v * v / (2f * 2f * a)
        val k = max(ceil((d + stopDist) / legStride - 1e-3f).toInt(), floor(d / legStride).toInt() + 1)
        if (k < legSteps) {
            legSteps = k
            legLen = k * legStride
            interrupted = true
        }
        holdAfterArrive = max(holdAfterArrive, holdSec)
    }

    // ── 站 ──

    private fun dwell(dt: Float) {
        dwellLeft -= dt
        breathT += dt
        actT += dt
        if (dwellLeft <= 0f) leave()
    }

    private fun leave() {
        val target =
            if (interrupted) {
                interrupted = false
                targetIdx
            } else {
                nextTarget()
            }
        startLeg(target)
    }

    private fun nextTarget(): Int {
        val n = stopS.size
        if (n == 1) return 0
        return when (val o = spec.order) {
            is RouteOrder.PingPong -> {
                if (stopIdx + pingDir !in 0 until n) pingDir = -pingDir
                stopIdx + pingDir
            }
            is RouteOrder.Home -> if (stopIdx == o.index) randomOther(stopIdx, n) else o.index
            is RouteOrder.Shuffle -> randomOther(stopIdx, n)
        }
    }

    private fun randomOther(not: Int, n: Int): Int {
        var k = rnd.nextInt(n - 1)
        if (k >= not) k++
        return k
    }

    // ── 走 ──

    private fun startLeg(target: Int) {
        val to = stopS[target]
        val len = abs(to - sNow)
        targetIdx = target
        if (len < 0.002f) {
            arrive(target, 0f, to)
            return
        }
        legFrom = sNow
        legSign = if (to >= sNow) 1f else -1f
        legLen = len
        val nominal = g.strideBh * bhW
        legSteps = max(1, (len / nominal).roundToInt())
        legStride = len / legSteps // ← 关键：整步数，保证收步时正好脚落地
        d = 0f
        v = vMin
        line.sample(to, tmp)
        val toX = tmp[0]
        line.sample(sNow, tmp)
        if (abs(toX - tmp[0]) > 1e-4f) facingLeft = toX < tmp[0]
        curAction = null
        state = State.Walk
    }

    private fun accel(): Float = vMax * vMax / (2f * g.accelStrides * legStride)

    private fun walk(dt: Float) {
        val a = accel()
        val remain = max(legLen - d, 0f)
        // 「到站」式限速：离终点越近允许的速度越低，但有 vMin 的底，保证一定走得到
        val vTarget = min(vMax, vMin + sqrt(2f * a * remain))
        v = if (vTarget > v) min(vTarget, v + a * dt) else max(vTarget, v - 2f * a * dt)
        d += v * dt
        if (d >= legLen) {
            d = legLen
            val to = legFrom + legSign * legLen
            if (interrupted) {
                // 半路收步：站 holdAfterArrive 秒后 leave() 会接着去 targetIdx
                sNow = to
                state = State.Dwell
                curAction = null
                dwellLeft = holdAfterArrive
                holdAfterArrive = 0f
            } else {
                arrive(targetIdx, holdAfterArrive, to)
                holdAfterArrive = 0f
            }
        }
    }

    private fun arrive(idx: Int, extraHold: Float, s: Float) {
        sNow = s
        stopIdx = idx
        state = State.Dwell
        v = 0f
        val st = spec.stops[idx]
        applyFace(st.face)
        curAction = st.action?.let { actionByKey[it] }
        actT = 0f
        dwellLeft = max(st.dwell.start + rnd.nextFloat() * (st.dwell.endInclusive - st.dwell.start), extraHold)
        if (spec.order is RouteOrder.PingPong) {
            if (idx == stopS.size - 1) pingDir = -1 else if (idx == 0) pingDir = 1
        }
    }

    private fun applyFace(f: Face) {
        when (f) {
            Face.Left -> facingLeft = true
            Face.Right -> facingLeft = false
            Face.Keep -> Unit
        }
    }

    // ── 输出 ──

    private fun place() {
        if (state == State.Walk) {
            line.sample(legFrom + legSign * d, tmp)
            footX = tmp[0]
            footY = tmp[1]
            val phi = d / legStride
            val stepI = floor(phi).toInt()
            val frac = phi - stepI
            val m = g.walkFrames / 2 // 每步几帧
            val sub = min((frac * m).toInt(), m - 1)
            frame = (stepI and 1) * m + sub // 整数相位处 sub=0：正好是接触帧
            pose = Pose.Walk
            actionKey = null
            val speedK = (v / vMax).coerceIn(0f, 1f)
            bobBh = g.bobBh * sin(PI * frac).toFloat() * speedK // frac=0/1 时为 0：和换帧同一时刻最低
            swayBh = g.swayBh * sin(PI * phi).toFloat() * speedK
            breath = 1f
        } else {
            line.sample(sNow, tmp)
            footX = tmp[0]
            footY = tmp[1]
            bobBh = 0f
            swayBh = 0f
            val act = curAction
            if (act != null) {
                pose = Pose.Action
                actionKey = act.key
                frame = actionFrameAt(act, actT)
                breath = 1f
            } else {
                pose = Pose.Stand
                actionKey = null
                frame = 0
                breath = 1f + 0.012f * sin(2.0 * PI * breathT / 3.4).toFloat()
            }
        }
    }

    private fun actionFrameAt(a: ActionSpec, t: Float): Int {
        var x = if (a.total > 0f) t % a.total else 0f
        for (i in a.frames.indices) {
            val f = a.frames[i]
            if (x < f.sec) return f.frame
            x -= f.sec
        }
        return a.frames.last().frame
    }

    /** 调试读数。 */
    fun debugLine(): String =
        "${spec.id} ${state.name} d=${"%.3f".format(d)}/${"%.3f".format(legLen)} v=${"%.2f".format(if (vMax > 0f) v / vMax else 0f)} " +
            "φ=${"%.2f".format(if (legStride > 0f) d / legStride else 0f)} f=$frame"
}

// ───────────────────────────── 世界 ─────────────────────────────

internal class ResidentWorld(roster: List<ResidentSpec>, aspect: Float) {
    val walkers: Array<ResidentWalker> =
        Array(roster.size) { ResidentWalker(roster[it], aspect, seed = 7919L * (it + 1)) }

    /** 从后往前（脚越靠上越先画）。 */
    val drawOrder = IntArray(walkers.size) { it }
    var time = 0f; private set

    fun advance(dt: Float) {
        time += dt
        for (i in walkers.indices) walkers[i].advance(dt)
        sortByFootY()
    }

    fun settleAll() {
        for (i in walkers.indices) walkers[i].settle()
        sortByFootY()
    }

    private fun sortByFootY() {
        for (i in 1 until drawOrder.size) {
            val k = drawOrder[i]
            val y = walkers[k].footY
            var j = i - 1
            while (j >= 0 && walkers[drawOrder[j]].footY > y) {
                drawOrder[j + 1] = drawOrder[j]
                j--
            }
            drawOrder[j + 1] = k
        }
    }
}
