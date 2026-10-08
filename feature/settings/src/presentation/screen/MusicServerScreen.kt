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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suanran.dreambox.core.contract.AppSettingsReader
import com.suanran.dreambox.feature.settings.data.music.SubsonicClient
import com.suanran.dreambox.feature.settings.presentation.music.MusicSectionTitle
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.misc.BocchiCard
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.navigation.Navigator
import com.suanran.dreambox.presentation.theme.AppTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
private fun MusicServerIcon(imageVector: ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, end = 12.dp),
    )
}

/**
 * 音乐服配置：地址 / 账号 / 密码直接内嵌输入，改完即保存，无弹窗。
 * 走 Subsonic / OpenSubsonic 协议（Navidrome）。
 */
@Composable
fun MusicServerScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val appSettings = koinInject<AppSettingsReader>()
    val scope = rememberCoroutineScope()
    val txt = FlyTxt.Settings.Experimental.Music
    val spacing = AppTheme.spacing

    val serverUrl by appSettings.musicServerUrl.state.collectAsStateWithLifecycle()
    val serverUser by appSettings.musicServerUser.state.collectAsStateWithLifecycle()
    val serverPassword by appSettings.musicServerPassword.state.collectAsStateWithLifecycle()

    var urlInput by remember(serverUrl) { mutableStateOf(serverUrl) }
    var userInput by remember(serverUser) { mutableStateOf(serverUser) }
    var passwordInput by remember(serverPassword) { mutableStateOf(serverPassword) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    fun saveAll() {
        appSettings.musicServerUrl.set(urlInput.trim().trimEnd('/'))
        appSettings.musicServerUser.set(userInput.trim())
        appSettings.musicServerPassword.set(passwordInput)
    }

    Scaffold(
        topBar = {
            TopBar(
                title = txt.ServerTitle,
                scrollBehavior = scrollBehavior,
                navigationIcon = { NavigationBackIcon(navigator = navigator) },
            )
        },
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
            verticalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            item {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.space16, vertical = spacing.space12),
                    ) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            MusicServerIcon(Icons.Filled.Cloud)
                            Text(
                                text = txt.ServerUrl,
                                style = MiuixTheme.textStyles.title4,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(modifier = Modifier.height(spacing.space8))
                        TextField(
                            value = urlInput,
                            onValueChange = {
                                urlInput = it
                                appSettings.musicServerUrl.set(it.trim().trimEnd('/'))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = txt.ServerUrlHint,
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                    }
                }
            }
            item {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.space16, vertical = spacing.space12),
                    ) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            MusicServerIcon(Icons.Filled.Person)
                            Text(
                                text = txt.ServerUser,
                                style = MiuixTheme.textStyles.title4,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(modifier = Modifier.height(spacing.space8))
                        TextField(
                            value = userInput,
                            onValueChange = {
                                userInput = it
                                appSettings.musicServerUser.set(it.trim())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = txt.ServerUserHint,
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                        )
                    }
                }
            }
            item {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.space16, vertical = spacing.space12),
                    ) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            MusicServerIcon(Icons.Filled.VpnKey)
                            Text(
                                text = txt.ServerPassword,
                                style = MiuixTheme.textStyles.title4,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(modifier = Modifier.height(spacing.space8))
                        TextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                appSettings.musicServerPassword.set(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = txt.ServerPasswordHint,
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(spacing.space8))
                Button(
                    onClick = {
                        saveAll()
                        val url = urlInput.trim()
                        val user = userInput.trim()
                        if (url.isBlank() || user.isBlank()) {
                            testResult = txt.FillAllFirst
                            return@Button
                        }
                        testing = true
                        testResult = null
                        scope.launch {
                            val result = SubsonicClient(url, user, passwordInput).ping()
                            testResult = result.fold(
                                onSuccess = { "${txt.TestOk}：$it" },
                                onFailure = { "${txt.TestFail}：${it.message}" },
                            )
                            testing = false
                        }
                    },
                    enabled = !testing,
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(text = if (testing) txt.Testing else txt.TestConnection)
                }
                testResult?.let { result ->
                    Spacer(modifier = Modifier.height(spacing.space8))
                    MusicSectionTitle(text = result)
                }
                Spacer(modifier = Modifier.height(spacing.space12))
                Text(
                    text = txt.ServerHelp,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
