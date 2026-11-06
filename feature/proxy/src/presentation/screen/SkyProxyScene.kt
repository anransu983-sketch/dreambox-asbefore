package com.suanran.dreambox.feature.proxy.presentation.screen

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.suanran.dreambox.core.model.proxy.Proxy
import com.suanran.dreambox.core.model.proxy.ProxyGroupInfo
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.navigation.BottomBarDestination
import com.suanran.dreambox.presentation.component.navigation.LocalHandlePageChange
import com.suanran.dreambox.presentation.component.navigation.LocalMainPagerState
import com.suanran.dreambox.presentation.component.sky.SkyBackdropFull
import com.suanran.dreambox.presentation.component.sky.SkyBadgeGold
import com.suanran.dreambox.presentation.component.sky.SkyDeepBase
import com.suanran.dreambox.presentation.component.sky.SkyLabelCore
import com.suanran.dreambox.presentation.component.sky.SkyLabelEntry
import com.suanran.dreambox.presentation.component.sky.SkyNightBase
import com.suanran.dreambox.presentation.component.sky.SkyShotMap
import com.suanran.dreambox.presentation.component.sky.SkyStateOff
import com.suanran.dreambox.presentation.component.sky.SkyStatusBarIcons
import com.suanran.dreambox.presentation.component.sky.rememberSkyCaption
import com.suanran.dreambox.presentation.component.sky.rememberSkyClock
import com.suanran.dreambox.presentation.component.sky.skyLatencyColor
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val SKY_PROXY_URI = "file:///android_asset/sky_proxy_harbor.webp"

/** 五盏系泊灯的横坐标（图比例，定稿画实测）。 */
private val LANTERN_X = listOf(0.341f, 0.488f, 0.624f, 0.748f, 0.846f)
private const val LANTERN_HEAD_Y = 0.337f
private const val LANTERN_NAME_Y = 0.449f

private data class Bubble(val npc: HarborNpc, val text: String, val id: Int)

