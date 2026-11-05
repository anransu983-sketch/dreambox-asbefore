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

package com.suanran.dreambox.feature.settings.presentation.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Science
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suanran.dreambox.core.contract.AppSettingsReader
import com.suanran.dreambox.presentation.theme.horizontalPadding
import androidx.compose.foundation.Image
import com.suanran.dreambox.presentation.component.misc.rememberCustomIconBitmap
import com.suanran.dreambox.presentation.component.misc.BocchiBlueBg
import com.suanran.dreambox.presentation.component.misc.BocchiBlueFg
import com.suanran.dreambox.presentation.component.misc.BocchiPink
import com.suanran.dreambox.presentation.component.misc.BocchiPinkBg
import com.suanran.dreambox.presentation.component.misc.BocchiStar
import com.suanran.dreambox.presentation.component.misc.bocchiDark
import com.suanran.dreambox.presentation.component.misc.bocchiSummaryColor
import com.suanran.dreambox.presentation.component.misc.bocchiTitleColor
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.navigation.LocalDetailNavigator
import com.suanran.dreambox.presentation.component.navigation.LocalNavigator
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.icon.FlyCat
import com.suanran.dreambox.presentation.icon.flycat.FlaskConical
import com.suanran.dreambox.presentation.icon.flycat.Github
import com.suanran.dreambox.presentation.icon.flycat.GitMerge
import com.suanran.dreambox.presentation.icon.flycat.Meta
import com.suanran.dreambox.presentation.icon.flycat.ScrollText
import com.suanran.dreambox.presentation.icon.flycat.Search
import com.suanran.dreambox.presentation.icon.flycat.Settings2
import com.suanran.dreambox.presentation.icon.flycat.WifiCog
import com.suanran.dreambox.presentation.navigation.Route
import com.suanran.dreambox.presentation.theme.AppTheme
import com.tencent.mmkv.MMKV
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import com.suanran.dreambox.presentation.component.navigation.BottomBarDestination
import com.suanran.dreambox.presentation.component.navigation.LocalHandlePageChange
import com.suanran.dreambox.presentation.component.navigation.LocalMainPagerState
import com.suanran.dreambox.presentation.component.sky.SkyBackdropFull
import com.suanran.dreambox.presentation.component.sky.SkyBadgeGold
import com.suanran.dreambox.presentation.component.sky.SkyDeepBase
import com.suanran.dreambox.presentation.component.sky.SkyGlowLabel
import com.suanran.dreambox.presentation.component.sky.SkyLabelEntry
import com.suanran.dreambox.presentation.component.sky.SkyLabelGlow
import com.suanran.dreambox.presentation.component.sky.SkyNightBase
import com.suanran.dreambox.presentation.component.sky.SkyShotMap
import com.suanran.dreambox.presentation.component.sky.SkyStatusBarIcons
import com.suanran.dreambox.presentation.component.sky.rememberSkyCaption
import com.suanran.dreambox.presentation.component.sky.rememberSkyClock
import com.suanran.dreambox.presentation.component.sky.skyCenteredAt
import kotlinx.coroutines.delay
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import com.suanran.dreambox.runtime.api.contract.ProxyControlContract
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** 设置磁吸模块描述。 */
private data class SettingsModule(
    val key: String,
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val route: Route?,
    val badge: String? = null,
)

private const val MMKV_KEY_MODULE_ORDER = "settings_module_order"

