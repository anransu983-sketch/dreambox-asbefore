/*
 * This file is part of DreamBox.
 *
 * DreamBox is free software: you can redistribute it and/or modify
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
 */

package com.suanran.dreambox.feature.home.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.suanran.dreambox.core.model.profile.Profile
import com.suanran.dreambox.core.util.format.formatBytes
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.card.Card
import com.suanran.dreambox.presentation.theme.UiDp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 订阅流量卡：显示当前 URL 订阅的已用/总量与到期日。
 * 仅当 profile 为 Url 类型且带有 userinfo（total > 0 或 expire > 0）时由调用方展示。
 */
@Composable
fun SubscriptionCard(
    profile: Profile,
    modifier: Modifier = Modifier,
) {
    val used = (profile.upload + profile.download).coerceAtLeast(0L)
    val total = profile.total.coerceAtLeast(0L)
    val progress = if (total > 0L) (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val expireText = remember(profile.expire) {
        if (profile.expire > 0L) {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(profile.expire * 1000L))
        } else {
            null
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = UiDp.dp16, vertical = UiDp.dp14),
            verticalArrangement = Arrangement.spacedBy(UiDp.dp8),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = FlyTxt.Home.SubscriptionCard.Title,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (expireText != null) {
                    Text(
                        text = FlyTxt.Home.SubscriptionCard.ExpirePrefix + expireText,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = "${formatBytes(used)} / ${if (total > 0L) formatBytes(total) else "∞"}",
                style = MiuixTheme.textStyles.title2,
                color = MiuixTheme.colorScheme.onSurface,
            )
            if (total > 0L) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(UiDp.dp6).clip(RoundedCornerShape(UiDp.dp3)).background(MiuixTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(progress).height(UiDp.dp6).clip(RoundedCornerShape(UiDp.dp3)).background(MiuixTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

/** 是否展示订阅卡：Url 订阅且带 userinfo。 */
fun Profile.showSubscriptionCard(): Boolean =
    type == Profile.Type.Url && (total > 0L || expire > 0L)
