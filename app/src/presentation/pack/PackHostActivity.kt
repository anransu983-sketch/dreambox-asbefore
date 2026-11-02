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
