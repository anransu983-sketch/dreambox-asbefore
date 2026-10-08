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

package com.suanran.dreambox.feature.settings.presentation.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.suanran.dreambox.core.model.animation.BuiltinAnimationConfigs
import com.suanran.dreambox.core.model.animation.DreamAnimation
import com.suanran.dreambox.locale.FlyTxt

/** 内置动画列表（本地化名称/描述），在动画商店中以不可删除身份展示。 */
@Composable
fun rememberBuiltinDreamAnimations(): List<DreamAnimation> {
    val animTxt = FlyTxt.AnimationStore
    return remember(animTxt) {
        listOf(
            DreamAnimation(
                id = BuiltinAnimationConfigs.SNOW_ID,
                name = animTxt.PresetSnowName,
                version = "1.0",
                author = animTxt.PresetAuthor,
                description = animTxt.PresetSnowDesc,
                particles = BuiltinAnimationConfigs.config(BuiltinAnimationConfigs.SNOW_ID)!!,
                sourceUrl = null,
                builtin = true,
            ),
            DreamAnimation(
                id = BuiltinAnimationConfigs.FIREWORKS_ID,
                name = animTxt.PresetFireworksName,
                version = "1.0",
                author = animTxt.PresetAuthor,
                description = animTxt.PresetFireworksDesc,
                particles = BuiltinAnimationConfigs.config(BuiltinAnimationConfigs.FIREWORKS_ID)!!,
                sourceUrl = null,
                builtin = true,
            ),
            DreamAnimation(
                id = BuiltinAnimationConfigs.RAIN_ID,
                name = animTxt.PresetRainName,
                version = "1.0",
                author = animTxt.PresetAuthor,
                description = animTxt.PresetRainDesc,
                particles = BuiltinAnimationConfigs.config(BuiltinAnimationConfigs.RAIN_ID)!!,
                sourceUrl = null,
                builtin = true,
            ),
        )
    }
}
