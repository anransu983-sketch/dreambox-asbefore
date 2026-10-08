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

package com.suanran.dreambox.feature.settings.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.suanran.dreambox.core.model.ThemeMode
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.theme.DEFAULT_CUSTOM_THEME_SEED_ARGB

/**
 * 内置主题预设：一次点选同时切换主题模式与主题色，对齐梦盒 Windows 版的主题模块。
 *
 * 在主题商店中以内置主题身份展示（不可删除、不可覆盖）。
 */
object DreamThemePresets {
    const val VERSION = "1.0"
    const val DEFAULT_DARK_ID = "default-dark"
    const val BOCCHI_STARLIGHT_ID = "bocchi-starlight"

    /** 波奇酱粉：粉色星空主题的取色 */
    const val BOCCHI_PINK_SEED_ARGB: Long = 0xFFFF6B9DL

    private val builtinIds = setOf(DEFAULT_DARK_ID, BOCCHI_STARLIGHT_ID)

    fun isBuiltinId(id: String): Boolean = id in builtinIds
}

/** 内置主题列表（本地化名称/描述）。 */
@Composable
fun rememberBuiltinDreamThemes(): List<DreamTheme> {
    val themeTxt = FlyTxt.AppSettings.Interface.Theme
    return remember(themeTxt) {
        listOf(
            DreamTheme(
                id = DreamThemePresets.DEFAULT_DARK_ID,
                name = themeTxt.PresetDefaultDarkName,
                version = DreamThemePresets.VERSION,
                author = themeTxt.PresetDefaultDarkAuthor,
                description = themeTxt.PresetDefaultDarkDesc,
                themeMode = ThemeMode.Dark,
                seedColorArgb = DEFAULT_CUSTOM_THEME_SEED_ARGB,
                sourceUrl = null,
                builtin = true,
            ),
            DreamTheme(
                id = DreamThemePresets.BOCCHI_STARLIGHT_ID,
                name = themeTxt.PresetBocchiName,
                version = DreamThemePresets.VERSION,
                author = themeTxt.PresetBocchiAuthor,
                description = themeTxt.PresetBocchiDesc,
                themeMode = ThemeMode.Dark,
                seedColorArgb = DreamThemePresets.BOCCHI_PINK_SEED_ARGB,
                sourceUrl = null,
                builtin = true,
            ),
        )
    }
}
