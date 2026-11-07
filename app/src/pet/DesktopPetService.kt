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
import kotlin.math.sin
import kotlin.random.Random

/**
 * 桌面宠物（光点仓鼠）：独立悬浮窗服务，与代理/节点等本体功能完全隔离。
 * 开关在 设置 → 实验室 → 桌面宠物；状态存 SharedPreferences("desktop_pet")。
 * 行为与 sshtty 里的主角仓鼠同源：随机过日子（揉爪/洗脸/啃薯条/抱瓜子/睡觉/散步），
 * 外加桌面专属：沿屏幕底部行走、可拖动、点一下换个动作。
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

    private enum class Act { IDLE, WALK, RUB, WASH, FRY, SEED, SLEEP }

    private var act = Act.IDLE
    private var actStart = 0L
    private var actDur = 2000L
    private var walkDir = -1
    private var walkFast = false

    private var dragging = false
    private var settling = false
    private var hopStart = 0L
    private var downRawX = 0f
    private var downRawY = 0f
    private var downLpX = 0
    private var downLpY = 0
    private var downAt = 0L
    private var moved = false

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()
    private fun screenW(): Int = resources.displayMetrics.widthPixels
    private fun screenH(): Int = resources.displayMetrics.heightPixels
    private fun groundY(): Int = (screenH() - petH - dp(30f)).coerceAtLeast(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        petW = dp(96f)
        petH = dp(112f)
        startAsForeground()
        if (!buildOverlay()) {
            stopSelf()
            return
        }
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
        return try {
            assets.open("pet/$name.png").use { BitmapFactory.decodeStream(it) }?.also { frames[name] = it }
        } catch (_: Exception) {
            null
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
                dragging = true
                moved = false
                settling = false
                downRawX = ev.rawX
                downRawY = ev.rawY
                downLpX = lp.x
                downLpY = lp.y
                downAt = SystemClock.uptimeMillis()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (ev.rawX - downRawX).toInt()
                val dy = (ev.rawY - downRawY).toInt()
                if (abs(dx) > dp(6f) || abs(dy) > dp(6f)) moved = true
                if (moved) {
                    lp.x = (downLpX + dx).coerceIn(0, (screenW() - petW).coerceAtLeast(0))
                    lp.y = (downLpY + dy).coerceIn(0, (screenH() - petH).coerceAtLeast(0))
                    applyLp()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasDrag = moved
                dragging = false
                if (!wasDrag && SystemClock.uptimeMillis() - downAt < 350) {
                    // 点一下：跳一下，换个开心动作
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
            Act.IDLE -> Random.nextLong(2200, 4200)
        }
        if (a == Act.WALK) {
            walkFast = Random.nextInt(100) < 12
            if (lp.x < screenW() / 3) walkDir = 1 else if (lp.x > screenW() * 2 / 3) walkDir = -1 else walkDir = if (Random.nextBoolean()) 1 else -1
        }
    }

    private fun chooseNext(initial: Boolean = false) {
        if (initial) {
            startAct(Act.IDLE)
            return
        }
        val roll = Random.nextInt(100)
        startAct(
            when {
                roll < 30 -> Act.WALK
                roll < 42 -> Act.RUB
                roll < 52 -> Act.WASH
                roll < 66 -> Act.FRY
                roll < 78 -> Act.SEED
                roll < 88 -> Act.SLEEP
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

    private fun step() {
        val now = SystemClock.uptimeMillis()
        val iv = img ?: return

        // 落回地面（拖动松手后）
        if (settling && !dragging) {
            val g = groundY()
            if (lp.y < g) {
                lp.y = (lp.y + dp(4f)).coerceAtMost(g)
                applyLp()
            } else {
                settling = false
            }
        }

        // 点击小跳
        var hopOff = 0
        if (hopStart > 0) {
            val t = (now - hopStart).toFloat() / 450f
            if (t >= 1f) {
                hopStart = 0
            } else {
                hopOff = -(sin(t * Math.PI) * dp(16f)).toInt()
            }
        }

        if (!dragging && now - actStart > actDur) chooseNext()

        // 行走位移
        if (act == Act.WALK && !dragging && !settling) {
            val speed = if (walkFast) dp(1.5f) else dp(0.85f)
            var nx = lp.x + speed * walkDir
            val maxX = (screenW() - petW).coerceAtLeast(0)
            if (nx <= 0) {
                nx = 0
                walkDir = 1
                chooseNext()
            } else if (nx >= maxX) {
                nx = maxX
                walkDir = -1
                chooseNext()
            }
            if (nx != lp.x) {
                lp.x = nx
                applyLp()
            }
        }

        // 帧选择
        val elapsed = now - actStart
        val frameName = when (act) {
            Act.RUB -> if ((elapsed / 240) % 2 == 0L) "ham-f1" else "ham-f2"
            Act.WASH -> if ((elapsed / 220) % 2 == 0L) "ham-f5" else "ham-f6"
            Act.FRY -> when ((elapsed / 300) % 3) {
                0L -> "ham-e0"
                1L -> "ham-e1"
                else -> "ham-e2"
            }
            Act.SEED -> when ((elapsed / 320) % 3) {
                0L -> "ham-g0"
                1L -> "ham-g1"
                else -> "ham-g2"
            }
            Act.WALK -> if (walkFast) {
                if ((elapsed / 170) % 2 == 0L) "ham-r0" else "ham-r1"
            } else {
                if ((elapsed / 190) % 2 == 0L) "ham-f3" else "ham-f4"
            }
            Act.SLEEP -> "ham-f0"
            Act.IDLE -> if (now % 3400 < 150) "ham-f7" else "ham-f0"
        }
        frame(frameName)?.let { iv.setImageBitmap(it) }

        // 朝向与睡姿
        iv.scaleX = if (act == Act.WALK && walkDir < 0) -1f else 1f
        val targetSquash = if (act == Act.SLEEP) 0.84f else 1f
        if (iv.scaleY != targetSquash) {
            iv.pivotY = iv.height.toFloat()
            iv.scaleY = targetSquash
        }

        // Zzz
        val z = zzz
        if (z != null) {
            if (act == Act.SLEEP) {
                z.visibility = View.VISIBLE
                val phase = ((now - actStart) % 1800).toFloat() / 1800f
                z.alpha = 0.35f + 0.65f * sin(phase * Math.PI).toFloat()
                z.translationY = -phase * dp(10f)
            } else if (z.visibility == View.VISIBLE) {
                z.visibility = View.GONE
            }
        }

        // 小跳的视觉偏移（不改布局参数，用根视图 translation）
        root?.translationY = hopOff.toFloat()
    }
}
