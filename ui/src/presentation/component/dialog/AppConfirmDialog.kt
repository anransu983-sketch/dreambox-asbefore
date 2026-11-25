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

package com.suanran.dreambox.presentation.component.dialog

import androidx.compose.runtime.Composable
import com.suanran.dreambox.locale.FlyTxt

@Composable
fun AppConfirmDialog(
    show: Boolean,
    title: String,
    message: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = FlyTxt.Component.Button.Confirm,
    cancelText: String = FlyTxt.Component.Button.Cancel,
    // 确认框必须渲染在根 Scaffold 层：否则在带悬浮底栏的页面（代理页）上，
    // 底栏会盖住按钮行，确认框只剩标题和正文、没法点（1.209 删除节点实测）。
    renderInRootScaffold: Boolean = true,
) {
    AppDialog(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest,
        renderInRootScaffold = renderInRootScaffold,
    ) {
        AppConfirmDialogContent(
            message = message,
            onCancel = onDismissRequest,
            onConfirm = onConfirm,
            cancelText = cancelText,
            confirmText = confirmText,
        )
    }
}
