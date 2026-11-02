package com.suanran.dreambox.feature.settings.presentation.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suanran.dreambox.feature.settings.presentation.pack.PACKS_VISIBILITY_SCOPE
import com.suanran.dreambox.feature.settings.presentation.pack.PackDef
import com.suanran.dreambox.feature.settings.presentation.pack.PackStore
import com.suanran.dreambox.presentation.component.sortable.loadHiddenSections
import com.tencent.mmkv.MMKV
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
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

private fun packIcon(id: String): String = when (id) {
    "nodeforge" -> "🚀"
    else -> "🧩"
}

/** 卡片底部的大按钮（磁吸模块式，和设置页的大卡片语言对齐）。 */
@Composable
private fun PackActionButton(
    text: String,
    filled: Boolean,
    danger: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val primary = MiuixTheme.colorScheme.primary
    val shape = RoundedCornerShape(12.dp)
    val bg = when {
        !enabled -> primary.copy(alpha = 0.35f)
        filled -> primary
        else -> Color.Transparent
    }
    val fg = when {
        filled -> Color.White
        danger -> ColorDanger
        else -> primary
    }
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background(bg)
            .then(if (filled) Modifier else Modifier.border(1.dp, fg.copy(alpha = 0.55f), shape))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
    }
}

/** 状态小圆点 + 文案。 */
@Composable
private fun PackStatusLine(text: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (ok) ColorGreen else BocchiTextGray.copy(alpha = 0.5f)),
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = text,
            fontSize = 12.5.sp,
            color = if (ok) ColorGreen else BocchiTextGray,
        )
    }
}

/**
 * 功能包：本体只带核心代理，重型功能按需下载。
 * 下完立刻能用，删掉立刻腾空间（包目录整体移除）。
 * 卡片布局对齐设置主页：圆角图标贴片 + 标题/状态 + 大按钮，间距拉开。
 */
@Composable
fun FeaturePacksScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val store = remember { PackStore(context) }
    val mmkv: MMKV = koinInject(named("settings"))
    val scope = rememberCoroutineScope()
    var refreshTick by remember { mutableStateOf(0) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var progressText by remember { mutableStateOf("") }
    // 模块页关掉显示开关的包不在这里出现（去模块页或已隐藏的模块页恢复）。
    val packHidden = remember(refreshTick) { loadHiddenSections(mmkv, PACKS_VISIBILITY_SCOPE) }
    val visiblePacks = PackStore.REGISTRY.filter { it.id !in packHidden }

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
                title = "插件",
                scrollBehavior = scrollBehavior,
                navigationIcon = { NavigationBackIcon(navigator = navigator) },
            )
        },
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                        text = "暂无可用插件。",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }
            if (visiblePacks.isEmpty() && PackStore.REGISTRY.isNotEmpty()) {
                item {
                    Text(
                        text = "插件都已隐藏，去「模块」页把开关打开就能恢复。",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }
            items(visiblePacks.size, key = { visiblePacks[it].id }) { index ->
                val def = visiblePacks[index]
                @Suppress("UNUSED_EXPRESSION") refreshTick
                val installed = remember(refreshTick, def.id) { store.isInstalled(def) }
                val installedBytes = remember(refreshTick, def.id) { store.installedSize(def.id) }
                val hasOldVersion = !installed && installedBytes > 0L
                val busy = busyId == def.id
                BocchiCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.13f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(text = packIcon(def.id), fontSize = 23.sp)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = def.name,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BocchiTextDark,
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                when {
                                    busy -> PackStatusLine(progressText.ifBlank { "处理中…" }, ok = false)
                                    installed -> PackStatusLine("已安装", ok = true)
                                    hasOldVersion -> PackStatusLine("可更新", ok = false)
                                    else -> PackStatusLine("未安装", ok = false)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(BocchiTextGray.copy(alpha = 0.12f))
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "v${def.version}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = BocchiTextGray,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = def.summary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = BocchiTextGray,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (installed || hasOldVersion) "占用 ${formatSize(installedBytes)}" else "下载 ${def.sizeText}",
                                fontSize = 12.5.sp,
                                color = BocchiTextGray,
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (installed) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PackActionButton(
                                    text = "打开",
                                    filled = true,
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f),
                                ) { openPack(def) }
                                PackActionButton(
                                    text = "删除",
                                    filled = false,
                                    danger = true,
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    store.uninstall(def.id)
                                    refreshTick++
                                    Toast.makeText(context, "已删除 ${def.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            PackActionButton(
                                text = when {
                                    busy -> progressText.ifBlank { "下载中…" }
                                    hasOldVersion -> "更新到 v${def.version}"
                                    else -> "下载"
                                },
                                filled = true,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
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
                            }
                        }
                    }
                }
            }
        }
    }
}

private val ColorGreen = Color(0xFF43A047)
private val ColorDanger = Color(0xFFD9534F)
