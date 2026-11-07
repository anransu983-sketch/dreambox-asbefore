package com.suanran.dreambox.feature.settings.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Pets
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suanran.dreambox.presentation.component.misc.BocchiBlueBg
import com.suanran.dreambox.presentation.component.misc.BocchiBlueFg
import com.suanran.dreambox.presentation.component.misc.BocchiPink
import com.suanran.dreambox.presentation.component.misc.BocchiPinkBg
import com.suanran.dreambox.presentation.component.misc.BocchiStar
import com.suanran.dreambox.presentation.component.misc.BocchiTextDark
import com.suanran.dreambox.presentation.component.misc.BocchiTextGray
import com.suanran.dreambox.presentation.component.sortable.SortableSectionCard
import com.suanran.dreambox.presentation.component.sortable.loadHiddenSections
import com.suanran.dreambox.presentation.component.sortable.saveHiddenSections
import com.suanran.dreambox.presentation.component.sortable.hiddenSectionsRestore
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.icon.FlyCat
import com.suanran.dreambox.presentation.icon.flycat.Search
import com.suanran.dreambox.presentation.icon.flycat.Settings2
import com.suanran.dreambox.presentation.icon.flycat.UserKey
import com.suanran.dreambox.presentation.navigation.Navigator
import com.suanran.dreambox.presentation.navigation.Route
import com.suanran.dreambox.presentation.theme.AppTheme
import top.yukonga.miuix.kmp.basic.Icon
import com.suanran.dreambox.presentation.component.misc.BocchiCard
import com.suanran.dreambox.presentation.component.misc.BocchiSectionTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

private data class LabEntry(
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val route: Route,
)

/**
 * 实验室：实验性/小众功能集合（主题、探针、IP 测试）。
 */
