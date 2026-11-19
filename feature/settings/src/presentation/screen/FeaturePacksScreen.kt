package com.suanran.dreambox.feature.settings.presentation.screen

import android.content.Intent
import android.util.Log
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suanran.dreambox.core.contract.OverrideApplier
import com.suanran.dreambox.core.contract.OverrideConfigRepository
import com.suanran.dreambox.core.contract.ProfileBindingReader
import com.suanran.dreambox.feature.settings.presentation.module.ModuleDef
import com.suanran.dreambox.feature.settings.presentation.module.ModuleOverrideBridge
import com.suanran.dreambox.feature.settings.presentation.module.ModuleStore
import com.suanran.dreambox.feature.settings.presentation.module.SeedModules
import com.suanran.dreambox.feature.settings.presentation.pack.PACKS_VISIBILITY_SCOPE
import com.suanran.dreambox.feature.settings.presentation.pack.PackDef
import com.suanran.dreambox.feature.settings.presentation.pack.PackStore
import com.suanran.dreambox.runtime.api.contract.ProfileRepositoryContract
import com.suanran.dreambox.presentation.component.sortable.loadHiddenSections
import com.tencent.mmkv.MMKV
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.misc.BocchiTextDark
import com.suanran.dreambox.presentation.component.misc.BocchiTextGray
import com.suanran.dreambox.presentation.component.misc.bocchiSummaryColor
import com.suanran.dreambox.presentation.component.misc.bocchiTitleColor
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.navigation.Navigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
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
    // ── 轻模块（Loon 式模块市场，v1）──
    // 聚合覆写桥：已启用模块的规则聚成一份覆写，照 QuickRules 的路子绑定 + 重应用。
    val moduleStore = remember { ModuleStore(context) }
    val moduleConfigRepo: OverrideConfigRepository = koinInject()
    val moduleApplier: OverrideApplier = koinInject()
    val moduleBindingReader: ProfileBindingReader = koinInject()
    val moduleProfilesRepo: ProfileRepositoryContract = koinInject()
    val moduleBridge = remember {
        ModuleOverrideBridge(
            configRepo = moduleConfigRepo,
            bindingReader = moduleBindingReader,
            applier = moduleApplier,
            queryActiveProfile = { moduleProfilesRepo.queryActiveProfile() },
        )
    }
    var marketModules by remember { mutableStateOf(SeedModules.ALL) }
    var marketFromRemote by remember { mutableStateOf(false) }
    var moduleUrlText by remember { mutableStateOf("") }
    var busyModuleId by remember { mutableStateOf<String?>(null) }
    // 开关乐观状态：点下立刻翻开关，后台再写盘+重算规则；写完用磁盘真实值对账。
    // 之前开关完全等后台协程跑完才翻，桥里任何一步卡住都会让开关看起来“点不动”。
    var moduleEnabledOverride by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    // 最后点到的那个模块，下面详情卡就讲它。
    var selectedModuleId by remember { mutableStateOf<String?>(null) }
    // 1.200 分页签：0=模块市场，1=功能包。两区不再叠在一页里滑到底，默认停模块市场。
    var selectedTab by remember { mutableStateOf(0) }
    // 每次刷新都重拉一次远程索引（拉不到就回内置种子，不报错）。
    LaunchedEffect(refreshTick) {
        val (list, fromRemote) = moduleStore.fetchMarket()
        marketModules = list
        marketFromRemote = fromRemote
    }
    val installedModules = remember(refreshTick) { moduleStore.listInstalled() }
    // 市场里没有、但已从 URL 装上的模块也要显示出来。
    val displayModuleDefs = remember(marketModules, installedModules) {
        val marketIds = marketModules.map { it.id }.toSet()
        marketModules + installedModules.filter { it.def.id !in marketIds }.map { it.def }
    }

    /** 按已装模块的开关状态重算聚合覆写并重应用。带 15 秒上限：桥里任何一步卡住都不能把开关钉死在“处理中”。 */
    suspend fun syncModules() {
        val ok = withTimeoutOrNull(15_000) {
            val enabled = withContext(Dispatchers.IO) { moduleStore.listInstalled() }
                .filter { it.enabled }.map { it.def }
            moduleBridge.syncEnabled(enabled)
            true
        }
        if (ok != true) {
            Toast.makeText(context, "规则应用超时，开关已保存，拨动一次会重新应用", Toast.LENGTH_LONG).show()
        }
    }

    /** 模块行/详情卡共用的三个动作：装、拨、删。拨动的逻辑与 1.195 完全一致（同步写盘 + 乐观翻转）。 */
    fun installModule(def: ModuleDef) {
        busyModuleId = def.id
        scope.launch {
            try {
                moduleStore.install(def)
                syncModules()
                Toast.makeText(context, "${def.name} 已安装并启用", Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Toast.makeText(context, "安装失败：${t.message}", Toast.LENGTH_LONG).show()
            } finally {
                busyModuleId = null
                refreshTick++
            }
        }
    }
    fun toggleModule(def: ModuleDef, on: Boolean, enabledBefore: Boolean) {
        Log.d("ModuleToggle", "onToggle called id=${def.id} on=$on enabledBefore=$enabledBefore")
        Toast.makeText(context, "已点：${def.name} -> ${if (on) "启用" else "关闭"}", Toast.LENGTH_SHORT).show()
        runCatching { moduleStore.setEnabled(def.id, on) }
            .onFailure { Toast.makeText(context, "写盘失败：${it.message}", Toast.LENGTH_LONG).show() }
        moduleEnabledOverride = moduleEnabledOverride + (def.id to on)
        busyModuleId = def.id
        scope.launch {
            try {
                syncModules()
                Log.d("ModuleToggle", "syncModules done id=${def.id} on=$on")
            } catch (t: Throwable) {
                Log.d("ModuleToggle", "toggle failed id=${def.id}: ${t.message}")
                Toast.makeText(context, "切换失败：${t.message}", Toast.LENGTH_LONG).show()
            } finally {
                busyModuleId = null
                moduleEnabledOverride = moduleEnabledOverride - def.id
                refreshTick++
                Log.d("ModuleToggle", "toggle finally id=${def.id} refreshTick=$refreshTick")
            }
        }
    }
    fun deleteModule(def: ModuleDef) {
        busyModuleId = def.id
        scope.launch {
            try {
                moduleStore.uninstall(def.id)
                syncModules()
                Toast.makeText(context, "已删除 ${def.name}", Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Toast.makeText(context, "删除失败：${t.message}", Toast.LENGTH_LONG).show()
            } finally {
                busyModuleId = null
                refreshTick++
            }
        }
    }
    fun updateModule(def: ModuleDef) {
        busyModuleId = def.id
        scope.launch {
            try {
                moduleStore.update(def)
                syncModules()
                Toast.makeText(context, "${def.name} 已更新到 v${def.version}", Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Toast.makeText(context, "更新失败：${t.message}", Toast.LENGTH_LONG).show()
            } finally {
                busyModuleId = null
                refreshTick++
            }
        }
    }
    // 模块页关掉显示开关的包不在这里出现（去模块页或已隐藏的模块页恢复）。
    val packHidden = remember(refreshTick) { loadHiddenSections(mmkv, PACKS_VISIBILITY_SCOPE) }
    val visiblePacks = PackStore.REGISTRY.filter { it.id !in packHidden }.distinctBy { it.id }

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
                actions = { com.suanran.dreambox.presentation.component.help.ModuleHelpAction("packs") },
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
            // 1.200 分页签：点一下直达，不用滑过另一区。复用 PackActionButton 当页签，选中实心、未选描边。
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    PackActionButton(
                        text = "模块市场 ${displayModuleDefs.size}",
                        filled = selectedTab == 0,
                        modifier = Modifier.weight(1f),
                    ) { selectedTab = 0 }
                    PackActionButton(
                        text = "功能包 ${visiblePacks.size}",
                        filled = selectedTab == 1,
                        modifier = Modifier.weight(1f),
                    ) { selectedTab = 1 }
                }
            }
            // ── 模块市场（Loon 式轻模块）：纯规则包，开关即生效 ──
            if (selectedTab == 0) {
            item {
                Text(
                    text = "模块市场",
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                Text(
                    text = if (marketFromRemote) "轻模块是纯规则包：一键安装，开关当场生效，删掉就干净移除。"
                    else "轻模块是纯规则包：一键安装，开关当场生效，删掉就干净移除。（远程索引暂不可达，当前显示内置种子）",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        TextField(
                            value = moduleUrlText,
                            onValueChange = { moduleUrlText = it },
                            label = "粘贴 module.json 链接安装…",
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PackActionButton(
                            text = if (busyModuleId == "__url__") "安装中…" else "从链接安装",
                            filled = true,
                            enabled = busyModuleId == null && moduleUrlText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val url = moduleUrlText.trim()
                            busyModuleId = "__url__"
                            scope.launch {
                                try {
                                    val def = moduleStore.installFromUrl(url)
                                    syncModules()
                                    moduleUrlText = ""
                                    Toast.makeText(context, "${def.name} 已安装并启用", Toast.LENGTH_SHORT).show()
                                } catch (t: Throwable) {
                                    Toast.makeText(context, "安装失败：${t.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    busyModuleId = null
                                    refreshTick++
                                }
                            }
                        }
                    }
                }
            }
            // ── 简单列表（1.202）：仓鼠仪表盘已删，一行一卡，点行即装/拨开关。──
            item {
                val enabledCount = displayModuleDefs.count { def ->
                    moduleEnabledOverride[def.id]
                        ?: (installedModules.find { it.def.id == def.id }?.enabled == true)
                }
                Text(
                    text = "共 ${displayModuleDefs.size} 个模块 · 已启用 $enabledCount 个 · 规则 ${displayModuleDefs.sumOf { it.rules.size }} 条",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            items(displayModuleDefs.size, key = { displayModuleDefs[it].id }) { index ->
                val def = displayModuleDefs[index]
                val installed = installedModules.any { it.def.id == def.id }
                val enabled = (moduleEnabledOverride[def.id]
                    ?: (installedModules.find { it.def.id == def.id }?.enabled == true)) && installed
                val busy = busyModuleId == def.id
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    ModuleSimpleRow(
                        def = def,
                        enabled = enabled,
                        installed = installed,
                        busy = busy,
                        onTap = {
                            selectedModuleId = def.id
                            if (installed) toggleModule(def, !enabled, enabled) else installModule(def)
                        },
                    )
                }
            }
            // ── 选中模块的详情卡（玻璃卡）──
            item {
                val selectedDef = displayModuleDefs.find { it.id == selectedModuleId }
                    ?: displayModuleDefs.firstOrNull()
                if (selectedDef != null) {
                    val installed = installedModules.find { it.def.id == selectedDef.id }
                    val enabled = moduleEnabledOverride[selectedDef.id]
                        ?: (installed?.enabled == true)
                    val busy = busyModuleId == selectedDef.id
                    val hasUpdate = installed != null && installed.def.version != selectedDef.version
                    ModuleDetailCard(
                        def = selectedDef,
                        installed = installed != null,
                        enabled = enabled,
                        hasUpdate = hasUpdate,
                        busy = busy,
                        onInstall = { installModule(selectedDef) },
                        onUpdate = { updateModule(selectedDef) },
                        onToggle = { toggleModule(selectedDef, !enabled, enabled) },
                        onDelete = { deleteModule(selectedDef) },
                    )
                }
            }
            } // selectedTab == 0：模块市场
            // ── 功能包（原有重型功能包，原样保留）──
            if (selectedTab == 1) {
            item {
                Text(
                    text = "功能包",
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
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
                GlassCard(modifier = Modifier.fillMaxWidth()) {
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
            } // selectedTab == 1：功能包
        }
    }
}

/** 玻璃磁吸悬浮卡（1.202，插件页专用）：半透明卡面 + 20dp 圆角 + 细亮边 + 悬浮阴影。
 * 卡与卡之间靠列表 16dp 间距隔开，不连成一长条；只在插件页内用，不动全 App 主题。 */
@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .shadow(8.dp, shape)
            .clip(shape)
            .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape),
    ) {
        content()
    }
}

private val ModuleSwitchGold = Color(0xFFF2C46B)
private val ModulePink = Color(0xFFF48FB1)

/** 开关只画样子，不接点按（1.190 教训：父子双 clickable 嵌套会丢点按；整行点按只走 Row 的 onTap）。 */
@Composable
private fun ModuleSwitchVisual(on: Boolean) {
    Box(
        modifier = Modifier
            .size(40.dp, 24.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                brush = if (on) Brush.horizontalGradient(listOf(ModuleSwitchGold, ModulePink))
                else SolidColor(Color(0xFF82808C).copy(alpha = 0.45f))
            ),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .offset(x = if (on) 19.dp else 3.dp, y = 3.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** 模块简单行（1.202）：名字 + 规则数/状态 + 右边开关，无分类、无仓鼠、无动画。 */
@Composable
private fun ModuleSimpleRow(
    def: ModuleDef,
    enabled: Boolean,
    installed: Boolean,
    busy: Boolean,
    onTap: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy, onClick = onTap)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = def.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = bocchiTitleColor(),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    busy -> "处理中…"
                    enabled -> "已启用 · 规则 ${def.rules.size} 条"
                    installed -> "未启用 · 规则 ${def.rules.size} 条"
                    else -> "未安装 · 规则 ${def.rules.size} 条"
                },
                fontSize = 13.sp,
                color = if (enabled) ColorGreen else bocchiSummaryColor(),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        ModuleSwitchVisual(on = enabled)
    }
}

@Composable
private fun ModuleDetailCard(
    def: ModuleDef,
    installed: Boolean,
    enabled: Boolean,
    hasUpdate: Boolean,
    busy: Boolean,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "🧩", fontSize = 23.sp)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = def.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = bocchiTitleColor(),
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    when {
                        busy -> PackStatusLine("处理中…", ok = false)
                        installed && enabled -> PackStatusLine("已安装 · 已启用", ok = true)
                        installed -> PackStatusLine("已安装 · 未启用", ok = false)
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
                text = def.desc,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = bocchiSummaryColor(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${def.category} · 规则 ${def.rules.size} 条 · ${def.author}",
                fontSize = 12.5.sp,
                color = bocchiSummaryColor(),
            )
            if (installed) {
                Spacer(modifier = Modifier.height(12.dp))
                // 开关只用 PackActionButton（Box + clickable），这是卡片里唯一一直能点的按钮。
                PackActionButton(
                    text = if (enabled) "已启用，点击关闭" else "启用",
                    filled = !enabled,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { onToggle() }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (hasUpdate) {
                        PackActionButton(
                            text = "更新到 v${def.version}",
                            filled = true,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        ) { onUpdate() }
                    }
                    PackActionButton(
                        text = "删除",
                        filled = false,
                        danger = true,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) { onDelete() }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                PackActionButton(
                    text = if (busy) "安装中…" else "安装",
                    filled = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { onInstall() }
            }
        }
    }
}


private val ColorGreen = Color(0xFF43A047)
private val ColorDanger = Color(0xFFD9534F)

