package com.suanran.dreambox.core.pack

import android.app.Activity
import android.content.Context
import android.content.Intent
import java.util.concurrent.atomic.AtomicReference

/**
 * 功能包原生桥契约（宿主侧定义，包内 dex 实现）。
 *
 * 宿主只提供通用能力：WebView 容器、Capacitor 桥协议分发、包的下载/安装/删除。
 * 具体功能（例如芷湘的 HttpBridge / Playback / LocalMusic）全部在包内实现，
 * 通过 [dispatch] 以 JSON 进出；删掉包目录后，包的代码与数据一并消失。
 */
fun interface PackCallback {
    fun onResult(success: Boolean, dataJson: String?, error: String?)
}

interface PackBridgeDispatcher {
    /** [emit] 供包向网页推事件（对应 Capacitor 的 notifyListeners）。 */
    fun init(context: Context, emit: (pluginId: String, eventName: String, dataJson: String) -> Unit)

    /** 返回 true 表示该调用已被包接管（同步或异步回调）；false 表示包不处理这个插件/方法。 */
    fun dispatch(pluginId: String, method: String, optionsJson: String, callback: PackCallback): Boolean

    fun release() {}
}

/**
 * 通用网页授权总线：包内代码需要打开一个网页登录/授权页时，经宿主预声明的
 * PackAuthActivity 打开，完成后把该页面的 Cookie 字符串回给包。
 * 包本身没有任何 Activity，这层是宿主给所有包共用的壳。
 */
object PackAuthBus {
    const val ACTIVITY_CLASS = "com.suanran.dreambox.presentation.pack.PackAuthActivity"
    const val EXTRA_URL = "pack_auth_url"
    const val EXTRA_TITLE = "pack_auth_title"

    private val pending = AtomicReference<((String) -> Unit)?>(null)

    fun request(activityContext: Context, url: String, title: String, done: (String) -> Unit) {
        pending.set(done)
        val intent = Intent().apply {
            setClassName(activityContext.packageName, ACTIVITY_CLASS)
            putExtra(EXTRA_URL, url)
            putExtra(EXTRA_TITLE, title)
            if (activityContext !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activityContext.startActivity(intent)
    }

    fun complete(cookies: String) {
        pending.getAndSet(null)?.invoke(cookies)
    }
}