@Composable
fun LabScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val spacing = AppTheme.spacing
    val context = LocalContext.current

    // 实验室入口
    val mmkv: com.tencent.mmkv.MMKV = org.koin.compose.koinInject(org.koin.core.qualifier.named("settings"))

    val entries = listOf(
        LabEntry(
            title = "插件",
            summary = "重型功能按需下载，删掉腾空间",
            icon = Icons.Filled.Extension,
            route = Route.FeaturePacks,
        ),
        LabEntry(
            title = "模块",
            summary = "各页面功能模块开关，关掉即隐藏",
            icon = Icons.Filled.ViewModule,
            route = Route.Modules,
        ),
    )

    var ageKeyHybrid by remember { mutableStateOf(false) }
    var ageKeyDialogVisible by remember { mutableStateOf(false) }
    var ageChooserVisible by remember { mutableStateOf(false) }

    val defaultKeys = listOf("packs", "modules")
    var hiddenKeys by remember { mutableStateOf(loadHiddenSections(mmkv, "lab")) }
    val visibleOrder = defaultKeys.filter { it !in hiddenKeys }
    val listState = rememberLazyListState()
    val entryByKey = entries.associateBy {
        when (it.route) {
            Route.FeaturePacks -> "packs"
            Route.Modules -> "modules"
            else -> "packs"
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                title = "实验室",
                scrollBehavior = scrollBehavior,
                navigationIcon = { NavigationBackIcon(navigator = navigator) },
                actions = { com.suanran.dreambox.presentation.component.help.ModuleHelpAction("lab") },
            )
        },
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            lazyListState = listState,
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "实验性功能，稳定后会移到外面",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            itemsIndexed(visibleOrder, key = { _, k -> k }) { index, key ->
                val e = entryByKey[key] ?: return@itemsIndexed
                SortableSectionCard(
                        isDragging = false,
                        onHide = {
                            hiddenKeys = hiddenKeys + key
                            saveHiddenSections(mmkv, "lab", hiddenKeys + key)
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { navigator.push(e.route) }
                                .padding(horizontal = spacing.space16, vertical = spacing.space14),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LabEntryIcon(icon = e.icon, key = key)
                            Spacer(modifier = Modifier.width(spacing.space12))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = e.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = e.summary,
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                )
                            }
                            Text(
                                text = "✦",
                                fontSize = 12.sp,
                                color = BocchiPink.copy(alpha = 0.6f),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
            }
            item {
                // 与上面插件/模块完全同款的入口行（原先标题在卡外、两行裸文字，和整页风格不一致，用户指为突兀）
                BocchiCard {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { ageChooserVisible = true }
                            .padding(horizontal = spacing.space16, vertical = spacing.space14),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LabEntryIcon(icon = FlyCat.UserKey, key = "agekey")
                        Spacer(modifier = Modifier.width(spacing.space12))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = FlyTxt.MetaFeature.AgeKey.Section,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BocchiTextDark,
                                maxLines = 1,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "生成 X25519 或混合后量子密钥",
                                fontSize = 11.sp,
                                color = BocchiTextGray,
                                maxLines = 1,
                            )
                        }
                        Text(
                            text = "✦",
                            fontSize = 12.sp,
                            color = BocchiPink.copy(alpha = 0.6f),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = BocchiTextGray,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            item {
                // 桌面宠物：独立悬浮窗服务，开关自带状态存储，不依赖任何现有设置项
                val petPrefs = remember { context.getSharedPreferences("desktop_pet", android.content.Context.MODE_PRIVATE) }
                var petEnabled by remember { mutableStateOf(petPrefs.getBoolean("enabled", false)) }
                // 从通知「抱回梦盒」停宠时服务侧会写回 enabled=false；回到本页时
                // 按 prefs 实时刷新开关，别停在旧状态上（此前要离开重进才对）。
                val petOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                androidx.compose.runtime.DisposableEffect(petOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            petEnabled = petPrefs.getBoolean("enabled", false)
                        }
                    }
                    petOwner.lifecycle.addObserver(observer)
                    onDispose { petOwner.lifecycle.removeObserver(observer) }
                }
                BocchiCard {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = spacing.space16, vertical = spacing.space14),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LabEntryIcon(icon = Icons.Filled.Pets, key = "pet")
                        Spacer(modifier = Modifier.width(spacing.space12))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "桌面宠物",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BocchiTextDark,
                                maxLines = 1,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "光点仓鼠住到手机桌面：会乱跑，揪耳朵会挣扎",
                                fontSize = 11.sp,
                                color = BocchiTextGray,
                                maxLines = 1,
                            )
                        }
                        Switch(
                            checked = petEnabled,
                            onCheckedChange = { want ->
                                val svcIntent = android.content.Intent().setClassName(
                                    context.packageName,
                                    "com.suanran.dreambox.pet.DesktopPetService",
                                )
                                if (want) {
                                    if (!android.provider.Settings.canDrawOverlays(context)) {
                                        val permIntent = android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            android.net.Uri.parse("package:" + context.packageName),
                                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(permIntent)
                                    } else {
                                        petPrefs.edit().putBoolean("enabled", true).apply()
                                        petEnabled = true
                                        androidx.core.content.ContextCompat.startForegroundService(context, svcIntent)
                                    }
                                } else {
                                    petPrefs.edit().putBoolean("enabled", false).apply()
                                    petEnabled = false
                                    context.stopService(svcIntent)
                                }
                            },
                        )
                    }
                    // 大小三档：改完即时生效（服务在运行就重新发一次启动意图让它应用新尺寸）
                    var petSize by remember { mutableStateOf(petPrefs.getInt("size_dp", 96)) }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = spacing.space16, end = spacing.space16, bottom = spacing.space14),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "大小",
                            fontSize = 12.sp,
                            color = BocchiTextGray,
                        )
                        Spacer(modifier = Modifier.width(spacing.space12))
                        listOf(72 to "小", 96 to "中", 128 to "大", 208 to "巨大").forEach { (dpVal, label) ->
                            val selected = petSize == dpVal
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) BocchiPink else BocchiBlueBg)
                                    .clickable {
                                        petSize = dpVal
                                        petPrefs.edit().putInt("size_dp", dpVal).apply()
                                        if (petEnabled) {
                                            val svcIntent2 = android.content.Intent().setClassName(
                                                context.packageName,
                                                "com.suanran.dreambox.pet.DesktopPetService",
                                            )
                                            androidx.core.content.ContextCompat.startForegroundService(context, svcIntent2)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    color = if (selected) Color.White else BocchiTextDark,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                    // 品种与颜色：改完即时换（服务在运行就重新发一次启动意图应用）
                    var petBreed by remember { mutableStateOf(petPrefs.getString("breed", "golden") ?: "golden") }
                    var petTint by remember { mutableStateOf(petPrefs.getInt("tint", 0)) }
                    fun applyPetLook() {
                        if (petEnabled) {
                            val svcIntent3 = android.content.Intent().setClassName(
                                context.packageName,
                                "com.suanran.dreambox.pet.DesktopPetService",
                            )
                            androidx.core.content.ContextCompat.startForegroundService(context, svcIntent3)
                        }
                    }
                    Text(
                        text = "品种",
                        fontSize = 12.sp,
                        color = BocchiTextGray,
                        modifier = Modifier.padding(start = spacing.space16, top = 2.dp, bottom = 6.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = spacing.space16, end = spacing.space16, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf(
                            "golden" to "金丝熊",
                            "sanxian" to "三线",
                            "niunai" to "奶牛",
                            "yinhu" to "银狐",
                            "heixiong" to "黑熊",
                        ).forEach { (key, label) ->
                            val selected = petBreed == key
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) BocchiPink else BocchiBlueBg)
                                    .clickable {
                                        petBreed = key
                                        petPrefs.edit().putString("breed", key).apply()
                                        applyPetLook()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    color = if (selected) Color.White else BocchiTextDark,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                    Text(
                        text = "颜色",
                        fontSize = 12.sp,
                        color = BocchiTextGray,
                        modifier = Modifier.padding(start = spacing.space16, bottom = 6.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = spacing.space16, end = spacing.space16, bottom = spacing.space14),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf(
                            0 to Color(0xFFE8B04B),
                            1 to Color(0xFFF9A8D4),
                            2 to Color(0xFF7DD3FC),
                            3 to Color(0xFF6EE7B7),
                            4 to Color(0xFFC4B5FD),
                        ).forEach { (idx, col) ->
                            val selected = petTint == idx
                            Box(
                                modifier = Modifier
                                    .size(if (selected) 26.dp else 22.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(col)
                                    .clickable {
                                        petTint = idx
                                        petPrefs.edit().putInt("tint", idx).apply()
                                        applyPetLook()
                                    },
                            ) {}
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                    }
                    Text(
                        text = "吃饱后的谢谢话（一行一句，留空用默认）",
                        fontSize = 12.sp,
                        color = BocchiTextGray,
                        modifier = Modifier.padding(start = spacing.space16, bottom = 6.dp),
                    )
                    var petLines by remember { mutableStateOf(petPrefs.getString("bubble_lines", "") ?: "") }
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = spacing.space16, end = spacing.space16, bottom = spacing.space14)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BocchiBlueBg)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        BasicTextField(
                            value = petLines,
                            onValueChange = {
                                petLines = it
                                petPrefs.edit().putString("bubble_lines", it).apply()
                            },
                            textStyle = TextStyle(fontSize = 13.sp, color = BocchiTextDark),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (petLines.isEmpty()) {
                                    Text(
                                        text = "谢谢主人，愿主人天天开心^_^",
                                        fontSize = 13.sp,
                                        color = BocchiTextGray,
                                    )
                                }
                                inner()
                            },
                        )
                    }
                }
            }
        }
        com.suanran.dreambox.presentation.component.dialog.AppDialog(
            show = ageChooserVisible,
            title = FlyTxt.MetaFeature.AgeKey.Section,
            onDismissRequest = { ageChooserVisible = false },
        ) {
            Column {
                ArrowPreference(
                    title = FlyTxt.MetaFeature.AgeKey.X25519Title,
                    onClick = {
                        ageChooserVisible = false
                        ageKeyHybrid = false
                        ageKeyDialogVisible = true
                    },
                )
                ArrowPreference(
                    title = FlyTxt.MetaFeature.AgeKey.HybridTitle,
                    onClick = {
                        ageChooserVisible = false
                        ageKeyHybrid = true
                        ageKeyDialogVisible = true
                    },
                )
            }
        }
        AgeKeyGeneratorDialog(
            show = ageKeyDialogVisible,
            hybrid = ageKeyHybrid,
            onDismiss = { ageKeyDialogVisible = false },
            onDismissFinished = {},
        )
    }
}

@Composable
private fun LabEntryIcon(icon: ImageVector, key: String) {
    val bg = when (kotlin.math.abs(key.hashCode()) % 3) {
        0 -> BocchiPinkBg
        1 -> BocchiBlueBg
        else -> Color(0xFFE3F5E9)
    }
    val fg = when (kotlin.math.abs(key.hashCode()) % 3) {
        0 -> BocchiStar
        1 -> BocchiBlueFg
        else -> Color(0xFF43A047)
    }
    Box(
        modifier = Modifier.size(AppTheme.sizes.settingsIconContainerSize)
            .clip(RoundedCornerShape(12.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(24.dp),
        )
    }
}
