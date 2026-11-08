package com.suanran.dreambox.pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.PathShape
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import java.util.Calendar
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
        const val ACTION_VIDEO_STATE = "com.suanran.dreambox.pet.VIDEO_STATE"
        private const val CHANNEL_ID = "desktop_pet"
        private const val NOTIF_ID = 4201
        private const val TICK_MS = 90L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var root: FrameLayout? = null
    private var img: ImageView? = null
    private var zzz: TextView? = null
    private var heart: TextView? = null
    // 台词气泡：独立悬浮窗，浮在仓鼠头顶上方（带小尾巴），不挤在宠物窗里遮耳朵
    private var bubbleRoot: LinearLayout? = null
    private var bubbleText: TextView? = null
    private var bubbleTail: View? = null
    private var bubbleEars: View? = null // 胖团子款的熊耳朵（云朵款不用）
    private var bubbleDots: LinearLayout? = null // 云朵款的圆点小尾巴（胖团子款用三角尾）
    private var bubbleStyleCloud = true // 本次气泡款式：云朵团 / 胖团子，两款随机轮换（她点选 A+C）
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var bubbleW = 0 // 气泡整句量好的宽高：窗口用显式尺寸，不随逐字打字塌缩
    private var bubbleH = 0
    private var bubbleFlipped = false // 头顶空间不够（仓鼠爬到屏幕顶）时翻到下方
    private var bubbleFull = ""
    private var bubbleStart = 0L
    private var bubbleUntil = 0L


    // 点按连击与长按
    private var tapStreak = 0
    private var lastTapAt = 0L
    private var longPressed = false
    private val longPressRun = Runnable {
        if (dragging && !moved && !grabbed) {
            longPressed = true
            startAct(Act.SLEEP)
        }
    }
    private lateinit var lp: WindowManager.LayoutParams
    private val frames = HashMap<String, Bitmap>()

    private var petW = 0
    private var petH = 0

    private enum class Act {
        IDLE, WALK, RUB, WASH, FRY, SEED,
        DRINK, LATIAO, LOLLIPOP, SAUSAGE, MALATANG, CRAYFISH, ICECREAM, HOTPOT,
        BASKETBALL, SWIM, RIDE, SKATE, SKI, GAME, MUSIC, SMOKE,
        SLEEP, STRETCH, ROLL, FLEE, ROAM, PEEK, HANG, SPIN, DIZZY,
    }

    private fun isFood(a: Act): Boolean = when (a) {
        Act.FRY, Act.SEED, Act.DRINK, Act.LATIAO, Act.LOLLIPOP, Act.SAUSAGE,
        Act.MALATANG, Act.CRAYFISH, Act.ICECREAM, Act.HOTPOT -> true
        else -> false
    }

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
    private var powerManager: PowerManager? = null
    private var hopStart = 0L
    private var downRawX = 0f
    private var downRawY = 0f
    private var downLpX = 0
    private var downLpY = 0
    private var downAt = 0L
    private var moved = false
    private var downTapY = 0f
    private var pokeAt = 0L

    // 活气：情绪（活力值）随互动涨、随时间落；好久没人理会自己跑出来找人
    private var mood = 60
    private var lastInteractAt = SystemClock.uptimeMillis()
    private var lastChimeHour = -1
    private val lifeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT -> say(PetTalk.Pool.UNLOCK, 55)
                Intent.ACTION_POWER_CONNECTED -> say(PetTalk.Pool.CHARGE, 80)
            }
        }
    }

    // 看视频自动藏身：琳播放器进播放页时发信号（playing=true，停留期间每 30s 心跳），退出时发 playing=false
    @Volatile
    private var videoHidden = false
    private var lastVideoSignalAt = 0L
    private val videoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_VIDEO_STATE) return
            if (intent.getBooleanExtra("playing", false)) {
                lastVideoSignalAt = SystemClock.uptimeMillis()
                setVideoHidden(true)
            } else {
                setVideoHidden(false)
            }
        }
    }

    private fun setVideoHidden(hidden: Boolean) {
        if (videoHidden == hidden) return
        videoHidden = hidden
        if (hidden) {
            bubbleRoot?.visibility = View.GONE
            root?.visibility = View.GONE
        } else {
            root?.visibility = View.VISIBLE
        }
    }

    // 摇一摇：加速度突变 → 晕乎乎
    private var sensorManager: SensorManager? = null
    private var lastAccelMag = 9.8f
    private var lastShakeAt = 0L
    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            if (powerManager?.isInteractive == false) return
            val x = e.values[0]
            val y = e.values[1]
            val z = e.values[2]
            val mag = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta = abs(mag - lastAccelMag)
            lastAccelMag = mag
            val now = SystemClock.uptimeMillis()
            if (delta > 11f && now - lastShakeAt > 1800 && !grabbed && !dragging && !falling && act != Act.ROAM) {
                lastShakeAt = now
                bumpAffinity(1)
                startAct(Act.DIZZY)
                say(PetTalk.Pool.DIZZY, 90)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

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
        powerManager = getSystemService(POWER_SERVICE) as? PowerManager
        applySize()
        startAsForeground()
        if (!buildOverlay()) {
            stopSelf()
            return
        }
        applyAppearance()
        chooseNext(initial = true)
        handler.post(tick)
        registerShake()
        // 活气：解锁/插上充电时搭句话（系统广播，免导出）
        val lifeFilter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
        }
        try {
            androidx.core.content.ContextCompat.registerReceiver(
                this, lifeReceiver, lifeFilter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } catch (_: Exception) {
        }
        // 看琳 Emby 时自动藏身：琳是另一个 App 须导出接收；信号最坏只让仓鼠藏一会儿，另有失联自恢复兜底
        try {
            androidx.core.content.ContextCompat.registerReceiver(
                this, videoReceiver, IntentFilter(ACTION_VIDEO_STATE),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
            )
        } catch (_: Exception) {
        }
        // 开场按时段打个招呼
        handler.postDelayed({ say(PetTalk.timePool()) }, 1400)
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
        try {
            bubbleRoot?.let { wm?.removeViewImmediate(it) }
        } catch (_: Exception) {
        }
        bubbleRoot = null
        root = null
        sensorManager?.unregisterListener(shakeListener)
        try {
            unregisterReceiver(lifeReceiver)
        } catch (_: Exception) {
        }
        try {
            unregisterReceiver(videoReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    private fun registerShake() {
        val sm = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sensorManager = sm
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sm.registerListener(shakeListener, acc, SensorManager.SENSOR_DELAY_GAME)
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
        val hv = TextView(this).apply {
            text = "♥"
            setTextColor(0xFFF472A0.toInt())
            textSize = 19f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START,
            )
            alpha = 0f
        }
        container.addView(hv)
        heart = hv
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
            buildBubbleWindow()
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 气泡小尾巴：三角，气泡在上方时朝下指着仓鼠，翻到下方时朝上。 */
    private fun tailDrawable(pointDown: Boolean, color: Int): ShapeDrawable {
        val p = Path()
        if (pointDown) {
            p.moveTo(0f, 0f)
            p.lineTo(100f, 0f)
            p.lineTo(50f, 100f)
        } else {
            p.moveTo(0f, 100f)
            p.lineTo(100f, 100f)
            p.lineTo(50f, 0f)
        }
        p.close()
        return ShapeDrawable(PathShape(p, 100f, 100f)).apply {
            paint.color = color
            paint.isAntiAlias = true
        }
    }

    /** 当前款式的小尾巴控件：云朵款是圆点串，胖团子款是三角。 */
    private fun currentTailView(): View? = if (bubbleStyleCloud) bubbleDots else bubbleTail

    /**
     * 按当前款式＋翻转状态重排气泡内部：
     * 云朵团＝白云朵身（蓝边波浪）＋两颗圆点滴向仓鼠；胖团子＝奶油胖方身＋熊耳朵＋三角尾。
     * 翻到下方时尾巴转到上边，耳朵收起（不然像脚）。
     */
    private fun layoutBubble() {
        val rootL = bubbleRoot ?: return
        val tv = bubbleText ?: return
        val tail = bubbleTail ?: return
        val dots = bubbleDots ?: return
        val ears = bubbleEars ?: return
        if (bubbleStyleCloud) {
            tv.background = CloudDrawable(0xFFFFFFFF.toInt(), 0xFFA8C7E8.toInt(), resources.displayMetrics.density)
            tv.setPadding(dp(17f), dp(13f), dp(17f), dp(13f))
            tail.visibility = View.GONE
            dots.visibility = View.VISIBLE
            ears.visibility = View.GONE
        } else {
            tv.background = GradientDrawable().apply {
                setColor(0xFFFFFBF0.toInt())
                cornerRadius = dp(24f).toFloat()
                setStroke(dp(1.5f), 0xFFC9955C.toInt())
            }
            tv.setPadding(dp(15f), dp(10f), dp(15f), dp(10f))
            tail.background = tailDrawable(pointDown = !bubbleFlipped, color = 0xFFFFFBF0.toInt())
            tail.visibility = View.VISIBLE
            dots.visibility = View.GONE
            ears.visibility = if (bubbleFlipped) View.GONE else View.VISIBLE
        }
        rootL.removeAllViews()
        val tailNow = currentTailView() ?: tail
        if (bubbleFlipped) {
            rootL.addView(tailNow)
            rootL.addView(tv)
        } else {
            if (!bubbleStyleCloud) rootL.addView(ears)
            rootL.addView(tv)
            rootL.addView(tailNow)
        }
    }

    private fun setBubbleFlipped(flipped: Boolean) {
        bubbleFlipped = flipped
        layoutBubble()
    }

    /** 台词气泡单独一个悬浮窗：不抢触摸（手指能穿过去点下面的 App），位置每帧跟随仓鼠。 */
    private fun buildBubbleWindow() {
        val tv = TextView(this).apply {
            setTextColor(0xFF4A362C.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            maxWidth = dp(172f)
        }
        val tail = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(14f), dp(9f))
        }
        // 胖团子款的熊耳朵：两只圆耳朵压在气泡顶边上（负边距与气泡身重叠 7dp）
        val ears = BearEarsView(this, dp(10f), dp(15f), resources.displayMetrics.density).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(14f),
            ).apply { bottomMargin = -dp(7f) }
        }
        // 云朵款的圆点小尾巴：一大一小两颗圆点从云朵滴向仓鼠
        val dots = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val d1 = View(this@DesktopPetService).apply {
                layoutParams = LinearLayout.LayoutParams(dp(9f), dp(9f)).apply { topMargin = dp(3f) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFFFFFFFF.toInt())
                    setStroke(dp(1.2f), 0xFFA8C7E8.toInt())
                }
            }
            val d2 = View(this@DesktopPetService).apply {
                layoutParams = LinearLayout.LayoutParams(dp(5.5f), dp(5.5f)).apply { topMargin = dp(2.5f) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFFFFFFFF.toInt())
                    setStroke(dp(1f), 0xFFA8C7E8.toInt())
                }
            }
            addView(d1)
            addView(d2)
        }
        val rootL = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
        }
        bubbleText = tv
        bubbleTail = tail
        bubbleEars = ears
        bubbleDots = dots
        bubbleRoot = rootL
        setBubbleFlipped(false)
        val blp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        bubbleLp = blp
        try {
            wm?.addView(rootL, blp)
        } catch (_: Exception) {
            bubbleRoot = null
            bubbleText = null
            bubbleTail = null
            bubbleEars = null
            bubbleDots = null
            bubbleLp = null
        }
    }

    /** 把气泡摆到仓鼠头顶上方：水平对着仓鼠中心、贴屏幕边自动收，尾巴始终指向仓鼠。 */
    private fun updateBubblePosition() {
        val rootL = bubbleRoot ?: return
        if (rootL.visibility != View.VISIBLE) return
        val blp = bubbleLp ?: return
        val bw = bubbleW
        val bh = bubbleH
        if (bw <= 0 || bh <= 0) return
        val flipped = lp.y - bh - dp(2f) < dp(4f)
        if (flipped != bubbleFlipped) {
            setBubbleFlipped(flipped)
        }
        val petCenterX = lp.x + petW / 2
        val margin = dp(6f)
        val targetX = (petCenterX - bw / 2).coerceIn(margin, (screenW() - bw - margin).coerceAtLeast(margin))
        val targetY = if (flipped) lp.y + petH + dp(2f) else lp.y - bh - dp(2f)
        // 气泡被屏幕边挤偏时，尾巴横移补偿，尖端仍对准仓鼠
        val tailShift = (petCenterX - (targetX + bw / 2)).toFloat()
            .coerceIn(-(bw / 2 - dp(22f)).toFloat().coerceAtLeast(0f), (bw / 2 - dp(22f)).toFloat().coerceAtLeast(0f))
        currentTailView()?.translationX = tailShift
        if (blp.x != targetX || blp.y != targetY) {
            blp.x = targetX
            blp.y = targetY
            try {
                wm?.updateViewLayout(rootL, blp)
            } catch (_: Exception) {
            }
        }
    }

    /** 多指介入时把手势按取消收尾：揪着的放下（掉落）、拖动的停住（不算点按）。 */
    private fun finishGestureAsCancel() {
        handler.removeCallbacks(longPressRun)
        if (grabbed) {
            grabbed = false
            root?.rotation = 0f
            img?.scaleY = 1f
            falling = true
            fallV = 0
            val now = SystemClock.uptimeMillis()
            grabCount = if (now - lastGrabAt < 20000) grabCount + 1 else 1
            lastGrabAt = now
            lastInteractAt = now
        }
        if (dragging) {
            dragging = false
            longPressed = false
            if (lp.y < groundY()) settling = true
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
                downTapY = ev.y
                if (!grabbed) {
                    longPressed = false
                    handler.postDelayed(longPressRun, 550)
                }
                if (grabbed) {
                    zzz?.visibility = View.GONE
                    say(PetTalk.Pool.GRAB, 50)
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // 第二根手指落下：当前手势按取消收尾。否则第一根手指先抬起后，
                // 坐标基准还停在旧手指上，MOVE 会把宠物拽跳位。
                finishGestureAsCancel()
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
                    if (abs(dx) > dp(6f) || abs(dy) > dp(6f)) {
                        if (!moved) handler.removeCallbacks(longPressRun)
                        moved = true
                    }
                    if (moved) {
                        lp.x = (downLpX + dx).coerceIn(0, maxX())
                        lp.y = (downLpY + dy).coerceIn(0, maxY())
                        applyLp()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRun)
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
            lastInteractAt = now
                } else if (dragging) {
                    dragging = false
                    if (longPressed) {
                        longPressed = false
                    } else if (!moved && SystemClock.uptimeMillis() - downAt < 350) {
                        val nowU = SystemClock.uptimeMillis()
                        tapStreak = if (nowU - lastTapAt < 400) tapStreak + 1 else 1
                        lastTapAt = nowU
                        hopStart = nowU
                        bumpAffinity(1)
                        when {
                            tapStreak >= 4 -> {
                                // 连点四下：原地转圈圈，转完发晕
                                tapStreak = 0
                                startAct(Act.SPIN)
                                handler.postDelayed({ say(PetTalk.Pool.DIZZY, 85) }, 980)
                            }
                            tapStreak == 3 -> {
                                tapStreak = 0
                                startAct(Act.ROLL)
                            }
                            tapStreak == 2 -> {
                                bumpAffinity(1)
                                startAct(
                                    listOf(
                                        Act.FRY, Act.SEED, Act.DRINK, Act.LATIAO,
                                        Act.LOLLIPOP, Act.SAUSAGE, Act.ICECREAM,
                                    ).random(),
                                )
                                showHeart()
                            }
                            downTapY > petH * 0.62f -> {
                                // 挠脚心：笑得打滚或赶紧洗脸
                                startAct(if (Random.nextBoolean()) Act.ROLL else Act.WASH)
                                say(PetTalk.Pool.TICKLE, 65)
                            }
                            else -> {
                                // 戳肚子：弹一下，偶尔换个小动作
                                pokeAt = nowU
                                if (Random.nextInt(100) < 45) {
                                    startAct(listOf(Act.RUB, Act.STRETCH, Act.WASH).random())
                                }
                                say(PetTalk.Pool.POKE, 65)
                            }
                        }
                    } else if (lp.y < groundY()) {
                        settling = true
                    }
                }
                return true
            }
        }
        return false
    }

    private fun showHeart() {
        heart?.apply {
            animate().cancel()
            alpha = 1f
            translationY = dp(8f).toFloat()
            animate().translationY(-dp(20f).toFloat()).alpha(0f).setDuration(1000).start()
        }
    }

    private fun pickBubbleLine(): String {
        val raw = getSharedPreferences("desktop_pet", MODE_PRIVATE).getString("bubble_lines", null)
        val custom = raw?.split('\n', '；', ';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (custom.isNotEmpty()) return custom.random()
        return PetTalk.line(PetTalk.Pool.FED)
    }

    private fun showBubble(text: String) {
        if (videoHidden) return // 藏身看视频时不冒泡
        bubbleFull = text
        bubbleStart = SystemClock.uptimeMillis()
        bubbleUntil = bubbleStart + 900 + text.length * 75L + 1700
        val rootL = bubbleRoot ?: return
        val bt = bubbleText ?: return
        val blp = bubbleLp ?: return
        // 款式轮换：云朵团 / 胖团子随机一款（她点选 A+C 两款都要）
        bubbleStyleCloud = Random.nextBoolean()
        layoutBubble()
        // 先按整句量好尺寸、把窗口宽高写死，再开始逐字打字：
        // 1.175 的气泡窗用 WRAP_CONTENT 随逐字文本量宽，真机上塌成一条空壳、字被挤没（她截图实证）
        // 宽度收到 172dp 让字断成两三行、气泡成团不拉长条（她明令不要长条条）
        bt.text = text
        bt.measure(
            View.MeasureSpec.makeMeasureSpec(dp(172f), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        // 熊耳朵宽度跟着气泡身走
        bubbleEars?.let { ears ->
            val lp2 = ears.layoutParams as LinearLayout.LayoutParams
            if (lp2.width != bt.measuredWidth) {
                lp2.width = bt.measuredWidth
                ears.layoutParams = lp2
            }
        }
        val wSpec = View.MeasureSpec.makeMeasureSpec(dp(190f), View.MeasureSpec.AT_MOST)
        val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        rootL.measure(wSpec, hSpec)
        bubbleW = rootL.measuredWidth
        bubbleH = rootL.measuredHeight
        bt.text = ""
        blp.width = bubbleW
        blp.height = bubbleH
        rootL.visibility = View.VISIBLE
        try {
            wm?.updateViewLayout(rootL, blp)
        } catch (_: Exception) {
        }
        updateBubblePosition()
    }

    /** 按场景池说话：气泡正显示时不抢，chance 为开口概率（百分比）。 */
    private fun say(pool: PetTalk.Pool, chance: Int = 100) {
        if (bubbleRoot?.visibility == View.VISIBLE) return
        if (chance < 100 && Random.nextInt(100) >= chance) return
        showBubble(PetTalk.line(pool))
    }

    /** 亲密度：互动攒数，攒到里程碑冒爱心庆祝。存 desktop_pet 偏好，实验室卡片可见。 */
    private fun bumpAffinity(n: Int) {
        lastInteractAt = SystemClock.uptimeMillis()
        mood = (mood + n * 3).coerceAtMost(100)
        val prefs = getSharedPreferences("desktop_pet", MODE_PRIVATE)
        val v = prefs.getInt("affinity", 0) + n
        prefs.edit().putInt("affinity", v).apply()
        val milestones = intArrayOf(50, 100, 200, 300, 500, 750, 1000, 1500, 2000, 3000, 5000)
        if (milestones.contains(v)) {
            showHeart()
            handler.postDelayed({ showBubble(PetTalk.milestone(v)) }, 350)
        }
    }

    private fun applyLp() {
        try {
            root?.let { wm?.updateViewLayout(it, lp) }
        } catch (_: Exception) {
        }
        updateBubblePosition()
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
            Act.ROAM -> Random.nextLong(28000, 36000)
            Act.DRINK -> Random.nextLong(2600, 4200)
            Act.LATIAO -> Random.nextLong(2600, 4000)
            Act.LOLLIPOP -> Random.nextLong(2600, 3800)
            Act.SAUSAGE -> Random.nextLong(2600, 4000)
            Act.MALATANG -> Random.nextLong(3400, 5000)
            Act.CRAYFISH -> Random.nextLong(2800, 4200)
            Act.ICECREAM -> Random.nextLong(2600, 3800)
            Act.HOTPOT -> Random.nextLong(3400, 5000)
            Act.BASKETBALL -> Random.nextLong(3000, 4500)
            Act.SWIM -> Random.nextLong(5000, 8000)
            Act.RIDE -> Random.nextLong(6000, 9000)
            Act.SKATE -> Random.nextLong(5000, 8000)
            Act.SKI -> Random.nextLong(5000, 8000)
            Act.GAME -> Random.nextLong(3000, 4500)
            Act.MUSIC -> Random.nextLong(3400, 5000)
            Act.SMOKE -> Random.nextLong(3800, 5200)
            Act.PEEK -> Random.nextLong(2600, 4000)
            Act.HANG -> Random.nextLong(3000, 4600)
            Act.SPIN -> 900L
            Act.DIZZY -> Random.nextLong(2400, 3200)
            Act.IDLE -> Random.nextLong(2200, 4200)
        }
        when (a) {
            Act.WALK -> {
                walkFast = Random.nextInt(100) < 12
                walkDir = pickDir()
            }
            Act.FLEE -> walkDir = pickDir()
            Act.RIDE, Act.SKATE, Act.SKI, Act.SWIM -> walkDir = pickDir()
            Act.ROLL, Act.SPIN -> rollDir = pickDir()
            Act.ROAM -> roamTarget = nearestCornerClockwise()
            Act.PEEK -> say(PetTalk.Pool.PEEK, 50)
            Act.HANG -> say(PetTalk.Pool.HANG, 50)
            Act.SLEEP -> say(PetTalk.Pool.SLEEPY, 30)
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
                roll < 14 -> Act.WALK
                roll < 20 -> Act.RUB
                roll < 25 -> Act.WASH
                roll < 31 -> Act.FRY
                roll < 36 -> Act.SEED
                roll < 40 -> Act.DRINK
                roll < 43 -> Act.LATIAO
                roll < 46 -> Act.LOLLIPOP
                roll < 49 -> Act.SAUSAGE
                roll < 51 -> Act.MALATANG
                roll < 53 -> Act.CRAYFISH
                roll < 56 -> Act.ICECREAM
                roll < 58 -> Act.HOTPOT
                roll < 61 -> Act.BASKETBALL
                roll < 64 -> Act.SWIM
                roll < 67 -> Act.RIDE
                roll < 70 -> Act.SKATE
                roll < 72 -> Act.SKI
                roll < 75 -> Act.GAME
                roll < 78 -> Act.MUSIC
                roll < 84 -> Act.SLEEP
                roll < 88 -> Act.STRETCH
                roll < 91 -> Act.ROLL
                roll < 93 -> Act.PEEK
                roll < 94 -> Act.HANG
                roll < 97 -> Act.ROAM
                roll < 98 -> Act.SMOKE
                else -> Act.IDLE
            },
        )
        // 深夜更容易犯困
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if ((hour >= 23 || hour < 7) && act != Act.SLEEP && act != Act.ROAM && Random.nextInt(100) < 20) {
            startAct(Act.SLEEP)
        }
        // 情绪偏向：活力高时把发呆/睡觉换成活动，活力低时更容易歇着
        when {
            mood >= 65 && (act == Act.IDLE || act == Act.SLEEP) && Random.nextInt(100) < 55 ->
                startAct(listOf(Act.WALK, Act.ROLL, Act.RUB, Act.BASKETBALL, Act.GAME).random())
            mood <= 30 && act != Act.SLEEP && act != Act.ROAM && Random.nextInt(100) < 35 ->
                startAct(listOf(Act.SLEEP, Act.IDLE, Act.SEED).random())
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            // 熄屏时不跑状态机、不刷窗口（此前 90ms 整夜空转耗电）；低频探活，亮屏自动恢复
            val screenOn = powerManager?.isInteractive ?: true
            if (screenOn) {
                // 藏身信号失联兜底：琳进程被杀等情况没发退出信号时，2 分钟后自己回来
                if (videoHidden && SystemClock.uptimeMillis() - lastVideoSignalAt > 120_000) {
                    setVideoHidden(false)
                }
                step()
                handler.postDelayed(this, TICK_MS)
            } else {
                handler.postDelayed(this, 1000L)
            }
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

        // —— 通用兜底：点按/长按会清掉 settling/falling，仓鼠被留在半空做地面
        // 动作（悬空睡觉/吃东西）。不在地面、又不在掉落/滑落/满屏跑/被抓拖时，
        // 恢复滑落回地面；用 settling 而非 falling，不触发落地逃跑那套、不打断当前动作。
        if (!grabbed && !dragging && !falling && !settling && act != Act.ROAM && act != Act.HANG && lp.y < groundY()) {
            settling = true
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

        // 整点报时（每小时只报一次）
        val calNow = Calendar.getInstance()
        if (calNow.get(Calendar.MINUTE) == 0 && calNow.get(Calendar.HOUR_OF_DAY) != lastChimeHour) {
            lastChimeHour = calNow.get(Calendar.HOUR_OF_DAY)
            if (Random.nextInt(100) < 75 && bubbleRoot?.visibility != View.VISIBLE) {
                val h12 = when (val h = lastChimeHour % 12) { 0 -> 12; else -> h }
                showBubble(PetTalk.chime(h12))
            }
        }

        if (!dragging && now - actStart > actDur) {
            if (act == Act.ROAM && lp.y < groundY()) {
                // 满屏跑结束时若不在地面，掉下去再继续过日子
                falling = true
                fallV = 0
                grabCount = 0
            }
            if (act == Act.PEEK) {
                // 探头结束先收回原位，别带着偏移进下一个动作
                lp.y = groundY()
                applyLp()
            }
            if (act == Act.HANG) {
                // 倒挂结束：松手掉下去（落地那套逃跑逻辑接着演）
                root?.rotation = 0f
                falling = true
                fallV = 0
                grabCount = 0
            }
            when {
                isFood(act) -> {
                    bumpAffinity(1)
                    mood = (mood + 10).coerceAtMost(100)
                    if (Random.nextInt(100) < 50) showBubble(pickBubbleLine())
                }
                act == Act.SLEEP -> {}
                else -> say(PetTalk.idlePool(), 20)
            }
            mood = (mood - 3).coerceAtLeast(0)
            // 好久没人理：自己满屏跑出来找主人
            if (now - lastInteractAt > 240_000 && Random.nextInt(100) < 40) {
                lastInteractAt = now
                startAct(Act.ROAM)
                say(PetTalk.Pool.LONELY, 85)
            } else {
                chooseNext()
            }
        }

        val elapsed = now - actStart
        var squash = 1f
        var stretchX = 1f
        iv.translationX = 0f
        iv.translationY = 0f
        iv.rotation = 0f

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
                    val fleeSp = dp(2.1f)
                    var nx = lp.x + fleeSp * walkDir
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
                val sp = dp(6.5f)
                val dx = tx - lp.x
                val dy = ty - lp.y
                if (!dragging) {
                    if (abs(dx) <= sp && abs(dy) <= sp) {
                        lp.x = tx
                        lp.y = ty
                        roamTarget = (roamTarget + 1) % 4
                    } else {
                        lp.x += dx.coerceIn(-sp, sp)
                        lp.y += dy.coerceIn(-sp, sp)
                    }
                    applyLp()
                }
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
            Act.PEEK -> {
                if (!dragging) {
                    val p = (elapsed.toFloat() / actDur).coerceIn(0f, 1f)
                    val depth = when {
                        p < 0.18f -> p / 0.18f
                        p > 0.82f -> (1f - p) / 0.18f
                        else -> 1f
                    }
                    val targetY = groundY() + (petH * 0.58f * depth).toInt()
                    if (lp.y != targetY) {
                        lp.y = targetY
                        applyLp()
                    }
                }
                setFrame(if (now % 3600 < 160) "ham-f7" else "ham-f0")
                iv.scaleX = 1f
            }
            Act.HANG -> {
                val ascend = (elapsed / 380f).coerceIn(0f, 1f)
                if (!dragging) {
                    val targetY = (groundY() * (1f - ascend)).toInt()
                    if (lp.y != targetY) {
                        lp.y = targetY
                        applyLp()
                    }
                }
                root?.rotation = 180f * ascend
                iv.rotation = (sin(elapsed / 380.0) * 5 * ascend).toFloat()
                setFrame("ham-f3")
                iv.scaleX = 1f
            }
            Act.SPIN -> {
                val p = (elapsed.toFloat() / actDur).coerceIn(0f, 1f)
                root?.rotation = 720f * p * rollDir
                setFrame("ham-f3")
                iv.scaleX = 1f
                if (p >= 1f) root?.rotation = 0f
            }
            Act.DIZZY -> {
                setFrame(if ((elapsed / 300) % 2 == 0L) "ham-f7" else "ham-f0")
                iv.rotation = (sin(elapsed / 160.0) * 10 * (1.0 - elapsed.toFloat() / actDur)).toFloat()
                iv.scaleX = 1f
            }
            Act.DRINK -> {
                setFrame("food-drink")
                iv.translationY = (sin(elapsed / 170.0) * dp(2f)).toFloat()
                iv.scaleX = 1f
            }
            Act.LATIAO, Act.SAUSAGE, Act.CRAYFISH -> {
                setFrame("food-" + act.name.lowercase())
                squash = if ((elapsed / 260) % 2 == 0L) 0.95f else 1f
                iv.scaleX = 1f
            }
            Act.MALATANG, Act.HOTPOT -> {
                setFrame("food-" + act.name.lowercase())
                squash = if ((elapsed / 300) % 2 == 0L) 0.93f else 1f
                iv.scaleX = 1f
            }
            Act.ICECREAM -> {
                setFrame("food-icecream")
                squash = if ((elapsed / 480) % 2 == 0L) 0.97f else 1f
                iv.scaleX = 1f
            }
            Act.LOLLIPOP -> {
                setFrame("food-lollipop")
                iv.rotation = (sin(elapsed / 320.0) * 3).toFloat()
                iv.scaleX = 1f
            }
            Act.BASKETBALL -> {
                setFrame("food-basketball")
                if (elapsed % 950 < 95 && hopStart == 0L) hopStart = now
                iv.scaleX = 1f
            }
            Act.SWIM -> {
                if (!dragging) {
                    var nx = lp.x + dp(0.5f) * walkDir
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
                setFrame("food-swim")
                iv.translationY = (sin(elapsed / 420.0) * dp(3f)).toFloat()
                iv.scaleX = if (walkDir < 0) -1f else 1f
            }
            Act.RIDE -> {
                if (!dragging) {
                    var nx = lp.x + dp(3.6f) * walkDir
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
                setFrame("food-ride")
                iv.translationY = if ((elapsed / 90) % 2 == 0L) -dp(1f).toFloat() else 0f
                iv.scaleX = if (walkDir < 0) -1f else 1f
            }
            Act.SKATE -> {
                if (!dragging) {
                    var nx = lp.x + dp(2.2f) * walkDir
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
                setFrame("food-skate")
                iv.rotation = (sin(elapsed / 300.0) * 2).toFloat()
                iv.scaleX = if (walkDir < 0) -1f else 1f
            }
            Act.SKI -> {
                if (!dragging) {
                    var nx = lp.x + dp(2.6f) * walkDir
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
                setFrame("food-ski")
                iv.scaleX = if (walkDir < 0) -1f else 1f
            }
            Act.GAME -> {
                setFrame("food-game")
                iv.translationX = (((elapsed / 70) % 3) - 1) * dp(1.2f).toFloat()
                iv.scaleX = 1f
            }
            Act.MUSIC -> {
                setFrame("food-music")
                squash = if ((elapsed / 450) % 2 == 0L) 0.96f else 1f
                iv.rotation = (sin(elapsed / 450.0) * 2.5).toFloat()
                iv.scaleX = 1f
            }
            Act.SMOKE -> {
                setFrame("food-smoke")
                squash = 1.02f
                iv.rotation = (sin(elapsed / 900.0) * 1.5).toFloat()
                iv.scaleX = 1f
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
                // 呼吸感：待机也微微起伏摇摆，永远不会完全静止
                iv.translationY = (sin(now / 900.0) * dp(1.2f)).toFloat()
                iv.rotation = (sin(now / 1500.0) * 0.8).toFloat()
                iv.scaleX = 1f
            }
        }
        if (act != Act.ROLL && act != Act.SPIN && act != Act.HANG) root?.rotation = 0f

        // 落地瞬间压扁（盖过睡姿压扁判定之前先算，再让 SLEEP 的值优先）
        if (landAt > 0 && now - landAt < 180 && act != Act.SLEEP) squash = 0.78f
        if (pokeAt > 0 && now - pokeAt < 160 && act != Act.SLEEP) squash = 0.82f
        if (iv.scaleY != squash) {
            iv.pivotY = iv.height.toFloat()
            iv.scaleY = squash
        }
        if (act != Act.STRETCH && iv.scaleX != 1f && act != Act.WALK && act != Act.FLEE && act != Act.ROAM && act != Act.RIDE && act != Act.SKATE && act != Act.SKI && act != Act.SWIM) {
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

        // 台词气泡：逐字打出，到时收起；显示期间位置每帧跟随仓鼠
        val br = bubbleRoot
        val bt = bubbleText
        if (br != null && bt != null && br.visibility == View.VISIBLE) {
            if (now > bubbleUntil) {
                br.visibility = View.GONE
            } else {
                val n = ((now - bubbleStart) / 70).toInt().coerceIn(0, bubbleFull.length)
                val t = bubbleFull.substring(0, n)
                if (bt.text.toString() != t) bt.text = t
                updateBubblePosition()
            }
        }
    }
}

/** 云朵团气泡身：上下缘带一排小圆波浪的云朵；蓝边靠先画一层边色云、再上移盖白色云露出。 */
private class CloudDrawable(
    fillColor: Int,
    edgeColor: Int,
    private val density: Float,
) : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
    }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = edgeColor
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        drawCloud(canvas, b, edgePaint, 1.6f * density)
        drawCloud(canvas, b, fillPaint, 0f)
    }

    private fun drawCloud(canvas: Canvas, b: Rect, p: Paint, dy: Float) {
        val r = 8.5f * density
        val left = b.left + r * 0.35f
        val right = b.right - r * 0.35f
        val top = b.top + r
        val bottom = b.bottom - r
        if (right <= left || bottom <= top) return
        val body = RectF(left, top + dy, right, bottom + dy)
        canvas.drawRoundRect(body, r * 1.3f, r * 1.3f, p)
        val spacing = r * 1.72f
        var x = left + r * 0.95f
        while (x < right - r * 0.4f) {
            canvas.drawCircle(x, top + dy, r, p)
            canvas.drawCircle(x, bottom + dy, r * 0.85f, p)
            x += spacing
        }
        canvas.drawCircle(right - r * 0.55f, top + dy, r, p)
        canvas.drawCircle(right - r * 0.55f, bottom + dy, r * 0.85f, p)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Drawable opacity")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** 胖团子款的熊耳朵：一左一右两只圆耳朵（外棕内粉），圆心压在控件底边附近，与气泡身重叠着长。 */
private class BearEarsView(
    context: Context,
    private val earR: Int,
    private val inset: Int,
    private val density: Float,
) : View(context) {
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFC9955C.toInt()
        style = Paint.Style.FILL
    }
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF8B7B2.toInt()
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cy = height - 3f * density
        val lx = (inset + earR).toFloat()
        val rx = (width - inset - earR).toFloat()
        if (rx <= lx) return
        canvas.drawCircle(lx, cy, earR.toFloat(), outerPaint)
        canvas.drawCircle(rx, cy, earR.toFloat(), outerPaint)
        canvas.drawCircle(lx, cy, earR * 0.55f, innerPaint)
        canvas.drawCircle(rx, cy, earR * 0.55f, innerPaint)
    }
}
