package com.suanran.dreambox.presentation.pack

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.util.Log
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.suanran.dreambox.data.logging.AppLogBuffer
import com.suanran.dreambox.core.pack.PackBridgeDispatcher
import com.suanran.dreambox.core.pack.PackCallback
import dalvik.system.DexClassLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.Executors

/**
 * 通用功能包宿主：本体里唯一的包容器，不含任何具体包的逻辑。
 *
 * 做四件事：
 * 1) 把包目录当作 https://pack.local/ 提供给 WebView（shouldInterceptRequest）。
 * 2) 在网页脚本之前注入 Capacitor 官方 native-bridge.js 与 PluginHeaders，
 *    使包内网页按 Capacitor 约定调原生（window.androidBridge.postMessage）。
 * 3) 用 DexClassLoader 加载包内 plugins.dex 的桥入口，把调用以 JSON 转发给它。
 * 4) 包的事件经 Capacitor.fromNative(save=true) 推回网页的 addListener 回调。
 */
class PackHostActivity : Activity() {

    private lateinit var webView: WebView
    private var dispatcher: PackBridgeDispatcher? = null
    private val io = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** addListener 注册表：pluginId|eventName -> callbackId 集合。 */
    private val listeners = mutableMapOf<String, MutableSet<String>>()

    private lateinit var packDir: File
    private var pluginHeadersJson: String = "[]"
    private var hostBackupAllowed: Boolean = false
    private var hostKernelAllowed: Boolean = false
    private var hostSectAllowed: Boolean = false
    private var hostRebirthAllowed: Boolean = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 加固：加载功能包 dex 前再验一次宿主签名（DEBUG 包不查）
        if (!com.suanran.dreambox.BuildConfig.DEBUG &&
            !com.suanran.dreambox.security.IntegrityGuard.verify(this)
        ) {
            finish(); return
        }
        // 全面屏手势：内容延伸到系统栏之下，手势返回区域不被遮挡
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val packId = intent.getStringExtra("pack_id") ?: run { finish(); return }
        val packName = intent.getStringExtra("pack_name") ?: packId
        val dirPath = intent.getStringExtra("pack_dir") ?: run { finish(); return }
        val entryClass = intent.getStringExtra("pack_entry_class") ?: ""
        packDir = File(dirPath)
        title = packName

        pluginHeadersJson = readPackPluginHeaders()
        hostBackupAllowed = runCatching {
            val marker = File(packDir, "pack.json")
            marker.isFile && JSONObject(marker.readText()).optBoolean("hostBackup", false)
        }.getOrDefault(false)
        hostKernelAllowed = runCatching {
            val marker = File(packDir, "pack.json")
            marker.isFile && JSONObject(marker.readText()).optBoolean("hostKernel", false)
        }.getOrDefault(false)
        hostSectAllowed = runCatching {
            val marker = File(packDir, "pack.json")
            marker.isFile && JSONObject(marker.readText()).optBoolean("hostSect", false)
        }.getOrDefault(false)
        hostRebirthAllowed = runCatching {
            val marker = File(packDir, "pack.json")
            marker.isFile && JSONObject(marker.readText()).optBoolean("hostRebirth", false)
        }.getOrDefault(false)
        requestPackPermissions()
        dispatcher = loadDispatcher(entryClass)

