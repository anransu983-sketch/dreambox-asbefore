package com.suanran.dreambox.feature.settings.presentation.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suanran.dreambox.feature.settings.presentation.pack.PackDef
import com.suanran.dreambox.feature.settings.presentation.pack.PackStore
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.misc.BocchiCard
import com.suanran.dreambox.presentation.component.misc.BocchiTextDark
import com.suanran.dreambox.presentation.component.misc.BocchiTextGray
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.navigation.Navigator
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "0 MB"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

/**
 * 功能包：本体只带核心代理，重型功能按需下载。
 * 下完立刻能用，删掉立刻腾空间（包目录整体移除）。
 */
@Composable
fun FeaturePacksScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val store = remember { PackStore(context) }
    val scope = rememberCoroutineScope()
    var refreshTick by remember { mutableStateOf(0) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var progressText by remember { mutableStateOf("") }

    fun openPack(def: PackDef) {
        val intent = Intent().apply {
            setClassName(context.packageName, "com.suanran.dreambox.presentation.pack.PackHostActivity")
            putExtra("pack_id", def.id)
            putExtra("pack_name", def.name)
            putExtra("pack_dir", store.dirOf(def.id).absolutePath)
            putExtra("pack_entry_class", def.entryClass)
        }
        context.startActivity(intent)
    }

    Scaffold(
        topBar = {
            TopBar(
                title = "功能包",
                scrollBehavior = scrollBehavior,
                navigationIcon = { NavigationBackIcon(navigator = navigator) },
            )
        },
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "本体只带核心代理功能，重型功能按需下载。下完立刻能用，删掉立刻腾空间。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            if (PackStore.REGISTRY.isEmpty()) {
                item {
                    Text(
                        text = "暂无可用功能包。",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }
            items(PackStore.REGISTRY.size, key = { PackStore.REGISTRY[it].id }) { index ->
                val def = PackStore.REGISTRY[index]
                @Suppress("UNUSED_EXPRESSION") refreshTick
                val installed = remember(refreshTick, def.id) { store.isInstalled(def) }
                val installedBytes = remember(refreshTick, def.id) { store.installedSize(def.id) }
                val busy = busyId == def.id
                BocchiCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = def.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BocchiTextDark,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "v${def.version}",
                                fontSize = 11.sp,
                                color = BocchiTextGray,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = def.summary,
                            fontSize = 12.sp,
                            color = BocchiTextGray,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        if (installed) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "✓ 已安装 v${def.version}",
                                    fontSize = 12.sp,
                                    color = ColorGreen,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = formatSize(installedBytes),
                                    fontSize = 12.sp,
                                    color = BocchiTextGray,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "打开",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.clickable(enabled = !busy) { openPack(def) },
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "删除",
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.clickable(enabled = !busy) {
                                        store.uninstall(def.id)
                                        refreshTick++
                                        Toast.makeText(context, "已删除 ${def.name}", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (busy) progressText else "未安装",
                                    fontSize = 12.sp,
                                    color = BocchiTextGray,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = def.sizeText,
                                    fontSize = 12.sp,
                                    color = BocchiTextGray,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (busy) "下载中…" else "下载",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.clickable(enabled = !busy) {
                                        busyId = def.id
                                        progressText = "下载中…"
                                        scope.launch {
                                            try {
                                                store.install(def) { p ->
                                                    progressText = if (p >= 0f) "下载中 ${(p * 100).toInt()}%" else "下载中…"
                                                }
                                                Toast.makeText(context, "${def.name} 已安装", Toast.LENGTH_SHORT).show()
                                            } catch (t: Throwable) {
                                                Toast.makeText(context, "下载失败：${t.message}", Toast.LENGTH_LONG).show()
                                            } finally {
                                                busyId = null
                                                refreshTick++
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val ColorGreen = androidx.compose.ui.graphics.Color(0xFF43A047)
