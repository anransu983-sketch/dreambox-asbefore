package com.suanran.dreambox.feature.settings.presentation.screen

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Image
import androidx.compose.ui.composed
import androidx.compose.ui.res.painterResource
import com.suanran.dreambox.feature.settings.R
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
import com.suanran.dreambox.presentation.component.misc.BocchiCard
import com.suanran.dreambox.presentation.component.misc.BocchiTextDark
import com.suanran.dreambox.presentation.component.misc.BocchiTextGray
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
    // 最后点到的那个模块，下面磨砂详情卡就讲它。
    var selectedModuleId by remember { mutableStateOf<String?>(null) }
    // 仪表盘顶部的圆形分类（全部 / 去广告 / 隐私 / 安全），点一个只看那类灯。
    var selectedCategory by remember { mutableStateOf("全部") }
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
                BocchiCard(modifier = Modifier.fillMaxWidth()) {
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
            // ── 仓鼠仪表盘：顶部圆形分类签 + 磨砂 hero 卡 + 便当格 + 模块大卡行（参考琳记录页的布局）。──
            item {
                val enabledCount = displayModuleDefs.count { def ->
                    moduleEnabledOverride[def.id]
                        ?: (installedModules.find { it.def.id == def.id }?.enabled == true)
                }
                ModuleDashboard(
                    defs = displayModuleDefs,
                    installedIds = installedModules.map { it.def.id }.toSet(),
                    isEnabled = { def ->
                        moduleEnabledOverride[def.id]
                            ?: (installedModules.find { it.def.id == def.id }?.enabled == true)
                    },
                    isBusy = { def -> busyModuleId == def.id },
                    enabledCount = enabledCount,
                    selectedCategory = selectedCategory,
                    onCategorySelect = { selectedCategory = it },
                    onModuleTap = { def, enabled ->
                        selectedModuleId = def.id
                        if (installedModules.any { it.def.id == def.id }) {
                            toggleModule(def, !enabled, enabled)
                        } else {
                            installModule(def)
                        }
                    },
                )
            }
            // ── 选中那盏灯的磨砂详情卡 ──
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
            } // selectedTab == 1：功能包
        }
    }
}

/** 仓鼠版模块市场：骨架照琳记录页（圆形分类签 + Hero + 便当格），行语言照白月光 medalRow，玻璃照琳 StatsGlassCard。 */
private val LanternGold = Color(0xFFF2C46B)
private val LanternGoldDeep = Color(0xFFE8A33D)
private val LanternNight = Color(0xFF0B1636)
private val LanternCard = Color(0xFF141B3D)
private val LanternLabelCore = Color(0xFFEAF3FF)
private val LanternLabelGlow = Color(0xFF8FC8FF)
private val LanternOk = Color(0xFFA6EBCB)
private val LanternOff = Color(0xFFB9C3D9)
private val LanternCyan = Color(0xFF7FE3FF)
private val ModuleGlass = Color(0xFF141B3D)
private val ModulePink = Color(0xFFF48FB1)

/** 模块配哪只仓鼠（按 id 固定，换 id 也不串）。URL 装的陌生模块默认 ham_12。 */
private fun moduleHamster(def: ModuleDef): Int = when (def.id) {
    "adblock" -> R.drawable.ham_01
    "tracking" -> R.drawable.ham_03
    "splash" -> R.drawable.ham_04
    "videoads" -> R.drawable.ham_06
    "antimalware" -> R.drawable.ham_07
    "privacyplus" -> R.drawable.ham_08
    "shopping" -> R.drawable.ham_10
    else -> R.drawable.ham_12
}

/** 分类签配哪只仓鼠。 */
private fun categoryHamster(cat: String): Int = when (cat) {
    "全部" -> R.drawable.ham_01
    "去广告" -> R.drawable.ham_03
    "隐私" -> R.drawable.ham_05
    "安全" -> R.drawable.ham_09
    else -> R.drawable.ham_12
}

