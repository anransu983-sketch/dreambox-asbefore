/*
 * DreamBox home: procedural ambient sound for the sky-island scene. 零素材、全程序合成。
 *
 * 层：风（常驻）· 瀑布水声（常驻）· 夜市人声底噪（随 lively）· 偶发市井清响（随 lively）
 *     · 飞艇风铃 / 城堡远钟（随 lively，事件）。风/水是岛的呼吸，人气层随灯火一起旺。
 *
 * 设计要点
 *  - 引擎线程实时合成 22.05kHz 立体声 PCM16 → AudioTrack(MODE_STREAM)。连续合成、没有循环点，所以无缝。
 *  - 不申请音频焦点（从不调用 requestAudioFocus）：别的 App 的播放不被打断、不被压低。
 *  - 门控（见 [rememberSkyAmbientSound]）：用户开关 ∧ 首页真在屏 ∧ App 前台(RESUMED) ∧ 没被拔耳机事件静音；
 *    引擎线程自己再看系统静音（铃声模式非正常 / 媒体音量 0）→ 只写静音、不渲染；
 *    启动时别的 App 正在放音乐 → 不启动。离开/切后台：80ms 线性淡出，约 0.25s 内无声，线程退出并释放 AudioTrack。
 *  - 钟/风铃的「事件」由引擎调度，并按 AudioTrack 播放头换算出「声音真正响起」的时刻，
 *    延迟回调到主线程 [Listener.onBell]/[Listener.onChime]，场景据此触发 bellPulse/chimePulse —— 声画同刻。
 *
 * 各层电平（每声道 RMS，满刻度=1）——由同公式的 C 镜像仿真校准，实测：
 *    风 −33.0 dBFS · 水 −29.9 dBFS（上一号把水声从 0.30 修正到 0.032 的量级）· 人声底噪 −35.1 dBFS(lively=1)
 *    整体 RMS ≈ −27.3 dBFS，峰值 ≈ −12 dBFS，留足余量；系统音量决定最终响度。
 *
 * NOT compiled here (no Gradle in the authoring sandbox) – expect to fix an import or two.
 */
package com.suanran.dreambox.feature.home.presentation.screen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

// ───────────────────────────── 常量与各层增益 ─────────────────────────────

private const val SR = 22050
private const val SRF = 22050f
private const val BLOCK = 512 // 每次写 512 帧 ≈ 23ms
private const val TWO_PI = 6.2831855f
private const val PI_F = 3.1415927f

/** 跟随系统静音：铃声模式为静音/振动时不出声（媒体音量为 0 时本来就无声，这里一并不渲染）。 */
private const val FOLLOW_RINGER_SILENT = true

/** 人声底噪的「有调成分」：0.6=远处人语（带音高的音节），0=纯噪声起伏（听着太假时的替代法，不改别处）。 */
private const val MURMUR_TONAL = 0.6f

// 原始增益常量：由仿真把各层标定到目标 RMS（见文件头）。改它们 = 改该层音量。
private const val WIND_LOW_G = 0.43f // 低频风体（两级 360Hz 低通噪声）
private const val WIND_WH_G = 0.172f // 风口哨（可变中心频率带通噪声，随阵风）
private const val WATER_G = 0.0865f // 瀑布水声（三段带通噪声 480/1500/3600Hz）
private const val VOICE_G = 1.0f // 人声音节（5 路/声道）
private const val BED_G = 0.5f // 人群「底床」（900Hz 低通噪声）
private const val MURMUR_G = 0.2225f // 人声层总增益（lively=1 时 RMS −35.1 dBFS）
private const val CLINK_G = 0.030f // 市井清响（碰杯/木敲）峰值
private const val CHIME_G = 0.10f // 风铃每根铜管峰值（单事件峰值 ≈ −19.7 dBFS）
private const val BELL_G = 0.075f // 远钟峰值系数（单事件峰值 ≈ −16 dBFS，经 1.5kHz 低通后）
private const val ECHO_SEND = 0.32f // 事件的「空气感」回声量
private const val MASTER = 1.0f

// ───────────────────────────── DSP 原语 ─────────────────────────────

private class Rng(seed: Int) {
    private var s = if (seed == 0) 0x1234567 else seed

