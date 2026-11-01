package com.suanran.dreambox.presentation.pack

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import com.suanran.dreambox.core.pack.PackAuthBus

/**
 * 通用网页授权壳（宿主预声明，给所有功能包共用）：
 * 打开包指定的登录页，用户完成登录后点「完成」，把该站 Cookie 回给包。
 */
class PackAuthActivity : Activity() {

    private lateinit var webView: WebView
    private var targetUrl: String = ""
    private var finished = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetUrl = intent.getStringExtra(PackAuthBus.EXTRA_URL).orEmpty()
        title = intent.getStringExtra(PackAuthBus.EXTRA_TITLE) ?: "登录"

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val doneButton = Button(this).apply {
            text = "完成"
            setOnClickListener { complete() }
        }
        root.addView(doneButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        webView = WebView(this)
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { gravity = Gravity.FILL })
        setContentView(root)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        webView.webViewClient = WebViewClient()
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        if (targetUrl.isNotBlank()) webView.loadUrl(targetUrl)
    }

    private fun complete() {
        if (finished) return
        finished = true
        CookieManager.getInstance().flush()
        val cookies = if (targetUrl.isNotBlank()) CookieManager.getInstance().getCookie(targetUrl).orEmpty() else ""
        PackAuthBus.complete(cookies)
        finish()
    }

    override fun onDestroy() {
        if (!finished) {
            finished = true
            PackAuthBus.complete("")
        }
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}
