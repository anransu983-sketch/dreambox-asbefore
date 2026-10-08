/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c)  YumeYucca 2025 - Present
 * Based on YumeBox by YumeYucca
 *
 */

package com.suanran.dreambox.feature.dashboard.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/** 检测的流媒体平台。 */
enum class StreamPlatform(val displayName: String) {
    NETFLIX("Netflix"),
    DISNEY_PLUS("Disney+"),
    YOUTUBE_PREMIUM("YouTube Premium"),
}

/** 单个平台的解锁检测结果。 */
sealed interface StreamUnlockResult {
    data object Idle : StreamUnlockResult
    data object Checking : StreamUnlockResult
    data class Unlocked(val region: String?) : StreamUnlockResult
    data object Locked : StreamUnlockResult
    data object Failed : StreamUnlockResult
}

data class StreamPlatformState(
    val platform: StreamPlatform,
    val result: StreamUnlockResult = StreamUnlockResult.Idle,
)

/**
 * 流媒体解锁检测：请求经本机 mihomo mixed 端口代理发出，走当前节点出站。
 *
 * 注意：用 HttpURLConnection 而非 OkHttp/Ktor——dashboard 模块编译期没有
 * okhttp/ktor 依赖（core 里是 implementation，不透出），且要求不引入新依赖。
 */
class StreamUnlockViewModel : ViewModel() {
    private val _states =
        MutableStateFlow(StreamPlatform.entries.map { StreamPlatformState(it) })
    val states: StateFlow<List<StreamPlatformState>> = _states.asStateFlow()

    private val jobs = mutableMapOf<StreamPlatform, Job>()

    /** 检测全部平台。 */
    fun checkAll() {
        StreamPlatform.entries.forEach(::check)
    }

    /** 检测单个平台；重复点击会取消上一次检测。 */
    fun check(platform: StreamPlatform) {
        jobs[platform]?.cancel()
        setResult(platform, StreamUnlockResult.Checking)
        jobs[platform] =
            viewModelScope.launch {
                val result =
                    try {
                        withContext(Dispatchers.IO) { probe(platform) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "StreamUnlock: unexpected error: ${platform.name}")
                        StreamUnlockResult.Failed
                    }
                setResult(platform, result)
            }
    }

    private fun setResult(platform: StreamPlatform, result: StreamUnlockResult) {
        _states.update { states ->
            states.map { if (it.platform == platform) it.copy(result = result) else it }
        }
    }

    override fun onCleared() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }
}

/** mihomo mixed 端口：检测流量经本机 HTTP 代理，借当前节点出口访问。 */
private val PROXY = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 7890))
private const val TIMEOUT_MS = 10_000

/** 响应体读取上限，避免下载完整大页面。 */
private const val MAX_BODY_BYTES = 512 * 1024

private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

/** 从 Netflix 跳转 Location 解析地区码，如 https://www.netflix.com/jp/title/... → JP。 */
private val NETFLIX_REGION_REGEX =
    Regex("netflix\\.com/([a-z]{2}(?:-[a-z]{2})?)/title", RegexOption.IGNORE_CASE)

private fun probe(platform: StreamPlatform): StreamUnlockResult =
    try {
        when (platform) {
            StreamPlatform.NETFLIX -> probeNetflix()
            StreamPlatform.DISNEY_PLUS -> probeDisneyPlus()
            StreamPlatform.YOUTUBE_PREMIUM -> probeYouTubePremium()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // 任何异常 → 检测失败，不崩溃
        Timber.w(e, "StreamUnlock: probe failed: ${platform.name}")
        StreamUnlockResult.Failed
    }

private fun openConnection(url: String, followRedirects: Boolean): HttpURLConnection {
    val conn = URL(url).openConnection(PROXY) as HttpURLConnection
    conn.connectTimeout = TIMEOUT_MS
    conn.readTimeout = TIMEOUT_MS
    conn.instanceFollowRedirects = followRedirects
    conn.setRequestProperty("User-Agent", USER_AGENT)
    conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
    return conn
}

private fun HttpURLConnection.readBodyCapped(): String {
    val stream =
        try {
            inputStream
        } catch (e: IOException) {
            errorStream ?: throw e
        }
    val buffer = ByteArray(MAX_BODY_BYTES + 1)
    var total = 0
    stream.use { input ->
        while (total <= MAX_BODY_BYTES) {
            val n = input.read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
    }
    return String(buffer, 0, total, Charsets.UTF_8)
}

/**
 * Netflix：不跟随跳转，看 Location 头。
 * 含 netflix.com/title 且路径不含 notAvailable → 解锁；尝试从 Location 解析地区码。
 */
private fun probeNetflix(): StreamUnlockResult {
    val conn = openConnection("https://www.netflix.com/title/80018499", followRedirects = false)
    try {
        val code = conn.responseCode
        val location = conn.getHeaderField("Location").orEmpty()
        val isTitleRedirect = location.contains("netflix.com/title", ignoreCase = true)
        val isNotAvailable = location.contains("notavailable", ignoreCase = true)
        return when {
            // 直接返回标题页（无跳转）→ 解锁，地区未知
            code == 200 -> StreamUnlockResult.Unlocked(region = null)
            code in 300..399 && isTitleRedirect && !isNotAvailable ->
                StreamUnlockResult.Unlocked(
                    region =
                        NETFLIX_REGION_REGEX.find(location)
                            ?.groupValues
                            ?.get(1)
                            ?.uppercase(),
                )
            // 跳转到 notAvailable → 未解锁
            code in 300..399 && isNotAvailable -> StreamUnlockResult.Locked
            else -> StreamUnlockResult.Failed
        }
    } finally {
        conn.disconnect()
    }
}

/** Disney+：跟随跳转，200 且内容含 disneyplus 且不含不可用关键字 → 解锁。 */
private fun probeDisneyPlus(): StreamUnlockResult {
    val conn = openConnection("https://www.disneyplus.com", followRedirects = true)
    try {
        if (conn.responseCode != 200) return StreamUnlockResult.Failed
        val body = conn.readBodyCapped()
        val unlocked =
            body.contains("disneyplus", ignoreCase = true) &&
                !body.contains("not available", ignoreCase = true) &&
                !body.contains("unavailable", ignoreCase = true)
        return if (unlocked) {
            StreamUnlockResult.Unlocked(region = null)
        } else {
            StreamUnlockResult.Locked
        }
    } finally {
        conn.disconnect()
    }
}

/** YouTube Premium：内容含 Premium 且不含 not available → 解锁（地区难取，只显示已解锁）。 */
private fun probeYouTubePremium(): StreamUnlockResult {
    val conn = openConnection("https://www.youtube.com/premium", followRedirects = true)
    try {
        if (conn.responseCode != 200) return StreamUnlockResult.Failed
        val body = conn.readBodyCapped()
        val unlocked =
            body.contains("Premium") &&
                !body.contains("not available", ignoreCase = true)
        return if (unlocked) {
            StreamUnlockResult.Unlocked(region = null)
        } else {
            StreamUnlockResult.Locked
        }
    } finally {
        conn.disconnect()
    }
}