    /** [-1,1) */
    fun f(): Float {
        var x = s
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        s = x
        return x * 4.656613e-10f
    }

    fun u(): Float = f() * 0.5f + 0.5f

    fun range(a: Float, b: Float): Float = a + (b - a) * u()
}

private class Lp(fc: Float) {
    private val a = 1f - exp(-TWO_PI * fc / SRF)
    private var y = 0f

    fun p(x: Float): Float {
        y += a * (x - y)
        return y
    }
}

/** TPT 状态变量滤波器的带通输出（峰值增益 1），任何频率都稳定。 */
private class Bp(fc: Float, q: Float) {
    private var g = 0f
    private var k = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var a3 = 0f
    private var s1 = 0f
    private var s2 = 0f

    init {
        set(fc, q)
    }

    fun set(fc: Float, q: Float) {
        g = tan(PI_F * fc / SRF)
        k = 1f / q
        a1 = 1f / (1f + g * (g + k))
        a2 = g * a1
        a3 = g * a2
    }

    fun p(x: Float): Float {
        val v3 = x - s2
        val v1 = a1 * s1 + a2 * v3
        val v2 = s2 + a2 * s1 + a3 * v3
        s1 = 2f * v1 - s1
        s2 = 2f * v2 - s2
        return k * v1
    }
}

private class Echo(sec: Float, private val fb: Float, lpHz: Float) {
    private val n = (sec * SRF).toInt()
    private val buf = FloatArray(n)
    private var idx = 0
    private val lp = Lp(lpHz)

    fun p(x: Float): Float {
        val d = buf[idx]
        buf[idx] = x + lp.p(d) * fb
        if (++idx >= n) idx = 0
        return d
    }
}

private fun softClip(x: Float): Float = x * (27f + x * x) / (27f + 9f * x * x)

// ───────────────────────────── 常驻层：风 / 水 ─────────────────────────────

/** 风：低频风体 + 随阵风游走的口哨。阵风 = 两条慢 LFO（9.3s / 14.1s）合成，左右相位错开。 */
private class Wind(seed: Int, private val ph: Float) {
    private val r = Rng(seed)
    private val l1 = Lp(360f)
    private val l2 = Lp(360f)
    private val bp = Bp(800f, 5f)
    private var gA = 0f
    private var gB = 0f
    private var init = false

    private fun gust(t: Float): Float {
        val a =
            0.5f +
                0.25f * sin(TWO_PI * (t / 9.3f + ph)) +
                0.25f * sin(TWO_PI * (t / 14.1f + ph * 1.7f))
        return 0.3f + 0.7f * a * a
    }

    /** 每块调一次：块首/块尾增益之间逐样本线性插值，没有拉链噪声。 */
    fun ctrl(t: Float) {
        val g = gust(t)
        if (!init) {
            gA = g
            init = true
        } else {
            gA = gB
        }
        gB = g
        bp.set(650f + 450f * g, 5f)
    }

    fun p(fi: Float): Float {
        val g = gA + (gB - gA) * fi
        val x = r.f()
        val low = l2.p(l1.p(x))
        val wh = bp.p(x)
        return (low * WIND_LOW_G + wh * WIND_WH_G * g) * g
    }
}

/** 瀑布：三段带通噪声，各自慢速幅度起伏（3.1/4.3/5.9s），避免平直的「嘶」。左右两路独立噪声。 */
private class Water(seed: Int, private val ph: Float) {
    private val r = Rng(seed)
    private val b = Array(3) { Bp(WF[it], WQ[it]) }
    private val aA = FloatArray(3)
    private val aB = FloatArray(3)
    private var init = false

    fun ctrl(t: Float) {
        for (k in 0 until 3) {
            val a = 1f + 0.18f * sin(TWO_PI * (t / WP[k] + ph * (k + 1)))
            aA[k] = if (!init) a else aB[k]
            aB[k] = a
        }
        init = true
    }

    fun p(fi: Float): Float {
        val x = r.f()
        var s = 0f
        for (k in 0 until 3) {
            val a = aA[k] + (aB[k] - aA[k]) * fi
            s += WW[k] * a * b[k].p(x)
        }
        return s * WATER_G
    }

