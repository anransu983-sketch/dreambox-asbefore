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

package com.suanran.dreambox.feature.dashboard.presentation.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suanran.dreambox.feature.dashboard.presentation.viewmodel.StreamPlatformState
import com.suanran.dreambox.feature.dashboard.presentation.viewmodel.StreamUnlockResult
import com.suanran.dreambox.feature.dashboard.presentation.viewmodel.StreamUnlockViewModel
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.navigation.Navigator
import com.suanran.dreambox.presentation.theme.AppTheme
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class StatusDisplay(val text: String, val color: Color)

@Composable
private fun StreamUnlockResult.getDisplay(): StatusDisplay =
    when (this) {
        StreamUnlockResult.Idle ->
            StatusDisplay(
                text = FlyTxt.StreamUnlock.Status.Idle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        StreamUnlockResult.Checking ->
            StatusDisplay(
                text = FlyTxt.StreamUnlock.Status.Checking,
                color = MiuixTheme.colorScheme.primary,
            )
        is StreamUnlockResult.Unlocked ->
            StatusDisplay(
                text =
                    if (region != null) {
                        FlyTxt.StreamUnlock.Status.UnlockedWithRegion.format(region)
                    } else {
                        FlyTxt.StreamUnlock.Status.Unlocked
                    },
                color = MiuixTheme.colorScheme.primary,
            )
        StreamUnlockResult.Locked ->
            StatusDisplay(
                text = FlyTxt.StreamUnlock.Status.Locked,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        StreamUnlockResult.Failed ->
            StatusDisplay(
                text = FlyTxt.StreamUnlock.Status.Failed,
                color = MiuixTheme.colorScheme.error,
            )
    }

@Composable
fun StreamUnlockScreen(navigator: Navigator) {
    val viewModel = koinViewModel<StreamUnlockViewModel>()
    val states by viewModel.states.collectAsStateWithLifecycle()
    val spacing = AppTheme.spacing

    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopBar(
                title = FlyTxt.StreamUnlock.Title,
                scrollBehavior = scrollBehavior,
                navigationIconPadding = 0.dp,
                navigationIcon = { NavigationBackIcon(navigator = navigator) },
            )
        },
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = innerPadding,
            contentPadding =
                PaddingValues(
                    start = spacing.screenHorizontal,
                    end = spacing.screenHorizontal,
                    top = innerPadding.calculateTopPadding(),
                    bottom = mainLikePadding.calculateBottomPadding() + spacing.space12,
                ),
        ) {
            item {
                Button(
                    onClick = { viewModel.checkAll() },
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.space12),
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text(text = FlyTxt.StreamUnlock.CheckAll)
                }
            }

            items(
                items = states,
                key = { it.platform },
                contentType = { "stream-platform" },
            ) { state ->
                StreamPlatformCard(
                    state = state,
                    onRecheck = { viewModel.check(state.platform) },
                    modifier = Modifier.padding(top = spacing.space12),
                )
            }
        }
    }
}

@Composable
private fun StreamPlatformCard(
    state: StreamPlatformState,
    onRecheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    val status = state.result.getDisplay()

    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        horizontal = spacing.space16,
                        vertical = spacing.space12,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.platform.displayName,
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = status.text,
                    style = MiuixTheme.textStyles.body2,
                    color = status.color,
                    modifier = Modifier.padding(top = spacing.space4),
                )
            }
            TextButton(
                text = FlyTxt.StreamUnlock.Recheck,
                onClick = onRecheck,
            )
        }
    }
}
