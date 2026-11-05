/*
 * DreamBox home: ambient "life" layer over the sky-island painting.
 *
 * 只叠光与雾，不画形：灯火 / 炊烟 / 云雾横漂 / 瀑布微光 / 远空航灯。
 * 1.112（声音已按用户口径整体删除，画面独挑大梁）：再加星光闪烁 / 瀑布双脉流光＋瀑脚水雾 /
 * 云海五层翻涌 / 巨树树冠微光 / 城堡与市集暖晕 / 飞艇灯笼摇曳＋气囊月光 / 全场萤火光尘。
 * 全部坐标是图比例（同 SkyMap 的 Crop 映射）；一块 Canvas、约 80 次 draw、≤30fps，
 * 所有动画值只在 draw 阶段读取，零重组。时间 t 取值 0..120s 且所有周期都整除 120，
 * 所以 t 回绕处无接缝。
 *
 * NOT compiled here (no Gradle in the authoring sandbox) – expect to fix an import or two.
 */
package com.suanran.dreambox.feature.home.presentation.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private const val TAU = 6.2831855f
private val PIF = PI.toFloat()

/** 远空航灯这一趟的参数：方向 + 高度（图比例 y）。 */
@Immutable internal class FarSpec(val fromLeft: Boolean, val y: Float)

// ───────────────────────────── 光/雾「单位刷」 ─────────────────────────────
// 每种颜色只建一把半径 100 的单位径向刷，用 translate+scale 复用：每帧零分配、不重建 shader。

private fun unit(c: Color): Brush =
    Brush.radialGradient(
        0f to c,
        0.35f to c.copy(alpha = c.alpha * 0.45f),
        1f to c.copy(alpha = 0f),
        center = Offset.Zero,
        radius = 100f,
    )

private val WarmUnit = unit(Color(0xFFFFC27A)) // 灯火暖光
private val FarUnit = unit(Color(0xFFFFE9C4)) // 航灯
private val CoolUnit = unit(Color(0xFFB9C9EC)) // 云雾
private val SheenUnit = unit(Color(0xFFDFEEFF)) // 瀑布光泽
private val SmokeUnit = unit(Color(0xFFD7DEF0)) // 炊烟
private val DimUnit = unit(Color(0xFF08102A)) // 压暗（雾斑，不是物体）：把画里烘焙的灯光「调暗」
private val StarUnit = unit(Color(0xFFEAF2FF)) // 星光
private val LeafUnit = unit(Color(0xFFCFEF9F)) // 树冠微光（青黄）
private val MoteCoolUnit = unit(Color(0xFF9FE8FF)) // 青色光尘（同浮石）

/** 以 c 为中心画一团 rx×ry 的柔光；blend 默认 Plus（叠光），压暗/炊烟用 SrcOver。 */
private fun DrawScope.glow(
    b: Brush,
    c: Offset,
    rx: Float,
    a: Float,
    ry: Float = rx,
    blend: BlendMode = BlendMode.Plus,
) {
    if (a <= 0.004f) return
    withTransform({
        translate(c.x, c.y)
        scale(rx / 100f, ry / 100f, Offset.Zero)
    }) {
        drawCircle(b, 100f, Offset.Zero, a.coerceAtMost(1f), blendMode = blend)
    }
}

// ───────────────────────────── 灯火表 ─────────────────────────────
// 坐标：从 v25 预览图（840×1680，整图未裁）带网格放大后量得，误差约 ±0.003；
// 拿不准的在注释里标了「估」。用 debug 拖点校一遍即可。
// rank = 「第几个亮起」：warmth 上升时按 rank 从小到大渐次点亮；未连接 warmth=0.22，只剩 rank≲0.25 的几点。

@Immutable
private class Lamp(
    val x: Float,
    val y: Float,
    val r: Float, // 光晕半径（占图宽）
    val a: Float, // 峰值 alpha
    val rank: Float,
    val flick: Float, // 闪烁深度
    val p1: Float,
    val p2: Float,
    val ph: Float,
    val dropP: Float, // 熄灭周期（0=不熄）
    val dropSec: Float, // 每次熄灭持续秒数
    val dropPh: Float,
    val group: Int, // 0 城堡（远钟脉冲） 1 工坊灯串 2 飞艇（风铃脉冲）
)

private val SlowP1 = floatArrayOf(3f, 4f, 5f, 6f) // 都整除 120
private val FastP1 = floatArrayOf(1.5f, 2f, 2.5f, 3f)
private val P2 = floatArrayOf(8f, 10f, 12f, 15f)