    private companion object {
        val WP = floatArrayOf(3.1f, 4.3f, 5.9f)
        val WF = floatArrayOf(480f, 1500f, 3600f)
        val WQ = floatArrayOf(0.8f, 0.9f, 1.0f)
        val WW = floatArrayOf(1.0f, 0.8f, 0.5f)
    }
}

// ───────────────────────────── 人气层：远处夜市人声底噪 ─────────────────────────────

/**
 * 一个「说话的人」：音节（90–240ms，sin² 包络）+ 随机停顿；每个音节随机基频（105–250Hz）和两个共振峰，
 * 声源 = 锯齿(有调) + 噪声(气声)。远处听不清内容，只剩「人在说话」的质感。
 */
private class Babbler(seed: Int, private val tonal: Float) {
    private val r = Rng(seed)
    private val b1 = Bp(500f, 5f)
    private val b2 = Bp(1500f, 6f)
    private var ph = 0f
    private var f0 = 150f
    private var f0t = 150f
    private var amp = 0f
    private var left = 0
    private var total = 1
    private var gap = (r.u() * 0.6f * SRF).toInt() + 1

    fun p(density: Float): Float {
        var out = 0f
        if (left > 0) {
            val pos = (total - left).toFloat() / total.toFloat()
            val s = sin(PI_F * pos)
            val env = s * s
            f0 += (f0t - f0) * 0.002f
            ph += f0 / SRF
            if (ph >= 1f) ph -= 1f
            val saw = 2f * ph - 1f
            val src = tonal * saw + 0.5f * r.f()
            out = (b1.p(src) + 0.6f * b2.p(src)) * env * amp
            if (--left == 0) gap = (r.range(0.02f, 0.45f) / max(density, 0.2f) * SRF).toInt()
        } else if (--gap <= 0) {
            total = (r.range(0.09f, 0.24f) * SRF).toInt()
            left = total
            f0t = r.range(105f, 250f)
            b1.set(r.range(380f, 780f), 5f)
            b2.set(r.range(950f, 2200f), 6f)
            amp = r.range(0.45f, 1f)
        }
        return out
    }
}

/** 一个声道的人群：5 个说话人 + 900Hz 低通噪声底床，再两级 2.3kHz 低通压掉高频（远）。 */
private class Murmur(seed: Int, tonal: Float) {
    private val r = Rng(seed * 7919 + 13)
    private val v = Array(5) { Babbler(seed * 31 + it * 977 + 5, tonal) }
    private val bl1 = Lp(900f)
    private val bl2 = Lp(900f)
    private val d1 = Lp(2300f)
    private val d2 = Lp(2300f)

    fun p(density: Float): Float {
        var s = 0f
        for (b in v) s += b.p(density)
        val bed = bl2.p(bl1.p(r.f())) * BED_G * (0.6f + 0.4f * density)
        return d2.p(d1.p(s * VOICE_G + bed)) * MURMUR_G
    }
}

// ───────────────────────────── 事件层：共振器声部池（钟 / 风铃 / 清响） ─────────────────────────────

/** 每个部分音 = 一个衰减的二阶谐振器（脉冲激励）；64 个声部，满了偷最旧的。 */
private class Voices {
    private val n = 64
    private val on = BooleanArray(n)
    private val a1 = FloatArray(n)
    private val a2 = FloatArray(n)
    private val y1 = FloatArray(n)
    private val y2 = FloatArray(n)
    private val gl = FloatArray(n)
    private val gr = FloatArray(n)
    private val imp = FloatArray(n)
    private val delay = IntArray(n)
    private val left = IntArray(n)
    private val bright = BooleanArray(n)

    // 当前样本输出：b = 钟（需远距低通），c = 亮音（风铃/清响）
    var bL = 0f
    var bR = 0f
    var cL = 0f
    var cR = 0f

    fun clear() {
        java.util.Arrays.fill(on, false)
    }

