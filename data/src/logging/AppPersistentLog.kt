package com.suanran.dreambox.data.logging

import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.LinkedBlockingQueue

/**
 * 持久化应用日志：把 AppLogBuffer 的应用侧日志（不含 mihomo 实时流）异步落盘到
 * filesDir/logs/app-diagnostic.log，App 重启/被杀后不丢，可在「日志」页直接打开/导出。
 * 单文件滚动：启动时若超过上限，旧文件改名为 app-diagnostic.old.log（保留一份）。
 * 写入走单线程队列批量刷盘，调用方永不阻塞、永不抛异常。
 */
object AppPersistentLog {
    private const val FILE_NAME = "app-diagnostic.log"
    private const val OLD_FILE_NAME = "app-diagnostic.old.log"
    private const val MAX_FILE_BYTES = 1_000_000L

    private val queue = LinkedBlockingQueue<String>(4096)
    private val initLock = Any()

    @Volatile
    private var started = false

    fun init(context: Context) {
        synchronized(initLock) {
            if (started) return
            started = true
        }
        val appContext = context.applicationContext
        val dir = File(appContext.filesDir, "logs").apply { mkdirs() }
        val file = File(dir, FILE_NAME)
        runCatching {
            if (file.isFile && file.length() > MAX_FILE_BYTES) {
                val old = File(dir, OLD_FILE_NAME)
                if (old.exists()) old.delete()
                file.renameTo(old)
            }
        }
        val version = runCatching {
            val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            "${info.versionName} (${info.versionCode})"
        }.getOrDefault("?")
        val bootTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
        append("[$bootTime] [INFO] App: ===== DreamBox start, version $version =====")
        val writer = Thread({
            var buffered: BufferedWriter? = null
            try {
                buffered = BufferedWriter(FileWriter(file, true))
                while (true) {
                    val first = queue.take()
                    buffered.write(first)
                    buffered.newLine()
                    var next = queue.poll()
                    while (next != null) {
                        buffered.write(next)
                        buffered.newLine()
                        next = queue.poll()
                    }
                    buffered.flush()
                }
            } catch (_: Throwable) {
                // 日志线程静默失败，绝不影响 App 本体。
            } finally {
                runCatching { buffered?.close() }
            }
        }, "AppPersistentLogWriter")
        writer.isDaemon = true
        writer.start()
    }

    /** 入队一条已格式化日志行；未初始化或队列满时静默丢弃。 */
    fun append(line: String) {
        if (!started) return
        runCatching { queue.offer(line) }
    }
}