private fun lamp(
    i: Int,
    group: Int,
    x: Float,
    y: Float,
    r: Float,
    rank: Float,
    a: Float = 0.26f,
    flick: Float = 0.18f,
    fast: Boolean = false,
    dropP: Float = 0f,
    dropSec: Float = 0f,
): Lamp =
    Lamp(
        x, y, r, a, rank, flick,
        p1 = (if (fast) FastP1 else SlowP1)[i % 4],
        p2 = P2[(i * 3 + 1) % 4],
        ph = (i * 0.381966f) % 1f,
        dropP = dropP,
        dropSec = dropSec,
        dropPh = (i * 0.618f) % 1f,
        group = group,
    )

private val Lamps: List<Lamp> =
    listOf(
        // ── 城堡（group 0）：窗光隔一阵换一两扇亮灭，像人在屋里走动 ──
        lamp(0, 0, 0.503f, 0.318f, 0.030f, 0.00f, a = 0.24f, flick = 0.10f), // 正门光，常亮
        lamp(1, 0, 0.504f, 0.292f, 0.016f, 0.10f, dropP = 60f, dropSec = 4f), // 山墙窗
        lamp(2, 0, 0.475f, 0.317f, 0.016f, 0.25f, dropP = 40f, dropSec = 5f), // 门左窗
        lamp(3, 0, 0.492f, 0.301f, 0.014f, 0.35f, dropP = 30f, dropSec = 4f), // 二层左窗
        lamp(4, 0, 0.437f, 0.317f, 0.016f, 0.45f, dropP = 40f, dropSec = 6f), // 左翼窗
        lamp(5, 0, 0.517f, 0.301f, 0.012f, 0.55f, dropP = 30f, dropSec = 4f), // 二层右小窗
        lamp(6, 0, 0.525f, 0.317f, 0.016f, 0.62f, dropP = 24f, dropSec = 4f), // 门右窗
        lamp(7, 0, 0.530f, 0.304f, 0.012f, 0.70f, dropP = 40f, dropSec = 5f), // 右侧窗
        lamp(8, 0, 0.563f, 0.317f, 0.014f, 0.80f, dropP = 60f, dropSec = 6f), // 右翼窗（双窗之一）
        lamp(9, 0, 0.569f, 0.317f, 0.012f, 0.90f, dropP = 30f, dropSec = 4f), // 右翼窗（双窗之二）
        // ── 工坊（group 1）：夜市灯串，快闪、错相位、不熄 ──
        lamp(10, 1, 0.499f, 0.375f, 0.022f, 0.05f, a = 0.28f, flick = 0.22f), // 齿轮轴心窗光，常亮
        lamp(11, 1, 0.350f, 0.378f, 0.014f, 0.15f, flick = 0.38f, fast = true), // 左摊（图左缘，估）
        lamp(12, 1, 0.619f, 0.375f, 0.020f, 0.20f, a = 0.28f, flick = 0.30f, fast = true), // 右棚大灯
        lamp(13, 1, 0.375f, 0.376f, 0.012f, 0.40f, flick = 0.38f, fast = true), // 左摊第二盏
        lamp(14, 1, 0.524f, 0.369f, 0.010f, 0.52f, flick = 0.35f, fast = true), // 轴心小棚顶灯
        lamp(15, 1, 0.664f, 0.382f, 0.014f, 0.58f, flick = 0.38f, fast = true), // 最右摊（带蓝光，估）
        lamp(16, 1, 0.583f, 0.382f, 0.010f, 0.68f, flick = 0.35f, fast = true), // 齿轮右下小灯
        lamp(17, 1, 0.639f, 0.382f, 0.010f, 0.76f, flick = 0.35f, fast = true), // 右棚旁小灯
        // ── 飞艇（group 2）：灯笼慢呼吸，偶尔熄一盏；吊舱窗光更小 ──
        lamp(18, 2, 0.717f, 0.229f, 0.050f, 0.12f, a = 0.16f, flick = 0.12f), // 气囊内大灯笼的透光
        lamp(19, 2, 0.667f, 0.267f, 0.022f, 0.22f, a = 0.30f, flick = 0.16f, dropP = 120f, dropSec = 6f),
        lamp(20, 2, 0.875f, 0.267f, 0.022f, 0.30f, a = 0.30f, flick = 0.16f, dropP = 120f, dropSec = 5f),
        lamp(21, 2, 0.676f, 0.203f, 0.016f, 0.42f, dropP = 60f, dropSec = 5f),
        lamp(22, 2, 0.894f, 0.232f, 0.016f, 0.50f, dropP = 60f, dropSec = 4f),
        lamp(23, 2, 0.638f, 0.239f, 0.014f, 0.60f, dropP = 120f, dropSec = 6f),
        lamp(24, 2, 0.734f, 0.312f, 0.010f, 0.66f, dropP = 40f, dropSec = 5f), // 船舱窗光
        lamp(25, 2, 0.838f, 0.251f, 0.012f, 0.72f, dropP = 120f, dropSec = 5f),
        lamp(26, 2, 0.748f, 0.311f, 0.008f, 0.78f), // 船舱窗光
        lamp(27, 2, 0.695f, 0.246f, 0.010f, 0.84f), // 气囊下小灯笼（本来就暗）
        lamp(28, 2, 0.767f, 0.306f, 0.007f, 0.92f), // 船舱窗光（估）
    )