    fun add(f: Float, t60: Float, amp: Float, pan: Float, delayF: Int, isBright: Boolean) {
        var k = -1
        for (i in 0 until n) {
            if (!on[i]) {
                k = i
                break
            }
        }
        if (k < 0) {
            var m = Int.MAX_VALUE
            for (i in 0 until n) {
                if (left[i] < m) {
                    m = left[i]
                    k = i
                }
            }
        }
        val w = TWO_PI * f / SRF
        val rr = 10f.pow(-3f / (t60 * SRF)) // T60：衰减 60dB 的时间
        a1[k] = 2f * rr * cos(w)
        a2[k] = -rr * rr
        y1[k] = 0f
        y2[k] = 0f
        imp[k] = amp * sin(w) // 脉冲激励 → y[n] = amp·r^n·sin((n+1)w)，从零相位起振，无起音咔嗒
        val th = (pan + 1f) * 0.25f * PI_F // 等功率声像
        gl[k] = cos(th)
        gr[k] = sin(th)
        delay[k] = delayF
        left[k] = (t60 * SRF).toInt()
        bright[k] = isBright
        on[k] = true
    }

    fun step() {
        bL = 0f
        bR = 0f
        cL = 0f
        cR = 0f
        for (i in 0 until n) {
            if (!on[i]) continue
            if (delay[i] > 0) {
                delay[i]--
                continue
            }
            val x = imp[i]
            imp[i] = 0f
            val y = a1[i] * y1[i] + a2[i] * y2[i] + x
            y2[i] = y1[i]
            y1[i] = y
            if (bright[i]) {
                cL += y * gl[i]
                cR += y * gr[i]
            } else {
                bL += y * gl[i]
                bR += y * gr[i]
            }
            if (--left[i] <= 0) on[i] = false
        }
    }
}

// 风铃：五声音阶 C6–C7，三个非谐部分音（管状铃比例）。远钟：F=196Hz，八个教堂钟部分音。
private val PENTA = floatArrayOf(1046.5f, 1174.7f, 1318.5f, 1568.0f, 1760.0f, 2093.0f)
private val CH_R = floatArrayOf(1f, 2.756f, 5.404f)
private val CH_T = floatArrayOf(3.2f, 1.5f, 0.6f)
private val CH_A = floatArrayOf(1f, 0.45f, 0.18f)
private val BL_R = floatArrayOf(0.5f, 1f, 1.183f, 1.506f, 2f, 2.514f, 3.011f, 4.166f)
private val BL_T = floatArrayOf(7f, 6f, 4.5f, 3.5f, 3f, 2f, 1.4f, 0.8f)
private val BL_A = floatArrayOf(0.55f, 1f, 0.7f, 0.6f, 0.5f, 0.3f, 0.2f, 0.1f)
private const val BELL_F0 = 196f
private const val TOLL_GAP_SEC = 3.2f

// ───────────────────────────── 引擎 ─────────────────────────────

internal class SkyAmbientAudio(context: Context) {
    /** 回调都在主线程。onBell/onChime 的时刻 = 声音真正响起的时刻（已按播放头补偿）。 */
    interface Listener {
        fun onBell()

        fun onChime()

        /** 引擎线程真的在出声/已退出。 */
        fun onRunning(running: Boolean)
    }

