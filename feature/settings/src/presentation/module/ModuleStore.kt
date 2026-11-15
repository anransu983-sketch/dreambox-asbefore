package com.suanran.dreambox.feature.settings.presentation.module

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * 轻模块的本地仓库：下载、安装、更新、卸载都在应用私有目录 modules/<id>/ 下完成。
 *
 * 和 PackStore 的区别：这里装的是纯规则 JSON，不含 dex、不走 PackHostActivity，
 * 开关也不只是“从页面隐藏”，而是真的进/出流量规则（见 ModuleOverrideBridge）。
 *
 * 远程索引：学琳的 packs.json，不硬编码进 App。
 * 索引挂在 packs Release 上，加新模块只改索引文件，不发 App 版。
 * 索引拉不到（没网 / 文件还没上传）时用内置三种子兜底，市场照常能打开。
 */
class ModuleStore(context: Context) {
    private val modulesRoot = File(context.applicationContext.filesDir, "modules")
    private val stateFile = File(modulesRoot, "state.json")
    private val http = OkHttpClient()

    data class InstalledModule(val def: ModuleDef, val enabled: Boolean)

    /** 开关状态存在 state.json，不写进 module.json（那是远端原样内容）。 */
    private fun loadState(): JSONObject {
        if (!stateFile.isFile) return JSONObject()
        return runCatching { JSONObject(stateFile.readText()) }.getOrDefault(JSONObject())
    }

    private fun saveState(state: JSONObject) {
        modulesRoot.mkdirs()
        stateFile.writeText(state.toString())
    }

    private fun enabledIds(state: JSONObject): MutableSet<String> {
        val set = mutableSetOf<String>()
        val arr = state.optJSONArray("enabled") ?: return set
        for (i in 0 until arr.length()) set += arr.optString(i)
        return set
    }

    fun dirOf(id: String): File = File(modulesRoot, id)

    fun listInstalled(): List<InstalledModule> {
        if (!modulesRoot.isDirectory) return emptyList()
        val state = loadState()
        val enabled = enabledIds(state)
        return modulesRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val marker = File(dir, "module.json")
                if (!marker.isFile) return@mapNotNull null
                runCatching { ModuleDef.parseText(marker.readText()) }.getOrNull()
                    ?.let { InstalledModule(it, it.id in enabled) }
            }
            ?.sortedBy { it.def.id }
            ?: emptyList()
    }

    fun isInstalled(id: String): Boolean = File(dirOf(id), "module.json").isFile

    fun isEnabled(id: String): Boolean = id in enabledIds(loadState())

    /** 拉远程索引，失败就回内置种子。返回（列表，是否来自远端）。 */
    suspend fun fetchMarket(): Pair<List<ModuleDef>, Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(MODULES_INDEX_URL).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                val body = response.body.string()
                ModuleDef.parseIndex(body) to true
            }
        }.getOrElse { SeedModules.ALL to false }
    }

    /** 从市场安装：索引条目本身就是完整模块定义，直接落盘。装完默认启用。 */
    suspend fun install(def: ModuleDef): ModuleDef = withContext(Dispatchers.IO) {
        val dir = dirOf(def.id)
        dir.mkdirs()
        File(dir, "module.json").writeText(def.toJson().toString(2))
        setEnabled(def.id, true)
        def
    }

    /** 从 URL 安装：拉一份 module.json，校验通过才落盘。 */
    suspend fun installFromUrl(rawUrl: String): ModuleDef = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        require(url.startsWith("http://") || url.startsWith("https://")) { "只支持 http/https 链接" }
        val request = Request.Builder().url(url).build()
        val text = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("下载失败（HTTP ${response.code}）")
            response.body.string()
        }
        val def = ModuleDef.parseText(text).copy(sourceUrl = url)
        install(def)
    }

    /** 更新：用市场里的新版覆盖旧文件，开关状态保持不变。 */
    suspend fun update(def: ModuleDef): ModuleDef = withContext(Dispatchers.IO) {
        val wasEnabled = isEnabled(def.id)
        val dir = dirOf(def.id)
        dir.mkdirs()
        File(dir, "module.json").writeText(def.toJson().toString(2))
        setEnabled(def.id, wasEnabled)
        def
    }

    fun uninstall(id: String) {
        dirOf(id).deleteRecursively()
        val state = loadState()
        val enabled = enabledIds(state)
        if (enabled.remove(id)) {
            state.put("enabled", enabled.toList())
            saveState(state)
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val state = loadState()
        val set = enabledIds(state)
        if (enabled) set += id else set -= id
        state.put("enabled", set.toList())
        saveState(state)
    }

    companion object {
        /**
         * 远程索引地址（packs Release）。文件还没上传前拉不到，
         * fetchMarket 会自动回落到内置三种子，不报错。
         */
        const val MODULES_INDEX_URL =
            "https://github.com/anransu983-sketch/dreambox-asbefore/releases/download/packs/modules.json"
    }
}