/** 灯的亮度 0..~1.3：rank 门控（渐次亮起）× 双正弦闪烁 × 偶尔熄灭 + 远钟/风铃脉冲。 */
private fun level(l: Lamp, t: Float, warmth: Float, pulse: Float): Float {
    val gate = ((warmth - l.rank) * 8f + 0.5f).coerceIn(0f, 1f)
    if (gate <= 0f) return 0f
    val f = 0.6f * sin(TAU * (t / l.p1 + l.ph)) + 0.4f * sin(TAU * (t / l.p2 + l.ph * 2.3f))
    var v = 1f - l.flick * (0.5f - 0.5f * f)
    if (l.dropP > 0f) {
        val u = (t / l.dropP + l.dropPh) % 1f
        val d = l.dropSec / l.dropP
        if (u < d) v *= 1f - (min(u, d - u) / (0.6f / l.dropP)).coerceIn(0f, 1f) // 0.6s 渐灭渐亮
    }
    return (v + pulse * 0.35f) * gate
}

// ───────────────────────────── 炊烟 / 云雾 / 瀑布 ─────────────────────────────

private class Plume(
    val x: Float, val y: Float, // 出烟口
    val rise: Float, // 上升高度（占图高）
    val drift: Float, // 顺风右漂（占图宽），与云雾同向
    val r0: Float, // 起始半径（占图宽）
    val a: Float,
    val life: Float, // 整除 120
    val puffs: Int,
)

private val Plumes =
    listOf(
        Plume(0.503f, 0.275f, 0.060f, 0.022f, 0.0065f, 0.15f, 10f, 6), // 城堡屋脊小烟囱（估）
        Plume(0.608f, 0.358f, 0.078f, 0.026f, 0.0070f, 0.19f, 10f, 6), // 工坊右棚屋顶（估）
    )

private class Mist(
    val y: Float, val rx: Float, val ry: Float, val a: Float,
    val period: Float, val off: Float, val dir: Float,
)

private val Mists =
    listOf(
        Mist(0.640f, 0.36f, 0.040f, 0.075f, 120f, 0.00f, +1f), // 崖底雾带
        Mist(0.780f, 0.44f, 0.050f, 0.060f, 120f, 0.38f, -1f), // 云海上层（反向，视差）
        Mist(0.880f, 0.50f, 0.055f, 0.050f, 120f, 0.70f, +1f), // 云海下层
        Mist(0.845f, 0.52f, 0.052f, 0.052f, 120f, 0.52f, -1f), // 岛尖右下云层
        Mist(0.945f, 0.60f, 0.058f, 0.048f, 120f, 0.85f, +1f), // 云海底层（最宽最慢）
    )

private class Fall(
    val x: Float, val y0: Float, val y1: Float, val period: Float, val off: Float, val rx: Float,
)

private val Falls =
    listOf(
        Fall(0.116f, 0.380f, 0.712f, 10f, 0.00f, 0.011f), // 左瀑布左缕
        Fall(0.163f, 0.380f, 0.712f, 12f, 0.45f, 0.013f), // 左瀑布右缕
        Fall(0.862f, 0.380f, 0.672f, 12f, 0.20f, 0.011f), // 右瀑布左缕
        Fall(0.893f, 0.380f, 0.672f, 15f, 0.65f, 0.012f), // 右瀑布右缕
    )

// ───────────────────────────── 1.112 新元素表 ─────────────────────────────

/** 星光：位置避开岛体，只落在夜空；周期全整除 120，相位按序号错开。 */
@Immutable
private class Star(val x: Float, val y: Float, val r: Float, val a: Float, val period: Float, val ph: Float)

