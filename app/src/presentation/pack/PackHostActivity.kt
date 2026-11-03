package com.suanran.dreambox.presentation.pack

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    }

    override fun onDestroy() {
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
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else finish()
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
    // 身份阶梯（用户定版，独立于境界按修为晋升）
    // 十一档：弟子线（杂役→外门→内门→核心→真传）→执事（管外门/内门）→长老（管杂役/外门/内门）→峰主（执掌一峰）→道子→门主→掌门老祖（4500，与圣地同期）
    private val sectIdentityThresholds = intArrayOf(0, 60, 180, 400, 800, 1100, 1400, 1900, 2500, 3200, 4500)
    private val sectIdentityNames = listOf("杂役弟子", "外门弟子", "内门弟子", "核心弟子", "真传弟子", "执事", "长老", "峰主", "道子", "门主", "掌门老祖")

    private fun sectIdentityOf(exp: Int): Int {
        var g = 0
        for (i in sectIdentityThresholds.indices) if (exp >= sectIdentityThresholds[i]) g = i
        return g
    }

    private fun sectSettings() =
        org.koin.core.context.GlobalContext.get().get<com.suanran.dreambox.data.store.AppSettingsStore>()

    /** 修为 = 入门天数×10 + 已导入配置×25 + 已装插件×20 + 插件内修炼加成。 */
    private fun sectStateJson(): String {
        val s = sectSettings()
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
        val bonus = s.sectExpBonus.value
        val exp = days * 10 + profileCount * 25 + packCount * 20 + bonus
        var rank = 0
        for (i in sectRankThresholds.indices) if (exp >= sectRankThresholds[i]) rank = i
        val next = if (rank + 1 < sectRankThresholds.size) sectRankThresholds[rank + 1] else -1
        return JSONObject().apply {
            put("sectId", s.sectPathId.value)
            put("joinedAt", joinedAt)
            put("days", days)
            put("profileCount", profileCount)
            put("packCount", packCount)
            put("expBonus", bonus)
            put("exp", exp)
            put("rankIndex", rank)
            put("rankName", sectRankNames[rank])
            // 职务自选：执事（下标5）起是职务，修为解锁后可自选（含选低不选高）；未选或所选未解锁时按最高解锁档
            val eligible = sectIdentityOf(exp)
            val choice = s.sectPostChoice.value
            val identity = if (choice >= 5 && choice <= eligible) choice else eligible
            put("identityIndex", identity)
            put("eligibleIndex", eligible)
            put("postChoice", choice)
            put("title", sectIdentityNames[identity])
            put("nextThreshold", next)
            val grade = sectGradeOf(exp)
            put("gradeIndex", grade)
            put("gradeName", sectGradeNames[grade])
            put("nextGradeThreshold", if (grade + 1 < sectGradeThresholds.size) sectGradeThresholds[grade + 1] else -1)
            put("salary", grade * 4)
            put("cultGain", 20 + grade)
            put("lastSalaryDay", s.sectLastSalaryDay.value)
            put("lastCultivateDay", s.sectLastCultivateDay.value)
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
                rank <= 0 -> 0.96f
                rank >= 6 -> 1.04f
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
                        s.sectExpBonus.set(s.sectExpBonus.value + gain)
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
                        s.sectExpBonus.set(s.sectExpBonus.value + st0.optInt("salary", 0))
                        val st = JSONObject(sectStateJson())
                        applySectRankLayout(st.optInt("rankIndex", 0))
                        sectStateJson()
                    }
                    "choosePost" -> {
                        // 自选职务：index=-1 回到自动（随修为）；5..10 须已解锁（门槛<=当前修为）
                        val idx = options.optInt("index", -1)
                        if (idx == -1) {
                            s.sectPostChoice.set(-1)
                        } else {
                            if (idx < 5 || idx >= sectIdentityNames.size) throw IllegalArgumentException("该档不是可选职务")
                            val st0 = JSONObject(sectStateJson())
                            if (idx > st0.optInt("eligibleIndex", 0)) throw IllegalStateException("修为未到，还当不了${sectIdentityNames[idx]}")
                            s.sectPostChoice.set(idx)
                        }
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