        webView = WebView(this)
        setContentView(webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            builtInZoomControls = false
        }
        webView.addJavascriptInterface(AndroidBridge(), "androidBridge")
        webView.webViewClient = PackWebViewClient()
        // 没有 WebChromeClient 时 window.confirm() 恒返回 false、alert() 无声：
        // 包内带确认弹窗的按钮会表现为「点了没反应」（sshtty v4 完整版调优实测）。
        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@PackHostActivity)
                    .setMessage(message ?: "")
                    .setPositiveButton("确定") { _, _ -> result?.confirm() }
                    .setNegativeButton("取消") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@PackHostActivity)
                    .setMessage(message ?: "")
                    .setPositiveButton("好") { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.confirm() }
                    .show()
                return true
            }
        }
        webView.loadUrl("https://pack.local/index.html")
        // 全面屏手势返回（预测性返回）走 OnBackInvoked 体系、不会调用旧 onBackPressed 覆写，
        // 必须显式注册回调走同一套逻辑，否则手势一划就直接退出功能包（真机实测）。
        if (Build.VERSION.SDK_INT >= 33) {
            val cb = OnBackInvokedCallback { handleBack() }
            backInvokedCallback = cb
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb
            )
        }
    }

    private var backInvokedCallback: OnBackInvokedCallback? = null

    private fun handleBack() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else finish()
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) {
            backInvokedCallback?.let { runCatching { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) } }
            backInvokedCallback = null
        }
        runCatching { dispatcher?.release() }
        io.shutdownNow()
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("androidBridge")
            webView.destroy()
        }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBack()
    }

    // ── 包元数据 ──────────────────────────────────────────────

    /** pack.json 里的 pluginHeaders 原样给网页；缺省给芷湘包的已知插件面。 */
    private fun readPackPluginHeaders(): String {
        val marker = File(packDir, "pack.json")
        if (marker.isFile) {
            runCatching {
                val json = JSONObject(marker.readText())
                val headers = json.optJSONArray("pluginHeaders")
                if (headers != null) return headers.toString()
            }
        }
        return DEFAULT_PLUGIN_HEADERS
    }

    /** 包在 pack.json 的 permissions 里声明需要的运行时权限，宿主在打开时统一申请。 */
    private fun requestPackPermissions() {
        val marker = File(packDir, "pack.json")
        if (!marker.isFile) return
        val wanted = runCatching {
            val arr = JSONObject(marker.readText()).optJSONArray("permissions") ?: return
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
        val missing = wanted.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            runCatching { requestPermissions(missing.toTypedArray(), 4210) }
        }
    }

    private fun loadDispatcher(entryClass: String): PackBridgeDispatcher? {
        if (entryClass.isBlank()) return null
        val dex = File(packDir, "plugins.dex")
        if (!dex.isFile) return null
        return runCatching {
            // Android 14+：动态加载的 dex 必须只读。
            dex.setReadOnly()
            val optDir = File(codeCacheDir, "packdex").apply { mkdirs() }
            val loader = DexClassLoader(dex.absolutePath, optDir.absolutePath, null, javaClass.classLoader)
            val clazz = loader.loadClass(entryClass)
            val instance = clazz.getDeclaredConstructor().newInstance()
            (instance as? PackBridgeDispatcher)?.also {
                it.init(applicationContext) { pluginId, eventName, dataJson -> emitEvent(pluginId, eventName, dataJson) }
                AppLogBuffer.forceAdd(Log.INFO, "Pack", "原生代码加载成功 entry=$entryClass dir=${packDir.name}")
            }
        }.getOrElse {
            AppLogBuffer.forceAdd(
                Log.ERROR, "Pack",
                "原生代码加载失败 entry=$entryClass dir=${packDir.name}\n" + Log.getStackTraceString(it),
            )
            Toast.makeText(this, "插件原生代码加载失败：${it.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    // ── Capacitor 桥 ─────────────────────────────────────────

    inner class AndroidBridge {
        @JavascriptInterface
        fun postMessage(message: String) {
            val data = runCatching { JSONObject(message) }.getOrNull() ?: return
            val callbackId = data.optString("callbackId")
            val pluginId = data.optString("pluginId")
            val method = data.optString("methodName")
            val options = data.optJSONObject("options") ?: JSONObject()
            if (method == "addListener") {
                val eventName = options.optString("eventName")
                if (eventName.isNotBlank()) {
                    val key = "$pluginId|$eventName"
                    synchronized(listeners) { listeners.getOrPut(key) { mutableSetOf() }.add(callbackId) }
                }
                sendResult(callbackId, success = true, dataJson = "{}", save = true)
                return
            }
            if (method == "removeListener") {
                val target = options.optString("callbackId")
                synchronized(listeners) { listeners.values.forEach { it.remove(target) } }
                sendResult(callbackId, success = true, dataJson = "{}", save = false)
                return
            }
            if (pluginId == "HostBackup") {
                handleHostBackup(callbackId, method, options)
                return
            }
            if (pluginId == "HostKernel") {
                handleHostKernel(callbackId, method, options)
                return
            }
            if (pluginId == "HostSect") {
                handleHostSect(callbackId, method, options)
                return
            }
            if (pluginId == "HostRebirth") {
                handleHostRebirth(callbackId, method, options)
                return
            }
            val current = dispatcher
            if (current == null) {
                sendResult(callbackId, success = false, dataJson = null, error = "插件原生代码不可用（$pluginId 未实现）")
                return
            }
            io.execute {
                val handled = runCatching {
                    current.dispatch(pluginId, method, options.toString(), PackCallback { success, dataJson, error ->
                        sendResult(callbackId, success, dataJson, error)
                    })
                }.onFailure {
                    AppLogBuffer.forceAdd(
                        Log.ERROR, "Pack",
                        "dispatch 异常 $pluginId.$method dir=${packDir.name}\n" + Log.getStackTraceString(it),
                    )
                }.getOrDefault(false)
                if (!handled) {
                    sendResult(callbackId, success = false, dataJson = null, error = "插件未实现 $pluginId.$method")
                }
            }
        }
    }

    // ── 高武门派桥（HostSect）─────────────────────────────────

    /** 七修流派的宿主侧定版：种子色与明暗必须与插件页 SECTS 一致，改一处须同步另一处。 */
    private data class SectDef(val seedArgb: Long, val dark: Boolean)

    private val sectDefs: Map<String, SectDef> = mapOf(
        "jian" to SectDef(0xFF0284C7L, true),
        "fu" to SectDef(0xFFF59E0BL, false), // 符修默认浅色（黄纸）；1.69 起山门页可明暗翻转，白卡坏页已在 BocchiCard 中央修复
        "zhen" to SectDef(0xFF6366F1L, true),
        "ti" to SectDef(0xFFEA580CL, true),
        "kuilei" to SectDef(0xFF0F766EL, true),
        "gui" to SectDef(0xFF16A34AL, true),
        "mo" to SectDef(0xFFDC2626L, true),
    )

    private val sectRankThresholds = intArrayOf(0, 60, 180, 400, 800, 1400, 2200)

    // 宗门品级：不入流(0)→九品(1)→…→一品(9)→圣地(10)，按总修为晋升；品级定名号（插件页名字表）、
    // 山门布局与资源：俸禄 = 品级×4/日，修炼加成 = 品级（单次修炼 = 20 + 品级）。
    private val sectGradeThresholds = intArrayOf(0, 100, 250, 450, 700, 1000, 1400, 1900, 2500, 3200, 4500)
    private val sectGradeNames = listOf("不入流", "九品", "八品", "七品", "六品", "五品", "四品", "三品", "二品", "一品", "圣地")

    private fun sectGradeOf(exp: Int): Int {
        var g = 0
        for (i in sectGradeThresholds.indices) if (exp >= sectGradeThresholds[i]) g = i
        return g
    }
    private val sectRankNames = listOf("炼气", "筑基", "金丹", "元婴", "化神", "炼虚", "大乘")
    // 身份与职位是两轨（1.74 用户定版）：身份＝弟子辈辈分，随修为自动晋升；
    // 职位＝任职（执事/长老/峰主都是差事、不是身份），修为解锁后自选，可任低不任高，亦可不就任。
    // 身份六档：杂役→外门→内门→核心→亲传（《弑天刃》弟子顶档是亲传）→道子（弟子辈顶点、宗门继承人）
    private val sectIdentityThresholds = intArrayOf(0, 60, 180, 400, 800, 1400)
    private val sectIdentityNames = listOf("杂役弟子", "外门弟子", "内门弟子", "核心弟子", "亲传弟子", "道子")

    private fun sectIdentityOf(exp: Int): Int {
        var g = 0
        for (i in sectIdentityThresholds.indices) if (exp >= sectIdentityThresholds[i]) g = i
        return g
    }

    // 职位六档：执事（管外门/内门）→长老（管杂役/外门/内门）→峰主（执掌一峰）→太上长老→掌门→老祖（4500，与圣地同期）
    private val sectPostThresholds = intArrayOf(1100, 1400, 1900, 2500, 3200, 4500)
    private val sectPostNames = listOf("执事", "长老", "峰主", "太上长老", "掌门", "老祖")

    /** 已解锁的最高职位下标，未解锁任何职位为 -1。 */
    private fun sectPostEligibleOf(exp: Int): Int {
        var g = -1
        for (i in sectPostThresholds.indices) if (exp >= sectPostThresholds[i]) g = i
        return g
    }

    private fun sectSettings() =
        org.koin.core.context.GlobalContext.get().get<com.suanran.dreambox.data.store.AppSettingsStore>()

    // ── 轮回加点制（pack v35，用户 2026-11-05 定调：不要现在这套，完全按照轮回那种）──
    // 点数只来自这一世的作为：成就（grant）、每日修炼、俸禄、互动奖励（reward）。
    // 天数/配置/插件数的被动公式就此废除。境界靠花点突破（breakthrough），身份随境界，
    // 宗门品级与职位资格看本世功绩（累计获得点数）；一世终结走 rebirth 结算 → allocate 配点定下一世开局。
    private val sectBreakCosts = intArrayOf(60, 120, 220, 400, 600, 800)
    private val sectAllocKeys = listOf("gengu", "wuxing", "qiyun", "linggen", "jiashi", "renyuan")

    private fun sectAllocOf(s: com.suanran.dreambox.data.store.AppSettingsStore): JSONObject {
        val out = JSONObject()
        val saved = runCatching { JSONObject(s.sectAlloc.value.ifBlank { "{}" }) }.getOrNull()
        for (k in sectAllocKeys) out.put(k, saved?.optInt(k, 0) ?: 0)
        return out
    }

    /** 此世评价：功绩 + 境界 + 职位资格 → 档位与结算倍率。 */
    private fun sectEvalOf(earned: Int, rank: Int, postEligible: Int): Pair<String, Double> {
        val score = earned + rank * 300 + (postEligible + 1) * 200
        return when {
            score >= 6000 -> "S" to 1.5
            score >= 4000 -> "A" to 1.3
            score >= 2500 -> "B" to 1.15
            score >= 1200 -> "C" to 1.0
            else -> "D" to 0.8
        }
    }

    private fun sectBreakCost(rank: Int, gengu: Int): Int =
        if (rank >= 6) -1 else Math.round(sectBreakCosts[rank] * (1.0 - minOf(0.5, gengu * 0.003))).toInt()

    /** 旧公式最后运行一次：把存量修为换算成本世功绩与已解锁境界，之后旧公式永不启用。 */
    private fun sectMigrateIfNeeded(s: com.suanran.dreambox.data.store.AppSettingsStore) {
        if (s.sectRankIdx.value >= 0 && s.sectPtMigrated.value) return
        val joinedAt = s.sectJoinedAt.value
        val days = if (joinedAt <= 0L) 0 else
            ((System.currentTimeMillis() - joinedAt) / 86_400_000L).toInt().coerceAtLeast(0)
        val profileCount = runCatching {
            File(filesDir, "imported").listFiles()?.count { it.isDirectory } ?: 0
        }.getOrDefault(0)
        val packCount = runCatching {
            File(filesDir, "packs").listFiles()
                ?.count { it.isDirectory && File(it, "pack.json").isFile } ?: 0
        }.getOrDefault(0)
        val legacyExp = days * 10 + profileCount * 25 + packCount * 20 + s.sectExpBonus.value
        if (s.sectRankIdx.value < 0) {
            var r = 0
            for (i in sectRankThresholds.indices) if (legacyExp >= sectRankThresholds[i]) r = i
            s.sectRankIdx.set(r)
        }
        if (!s.sectPtMigrated.value) {
            if (s.sectPtEarned.value == 0 && legacyExp > 0) s.sectPtEarned.set(legacyExp)
            s.sectPtMigrated.set(true)
        }
    }

    private fun sectStateJson(): String {
        val s = sectSettings()
        sectMigrateIfNeeded(s)
        val joinedAt = s.sectJoinedAt.value
        val days = if (joinedAt <= 0L) 0 else
            ((System.currentTimeMillis() - joinedAt) / 86_400_000L).toInt().coerceAtLeast(0)
        val profileCount = runCatching {
            File(filesDir, "imported").listFiles()?.count { it.isDirectory } ?: 0
        }.getOrDefault(0)
        val packCount = runCatching {
            File(filesDir, "packs").listFiles()
                ?.count { it.isDirectory && File(it, "pack.json").isFile } ?: 0
        }.getOrDefault(0)
        val earned = s.sectPtEarned.value
        val balance = s.sectPtBalance.value
        val rank = s.sectRankIdx.value.coerceIn(0, 6)
        val alloc = sectAllocOf(s)
        // 身份随境界晋升；上一世配点里的家世给新一世一个出身下限（嫡系不至沦为杂役）
        var identity = minOf(rank, 5)
        val jiashi = alloc.optInt("jiashi", 0)
        val identityFloor = when { jiashi >= 300 -> 2; jiashi >= 150 -> 1; else -> 0 }
        if (identity < identityFloor) identity = identityFloor
        // 职位独立一轨：choice=-1 自动（取最高解锁）、-2 不就任、0..5 自选职位下标
        var choice = s.sectPostChoice.value
        if (!s.sectPostMigrated.value) {
            val mapped = when (choice) {
                5 -> 0; 6 -> 1; 7 -> 2; 8 -> -1; 9 -> 4; 10 -> 5
                else -> choice
            }
            if (mapped != choice) s.sectPostChoice.set(mapped)
            s.sectPostMigrated.set(true)
            choice = mapped
        }
        val postEligible = sectPostEligibleOf(earned)
        val post = when {
            postEligible < 0 || choice == -2 -> -1
            choice in 0..postEligible -> choice
            else -> postEligible
        }
        val grade = sectGradeOf(earned)
        val (evalGrade, evalMult) = sectEvalOf(earned, rank, postEligible)
        return JSONObject().apply {
            put("sectId", s.sectPathId.value)
            put("joinedAt", joinedAt)
            put("days", days)
            put("profileCount", profileCount)
            put("packCount", packCount)
            put("expBonus", s.sectExpBonus.value)
            put("exp", earned) // 兼容字段：现为本世功绩（累计获得轮回点）
            put("points", balance)
            put("earned", earned)
            put("life", s.sectPtLife.value)
            put("alloc", alloc)
            put("pool", s.sectPtPool.value)
            put("allocPending", s.sectAllocPending.value)
            put("openingStones", s.sectOpeningStones.value)
            put("lifeLog", runCatching { org.json.JSONArray(s.sectLifeLog.value.ifBlank { "[]" }) }.getOrDefault(org.json.JSONArray()))
            put("rankIndex", rank)
            put("rankName", sectRankNames[rank])
            put("identityIndex", identity)
            put("title", sectIdentityNames[identity])
            put("postIndex", post)
            put("postEligibleIndex", postEligible)
            put("eligibleIndex", postEligible)
            put("postChoice", choice)
            put("postName", if (post >= 0) sectPostNames[post] else "")
            put("nextThreshold", if (rank + 1 < sectRankThresholds.size) sectRankThresholds[rank + 1] else -1)
            put("nextBreakCost", sectBreakCost(rank, alloc.optInt("gengu", 0)))
            put("gradeIndex", grade)
            put("gradeName", sectGradeNames[grade])
            put("nextGradeThreshold", if (grade + 1 < sectGradeThresholds.size) sectGradeThresholds[grade + 1] else -1)
            put("salary", grade * 4)
            put("cultGain", 20 + grade + s.sectSeriesBonus.value)
            put("seriesBonus", s.sectSeriesBonus.value)
            put("rewardLog", s.sectRewardLog.value)
            put("lastSalaryDay", s.sectLastSalaryDay.value)
            put("lastCultivateDay", s.sectLastCultivateDay.value)
            put("evalGrade", evalGrade)
            put("evalMult", evalMult)
            put("today", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()))
            put("seedArgb", s.themeAccentColorArgb.value)
            put("themeMode", s.themeMode.value.name)
        }.toString()
    }

    /** 境界到了，整个 App 的布局跟着变：不是插件页自娱自乐，本体设置同步落盘、重启生效。 */
    private fun applySectRankLayout(rank: Int) {
        val s = sectSettings()
        s.topBarBlurEnabled.set(rank >= 2)
        s.animationEnabled.set(rank >= 3)
        s.bottomBarAutoHide.set(rank < 4)
        s.pageScale.set(
            when {
                rank <= 0 -> 0.98f
                rank >= 6 -> 1.03f
                rank >= 4 -> 1.02f
                else -> 1.0f
            }
        )
    }

    /**
     * 宿主门派桥：只有 pack.json 声明 "hostSect": true 的包能调。
     * 选修 = 把该流派的种子色/明暗写进本体主题（全 App 换装）；境界由宿主按真实使用数据算，
     * 插件页只能展示与领每日修炼加成，改不了境界本身。
     */
    private fun handleHostSect(callbackId: String, method: String, options: JSONObject) {
        if (!hostSectAllowed) {
            sendResult(callbackId, success = false, dataJson = null, error = "该插件未声明 hostSect 权限")
            return
        }
        io.execute {
            val result = runCatching {
                val s = sectSettings()
                when (method) {
                    "state" -> sectStateJson()
                    "choose" -> {
                        val sectId = options.optString("sectId")
                        val def = sectDefs[sectId] ?: throw IllegalArgumentException("未知流派 $sectId")
                        if (s.sectJoinedAt.value <= 0L) s.sectJoinedAt.set(System.currentTimeMillis())
                        s.sectPathId.set(sectId)
                        s.themeAccentColorArgb.set(def.seedArgb)
                        s.themeMode.set(
                            if (def.dark) com.suanran.dreambox.core.model.ThemeMode.Dark
                            else com.suanran.dreambox.core.model.ThemeMode.Light
                        )
                        // 门派引导走完，旧的新手引导不再弹
                        s.homePreviewGuideShown.set(true)
                        val st = JSONObject(sectStateJson())
                        applySectRankLayout(st.optInt("rankIndex", 0))
                        sectStateJson()
                    }
                    "cultivate" -> {
                        if (s.sectPathId.value.isBlank()) throw IllegalStateException("先选一个流派再修炼")
                        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            .format(java.util.Date())
                        if (s.sectLastCultivateDay.value == today) throw IllegalStateException("今日已修炼，明日再来")
                        val gain = JSONObject(sectStateJson()).optInt("cultGain", 20)
                        s.sectLastCultivateDay.set(today)
                        s.sectPtBalance.set(s.sectPtBalance.value + gain)
                        s.sectPtEarned.set(s.sectPtEarned.value + gain)
                        val st = JSONObject(sectStateJson())
                        applySectRankLayout(st.optInt("rankIndex", 0))
                        sectStateJson()
                    }
                    "setDark" -> {
                        // 明暗翻转：不改门派，只翻本体明暗（种子色不动）
                        val dark = options.optBoolean("dark", true)
                        s.themeMode.set(
                            if (dark) com.suanran.dreambox.core.model.ThemeMode.Dark
                            else com.suanran.dreambox.core.model.ThemeMode.Light
                        )
                        sectStateJson()
                    }
                    "salary" -> {
                        // 宗门俸禄：每日一次，数额由品级定（不入流没有俸禄）
                        if (s.sectPathId.value.isBlank()) throw IllegalStateException("先选一个流派再领俸禄")
                        val st0 = JSONObject(sectStateJson())
                        val grade = st0.optInt("gradeIndex", 0)
                        if (grade <= 0) throw IllegalStateException("不入流的宗门没有俸禄，先晋品再说")
                        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            .format(java.util.Date())
                        if (s.sectLastSalaryDay.value == today) throw IllegalStateException("今日俸禄已领过")
                        s.sectLastSalaryDay.set(today)
                        val sal = st0.optInt("salary", 0)
                        s.sectPtBalance.set(s.sectPtBalance.value + sal)
                        s.sectPtEarned.set(s.sectPtEarned.value + sal)
                        val st = JSONObject(sectStateJson())
                        applySectRankLayout(st.optInt("rankIndex", 0))
                        sectStateJson()
                    }
                    "choosePost" -> {
                        // 自选职位：index=-1 自动（随修为取最高解锁）、-2 不就任、0..5 职位下标（须已解锁）
                        val idx = options.optInt("index", -1)
                        when {
                            idx == -1 -> s.sectPostChoice.set(-1)
                            idx == -2 -> s.sectPostChoice.set(-2)
                            idx in sectPostNames.indices -> {
                                val st0 = JSONObject(sectStateJson())
                                if (idx > st0.optInt("postEligibleIndex", -1)) throw IllegalStateException("修为未到，还当不了${sectPostNames[idx]}")
                                s.sectPostChoice.set(idx)
                            }
                            else -> throw IllegalArgumentException("未知职位")
                        }
                        s.sectPostMigrated.set(true)
                        sectStateJson()
                    }
                    "cheat" -> {
                        // 修改器（用户自备面板）：新制下直接设定轮回点/功绩/境界；
                        // legacy 键 expBonus/expDelta 映射为功绩（境界按旧门槛一并换算，品级/职位资格随功绩）
                        sectMigrateIfNeeded(s)
                        if (options.has("points")) {
                            s.sectPtBalance.set(options.optInt("points").coerceIn(0, 999999))
                        }
                        if (options.has("earned")) {
                            s.sectPtEarned.set(options.optInt("earned").coerceIn(0, 999999))
                        }
                        if (options.has("rank")) {
                            s.sectRankIdx.set(options.optInt("rank").coerceIn(0, 6))
                        }
                        if (options.has("expBonus")) {
                            val v = options.optInt("expBonus").coerceIn(0, 999999)
                            s.sectPtEarned.set(v)
                            var r = 0
                            for (i in sectRankThresholds.indices) if (v >= sectRankThresholds[i]) r = i
                            s.sectRankIdx.set(r)
                        }
                        if (options.has("expDelta")) {
                            val v = (s.sectPtEarned.value + options.optInt("expDelta")).coerceIn(0, 999999)
                            s.sectPtEarned.set(v)
                            var r = 0
                            for (i in sectRankThresholds.indices) if (v >= sectRankThresholds[i]) r = i
                            s.sectRankIdx.set(r)
                        }
                        val st = JSONObject(sectStateJson())
                        applySectRankLayout(st.optInt("rankIndex", 0))
                        sectStateJson()
                    }
                    "reward" -> {
                        // 互动奖励（1.77）：数额与每日上限由宿主定版，插件只报 kind/result，防刷靠宿主台账
                        if (s.sectPathId.value.isBlank()) throw IllegalStateException("先选一个流派")
                        val kind = options.optString("kind")
                        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            .format(java.util.Date())
                        if (kind == "series") {
                            // 功法系列集齐：永久修炼加成 +2/系列，一次性领取
                            val sid = options.optString("series")
                            if (sid.isBlank()) throw IllegalArgumentException("缺 series")
                            val claimed = s.sectSeriesClaimed.value.split(",").filter { it.isNotBlank() }
                            if (sid in claimed) throw IllegalStateException("这个系列的加成已经领过")
                            s.sectSeriesClaimed.set((claimed + sid).joinToString(","))
                            s.sectSeriesBonus.set(s.sectSeriesBonus.value + 2)
                            return@runCatching sectStateJson()
                        }
                        val counts = mutableMapOf<String, Int>()
                        val saved = s.sectRewardLog.value
                        if (saved.startsWith("$today|")) {
                            saved.removePrefix("$today|").split(",").forEach { part ->
                                val kv = part.split("=")
                                if (kv.size == 2) counts[kv[0]] = kv[1].toIntOrNull() ?: 0
                            }
                        }
                        val result = options.optString("result")
                        // 静室（洞府设施，经 series 通道领过 "jingshi1"）让奇遇每日多 1 次
                        val eventLimit = 2 +
                            if (s.sectSeriesClaimed.value.split(",").contains("jingshi1")) 1 else 0
                        val (amount, limit) = when (kind) {
                            "spar" -> (if (result == "win") 20 else 6) to 3
                            "alchemy" -> (when (result) {
                                "perfect" -> 18; "fine" -> 10; else -> 3
                            }) to 3
                            "event" -> options.optInt("amount", 0).coerceIn(0, 20) to eventLimit
                            "lecture" -> 10 to 1
                            "patrol" -> 8 to 1
                            "tome_dup" -> 3 to 2
                            "garden" -> options.optInt("amount", 0).coerceIn(0, 8) to 1
                            else -> throw IllegalArgumentException("未知奖励 $kind")
                        }
                        val used = counts[kind] ?: 0
                        if (used >= limit) throw IllegalStateException("今日次数已用完")
                        counts[kind] = used + 1
                        s.sectRewardLog.set(today + "|" + counts.entries.joinToString(",") { "${it.key}=${it.value}" })
                        if (amount > 0) {
                            s.sectPtBalance.set(s.sectPtBalance.value + amount)
                            s.sectPtEarned.set(s.sectPtEarned.value + amount)
                        }
                        val st = JSONObject(sectStateJson())
                        applySectRankLayout(st.optInt("rankIndex", 0))
                        sectStateJson()
                    }
                    "grant" -> {
                        // 成就产点（轮回加点制的点数源头）：插件报成就 id 与点数，宿主持本世台账，
                        // 同一成就一世只发一次——插件重渲染/补报不会重复产点。
                        if (s.sectPathId.value.isBlank()) throw IllegalStateException("先选一个流派")
                        val gid = options.optString("id")
                        if (gid.isBlank()) throw IllegalArgumentException("缺成就 id")
                        val pts = options.optInt("points", 0).coerceIn(0, 500)
                        val granted = s.sectGranted.value.split(",").filter { it.isNotBlank() }
                        if (pts > 0 && gid !in granted) {
                            s.sectGranted.set((granted + gid).joinToString(","))
                            s.sectPtBalance.set(s.sectPtBalance.value + pts)
                            s.sectPtEarned.set(s.sectPtEarned.value + pts)
                        }
                        sectStateJson()
                    }
                    "breakthrough" -> {
                        // 境界不再随时间自动涨：花轮回点突破，根骨配点给花费折扣（每点 -3%）
                        if (s.sectPathId.value.isBlank()) throw IllegalStateException("先选一个流派再突破")
                        sectMigrateIfNeeded(s)
                        val rank = s.sectRankIdx.value.coerceIn(0, 6)
                        if (rank >= 6) throw IllegalStateException("已至大乘绝巅，再往上就是成仙路了")
                        val cost = sectBreakCost(rank, sectAllocOf(s).optInt("gengu", 0))
                        if (s.sectPtBalance.value < cost) throw IllegalStateException("轮回点不足：突破${sectRankNames[rank + 1]}需 $cost 点，你有 ${s.sectPtBalance.value} 点")
                        s.sectPtBalance.set(s.sectPtBalance.value - cost)
                        s.sectRankIdx.set(rank + 1)
                        applySectRankLayout(rank + 1)
                        sectStateJson()
                    }
                    "rebirth" -> sectDoRebirth(s, options.optString("ending"))
                    "allocate" -> sectDoAllocate(s, options.optJSONObject("alloc"))
                    "claimOpening" -> {
                        // 轮回包配点时托管的开局灵石，门派包领取后清零（一世一次）
                        s.sectOpeningStones.set(0)
                        sectStateJson()
                    }
                    "leave" -> {
                        s.sectPathId.set("")
                        s.themeAccentColorArgb.set(0xFF138A74L)
                        s.themeMode.set(com.suanran.dreambox.core.model.ThemeMode.Auto)
                        applySectRankLayout(1)
                        sectStateJson()
                    }
                    else -> throw IllegalArgumentException("未知方法 $method")
                }
            }
            result.onSuccess { data ->
                sendResult(callbackId, success = true, dataJson = data, error = null)
            }.onFailure { e ->
                AppLogBuffer.forceAdd(Log.ERROR, "Pack", "HostSect.$method 失败\n" + Log.getStackTraceString(e))
                sendResult(callbackId, success = false, dataJson = null, error = e.message ?: "门派桥失败")
            }
        }
    }

    /** 转世结算（HostSect 与 HostRebirth 共用）：评价×结局加成得点池，本世账目清零，入待配点阶段。 */
    private fun sectDoRebirth(s: com.suanran.dreambox.data.store.AppSettingsStore, ending: String): String {
        if (s.sectPathId.value.isBlank()) throw IllegalStateException("先选一个流派")
        sectMigrateIfNeeded(s)
        val rank = s.sectRankIdx.value.coerceIn(0, 6)
        val endingBonus = when (ending) {
            "feisheng" -> { if (rank < 6) throw IllegalStateException("飞升需大乘绝巅"); 150 }
            "zuohua" -> { if (rank < 5) throw IllegalStateException("坐化需炼虚以上"); 80 }
            "bingjie" -> { if (rank < 2) throw IllegalStateException("兵解需金丹以上"); 50 }
            else -> throw IllegalArgumentException("未知结局 $ending")
        }
        val earned = s.sectPtEarned.value
        val (evalGrade, mult) = sectEvalOf(earned, rank, sectPostEligibleOf(earned))
        val pool = (Math.round((s.sectPtBalance.value + earned * 0.1) * mult) + endingBonus).toInt()
        // 碑林台账：先把这一世记进前世录，再清账
        val log = runCatching { org.json.JSONArray(s.sectLifeLog.value.ifBlank { "[]" }) }.getOrDefault(org.json.JSONArray())
        log.put(JSONObject().apply {
            put("life", s.sectPtLife.value)
            put("ending", ending)
            put("eval", evalGrade)
            put("earned", earned)
            put("pool", pool)
        })
        s.sectLifeLog.set(log.toString())
        s.sectPtLife.set(s.sectPtLife.value + 1)
        s.sectPtPool.set(pool)
        s.sectAllocPending.set(true)
        s.sectPtBalance.set(0)
        s.sectPtEarned.set(0)
        s.sectGranted.set("")
        s.sectRankIdx.set(0)
        s.sectPostChoice.set(-1)
        s.sectLastCultivateDay.set("")
        s.sectLastSalaryDay.set("")
        s.sectRewardLog.set("")
        s.sectSeriesClaimed.set("")
        s.sectSeriesBonus.set(0)
        applySectRankLayout(0)
        return sectStateJson()
    }

    /** 转世配点（自由数值制）：六维直接填点数、总和不超点池；剩下的转入新一世余额，家世折开局灵石托管。 */
    private fun sectDoAllocate(s: com.suanran.dreambox.data.store.AppSettingsStore, a: JSONObject?): String {
        if (!s.sectAllocPending.value) throw IllegalStateException("当前不在转世配点阶段")
        if (a == null) throw IllegalArgumentException("缺 alloc")
        val clean = JSONObject()
        var sum = 0
        for (k in sectAllocKeys) {
            val v = a.optInt(k, 0).coerceIn(0, 999999)
            clean.put(k, v)
            sum += v
        }
        if (sum > s.sectPtPool.value) throw IllegalStateException("点数不够：点池 ${s.sectPtPool.value}，这套配点要 $sum")
        s.sectAlloc.set(clean.toString())
        s.sectPtBalance.set(s.sectPtPool.value - sum)
        s.sectPtPool.set(0)
        s.sectAllocPending.set(false)
        s.sectOpeningStones.set(clean.optInt("jiashi", 0) * 50)
        return sectStateJson()
    }

    /**
     * 轮回桥（轮回独立包专用）：只有 pack.json 声明 "hostRebirth": true 的包能调。
     * 账本与门派桥共用同一套存储：门派包负责产点与现世突破，轮回包负责转世、配点与前世碑林。
     */
    private fun handleHostRebirth(callbackId: String, method: String, options: JSONObject) {
        if (!hostRebirthAllowed) {
            sendResult(callbackId, success = false, dataJson = null, error = "该插件未声明 hostRebirth 权限")
            return
        }
        io.execute {
            val result = runCatching {
                val s = sectSettings()
                when (method) {
                    "state" -> sectStateJson()
                    "rebirth" -> sectDoRebirth(s, options.optString("ending"))
                    "allocate" -> sectDoAllocate(s, options.optJSONObject("alloc"))
                    else -> throw IllegalArgumentException("未知方法 $method")
                }
            }
            result.onSuccess { data ->
                sendResult(callbackId, success = true, dataJson = data, error = null)
            }.onFailure { e ->
                AppLogBuffer.forceAdd(Log.ERROR, "Pack", "HostRebirth.$method 失败\n" + Log.getStackTraceString(e))
                sendResult(callbackId, success = false, dataJson = null, error = e.message ?: "轮回桥失败")
            }
        }
    }

    /**
     * 宿主备份桥（插件化 WebDAV 等包用）：只有 pack.json 声明 "hostBackup": true 的包能调。
     * export 把全量备份 zip 写进包目录；import 从包目录读 zip 执行恢复（恢复会停核心重应用，
     * 与本体「恢复数据」同一条路）。文件名只取 basename，防路径穿越。
     */
    private fun handleHostBackup(callbackId: String, method: String, options: JSONObject) {
        if (!hostBackupAllowed) {
            sendResult(callbackId, success = false, dataJson = null, error = "该插件未声明 hostBackup 权限")
            return
        }
        io.execute {
            val result = runCatching {
                val repo = org.koin.core.context.GlobalContext.get().get<com.suanran.dreambox.core.contract.BackupDataSource>()
                when (method) {
                    "export" -> {
                        val out = File(packDir, "host-backup.zip")
                        kotlinx.coroutines.runBlocking {
                            java.io.FileOutputStream(out).use { repo.exportBackup(it) }
                        }
                        JSONObject().put("file", out.name).put("size", out.length()).toString()
                    }
                    "import" -> {
                        val name = File(options.optString("file", "host-backup.zip")).name
                        val input = File(packDir, name)
                        require(input.isFile) { "包目录里没有 $name" }
                        kotlinx.coroutines.runBlocking {
                            java.io.FileInputStream(input).use { repo.restoreBackup(it) }
                        }
                        JSONObject().put("file", name).toString()
                    }
                    else -> throw IllegalArgumentException("未知方法 $method")
                }
            }
            result.onSuccess { data ->
                sendResult(callbackId, success = true, dataJson = data, error = null)
            }.onFailure { e ->
                AppLogBuffer.forceAdd(Log.ERROR, "Pack", "HostBackup.$method 失败\n" + Log.getStackTraceString(e))
                sendResult(callbackId, success = false, dataJson = null, error = e.message ?: "备份桥失败")
            }
        }
    }

    /**
     * 宿主内核桥（内核管家包用）：只有 pack.json 声明 "hostKernel": true 的包能调。
     * 插件只画界面，列表/下载/切换/装自定义内核全部委托本体 KernelManager 在宿主进程执行，
     * 与原网络设置「内核管理」同一套逻辑与数据。
     */
    private fun handleHostKernel(callbackId: String, method: String, options: JSONObject) {
        if (!hostKernelAllowed) {
            sendResult(callbackId, success = false, dataJson = null, error = "该插件未声明 hostKernel 权限")
            return
        }
        io.execute {
            val result = runCatching {
                val km = com.suanran.dreambox.core.kernel.KernelManager
                when (method) {
                    "state" -> kernelStateJson(forceNetwork = false)
                    "refresh" -> kernelStateJson(forceNetwork = true)
                    "install" -> {
                        val id = options.optString("id")
                        require(id.isNotBlank()) { "缺少内核 id" }
                        val index = kotlinx.coroutines.runBlocking { km.fetchIndex(this@PackHostActivity) }
                        val kernel = index.kernels.firstOrNull { it.id == id }
                            ?: throw IllegalArgumentException("索引里没有内核 $id，先点检查更新")
                        kotlinx.coroutines.runBlocking { km.install(this@PackHostActivity, kernel) }
                        kernelStateJson(forceNetwork = false)
                    }
                    "activate" -> {
                        val id = options.optString("id")
                        require(id.isNotBlank()) { "缺少内核 id" }
                        km.activate(this@PackHostActivity, id)
                        kernelStateJson(forceNetwork = false)
                    }
                    "installCustom" -> {
                        val url = options.optString("url").trim()
                        require(url.startsWith("https://")) { "自定义内核链接必须以 https:// 开头" }
                        val kernel = kotlinx.coroutines.runBlocking {
                            km.installPluginUrl(this@PackHostActivity, url)
                        }
                        km.activate(this@PackHostActivity, kernel.id)
                        kernelStateJson(forceNetwork = false)
                    }
                    else -> throw IllegalArgumentException("未知方法 $method")
                }
            }
            result.onSuccess { data ->
                sendResult(callbackId, success = true, dataJson = data, error = null)
            }.onFailure { e ->
                AppLogBuffer.forceAdd(Log.ERROR, "Pack", "HostKernel.$method 失败\n" + Log.getStackTraceString(e))
                sendResult(callbackId, success = false, dataJson = null, error = e.message ?: "内核桥失败")
            }
        }
    }

    private fun kernelStateJson(forceNetwork: Boolean): String {
        val km = com.suanran.dreambox.core.kernel.KernelManager
        val ctx = this@PackHostActivity
        val activeId = km.activeKernelId(ctx)
        val index = runCatching {
            kotlinx.coroutines.runBlocking { km.fetchIndex(ctx, forceNetwork = forceNetwork) }
        }.getOrNull()
        val rows = org.json.JSONArray()
        val seen = mutableSetOf<String>()
        fun putRow(id: String, name: String, version: String, commit: String) {
            if (!seen.add(id)) return
            rows.put(JSONObject().apply {
                put("id", id)
                put("name", name)
                put("version", version)
                put("commit", commit)
                put("builtin", id == km.BUNDLED_ALPHA_ID)
                put("installed", id == km.BUNDLED_ALPHA_ID || km.isInstalled(ctx, id))
                put("active", id == activeId)
            })
        }
        // 内置内核永远在第一位（与原内核管理列表一致）
        putRow(
            km.BUNDLED_ALPHA_ID,
            "内置 Alpha",
            "内置",
            km.installedCommit(ctx, km.BUNDLED_ALPHA_ID) ?: "",
        )
        index?.kernels?.forEach { k -> putRow(k.id, k.name, k.version, k.commit) }
        // 已装但不在索引里的（自定义内核）也要能切回去
        km.installedKernelIds(ctx).forEach { id ->
            putRow(id, km.installedName(ctx, id), "已安装", km.installedCommit(ctx, id) ?: "")
        }
        val activeName = rows.let {
            var n = activeId
            for (i in 0 until it.length()) {
                val o = it.optJSONObject(i) ?: continue
                if (o.optString("id") == activeId) { n = o.optString("name"); break }
            }
            n
        }
        return JSONObject().apply {
            put("activeId", activeId)
            put("activeName", activeName)
            put("kernels", rows)
        }.toString()
    }

    private fun sendResult(callbackId: String, success: Boolean, dataJson: String?, error: String? = null, save: Boolean = false) {
        if (callbackId.isBlank()) return
        val payload = JSONObject().apply {
            put("callbackId", callbackId)
            put("success", success)
            put("save", save)
            if (dataJson != null) runCatching { put("data", JSONObject(dataJson)) }
                .onFailure { runCatching { put("data", JSONArray(dataJson)) } }
            if (error != null) put("error", JSONObject().put("message", error))
        }
        mainHandler.post {
            if (::webView.isInitialized) {
                webView.evaluateJavascript("window.Capacitor && window.Capacitor.fromNative($payload)", null)
            }
        }
    }

    private fun emitEvent(pluginId: String, eventName: String, dataJson: String) {
        val key = "$pluginId|$eventName"
        val ids = synchronized(listeners) { listeners[key]?.toList() }.orEmpty()
        ids.forEach { sendResult(it, success = true, dataJson = dataJson, save = true) }
    }

    // ── 本地资源服务 ─────────────────────────────────────────

    inner class PackWebViewClient : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val uri = request.url
            if (uri.host != "pack.local") return null
            val path = uri.path ?: "/"
            val rel = if (path == "/" || path == "/index.html") "index.html" else path.trimStart('/')
            val file = File(packDir, rel)
            val rootCanonical = packDir.canonicalPath
            if (!file.canonicalPath.startsWith(rootCanonical) || !file.isFile) {
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }
            if (rel == "index.html") {
                val html = injectBridge(file.readText())
                return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
            }
            return WebResourceResponse(mimeOf(rel), "utf-8", file.inputStream())
        }
    }

    /** 在 <head> 最前面塞入 PluginHeaders 与官方 native-bridge.js，保证早于包内脚本执行。 */
    private fun injectBridge(html: String): String {
        val bridgeFile = File(packDir, "native-bridge.js")
        val bridgeJs = if (bridgeFile.isFile) bridgeFile.readText() else ""
        val inject = StringBuilder()
            .append("<script>window.WEBVIEW_SERVER_URL='https://pack.local';window.Capacitor={PluginHeaders:")
            .append(pluginHeadersJson)
            .append("};</script>")
            .append("<script>")
            .append(bridgeJs)
            .append("</script>")
            .toString()
        val headEnd = html.indexOf("<head>")
        return if (headEnd >= 0) {
            val insertAt = headEnd + "<head>".length
            html.substring(0, insertAt) + inject + html.substring(insertAt)
        } else {
            inject + html
        }
    }

    private fun mimeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html"
        "js", "mjs" -> "text/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        else -> "application/octet-stream"
    }

    companion object {
        /** 芷湘包 v1 的插件面（与芷湘 Java 插件的 @PluginMethod 一一对应）。 */
        const val DEFAULT_PLUGIN_HEADERS: String = """[
          {"name":"HttpBridge","methods":[{"name":"request","rtype":"promise"}]},
          {"name":"Playback","methods":[
            {"name":"addListener","rtype":"callback"},{"name":"removeListener","rtype":"callback"},
            {"name":"start","rtype":"promise"},{"name":"stop","rtype":"promise"},
            {"name":"ensureProxy","rtype":"promise"},{"name":"stopProxy","rtype":"promise"},
            {"name":"setMedia","rtype":"promise"},{"name":"setPlaying","rtype":"promise"},
            {"name":"clearMedia","rtype":"promise"}]},
          {"name":"LocalMusic","methods":[
            {"name":"scan","rtype":"promise"},{"name":"coverArt","rtype":"promise"},
            {"name":"coverFile","rtype":"promise"},{"name":"checkAllFilesAccess","rtype":"promise"},
            {"name":"requestAllFilesAccess","rtype":"promise"},{"name":"writeSidecar","rtype":"promise"},
            {"name":"downloadCover","rtype":"promise"},{"name":"embeddedLyrics","rtype":"promise"},
            {"name":"saveToDownloads","rtype":"promise"}]},
          {"name":"YtmSignIn","methods":[
            {"name":"signIn","rtype":"promise"},{"name":"probeWatchPage","rtype":"promise"},
            {"name":"extractViaPage","rtype":"promise"}]},
          {"name":"Wallpaper","methods":[
            {"name":"setWallpaperState","rtype":"promise"},{"name":"openLiveWallpaperPicker","rtype":"promise"}]},
          {"name":"ExternalOpen","methods":[{"name":"openUrl","rtype":"promise"}]}
        ]"""
    }
}
