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

package com.suanran.dreambox.feature.profiles.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import com.suanran.dreambox.core.model.profile.Profile
import com.suanran.dreambox.feature.profiles.presentation.viewmodel.ProfilesViewModel
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.card.ProfileCard
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.misc.CenteredText
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.icon.FlyCat
import com.suanran.dreambox.presentation.icon.flycat.BadgePlus
import com.suanran.dreambox.presentation.icon.flycat.CircleFadingArrowUp
import com.suanran.dreambox.presentation.theme.UiDp
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suanran.dreambox.core.util.format.ByteFormatter
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
import com.suanran.dreambox.presentation.component.sky.SkyStatusBarIcons
import com.suanran.dreambox.presentation.component.sky.rememberSkyCaption
import com.suanran.dreambox.presentation.component.sky.rememberSkyClock
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun ProfilesPageHost(
    mainInnerPadding: PaddingValues,
    profiles: List<Profile>,
    state: ProfilesDialogState,
    profilesViewModel: ProfilesViewModel,
    isRunning: Boolean,
    onUpdateAll: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()

    ProfilesPageContent(
        mainInnerPadding = mainInnerPadding,
        profiles = profiles,
        isDownloading = state.isDownloading,
        onUpdateAll = onUpdateAll,
        onAddProfile = {
            state.profileToEdit = null
            state.showAdd.value = true
        },
        onReorderProfiles = { from, to -> profilesViewModel.reorderProfiles(from, to) },
        onShareProfile = { profile ->
            if (!state.isDownloading) {
                state.shareTarget = profile
                state.showShare.value = true
            }
        },
        onUpdateProfile = { profile ->
            if (!state.isDownloading) {
                state.isDownloading = true
                scope.launch {
                    profilesViewModel.updateProfile(profile.uuid)
                    state.isDownloading = false
                }
            }
        },
        onDeleteProfile = { profile ->
            if (!state.isDownloading) {
                state.deleteTarget = profile
                state.showDelete = true
            }
        },
        onEditProfile = { profile ->
            if (!state.isDownloading) {
                state.editOptionsTarget = profile
                state.showEditOptions = true
            }
        },
        onToggleProfile = { profile ->
            if (!state.isDownloading) {
                scope.launch {
                    if (profile.active && isRunning) {
                        profilesViewModel.stopProxy()
                    }
                    profilesViewModel.toggleProfileEnabled(profile.uuid)
                }
            }
        },
    )
}

