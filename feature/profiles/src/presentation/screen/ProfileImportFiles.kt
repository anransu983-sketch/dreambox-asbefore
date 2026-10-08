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

package com.suanran.dreambox.feature.profiles.presentation.screen

import android.app.Application
import android.content.Context
import android.net.Uri
import com.suanran.dreambox.core.model.profile.Profile
import com.suanran.dreambox.core.util.SingBoxConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.UUID

internal fun importedProfileDir(context: Context, profile: Profile): File =
    context.filesDir.importedProfileDir(profile.uuid)

internal fun importedConfigFile(context: Context, profile: Profile): File = importedProfileDir(context, profile).resolve("config.yaml")

internal suspend fun Application.copyProfileImport(uri: Uri, uuid: UUID) {    withContext(Dispatchers.IO) {
        val outputFile = filesDir.importedProfileDir(uuid).resolve("config.yaml")
        outputFile.parentFile?.mkdirs()
        contentResolver.openInputStream(uri)?.use { input ->
            outputFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("Failed to open file: $uri")
        Timber.d("File copied: ${outputFile.absolutePath}")
        // DreamBox: best-effort sing-box JSON -> Mihomo conversion on file import.
        runCatching {
            val text = outputFile.readText()
            if (SingBoxConverter.isSingBoxConfig(text)) {
                val result = SingBoxConverter.convert(text)
                if (result != null && result.converted > 0) {
                    outputFile.writeText(result.yaml)
                    Timber.i(
                        "SingBoxConverter: converted %d proxies (%d skipped) from %s",
                        result.converted,
                        result.skipped,
                        uri,
                    )
                }
            }
        }.onFailure { Timber.w(it, "SingBoxConverter: file import conversion failed, keeping original") }
    }
}

private fun File.importedProfileDir(uuid: UUID): File = resolve("imported").resolve(uuid.toString())

/** 将生成的节点 YAML 直接写入 profile 配置目录（用于剪贴板导入/手动添加）。 */
internal suspend fun Application.writeProfileYaml(yamlContent: String, uuid: UUID) {
    withContext(Dispatchers.IO) {
        val outputFile = filesDir.importedProfileDir(uuid).resolve("config.yaml")
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(yamlContent)
        Timber.d("Profile YAML written: ${outputFile.absolutePath}")
    }
}

/** 用节点列表生成最小可用的 Mihomo 配置 YAML。 */
internal fun buildNodesConfigYaml(nodes: List<Map<String, Any?>>): String {
    val proxies = nodes.map { node ->
        val m = linkedMapOf<String, Any?>()
        node.forEach { (k, v) -> if (v != null) m[k] = v }
        if ((m["name"] as? String).isNullOrBlank()) {
            m["name"] = "${m["server"]}:${m["port"]}"
        }
        m
    }
    val config = linkedMapOf<String, Any?>(
        "proxies" to proxies,
        "proxy-groups" to listOf(
            linkedMapOf(
                "name" to "Proxy",
                "type" to "select",
                "proxies" to proxies.map { it["name"] },
            ),
        ),
        "rules" to listOf("MATCH,Proxy"),
    )
    return com.suanran.dreambox.core.util.YamlCodec.dumpMap(config)
}