/**
 * 代理页 · 天空版：飞艇港。五盏系泊灯是代理组（灯色随该组当前节点延迟），
 * 点灯进组，节点是下层甲板上的光行；塔上「测速」牌点按测速。
 * 港里五个人各有活干：提灯人巡甲板、扛货人走楼梯、盘绳人守货栈、瞭望手在塔上、
 * 船夫在船里；点他们会说话，台词由 HarborTalk 用真实状态现组。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SkyProxyScene(
    isActive: Boolean,
    groups: List<ProxyGroupInfo>,
    selectedGroup: ProxyGroupInfo?,
    testingGroupNames: Set<String>,
    testingProxyNames: Set<String>,
    talkState: HarborTalkState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectGroup: (ProxyGroupInfo) -> Unit,
    onBackToGroups: () -> Unit,
    onSelectProxy: (String, String) -> Unit,
    onForceSelectProxy: (String, String) -> Unit,
    onTestAll: () -> Unit,
    onTestGroup: (String) -> Unit,
    onNavigateToProviders: (() -> Unit)?,
    searchVisible: Boolean,
    onSearchVisibleChange: (Boolean) -> Unit,
) {
    val pager = LocalMainPagerState.current
    val active = isActive && pager.selectedPage == BottomBarDestination.Proxy.ordinal
    SkyStatusBarIcons(active)
    val handlePageChange = LocalHandlePageChange.current
    val ctx = LocalContext.current
    val reduceMotion =
        remember {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
    val clock = rememberSkyClock(active, reduceMotion)
    var showTick by remember { mutableIntStateOf(0) }
    val caption = rememberSkyCaption(active, showTick)
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

    val talker = remember { HarborTalker() }
    var bubble by remember { mutableStateOf<Bubble?>(null) }
    var bubbleSeq by remember { mutableIntStateOf(0) }
    fun say(npc: HarborNpc, text: String) {
        bubbleSeq++
        bubble = Bubble(npc, text, bubbleSeq)
    }
    fun talkNow(): HarborTalkState =
        talkState.copy(
            hour = LocalTime.now().hour,
            testing = testingGroupNames.isNotEmpty(),
            groupName = selectedGroup?.name ?: talkState.groupName,
            groupCount = groups.size,
            nodeCount = selectedGroup?.proxies?.size ?: talkState.nodeCount,
        )
    // 对答是长驻协程，抓的是启动那一刻的状态会过时；这份永远读最新值
    val latestTalkState = rememberUpdatedState(talkState)
    val latestSelectedGroup = rememberUpdatedState(selectedGroup)
    val latestTesting = rememberUpdatedState(testingGroupNames)
    val latestGroups = rememberUpdatedState(groups)
    fun freshTalk(): HarborTalkState =
        latestTalkState.value.copy(
            hour = LocalTime.now().hour,
            testing = latestTesting.value.isNotEmpty(),
            groupName = latestSelectedGroup.value?.name ?: latestTalkState.value.groupName,
            groupCount = latestGroups.value.size,
            nodeCount = latestSelectedGroup.value?.proxies?.size ?: latestTalkState.value.nodeCount,
        )
    LaunchedEffect(bubble?.id) {
        val b = bubble ?: return@LaunchedEffect
        delay(4600)
        if (bubble?.id == b.id) bubble = null
    }
    // 换套 UI（她令）：组名不做标签贴灯。点灯＝问名：瞭望手出声报幕，组名同时被灯光
    // 投在甲板前的云海上，几秒后被云气冲散；进港时瞭望手报当前组一次。
    var projectedName by remember { mutableStateOf<String?>(null) }
    var projectionSeq by remember { mutableIntStateOf(0) }
    fun announceGroup(name: String) {
        say(HarborNpc.Lookout, name)
        projectionSeq++
        projectedName = name
    }
    LaunchedEffect(projectionSeq) {
        if (projectionSeq > 0) {
            delay(3700)
            projectedName = null
        }
    }
    var entryAnnounced by remember { mutableStateOf(false) }
    LaunchedEffect(selectedGroup?.name, groups.size, active) {
        if (selectedGroup != null) {
            entryAnnounced = false
        } else if (active && !fallback && !entryAnnounced && groups.isNotEmpty()) {
            delay(800)
            val cur =
                groups.firstOrNull { it.now != null && it.now == latestTalkState.value.nodeName }?.name
                    ?: groups.firstOrNull { it.name == "Proxy" }?.name
                    ?: groups.firstOrNull()?.name
            if (cur != null) {
                announceGroup(cur)
                entryAnnounced = true
            }
        }
    }
    // 碰头对答：页面活跃且在组视图时，每隔一阵随机挑一对邻人聊两句
    LaunchedEffect(active, selectedGroup?.name, fallback) {
        if (!active || selectedGroup != null || fallback) return@LaunchedEffect
        delay(9000)
        val pairs =
            listOf(
                HarborNpc.Lantern to HarborNpc.Lookout,
                HarborNpc.Crate to HarborNpc.Rope,
                HarborNpc.Rope to HarborNpc.Boat,
                HarborNpc.Lantern to HarborNpc.Crate,
                HarborNpc.Lookout to HarborNpc.Boat,
            )
        var idx = kotlin.random.Random.nextInt(pairs.size)
        while (true) {
            val (a, b) = pairs[idx % pairs.size]
            idx++
            if (bubble == null) {
                val (first, second) = talker.chat(a, b, freshTalk())
                say(first.first, first.second)
                delay(2600)
                say(second.first, second.second)
            }
            delay(24000)
        }
    }

    val pageCount = maxOf(1, (groups.size + LANTERN_X.size - 1) / LANTERN_X.size)
    var lanternPage by remember { mutableIntStateOf(0) }
    LaunchedEffect(pageCount) { if (lanternPage >= pageCount) lanternPage = pageCount - 1 }
    val pageGroups = groups.drop(lanternPage * LANTERN_X.size).take(LANTERN_X.size)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val map =
            remember(w, h) {
                SkyShotMap(w.toFloat(), h.toFloat(), w.toFloat(), h.toFloat(), 1f, 0.5f, 0.5f, aspect = 1120f / 2240f)
            }
        if (!fallback) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val t = clock.floatValue
                    translationX = sin(2.0 * PI * (t / 12f)).toFloat() * size.width * 0.0035f
                    translationY = sin(2.0 * PI * (t / 8f)).toFloat() * size.height * 0.0055f
                    rotationZ = sin(2.0 * PI * (t / 10f)).toFloat() * 0.3f
                    val breathe = 1.045f + 0.008f * sin(2.0 * PI * (t / 15f)).toFloat()
                    scaleX = breathe
                    scaleY = breathe
                },
            ) {
                SkyBackdropFull(
                    map = map,
                    assetUri = SKY_PROXY_URI,
                    sat = 1f,
                    dusk = 0f,
                    onReadyChange = { imgReady = it },
                )
                SkyHarborAmbient(
                    map = map,
                    phase = { clock.floatValue },
                    lanternTiers = pageGroups.map { g -> g.proxies.firstOrNull { it.name == g.now }?.delay ?: -1 },
                    testing = testingGroupNames.isNotEmpty(),
                    nodeView = selectedGroup != null,
                )
                HarborNpcLayer(map = map, clock = clock)
            }
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SkyNightBase, SkyDeepBase))))
        }

        if (imgReady || fallback) {
            if (!fallback && selectedGroup == null) {
                // 提灯人巡逻带（上层甲板）：点到他就说话
                NpcTouchZone(
                    center = map.at(Offset(0.585f, 0.404f)),
                    wDp = 340.dp,
                    hDp = 62.dp,
                    index = 20,
                    desc = HarborNpc.Lantern.label,
                ) { say(HarborNpc.Lantern, talker.speak(HarborNpc.Lantern, talkNow())) }
                // 扛货人楼梯带
                NpcTouchZone(
                    center = map.at(Offset(0.365f, 0.633f)),
                    wDp = 200.dp,
                    hDp = 150.dp,
                    index = 21,
                    desc = HarborNpc.Crate.label,
                ) { say(HarborNpc.Crate, talker.speak(HarborNpc.Crate, talkNow())) }
                // 盘绳人
                NpcTouchZone(
                    center = map.at(Offset(0.618f, 0.672f)),
                    wDp = 105.dp,
                    hDp = 105.dp,
                    index = 22,
                    desc = HarborNpc.Rope.label,
                ) { say(HarborNpc.Rope, talker.speak(HarborNpc.Rope, talkNow())) }
                // 瞭望手（塔楼阳台，与测速牌分开）
                NpcTouchZone(
                    center = map.at(Offset(0.207f, 0.258f)),
                    wDp = 95.dp,
                    hDp = 85.dp,
                    index = 23,
                    desc = HarborNpc.Lookout.label,
                ) { say(HarborNpc.Lookout, talker.speak(HarborNpc.Lookout, talkNow())) }
            }

            if (!fallback) {
                // 测速塔（画里的牌）：组视图测全部，节点视图测当前组
                ProxyHotspot(
                    center = map.at(Offset(0.198f, 0.352f)),
                    wDp = 130.dp,
                    hDp = 92.dp,
                    index = 4,
                    contentDescription = "测速",
                    onPressedChange = { p ->
                        pressedKey = if (p) "tower" else if (pressedKey == "tower") null else pressedKey
                    },
                    onClick = {
                        val g = selectedGroup
                        if (g == null) onTestAll() else onTestGroup(g.name)
                    },
                    onLongClick = {},
                )
            }

            if (selectedGroup == null) {
                // 组视图：五盏灯
                if (!fallback) {
                    pageGroups.forEachIndexed { index, group ->
                        val cx = LANTERN_X[index]
                        ProxyHotspot(
                            center = map.at(Offset(cx, LANTERN_HEAD_Y)),
                            wDp = 92.dp,
                            hDp = 118.dp,
                            index = index,
                            contentDescription = group.name,
                            onPressedChange = { p ->
                                pressedKey = if (p) "lan$index" else if (pressedKey == "lan$index") null else pressedKey
                            },
                            onClick = { announceGroup(group.name); onSelectGroup(group) },
                            onLongClick = { onTestGroup(group.name) },
                        )
                    }
                    if (groups.isEmpty()) {
                        GlowCenterText(center = map.at(Offset(0.5f, 0.56f)), text = FlyTxt.Proxy.Empty.NoNodes, size = 17)
                        GlowCenterText(center = map.at(Offset(0.5f, 0.60f)), text = FlyTxt.Proxy.Empty.Hint, size = 13)
                    }
                    if (pageCount > 1) {
                        if (lanternPage > 0) {
                            HarborChevron(center = map.at(Offset(0.035f, LANTERN_NAME_Y)), glyph = "‹", desc = "上一排灯") {
                                lanternPage--
                            }
                        }
                        if (lanternPage < pageCount - 1) {
                            HarborChevron(center = map.at(Offset(0.965f, LANTERN_NAME_Y)), glyph = "›", desc = "下一排灯") {
                                lanternPage++
                            }
                        }
                    }
                    if (onNavigateToProviders != null) {
                        ProxyHotspot(
                            center = map.at(Offset(0.778f, 0.845f)),
                            wDp = 175.dp,
                            hDp = 115.dp,
                            index = 6,
                            contentDescription = "提供商",
                            onPressedChange = { p ->
                                pressedKey = if (p) "boat" else if (pressedKey == "boat") null else pressedKey
                            },
                            onClick = { onNavigateToProviders() },
                            onLongClick = { say(HarborNpc.Boat, talker.speak(HarborNpc.Boat, talkNow())) },
                        )
                    }
                    SkyLabelEntry(
                        center = map.at(Offset(0.12f, 0.057f)),
                        title = "‹ " + BottomBarDestination.Home.label,
                        index = 12,
                        baseAlpha = 0f,
                        caption = { caption.value },
                        phase = { clock.floatValue },
                        parentSize = IntSize(w, h),
                        contentDescription = BottomBarDestination.Home.label,
                        active = active,
                        onClick = { handlePageChange(BottomBarDestination.Home.ordinal) },
                    )
                } else {
                    // 兜底：组名光字列
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize().padding(top = 110.dp),
                    ) {
                        groups.forEach { group ->
                            BasicText(
                                group.name,
                                style =
                                    TextStyle(
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SkyLabelCore,
                                        textAlign = TextAlign.Center,
                                    ),
                                modifier =
                                    Modifier.padding(vertical = 13.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { onSelectGroup(group) },
                            )
                        }
                        if (groups.isEmpty()) {
                            GlowCenterText(center = Offset(w / 2f, h * 0.4f), text = FlyTxt.Proxy.Empty.NoNodes, size = 17)
                        }
                    }
                }
            } else {
                // 节点视图：甲板上的光行
                val group = selectedGroup
                NodeDeckHeader(
                    map = map,
                    group = group,
                    searchVisible = searchVisible,
                    onBack = onBackToGroups,
                    onToggleSearch = { onSearchVisibleChange(!searchVisible) },
                )
                if (searchVisible) {
                    HarborSearchField(
                        map = map,
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                    )
                }
                val shown =
                    remember(group.name, group.proxies, searchQuery) {
                        if (searchQuery.isBlank()) {
                            group.proxies
                        } else {
                            group.proxies.filter { it.name.contains(searchQuery, ignoreCase = true) }
                        }
                    }
                LazyColumn(
                    modifier =
                        Modifier.fillMaxSize()
                            .padding(
                                top = with(androidx.compose.ui.platform.LocalDensity.current) { (h * if (searchVisible) 0.545f else 0.512f).toDp() },
                            ),
                ) {
                    items(items = shown, key = { it.name }) { proxy ->
                        NodeLightRow(
                            proxy = proxy,
                            selected = proxy.name == group.now,
                            testing = proxy.name in testingProxyNames || group.name in testingGroupNames,
                            phase = { clock.floatValue },
                            onClick = {
                                if (proxy.isGroup) {
                                    groups.firstOrNull { it.name == proxy.name }?.let { onSelectGroup(it) }
                                } else {
                                    onSelectProxy(group.name, proxy.name)
                                }
                            },
                            onLongClick = {
                                if (!proxy.isGroup) onForceSelectProxy(group.name, proxy.name)
                            },
                        )
                    }
                    if (shown.isEmpty()) {
                        item {
                            BasicText(
                                "没有匹配的节点",
                                style =
                                    TextStyle(
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 14.sp,
                                        color = SkyStateOff,
                                        textAlign = TextAlign.Center,
                                    ),
                                modifier = Modifier.fillMaxWidth().padding(top = 26.dp),
                            )
                        }
                    }
                }
            }

            // 云海投影：被点到的组名投在下层甲板前的云气上
            val pn = projectedName
            if (pn != null && !fallback) {
                HarborNameProjection(map = map, name = pn, seq = projectionSeq)
            }

            // 气泡：浮在说话人头顶（走动的人会带着气泡一起走）
            val b = bubble
            if (b != null && !fallback) {
                HarborBubble(text = b.text, centerAt = { map.at(npcAnchor(b.npc, clock.floatValue)) })
            }
        }
    }
}

/**
 * 组名投影：灯光把名字投到甲板前的云气上——大衬线字、微斜、略压扁像贴着云面，
 * 先在云上晕出一片光，再浮字，停一会儿被云气冲淡散去。名字只在被问时出现。
 */