private fun defaultModules(): List<SettingsModule> = listOf(
    SettingsModule(
        key = "app",
        title = FlyTxt.Settings.UiSettings.App,
        summary = FlyTxt.Settings.UiSettings.AppSummary,
        icon = FlyCat.Settings2,
        route = Route.AppSettings,
    ),
    SettingsModule(
        key = "network",
        title = FlyTxt.Settings.UiSettings.Network,
        summary = FlyTxt.Settings.UiSettings.NetworkSummary,
        icon = FlyCat.WifiCog,
        route = Route.NetworkSettings,
    ),
    SettingsModule(
        key = "override",
        title = FlyTxt.Settings.UiSettings.Override,
        summary = FlyTxt.Settings.UiSettings.OverrideSummary,
        icon = FlyCat.GitMerge,
        route = Route.Override,
    ),
    SettingsModule(
        key = "meta",
        title = FlyTxt.Settings.UiSettings.MetaFeatures,
        summary = FlyTxt.Settings.UiSettings.MetaFeaturesSummary,
        icon = FlyCat.Meta,
        route = Route.MetaFeature,
    ),
    SettingsModule(
        key = "lab",
        title = "实验室",
        summary = "插件 · 模块 · 密钥",
        icon = Icons.Filled.Science,
        route = Route.Lab,
    ),
    SettingsModule(
        key = "log",
        title = FlyTxt.Settings.More.Logs,
        summary = FlyTxt.Settings.More.LogsSummary,
        icon = FlyCat.ScrollText,
        route = Route.Log,
    ),
    SettingsModule(
        key = "about",
        title = FlyTxt.Settings.More.About,
        summary = FlyTxt.Settings.More.AboutSummary,
        icon = FlyCat.Github,
        route = Route.About,
        badge = com.suanran.dreambox.core.BuildConfigHolder.versionName,
    ),
)

@SuppressLint("LocalContextResourcesRead")
@Composable
fun SettingPager(mainInnerPadding: PaddingValues) {
    val scrollBehavior = MiuixScrollBehavior()
    val rootNavigator = LocalNavigator.current
    val detailNavigator = LocalDetailNavigator.current
    val openSecondary: (Route) -> Unit = { route ->
        if (detailNavigator != null) {
            detailNavigator.replaceAll(listOf(route))
        } else {
            rootNavigator.push(route)
        }
    }

    val mmkv: MMKV = koinInject(named("settings"))
    val settingsReader: AppSettingsReader = koinInject()
    val isAnime = true
    // 主页模块固定默认顺序（拖动排序已删，2026-11-02 用户拍板）；隐藏照常。
    val modules = defaultModules()
    // 隐藏集合只在「模块」页里管理（主页卡片上的 ⋮ 隐藏已删，2026-11-03 用户拍板）；这里每次组合现读现过滤。
    val hiddenKeys = loadHiddenModules(mmkv)

    val listState = rememberLazyListState()
    val visibleModules = modules.distinctBy { it.key }.filter { it.key !in hiddenKeys }

    // 仿首页（天空版）：整屏浮岛画 + 地标光字导航 + 云海光字七行，无横幅、无底栏。旧版保留在下方分支里，随时可切回。
    if (SKY_SETTINGS_PAGE) {
        SkySettingScene(
            modules = visibleModules,
            onOpen = { route -> openSecondary(route) },
        )
        return
    }

    // 深色时背景换成种子色深色渐变，不再整页波奇粉（与其它页黑底不统一，深色专项 1.77）。
    val dark = bocchiDark()
    val scheme = MiuixTheme.colorScheme
    val pageBrush = if (dark) {
        Brush.verticalGradient(
            listOf(
                scheme.primary.copy(alpha = 0.22f),
                scheme.background.copy(alpha = 0.6f),
                scheme.background,
            ),
        )
    } else {
        animeBackgroundBrush
    }

    Scaffold(topBar = { TopBar(title = FlyTxt.Settings.Title, scrollBehavior = scrollBehavior) }) { innerPadding ->
        ScreenLazyColumn(
            lazyListState = listState,
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainInnerPadding),
            modifier = if (isAnime) Modifier.background(pageBrush) else Modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(visibleModules, key = { _, m -> m.key }) { _, module ->
                if (isAnime) {
                    AnimeModuleCard(
                        module = module,
                        isDragging = false,
                        onClick = { module.route?.let { openSecondary(it) } },
                    )
                } else {
                    MaterialModuleCard(
                        module = module,
                        isDragging = false,
                        onClick = { module.route?.let { openSecondary(it) } },
                    )
                }
            }
        }
    }
}

private const val SKY_SETTINGS_PAGE = true

