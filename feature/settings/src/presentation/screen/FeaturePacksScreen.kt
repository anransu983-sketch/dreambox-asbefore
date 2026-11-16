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
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.panpf.sketch.rememberAsyncImagePainter
import com.github.panpf.sketch.request.ImageRequest
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
            // ── 灯笼夜空：模块不是列表里的一行，是一盏盏浮在夜空里的灯。点灯 = 启用/关闭。──
            item {
                val enabledCount = displayModuleDefs.count { def ->
                    moduleEnabledOverride[def.id]
                        ?: (installedModules.find { it.def.id == def.id }?.enabled == true)
                }
                ModuleLanternScene(
                    defs = displayModuleDefs,
                    installedIds = installedModules.map { it.def.id }.toSet(),
                    isEnabled = { def ->
                        moduleEnabledOverride[def.id]
                            ?: (installedModules.find { it.def.id == def.id }?.enabled == true)
                    },
                    isBusy = { def -> busyModuleId == def.id },
                    enabledCount = enabledCount,
                    onLanternTap = { def, enabled ->
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

/** 每盏灯在夜空里的位置（宽/高分数），按模块顺序取，超了就轮着用。 */
private val LanternSpots = listOf(
    0.14f to 0.10f,
    0.64f to 0.06f,
    0.40f to 0.30f,
    0.10f to 0.52f,
    0.70f to 0.48f,
    0.30f to 0.70f,
    0.64f to 0.72f,
)

@Composable
private fun ModuleLanternScene(
    defs: List<ModuleDef>,
    installedIds: Set<String>,
    isEnabled: (ModuleDef) -> Boolean,
    isBusy: (ModuleDef) -> Boolean,
    enabledCount: Int,
    onLanternTap: (ModuleDef, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val skyPainter = rememberAsyncImagePainter(
        request = ImageRequest(context, "file:///android_asset/sky_island_night.webp")
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(560.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(LanternNight),
    ) {
        Image(
            painter = skyPainter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // 压一层暗色，保证上面的光字读得清。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to LanternNight.copy(alpha = 0.45f),
                        0.5f to Color.Transparent,
                        1f to LanternNight.copy(alpha = 0.55f),
                    )
                ),
        )
        Text(
            text = "已点亮 $enabledCount / ${defs.size} 盏灯",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Serif,
            color = LanternLabelCore,
            style = androidx.compose.ui.text.TextStyle(
                shadow = Shadow(color = LanternLabelGlow, blurRadius = 18f),
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp),
        )
        Text(
            text = "点一盏灯，亮起一个模块",
            fontSize = 12.5.sp,
            color = LanternOff,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 52.dp),
        )
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            defs.forEachIndexed { index, def ->
                val spot = LanternSpots[index % LanternSpots.size]
                val enabled = isEnabled(def)
                val busy = isBusy(def)
                val installed = def.id in installedIds
                ModuleLantern(
                    def = def,
                    enabled = enabled && installed,
                    installed = installed,
                    busy = busy,
                    phase = index * 0.9f,
                    modifier = Modifier.offset {
                        IntOffset(
                            (spot.first * w.toPx()).roundToInt(),
                            (spot.second * h.toPx()).roundToInt(),
                        )
                    },
                    onTap = { onLanternTap(def, enabled && installed) },
                )
            }
        }
    }
}

@Composable
private fun ModuleLantern(
    def: ModuleDef,
    enabled: Boolean,
    installed: Boolean,
    busy: Boolean,
    phase: Float,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    // 上下浮动：每盏灯相位错开，像真的浮在夜空里。
    val bob = rememberInfiniteTransition(label = "lantern-bob-${def.id}")
    val bobT by bob.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(3600, easing = LinearEasing)),
        label = "bob",
    )
    val bobY = (sin(bobT + phase) * 6).dp
    // 点亮时金光呼吸。
    val glow = rememberInfiniteTransition(label = "lantern-glow-${def.id}")
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
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = tween(120),
        label = "press",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .offset(y = bobY)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !busy,
                onClick = onTap,
            ),
    ) {
        // 灯绳。
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(10.dp)
                .background(LanternOff.copy(alpha = 0.5f)),
        )
        // 灯体：亮是暖金光晕 + 金芯，灭是暗灰石。
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
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
                    color = if (enabled) LanternGold.copy(alpha = 0.9f) else LanternOff.copy(alpha = 0.35f),
                    shape = CircleShape,
                ),
        ) {
            // 光晕（呼吸），比灯体大一圈，浮在灯外。
            if (enabled) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
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
                fontSize = 26.sp,
                modifier = Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.55f },
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = def.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Serif,
            color = LanternLabelCore,
            style = androidx.compose.ui.text.TextStyle(
                shadow = Shadow(color = LanternLabelGlow, blurRadius = 12f),
            ),
        )
        Text(
            text = when {
                busy -> "处理中…"
                enabled -> "已点亮"
                installed -> "未点亮"
                else -> "未安装"
            },
            fontSize = 11.sp,
            color = if (enabled) LanternOk else LanternOff,
        )
    }
}

/** 选中那盏灯的磨砂详情卡：说明、规则数、开关、删除都在这。 */
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