@Composable
internal fun ProfilesPageContent(
    mainInnerPadding: PaddingValues,
    profiles: List<Profile>,
    isDownloading: Boolean,
    onUpdateAll: (() -> Unit)? = null,
    onAddProfile: () -> Unit,
    onReorderProfiles: (Int, Int) -> Unit,
    onShareProfile: (Profile) -> Unit,
    onUpdateProfile: (Profile) -> Unit,
    onDeleteProfile: (Profile) -> Unit,
    onEditProfile: (Profile) -> Unit,
    onToggleProfile: (Profile) -> Unit,
) {
    // 天空版工坊（用户 2026-11-06 定稿）：卷轴架场景，旧列表保留在标志下方随时切回。
    if (SKY_PROFILES_PAGE) {
        SkyProfileScene(
            profiles = profiles,
            isDownloading = isDownloading,
            onUpdateAll = onUpdateAll,
            onAddProfile = onAddProfile,
            onEditProfile = onEditProfile,
            onToggleProfile = onToggleProfile,
        )
        return
    }

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopBar(
                title = FlyTxt.ProfilesPage.Title,
                scrollBehavior = scrollBehavior,
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy(UiDp.dp4)) {
                        if (onUpdateAll != null) {
                            IconButton(onClick = onUpdateAll) {
                                Icon(
                                    imageVector = FlyCat.CircleFadingArrowUp,
                                    contentDescription = FlyTxt.ProfilesPage.Action.UpdateAll,
                                )
                            }
                        }
                        IconButton(onClick = onAddProfile) {
                            Icon(
                                imageVector = FlyCat.BadgePlus,
                                contentDescription = FlyTxt.ProfilesPage.Action.AddProfile,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (profiles.isEmpty()) {
            CenteredText(
                firstLine = FlyTxt.ProfilesPage.Empty.NoProfiles,
                secondLine = FlyTxt.ProfilesPage.Empty.Hint,
            )
        } else {
            ProfilesList(
                profiles = profiles,
                mainInnerPadding = mainInnerPadding,
                innerPadding = innerPadding,
                scrollBehavior = scrollBehavior,
                isDownloading = isDownloading,
                onReorderProfiles = onReorderProfiles,
                onShareProfile = onShareProfile,
                onUpdateProfile = onUpdateProfile,
                onDeleteProfile = onDeleteProfile,
                onEditProfile = onEditProfile,
                onToggleProfile = onToggleProfile,
            )
        }
    }
}

@Composable
private fun ProfilesList(
    profiles: List<Profile>,
    mainInnerPadding: PaddingValues,
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    isDownloading: Boolean,
    onReorderProfiles: (Int, Int) -> Unit,
    onShareProfile: (Profile) -> Unit,
    onUpdateProfile: (Profile) -> Unit,
    onDeleteProfile: (Profile) -> Unit,
    onEditProfile: (Profile) -> Unit,
    onToggleProfile: (Profile) -> Unit,
) {
    val listState = rememberLazyListState()
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            onReorderProfiles(from.index, to.index)
        }
    val importedDir = LocalContext.current.filesDir.resolve("imported")

    ScreenLazyColumn(
        lazyListState = listState,
        scrollBehavior = scrollBehavior,
        innerPadding = combinePaddingValues(innerPadding, mainInnerPadding),
        topPadding = UiDp.dp20,
    ) {
        items(items = profiles, key = { it.uuid.toString() }) { profile ->
            ReorderableItem(reorderState, key = profile.uuid.toString()) { isDragging ->
                ProfileCard(
                    profile = profile,
                    workDir = importedDir,
                    isDownloading = isDownloading,
                    modifier =
                        Modifier.longPressDraggableHandle().alpha(if (isDragging) 0.9f else 1f),
                    onExport = { onShareProfile(it) },
                    onUpdate = { onUpdateProfile(it) },
                    onDelete = { onDeleteProfile(it) },
                    onEdit = { onEditProfile(it) },
                    onToggleEnabled = { onToggleProfile(it) },
                )
            }
        }
    }
}

private const val SKY_PROFILES_PAGE = true

private const val SKY_PROFILES_URI = "file:///android_asset/sky_profiles_workshop.webp"

private val InkBrown = Color(0xFF4A3421)
private val SealRed = Color(0xFFB33A2B)

/** 四卷的位置（图比例，定稿画实测：右墙 2×2 卷轴架）。 */
private val PROFILE_SLOTS: List<Offset> =
    listOf(
        Offset(0.698f, 0.322f),
        Offset(0.878f, 0.322f),
        Offset(0.698f, 0.548f),
        Offset(0.878f, 0.548f),
    )

/**
 * 配置页 · 天空版：齿轮工坊整屏画。配置是右墙卷轴架上的卷轴，名字用墨写在卷面上
 * （动态数据，如首页城堡上的节点名），当前配置盖红印并亮金晕；点卷轴启用/停用，
 * 长按开编辑；铃下「更新」牌=全部更新，桌边「添加」牌=添加配置；卷轴多于四卷翻页。
 * 程序只叠光与热区。画加载失败时退回光字列表，功能不丢。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SkyProfileScene(
    profiles: List<Profile>,
    isDownloading: Boolean,
    onUpdateAll: (() -> Unit)?,
    onAddProfile: () -> Unit,
    onEditProfile: (Profile) -> Unit,
    onToggleProfile: (Profile) -> Unit,
) {
    val pager = LocalMainPagerState.current
    val active = pager.selectedPage == BottomBarDestination.Profiles.ordinal
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
    val pageCount = maxOf(1, (profiles.size + PROFILE_SLOTS.size - 1) / PROFILE_SLOTS.size)
    var page by remember { mutableIntStateOf(0) }
    LaunchedEffect(pageCount) { if (page >= pageCount) page = pageCount - 1 }
    val pageProfiles = profiles.drop(page * PROFILE_SLOTS.size).take(PROFILE_SLOTS.size)
    val activeSlot =
        pageProfiles.indexOfFirst { it.active }.takeIf { it >= 0 }?.let { PROFILE_SLOTS[it] }

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
                    assetUri = SKY_PROFILES_URI,
                    sat = 1f,
                    dusk = 0f,
                    onReadyChange = { imgReady = it },
                )
                SkyWorkshopAmbient(
                    map = map,
                    phase = { clock.floatValue },
                    boostKey = pressedKey,
                    boost = { boost.value },
                    activeSlot = activeSlot,
                    downloading = isDownloading,
                )
            }
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SkyNightBase, SkyDeepBase))))
        }
        if (imgReady || fallback) {
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { showTick++ },
            )
            if (!fallback) {
                pageProfiles.forEachIndexed { index, profile ->
                    val slot = PROFILE_SLOTS[index]
                    ScrollInkLabel(center = map.at(slot), profile = profile)
                    ProfileHotspot(
                        center = map.at(slot),
                        wDp = 118.dp,
                        hDp = 205.dp,
                        index = index,
                        contentDescription = profile.name,
                        onPressedChange = { p ->
                            pressedKey = if (p) "slot$index" else if (pressedKey == "slot$index") null else pressedKey
                        },
                        onClick = { onToggleProfile(profile) },
                        onLongClick = { onEditProfile(profile) },
                    )
                }
                if (profiles.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                            Modifier.offset {
                                val c = map.at(Offset(0.700f, 0.752f))
                                IntOffset((c.x - 105.dp.toPx()).roundToInt(), (c.y - 30.dp.toPx()).roundToInt())
                            }
                                .width(210.dp),
                    ) {
                        BasicText(
                            FlyTxt.ProfilesPage.Empty.NoProfiles,
                            style =
                                TextStyle(
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = InkBrown,
                                    textAlign = TextAlign.Center,
                                ),
                        )
                        BasicText(
                            FlyTxt.ProfilesPage.Empty.Hint,
                            style =
                                TextStyle(
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 11.5.sp,
                                    color = InkBrown.copy(alpha = 0.78f),
                                    textAlign = TextAlign.Center,
                                ),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                if (pageCount > 1) {
                    if (page > 0) {
                        PageChevron(center = map.at(Offset(0.578f, 0.435f)), glyph = "‹", desc = "上一架") {
                            page--
                            showTick++
                        }
                    }
                    if (page < pageCount - 1) {
                        PageChevron(center = map.at(Offset(0.982f, 0.435f)), glyph = "›", desc = "下一架") {
                            page++
                            showTick++
                        }
                    }
                }
                if (onUpdateAll != null) {
                    ProfileHotspot(
                        center = map.at(Offset(0.562f, 0.50f)),
                        wDp = 110.dp,
                        hDp = 180.dp,
                        index = 8,
                        contentDescription = FlyTxt.ProfilesPage.Action.UpdateAll,
                        onPressedChange = { p ->
                            pressedKey = if (p) "update" else if (pressedKey == "update") null else pressedKey
                        },
                        onClick = { onUpdateAll() },
                        onLongClick = {},
                    )
                }
                ProfileHotspot(
                    center = map.at(Offset(0.795f, 0.932f)),
                    wDp = 120.dp,
                    hDp = 130.dp,
                    index = 9,
                    contentDescription = FlyTxt.ProfilesPage.Action.AddProfile,
                    onPressedChange = { p ->
                        pressedKey = if (p) "add" else if (pressedKey == "add") null else pressedKey
                    },
                    onClick = onAddProfile,
                    onLongClick = {},
                )
            } else {
                // 画没加载出来时的兜底：中央一列光字行（点=启用/停用，长按=编辑）
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize().padding(top = 110.dp),
                ) {
                    profiles.forEachIndexed { index, profile ->
                        BasicText(
                            profile.name,
                            style =
                                TextStyle(
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (profile.active) SkyBadgeGold else SkyLabelCore,
                                    textAlign = TextAlign.Center,
                                ),
                            modifier =
                                Modifier.padding(vertical = 13.dp)
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { onToggleProfile(profile) },
                                        onLongClick = { onEditProfile(profile) },
                                    ),
                        )
                    }
                    if (profiles.isEmpty()) {
                        BasicText(
                            FlyTxt.ProfilesPage.Empty.NoProfiles,
                            style =
                                TextStyle(
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 17.sp,
                                    color = SkyLabelCore,
                                    textAlign = TextAlign.Center,
                                ),
                        )
                    }
                }
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
        }
    }
}

/** 卷面上的墨字：名字、来源、流量；当前配置盖红印。停用的卷轴墨色减半。 */
@Composable
private fun ScrollInkLabel(center: Offset, profile: Profile) {
    Box(
        Modifier.offset {
                IntOffset((center.x - 44.dp.toPx()).roundToInt(), (center.y - 79.dp.toPx()).roundToInt())
            }
            .size(88.dp, 158.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(if (profile.enabled) 1f else 0.45f),
        ) {
            BasicText(
                profile.name,
                style =
                    TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InkBrown,
                        textAlign = TextAlign.Center,
                    ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                profile.getDisplayProvider(),
                style =
                    TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 10.5.sp,
                        color = InkBrown.copy(alpha = 0.78f),
                        textAlign = TextAlign.Center,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
            val total = profile.totalBytes
            if (total != null && total > 0) {
                BasicText(
                    "${ByteFormatter.format(profile.usedBytes)} / ${ByteFormatter.format(total)}",
                    style =
                        TextStyle(
                            fontFamily = FontFamily.Serif,
                            fontSize = 9.5.sp,
                            color = InkBrown.copy(alpha = 0.66f),
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (profile.active) {
            Box(
                Modifier.align(Alignment.BottomEnd)
                    .padding(end = 2.dp, bottom = 6.dp)
                    .rotate(-10f)
                    .background(SealRed, RoundedCornerShape(4.dp))
                    .size(21.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    "用",
                    style =
                        TextStyle(
                            fontFamily = FontFamily.Serif,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        ),
                )
            }
        }
    }
}

/** 翻页箭头：画架两侧的光字。 */
@Composable
private fun PageChevron(center: Offset, glyph: String, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.offset {
                IntOffset((center.x - 22.dp.toPx()).roundToInt(), (center.y - 22.dp.toPx()).roundToInt())
            }
            .size(44.dp)
            .semantics {
                role = Role.Button
                contentDescription = desc
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            glyph,
            style =
                TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = SkyBadgeGold,
                    textAlign = TextAlign.Center,
                    shadow = Shadow(color = SkyBadgeGold.copy(alpha = 0.8f), blurRadius = 12f),
                ),
        )
    }
}

/** 画里物件的隐形热区（点按 + 长按）：无任何可见元素。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileHotspot(
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

/** 工坊氛围层：提灯呼吸、窗外星光、铃与木牌的按压光晕、当前卷轴的金晕、页码点。 */
@Composable
private fun SkyWorkshopAmbient(
    map: SkyShotMap,
    phase: () -> Float,
    boostKey: String?,
    boost: () -> Float,
    activeSlot: Offset?,
    downloading: Boolean,
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
        val flick = 0.86f + 0.14f * sin(2.0 * PI * (t / 3.7f)).toFloat()
        glow(Offset(0.660f, 0.112f), size.width * 0.16f, Color(0xFFFFC978), 0.30f * flick)
        // 窗外星光
        val starSeeds =
            listOf(
                Offset(0.205f, 0.185f) to 0.31f,
                Offset(0.315f, 0.150f) to 0.77f,
                Offset(0.255f, 0.300f) to 0.53f,
                Offset(0.350f, 0.265f) to 0.19f,
                Offset(0.190f, 0.420f) to 0.91f,
            )
        starSeeds.forEach { (pos, seed) ->
            val tw = 0.5f + 0.5f * sin(2.0 * PI * (t / (6f + seed * 5f)) + seed * 17f).toFloat()
            val p = map.at(pos)
            drawCircle(Color(0xFFDCEBFF).copy(alpha = 0.25f + 0.45f * tw), radius = 1.6f + 1.3f * tw, center = p)
        }
        // 当前卷轴金晕
        activeSlot?.let { s ->
            val pulse = 0.85f + 0.15f * sin(2.0 * PI * (t / 5f)).toFloat()
            glow(s, size.width * 0.17f, SkyBadgeGold, 0.26f * pulse)
        }
        // 按压光晕
        val bk = boostKey
        val bv = boost()
        if (bk != null && bv > 0.01f) {
            when {
                bk == "update" -> glow(Offset(0.562f, 0.47f), size.width * 0.15f, Color(0xFFFFC978), 0.34f * bv)
                bk == "add" -> glow(Offset(0.795f, 0.932f), size.width * 0.13f, Color(0xFFFFC978), 0.34f * bv)
                bk.startsWith("slot") ->
                    PROFILE_SLOTS.getOrNull(bk.removePrefix("slot").toIntOrNull() ?: -1)?.let {
                        glow(it, size.width * 0.16f, SkyBadgeGold, 0.30f * bv)
                    }
            }
        }
        // 更新中：铃光脉动
        if (downloading) {
            val pulse = 0.5f + 0.5f * sin(2.0 * PI * (t / 1.2f)).toFloat()
            glow(Offset(0.562f, 0.428f), size.width * 0.10f, Color(0xFFFFC978), 0.18f + 0.30f * pulse)
        }
        // 窗下雾流
        val mistX = 0.27f + 0.02f * sin(2.0 * PI * (t / 23f)).toFloat()
        glow(Offset(mistX, 0.665f), size.width * 0.20f, Color(0xFFBFD6F5), 0.10f)
    }
}