/**
 * 设置页 · 天空版：飞艇整屏画，七个入口的字直接画在画里（气球=应用、左灯=实验室、
 * 右灯=网络、主帆=覆写、船身=Meta 功能、远岛=关于、系泊石碑=日志），程序只做三件事：
 * 让整幅画缓缓浮动、在画上叠光与雾（灯火呼吸、瀑布微光、雾流、星光）、在画里的字上放隐形热区。
 * 浮现 = 各物件的光依次亮起（bloom）；按压时该物件的光涨起。跳转沿用 modules / openSecondary。
 */
@Composable
private fun SkySettingScene(
    modules: List<SettingsModule>,
    onOpen: (Route) -> Unit,
) {
    val pager = LocalMainPagerState.current
    val active = pager.selectedPage == BottomBarDestination.Setting.ordinal
    SkyStatusBarIcons(active)
    val handlePageChange = LocalHandlePageChange.current
    val proxyControl: ProxyControlContract = koinInject()
    val running by proxyControl.isRunning.collectAsStateWithLifecycle()
    val lively by animateFloatAsState(if (running) 1f else 0f, tween(5000), label = "setLively")
    val sat by animateFloatAsState(if (running) 1f else 0.8f, tween(700), label = "setSat")
    val dusk by animateFloatAsState(if (running) 0f else 0.10f, tween(700), label = "setDusk")
    val ctx = LocalContext.current
    val reduceMotion =
        remember {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
    val clock = rememberSkyClock(active, reduceMotion)
    var showTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val caption = rememberSkyCaption(active, showTick)
    LaunchedEffect(running) { showTick++ }
    var imgReady by remember { mutableStateOf(false) }
    var timedOut by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) {
            delay(1200)
            timedOut = true
        }
    }
    val fallback = timedOut && !imgReady
    var pressedKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(active) { if (!active) pressedKey = null }
    val boost = remember { Animatable(0f) }
    LaunchedEffect(pressedKey) {
        if (pressedKey != null) {
            boost.animateTo(1f, tween(120))
        } else {
            boost.animateTo(0f, tween(300))
        }
    }
    // 浮现：进页、连接变化、点画时，各物件的光依次亮起
    val bloom = remember { Animatable(0f) }
    LaunchedEffect(active, showTick, imgReady) {
        if (active && imgReady) {
            bloom.snapTo(0f)
            bloom.animateTo(1f, tween(1500))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val map =
            remember(w, h) {
                SkyShotMap(w.toFloat(), h.toFloat(), w.toFloat(), h.toFloat(), 1f, 0.5f, 0.5f, aspect = 1008f / 2128f)
            }
        if (!fallback) {
            // 会动的船：底画与氛围层同在一个缓浮层里（14s 一圈、±0.4% 漂移），光与画不脱位
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val t = clock.floatValue
                    translationX = sin(2.0 * PI * (t / 11f)).toFloat() * size.width * 0.0035f
                    translationY = sin(2.0 * PI * (t / 7f)).toFloat() * size.height * 0.0055f
                    rotationZ = sin(2.0 * PI * (t / 9f)).toFloat() * 0.3f
                    val breathe = 1.045f + 0.008f * sin(2.0 * PI * (t / 13f)).toFloat()
                    scaleX = breathe
                    scaleY = breathe
                },
            ) {
                SkyBackdropFull(
                    map = map,
                    assetUri = SKY_SETTINGS_SHIP_URI,
                    sat = sat,
                    dusk = dusk,
                    onReadyChange = { imgReady = it },
                )
                SkyShipAmbient(
                    phase = { clock.floatValue },
                    lively = lively,
                    boostKey = pressedKey,
                    boost = { boost.value },
                    bloom = { bloom.value },
                    mistOnly = false,
                )
            }
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SkyNightBase, SkyDeepBase))))
            SkyShipAmbient(
                phase = { clock.floatValue },
                lively = lively,
                boostKey = null,
                boost = { 0f },
                bloom = { 1f },
                mistOnly = true,
            )
        }
        if (imgReady || fallback) {
            // 点画任意处：光重新浮现（同首页题字时钟）
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) { showTick++ },
            )
            if (!fallback) {
                // 隐形热区：对准画里的字，点字办事
                modules.forEachIndexed { index, module ->
                    val spot = SETTINGS_SPOTS[module.key] ?: return@forEachIndexed
                    val sizeDp = SETTINGS_HOTSPOT_SIZE[module.key] ?: (150.dp to 72.dp)
                    ShipHotspot(
                        center = map.at(spot),
                        wDp = sizeDp.first,
                        hDp = sizeDp.second,
                        index = index,
                        contentDescription = settingsDesc(module),
                        onPressedChange = { p ->
                            pressedKey = if (p) module.key else if (pressedKey == module.key) null else pressedKey
                        },
                        onClick = { module.route?.let { onOpen(it) } },
                    )
                }
            } else {
                // 画没加载出来时的兜底：中央一列光字（行为与画上热区一致）
                modules.forEachIndexed { index, module ->
                    SkyLabelEntry(
                        center = Offset(w / 2f, h * (0.20f + index * 0.095f)),
                        title = module.title,
                        sub = settingsSub(module),
                        index = index,
                        caption = { caption.value },
                        phase = { clock.floatValue },
                        parentSize = IntSize(w, h),
                        contentDescription = settingsDesc(module),
                        active = active,
                        baseAlpha = 0f,
                        onClick = { module.route?.let { onOpen(it) } },
                    )
                }
            }
            // 「‹ 首页」：回岛的一条路（另有横滑与系统返回）
            SkyLabelEntry(
                center = map.at(Offset(0.12f, 0.057f)),
                title = "‹ " + BottomBarDestination.Home.label,
                index = 7,
                baseAlpha = 0f,
                caption = { caption.value },
                phase = { clock.floatValue },
                parentSize = IntSize(w, h),
                contentDescription = BottomBarDestination.Home.label,
                active = active,
                onClick = { handlePageChange(BottomBarDestination.Home.ordinal) },
            )
        }
    }
}

