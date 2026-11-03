package com.suanran.dreambox.feature.settings.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.suanran.dreambox.feature.settings.presentation.viewmodel.NetworkSettingsViewModel
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.navigation.Navigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold

/**
 * 内核管理独立页（2026-11-03 用户反馈后定案：内核是核心功能，不走插件页；
 * 网络设置里点「当前内核」进这里，切换/升级/自定义内核全是原生界面）。
 * 内容复用 NetworkSettingsViewModel 的内核状态与 KernelManagerSection。
 */
@Composable
fun KernelManagerScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val viewModel = koinViewModel<NetworkSettingsViewModel>()

    Scaffold(
        topBar = {
            TopBar(
                title = FlyTxt.NetworkSettings.Section.Kernel,
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
                KernelManagerSection(viewModel)
            }
        }
    }
}
