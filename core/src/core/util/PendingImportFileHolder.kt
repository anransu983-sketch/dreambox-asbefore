/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
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

package com.suanran.dreambox.core.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared holder for a pending local-file profile import (dreambox://import-file
 * deep link carrying a content/file Uri plus a display name). Used by packs
 * without a subscription URL (e.g. nodeforge sing-box mode): the pack saves a
 * Clash YAML to Downloads and asks the host to create a file profile from it,
 * so the user does not have to pick the file manually on the profiles page.
 */
object PendingImportFileHolder {

    data class PendingFile(val uri: String, val name: String)

    private val _pendingImportFile = MutableStateFlow<PendingFile?>(null)
    val pendingImportFile: StateFlow<PendingFile?> = _pendingImportFile.asStateFlow()

    fun set(uri: String, name: String) {
        _pendingImportFile.value = PendingFile(uri, name)
    }

    fun clear() {
        _pendingImportFile.value = null
    }
}
