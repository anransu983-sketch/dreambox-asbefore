package com.suanran.dreambox.feature.settings.presentation.pack

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/** 一个可下载功能包的定义。包内容与本体完全分离：本体只认识这份元数据和通用宿主。 */
data class PackDef(
    val id: String,
    val name: String,
    val summary: String,
    val version: String,
    val sizeText: String,
    val downloadUrl: String,
    /** 包内桥入口类名（实现 core 的 PackBridgeDispatcher），由宿主用 DexClassLoader 加载。 */
    val entryClass: String,
)

/**
 * 功能包仓库：下载、解压、删除都在应用私有目录 packs/<id>/ 下完成。
 * 已安装的判据是目录里有 pack.json 且与注册表版本一致。
 */
class PackStore(context: Context) {
    private val packsRoot = File(context.applicationContext.filesDir, "packs")
    private val cacheDir = File(context.applicationContext.cacheDir, "pack-downloads")
    private val http = OkHttpClient()

    fun dirOf(id: String): File = File(packsRoot, id)

    fun isInstalled(def: PackDef): Boolean {
        val marker = File(dirOf(def.id), "pack.json")
        if (!marker.isFile) return false
        return runCatching {
            val json = JSONObject(marker.readText())
            json.optString("id") == def.id && json.optString("version") == def.version
        }.getOrDefault(false)
    }

    /** 已安装包占用的磁盘大小（字节），未安装返回 0。 */
    fun installedSize(id: String): Long {
        val dir = dirOf(id)
        if (!dir.isDirectory) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** 下载并安装；[onProgress] 回调 0..1，未知总长时为 -1。失败抛异常，半成品目录会被清掉。 */
    suspend fun install(def: PackDef, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        cacheDir.mkdirs()
        val zipFile = File(cacheDir, "${def.id}.zip")
        val request = Request.Builder().url(def.downloadUrl).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("下载失败（HTTP ${response.code}）")
            val body = response.body ?: throw IllegalStateException("下载内容为空")
            val total = body.contentLength()
            body.byteStream().use { input ->
                FileOutputStream(zipFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        onProgress(if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f)
                    }
                }
            }
        }
        val target = dirOf(def.id)
        val staging = File(packsRoot, "${def.id}.staging")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            unzipSafely(zipFile, staging)
            val marker = File(staging, "pack.json")
            if (!marker.isFile) throw IllegalStateException("功能包缺少 pack.json")
            target.deleteRecursively()
            if (!staging.renameTo(target)) throw IllegalStateException("功能包装入失败")
        } catch (t: Throwable) {
            staging.deleteRecursively()
            throw t
        } finally {
            zipFile.delete()
        }
    }

    fun uninstall(id: String) {
        dirOf(id).deleteRecursively()
    }

    private fun unzipSafely(zipFile: File, destDir: File) {
        val destCanonical = destDir.canonicalPath
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val outFile = File(destDir, entry.name)
                val outCanonical = outFile.canonicalPath
                if (outCanonical != destCanonical && !outCanonical.startsWith(destCanonical + File.separator)) {
                    throw IllegalStateException("功能包包含非法路径：${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
            }
        }
    }

    companion object {
        /** 注册表：新增功能包只加一条定义，本体代码不用改。 */
        val REGISTRY: List<PackDef> = listOf(
            PackDef(
                id = "zhixiang",
                name = "芷湘",
                summary = "音乐播放器：本地 / 网易云 / YouTube Music 三源，独立芷湘 App 的全部功能以功能包形式在梦盒里跑",
                version = "1",
                sizeText = "约 2 MB",
                downloadUrl = "https://github.com/anransu983-sketch/dreambox-asbefore/releases/download/packs/zhixiang-pack-v1.zip",
                entryClass = "com.suanran.pack.zhixiang.ZhixiangPackEntry",
            ),
        )
    }
}