private val StarPeriods = floatArrayOf(6f, 8f, 10f, 12f, 15f, 20f, 24f, 30f)

private fun star(i: Int, x: Float, y: Float, r: Float, a: Float): Star =
    Star(x, y, r, a, StarPeriods[i % StarPeriods.size], (i * 0.618f) % 1f)

private val Stars: List<Star> =
    listOf(
        star(0, 0.040f, 0.050f, 0.009f, 0.26f), star(1, 0.110f, 0.100f, 0.007f, 0.20f),
        star(2, 0.170f, 0.040f, 0.010f, 0.30f), star(3, 0.240f, 0.120f, 0.006f, 0.18f),
        star(4, 0.300f, 0.060f, 0.008f, 0.24f), star(5, 0.370f, 0.100f, 0.007f, 0.20f),
        star(6, 0.430f, 0.030f, 0.009f, 0.28f), star(7, 0.500f, 0.080f, 0.006f, 0.18f),
        star(8, 0.560f, 0.130f, 0.008f, 0.22f), star(9, 0.600f, 0.050f, 0.007f, 0.20f),
        star(10, 0.680f, 0.090f, 0.010f, 0.30f), star(11, 0.740f, 0.040f, 0.007f, 0.20f),
        star(12, 0.810f, 0.110f, 0.008f, 0.24f), star(13, 0.880f, 0.060f, 0.009f, 0.26f),
        star(14, 0.950f, 0.100f, 0.007f, 0.20f), star(15, 0.030f, 0.200f, 0.008f, 0.22f),
        star(16, 0.060f, 0.270f, 0.006f, 0.18f), star(17, 0.970f, 0.220f, 0.008f, 0.22f),
        star(18, 0.990f, 0.300f, 0.006f, 0.18f), star(19, 0.960f, 0.360f, 0.007f, 0.20f),
        star(20, 0.080f, 0.160f, 0.007f, 0.20f), star(21, 0.920f, 0.170f, 0.009f, 0.26f),
        star(22, 0.360f, 0.160f, 0.006f, 0.16f), star(23, 0.640f, 0.160f, 0.006f, 0.16f),
        star(24, 0.210f, 0.155f, 0.007f, 0.18f), star(25, 0.790f, 0.155f, 0.007f, 0.18f),
    )

/** 瀑脚水雾：瀑布砸进云海处的水汽团，循环胀大变淡。 */
private class FallBase(val x: Float, val y: Float, val life: Float, val off: Float)

private val FallBases =
    listOf(
        FallBase(0.140f, 0.716f, 12f, 0.00f), // 左瀑布脚
        FallBase(0.878f, 0.676f, 15f, 0.40f), // 右瀑布脚
    )

/** 巨树树冠微光：青黄柔光在树冠里缓慢此明彼暗，像叶隙漏光又像萤火栖枝。 */
private class Canopy(val x: Float, val y: Float, val r: Float, val period: Float, val ph: Float)

private val Canopies =
    listOf(
        Canopy(0.205f, 0.172f, 0.075f, 15f, 0.00f),
        Canopy(0.295f, 0.150f, 0.060f, 20f, 0.35f),
        Canopy(0.140f, 0.215f, 0.055f, 12f, 0.62f),
        Canopy(0.255f, 0.205f, 0.065f, 24f, 0.15f),
    )

/** 全场萤火光尘：暖青两色，在岛面上空缓慢兜圈明灭；周期全整除 120。 */
@Immutable
private class Mote(val x: Float, val y: Float, val warm: Boolean, val period: Float, val ph: Float, val amp: Float)

private val MotePeriods = floatArrayOf(15f, 20f, 24f, 30f, 40f)

private fun mote(i: Int, x: Float, y: Float, warm: Boolean): Mote =
    Mote(x, y, warm, MotePeriods[i % MotePeriods.size], (i * 0.437f) % 1f, 0.008f + (i % 3) * 0.004f)

private val Motes: List<Mote> =
    listOf(
        mote(0, 0.160f, 0.300f, true), mote(1, 0.340f, 0.260f, false),
        mote(2, 0.440f, 0.330f, true), mote(3, 0.580f, 0.270f, true),
        mote(4, 0.660f, 0.340f, false), mote(5, 0.800f, 0.330f, true),
        mote(6, 0.880f, 0.420f, false), mote(7, 0.240f, 0.440f, true),
        mote(8, 0.400f, 0.470f, false), mote(9, 0.700f, 0.470f, true),
        mote(10, 0.520f, 0.420f, true), mote(11, 0.120f, 0.470f, false),
    )

