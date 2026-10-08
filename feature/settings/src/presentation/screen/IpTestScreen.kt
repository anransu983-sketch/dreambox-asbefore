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

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.card.Card
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.icon.FlyCat
import com.suanran.dreambox.presentation.icon.flycat.CircleGauge
import com.suanran.dreambox.presentation.icon.flycat.ScanEye
import com.suanran.dreambox.presentation.icon.flycat.Search
import com.suanran.dreambox.presentation.icon.flycat.ShieldCheck
import com.suanran.dreambox.presentation.icon.flycat.ShieldMinus
import com.suanran.dreambox.presentation.navigation.Navigator
import com.suanran.dreambox.presentation.theme.AppTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class IpPanel(
    val title: String,
    val url: String,
    val icon: ImageVector,
)

/**
 * 在应用内 WebView 打开 IP 测试页，并开启广告拦截。
 * feature 模块够不到 app 模块的 WebViewActivity 类，用显式 Intent（包名 + 类名）。
 */
private fun openIpPanel(context: Context, url: String) {
    val intent =
        Intent(Intent.ACTION_VIEW).setClassName(
            context.packageName,
            "com.suanran.dreambox.WebViewActivity",
        )
    intent.putExtra("initial_url", url)
    intent.putExtra("adblock", true)
    context.startActivity(intent)
}

@Composable
private fun IpPanelIcon(
    imageVector: ImageVector,
    contentDescription: String?,
) {
    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes
    Box(
        modifier =
            Modifier
                .padding(start = spacing.space4, end = spacing.space16)
                .requiredSize(componentSizes.settingsIconSlotSize),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.requiredSize(componentSizes.settingsIconGlyphSize),
        )
    }
}

/**
 * IP 测试模块：IP 查询 + 纯净度检测，应用内打开并拦截广告。
 */
@Composable
fun IpTestScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val txt = FlyTxt.Settings.Experimental

    val panels = listOf(
        IpPanel(txt.IpIpLa, "https://ipip.la", FlyCat.Search),
        IpPanel(txt.CleanIp, "https://cleanip.io", FlyCat.ShieldCheck),
        IpPanel(txt.IpPure, "https://ippure.com", FlyCat.ScanEye),
        IpPanel(txt.Ping0, "https://ping0.cc", FlyCat.CircleGauge),
        IpPanel(txt.Whoer, "https://whoer.net", FlyCat.ShieldCheck),
        IpPanel(txt.IpSb, "https://ip.sb", FlyCat.Search),
        IpPanel(txt.BrowserLeaks, "https://browserleaks.com/ip", FlyCat.ScanEye),
        IpPanel(txt.Scamalytics, "https://scamalytics.com", FlyCat.ShieldMinus),
        IpPanel(txt.IpLark, "https://iplark.com", FlyCat.ScanEye),
        IpPanel(txt.IpCheckIng, "https://ipcheck.ing", FlyCat.ShieldCheck),
        IpPanel(txt.IpScoreXyz, "https://ipscore.xyz", FlyCat.CircleGauge),
        IpPanel(txt.V6Test, "https://v6test.ipgg.cn", FlyCat.Search),
        IpPanel(txt.IpNetCoffee, "https://ip.net.coffee", FlyCat.ScanEye),
    )

    Scaffold(
        topBar = {
            TopBar(
                title = txt.IpTestModule,
                scrollBehavior = scrollBehavior,
                navigationIcon = { NavigationBackIcon(navigator = navigator) },
            )
        },
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                Card {
                    panels.forEach { panel ->
                        ArrowPreference(
                            title = panel.title,
                            summary = panel.url,
                            onClick = { openIpPanel(context, panel.url) },
                            startAction = {
                                IpPanelIcon(
                                    imageVector = panel.icon,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AppTheme.spacing.space32))
            }
        }
    }
}
