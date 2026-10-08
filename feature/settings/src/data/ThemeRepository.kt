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

package com.suanran.dreambox.feature.settings.data

import android.content.Context
import android.net.Uri
import com.suanran.dreambox.feature.settings.presentation.theme.DreamTheme
import com.suanran.dreambox.feature.settings.presentation.theme.DreamThemePresets
import com.suanran.dreambox.feature.settings.presentation.theme.THEME_MARKET_CATALOG_URL
import com.suanran.dreambox.feature.settings.presentation.theme.ThemeCatalogEntry
import com.suanran.dreambox.feature.settings.presentation.theme.ThemeParseError
import com.suanran.dreambox.feature.settings.presentation.theme.isValidThemeId
import com.suanran.dreambox.feature.settings.presentation.theme.parseThemeCatalog
import com.suanran.dreambox.feature.settings.presentation.theme.parseThemeJson
import com.suanran.dreambox.feature.settings.presentation.theme.toJson
import java.io.File
import java.net.HttpURLConnection
import java.util.zip.ZipInputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 主题商店的数据层。
 *
 * 内置主题由 [DreamThemePresets] 硬编码提供（本仓库不管理）；
 * 用户安装的主题以 JSON 文件存放在 `filesDir/themes/<id>.json`。
 */
class ThemeRepository(
    private val context: Context,
) {
    private val themesDir: File
        get() = File(context.filesDir, "themes").apply { mkdirs() }

    private fun themeFile(id: String): File = File(themesDir, "$id.json")

    /** 列出用户安装的主题（按名称排序）。损坏的文件会被跳过。 */
    fun listUserThemes(): List<DreamTheme> {
        val dir = themesDir
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.extension == "json" }
            .orEmpty()
            .mapNotNull { file ->
                runCatching {
                    val id = file.nameWithoutExtension
                    if (!isValidThemeId(id)) return@runCatching null
                    parseThemeJson(file.readText()).getOrThrow().copy(builtin = false)
                }.onFailure { e ->
                    Timber.w(e, "ThemeRepository: skip broken theme file ${file.name}")
                }.getOrNull()
            }
            .sortedBy { it.name.lowercase() }
    }

    /** 从 URL 下载主题 JSON 并安装。 */
    suspend fun installFromUrl(url: String): Result<DreamTheme> = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(ThemeInstallError.EmptyUrl)
        }
        val json = try {
            downloadText(trimmed)
        } catch (e: Exception) {
            Timber.w(e, "ThemeRepository: download failed: $trimmed")
            return@withContext Result.failure(ThemeInstallError.DownloadFailed(e.message ?: "unknown"))
        }
        installFromJson(json, sourceUrl = trimmed)
    }

    /** 从文件（分享/下载的 JSON）安装主题。 */
    suspend fun installFromFile(uri: Uri): Result<DreamTheme> = withContext(Dispatchers.IO) {
        val json = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?: return@withContext Result.failure(ThemeInstallError.ReadFailed("empty"))
        } catch (e: Exception) {
            Timber.w(e, "ThemeRepository: read file failed: $uri")
            return@withContext Result.failure(ThemeInstallError.ReadFailed(e.message ?: "unknown"))
        }
        installFromJson(json, sourceUrl = null)
    }

    /**
     * 从 ZIP 文件导入主题：zip 内需包含 `theme.json`
     * （根目录或任意一级子目录）。
     */
    suspend fun installFromZip(uri: Uri): Result<DreamTheme> = withContext(Dispatchers.IO) {
        val json = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    var found: String? = null
                    while (entry != null && found == null) {
                        val name = entry.name.trim().trimStart('/')
                        if (!entry.isDirectory &&
                            (name == "theme.json" || name.endsWith("/theme.json"))
                        ) {
                            val bytes = zip.readBytes()
                            if (bytes.size > 256 * 1024) {
                                return@withContext Result.failure(
                                    ThemeInstallError.ReadFailed("theme.json too large")
                                )
                            }
                            found = bytes.toString(Charsets.UTF_8)
                        }
                        entry = zip.nextEntry
                    }
                    found
                }
            } ?: return@withContext Result.failure(ThemeInstallError.ReadFailed("empty"))
        } catch (e: Exception) {
            Timber.w(e, "ThemeRepository: read zip failed: $uri")
            return@withContext Result.failure(ThemeInstallError.ReadFailed(e.message ?: "unknown"))
        }
        installFromJson(json, sourceUrl = null)
    }

    /** 解析主题 JSON 并落盘（sourceUrl 透传，用于后续更新）。 */
    fun installFromJson(json: String, sourceUrl: String?): Result<DreamTheme> {
        val parsed = parseThemeJson(json).getOrElse { e ->
            return Result.failure(
                when (e) {
                    is ThemeParseError -> ThemeInstallError.ParseError(e)
                    else -> ThemeInstallError.ParseError(ThemeParseError.NotJson(e.message ?: "unknown"))
                }
            )
        }
        if (DreamThemePresets.isBuiltinId(parsed.id)) {
            return Result.failure(ThemeInstallError.ParseError(ThemeParseError.BuiltinConflict(parsed.id)))
        }
        if (themeFile(parsed.id).exists()) {
            return Result.failure(ThemeInstallError.AlreadyExists(parsed.id))
        }
        // 落盘归一化后的 JSON（带上 sourceUrl，便于后续检查更新）
        val theme = parsed.copy(sourceUrl = sourceUrl ?: parsed.sourceUrl, builtin = false)
        return try {
            themeFile(theme.id).writeText(theme.toJson())
            Result.success(theme)
        } catch (e: Exception) {
            Timber.w(e, "ThemeRepository: save theme failed: ${theme.id}")
            Result.failure(ThemeInstallError.SaveFailed(e.message ?: "unknown"))
        }
    }

    /** 删除用户主题。内置主题不可删。 */
    fun deleteTheme(id: String): Result<Unit> {
        if (DreamThemePresets.isBuiltinId(id)) {
            return Result.failure(ThemeInstallError.IsBuiltin(id))
        }
        val file = themeFile(id)
        if (!file.exists()) return Result.failure(ThemeInstallError.NotFound(id))
        return if (file.delete()) Result.success(Unit)
        else Result.failure(ThemeInstallError.SaveFailed("delete failed"))
    }

    /**
     * 检查主题更新：按 [DreamTheme.sourceUrl] 重新拉取并对比 version。
     * 返回 null 表示无更新；主题没有 sourceUrl 时返回失败。
     */
    suspend fun checkForUpdate(theme: DreamTheme): Result<DreamTheme?> = withContext(Dispatchers.IO) {
        val url = theme.sourceUrl?.trim()
            ?: return@withContext Result.failure(ThemeInstallError.NoSourceUrl)
        val json = try {
            downloadText(url)
        } catch (e: Exception) {
            Timber.w(e, "ThemeRepository: update check failed: $url")
            return@withContext Result.failure(ThemeInstallError.DownloadFailed(e.message ?: "unknown"))
        }
        val remote = parseThemeJson(json).getOrElse { e ->
            return@withContext Result.failure(
                when (e) {
                    is ThemeParseError -> ThemeInstallError.ParseError(e)
                    else -> ThemeInstallError.ParseError(ThemeParseError.NotJson(e.message ?: "unknown"))
                }
            )
        }
        if (remote.id != theme.id) {
            return@withContext Result.failure(
                ThemeInstallError.ParseError(ThemeParseError.InvalidId("id changed: ${remote.id}"))
            )
        }
        if (remote.version == theme.version) {
            return@withContext Result.success(null)
        }
        // 落盘新版本（归一化，保留 sourceUrl）
        val updated = remote.copy(sourceUrl = url, builtin = false)
        return@withContext try {
            themeFile(theme.id).writeText(updated.toJson())
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(ThemeInstallError.SaveFailed(e.message ?: "unknown"))
        }
    }

    /** 拉取主题市场目录。 */
    suspend fun fetchMarketCatalog(): Result<List<ThemeCatalogEntry>> = withContext(Dispatchers.IO) {
        val json = try {
            downloadText(THEME_MARKET_CATALOG_URL)
        } catch (e: Exception) {
            Timber.w(e, "ThemeRepository: market catalog download failed")
            return@withContext Result.failure(ThemeInstallError.DownloadFailed(e.message ?: "unknown"))
        }
        parseThemeCatalog(json).fold(
            onSuccess = { catalog ->
                Result.success(catalog.themes.filter { it.id.isNotBlank() && it.download.isNotBlank() })
            },
            onFailure = { e ->
                Result.failure(
                    when (e) {
                        is ThemeParseError -> ThemeInstallError.ParseError(e)
                        else -> ThemeInstallError.ParseError(ThemeParseError.NotJson(e.message ?: "unknown"))
                    }
                )
            },
        )
    }

    /** 下载小体积文本（主题 JSON），限制 256KB。 */
    private fun downloadText(urlString: String): String {
        val url = URL(urlString)
        require(url.protocol == "http" || url.protocol == "https") { "unsupported protocol: ${url.protocol}" }
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "DreamBox/ThemeStore")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.size > 256 * 1024) throw java.io.IOException("theme json too large")
            return bytes.toString(Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }
}

/** 主题安装/更新/删除失败的原因。 */
sealed class ThemeInstallError(message: String) : Exception(message) {
    data object EmptyUrl : ThemeInstallError("empty_url")
    class DownloadFailed(val detail: String) : ThemeInstallError("download_failed:$detail")
    class ReadFailed(val detail: String) : ThemeInstallError("read_failed:$detail")
    class SaveFailed(val detail: String) : ThemeInstallError("save_failed:$detail")
    class ParseError(val parseError: ThemeParseError) : ThemeInstallError("parse:${parseError.message}")
    class AlreadyExists(val id: String) : ThemeInstallError("already_exists:$id")
    class IsBuiltin(val id: String) : ThemeInstallError("is_builtin:$id")
    class NotFound(val id: String) : ThemeInstallError("not_found:$id")
    data object NoSourceUrl : ThemeInstallError("no_source_url")
}