@Composable
private fun HarborNameProjection(map: SkyShotMap, name: String, seq: Int) {
    val alphaAnim = remember(seq) { Animatable(0f) }
    LaunchedEffect(seq) {
        alphaAnim.animateTo(1f, tween(450))
        delay(2400)
        alphaAnim.animateTo(0f, tween(800))
    }
    val center = map.at(Offset(0.635f, 0.470f))
    Box(
        Modifier.offset { IntOffset((center.x - 170.dp.toPx()).roundToInt(), (center.y - 40.dp.toPx()).roundToInt()) }
            .width(340.dp)
            .height(80.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    scaleY = 0.55f
                    this.alpha = alphaAnim.value * 0.55f
                }
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFFBFE0FF).copy(alpha = 0.38f), Color.Transparent),
                    ),
                ),
        )
        BasicText(
            name,
            style =
                TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SkyLabelCore,
                    textAlign = TextAlign.Center,
                    shadow = Shadow(color = Color(0xFF8FC8FF).copy(alpha = 0.85f), blurRadius = 16f),
                ),
            maxLines = 1,
            modifier =
                Modifier.graphicsLayer {
                    rotationZ = -2.5f
                    scaleY = 0.88f
                    this.alpha = alphaAnim.value
                },
        )
    }
}

/** 五人此刻的脚下锚点（图比例）：提灯人巡逻、扛货人走楼梯，其余守位。 */
private fun npcAnchor(npc: HarborNpc, t: Float): Offset =
    when (npc) {
        HarborNpc.Lantern -> {
            val cyc = (t % 60f) / 60f
            val tri = if (cyc < 0.5f) cyc * 2f else 2f - cyc * 2f
            Offset(0.295f + 0.58f * tri, 0.404f + sin(PI * tri).toFloat() * 0.008f)
        }
        HarborNpc.Crate -> {
            val k = crateProgress(t)
            val s = k * 7f
            val i = floor(s)
            val f = s - i
            val kk = ((i + f * f * (3f - 2f * f)) / 7f).coerceIn(0f, 1f)
            Offset(0.475f + (0.262f - 0.475f) * kk, 0.720f + (0.550f - 0.720f) * kk)
        }
        HarborNpc.Rope -> Offset(0.618f, 0.690f)
        HarborNpc.Lookout -> Offset(0.207f, 0.270f)
        HarborNpc.Boat -> Offset(0.778f, 0.858f)
    }

