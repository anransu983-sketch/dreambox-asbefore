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

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * [AssetDownloader] 的默认实现（HttpURLConnection，无新增依赖）。
 *
 * 用于 GeoX 等资源文件的下载。
 */
class HttpAssetDownloader : AssetDownloader {
    override suspend fun download(url: String, targetFile: File): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val parsed = URL(url)
                require(parsed.protocol == "http" || parsed.protocol == "https") {
                    "unsupported protocol: ${parsed.protocol}"
                }
                val conn = (parsed.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "DreamBox/AssetDownloader")
                }
                try {
                    val code = conn.responseCode
                    if (code !in 200..299) throw java.io.IOException("HTTP $code")
                    targetFile.parentFile?.mkdirs()
                    val tmp = File(targetFile.parentFile, "${targetFile.name}.tmp")
                    conn.inputStream.use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (targetFile.exists()) targetFile.delete()
                    if (!tmp.renameTo(targetFile)) throw java.io.IOException("rename failed")
                } finally {
                    conn.disconnect()
                }
            }.onFailure { e ->
                Timber.w(e, "HttpAssetDownloader: download failed: $url")
            }.isSuccess
        }
}