/** 画里字的隐形热区：无任何可见元素，只有语义与按压回调（按压点亮该物件的光）。 */
@Composable
private fun ShipHotspot(
    center: Offset,
    wDp: Dp,
    hDp: Dp,
    index: Int,
    contentDescription: String,
    onPressedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed) { onPressedChange(pressed) }
    Box(
        Modifier
            .offset {
                IntOffset(
                    (center.x - wDp.toPx() / 2f).roundToInt(),
                    (center.y - hDp.toPx() / 2f).roundToInt(),
                )
            }
            .size(wDp, hDp)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                traversalIndex = index.toFloat()
            }
            .clickable(interactionSource = interaction, indication = null) { onClick() },
    )
}

private const val SKY_SETTINGS_SHIP_URI = "file:///android_asset/sky_settings_airship.webp"

/** 画里七个字的中心（图比例，words10 定稿画实测）。 */
private val SETTINGS_SPOTS: Map<String, Offset> =
    mapOf(
        "app" to Offset(0.545f, 0.150f),
        "lab" to Offset(0.178f, 0.330f),
        "network" to Offset(0.865f, 0.330f),
        "override" to Offset(0.520f, 0.398f),
        "meta" to Offset(0.545f, 0.508f),
        "about" to Offset(0.125f, 0.545f),
        "log" to Offset(0.475f, 0.662f),
    )

/** 热区尺寸：罩住画里的字并留足手指余量。 */
private val SETTINGS_HOTSPOT_SIZE: Map<String, Pair<Dp, Dp>> =
    mapOf(
        "app" to (180.dp to 88.dp),
        "lab" to (118.dp to 158.dp),
        "network" to (118.dp to 158.dp),
        "override" to (170.dp to 84.dp),
        "meta" to (196.dp to 66.dp),
        "about" to (150.dp to 110.dp),
        "log" to (140.dp to 86.dp),
    )

/** 浮现次序（与 modules 默认顺序一致的错峰）。 */
private val SETTINGS_BLOOM_ORDER: Map<String, Int> =
    mapOf(
        "app" to 0,
        "network" to 1,
        "override" to 2,
        "meta" to 3,
        "lab" to 4,
        "log" to 5,
        "about" to 6,
    )