private fun npcFacingLeft(npc: HarborNpc, t: Float): Boolean =
    when (npc) {
        HarborNpc.Lantern -> (t % 60f) / 60f >= 0.5f
        HarborNpc.Crate -> {
            val cyc = (t % 40f) / 40f
            cyc < 0.5f
        }
        else -> false
    }

/** 扛货人的行程进度（0..1）：两端各停一会儿，中途匀速。 */
private fun crateProgress(t: Float): Float {
    val cyc = (t % 40f) / 40f
    return when {
        cyc < 0.4f -> cyc / 0.4f
        cyc < 0.5f -> 1f
        cyc < 0.9f -> 1f - (cyc - 0.5f) / 0.4f
        else -> 0f
    }
}

/**
 * 行走步态：（向上的脚步起伏 图比例，身体摇摆角）。走路是抬脚-落地，不是平滑滑行：
 * 起伏取 |sin| 一步一落，摇摆随步频。只有走路的两人有步态，其余返回 null 沿用呼吸浮动。
 */
private fun npcStepMotion(npc: HarborNpc, t: Float): Pair<Float, Float>? =
    when (npc) {
        HarborNpc.Lantern -> {
            val cyc = (t % 60f) / 60f
            val tri = if (cyc < 0.5f) cyc * 2f else 2f - cyc * 2f
            val phase = tri * 30f
            val dir = if (cyc < 0.5f) 1f else -1f
            (abs(sin(PI * phase)).toFloat() * 0.0012f) to (sin(2.0 * PI * phase).toFloat() * 0.8f * dir)
        }
        HarborNpc.Crate -> {
            val k = crateProgress(t)
            if (k <= 0f || k >= 1f) {
                0f to 0f
            } else {
                val phase = k * 12f
                (abs(sin(PI * phase)).toFloat() * 0.0011f) to (sin(2.0 * PI * phase).toFloat() * 0.6f)
            }
        }
        else -> null
    }


