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
 * 设置页 · 天空版：巨树镜头 Header（图比例取景，光字「设置」）+ 雾海光字清单。
 * 行数据与跳转沿用 [SettingPager] 原有 modules / openSecondary，行为不变。
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
    val labelsIn = remember { Animatable(0f) }
    LaunchedEffect(imgReady, fallback) {
        if (imgReady || fallback) labelsIn.animateTo(1f, tween(300))
    }
    var pressedKey by remember { mutableStateOf<String?>(null) }
    val lampBoost = remember { Animatable(0f) }
    LaunchedEffect(pressedKey) {
        if (pressedKey != null) {
            lampBoost.animateTo(1f, tween(600))
        } else {
            lampBoost.animateTo(0f, tween(300))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val map =
            remember(w, h) {
                SkyShotMap(w.toFloat(), h.toFloat(), w.toFloat(), h.toFloat(), 1f, 0.5f, 0.5f, aspect = 1040f / 2352f)
            }
        if (!fallback) {
            SkyBackdropFull(
                map = map,
                assetUri = SKY_SETTINGS_TREE_URI,
                sat = sat,
                dusk = dusk,
                onReadyChange = { imgReady = it },
            )
            SkyTreeAmbient(
                phase = { clock.floatValue },
                lively = lively,
                boostKey = pressedKey,
                boost = { lampBoost.value },
                mistOnly = false,
            )
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SkyNightBase, SkyDeepBase))))
            SkyTreeAmbient(
                phase = { clock.floatValue },
                lively = lively,
                boostKey = null,
                boost = { 0f },
                mistOnly = true,
            )
        }
        if (imgReady || fallback) {
            // 点画任意处：题字重新浮现（同首页题字时钟）
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) { showTick++ },
            )
            // 页题（heading，底值 0.60）
            SkyGlowLabel(
                line1 = FlyTxt.Settings.Title,
                line1Size = 17.sp,
                modifier =
                    Modifier.skyCenteredAt(map.at(Offset(0.50f, 0.057f))) { 0.60f * labelsIn.value * caption.value },
            )
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
            modules.forEachIndexed { index, module ->
                val spot = SETTINGS_SPOTS[module.key] ?: return@forEachIndexed
                val center =
                    if (fallback) {
                        Offset(w / 2f, h * (0.20f + index * 0.095f))
                    } else {
                        map.at(spot)
                    }
                SkyLabelEntry(
                    center = center,
                    title = module.title,
                    sub = settingsSub(module),
                    index = index,
                    caption = { caption.value },
                    phase = { clock.floatValue },
                    parentSize = IntSize(w, h),
                    contentDescription = settingsDesc(module),
                    active = active,
                    baseAlpha = 0f,
                    mistAlpha = if (module.key == "app" || module.key == "meta") 0.45f else 0.34f,
                    onPressedChange = { p ->
                        if (p) showTick++
                        pressedKey = if (p) module.key else if (pressedKey == module.key) null else pressedKey
                    },
                    onClick = { module.route?.let { onOpen(it) } },
                )
            }
        }
    }
}

private const val SKY_SETTINGS_TREE_URI = "file:///android_asset/sky_settings_tree.webp"

/** 设置页七个题字在树图上的位置（图比例）与所挂的灯组。 */
private val SETTINGS_SPOTS: Map<String, Offset> =
    mapOf(
        "app" to Offset(0.145f, 0.310f),
        "network" to Offset(0.815f, 0.128f),
        "override" to Offset(0.235f, 0.640f),
        "meta" to Offset(0.845f, 0.465f),
        "lab" to Offset(0.135f, 0.680f),
        "log" to Offset(0.470f, 0.838f),
        "about" to Offset(0.835f, 0.848f),
    )