    private val ctx = context.applicationContext
    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())

    @Volatile var listener: Listener? = null

    /** 0..1：已连接=1，驱动人声层音量和事件是否发生；由场景的 lively 喂进来。 */
    @Volatile var lively = 0f

    @Volatile private var gateTarget = 0f
    private var thread: Thread? = null // 受 lock 保护

    /** 主线程调用。false = 没启动（别的 App 正在放音乐）。已在跑则只是取消淡出。 */
    fun start(): Boolean {
        synchronized(lock) {
            if (thread != null) {
                gateTarget = 1f
                return true
            }
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (am.isMusicActive) return false
            gateTarget = 1f
            val t = Synth()
            thread = t
            t.start()
            return true
        }
    }

    /** 80ms 线性淡出，线程自己退出并释放 AudioTrack。可重复调用。 */
    fun stop() {
        gateTarget = 0f
    }

    fun release() {
        gateTarget = 0f
        listener = null
    }

    private fun isSystemMuted(am: AudioManager): Boolean =
        (FOLLOW_RINGER_SILENT && am.ringerMode != AudioManager.RINGER_MODE_NORMAL) ||
            am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0

    private fun buildTrack(): AudioTrack? =
        try {
            val minBuf =
                AudioTrack.getMinBufferSize(
                    SR,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            val b =
                AudioTrack.Builder()
                    .setAudioAttributes(
                        // USAGE_GAME：走媒体音量、可与任何音乐混音；不申请焦点
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SR)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(max(minBuf, BLOCK * 4 * 5)) // ≈ 5 块 ≈ 116ms：声画对齐与停得快之间的折中
                    .setTransferMode(AudioTrack.MODE_STREAM)
            if (Build.VERSION.SDK_INT >= 26) b.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
            b.build()
        } catch (_: Throwable) {
            null
        }

    private inner class Synth : Thread("SkyAmbientAudio") {
        private val windL = Wind(0x1234567, 0f)
        private val windR = Wind(0x7654321, 0.37f)
        private val waterL = Water(0x2468ACE, 0f)
        private val waterR = Water(0x13579BD, 0.5f)
        private val murL = Murmur(11, MURMUR_TONAL)
        private val murR = Murmur(23, MURMUR_TONAL)
        private val voices = Voices()
        private val bellLpL = Lp(1500f)
        private val bellLpR = Lp(1500f)
        private val echoL = Echo(0.29f, 0.38f, 2500f)
        private val echoR = Echo(0.37f, 0.38f, 2500f)
        private val ev = Rng(System.nanoTime().toInt() or 1)

        // 事件回调队列：(帧号, 种类 0=钟 1=风铃)
        private val cbFrame = LongArray(16)
        private val cbKind = IntArray(16)
        private var cbN = 0

        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val track = buildTrack()
            if (track == null) {
                synchronized(lock) { if (thread === this) thread = null }
                postRunning()
                return
            }
            main.post { listener?.onRunning(true) }
            try {
                loop(track)
            } finally {
                try {
                    track.stop()
                } catch (_: Throwable) {}
                track.release()
                synchronized(lock) { if (thread === this) thread = null }
                postRunning()
            }
        }

        /** 只在没有更新的引擎线程时才通知「已停」，避免快速重启时顺序错乱。 */
        private fun postRunning() {
            main.post {
                val none = synchronized(lock) { thread == null }
                if (none) listener?.onRunning(false)
            }
        }

        private fun crowd(t: Float): Float =
            0.65f + 0.2f * sin(TWO_PI * t / 17f) + 0.15f * sin(TWO_PI * t / 29f + 1.3f)

        private fun queueCb(frame: Long, kind: Int) {
            if (cbN < cbFrame.size) {
                cbFrame[cbN] = frame
                cbKind[cbN] = kind
                cbN++
            }
        }

        /** 把本块内到期的事件，换算成「播放头走到该帧」的毫秒数，延迟投递到主线程。 */
        private fun flushCb(track: AudioTrack, blockStart: Long) {
            if (cbN == 0) return
            val head = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            var i = 0
            while (i < cbN) {
                if (cbFrame[i] < blockStart + BLOCK) {
                    val delayMs = ((cbFrame[i] - head) * 1000L / SR).coerceAtLeast(0L)
                    val kind = cbKind[i]
                    main.postDelayed(
                        { if (kind == 0) listener?.onBell() else listener?.onChime() },
                        delayMs,
                    )
                    cbN--
                    cbFrame[i] = cbFrame[cbN]
                    cbKind[i] = cbKind[cbN]
                } else {
                    i++
                }
            }
        }

        private fun trigChime(frame: Long) {
            val n = (3 + (ev.u() * 3f).toInt()).coerceAtMost(5)
            var last = -1
            var d = 0
            val pan = ev.range(0.45f, 0.85f) // 飞艇在画面右侧
            for (i in 0 until n) {
                var note = 0
                do {
                    note = (ev.u() * 6f).toInt().coerceAtMost(5)
                } while (note == last)
                last = note
                val vel = ev.range(0.6f, 1f)
                for (p in 0 until 3) {
                    voices.add(PENTA[note] * CH_R[p], CH_T[p], CHIME_G * vel * CH_A[p], pan, d, true)
                }
                d += (ev.range(0.07f, 0.22f) * SRF).toInt()
            }
            queueCb(frame, 1)
        }

        private fun trigBell(frame: Long) {
            val tolls = 1 + (ev.u() * 2f).toInt() // 1–2 响
            val pan = ev.range(-0.15f, 0.15f)
            for (t in 0 until tolls) {
                val d = (t * TOLL_GAP_SEC * SRF).toInt()
                for (p in 0 until 8) {
                    voices.add(BELL_F0 * BL_R[p], BL_T[p], BELL_G * BL_A[p], pan, d, false)
                }
                queueCb(frame + d, 0)
            }
        }

        private fun trigClink() {
            val pan = ev.range(-0.9f, 0.9f)
            if (ev.u() < 0.7f) { // 碰杯/金属
                val f = ev.range(2200f, 4200f)
                voices.add(f, 0.14f, CLINK_G, pan, 0, true)
                voices.add(f * 2.76f, 0.07f, CLINK_G * 0.4f, pan, 0, true)
            } else { // 木敲
                voices.add(ev.range(520f, 880f), 0.08f, CLINK_G * 1.1f, pan, 0, true)
            }
        }

        private fun loop(track: AudioTrack) {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val out = ShortArray(BLOCK * 2)
            val upStep = 1f / (1.5f * SRF) // 淡入 1.5s
            val downStep = 1f / (0.08f * SRF) // 淡出 80ms
            var frame = 0L
            var gate = 0f
            var lv = 0f
            var muted = false
            var sincePoll = 1000
            var nextBell = (ev.range(12f, 30f) * SRF).toLong()
            var nextChime = (ev.range(10f, 22f) * SRF).toLong()
            var nextClink = (2f * SRF).toLong()
            track.play()
            while (true) {
                if (sincePoll >= 43) { // 约每 1s 看一次系统静音（binder 调用，别太勤）
                    sincePoll = 0
                    muted = isSystemMuted(am)
                } else {
                    sincePoll++
                }
                val target = gateTarget
                val t = frame / SRF
                lv += (lively.coerceIn(0f, 1f) - lv) * 0.25f

                // 事件调度：只在有人气（已连接）且没在淡出时发生；节奏与场景原随机调度同分布
                if (target > 0f && lv > 0.5f) {
                    if (frame >= nextBell) {
                        if (!muted) trigBell(frame) else queueCb(frame, 0)
                        nextBell = frame + (ev.range(40f, 75f) * SRF).toLong()
                    }
                    if (frame >= nextChime) {
                        if (!muted) trigChime(frame) else queueCb(frame, 1)
                        nextChime = frame + (ev.range(25f, 50f) * SRF).toLong()
                    }
                    if (frame >= nextClink) {
                        if (!muted) trigClink()
                        nextClink = frame + (ev.range(2.5f, 7f) / (0.5f + lv) * SRF).toLong()
                    }
                }
                flushCb(track, frame)

                if (muted) {
                    // 系统静音：不渲染，写零；清掉声部，免得解除静音后冒出过期的钟声。视觉脉冲照常（上面已排队）
                    voices.clear()
                    java.util.Arrays.fill(out, 0)
                    gate = if (target < gate) max(target, gate - downStep * BLOCK) else gate
                } else {
                    windL.ctrl(t)
                    windR.ctrl(t)
                    waterL.ctrl(t)
                    waterR.ctrl(t)
                    val dens = crowd(t)
                    val lvc = lv.pow(1.2f) // 人声随人气略陡地升起
                    for (i in 0 until BLOCK) {
                        val fi = i.toFloat() / BLOCK
                        val bedL = windL.p(fi) + waterL.p(fi) + murL.p(dens) * lvc
                        val bedR = windR.p(fi) + waterR.p(fi) + murR.p(dens) * lvc
                        voices.step()
                        val evL = bellLpL.p(voices.bL) + voices.cL
                        val evR = bellLpR.p(voices.bR) + voices.cR
                        val sendL = echoL.p(evL)
                        val sendR = echoR.p(evR)
                        if (target < gate) {
                            gate -= downStep
                            if (gate < target) gate = target
                        } else if (target > gate) {
                            gate += upStep
                            if (gate > target) gate = target
                        }
                        val g = gate * gate * MASTER
                        out[2 * i] = toPcm(softClip((bedL + evL + sendL * ECHO_SEND) * g))
                        out[2 * i + 1] = toPcm(softClip((bedR + evR + sendR * ECHO_SEND) * g))
                    }
                }
                if (track.write(out, 0, out.size) < 0) return
                frame += BLOCK

                if (gate <= 0f && gateTarget <= 0f) {
                    synchronized(lock) {
                        if (gateTarget <= 0f) {
                            thread = null
                            return
                        }
                    }
                }
            }
        }

        private fun toPcm(x: Float): Short = (x * 32767f).toInt().coerceIn(-32768, 32767).toShort()
    }
}

