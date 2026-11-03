package com.suanran.dreambox.feature.settings.presentation.screen

import com.suanran.dreambox.presentation.icon.FlyCat
import com.suanran.dreambox.presentation.icon.flycat.Github
import com.suanran.dreambox.presentation.icon.flycat.GitMerge
import com.suanran.dreambox.presentation.icon.flycat.Meta
import com.suanran.dreambox.presentation.icon.flycat.ScrollText
import com.suanran.dreambox.presentation.icon.flycat.Settings2
import com.suanran.dreambox.presentation.icon.flycat.WifiCog
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DragHandle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.Switch
import com.suanran.dreambox.presentation.component.misc.rememberHomePanels
import com.suanran.dreambox.presentation.component.misc.HomePanels
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.misc.BocchiCard
import com.suanran.dreambox.presentation.component.misc.BocchiBlueBg
import com.suanran.dreambox.presentation.component.misc.BocchiBlueFg
import com.suanran.dreambox.presentation.component.misc.BocchiPinkBg
import com.suanran.dreambox.presentation.component.misc.BocchiStar
import com.suanran.dreambox.presentation.component.misc.BocchiPink
import com.suanran.dreambox.presentation.component.misc.BocchiSectionTitle
import com.suanran.dreambox.presentation.component.misc.BocchiTextDark
import com.suanran.dreambox.presentation.component.misc.BocchiTextGray
import com.suanran.dreambox.presentation.component.misc.CustomIcons
import com.suanran.dreambox.presentation.component.misc.rememberCustomIconBitmap
import com.suanran.dreambox.presentation.component.navigation.BottomBarDestination
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.navigation.Navigator
import com.suanran.dreambox.presentation.navigation.Route
import com.suanran.dreambox.feature.settings.presentation.screen.component.OneCardDivider
import com.suanran.dreambox.presentation.theme.AppTheme
import com.suanran.dreambox.presentation.util.toast
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 个性化（2026-11-03 参考琳）：应用图标预置、导航栏/模块/返回按钮自定义图片。 */
private data class AppIconVariant(
    val id: String,
    val name: String,
    val aliasSuffix: String,
    val swatch: Color,
)

private val APP_ICON_VARIANTS = listOf(
    AppIconVariant("default", "默认", ".DreamBoxLauncher", Color(0xFFF5EFE6)),
    AppIconVariant("pink", "粉樱", ".DreamBoxLauncherPink", Color(0xFFFF7BAC)),
    AppIconVariant("blue", "深蓝", ".DreamBoxLauncherBlue", Color(0xFF4A90D9)),
    AppIconVariant("dark", "曜黑", ".DreamBoxLauncherDark", Color(0xFF23232B)),
    AppIconVariant("custom", "自定义", ".DreamBoxLauncherCustom", Color(0xFFB79CFF)),
)

private const val ICON_VARIANT_KEY = "app_icon_variant"

