package com.suanran.dreambox.feature.settings.presentation.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.misc.BocchiCard
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.navigation.Navigator
import com.suanran.dreambox.presentation.theme.AppTheme
import com.tencent.mmkv.MMKV
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold

/**
 * 个性化子页组（主题页点进去版）：每块设置一个独立子页，
 * 骨架统一 Scaffold + TopBar + 一张 BocchiCard 装内容。
 */
@Composable
private fun PersonalizeSubPage(
    navigator: Navigator,
    title: String,
    content: @Composable () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopBar(
                title = title,
                scrollBehavior = scrollBehavior,
                navigationIconPadding = 0.dp,
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
                BocchiCard { content() }
            }
        }
    }
}

@Composable
fun AppIconScreen(navigator: Navigator) {
    PersonalizeSubPage(navigator, "应用图标") { AppIconBlock() }
}

@Composable
fun IconWorkshopScreen(navigator: Navigator) {
    val mmkv: MMKV = koinInject(named("settings"))
    val spacing = AppTheme.spacing
    PersonalizeSubPage(navigator, "自定义图标工坊") {
        Column(
            modifier = Modifier.padding(horizontal = spacing.space16, vertical = spacing.space14),
        ) {
            IconWorkshopBlock(mmkv = mmkv)
        }
    }
}

@Composable
fun NavIconsScreen(navigator: Navigator) {
    PersonalizeSubPage(navigator, "导航栏图标") { NavIconsBlock() }
}

@Composable
fun ModuleIconsScreen(navigator: Navigator) {
    PersonalizeSubPage(navigator, "模块图标") { ModuleIconsBlock() }
}

@Composable
fun BackButtonIconScreen(navigator: Navigator) {
    PersonalizeSubPage(navigator, "返回按钮图标") { BackButtonBlock() }
}

@Composable
fun HomePanelsScreen(navigator: Navigator) {
    PersonalizeSubPage(navigator, "首页面板") { HomePanelsBlock() }
}

@Composable
fun SparklineSettingsScreen(navigator: Navigator) {
    PersonalizeSubPage(navigator, "延迟曲线") { SparklineBlock() }
}