// ───────────────────────────── 合成 ─────────────────────────────

/**
 * 环境层。所有入参都是 lambda，只在 draw 阶段被调用（读 State 不触发重组）。
 *  - fade：图未就绪门控（同光字的 labelsFade）
 *  - t：0..120s 的环境时钟
 *  - lively：0..1，已连接=1（灯火/炊烟/航灯的「人气」）
 *  - bell/chime：远钟 / 风铃事件的脉冲 0..1
 *  - far：远空航灯这一趟的进度 0..1（0=没有）
 */
@Composable
internal fun SkyAmbientOverlay(
    map: SkyMap,
    fade: () -> Float,
    t: () -> Float,
    lively: () -> Float,
    bell: () -> Float,
    chime: () -> Float,
    far: () -> Float,
    farSpec: () -> FarSpec,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxSize()) {
        val f = fade()
        if (f <= 0.01f) return@Canvas
        val tt = t()
        val lv = lively()
        val warmth = 0.22f + 0.83f * lv // 未连接 0.22（零星几点），连接 1.05（全亮）
        val w = map.iwS
        val h = map.ihS

        // 0) 星光闪烁：每颗星自己的周期明灭，亮时带一粒芯
        for (st in Stars) {
            val tw = 0.5f + 0.5f * sin(TAU * (tt / st.period + st.ph))
            val a = st.a * (0.22f + 0.78f * tw * tw) * f
            val c = map.at(Offset(st.x, st.y))
            glow(StarUnit, c, st.r * w, a)
            drawCircle(Color(0xFFF4F8FF).copy(alpha = (a * 1.5f).coerceAtMost(0.85f)), 0.9.dp.toPx(), c, blendMode = BlendMode.Plus)
        }

        // 1) 云雾横漂：整条漂过去要 120s；未连接时雾略重（×1.25）
        val mistK = f * (1.25f - 0.25f * lv)
        for (m in Mists) {
            val u = (tt / m.period + m.off) % 1f
            val fx = if (m.dir > 0f) -0.30f + 1.60f * u else 1.30f - 1.60f * u
            glow(CoolUnit, map.at(Offset(fx, m.y)), m.rx * w, m.a * mistK * sin(PIF * u).coerceAtLeast(0f), ry = m.ry * h)
        }

        // 2) 瀑布流光：每缕两道光泽下淌（一道慢而宽、一道快而细），水是活的
        for (fl in Falls) {
            val u = (tt / fl.period + fl.off) % 1f
            val c = map.at(Offset(fl.x, fl.y0 + (fl.y1 - fl.y0) * u))
            val a = 0.13f * f * sin(PIF * u).coerceAtLeast(0f).pow(0.8f)
            glow(SheenUnit, c, fl.rx * w, a, ry = 0.045f * h)
            val u2 = (tt / (fl.period * 0.5f) + fl.off * 1.7f) % 1f
            val c2 = map.at(Offset(fl.x, fl.y0 + (fl.y1 - fl.y0) * u2))
            val a2 = 0.10f * f * sin(PIF * u2).coerceAtLeast(0f)
            glow(SheenUnit, c2, fl.rx * 0.55f * w, a2, ry = 0.028f * h)
        }
        // 瀑脚水雾：每处 3 团水汽轮流胀大、变淡、微微上抬
        for (fb in FallBases) {
            for (k in 0 until 3) {
                val u = (tt / fb.life + k / 3f + fb.off) % 1f
                val cc = map.at(Offset(fb.x + 0.006f * sin(TAU * (u + k * 0.31f)), fb.y - 0.012f * u))
                val a = 0.11f * f * sin(PIF * u).coerceAtLeast(0f)
                glow(SheenUnit, cc, (0.018f + 0.034f * u) * w, a, ry = (0.012f + 0.020f * u) * h)
            }
        }

        // 3) 炊烟：每缕 6 团，循环上升、变大、变淡；未连接时只剩 35%（压着火）
        val smokeK = (0.35f + 0.65f * lv) * f
        for (pi in Plumes.indices) {
            val p = Plumes[pi]
            for (k in 0 until p.puffs) {
                val u = (tt / p.life + k / p.puffs.toFloat() + pi * 0.21f) % 1f
                val x = p.x + p.drift * u + 0.004f * sin(TAU * (u * 1.3f + k * 0.37f))
                val y = p.y - p.rise * u
                val a = p.a * smokeK * sin(PIF * u).coerceAtLeast(0f) * (1f - 0.5f * u)
                glow(SmokeUnit, map.at(Offset(x, y)), p.r0 * (1f + 2.4f * u) * w, a, blend = BlendMode.SrcOver)
            }
        }

        // 3.5) 巨树树冠微光：四团青黄柔光错相位呼吸
        for (cp in Canopies) {
            val a = (0.055f + 0.045f * sin(TAU * (tt / cp.period + cp.ph))) * (0.7f + 0.3f * lv) * f
            glow(LeafUnit, map.at(Offset(cp.x, cp.y)), cp.r * w, a, ry = cp.r * 0.72f * w)
        }

        // 4) 灯火：亮 = 在画里烘焙的灯上叠暖光；暗 = 在灯上叠一团暗雾把它「调暗」
        val bellV = bell()
        val chimeV = chime()
        // 暖晕：城堡整片暖光随人气呼吸；市集暖光带细碎闪烁（人声鼎沸的视觉版）；气囊一层月光
        glow(WarmUnit, map.at(Offset(0.500f, 0.306f)), 0.088f * w,
            (0.045f + 0.028f * sin(TAU * (tt / 24f))) * warmth.coerceAtMost(1f) * f, ry = 0.052f * h)
        glow(WarmUnit, map.at(Offset(0.500f, 0.380f)), 0.165f * w,
            (0.030f + 0.045f * lv) * (0.8f + 0.2f * sin(TAU * (tt / 2.5f)) * sin(TAU * (tt / 3f + 0.4f))) * f,
            ry = 0.030f * h)
        glow(SheenUnit, map.at(Offset(0.770f, 0.180f)), 0.150f * w,
            (0.035f + 0.020f * sin(TAU * (tt / 20f + 0.3f))) * f, ry = 0.070f * h)
        for (l in Lamps) {
            val pulse = when (l.group) { 0 -> bellV; 2 -> chimeV; else -> 0f }
            val a = level(l, tt, warmth, pulse)
            // 飞艇灯笼随船轻轻摇曳：光晕中心小幅横摆（8s 一荡，与画里吊索同频感）
            val sway = if (l.group == 2) 0.0035f * sin(TAU * (tt / 8f + l.ph)) else 0f
            val c = map.at(Offset(l.x + sway, l.y))
            val r = l.r * w
            val dim = 1f - a.coerceAtMost(1f)
            glow(DimUnit, c, r * 0.9f, dim * dim * 0.60f * f, blend = BlendMode.SrcOver)
            glow(WarmUnit, c, r, l.a * a * f)
        }

        // 4.5) 全场萤火光尘：十几粒光尘在岛面上空兜圈明灭，未连接时只剩一半亮度
        for (m in Motes) {
            val px = m.x + m.amp * sin(TAU * (tt / m.period + m.ph))
            val py = m.y + m.amp * 0.7f * sin(TAU * (tt / m.period + m.ph * 1.7f + 0.25f))
            val tw = 0.5f + 0.5f * sin(TAU * (tt / 6f + m.ph * 2f))
            val a = (0.10f + 0.16f * tw) * f * (0.55f + 0.45f * lv)
            val c = map.at(Offset(px, py))
            glow(if (m.warm) WarmUnit else MoteCoolUnit, c, 0.012f * w, a)
            drawCircle(Color(0xFFFFF6E0).copy(alpha = (a * 1.6f).coerceAtMost(0.9f)), 1.0.dp.toPx(), c, blendMode = BlendMode.Plus)
        }

        // 5) 远空航灯：一粒暖白光点缓慢划过夜空，1.5s 一闪
        val p = far()
        if (p > 0f) {
            val s = farSpec()
            val x = if (s.fromLeft) -0.03f + 1.06f * p else 1.03f - 1.06f * p
            val y = s.y + 0.010f * sin(TAU * p * 1.5f)
            val edge = min(1f, min(p, 1f - p) / 0.08f)
            val blink = 0.65f + 0.35f * sin(TAU * tt / 1.5f)
            val c = map.at(Offset(x, y))
            glow(FarUnit, c, 0.010f * w, 0.55f * blink * edge * f)
            drawCircle(
                Color(0xFFFFF4DC).copy(alpha = 0.9f * blink * edge * f),
                1.1.dp.toPx(),
                c,
                blendMode = BlendMode.Plus,
            )
        }
    }
}
