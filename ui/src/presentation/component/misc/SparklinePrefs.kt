package com.suanran.dreambox.presentation.component.misc

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File

/**
 * 延迟走势小曲线的外观偏好（2026-11-03 用户点单：颜色和形状要能改）：
 * 形状 0=折线 1=平滑曲线 2=柱状；颜色 0=跟随主题 1=按延迟档（绿/黄/红）。
 * 默认 = 改版前的样子（折线 + 按延迟），用户在主题页「个性化 → 延迟曲线」里改。
 * 存 filesDir/sparkline_prefs.json，与 DelayHistory 同一套路。
 */
object SparklinePrefs {
    const val SHAPE_LINE = 0
    const val SHAPE_SMOOTH = 1
    const val SHAPE_BARS = 2
    const val COLOR_THEME = 0
    const val COLOR_LATENCY = 1

    var version by mutableStateOf(0)
        private set

    private var loaded = false
    private var shapeValue = SHAPE_LINE
    private var colorValue = COLOR_LATENCY

    private fun file(context: Context): File = File(context.filesDir, "sparkline_prefs.json")

    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val f = file(context)
            if (f.exists()) {
                val obj = JSONObject(f.readText())
                shapeValue = obj.optInt("shape", SHAPE_LINE)
                colorValue = obj.optInt("color", COLOR_LATENCY)
            }
        }
    }

    private fun save(context: Context) {
        runCatching {
            file(context).writeText(
                JSONObject().put("shape", shapeValue).put("color", colorValue).toString()
            )
        }
        version++
    }

    fun shape(context: Context): Int {
        load(context)
        return shapeValue
    }

    fun colorMode(context: Context): Int {
        load(context)
        return colorValue
    }

    fun setShape(context: Context, value: Int) {
        load(context)
        shapeValue = value
        save(context)
    }

    fun setColorMode(context: Context, value: Int) {
        load(context)
        colorValue = value
        save(context)
    }
}