// ───────────────────────────── 门控与接线（Compose） ─────────────────────────────

/**
 * 声音门控：用户开关 ∧ 首页真在屏(active) ∧ App 前台(RESUMED) ∧ 没发生「耳机拔出」事件。
 * 任一不满足 → 立刻淡出停声（≈0.25s 内无声）。返回的 State<Boolean> = 引擎线程是否在跑，
 * 场景据此决定 bell/chime 脉冲由音频事件驱动（true）还是回退到原随机调度（false）。
 *
 * lively 是 draw 阶段读取的 lambda（读 Compose State，不触发重组）；引擎自己做平滑。
 * 「首页真在屏」请把 pager 口径(active)与 HomePager 的 isActive 相与后传进来（见补丁）。
 */
@Composable
internal fun rememberSkyAmbientSound(
    enabled: Boolean,
    active: Boolean,
    lively: () -> Float,
    onBell: () -> Unit,
    onChime: () -> Unit,
): State<Boolean> {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val runningState = remember { mutableStateOf(false) }
    val bell by rememberUpdatedState(onBell)
    val chime by rememberUpdatedState(onChime)
    val engine = remember {
        SkyAmbientAudio(ctx).also { e ->
            e.listener =
                object : SkyAmbientAudio.Listener {
                    override fun onBell() {
                        bell()
                    }

                    override fun onChime() {
                        chime()
                    }

                    override fun onRunning(running: Boolean) {
                        runningState.value = running
                    }
                }
        }
    }

    // App 前台：生命周期 RESUMED 才响（切后台、锁屏、被别的 Activity/对话框盖住都会 ON_PAUSE）
    val resumed = remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> resumed.value = true
                Lifecycle.Event.ON_PAUSE -> resumed.value = false
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    // 耳机拔出（BECOMING_NOISY）：别让声音转到外放吓到人。离开首页再回来才重新允许。
    val noisy = remember { mutableStateOf(false) }
    DisposableEffect(ctx) {
        val rcv =
            object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    noisy.value = true
                }
            }
        ctx.registerReceiver(rcv, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        onDispose { ctx.unregisterReceiver(rcv) }
    }
    LaunchedEffect(active) { if (!active) noisy.value = false }

    val want = enabled && active && resumed.value && !noisy.value
    LaunchedEffect(want) { if (want) engine.start() else engine.stop() }
    LaunchedEffect(engine) { snapshotFlow { lively() }.collect { engine.lively = it } }
    DisposableEffect(engine) { onDispose { engine.release() } }
    return runningState
}