private fun applyAppIconVariant(context: Context, variant: AppIconVariant) {
    val pm = context.packageManager
    val pkg = context.packageName
    APP_ICON_VARIANTS.forEach { v ->
        pm.setComponentEnabledSetting(
            ComponentName(pkg, pkg + v.aliasSuffix),
            if (v.id == variant.id) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }
}

private val MODULE_ICON_ROWS = listOf(
    Triple("app", "应用", FlyCat.Settings2),
    Triple("network", "网络", FlyCat.WifiCog),
    Triple("override", "覆写", FlyCat.GitMerge),
    Triple("meta", "Meta 功能", FlyCat.Meta),
    Triple("lab", "实验室", androidx.compose.material.icons.Icons.Filled.Science),
    Triple("log", "日志", FlyCat.ScrollText),
    Triple("about", "关于", FlyCat.Github),
)

@Composable
fun rememberIconPicker(): (String) -> Unit {
    val context = LocalContext.current
    var pendingKey by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val key = pendingKey
        pendingKey = null
        if (uri != null && key != null) {
            if (CustomIcons.saveFromUri(context, key, uri)) {
                context.toast("图标已更新")
            } else {
                context.toast("图片读取失败，换一张试试")
            }
        }
    }
    return remember(picker) {
        { key: String ->
            pendingKey = key
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

/** 应用图标子页内容：预置款秒换（工坊已拆到独立子页）。 */
@Composable
fun AppIconBlock() {
    val context = LocalContext.current
    val spacing = AppTheme.spacing
    val mmkv: MMKV = koinInject(named("settings"))
    var iconVariant by remember {
        mutableStateOf(mmkv.decodeString(ICON_VARIANT_KEY, "default").orEmpty().ifBlank { "default" })
    }
    Column(
        modifier = Modifier.padding(horizontal = spacing.space16, vertical = spacing.space14),
        verticalArrangement = Arrangement.spacedBy(spacing.space10),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            APP_ICON_VARIANTS.forEach { variant ->
                val selected = iconVariant == variant.id
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(variant.swatch)
                            .then(
                                if (selected) {
                                    Modifier.border(2.dp, BocchiPink, RoundedCornerShape(14.dp))
                                } else {
                                    Modifier
                                },
                            )
                            .clickable {
                                iconVariant = variant.id
                                mmkv.encode(ICON_VARIANT_KEY, variant.id)
                                runCatching { applyAppIconVariant(context, variant) }
                                if (variant.id == "custom") {
                                    context.toast("已切到自定义款：先在工坊传图构建、装上定制版后生效")
                                } else {
                                    context.toast("已切换为「${variant.name}」，桌面图标稍等片刻刷新")
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (variant.id == "custom") "图" else "梦",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (variant.id == "default") BocchiTextDark else Color.White,
                        )
                    }
                    Text(
                        text = variant.name,
                        fontSize = 12.sp,
                        color = if (selected) BocchiPink else BocchiTextGray,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
        Text(
            text = "预置款秒换；自定义款先去「自定义图标工坊」传图、云端打包后安装定制版生效。",
            fontSize = 11.sp,
            color = BocchiTextGray,
        )
    }
}

/** 导航栏图标子页内容。 */
@Composable
fun NavIconsBlock() {
    val pick = rememberIconPicker()
    Column {
        BottomBarDestination.entries.forEach { dest ->
            CustomIconRow(
                title = dest.label,
                customKey = "tab_" + dest.name.lowercase(),
                onPick = { pick("tab_" + dest.name.lowercase()) },
            ) {
                Icon(
                    imageVector = dest.icon,
                    contentDescription = null,
                    tint = BocchiTextDark,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** 模块图标子页内容。 */
@Composable
fun ModuleIconsBlock() {
    val pick = rememberIconPicker()
    Column {
        MODULE_ICON_ROWS.forEach { (key, title, icon) ->
            CustomIconRow(
                title = title,
                customKey = "module_$key",
                onPick = { pick("module_$key") },
            ) {
                // 与设置页 ModuleIcon 同款贴片（底色按 key 轮换粉/蓝/绿），不用单字占位。
                val bg = when (kotlin.math.abs(key.hashCode()) % 3) {
                    0 -> BocchiPinkBg
                    1 -> BocchiBlueBg
                    else -> androidx.compose.ui.graphics.Color(0xFFE3F5E9)
                }
                val fg = when (kotlin.math.abs(key.hashCode()) % 3) {
                    0 -> BocchiStar
                    1 -> BocchiBlueFg
                    else -> androidx.compose.ui.graphics.Color(0xFF43A047)
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** 返回按钮图标子页内容。 */
@Composable
fun BackButtonBlock() {
    val pick = rememberIconPicker()
    val spacing = AppTheme.spacing
    Column {
        CustomIconRow(
            title = "返回按钮图标",
            customKey = "back_button",
            onPick = { pick("back_button") },
        ) {
            Icon(
                imageVector = MiuixIcons.Back,
                contentDescription = null,
                tint = BocchiTextDark,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = "所有页面的返回按钮统一用这个图；系统全面屏手势返回照常有效。",
            fontSize = 11.sp,
            color = BocchiTextGray,
            modifier = Modifier.padding(
                start = spacing.space16,
                end = spacing.space16,
                bottom = spacing.space12,
            ),
        )
    }
}

/** 首页面板子页内容：按住把手上下拖动排序（拖动中只改本地顺序，松手才落盘），右侧开关控制显隐。 */
@Composable
fun HomePanelsBlock() {
    val context = LocalContext.current
    val spacing = AppTheme.spacing
    val (storedOrder, panelHidden) = rememberHomePanels()
    var localOrder by remember { mutableStateOf<List<String>?>(null) }
    val panelOrder = localOrder ?: storedOrder
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val rowPx = with(androidx.compose.ui.platform.LocalDensity.current) { 52.dp.toPx() }
    Column {
        panelOrder.forEach { key ->
            androidx.compose.runtime.key(key) {
                val isDragging = dragKey == key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                        .then(if (isDragging) Modifier.background(BocchiPinkBg) else Modifier)
                        .padding(horizontal = spacing.space16),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .pointerInput(key) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        localOrder = panelOrder
                                        dragKey = key
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        localOrder?.let { HomePanels.setOrder(context, it) }
                                        localOrder = null
                                        dragKey = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        localOrder = null
                                        dragKey = null
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        val working = (localOrder ?: panelOrder).toMutableList()
                                        var idx = working.indexOf(key)
                                        if (idx < 0) idx = 0
                                        var off = dragOffset + amount.y
                                        var changed = false
                                        while (off > rowPx / 2 && idx < working.lastIndex) {
                                            working.removeAt(idx)
                                            working.add(idx + 1, key)
                                            idx += 1
                                            off -= rowPx
                                            changed = true
                                        }
                                        while (off < -rowPx / 2 && idx > 0) {
                                            working.removeAt(idx)
                                            working.add(idx - 1, key)
                                            idx -= 1
                                            off += rowPx
                                            changed = true
                                        }
                                        if (changed) localOrder = working
                                        dragOffset = off.coerceIn(-idx * rowPx, (working.lastIndex - idx) * rowPx)
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.DragHandle,
                            contentDescription = null,
                            tint = BocchiTextGray,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = HomePanels.LABELS[key] ?: key,
                        fontSize = 13.sp,
                        color = BocchiTextDark,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = key !in panelHidden,
                        onCheckedChange = { show ->
                            HomePanels.setHidden(
                                context,
                                if (show) panelHidden - key else panelHidden + key,
                            )
                        },
                    )
                }
            }
        }
        Text(
            text = "按住左侧把手上下拖动排序；首页顶部的流量与开关区固定，其余卡片可在这里调顺序、关掉不要的。",
            fontSize = 11.sp,
            color = BocchiTextGray,
            modifier = Modifier.padding(
                start = spacing.space16,
                end = spacing.space16,
                top = spacing.space8,
                bottom = spacing.space12,
            ),
        )
    }
}

/** 延迟曲线子页内容。 */
@Composable
fun SparklineBlock() {
    val context = LocalContext.current
    val spacing = AppTheme.spacing
    Column {
        var sparkShape by remember {
            mutableStateOf(
                com.suanran.dreambox.presentation.component.misc.SparklinePrefs.shape(context)
            )
        }
        var sparkColor by remember {
            mutableStateOf(
                com.suanran.dreambox.presentation.component.misc.SparklinePrefs.colorMode(context)
            )
        }
        top.yukonga.miuix.kmp.preference.WindowDropdownPreference(
            title = "曲线形状",
            summary = "节点行与详情抽屉里延迟走势线的形状",
            items = listOf("折线", "平滑曲线", "柱状"),
            selectedIndex = sparkShape,
            onSelectedIndexChange = { index ->
                sparkShape = index
                com.suanran.dreambox.presentation.component.misc.SparklinePrefs.setShape(context, index)
            },
        )
        top.yukonga.miuix.kmp.preference.WindowDropdownPreference(
            title = "曲线颜色",
            summary = "跟随主题用主题色；按延迟用绿 / 黄 / 红表示快慢",
            items = listOf("跟随主题", "按延迟"),
            selectedIndex = sparkColor,
            onSelectedIndexChange = { index ->
                sparkColor = index
                com.suanran.dreambox.presentation.component.misc.SparklinePrefs.setColorMode(context, index)
            },
        )
        Text(
            text = "节点图标也能换：点开任意节点，在详情抽屉里点「换图标」。",
            fontSize = 11.sp,
            color = BocchiTextGray,
            modifier = Modifier.padding(
                start = spacing.space16,
                end = spacing.space16,
                bottom = spacing.space12,
            ),
        )
    }
}

/**
 * 主题页「个性化」入口卡（点进去版）：七行入口各带当前状态小字，点进各自子页，
 * 主题页本身保持一屏，不再把七块设置全摊开。
 */
@Composable
fun PersonalizeEntriesCard(navigator: Navigator) {
    val context = LocalContext.current
    val mmkv: MMKV = koinInject(named("settings"))
    val iconVariantId = mmkv.decodeString(ICON_VARIANT_KEY, "default").orEmpty().ifBlank { "default" }
    val iconVariantName = APP_ICON_VARIANTS.firstOrNull { it.id == iconVariantId }?.name ?: "默认"
    val workshopSummary =
        if (mmkv.decodeString(TOKEN_KEY, "").orEmpty().isNotBlank()) "令牌已保存" else "未设置"
    val backCustom = rememberCustomIconBitmap("back_button") != null
    val (panelOrder, panelHidden) = rememberHomePanels()
    val shownCount = panelOrder.count { it !in panelHidden }
    val panelsSummary = "${panelOrder.size} 个 · " +
        if (shownCount == panelOrder.size) "全部显示" else "显示 $shownCount 个"
    val sparkSummary = "${
        listOf("折线", "平滑曲线", "柱状")[
            com.suanran.dreambox.presentation.component.misc.SparklinePrefs.shape(context)]
    } · ${
        listOf("跟随主题", "按延迟")[
            com.suanran.dreambox.presentation.component.misc.SparklinePrefs.colorMode(context)]
    }"
    BocchiCard {
        Column {
            PersonalizeEntryRow("应用图标", iconVariantName) { navigator.push(Route.AppIcon) }
            OneCardDivider()
            PersonalizeEntryRow("自定义图标工坊", workshopSummary) { navigator.push(Route.IconWorkshop) }
            OneCardDivider()
            PersonalizeEntryRow("导航栏图标", "${BottomBarDestination.entries.size} 项") {
                navigator.push(Route.NavIcons)
            }
            OneCardDivider()
            PersonalizeEntryRow("模块图标", "${MODULE_ICON_ROWS.size} 项") {
                navigator.push(Route.ModuleIcons)
            }
            OneCardDivider()
            PersonalizeEntryRow("返回按钮图标", if (backCustom) "自定义" else "默认") {
                navigator.push(Route.BackButtonIcon)
            }
            OneCardDivider()
            PersonalizeEntryRow("首页面板", panelsSummary) { navigator.push(Route.HomePanels) }
            OneCardDivider()
            PersonalizeEntryRow("延迟曲线", sparkSummary) { navigator.push(Route.SparklineSettings) }
        }
    }
}

@Composable
private fun PersonalizeEntryRow(title: String, summary: String, onClick: () -> Unit) {
    val spacing = AppTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = spacing.space16, vertical = spacing.space14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = BocchiTextDark,
            modifier = Modifier.weight(1f),
        )
        Text(text = summary, fontSize = 13.sp, color = BocchiTextGray)
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = BocchiTextGray,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CustomIconRow(
    title: String,
    customKey: String,
    onPick: () -> Unit,
    defaultPreview: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val spacing = AppTheme.spacing
    val custom = rememberCustomIconBitmap(customKey)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.space16, vertical = spacing.space10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (custom != null) {
                Image(
                    bitmap = custom,
                    contentDescription = null,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape),
                )
            } else {
                defaultPreview()
            }
        }
        Spacer(modifier = Modifier.width(spacing.space12))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = BocchiTextDark,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "换图",
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.clickable { onPick() },
        )
        if (custom != null) {
            Spacer(modifier = Modifier.width(spacing.space16))
            Text(
                text = "恢复默认",
                fontSize = 13.sp,
                color = BocchiTextGray,
                modifier = Modifier.clickable { CustomIcons.clear(context, customKey) },
            )
        }
    }
}

private const val GH_REPO = "anransu983-sketch/dreambox-asbefore"
private const val TOKEN_KEY = "icon_build_token"

@Composable
fun IconWorkshopBlock(mmkv: MMKV) {
    val context = LocalContext.current
    val spacing = AppTheme.spacing
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var token by remember { mutableStateOf(mmkv.decodeString(TOKEN_KEY, "").orEmpty()) }
    var status by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val tk = token.trim()
            if (tk.isEmpty()) {
                context.toast("先填构建令牌并保存")
            } else {
                status = "正在上传图片并触发构建…"
                scope.launch {
                    status = runCatching {
                        uploadIconAndDispatch(context, uri, tk)
                        "已开始构建：约 5–10 分钟后点「构建状态」确认，好了再开下载页安装"
                    }.getOrElse { "失败：" + (it.message ?: it.toString()) }
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.space10)) {
        Text(
            text = "自定义图片工坊",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = BocchiTextDark,
        )
        Text(
            text = "传一张图 → 云端打包（约 5–10 分钟）→ 装定制版，桌面图标就变成你的图。定制版与正式版同版本同签名，可互相覆盖装。令牌需要该仓库的 Contents 与 Actions 写权限，只存在本机。",
            fontSize = 11.sp,
            color = BocchiTextGray,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = token,
                onValueChange = { token = it },
                label = "构建令牌",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(spacing.space12))
            Text(
                text = "保存",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    mmkv.encode(TOKEN_KEY, token.trim())
                    context.toast("令牌已保存")
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.space16)) {
            Text(
                text = "上传图片并开始构建",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
            Text(
                text = "构建状态",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    val tk = token.trim()
                    if (tk.isEmpty()) {
                        context.toast("先填构建令牌并保存")
                    } else {
                        status = "查询中…"
                        scope.launch {
                            status = runCatching { fetchLatestRunStatus(tk) }
                                .getOrElse { "查询失败：" + (it.message ?: it.toString()) }
                        }
                    }
                },
            )
            Text(
                text = "打开定制版下载",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/$GH_REPO/releases/tag/custom-icon"),
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
        }
        if (status.isNotEmpty()) {
            Text(text = status, fontSize = 11.5.sp, color = BocchiTextGray)
        }
    }
}

private fun ghApi(
    method: String,
    path: String,
    token: String,
    body: org.json.JSONObject?,
): Pair<Int, String> {
    val conn = java.net.URL("https://api.github.com$path").openConnection() as java.net.HttpURLConnection
    conn.requestMethod = method
    conn.setRequestProperty("Authorization", "Bearer $token")
    conn.setRequestProperty("Accept", "application/vnd.github+json")
    conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
    conn.connectTimeout = 20000
    conn.readTimeout = 30000
    if (body != null) {
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
    }
    val code = conn.responseCode
    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
    val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    return code to text
}

private suspend fun uploadIconAndDispatch(context: Context, uri: android.net.Uri, token: String) {
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("图片读取失败")
        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val (getCode, getText) = ghApi("GET", "/repos/$GH_REPO/contents/custom-icon/icon.png?ref=main", token, null)
        val sha = if (getCode == 200) {
            runCatching { org.json.JSONObject(getText).optString("sha") }.getOrNull()
        } else {
            null
        }
        val putBody = org.json.JSONObject()
            .put("message", "workshop: custom app icon")
            .put("content", b64)
            .put("branch", "main")
        if (!sha.isNullOrEmpty()) putBody.put("sha", sha)
        val (putCode, putText) = ghApi("PUT", "/repos/$GH_REPO/contents/custom-icon/icon.png", token, putBody)
        if (putCode !in 200..299) {
            throw IllegalStateException("上传失败（$putCode）：${putText.take(140)}")
        }
        val dispatchBody = org.json.JSONObject()
            .put("ref", "main")
            .put("inputs", org.json.JSONObject().put("custom_icon", "true"))
        val (code, text) = ghApi(
            "POST",
            "/repos/$GH_REPO/actions/workflows/build-dreambox-apk.yml/dispatches",
            token,
            dispatchBody,
        )
        if (code !in 200..299) {
            throw IllegalStateException("触发构建失败（$code）：${text.take(140)}")
        }
    }
}

private suspend fun fetchLatestRunStatus(token: String): String {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val (code, text) = ghApi(
            "GET",
            "/repos/$GH_REPO/actions/workflows/build-dreambox-apk.yml/runs?per_page=1",
            token,
            null,
        )
        if (code != 200) throw IllegalStateException("HTTP $code")
        val run = org.json.JSONObject(text).optJSONArray("workflow_runs")?.optJSONObject(0)
            ?: return@withContext "还没有构建记录"
        val status = run.optString("status")
        val conclusion = run.optString("conclusion")
        val created = run.optString("created_at").take(16).replace("T", " ")
        when {
            status != "completed" -> "最近一次构建进行中（开始于 $created UTC）"
            conclusion == "success" -> "最近一次构建已成功（$created UTC）：若那是你的定制构建，去下载页装定制版"
            else -> "最近一次构建失败（$created UTC，结论 $conclusion）"
        }
    }
}
