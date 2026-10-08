package com.suanran.dreambox.presentation.component.sortable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 模块"..."菜单按钮：点击弹出编辑/隐藏/删除菜单（参考截图样式）。
 *
 * @param onEdit 为空则不显示"编辑"
 * @param onHide 隐藏回调（可恢复）
 * @param onDelete 为空则不显示"删除"（永久删除，用于自定义模块）
 */
@Composable
fun ModuleMenuButton(
    onEdit: (() -> Unit)? = null,
    onHide: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var showMenu by remember { mutableStateOf(false) }

    Icon(
        imageVector = Icons.Filled.MoreVert,
        contentDescription = "更多",
        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.size(20.dp)
            .clickable { showMenu = true },
    )

    if (showMenu) {
        val items = mutableListOf<DropdownItem>()
        onEdit?.let { edit ->
            items.add(
                DropdownItem(
                    text = "编辑",
                    icon = { mod ->
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = mod.size(20.dp),
                        )
                    },
                    onClick = {
                        showMenu = false
                        edit()
                    },
                )
            )
        }
        items.add(
            DropdownItem(
                text = "隐藏",
                icon = { mod ->
                    Icon(
                        imageVector = Icons.Filled.VisibilityOff,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = mod.size(20.dp),
                    )
                },
                onClick = {
                    showMenu = false
                    onHide()
                },
            )
        )
        onDelete?.let { delete ->
            items.add(
                DropdownItem(
                    text = "删除",
                    icon = { mod ->
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.error,
                            modifier = mod.size(20.dp),
                        )
                    },
                    onClick = {
                        showMenu = false
                        delete()
                    },
                )
            )
        }

        OverlayCascadingListPopup(
            show = true,
            entries = listOf(DropdownEntry(items = items)),
            onDismissRequest = { showMenu = false },
        )
    }
}