/** 步态帧号：与 npcStepMotion 的相位同源，相位每过一个整数换一帧（那一刻正好落脚、起伏为零）。 */
private fun npcStepFrame(npc: HarborNpc, t: Float): Int =
    when (npc) {
        HarborNpc.Lantern -> {
            val cyc = (t % 60f) / 60f
            val tri = if (cyc < 0.5f) cyc * 2f else 2f - cyc * 2f
            floor(tri * 30f).toInt() % 2
        }
        HarborNpc.Crate -> {
            val k = crateProgress(t)
            if (k <= 0f || k >= 1f) 0 else floor(k * 12f).toInt() % 2
        }
        else -> 0
    }

/** NPC 画片层：五人按各自的路数动（走、扛、盘绳、瞭望、坐船）。时钟只在 offset/layer 阶段读，不触发重组。 */
@Composable
private fun HarborNpcLayer(map: SkyShotMap, clock: MutableFloatState) {
    @Composable
    fun sprite(npc: HarborNpc, uri: String, heightDp: Dp, bobAmp: Float, bobPeriod: Float, aspect: Float, uriB: String? = null, aspectB: Float = 0f) {
        // 脚下贴地影：独立钉在锚点（脚底）不随步态起伏——人是踩在甲板上的，影子先落地。
        // 船夫坐在云海小船里，船下是云，不画影。
        if (npc != HarborNpc.Boat) {
            Box(
                Modifier.offset {
                        val pos = map.at(npcAnchor(npc, clock.floatValue))
                        val w = heightDp.toPx() * 0.68f
                        val h = heightDp.toPx() * 0.15f
                        IntOffset((pos.x - w / 2f).roundToInt(), (pos.y - h * 0.6f).roundToInt())
                    }
                    .size(heightDp * 0.68f, heightDp * 0.15f)
                    .drawBehind {
                        drawOval(
                            brush =
                                Brush.radialGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                                    center = center,
                                    radius = size.width / 2f,
                                ),
                            topLeft = Offset.Zero,
                            size = size,
                        )
                    },
            )
        }
        // 两帧步态：走路的两人有 A/B 两张姿势画片，按步频相位切换，相位整数点换帧（那一刻正好落脚）。
        // 居中按每张画片的真实宽高比算，脚底中心对准锚点——旧写法按正方形估，脚和脚下影子错位一截，人像贴纸。
        @Composable
        fun frame(uriX: String, aspectX: Float, frameIdx: Int) {
            HarborSpriteImage(
                assetUri = uriX,
                modifier =
                    Modifier.offset {
                            val tt = clock.floatValue
                            val anchor = npcAnchor(npc, tt)
                            val step = npcStepMotion(npc, tt)
                            val yOff =
                                step?.let { -it.first }
                                    ?: (sin(2.0 * PI * (tt / bobPeriod)).toFloat() * bobAmp)
                            val pos = map.at(Offset(anchor.x, anchor.y + yOff))
                            IntOffset(
                                (pos.x - heightDp.toPx() * aspectX / 2f).roundToInt(),
                                (pos.y - heightDp.toPx()).roundToInt(),
                            )
                        }
                        .height(heightDp)
                        .graphicsLayer {
                            val tt = clock.floatValue
                            scaleX = if (npcFacingLeft(npc, tt)) -1f else 1f
                            rotationZ =
                                (npcStepMotion(npc, tt)?.second ?: 0f) +
                                    when (npc) {
                                        HarborNpc.Rope -> sin(2.0 * PI * (tt / 2.5f)).toFloat() * 1.4f
                                        HarborNpc.Boat -> sin(2.0 * PI * (tt / 6f)).toFloat() * 1.1f
                                        else -> 0f
                                    }
                            if (uriB != null) alpha = if (npcStepFrame(npc, tt) == frameIdx) 1f else 0f
                        },
            )
        }
        frame(uri, aspect, 0)
        if (uriB != null) frame(uriB, aspectB, 1)
    }
    sprite(HarborNpc.Lookout, "file:///android_asset/npc_lookout.webp", 58.dp, 0.0006f, 8f, aspect = 0.5786f)
    sprite(HarborNpc.Lantern, "file:///android_asset/npc_lantern.webp", 62.dp, 0.0016f, 0.8f, aspect = 0.4734f, uriB = "file:///android_asset/npc_lantern_b.webp", aspectB = 0.3984f)
    sprite(HarborNpc.Crate, "file:///android_asset/npc_crate.webp", 60.dp, 0.0018f, 0.8f, aspect = 0.4906f, uriB = "file:///android_asset/npc_crate_b.webp", aspectB = 0.5141f)
    sprite(HarborNpc.Rope, "file:///android_asset/npc_rope.webp", 60.dp, 0.0012f, 2.5f, aspect = 0.6976f)
    sprite(HarborNpc.Boat, "file:///android_asset/npc_boat.webp", 54.dp, 0.0022f, 6f, aspect = 1.0643f)
}


