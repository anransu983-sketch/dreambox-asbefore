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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.math.sin
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
    // 灯笼夜空里最后点到的那盏灯，下面磨砂详情卡就讲它。
    var selectedModuleId by remember { mutableStateOf<String?>(null) }
    // 仪表盘顶部的圆形分类（全部 / 去广告 / 隐私 / 安全），点一个只看那类灯。
    var selectedCategory by remember { mutableStateOf("全部") }
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

    /** 灯笼/详情卡共用的三个动作：装、拨、删。拨灯的逻辑与 1.195 完全一致（同步写盘 + 乐观翻转）。 */
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
            // ── 模块市场（Loon 式轻模块）：纯规则包，开关即生效 ──
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
            // ── 灯笼仪表盘：顶部圆形分类 + 磨砂 hero 卡 + 双列网格灯卡（参考琳记录页的布局）。──
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
            // ── 功能包（原有重型功能包，原样保留）──
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
        }
    }
}

/** 轻模块卡片：和功能包卡片同语言，开关在卡片里直接拨，拨完立刻重算规则。 */
@Composable
/** 灯笼夜空：模块是一盏盏浮在夜空里的灯，不是列表。点灯 = 启用/关闭。 */
private val LanternGold = Color(0xFFF2C46B)
private val LanternGoldDeep = Color(0xFFE8A33D)
private val LanternNight = Color(0xFF0B1636)
private val LanternCard = Color(0xFF141B3D)
private val LanternLabelCore = Color(0xFFEAF3FF)
private val LanternLabelGlow = Color(0xFF8FC8FF)
private val LanternOk = Color(0xFFA6EBCB)
private val LanternOff = Color(0xFFB9C3D9)
private val LanternCyan = Color(0xFF7FE3FF)

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
            // 圆形分类。
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                categories.forEach { cat ->
                    val selected = cat == selectedCategory
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) LanternGold.copy(alpha = 0.25f)
                                    else LanternCard.copy(alpha = 0.7f)
                                )
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) LanternGold
                                    else LanternOff.copy(alpha = 0.4f),
                                    shape = CircleShape,
                                )
                                .clickable { onCategorySelect(cat) },
                        ) {
                            Text(
                                text = cat.first().toString(),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Serif,
                                color = if (selected) LanternGold else LanternLabelCore,
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = cat,
                            fontSize = 12.sp,
                            color = if (selected) LanternGold else LanternOff,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Hero 卡：已点亮几盏。
            ModuleHeroCard(enabledCount = enabledCount, total = defs.size)
            Spacer(modifier = Modifier.height(12.dp))
            // 双列网格灯卡。
            val filtered = if (selectedCategory == "全部") defs
            else defs.filter { it.category == selectedCategory }
            filtered.chunked(2).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    row.forEach { def ->
                        val enabled = isEnabled(def) && (def.id in installedIds)
                        val busy = isBusy(def)
                        ModuleGridCard(
                            def = def,
                            enabled = enabled,
                            installed = def.id in installedIds,
                            busy = busy,
                            phase = defs.indexOf(def) * 0.9f,
                            modifier = Modifier.weight(1f),
                            onTap = { onModuleTap(def, enabled) },
                        )
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
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

/** Hero 卡：已点亮几盏 + 进度条。 */
@Composable
private fun ModuleHeroCard(enabledCount: Int, total: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LanternCard.copy(alpha = 0.75f))
            .border(1.dp, LanternCyan.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(20.dp),
    ) {
        Column {
            Text(
                text = "已点亮",
                fontSize = 13.sp,
                color = LanternLabelGlow,
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

/** 网格灯卡：磨砂卡 + 灯芯（浮动 + 金光呼吸）+ 名字 + 状态 + 规则数，点卡即开关。 */
@Composable
private fun ModuleGridCard(
    def: ModuleDef,
    enabled: Boolean,
    installed: Boolean,
    busy: Boolean,
    phase: Float,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    // 灯芯上下浮动：每张卡相位错开。
    val bob = rememberInfiniteTransition(label = "orb-bob-${def.id}")
    val bobT by bob.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(3600, easing = LinearEasing)),
        label = "bob",
    )
    val bobY = (sin(bobT + phase) * 4).dp
    // 点亮时金光呼吸。
    val glow = rememberInfiniteTransition(label = "orb-glow-${def.id}")
    val glowT by glow.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(2600, easing = LinearEasing)),
        label = "glow",
    )
    val glowAlpha = if (enabled) 0.55f + 0.35f * sin(glowT + phase) else 0f
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(120),
        label = "press",
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(LanternCard.copy(alpha = 0.75f))
            .border(
                width = 1.dp,
                color = if (enabled) LanternGold.copy(alpha = 0.5f)
                else LanternCyan.copy(alpha = 0.25f),
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !busy,
                onClick = onTap,
            )
            .padding(16.dp),
    ) {
        Column {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .offset(y = bobY)
                    .background(
                        brush = if (enabled) {
                            Brush.radialGradient(
                                0f to LanternGold,
                                0.55f to LanternGoldDeep.copy(alpha = 0.85f),
                                1f to LanternGoldDeep.copy(alpha = 0.35f),
                            )
                        } else {
                            Brush.radialGradient(
                                0f to Color(0xFF3A4568),
                                1f to Color(0xFF232B47),
                            )
                        },
                        shape = CircleShape,
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (enabled) LanternGold.copy(alpha = 0.9f)
                        else LanternOff.copy(alpha = 0.35f),
                        shape = CircleShape,
                    ),
            ) {
                // 光晕（呼吸），比灯芯大一圈。
                if (enabled) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .graphicsLayer { alpha = glowAlpha }
                            .background(
                                brush = Brush.radialGradient(
                                    0f to LanternGold.copy(alpha = 0.55f),
                                    1f to Color.Transparent,
                                ),
                                shape = CircleShape,
                            ),
                    )
                }
                Text(
                    text = "🧩",
                    fontSize = 20.sp,
                    modifier = Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.55f },
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = def.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Serif,
                color = LanternLabelCore,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    busy -> "处理中…"
                    enabled -> "已点亮"
                    installed -> "未点亮"
                    else -> "未安装"
                },
                fontSize = 12.sp,
                color = if (enabled) LanternOk else LanternOff,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "规则 ${def.rules.size} 条",
                fontSize = 11.5.sp,
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
            .clip(RoundedCornerShape(20.dp))
            .background(LanternCard.copy(alpha = 0.82f))
            .border(1.dp, LanternCyan.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(20.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (enabled) LanternGold.copy(alpha = 0.22f)
                            else LanternCyan.copy(alpha = 0.13f)
                        ),
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
