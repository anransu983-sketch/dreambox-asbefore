package com.suanran.dreambox.feature.proxy.presentation.screen.node

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.suanran.dreambox.core.model.proxy.ProxyDisplayMode
import com.suanran.dreambox.core.model.proxy.ProxySortMode
import com.suanran.dreambox.locale.FlyTxt
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.window.WindowListPopup

internal val NodeSortModes =
    listOf(ProxySortMode.DEFAULT, ProxySortMode.BY_NAME, ProxySortMode.BY_LATENCY)

private val ProxySortMode.displayName: String
    get() = when (this) {
        ProxySortMode.DEFAULT -> FlyTxt.Proxy.SortMode.Default
        ProxySortMode.BY_NAME -> FlyTxt.Proxy.SortMode.ByName
        ProxySortMode.BY_LATENCY -> FlyTxt.Proxy.SortMode.ByLatency
    }

/**
 * 排序菜单：定位当前节点 / 默认顺序 / 按名称排序 / 按延迟排序。
 * displayMode 参数保留兼容，不再展示。
 */
@Composable
internal fun NodeSortPopup(
    show: Boolean,
    onDismiss: () -> Unit,
    displayMode: ProxyDisplayMode = ProxyDisplayMode.SINGLE_DETAILED,
    sortMode: ProxySortMode,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.Start,
    onDisplayModeSelected: (ProxyDisplayMode) -> Unit = {},
    onSortSelected: (ProxySortMode) -> Unit,
    onLocateCurrentProxy: (() -> Unit)? = null,
) {
    val selectedSortIndex = NodeSortModes.indexOf(sortMode).coerceAtLeast(0)
    WindowListPopup(
        show = show,
        popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
        alignment = alignment,
        onDismissRequest = onDismiss,
    ) {
        ListPopupColumn {
            if (onLocateCurrentProxy != null) {
                DropdownImpl(
                    text = FlyTxt.Proxy.Action.LocateCurrent,
                    optionSize = 1,
                    isSelected = false,
                    onSelectedIndexChange = {
                        onLocateCurrentProxy()
                        onDismiss()
                    },
                    index = 0,
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            NodeSortModes.forEachIndexed { index, mode ->
                DropdownImpl(
                    text = mode.displayName,
                    optionSize = NodeSortModes.size,
                    isSelected = selectedSortIndex == index,
                    onSelectedIndexChange = {
                        if (mode != sortMode) onSortSelected(mode)
                        onDismiss()
                    },
                    index = index,
                )
            }
        }
    }
}