private fun settingsSub(module: SettingsModule): AnnotatedString =
    buildAnnotatedString {
        append(module.summary)
        module.badge?.let { badge ->
            append(" · ")
            withStyle(SpanStyle(color = SkyBadgeGold)) { append(badge) }
        }
    }

private fun settingsDesc(module: SettingsModule): String =
    listOfNotNull(module.title, module.summary, module.badge).joinToString("，")

private data class ShipLamp(
    val group: String?,
    val fx: Float,
    val fy: Float,
    val rFrac: Float,
    val p1: Float,
    val p2: Float,
    val seed: Float,
    val peak: Float,
)

/** 画里的灯（图比例中心）：两盏大灯分属 lab / network，甲板小灯属 meta，远岛灯属 about。 */
private val SHIP_LAMPS: List<ShipLamp> =
    listOf(
        ShipLamp("lab", 0.178f, 0.330f, 0.052f, 4f, 9f, 0.13f, 0.30f),
        ShipLamp("network", 0.865f, 0.330f, 0.052f, 5f, 11f, 0.47f, 0.30f),
        ShipLamp(null, 0.414f, 0.262f, 0.026f, 3f, 8f, 0.71f, 0.24f),
        ShipLamp(null, 0.154f, 0.287f, 0.020f, 6f, 13f, 0.29f, 0.20f),
        ShipLamp(null, 0.454f, 0.306f, 0.020f, 4f, 10f, 0.83f, 0.20f),
        ShipLamp(null, 0.719f, 0.316f, 0.020f, 5f, 12f, 0.59f, 0.20f),
        ShipLamp(null, 0.905f, 0.267f, 0.020f, 3f, 9f, 0.97f, 0.20f),
        ShipLamp("meta", 0.471f, 0.489f, 0.016f, 6f, 14f, 0.37f, 0.18f),
        ShipLamp("meta", 0.524f, 0.489f, 0.016f, 7f, 15f, 0.61f, 0.18f),
        ShipLamp("about", 0.134f, 0.519f, 0.014f, 8f, 16f, 0.43f, 0.16f),
    )

private val SHIP_STARS: List<Offset> =
    listOf(
        Offset(0.05f, 0.04f), Offset(0.93f, 0.03f), Offset(0.97f, 0.12f), Offset(0.03f, 0.16f),
        Offset(0.06f, 0.30f), Offset(0.96f, 0.22f), Offset(0.90f, 0.06f), Offset(0.10f, 0.06f),
        Offset(0.02f, 0.45f), Offset(0.98f, 0.42f),
    )

/**
 * 设置页（飞艇之境）环境生命层：整幅画只叠光与雾——气球内光、灯火呼吸、字的浮现光晕、
 * 瀑布微光、雾流与星光。时钟只在 draw 阶段读；灯亮度随连接态 lively 与按压 boost 联动。
 */