/** NPC 画片：IO 线程解码合并后的 asset（不走图片库，零依赖风险）。 */
@Composable
private fun HarborSpriteImage(assetUri: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val bitmap by
        produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, assetUri) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                        ctx.assets.open(assetUri.removePrefix("file:///android_asset/")).use { BitmapFactory.decodeStream(it) }
                            ?.asImageBitmap()
                    }.getOrNull()
                }
        }
    val bmp = bitmap
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
    }
}

/** 羊皮纸小气泡：港口的人说话的地方。 */
@Composable
private fun HarborBubble(centerAt: () -> Offset, text: String) {
    Box(
        Modifier.offset {
                val center = centerAt()
                IntOffset((center.x - 95.dp.toPx()).roundToInt(), (center.y - 118.dp.toPx()).roundToInt())
            }
            .width(190.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF3E5C8).copy(alpha = 0.97f))
            .padding(horizontal = 11.dp, vertical = 8.dp),
    ) {
        BasicText(
            text,
            style =
                TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 12.5.sp,
                    color = Color(0xFF4A3421),
                    textAlign = TextAlign.Start,
                ),
        )
    }
}

/** 节点视图的头行：回港、组名、搜索开关。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NodeDeckHeader(
    map: SkyShotMap,
    group: ProxyGroupInfo,
    searchVisible: Boolean,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
) {
    val backPos = map.at(Offset(0.115f, 0.486f))
    Box(
        Modifier.offset { IntOffset((backPos.x - 52.dp.toPx()).roundToInt(), (backPos.y - 20.dp.toPx()).roundToInt()) }
            .size(104.dp, 40.dp)
            .semantics {
                role = Role.Button
                contentDescription = "回到港口"
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onBack() },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            "‹ 港口",
            style =
                TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SkyBadgeGold,
                    shadow = Shadow(color = SkyBadgeGold.copy(alpha = 0.75f), blurRadius = 10f),
                ),
        )
    }
    val namePos = map.at(Offset(0.235f, 0.486f))
    Box(
        Modifier.offset { IntOffset(namePos.x.roundToInt(), (namePos.y - 14.dp.toPx()).roundToInt()) }
            .width(230.dp),
    ) {
        BasicText(
            "${group.name} · ${group.proxies.size}",
            style =
                TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 15.sp,
                    color = SkyLabelCore,
                    shadow = Shadow(color = Color(0xFF8FC8FF).copy(alpha = 0.6f), blurRadius = 8f),
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val searchPos = map.at(Offset(0.905f, 0.486f))
    Box(
        Modifier.offset { IntOffset((searchPos.x - 40.dp.toPx()).roundToInt(), (searchPos.y - 20.dp.toPx()).roundToInt()) }
            .size(80.dp, 40.dp)
            .semantics {
                role = Role.Button
                contentDescription = "搜索节点"
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggleSearch() },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            if (searchVisible) "收起" else "搜索",
            style =
                TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 15.sp,
                    color = if (searchVisible) SkyBadgeGold else SkyLabelCore,
                    shadow = Shadow(color = Color(0xFF8FC8FF).copy(alpha = 0.6f), blurRadius = 8f),
                ),
        )
    }
}

/** 搜索输入：一行光字，写在甲板雾上。 */
@Composable
private fun HarborSearchField(map: SkyShotMap, query: String, onQueryChange: (String) -> Unit) {
    val pos = map.at(Offset(0.5f, 0.528f))
    Column(
        Modifier.offset { IntOffset((pos.x - 160.dp.toPx()).roundToInt(), (pos.y - 16.dp.toPx()).roundToInt()) }
            .width(320.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle =
                TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 16.sp,
                    color = SkyLabelCore,
                ),
            cursorBrush = SolidColor(SkyBadgeGold),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(SkyBadgeGold.copy(alpha = 0.55f)))
    }
}

