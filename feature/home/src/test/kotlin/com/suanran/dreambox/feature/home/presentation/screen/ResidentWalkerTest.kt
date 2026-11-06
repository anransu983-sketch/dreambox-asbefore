package com.suanran.dreambox.feature.home.presentation.screen

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertTrue

/** 放 feature/home/src/test/…，纯 JVM，不需要模拟器。把「不飘」的三条硬规矩变成会红的测试。 */
class ResidentWalkerTest {
    private val aspect = 1120f / 2240f

    /** 点到折线的最短距离（图宽单位）。 */
    private fun distToLine(x: Float, y: Float, g: List<RPoint>): Float {
        var best = Float.MAX_VALUE
        for (i in 1 until g.size) {
            val ax = g[i - 1].x
            val ay = g[i - 1].y / aspect
            val dx = g[i].x - ax
            val dy = g[i].y / aspect - ay
            val l2 = dx * dx + dy * dy
            val t = if (l2 > 0f) (((x - ax) * dx + (y / aspect - ay) * dy) / l2).coerceIn(0f, 1f) else 0f
            best = minOf(best, hypot(x - (ax + dx * t), y / aspect - (ay + dy * t)))
        }
        return best
    }

    /** 规矩 1（失败档案 4）：脚底每一帧都在地面线上，任何一位、任何时刻。 */
    @Test
    fun footAlwaysOnGroundLine() {
        for ((i, spec) in ResidentRoster.night.withIndex()) {
            val w = ResidentWalker(spec, aspect, seed = 100L + i)
            repeat(60 * 180) {
                w.advance(1f / 60f)
                assertTrue(distToLine(w.footX, w.footY, spec.ground) < 1e-4f, "${spec.id} 脚离线了")
            }
        }
    }

    /** 规矩 2（失败档案 3）：走帧只在「脚落地」时换——换帧那一帧身体起伏必须在最低点附近。 */
    @Test
    fun frameSwitchesOnlyAtFootContact() {
        for ((i, spec) in ResidentRoster.night.withIndex()) {
            val w = ResidentWalker(spec, aspect, seed = 200L + i)
            var prevFrame = -1
            var prevWalking = false
            var switches = 0
            repeat(60 * 180) {
                w.advance(1f / 60f)
                if (w.pose == Pose.Walk && prevWalking && w.frame != prevFrame) {
                    switches++
                    assertTrue(w.bobBh <= 0.2f * spec.gait.bobBh + 1e-6f, "${spec.id} 换帧时身体不在最低点：bob=${w.bobBh}")
                }
                prevWalking = w.pose == Pose.Walk
                prevFrame = w.frame
            }
            assertTrue(switches > 0 || spec.id == ResidentId.Smith, "${spec.id} 三分钟里一步都没走")
        }
    }

    /** 规矩 3：停步时一定是接触姿势（步数取整）——停下前最后一帧的相位是整数步。 */
    @Test
    fun walkEndsOnWholeStep() {
        val spec = ResidentRoster.night[1] // 背书包的：两端来回
        val w = ResidentWalker(spec, aspect, seed = 7L)
        var wasWalking = false
        var lastBob = 1f
        var stops = 0
        repeat(60 * 120) {
            w.advance(1f / 60f)
            if (wasWalking && !w.isWalking) {
                stops++
                assertTrue(lastBob <= 0.25f * spec.gait.bobBh, "收步时不在落脚点：bob=$lastBob")
            }
            wasWalking = w.isWalking
            lastBob = w.bobBh
        }
        assertTrue(stops >= 2, "两分钟里应该至少停两次")
    }
}
