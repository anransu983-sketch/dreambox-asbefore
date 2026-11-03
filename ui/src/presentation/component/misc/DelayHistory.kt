package com.suanran.dreambox.presentation.component.misc

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.json.JSONObject
import java.io.File

/**
 * 节点延迟历史：每个节点最近 24 次测速（ms），存 filesDir/delay_history.json。
 * 参考小火箭的延迟走势：节点行画一条小曲线，比单个数字更能看出节点稳不稳。
 * 超时（delay<=0）不记，避免把曲线拉变形。
 */
object DelayHistory {
    private const val FILE_NAME = "delay_history.json"
    private const val MAX_POINTS = 24

    var version by mutableStateOf(0)
        private set

    private var cache: MutableMap<String, MutableList<Int>>? = null

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun load(context: Context): MutableMap<String, MutableList<Int>> {
        cache?.let { return it }
        val map = mutableMapOf<String, MutableList<Int>>()
        runCatching {
            val f = file(context)
            if (f.exists()) {
                val obj = JSONObject(f.readText())
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val arr = obj.optJSONArray(k) ?: continue
                    val list = mutableListOf<Int>()
                    for (i in 0 until arr.length()) {
                        val v = arr.optInt(i, 0)
                        if (v in 1..5000) list.add(v)
                    }
                    if (list.isNotEmpty()) map[k] = list
                }
            }
        }
        cache = map
        return map
    }

    @Synchronized
    fun record(context: Context, node: String, delayMs: Int) {
        // 超时（<=0）与内核失败哨兵 65535（>5000）都不记，避免把曲线拉变形。
        if (delayMs <= 0 || delayMs > 5000) return
        val map = load(context)
        val list = map.getOrPut(node) { mutableListOf() }
        if (list.lastOrNull() == delayMs) return
        list.add(delayMs)
        while (list.size > MAX_POINTS) list.removeAt(0)
        runCatching {
            val obj = JSONObject()
            map.forEach { (k, v) ->
                val arr = org.json.JSONArray()
                v.forEach { arr.put(it) }
                obj.put(k, arr)
            }
            file(context).writeText(obj.toString())
        }
        version++
    }

    fun history(context: Context, node: String): List<Int> =
        load(context)[node]?.toList() ?: emptyList()
}

@Composable
fun rememberDelayHistory(node: String): List<Int> {
    val context = LocalContext.current
    val version = DelayHistory.version
    return remember(node, version) { DelayHistory.history(context, node) }
}
