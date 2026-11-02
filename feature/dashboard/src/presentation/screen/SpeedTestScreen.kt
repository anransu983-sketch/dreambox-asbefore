package com.suanran.dreambox.feature.dashboard.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suanran.dreambox.presentation.component.card.Card
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.navigation.Navigator
import com.suanran.dreambox.presentation.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import com.suanran.dreambox.presentation.component.navigation.TopBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import java.net.HttpURLConnection
import java.net.URL

/**
 * 客户端测速：经当前实际路径（代理开着且本应用未被分流排除时 = 经当前节点）
 * 向 Cloudflare 测速端点测延迟/下载/上传。和服务器侧 TQ 互补：这个测的是节点到手机的真实路径。
 */
private data class SpeedUiState(
    val running: Boolean = false,
    val phase: String = "点开始测速",
    val liveMbps: Double = 0.0,
    val pingMs: Long? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val error: String? = null,
)

@Composable
fun SpeedTestScreen(navigator: Navigator) {
    val spacing = AppTheme.spacing
    val scrollBehavior = MiuixScrollBehavior()
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(SpeedUiState()) }
    var job by remember { mutableStateOf<Job?>(null) }

    Scaffold(
        topBar = {
            TopBar(
                title = "客户端测速",
                scrollBehavior = scrollBehavior,
                navigationIcon = { NavigationBackIcon(navigator = navigator) },
            )
        },
    ) { innerPadding ->
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = innerPadding,
        ) {
            item {
                Card(modifier = Modifier.padding(vertical = spacing.space4)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.space16, vertical = spacing.space12),
                    ) {
                        Text(
                            text = "经 App 当前网络路径测速：代理运行中测节点速度，未运行测直连速度。",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            text = state.phase,
                            style = MiuixTheme.textStyles.title4,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = spacing.space12),
                        )
                        if (state.running) {
                            Text(
                                text = "%.1f Mbps".format(state.liveMbps),
                                style = MiuixTheme.textStyles.title1,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = spacing.space4),
                            )
                        }
                        state.error?.let {
                            Text(
                                text = it,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.error,
                                modifier = Modifier.padding(top = spacing.space8),
                            )
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.padding(vertical = spacing.space4)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.space16, vertical = spacing.space12),
                        verticalArrangement = Arrangement.spacedBy(spacing.space8),
                    ) {
                        SpeedResultRow("延迟", state.pingMs?.let { "$it ms" })
                        SpeedResultRow("下载", state.downloadMbps?.let { "%.1f Mbps".format(it) })
                        SpeedResultRow("上传", state.uploadMbps?.let { "%.1f Mbps".format(it) })
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        if (state.running) {
                            job?.cancel()
                            job = null
                            state = state.copy(running = false, phase = "已停止")
                        } else {
                            state = SpeedUiState(running = true, phase = "测延迟中…")
                            job = scope.launch {
                                runSpeedTest { updated -> state = updated }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.space12),
                ) {
                    Text(text = if (state.running) "停止" else "开始测速")
                }
            }
            item {
                Text(
                    text = "测速走本机当前网络路径：代理开着、且梦盒自身没被分流排除时，测的就是当前节点的速度。测速点为 Cloudflare，下载约 30MB + 上传约 12MB，注意流量。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = spacing.space10),
                )
            }
        }
    }
}

@Composable
private fun SpeedResultRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value ?: "—",
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

private suspend fun runSpeedTest(update: (SpeedUiState) -> Unit) {
    var state = SpeedUiState(running = true, phase = "测延迟中…")
    update(state)
    try {
        val ping = withContext(Dispatchers.IO) { measurePingMs() }
        state = state.copy(pingMs = ping, phase = "测下载中…")
        update(state)
        val down = withContext(Dispatchers.IO) {
            measureDownloadMbps { live ->
                update(state.copy(liveMbps = live))
            }
        }
        state = state.copy(downloadMbps = down, liveMbps = 0.0, phase = "测上传中…")
        update(state)
        val up = withContext(Dispatchers.IO) {
            measureUploadMbps { live ->
                update(state.copy(liveMbps = live))
            }
        }
        state = state.copy(uploadMbps = up, liveMbps = 0.0, running = false, phase = "测速完成")
        update(state)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        update(state.copy(running = false, phase = "测速失败", error = e.message ?: e.javaClass.simpleName))
    }
}

private fun openSpeedConnection(url: String): HttpURLConnection {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 10_000
    conn.readTimeout = 20_000
    conn.useCaches = false
    conn.setRequestProperty("User-Agent", "DreamBox SpeedTest")
    return conn
}

private fun measurePingMs(): Long {
    var best = Long.MAX_VALUE
    repeat(3) {
        val started = System.nanoTime()
        val conn = openSpeedConnection("https://speed.cloudflare.com/__down?bytes=0")
        try {
            conn.responseCode
            val elapsed = (System.nanoTime() - started) / 1_000_000
            if (elapsed < best) best = elapsed
        } finally {
            conn.disconnect()
        }
    }
    return if (best == Long.MAX_VALUE) -1 else best
}

private suspend fun measureDownloadMbps(onLive: (Double) -> Unit): Double {
    val conn = openSpeedConnection("https://speed.cloudflare.com/__down?bytes=30000000")
    try {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        val started = System.nanoTime()
        var lastReport = 0L
        conn.inputStream.use { input ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                val elapsedMs = (System.nanoTime() - started) / 1_000_000
                if (elapsedMs - lastReport >= 200) {
                    lastReport = elapsedMs
                    if (elapsedMs > 0) onLive(total * 8.0 / (elapsedMs / 1000.0) / 1_000_000.0)
                }
                if (elapsedMs >= 12_000) break
            }
        }
        val seconds = (System.nanoTime() - started) / 1_000_000_000.0
        return if (seconds > 0.2) total * 8.0 / seconds / 1_000_000.0 else 0.0
    } finally {
        conn.disconnect()
    }
}

private suspend fun measureUploadMbps(onLive: (Double) -> Unit): Double {
    val totalBytes = 12_000_000
    val conn = openSpeedConnection("https://speed.cloudflare.com/__up")
    try {
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setFixedLengthStreamingMode(totalBytes)
        val chunk = ByteArray(64 * 1024)
        var sent = 0
        val started = System.nanoTime()
        var lastReport = 0L
        conn.outputStream.use { out ->
            while (sent < totalBytes) {
                currentCoroutineContext().ensureActive()
                val n = minOf(chunk.size, totalBytes - sent)
                out.write(chunk, 0, n)
                sent += n
                val elapsedMs = (System.nanoTime() - started) / 1_000_000
                if (elapsedMs - lastReport >= 200 && elapsedMs > 0) {
                    lastReport = elapsedMs
                    onLive(sent * 8.0 / (elapsedMs / 1000.0) / 1_000_000.0)
                }
            }
            out.flush()
        }
        conn.inputStream.use { it.readBytes() }
        val seconds = (System.nanoTime() - started) / 1_000_000_000.0
        return if (seconds > 0.2) totalBytes * 8.0 / seconds / 1_000_000.0 else 0.0
    } finally {
        conn.disconnect()
    }
}
