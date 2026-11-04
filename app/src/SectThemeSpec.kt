package com.suanran.dreambox

import androidx.compose.ui.unit.dp
import com.suanran.dreambox.core.model.ThemeMode
import com.suanran.dreambox.presentation.theme.Radii
import com.suanran.dreambox.presentation.theme.Spacing

/**
 * 门派全 App 主题规格：选道即换装。
 * 不是只给插件页换皮——种子色、明暗、圆角语言、布局密度全部按门派走，
 * MainActivity 订阅 sectPathId 实时生效（选完道回到主界面即变，无需重启）。
 * 叛出师门（sectPathId 清空）后自动回到用户自己的主题设置。
 */
data class SectThemeSpec(
    val seedArgb: Long,
    val mode: ThemeMode,
    val radii: Radii,
    val spacing: Spacing,
)

val sectThemeSpecs: Map<String, SectThemeSpec> = mapOf(
    // 剑修 · 青冥剑宗：冷蓝、利落小圆角（剑锋）、略紧的排布
    "jian" to SectThemeSpec(
        seedArgb = 0xFF0284C7L,
        mode = ThemeMode.Dark,
        radii = Radii(
            radius8 = 4.dp, radius12 = 6.dp, radius14 = 7.dp, radius16 = 8.dp,
            radius18 = 9.dp, radius24 = 12.dp, radius36 = 16.dp,
        ),
        spacing = Spacing(contentHorizontal = 14.dp, screenHorizontal = 10.dp, screenVertical = 10.dp),
    ),
    // 符修 · 太清符宗：黄纸浅色、大而软的圆角（纸卷）、疏朗
    "fu" to SectThemeSpec(
        seedArgb = 0xFFF59E0BL,
        mode = ThemeMode.Light,
        radii = Radii(
            radius8 = 12.dp, radius12 = 18.dp, radius14 = 20.dp, radius16 = 24.dp,
            radius18 = 26.dp, radius24 = 32.dp, radius36 = 40.dp,
        ),
        spacing = Spacing(contentHorizontal = 18.dp, screenHorizontal = 14.dp, screenVertical = 14.dp),
    ),
    // 阵修 · 星枢阵宫：靛蓝、几何感中圆角、规整
    "zhen" to SectThemeSpec(
        seedArgb = 0xFF6366F1L,
        mode = ThemeMode.Dark,
        radii = Radii(
            radius8 = 6.dp, radius12 = 8.dp, radius14 = 10.dp, radius16 = 12.dp,
            radius18 = 14.dp, radius24 = 16.dp, radius36 = 20.dp,
        ),
        spacing = Spacing(contentHorizontal = 16.dp, screenHorizontal = 12.dp, screenVertical = 12.dp),
    ),
    // 体修 · 金刚体宗：橙、厚重圆角、大开大合的间距
    "ti" to SectThemeSpec(
        seedArgb = 0xFFEA580CL,
        mode = ThemeMode.Dark,
        radii = Radii(
            radius8 = 10.dp, radius12 = 14.dp, radius14 = 16.dp, radius16 = 18.dp,
            radius18 = 20.dp, radius24 = 28.dp, radius36 = 36.dp,
        ),
        spacing = Spacing(contentHorizontal = 20.dp, screenHorizontal = 16.dp, screenVertical = 16.dp),
    ),
    // 傀儡修 · 千机傀儡门：青、机械感极小圆角、紧凑
    "kuilei" to SectThemeSpec(
        seedArgb = 0xFF0F766EL,
        mode = ThemeMode.Dark,
        radii = Radii(
            radius8 = 3.dp, radius12 = 5.dp, radius14 = 6.dp, radius16 = 7.dp,
            radius18 = 8.dp, radius24 = 10.dp, radius36 = 14.dp,
        ),
        spacing = Spacing(contentHorizontal = 12.dp, screenHorizontal = 8.dp, screenVertical = 8.dp),
    ),
    // 鬼修 · 幽冥鬼府：幽绿、大圆角（阴柔）、疏冷
    "gui" to SectThemeSpec(
        seedArgb = 0xFF16A34AL,
        mode = ThemeMode.Dark,
        radii = Radii(
            radius8 = 16.dp, radius12 = 22.dp, radius14 = 24.dp, radius16 = 28.dp,
            radius18 = 30.dp, radius24 = 36.dp, radius36 = 44.dp,
        ),
        spacing = Spacing(contentHorizontal = 18.dp, screenHorizontal = 14.dp, screenVertical = 14.dp),
    ),
    // 魔修 · 血煞魔宗：血红、几乎直角（煞气）、紧逼
    "mo" to SectThemeSpec(
        seedArgb = 0xFFDC2626L,
        mode = ThemeMode.Dark,
        radii = Radii(
            radius8 = 2.dp, radius12 = 3.dp, radius14 = 4.dp, radius16 = 5.dp,
            radius18 = 6.dp, radius24 = 8.dp, radius36 = 10.dp,
        ),
        spacing = Spacing(contentHorizontal = 14.dp, screenHorizontal = 10.dp, screenVertical = 10.dp),
    ),
)