// ───────────────────────────── 快捷静音：一枚「♪」光字 ─────────────────────────────

private val GlyphCore = Color(0xFFEAF3FF)
private val GlyphGlow = Color(0xFF8FC8FF)

/**
 * 首页右上角的一枚「♪」：单字符，不是文字块。开=淡亮，关=更淡+划线。
 * lit 在题字亮起的 4 秒内为 true（跟着其它题字一起浮现，平时隐去只剩淡影）。
 * 48dp 触控区；无涟漪；读屏按开关控件处理（description 请传 FlyTxt 文案）。
 */
@Composable
internal fun SkySoundGlyph(
    on: Boolean,
    lit: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = "环境声",
) {
    val a by
        animateFloatAsState(
            targetValue =
                when {
                    lit && on -> 0.80f
                    lit -> 0.60f
                    on -> 0.32f
                    else -> 0.20f
                },
            animationSpec = tween(600),
            label = "soundGlyph",
        )
    Box(
        modifier
            .size(48.dp)
            .toggleable(
                value = on,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = { onToggle() },
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "♪",
            style =
                TextStyle(
                    color = GlyphCore.copy(alpha = a),
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Serif,
                    textDecoration = if (on) TextDecoration.None else TextDecoration.LineThrough,
                    shadow = Shadow(GlyphGlow.copy(alpha = 0.7f * a), Offset.Zero, 12f),
                ),
        )
    }
}
