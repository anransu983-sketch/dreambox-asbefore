package com.suanran.dreambox.presentation.component.misc

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 首页面板编排（Surge Dashboard Panels 式）：首页下半区的卡片可排序、可隐藏。
 * 存 filesDir/home_panels.json；个性化页里管理，首页读取渲染。
 * 英雄区（流量/开关）不在面板之列，永远置顶。
 */
object HomePanels {
    const val NODE = "node"
    const val SUBSCRIPTION = "subscription"
    const val SHORTCUTS = "shortcuts"
    const val IP = "ip"
    const val SPEED = "speed"
    const val TOPOLOGY = "topology"

    val ALL: List<String> = listOf(NODE, SUBSCRIPTION, SHORTCUTS, IP, SPEED, TOPOLOGY)

    val LABELS: Map<String, String> = mapOf(
        NODE to "节点信息",
        SUBSCRIPTION to "订阅卡（流量/到期）",
        SHORTCUTS to "快捷方式",
        IP to "IP 信息",
        SPEED to "速度曲线",
        TOPOLOGY to "连接拓扑",
    )

    private const val FILE_NAME = "home_panels.json"

    var version by mutableStateOf(0)
        private set

    private var cachedOrder: List<String>? = null
    private var cachedHidden: Set<String>? = null

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun load(context: Context) {
        if (cachedOrder != null) return
        var order: List<String> = ALL
        var hidden: Set<String> = emptySet()
        runCatching {
            val f = file(context)
            if (f.exists()) {
                val obj = JSONObject(f.readText())
                val arr = obj.optJSONArray("order")
                if (arr != null) {
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        val key = arr.optString(i)
                        if (key in ALL && key !in list) list += key
                    }
                    for (key in ALL) if (key !in list) list += key
                    order = list
                }
                val hArr = obj.optJSONArray("hidden")
                if (hArr != null) {
                    val set = mutableSetOf<String>()
                    for (i in 0 until hArr.length()) {
                        val key = hArr.optString(i)
                        if (key in ALL) set += key
                    }
                    hidden = set
                }
            }
        }
        cachedOrder = order
        cachedHidden = hidden
    }

    fun order(context: Context): List<String> {
        load(context)
        return cachedOrder ?: ALL
    }

    fun hidden(context: Context): Set<String> {
        load(context)
        return cachedHidden ?: emptySet()
    }

    fun setOrder(context: Context, order: List<String>) {
        val clean = order.filter { it in ALL }.distinct() + ALL.filter { it !in order }
        cachedOrder = clean
        persist(context)
        version++
    }

    fun setHidden(context: Context, hidden: Set<String>) {
        cachedHidden = hidden.filter { it in ALL }.toSet()
        persist(context)
        version++
    }

    fun move(context: Context, key: String, delta: Int) {
        val current = order(context).toMutableList()
        val index = current.indexOf(key)
        if (index < 0) return
        val target = (index + delta).coerceIn(0, current.lastIndex)
        if (target == index) return
        current.removeAt(index)
        current.add(target, key)
        setOrder(context, current)
    }

    private fun persist(context: Context) {
        runCatching {
            val obj = JSONObject()
            val orderArr = JSONArray()
            (cachedOrder ?: ALL).forEach { orderArr.put(it) }
            obj.put("order", orderArr)
            val hiddenArr = JSONArray()
            (cachedHidden ?: emptySet()).forEach { hiddenArr.put(it) }
            obj.put("hidden", hiddenArr)
            file(context).writeText(obj.toString())
        }
    }
}

@Composable
fun rememberHomePanels(): Pair<List<String>, Set<String>> {
    val context = LocalContext.current
    val version = HomePanels.version
    return remember(version) { HomePanels.order(context) to HomePanels.hidden(context) }
}
