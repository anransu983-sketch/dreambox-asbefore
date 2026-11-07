package com.suanran.dreambox.pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 桌面宠物（光点仓鼠）：独立悬浮窗服务，与代理/节点等本体功能完全隔离。
 * 开关在 设置 → 实验室 → 桌面宠物；状态存 SharedPreferences("desktop_pet")。
 *
 * 日常：随机过日子（揉爪/洗脸/啃薯条/抱瓜子/睡觉/伸懒腰/散步/滚一圈），偶尔满屏幕跑一圈。
 * 互动：拖动搬家（松手落回底部）、点一下小跳；
 *       揪耳朵（按住头顶区域）会被提起来挣扎，松手掉回地面，
 *       揪两次或运气不好会受惊——背包袱逃跑，跑着跑着满屏幕乱窜。
 */
class DesktopPetService : Service() {

    companion object {
        const val ACTION_STOP = "com.suanran.dreambox.pet.STOP"
        private const val CHANNEL_ID = "desktop_pet"
        private const val NOTIF_ID = 4201
        private const val TICK_MS = 90L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var root: FrameLayout? = null
    private var img: ImageView? = null
    private var zzz: TextView? = null
    private lateinit var lp: WindowManager.LayoutParams
    private val frames = HashMap<String, Bitmap>()

    private var petW = 0
    private var petH = 0

    private enum class Act { IDLE, WALK, RUB, WASH, FRY, SEED, SLEEP, STRETCH, ROLL, FLEE, ROAM }

    private var act = Act.IDLE
    private var actStart = 0L
    private var actDur = 2000L
    private var walkDir = -1
    private var walkFast = false
    private var rollDir = 1
    private var roamTarget = 0 // 0=左下 1=左上 2=右上 3=右下（沿屏幕边缘顺时针）

    // 互动状态
    private var dragging = false
    private var grabbed = false // 被揪耳朵提着
    private var grabDX = 0f
    private var grabDY = 0f
    private var grabCount = 0
    private var lastGrabAt = 0L
    private var falling = false
    private var fallV = 0
    private var landAt = 0L
    private var settling = false
    private var hopStart = 0L
    private var downRawX = 0f
    private var downRawY = 0f
    private var downLpX = 0
    private var downLpY = 0
    private var downAt = 0L
    private var moved = false

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    /** 桌宠大小（宽 dp）：实验室里调，存 SharedPreferences("desktop_pet").size_dp。 */
    private fun applySize() {
        val size = getSharedPreferences("desktop_pet", MODE_PRIVATE).getInt("size_dp", 96)
        petW = dp(size.toFloat())
        petH = dp(size * 7f / 6f)
    }

    private fun applySizeLive() {
        applySize()
        lp.width = petW
        lp.height = petH
        lp.x = lp.x.coerceIn(0, maxX())
        lp.y = lp.y.coerceAtMost(groundY()).coerceAtLeast(0)
        applyLp()
    }
    private fun screenW(): Int = resources.displayMetrics.widthPixels
    private fun screenH(): Int = resources.displayMetrics.heightPixels
    private fun maxX(): Int = (screenW() - petW).coerceAtLeast(0)
    private fun maxY(): Int = (screenH() - petH).coerceAtLeast(0)
    private fun groundY(): Int = (screenH() - petH - dp(30f)).coerceAtLeast(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        applySize()
        startAsForeground()
        if (!buildOverlay()) {
            stopSelf()
            return
        }
        applyAppearance()
        chooseNext(initial = true)
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("desktop_pet", MODE_PRIVATE)
        if (intent?.action == ACTION_STOP || !prefs.getBoolean("enabled", false)) {
            prefs.edit().putBoolean("enabled", false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        if (root != null) {
            applySizeLive()
            applyAppearance()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            root?.let { wm?.removeViewImmediate(it) }
        } catch (_: Exception) {
        }
        root = null
        super.onDestroy()
    }

    // ---------- 前台通知 ----------

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "桌面宠物", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent ?: Intent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, DesktopPetService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("仓鼠桌宠")
            .setContentText("光点仓鼠正在桌面上陪你")
            .setSmallIcon(applicationInfo.icon)
            .setContentIntent(openPi)
            .addAction(0, "抱回梦盒", stopPi)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (_: Exception) {
            try {
                startForeground(NOTIF_ID, notif)
            } catch (_: Exception) {
            }
        }
    }

    // ---------- 悬浮窗 ----------

    private fun frame(name: String): Bitmap? {
        frames[name]?.let { return it }
        val breed = getSharedPreferences("desktop_pet", MODE_PRIVATE).getString("breed", "golden") ?: "golden"
        val path = if (breed == "golden") "pet/$name.png" else "pet/$breed/$name.png"
        return try {
            assets.open(path).use { BitmapFactory.decodeStream(it) }?.also { frames[name] = it }
        } catch (_: Exception) {
            null
        }
    }

    /** 换品种/颜色时调用：清帧缓存并应用色调滤镜（色调按 CSS hue-rotate 公式）。 */
    private fun applyAppearance() {
        frames.clear()
        val tint = getSharedPreferences("desktop_pet", MODE_PRIVATE).getInt("tint", 0)
        val deg = when (tint) {
            1 -> 315f // 粉
            2 -> 195f // 蓝
            3 -> 130f // 绿
            4 -> 265f // 紫
            else -> 0f
        }
        val iv = img ?: return
        if (deg == 0f) {
            iv.clearColorFilter()
        } else {
            val rad = Math.toRadians(deg.toDouble())
            val c = cos(rad).toFloat()
            val sn = sin(rad).toFloat()
            iv.colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        0.213f + c * 0.787f - sn * 0.213f, 0.715f - c * 0.715f - sn * 0.715f, 0.072f - c * 0.072f + sn * 0.928f, 0f, 0f,
                        0.213f - c * 0.213f + sn * 0.143f, 0.715f + c * 0.285f + sn * 0.140f, 0.072f - c * 0.072f - sn * 0.283f, 0f, 0f,
                        0.213f - c * 0.213f - sn * 0.787f, 0.715f - c * 0.715f + sn * 0.715f, 0.072f + c * 0.928f + sn * 0.072f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
    }

    private fun buildOverlay(): Boolean {
        wm = getSystemService(WindowManager::class.java) ?: return false
        val container = FrameLayout(this)
        val iv = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val z = TextView(this).apply {
            text = "Z z z"
            setTextColor(Color.rgb(147, 197, 253))
            textSize = 15f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            )
            visibility = View.GONE
        }
        container.addView(iv)
        container.addView(z)
        img = iv
        zzz = z
        root = container

        lp = WindowManager.LayoutParams(
            petW,
            petH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenW() - petW - dp(18f)).coerceAtLeast(0)
            y = groundY()
        }
        container.setOnTouchListener { _, ev -> onTouch(ev) }
        return try {
            wm?.addView(container, lp)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun onTouch(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 头顶区域按下 = 揪耳朵（耳朵在画布顶部中间一带）
                val inEar = ev.y < petH * 0.30f && ev.x > petW * 0.15f && ev.x < petW * 0.85f
                grabbed = inEar
                dragging = !inEar
                moved = false
                settling = false
                falling = false
                hopStart = 0
                grabDX = ev.x
                grabDY = ev.y
                downRawX = ev.rawX
                downRawY = ev.rawY
                downLpX = lp.x
                downLpY = lp.y
                downAt = SystemClock.uptimeMillis()
                if (grabbed) zzz?.visibility = View.GONE
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (grabbed) {
                    lp.x = (ev.rawX - grabDX).toInt().coerceIn(0, maxX())
                    lp.y = (ev.rawY - grabDY).toInt().coerceIn(0, maxY())
                    applyLp()
                } else if (dragging) {
                    val dx = (ev.rawX - downRawX).toInt()
                    val dy = (ev.rawY - downRawY).toInt()
                    if (abs(dx) > dp(6f) || abs(dy) > dp(6f)) moved = true
                    if (moved) {
                        lp.x = (downLpX + dx).coerceIn(0, maxX())
                        lp.y = (downLpY + dy).coerceIn(0, maxY())
                        applyLp()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (grabbed) {
                    grabbed = false
                    root?.rotation = 0f
                    img?.scaleY = 1f
                    // 松手：掉下去
                    falling = true
                    fallV = 0
                    val now = SystemClock.uptimeMillis()
                    grabCount = if (now - lastGrabAt < 20000) grabCount + 1 else 1
                    lastGrabAt = now
                } else if (dragging) {
                    dragging = false
                    if (!moved && SystemClock.uptimeMillis() - downAt < 350) {
                        hopStart = SystemClock.uptimeMillis()
                        startAct(
                            when (Random.nextInt(3)) {
                                0 -> Act.RUB
                                1 -> Act.FRY
                                else -> Act.SEED
                            },
                        )
                    } else if (lp.y < groundY()) {
                        settling = true
                    }
                }
                return true
            }
        }
        return false
    }

    private fun applyLp() {
        try {
            root?.let { wm?.updateViewLayout(it, lp) }
        } catch (_: Exception) {
        }
    }

    // ---------- 行为引擎 ----------

    private fun startAct(a: Act) {
        act = a
        actStart = SystemClock.uptimeMillis()
        actDur = when (a) {
            Act.RUB -> Random.nextLong(1600, 2800)
            Act.WASH -> Random.nextLong(1800, 3000)
            Act.FRY -> Random.nextLong(2400, 3800)
            Act.SEED -> Random.nextLong(2400, 3800)
            Act.SLEEP -> Random.nextLong(6000, 11000)
            Act.WALK -> Random.nextLong(5000, 10000)
            Act.STRETCH -> Random.nextLong(2000, 2600)
            Act.ROLL -> 720L
            Act.FLEE -> Random.nextLong(1500, 2400)
            Act.ROAM -> Random.nextLong(9000, 15000)
            Act.IDLE -> Random.nextLong(2200, 4200)
        }
        when (a) {
            Act.WALK -> {
                walkFast = Random.nextInt(100) < 12
                walkDir = pickDir()
            }
            Act.FLEE -> walkDir = pickDir()
            Act.ROLL -> rollDir = pickDir()
            Act.ROAM -> roamTarget = nearestCornerClockwise()
            else -> {}
        }
    }

    private fun pickDir(): Int =
        if (lp.x < screenW() / 3) 1 else if (lp.x > screenW() * 2 / 3) -1 else if (Random.nextBoolean()) 1 else -1

    private fun nearestCornerClockwise(): Int {
        // 从当前位置就近选一个角作为起点，随后顺时针绕行
        val cx = if (lp.x > screenW() / 2) 1 else 0
        val cy = if (lp.y > screenH() / 2) 1 else 0
        return when {
            cx == 0 && cy == 1 -> 1 // 左下 → 左上
            cx == 0 && cy == 0 -> 2 // 左上 → 右上
            cx == 1 && cy == 0 -> 3 // 右上 → 右下
            else -> 0 // 右下 → 左下
        }
    }

    private fun cornerXY(c: Int): Pair<Int, Int> = when (c) {
        0 -> 0 to groundY()
        1 -> 0 to 0
        2 -> maxX() to 0
        else -> maxX() to groundY()
    }

    private fun chooseNext(initial: Boolean = false) {
        if (initial) {
            startAct(Act.IDLE)
            return
        }
        val roll = Random.nextInt(100)
        startAct(
            when {
                roll < 26 -> Act.WALK
                roll < 37 -> Act.RUB
                roll < 46 -> Act.WASH
                roll < 59 -> Act.FRY
                roll < 70 -> Act.SEED
                roll < 79 -> Act.SLEEP
                roll < 87 -> Act.STRETCH
                roll < 93 -> Act.ROLL
                roll < 96 -> Act.ROAM
                else -> Act.IDLE
            },
        )
    }

    private val tick = object : Runnable {
        override fun run() {
            step()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private fun setFrame(name: String) {
        frame(name)?.let { img?.setImageBitmap(it) }
    }

    private fun step() {
        val now = SystemClock.uptimeMillis()
        val iv = img ?: return

        // —— 被揪着：挣扎（蹬腿帧快切＋身体摇摆＋轻微拉长）——
        if (grabbed) {
            setFrame(if ((now / 110) % 2 == 0L) "ham-f3" else "ham-f4")
            root?.rotation = (sin(now / 90.0) * 7).toFloat()
            iv.scaleY = 1.05f
            iv.scaleX = 1f
            return
        }

        // —— 掉落（重力加速，落地压扁一下）——
        if (falling) {
            fallV += dp(1.1f)
            lp.y += fallV
            if (lp.y >= groundY()) {
                lp.y = groundY()
                falling = false
                landAt = now
                // 落地后：被揪两次必满屏跑；否则三成概率也跑，其余背包袱逃一段
                when {
                    grabCount >= 2 -> {
                        grabCount = 0
                        startAct(Act.ROAM)
                    }
                    Random.nextInt(100) < 30 -> startAct(Act.ROAM)
                    else -> startAct(Act.FLEE)
                }
            }
            applyLp()
            setFrame("ham-f3")
            return
        }

        // —— 拖动松手后落回地面 ——
        if (settling && !dragging) {
            val g = groundY()
            if (lp.y < g) {
                lp.y = (lp.y + dp(4f)).coerceAtMost(g)
                applyLp()
            } else {
                settling = false
            }
        }

        // —— 点击小跳 ——
        var hopOff = 0
        if (hopStart > 0) {
            val t = (now - hopStart).toFloat() / 450f
            if (t >= 1f) {
                hopStart = 0
            } else {
                hopOff = -(sin(t * Math.PI) * dp(16f)).toInt()
            }
        }
        root?.translationY = hopOff.toFloat()

        if (!dragging && now - actStart > actDur) {
            if (act == Act.ROAM && lp.y < groundY()) {
                // 满屏跑结束时若不在地面，掉下去再继续过日子
                falling = true
                fallV = 0
                grabCount = 0
            }
            chooseNext()
        }

        val elapsed = now - actStart
        var squash = 1f
        var stretchX = 1f

        when (act) {
            Act.WALK -> {
                if (!dragging && !settling) {
                    val speed = if (walkFast) dp(1.5f) else dp(0.85f)
                    var nx = lp.x + speed * walkDir
                    if (nx <= 0) {
                        nx = 0
                        walkDir = 1
                        chooseNext()
                    } else if (nx >= maxX()) {
                        nx = maxX()
                        walkDir = -1
                        chooseNext()
                    }
                    if (nx != lp.x) {
                        lp.x = nx
                        applyLp()
                    }
                }
                setFrame(
                    if (walkFast) {
                        if ((elapsed / 170) % 2 == 0L) "ham-r0" else "ham-r1"
                    } else {
                        if ((elapsed / 190) % 2 == 0L) "ham-f3" else "ham-f4"
                    },
                )
                iv.scaleX = if (walkDir < 0) -1f else 1f
            }
            Act.FLEE -> {
                if (!dragging) {
                    var nx = lp.x + dp(2.1f) * walkDir
                    if (nx <= 0) {
                        nx = 0
                        walkDir = 1
                    } else if (nx >= maxX()) {
                        nx = maxX()
                        walkDir = -1
                    }
                    if (nx != lp.x) {
                        lp.x = nx
                        applyLp()
                    }
                }
                setFrame(if ((elapsed / 150) % 2 == 0L) "ham-r0" else "ham-r1")
                iv.scaleX = if (walkDir < 0) -1f else 1f
            }
            Act.ROAM -> {
                val (tx, ty) = cornerXY(roamTarget)
                val sp = dp(2.6f)
                val dx = tx - lp.x
                val dy = ty - lp.y
                if (abs(dx) <= sp && abs(dy) <= sp) {
                    lp.x = tx
                    lp.y = ty
                    roamTarget = (roamTarget + 1) % 4
                } else {
                    lp.x += dx.coerceIn(-sp, sp)
                    lp.y += dy.coerceIn(-sp, sp)
                }
                applyLp()
                setFrame(if ((elapsed / 130) % 2 == 0L) "ham-f3" else "ham-f4")
                if (dx != 0) iv.scaleX = if (dx < 0) -1f else 1f
            }
            Act.ROLL -> {
                val p = (elapsed.toFloat() / actDur).coerceIn(0f, 1f)
                root?.rotation = 360f * p * rollDir
                if (!dragging) {
                    val nx = (lp.x + dp(1.15f) * rollDir).coerceIn(0, maxX())
                    if (nx != lp.x) {
                        lp.x = nx
                        applyLp()
                    }
                }
                setFrame("ham-f1")
                iv.scaleX = 1f
                if (p >= 1f) root?.rotation = 0f
            }
            Act.RUB -> {
                setFrame(if ((elapsed / 240) % 2 == 0L) "ham-f1" else "ham-f2")
                iv.scaleX = 1f
            }
            Act.WASH -> {
                setFrame(if ((elapsed / 220) % 2 == 0L) "ham-f5" else "ham-f6")
                iv.scaleX = 1f
            }
            Act.FRY -> {
                setFrame(
                    when ((elapsed / 300) % 3) {
                        0L -> "ham-e0"
                        1L -> "ham-e1"
                        else -> "ham-e2"
                    },
                )
                iv.scaleX = 1f
            }
            Act.SEED -> {
                setFrame(
                    when ((elapsed / 320) % 3) {
                        0L -> "ham-g0"
                        1L -> "ham-g1"
                        else -> "ham-g2"
                    },
                )
                iv.scaleX = 1f
            }
            Act.SLEEP -> {
                setFrame("ham-f0")
                iv.scaleX = 1f
                squash = 0.84f
            }
            Act.STRETCH -> {
                // 伸懒腰：前半闭眼拉长，后半恢复
                if (elapsed < actDur / 2) {
                    setFrame("ham-f7")
                    squash = 1.07f
                    stretchX = 0.96f
                } else {
                    setFrame("ham-f0")
                }
                iv.scaleX = stretchX
            }
            Act.IDLE -> {
                setFrame(if (now % 3400 < 150) "ham-f7" else "ham-f0")
                iv.scaleX = 1f
            }
        }
        if (act != Act.ROLL) root?.rotation = 0f

        // 落地瞬间压扁（盖过睡姿压扁判定之前先算，再让 SLEEP 的值优先）
        if (landAt > 0 && now - landAt < 180 && act != Act.SLEEP) squash = 0.78f
        if (iv.scaleY != squash) {
            iv.pivotY = iv.height.toFloat()
            iv.scaleY = squash
        }
        if (act != Act.STRETCH && iv.scaleX != 1f && act != Act.WALK && act != Act.FLEE && act != Act.ROAM) {
            iv.scaleX = 1f
        }

        // Zzz
        val z = zzz
        if (z != null) {
            if (act == Act.SLEEP && !dragging) {
                z.visibility = View.VISIBLE
                val phase = ((now - actStart) % 1800).toFloat() / 1800f
                z.alpha = 0.35f + 0.65f * sin(phase * Math.PI).toFloat()
                z.translationY = -phase * dp(10f)
            } else if (z.visibility == View.VISIBLE) {
                z.visibility = View.GONE
            }
        }
    }
}
