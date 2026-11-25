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

package com.suanran.dreambox.core.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Per-profile node visibility, stored as `node-visibility.json` inside the profile directory.
 *
 * Two tiers, by design:
 * - [hidden]: nodes the user hid from the proxy page. They are filtered out of the compiled
 *   config (so Auto/url-test never probes or selects them) but stay listed in the app's
 *   "hidden nodes" sheet, where they can be restored.
 * - [deleted]: nodes the user deleted. They are filtered out the same way, but there is no
 *   in-app restore entry for them: they only come back when the profile is re-created
 *   (re-imported / re-configured), which starts from a fresh profile directory.
 *
 * The file lives next to `config.yaml`, so subscription updates keep it (the update flow
 * copies the whole profile directory into staging before replacing the config) and deleting
 * the profile removes it with everything else.
 */
data class NodeVisibility(
    val hidden: Set<String> = emptySet(),
    val deleted: Set<String> = emptySet(),
) {
    /** Every node name that must not reach the compiled config or the node list. */
    val excluded: Set<String>
        get() = hidden + deleted
}

object NodeVisibilityStore {
    const val FILE_NAME = "node-visibility.json"

    @Serializable
    private data class Payload(
        val hidden: List<String> = emptyList(),
        val deleted: List<String> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun load(profileDir: File): NodeVisibility {
        val file = profileDir.resolve(FILE_NAME)
        if (!file.isFile) return NodeVisibility()
        return runCatching {
                val payload = json.decodeFromString(Payload.serializer(), file.readText())
                NodeVisibility(
                    hidden = payload.hidden.filter(String::isNotBlank).toSet(),
                    deleted = payload.deleted.filter(String::isNotBlank).toSet(),
                )
            }
            .getOrDefault(NodeVisibility())
    }

    fun save(profileDir: File, visibility: NodeVisibility) {
        profileDir.mkdirs()
        val payload =
            Payload(
                hidden = visibility.hidden.sorted(),
                deleted = visibility.deleted.sorted(),
            )
        val text = json.encodeToString(Payload.serializer(), payload)
        val target = profileDir.resolve(FILE_NAME)
        val tmp = profileDir.resolve("$FILE_NAME.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }
}