/** 节点光行：名字在左、延迟按三档色在右，选中者金点领头。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NodeLightRow(
    proxy: Proxy,
    selected: Boolean,
    testing: Boolean,
    phase: () -> Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        if (selected) {
            Canvas(Modifier.size(8.dp)) {
                drawCircle(SkyBadgeGold)
            }
            Spacer(Modifier.width(10.dp))
        }
        BasicText(
            proxy.name,
            style =
                TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 15.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) SkyBadgeGold else SkyLabelCore,
                    shadow = Shadow(color = Color(0xFF8FC8FF).copy(alpha = 0.45f), blurRadius = 7f),
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        val delayText =
            when {
                testing -> "···"
                proxy.delay in 1..5000 -> proxy.delay.toString()
                else -> "—"
            }
        BasicText(
            delayText,
            style =
                TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color =
                        when {
                            testing -> SkyLabelCore.copy(alpha = 0.55f + 0.3f * sin(2.0 * PI * (phase() / 1.1f)).toFloat())
                            proxy.delay in 1..5000 -> skyLatencyColor(proxy.delay)
                            else -> SkyStateOff
                        },
                ),
        )
    }
}

@Composable
private fun GlowCenterText(center: Offset, text: String, size: Int) {
    Box(
        Modifier.offset { IntOffset((center.x - 130.dp.toPx()).roundToInt(), (center.y - 16.dp.toPx()).roundToInt()) }
            .width(260.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style =
                TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = size.sp,
                    color = SkyLabelCore,
                    textAlign = TextAlign.Center,
                    shadow = Shadow(color = Color(0xFF8FC8FF).copy(alpha = 0.6f), blurRadius = 9f),
                ),
        )
    }
}

@Composable
private fun HarborChevron(center: Offset, glyph: String, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.offset { IntOffset((center.x - 20.dp.toPx()).roundToInt(), (center.y - 20.dp.toPx()).roundToInt()) }
            .size(40.dp)
            .semantics {
                role = Role.Button
                contentDescription = desc
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            glyph,
            style =
                TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = SkyBadgeGold,
                    textAlign = TextAlign.Center,
                    shadow = Shadow(color = SkyBadgeGold.copy(alpha = 0.8f), blurRadius = 12f),
                ),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProxyHotspot(
    center: Offset,
    wDp: Dp,
    hDp: Dp,
    index: Int,
    contentDescription: String,
    onPressedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
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
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    )
}

@Composable
private fun NpcTouchZone(
    center: Offset,
    wDp: Dp,
    hDp: Dp,
    index: Int,
    desc: String,
    onClick: () -> Unit,
) {
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
                contentDescription = desc
                traversalIndex = index.toFloat()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
    )
}

/** 港口氛围：五灯火光与延迟色晕、塔灯、吊灯、船灯、雾流；节点视图时下层甲板罩一层雾幕衬光行。 */
@Composable
private fun SkyHarborAmbient(
    map: SkyShotMap,
    phase: () -> Float,
    lanternTiers: List<Int>,
    testing: Boolean,
    nodeView: Boolean,
) {
    Canvas(Modifier.fillMaxSize()) {
        val t = phase()
        fun glow(c: Offset, r: Float, color: Color, a: Float) {
            if (a <= 0.003f) return
            val p = map.at(c)
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(color.copy(alpha = a), Color.Transparent),
                        center = p,
                        radius = r,
                    ),
                radius = r,
                center = p,
            )
        }
        // 五盏系泊灯：暖光呼吸 + 灯下延迟色晕
        LANTERN_X.forEachIndexed { i, x ->
            val flick = 0.85f + 0.15f * sin(2.0 * PI * (t / (3.1f + i * 0.7f)) + i * 1.7f).toFloat()
            glow(Offset(x, LANTERN_HEAD_Y), size.width * 0.085f, Color(0xFFFFC978), 0.30f * flick)
            val delay = lanternTiers.getOrNull(i) ?: -1
            if (delay in 1..5000) {
                glow(Offset(x, 0.415f), size.width * 0.075f, skyLatencyColor(delay), 0.16f)
            }
        }
        // 塔灯：测速时脉动
        val towerPulse =
            if (testing) {
                0.30f + 0.25f * sin(2.0 * PI * (t / 1.1f)).toFloat()
            } else {
                0.30f + 0.06f * sin(2.0 * PI * (t / 4.3f)).toFloat()
            }
        glow(Offset(0.198f, 0.250f), size.width * 0.11f, Color(0xFFFFC978), towerPulse)
        // 甲板吊灯与船灯
        glow(Offset(0.185f, 0.605f), size.width * 0.055f, Color(0xFFFFC978), 0.22f)
        glow(Offset(0.655f, 0.600f), size.width * 0.055f, Color(0xFFFFC978), 0.22f)
        glow(Offset(0.415f, 0.815f), size.width * 0.05f, Color(0xFFFFC978), 0.20f)
        // 飞艇内光
        glow(Offset(0.60f, 0.135f), size.width * 0.16f, Color(0xFFFFB35C), 0.13f + 0.03f * sin(2.0 * PI * (t / 6f)).toFloat())
        // 节点视图：下层甲板雾幕（衬光行）
        if (nodeView) {
            val top = size.height * 0.462f
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0A142E).copy(alpha = 0.05f), Color(0xFF0A142E).copy(alpha = 0.45f)),
                        startY = top,
                        endY = size.height,
                    ),
                topLeft = Offset(0f, top),
                size = androidx.compose.ui.geometry.Size(size.width, size.height - top),
            )
        }
        // 雾流
        val m1 = 0.3f + 0.03f * sin(2.0 * PI * (t / 26f)).toFloat()
        glow(Offset(m1, 0.52f), size.width * 0.22f, Color(0xFFBFD6F5), 0.08f)
        val m2 = 0.7f + 0.03f * sin(2.0 * PI * (t / 31f) + 2f).toFloat()
        glow(Offset(m2, 0.93f), size.width * 0.24f, Color(0xFFBFD6F5), 0.08f)
    }
}