/** 仓鼠图标（照琳 HamsterIcon 原样抄）：待机呼吸浮动，周期 1700 + (index % 4) * 200 ms，相位按序号错开，上下 ±2dp；点按压到 0.86 再弹簧回弹。 */
@Composable
private fun HamsterIcon(
    resId: Int,
    index: Int,
    pressed: Boolean = false,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "hamsterBreath")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1700 + (index % 4) * 200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val bobDp = sin(phase + index * 1.37f).toFloat() * 2f
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        modifier = modifier
            .size(iconSize)
            .graphicsLayer {
                translationY = bobDp * density
                scaleX = pressScale
                scaleY = pressScale
            },
    )
}

/** 依次入场（照琳 Modifier.entrance）：按序号 delay index * 45ms，上限 12 项，alpha 0→1、translationY 22→0，380ms。 */
private fun Modifier.moduleEntrance(index: Int): Modifier = composed {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val delay = index.coerceAtMost(11) * 45
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(380, delayMillis = delay),
        label = "entranceAlpha",
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 22f,
        animationSpec = tween(380, delayMillis = delay),
        label = "entranceY",
    )
    this.graphicsLayer {
        this.alpha = alpha
        translationY = offsetY
    }
}

/** 开关只画样子，不接点按（1.190 教训：父子双 clickable 嵌套会丢点按；整行点按只走 Row 的 onToggle）。 */
@Composable
private fun ModuleSwitchVisual(on: Boolean) {
    Box(
        modifier = Modifier
            .size(40.dp, 24.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                brush = if (on) Brush.horizontalGradient(listOf(LanternGold, ModulePink))
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

/** 模块行（照白月光 medalRow）：48dp 仓鼠 + 标题 16sp + 副标题 13sp + 右边开关。关掉整行（仓鼠 + 文字）变淡 0.55，开关本身不动。 */
@Composable
private fun ModuleMedalRow(
    def: ModuleDef,
    index: Int,
    enabled: Boolean,
    installed: Boolean,
    busy: Boolean,
    onTap: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 已装但未启用才变淡；未安装的保持全亮（等用户来装）。
    val dim = installed && !enabled
    val dimAlpha = if (dim) 0.55f else 1f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .moduleEntrance(index)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !busy,
                onClick = onTap,
            )
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        HamsterIcon(
            resId = moduleHamster(def),
            index = index,
            pressed = pressed,
            iconSize = 48.dp,
            modifier = Modifier.graphicsLayer { alpha = dimAlpha },
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { alpha = dimAlpha },
        ) {
            Text(
                text = def.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Serif,
                color = LanternLabelCore,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    busy -> "处理中…"
                    enabled -> "已点亮 · 规则 ${def.rules.size} 条"
                    installed -> "未点亮 · 规则 ${def.rules.size} 条"
                    else -> "未安装 · 规则 ${def.rules.size} 条"
                },
                fontSize = 13.sp,
                color = if (enabled) LanternOk else LanternOff,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        ModuleSwitchVisual(on = enabled)
    }
}

/** 便当格（照琳记录页双列便当格）：左竖版卡放规则总数，右两小卡放已启用与已安装。数字全是真实计数，不编。 */
@Composable
private fun ModuleBentoRow(
    totalRules: Int,
    enabledCount: Int,
    total: Int,
    installedCount: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(18.dp))
                .background(ModuleGlass.copy(alpha = 0.44f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                .padding(14.dp),
        ) {
            HamsterIcon(
                resId = R.drawable.ham_12,
                index = 0,
                iconSize = 40.dp,
                modifier = Modifier.align(Alignment.TopEnd),
            )
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(
                    text = "规则总数",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ModulePink,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$totalRules 条",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = LanternLabelCore,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$total 个模块 · $enabledCount 个已启用",
                    fontSize = 11.sp,
                    color = LanternLabelCore.copy(alpha = 0.75f),
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(ModuleGlass.copy(alpha = 0.44f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column {
                    Text(
                        text = "已启用",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ModulePink,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$enabledCount / $total",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = LanternLabelCore,
                    )
                    Text(
                        text = "个模块已点亮",
                        fontSize = 11.sp,
                        color = LanternLabelCore.copy(alpha = 0.55f),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(ModuleGlass.copy(alpha = 0.44f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column {
                    Text(
                        text = "已安装",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ModulePink,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$installedCount 个",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = LanternLabelCore,
                    )
                    Text(
                        text = "个模块已装上",
                        fontSize = 11.sp,
                        color = LanternLabelCore.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleDashboard(
    defs: List<ModuleDef>,
    installedIds: Set<String>,
    isEnabled: (ModuleDef) -> Boolean,
    isBusy: (ModuleDef) -> Boolean,
    enabledCount: Int,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    onModuleTap: (ModuleDef, Boolean) -> Unit,
) {
    val categories = listOf("全部", "去广告", "隐私", "安全")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF0D1B40),
                    0.55f to Color(0xFF0B1636),
                    1f to Color(0xFF050B1F),
                )
            ),
    ) {
        // 星星 + 月晕 + 淡云（现画夜空）。
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            LanternStars.forEach { star ->
                val (fx, fy, size, alpha) = star
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (fx * w.toPx()).roundToInt(),
                                (fy * h.toPx()).roundToInt(),
                            )
                        }
                        .size(size.dp)
                        .background(Color.White.copy(alpha = alpha), CircleShape),
                )
            }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (0.70f * w.toPx()).roundToInt(),
                            (0.02f * h.toPx()).roundToInt(),
                        )
                    }
                    .size(120.dp)
                    .background(
                        Brush.radialGradient(
                            0f to Color(0xFFBFE6FF).copy(alpha = 0.30f),
                            1f to Color.Transparent,
                        ),
                        CircleShape,
                    ),
            )
            LanternClouds.forEach { (fx, fy, cw, ch, alpha) ->
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (fx * w.toPx()).roundToInt(),
                                (fy * h.toPx()).roundToInt(),
                            )
                        }
                        .size(cw.dp, ch.dp)
                        .background(
                            Brush.radialGradient(
                                0f to Color(0xFF8FC8FF).copy(alpha = alpha),
                                1f to Color.Transparent,
                            ),
                            CircleShape,
                        ),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // 圆形分类签（照琳 RecordsTabBar：52dp 圆，圆里是仓鼠图，选中放大 1.07 + 金边，label 在圆下）。
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                categories.forEachIndexed { catIndex, cat ->
                    val selected = cat == selectedCategory
                    val tabScale by animateFloatAsState(
                        targetValue = if (selected) 1.07f else 1f,
                        animationSpec = tween(200),
                        label = "tabScale",
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(52.dp)
                                .graphicsLayer {
                                    scaleX = tabScale
                                    scaleY = tabScale
                                }
                                .clip(CircleShape)
                                .background(
                                    if (selected) LanternGold.copy(alpha = 0.20f)
                                    else LanternCard.copy(alpha = 0.7f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) Color(0xBFFFC79A)
                                    else LanternOff.copy(alpha = 0.4f),
                                    shape = CircleShape,
                                )
                                .clickable { onCategorySelect(cat) },
                        ) {
                            HamsterIcon(
                                resId = categoryHamster(cat),
                                index = catIndex,
                                iconSize = 40.dp,
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = cat,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) Color(0xFFFFC79A) else LanternOff,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Hero 卡：已点亮几盏（照琳 StatsGlassCard 的玻璃配方）。
            ModuleHeroCard(enabledCount = enabledCount, total = defs.size)
            Spacer(modifier = Modifier.height(12.dp))
            // 便当格：规则总数 + 已启用 + 已安装。
            val totalRules = defs.sumOf { it.rules.size }
            val installedCount = defs.count { it.id in installedIds }
            ModuleBentoRow(
                totalRules = totalRules,
                enabledCount = enabledCount,
                total = defs.size,
                installedCount = installedCount,
            )
            Spacer(modifier = Modifier.height(12.dp))
            // 模块列表大卡（照白月光：一张 28dp 大卡包住全部行）。
            val filtered = if (selectedCategory == "全部") defs
            else defs.filter { it.category == selectedCategory }
            val filteredEnabledCount = filtered.count { isEnabled(it) && it.id in installedIds }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(ModuleGlass.copy(alpha = 0.44f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
                    .padding(vertical = 6.dp),
            ) {
                Column {
                    Text(
                        text = "共 ${filtered.size} 个模块 · 已启用 $filteredEnabledCount 个",
                        fontSize = 12.sp,
                        color = LanternOff,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                    filtered.forEachIndexed { rowIndex, def ->
                        val enabled = isEnabled(def) && (def.id in installedIds)
                        val busy = isBusy(def)
                        ModuleMedalRow(
                            def = def,
                            index = rowIndex,
                            enabled = enabled,
                            installed = def.id in installedIds,
                            busy = busy,
                            onTap = { onModuleTap(def, enabled) },
                        )
                        if (rowIndex < filtered.lastIndex) {
                            // 行间发丝线（照白月光：左缩进，不顶满宽）。
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 80.dp, end = 18.dp)
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.08f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 夜空里的星星（宽/高分数、直径 dp、透明度），位置固定，画出来就长这样。 */
private val LanternStars = listOf(
    LanternStar(0.06f, 0.05f, 2.5f, 0.9f),
    LanternStar(0.18f, 0.12f, 2f, 0.6f),
    LanternStar(0.30f, 0.04f, 3f, 0.9f),
    LanternStar(0.44f, 0.10f, 2f, 0.5f),
    LanternStar(0.55f, 0.05f, 2.5f, 0.8f),
    LanternStar(0.82f, 0.10f, 2f, 0.6f),
    LanternStar(0.92f, 0.18f, 3f, 0.9f),
    LanternStar(0.10f, 0.28f, 2f, 0.5f),
    LanternStar(0.24f, 0.34f, 2.5f, 0.7f),
    LanternStar(0.58f, 0.26f, 2f, 0.5f),
    LanternStar(0.88f, 0.32f, 2.5f, 0.7f),
    LanternStar(0.05f, 0.48f, 2f, 0.6f),
    LanternStar(0.36f, 0.44f, 2f, 0.5f),
    LanternStar(0.50f, 0.56f, 2.5f, 0.7f),
    LanternStar(0.90f, 0.55f, 2f, 0.5f),
    LanternStar(0.14f, 0.66f, 2.5f, 0.7f),
    LanternStar(0.48f, 0.78f, 2f, 0.5f),
    LanternStar(0.76f, 0.82f, 2f, 0.6f),
    LanternStar(0.22f, 0.88f, 2f, 0.5f),
    LanternStar(0.60f, 0.92f, 2.5f, 0.7f),
)

private data class LanternStar(val x: Float, val y: Float, val size: Float, val alpha: Float)

/** 夜空里的淡云（宽/高分数、宽 dp、高 dp、透明度）。 */
private val LanternClouds = listOf(
    LanternCloud(0.02f, 0.20f, 150f, 60f, 0.10f),
    LanternCloud(0.55f, 0.38f, 170f, 70f, 0.08f),
    LanternCloud(0.10f, 0.60f, 160f, 64f, 0.09f),
    LanternCloud(0.48f, 0.80f, 180f, 70f, 0.08f),
)

private data class LanternCloud(val x: Float, val y: Float, val w: Float, val h: Float, val alpha: Float)

/** Hero 卡：已点亮几盏 + 进度条（照琳 StatsGlassCard 的玻璃配方：18dp 圆角 + 深色 0.44 + 1dp 白 0.15 边）。 */
@Composable
private fun ModuleHeroCard(enabledCount: Int, total: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ModuleGlass.copy(alpha = 0.44f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
            .padding(20.dp),
    ) {
        Column {
            Text(
                text = "已点亮",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = ModulePink,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$enabledCount",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = LanternGold,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = Shadow(color = LanternGold, blurRadius = 16f),
                    ),
                )
                Text(
                    text = " / $total 盏灯",
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Serif,
                    color = LanternLabelCore,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(LanternOff.copy(alpha = 0.2f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (total > 0) enabledCount.toFloat() / total else 0f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(LanternGoldDeep, LanternGold)
                            )
                        ),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "点一盏灯，亮起一个模块",
                fontSize = 12.5.sp,
                color = LanternOff,
            )
        }
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ModuleGlass.copy(alpha = 0.44f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
            .padding(20.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HamsterIcon(
                    resId = moduleHamster(def),
                    index = 0,
                    iconSize = 48.dp,
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = def.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Serif,
                        color = LanternLabelCore,
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
                        .background(LanternOff.copy(alpha = 0.12f))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "v${def.version}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = LanternOff,
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = def.desc,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = LanternOff,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${def.category} · 规则 ${def.rules.size} 条 · ${def.author}",
                fontSize = 12.5.sp,
                color = LanternOff,
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