private val SETTINGS_LAMP_GROUP: Map<String, String> =
    mapOf(
        "app" to "left",
        "network" to "canopy",
        "override" to "swing",
        "meta" to "right",
        "lab" to "stoneL",
        "log" to "stoneB",
        "about" to "candle",
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

private data class TreeLamp(
    val group: String,
    val fx: Float,
    val fy: Float,
    val rFrac: Float,
    val p1: Float,
    val p2: Float,
    val depth: Float,
    val seed: Float,
    val peak: Float,
)

private val TREE_LAMPS: List<TreeLamp> =
    listOf(
        // 左枝灯笼群（静谧版实测位置）
        TreeLamp("left", 0.150f, 0.272f, 0.020f, 3f, 8f, 0.22f, 0.37f, 0.25f),
        TreeLamp("left", 0.190f, 0.282f, 0.022f, 4f, 10f, 0.18f, 0.11f, 0.27f),
        TreeLamp("left", 0.115f, 0.295f, 0.016f, 5f, 12f, 0.15f, 0.53f, 0.22f),
        TreeLamp("left", 0.228f, 0.308f, 0.015f, 6f, 15f, 0.12f, 0.71f, 0.20f),
        TreeLamp("left", 0.262f, 0.295f, 0.013f, 4f, 9f, 0.20f, 0.83f, 0.19f),
        TreeLamp("left", 0.095f, 0.315f, 0.013f, 3f, 11f, 0.21f, 0.29f, 0.19f),
        // 右侧垂灯（疏）
        TreeLamp("right", 0.848f, 0.207f, 0.017f, 6f, 12f, 0.15f, 0.19f, 0.24f),
        TreeLamp("right", 0.852f, 0.362f, 0.016f, 8f, 15f, 0.13f, 0.43f, 0.22f),
        TreeLamp("right", 0.898f, 0.382f, 0.014f, 7f, 11f, 0.16f, 0.67f, 0.20f),
        TreeLamp("right", 0.742f, 0.218f, 0.012f, 5f, 10f, 0.17f, 0.59f, 0.18f),
        // 秋千旁小灯
        TreeLamp("swing", 0.345f, 0.452f, 0.013f, 5f, 12f, 0.15f, 0.79f, 0.19f),
        // 石灯（更暗更稳）
        TreeLamp("stoneL", 0.022f, 0.688f, 0.010f, 6f, 15f, 0.08f, 0.31f, 0.16f),
        TreeLamp("stoneL", 0.095f, 0.792f, 0.013f, 6f, 15f, 0.08f, 0.47f, 0.17f),
        TreeLamp("stoneB", 0.472f, 0.902f, 0.014f, 8f, 12f, 0.08f, 0.57f, 0.18f),
        // 右下小烛
        TreeLamp("candle", 0.742f, 0.712f, 0.009f, 3f, 8f, 0.22f, 0.89f, 0.17f),
        TreeLamp("candle", 0.930f, 0.718f, 0.010f, 3f, 8f, 0.22f, 0.73f, 0.18f),
    )

/** 萤火（图比例中心；轨迹为小幅 Lissajous 漂移）。 */
private val TREE_FIREFLIES: List<Offset> =
    listOf(
        Offset(0.30f, 0.52f), Offset(0.42f, 0.60f), Offset(0.60f, 0.52f), Offset(0.68f, 0.66f),
        Offset(0.36f, 0.70f), Offset(0.55f, 0.74f), Offset(0.80f, 0.66f), Offset(0.16f, 0.86f),
        Offset(0.30f, 0.90f), Offset(0.62f, 0.86f), Offset(0.72f, 0.94f), Offset(0.44f, 0.95f),
        Offset(0.90f, 0.78f), Offset(0.08f, 0.55f),
    )

private val TREE_SPARKLES: List<Offset> =
    listOf(
        Offset(0.20f, 0.12f), Offset(0.35f, 0.08f), Offset(0.50f, 0.16f), Offset(0.65f, 0.10f),
        Offset(0.78f, 0.20f), Offset(0.28f, 0.24f), Offset(0.58f, 0.26f), Offset(0.88f, 0.32f),
        Offset(0.12f, 0.20f), Offset(0.45f, 0.28f),
    )

private val TREE_STARS: List<Offset> =
    listOf(
        Offset(0.06f, 0.03f), Offset(0.20f, 0.05f), Offset(0.38f, 0.02f), Offset(0.55f, 0.04f),
        Offset(0.70f, 0.03f), Offset(0.85f, 0.05f), Offset(0.95f, 0.02f), Offset(0.30f, 0.09f),
        Offset(0.62f, 0.08f), Offset(0.78f, 0.09f), Offset(0.12f, 0.10f), Offset(0.48f, 0.10f),
    )

/**
 * 设置页（巨树之境）环境生命层：灯火呼吸、萤火、树冠微光、星与雾流。
 * 时钟只在 draw 阶段读；灯亮度随连接态 lively 与按压 boost 联动。
 */
@Composable
private fun SkyTreeAmbient(
    phase: () -> Float,
    lively: Float,
    boostKey: String?,
    boost: () -> Float,
    mistOnly: Boolean,
) {
    val warm = Color(0xFFFFC27A)
    Canvas(Modifier.fillMaxSize()) {
        val t = phase()
        val w = size.width
        val h = size.height
        // 雾流三团（兜底时也只剩它们）
        val bandY = listOf(0.62f, 0.78f, 0.92f)
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
        val boostGroup = boostKey?.let { SETTINGS_LAMP_GROUP[it] }
        val boostV = boost()
        for (lamp in TREE_LAMPS) {
            val s1 = 0.5f + 0.5f * sin(2.0 * PI * (t / lamp.p1 + lamp.seed)).toFloat()
            val s2 = 0.5f + 0.5f * sin(2.0 * PI * (t / lamp.p2 + lamp.seed * 1.7f)).toFloat()
            var a = lamp.peak * lampMul * (1f - lamp.depth * (0.4f + 0.6f * s1) * (0.5f + 0.5f * s2))
            // 左枝群里有一盏每 60s 熄 4s
            if (lamp.group == "left" && lamp.seed == 0.53f && (t % 60f) in 30f..34f) a *= 0.15f
            if (boostGroup == lamp.group) a += 0.35f * boostV
            val c = Offset(lamp.fx * w, lamp.fy * h)
            val r = lamp.rFrac * w
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
        // 萤火
        for ((i, base) in TREE_FIREFLIES.withIndex()) {
            val p = listOf(20f, 24f, 30f, 40f, 60f, 120f)[i % 6]
            val kx = (i % 3) + 1
            val ky = ((i + 1) % 3) + 1
            val x = base.x * w + 0.008f * w * sin(2.0 * PI * (kx * t / p + i * 0.37f)).toFloat()
            val y = base.y * h + 0.006f * h * sin(2.0 * PI * (ky * t / p + i * 0.53f)).toFloat()
            val tw = 0.15f + 0.60f * (0.5f + 0.5f * sin(2.0 * PI * (t / listOf(3f, 4f, 5f, 6f)[i % 4] + i * 0.61f)).toFloat()).pow(2)
            val col = if (i % 5 == 4) Color.White else Color(0xFFD8F2A0)
            val c = Offset(x, y)
            drawCircle(col.copy(alpha = tw * 0.16f), 2.8.dp.toPx(), c, blendMode = BlendMode.Plus)
            drawCircle(col.copy(alpha = tw * 0.9f), 1.2.dp.toPx(), c)
        }
        // 树冠微光与星
        for ((i, s) in TREE_SPARKLES.withIndex()) {
            val per = listOf(6f, 8f, 10f, 12f, 15f, 20f)[i % 6]
            val tw = 0.5f + 0.5f * sin(2.0 * PI * (t / per + i * 0.43f)).toFloat()
            val c = Offset(s.x * w, s.y * h)
            drawCircle(SkyLabelGlow.copy(alpha = tw * 0.30f), 3.4f, c, blendMode = BlendMode.Plus)
            drawCircle(Color.White.copy(alpha = tw * 0.8f), 1.2f, c)
        }
        for ((i, s) in TREE_STARS.withIndex()) {
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