@Composable
private fun SkyShipAmbient(
    phase: () -> Float,
    lively: Float,
    boostKey: String?,
    boost: () -> Float,
    bloom: () -> Float,
    mistOnly: Boolean,
) {
    val warm = Color(0xFFFFC27A)
    Canvas(Modifier.fillMaxSize()) {
        val t = phase()
        val w = size.width
        val h = size.height
        // 雾流三团（兜底时也只剩它们）
        val bandY = listOf(0.60f, 0.76f, 0.90f)
        for ((k, fy) in bandY.withIndex()) {
            val drift = sin(2.0 * PI * (t / 120f + k * 0.33f)).toFloat() * 0.03f
            val c = Offset((0.5f + drift) * w, fy * h)
            val r = w * 0.85f
            withTransform({ scale(1f, 0.15f, pivot = c) }) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
                        center = c,
                        radius = r,
                    ),
                    r,
                    c,
                )
            }
        }
        if (mistOnly) return@Canvas
        val lampMul = 0.78f + 0.22f * lively
        val boostV = boost()
        val bloomV = bloom()
        // 气球内光：船的心脏，慢呼吸
        run {
            val breathe = 0.5f + 0.5f * sin(2.0 * PI * (t / 9f)).toFloat()
            val a = (0.045f + 0.025f * breathe) * lampMul
            val c = Offset(0.52f * w, 0.315f * h)
            drawCircle(
                Brush.radialGradient(listOf(warm.copy(alpha = a), Color.Transparent), center = c, radius = w * 0.34f),
                w * 0.34f,
                c,
                blendMode = BlendMode.Plus,
            )
        }
        // 灯火
        for (lamp in SHIP_LAMPS) {
            val s1 = 0.5f + 0.5f * sin(2.0 * PI * (t / lamp.p1 + lamp.seed)).toFloat()
            val s2 = 0.5f + 0.5f * sin(2.0 * PI * (t / lamp.p2 + lamp.seed * 1.7f)).toFloat()
            var a = lamp.peak * lampMul * (0.55f + 0.45f * s1) * (0.6f + 0.4f * s2)
            if (boostKey != null && boostKey == lamp.group) a += 0.38f * boostV
            val c = Offset(lamp.fx * w, lamp.fy * h)
            val r = lamp.rFrac * w * (if (boostKey == lamp.group) 1f + 0.3f * boostV else 1f)
            drawCircle(
                Brush.radialGradient(
                    listOf(warm.copy(alpha = a.coerceIn(0f, 0.85f)), Color.Transparent),
                    center = c,
                    radius = r * 2.2f,
                ),
                r * 2.2f,
                c,
                blendMode = BlendMode.Plus,
            )
        }
        // 字的浮现光晕：随 bloom 依次亮起；按压时该字的光涨起
        for ((key, spot) in SETTINGS_SPOTS) {
            val order = SETTINGS_BLOOM_ORDER[key] ?: 0
            val local = (bloomV * 1.6f - order * 0.09f).coerceIn(0f, 1f)
            var a = 0.15f * local * (0.7f + 0.3f * lively)
            var rMul = 1f
            if (boostKey == key) {
                a += 0.30f * boostV
                rMul = 1f + 0.3f * boostV
            }
            if (a <= 0.003f) continue
            val c = Offset(spot.x * w, spot.y * h)
            val r = w * 0.105f * rMul
            withTransform({ scale(1f, 0.62f, pivot = c) }) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(warm.copy(alpha = a.coerceIn(0f, 0.8f)), Color.Transparent),
                        center = c,
                        radius = r,
                    ),
                    r,
                    c,
                    blendMode = BlendMode.Plus,
                )
            }
        }
        // 瀑布微光：一条会流动的凉光
        run {
            val pulse = 0.5f + 0.5f * sin(2.0 * PI * (t / 3.7f)).toFloat()
            val c = Offset(0.795f * w, 0.845f * h)
            val r = w * 0.17f
            withTransform({ scale(0.34f, 1f, pivot = c) }) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFFBFE0FF).copy(alpha = 0.04f + 0.035f * pulse), Color.Transparent),
                        center = c,
                        radius = r,
                    ),
                    r,
                    c,
                    blendMode = BlendMode.Plus,
                )
            }
            val fall = ((t * 0.03f) % 0.09f)
            val c2 = Offset(0.795f * w, (0.775f + fall) * h)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
                    center = c2,
                    radius = w * 0.05f,
                ),
                w * 0.05f,
                c2,
                blendMode = BlendMode.Plus,
            )
        }
        // 星光（只落在画的天空空隙里）
        for ((i, s) in SHIP_STARS.withIndex()) {
            val tw = 0.3f + 0.5f * (0.5f + 0.5f * sin(2.0 * PI * (t / 5f + i * 0.29f)).toFloat())
            drawCircle(Color.White.copy(alpha = tw), 1.1f, Offset(s.x * w, s.y * h))
        }
    }
}


/** 动漫风背景：波奇酱粉纵向渐变。 */
private val animeBackgroundBrush = Brush.verticalGradient(
    listOf(Color(0xFFFF7BAC), Color(0xFFFFB9D4), Color(0xFFFFE7F0)),
)

