package com.suanran.dreambox.feature.settings.presentation.custommodule

import com.tencent.mmkv.MMKV
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 用户自定义模块：名字 + 上传照片做图标 + 点击打开链接。
 * 会混在设置主页的磁吸模块列表里一起拖拽排序。
 */
data class CustomModule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val summary: String = "",
    /** 图标图片在本机存储的绝对路径，为空则用默认图标。 */
    val iconPath: String = "",
    /** 点击后打开的链接，为空则只展示不跳转。 */
    val url: String = "",
    /** 图标形状：rounded=圆角方, circle=圆形, squircle=超椭圆。 */
    val iconShape: String = "rounded",
    /** 图标效果：none=原图, mono=单色, glass=玻璃拟态。 */
    val iconEffect: String = "none",
    /** 单色效果时的颜色 ARGB，默认主题绿。 */
    val iconTint: Long = 0xFF138A74L,
    /** 是否隐藏（软删除，可恢复）。 */
    val hidden: Boolean = false,
)

private const val KEY_CUSTOM_MODULES = "custom_modules_v1"

class CustomModuleStore(private val mmkv: MMKV) {

    fun load(): List<CustomModule> {
        val raw = mmkv.decodeString(KEY_CUSTOM_MODULES, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                CustomModule(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    name = o.optString("name", "自定义模块"),
                    summary = o.optString("summary", ""),
                    iconPath = o.optString("iconPath", ""),
                    url = o.optString("url", ""),
                    iconShape = o.optString("iconShape", "rounded"),
                    iconEffect = o.optString("iconEffect", "none"),
                    iconTint = o.optLong("iconTint", 0xFF138A74L),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(modules: List<CustomModule>) {
        val arr = JSONArray()
        modules.forEach { m ->
            arr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("name", m.name)
                    .put("summary", m.summary)
                    .put("iconPath", m.iconPath)
                    .put("url", m.url)
                    .put("iconShape", m.iconShape)
                    .put("iconEffect", m.iconEffect)
                    .put("iconTint", m.iconTint),
            )
        }
        mmkv.encode(KEY_CUSTOM_MODULES, arr.toString())
    }
}
