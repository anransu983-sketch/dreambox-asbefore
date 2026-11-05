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

/** 功能包显示开关的作用域：模块页开关与已隐藏页恢复共用 section_hidden_packs 存储。 */
const val PACKS_VISIBILITY_SCOPE = "packs"

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
            if (!marker.isFile) throw IllegalStateException("插件缺少 pack.json")
            target.deleteRecursively()
            if (!staging.renameTo(target)) throw IllegalStateException("插件装入失败")
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
                    throw IllegalStateException("插件包含非法路径：${entry.name}")
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
        /**
         * 注册表：新增功能包只加一条定义，本体代码不用改。
         * 芷湘包已于 2026-11-02 按用户决定下架（包内网易云播不了，用户拍板删）；
         * 一键节点包同日立项接棒（SSH 全自动部署 s-ui + VLESS+Reality）。
         */
        val REGISTRY: List<PackDef> = listOf(
            PackDef(
                id = "nodeforge",
                name = "一键节点",
                summary = "填服务器 IP 与 root 密码，一键部署多协议节点（VLESS+Reality 主协议，可加 Hysteria2 与 Shadowsocks 2022）。双引擎：s-ui 面板版（订阅链接、面板绑回环防爆破）或 sing-box 无面板版（无网址，配置直接在终端里看），并按防 DNS/WebRTC 泄露模板生成梦盒配置；可选填 SSH 公钥（可多行全装），装完自动写入并可关闭密码登录；插件自带部署钥匙，密码已关的机器也能连上部署",
                version = "28",
                sizeText = "约 300 KB",
                downloadUrl = "https://github.com/anransu983-sketch/dreambox-asbefore/releases/download/packs/nodeforge-pack-v28.zip",
                entryClass = "com.suanran.pack.nodeforge.NodeForgePackEntry",
            ),
            PackDef(
                id = "rebirth",
                name = "轮回",
                summary = "高武门派与轮回已合为一体：一个包走完修仙一生与百世轮回。山门大世界照旧（选道、境界、俸禄、成就、集市、万界、NPC），山门里多了一间轮回殿——转世结算、自由配点、遗产回响、天道注视与伐天、百世长卷都在里面；每世按配点加权随机落界与修炼体系（12 界 7 体系），修全七体系走满百世可融万法归一。原高武门派包的进度（弟子、设施、NPC 记录）同源存储，自动延续。",
                version = "18",
                sizeText = "约 2.6 MB",
                downloadUrl = "https://github.com/anransu983-sketch/dreambox-asbefore/releases/download/packs/rebirth-pack-v18.zip",
                entryClass = "com.suanran.pack.rebirth.RebirthPackEntry",
            ),
            PackDef(
                id = "iptest",
                name = "IP 测试",
                summary = "IP 查询与纯净度检测：13 个常用检测站经当前代理出口一键打开",
                version = "1",
                sizeText = "约 30 KB",
                downloadUrl = "https://github.com/anransu983-sketch/dreambox-asbefore/releases/download/packs/iptest-pack-v1.zip",
                entryClass = "com.suanran.pack.iptest.IpTestPackEntry",
            ),
            PackDef(
                id = "unlock",
                name = "流媒体解锁",
                summary = "经当前节点出口检测 Netflix / Disney+ / YouTube Premium 的解锁情况与地区；平台判定逻辑放插件里，改版漂移只更新插件不用发版",
                version = "1",
                sizeText = "约 60 KB",
                downloadUrl = "https://github.com/anransu983-sketch/dreambox-asbefore/releases/download/packs/unlock-pack-v1.zip",
                entryClass = "com.suanran.pack.unlock.UnlockPackEntry",
            ),
            PackDef(
                id = "webdav",
                name = "WebDAV 云备份",
                summary = "把全量备份（配置/订阅/设置）存到自己的 WebDAV 网盘：备份包由本体生成与恢复，本插件只管传输；账号密码加密保存在本机",
                version = "1",
                sizeText = "约 120 KB",
                downloadUrl = "https://github.com/anransu983-sketch/dreambox-asbefore/releases/download/packs/webdav-pack-v1.zip",
                entryClass = "com.suanran.pack.webdav.WebDavPackEntry",
            ),
            PackDef(
                id = "sshtty",
                name = "VPS 终端",
                summary = "JuiceSSH 式 SSH 终端：服务器 IP/密码/私钥输一次加密保存，下次点一下直接进终端（xterm + 功能键条）；支持私钥登录，密钥可在插件里生成；另带一键 TQ 测速、一键融合怪、一键 IP 体检（报告默认给 IP 打码）、一键 NodeQuality 全家桶（硬件/IP/网络/回程四合一）（跑完报告链接都自动提取成卡，可复制可直接打开，日志自动洗掉颜色码）与一键调优（Tcpfit 审计版，哈希校验＋可回滚）",
                version = "10",
                sizeText = "约 340 KB",
                downloadUrl = "https://github.com/anransu983-sketch/dreambox-asbefore/releases/download/packs/sshtty-pack-v10.zip",
                entryClass = "com.suanran.pack.sshtty.SshTermPackEntry",
            ),
        )
    }
}