/** 动漫风玻璃卡片上的深色文字。 */
private val animeOnGlass = Color(0xFF4A1E2C)

/** 波奇酱磁吸模块卡片：白色 + 粉/蓝细描边，小字号。 */
@Composable
private fun AnimeModuleCard(
    module: SettingsModule,
    isDragging: Boolean,
    onClick: () -> Unit,
) {
    val cardShape = RoundedCornerShape(20.dp)
    val dark = bocchiDark()
    Box(
        modifier = Modifier
            .horizontalPadding()
            .then(if (isDragging) Modifier.shadow(12.dp, cardShape) else Modifier)
            .clip(cardShape)
            .background(if (dark) MiuixTheme.colorScheme.surfaceContainer else Color.White)
            .border(1.dp, BocchiPink.copy(alpha = if (dark) 0.3f else 0.45f), cardShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModuleIcon(icon = module.icon, key = module.key)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = bocchiTitleColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (module.badge != null) "${module.summary} · ${module.badge}" else module.summary,
                    fontSize = 11.sp,
                    color = bocchiSummaryColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                tint = bocchiSummaryColor(),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 简约风磁吸模块卡片：Material tonal 卡片 + 小单色图标，行高收紧。 */
@Composable
private fun MaterialModuleCard(
    module: SettingsModule,
    isDragging: Boolean,
    onClick: () -> Unit,
) {
    val cardShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .horizontalPadding()
            .then(if (isDragging) Modifier.shadow(8.dp, cardShape) else Modifier)
            .clip(cardShape)
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = module.icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.title,
                    style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.Medium),
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (module.badge != null) "${module.summary} · ${module.badge}" else module.summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun ModuleIcon(icon: ImageVector, key: String) {
    val componentSizes = AppTheme.sizes
    val radii = AppTheme.radii
    // 图标圆底色按粉蓝绿轮换
    val bg = when (abs(key.hashCode()) % 3) {
        0 -> BocchiPinkBg
        1 -> BocchiBlueBg
        else -> Color(0xFFE3F5E9)
    }
    val fg = when (abs(key.hashCode()) % 3) {
        0 -> BocchiStar
        1 -> BocchiBlueFg
        else -> Color(0xFF43A047)
    }
    Box(
        modifier = Modifier
            .size(componentSizes.settingsIconContainerSize)
            .clip(RoundedCornerShape(radii.radius16))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        val customModuleIcon = rememberCustomIconBitmap("module_$key")
        if (customModuleIcon != null) {
            Image(
                bitmap = customModuleIcon,
                contentDescription = null,
                modifier = Modifier.size(componentSizes.settingsIconContainerSize),
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(componentSizes.settingsIconGlyphSize),
            )
        }
    }
}

private fun loadModuleOrder(mmkv: MMKV): List<SettingsModule> {
    val defaults = defaultModules()
    val saved = mmkv.decodeString(MMKV_KEY_MODULE_ORDER, "").orEmpty()
    if (saved.isBlank()) return defaults
    val order = saved.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val byKey = defaults.associateBy { it.key }
    val ordered = order.mapNotNull { byKey[it] }
    val missing = defaults.filter { d -> d.key !in order }
    val result = (ordered + missing).distinctBy { it.key }
    // 脏数据清理：如果存档里有重复 key，回写一份干净的
    if (order.size != order.distinct().size) {
        saveModuleOrder(mmkv, result)
    }
    return result
}

private fun saveModuleOrder(mmkv: MMKV, modules: List<SettingsModule>) {
    mmkv.encode(MMKV_KEY_MODULE_ORDER, modules.distinctBy { it.key }.joinToString(",") { it.key })
}

private const val MMKV_KEY_MODULE_HIDDEN = "settings_module_hidden"

/** 加载被隐藏的模块 key 集合。 */
private fun loadHiddenModules(mmkv: MMKV): Set<String> {
    return mmkv.decodeString(MMKV_KEY_MODULE_HIDDEN, "").orEmpty()
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}
