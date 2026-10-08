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
import com.suanran.dreambox.core.model.animation.AnimationParseError
import com.suanran.dreambox.core.model.animation.BuiltinAnimationConfigs
import com.suanran.dreambox.core.model.animation.DreamAnimation
import com.suanran.dreambox.core.model.animation.isValidAnimationId
import com.suanran.dreambox.core.model.animation.parseAnimationJson
import com.suanran.dreambox.core.model.animation.toJson
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 动画商店的数据层（对标 ThemeRepository）。
 *
 * 内置动画由 [BuiltinAnimationConfigs] 硬编码提供粒子参数；
 * 用户安装的动画以 JSON 文件存放在 `filesDir/animations/<id>.json`。
 */
class AnimationRepository(
    private val context: Context,
) {
    private val animationsDir: File
        get() = File(context.filesDir, "animations").apply { mkdirs() }

    private fun animationFile(id: String): File = File(animationsDir, "$id.json")

    /** 列出用户安装的动画（按名称排序）。损坏的文件会被跳过。 */
    fun listUserAnimations(): List<DreamAnimation> {
        val dir = animationsDir
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.extension == "json" }
            .orEmpty()
            .mapNotNull { file ->
                runCatching {
                    val id = file.nameWithoutExtension
                    if (!isValidAnimationId(id)) return@runCatching null
                    parseAnimationJson(file.readText()).getOrThrow().copy(builtin = false)
                }.onFailure { e ->
                    Timber.w(e, "AnimationRepository: skip broken animation file ${file.name}")
                }.getOrNull()
            }
            .sortedBy { it.name.lowercase() }
    }

    /** 从 URL 下载动画 JSON 并安装。 */
    suspend fun installFromUrl(url: String): Result<DreamAnimation> = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(AnimationInstallError.EmptyUrl)
        }
        val json = try {
            downloadText(trimmed)
        } catch (e: Exception) {
            Timber.w(e, "AnimationRepository: download failed: $trimmed")
            return@withContext Result.failure(AnimationInstallError.DownloadFailed(e.message ?: "unknown"))
        }
        installFromJson(json, sourceUrl = trimmed)
    }

    /**
     * 从 ZIP 文件导入动画：zip 内需包含 `animation.json`
     * （根目录或任意一级子目录）。
     */
    suspend fun installFromZip(uri: Uri): Result<DreamAnimation> = withContext(Dispatchers.IO) {
        val json = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    var found: String? = null
                    while (entry != null && found == null) {
                        val name = entry.name.trim().trimStart('/')
                        if (!entry.isDirectory &&
                            (name == "animation.json" || name.endsWith("/animation.json"))
                        ) {
                            val bytes = zip.readBytes()
                            if (bytes.size > 256 * 1024) {
                                return@withContext Result.failure(
                                    AnimationInstallError.ReadFailed("animation.json too large")
                                )
                            }
                            found = bytes.toString(Charsets.UTF_8)
                        }
                        entry = zip.nextEntry
                    }
                    found
                }
            } ?: return@withContext Result.failure(AnimationInstallError.ReadFailed("empty"))
        } catch (e: Exception) {
            Timber.w(e, "AnimationRepository: read zip failed: $uri")
            return@withContext Result.failure(AnimationInstallError.ReadFailed(e.message ?: "unknown"))
        }
        installFromJson(json, sourceUrl = null)
    }

    /** 解析动画 JSON 并落盘（sourceUrl 透传，用于后续更新）。 */
    fun installFromJson(json: String, sourceUrl: String?): Result<DreamAnimation> {
        val parsed = parseAnimationJson(json).getOrElse { e ->
            return Result.failure(
                when (e) {
                    is AnimationParseError -> AnimationInstallError.ParseError(e)
                    else -> AnimationInstallError.ParseError(AnimationParseError.NotJson(e.message ?: "unknown"))
                }
            )
        }
        if (BuiltinAnimationConfigs.isBuiltinId(parsed.id)) {
            return Result.failure(AnimationInstallError.ParseError(AnimationParseError.BuiltinConflict(parsed.id)))
        }
        if (animationFile(parsed.id).exists()) {
            return Result.failure(AnimationInstallError.AlreadyExists(parsed.id))
        }
        val animation = parsed.copy(sourceUrl = sourceUrl ?: parsed.sourceUrl, builtin = false)
        return try {
            animationFile(animation.id).writeText(animation.toJson())
            Result.success(animation)
        } catch (e: Exception) {
            Timber.w(e, "AnimationRepository: save animation failed: ${animation.id}")
            Result.failure(AnimationInstallError.SaveFailed(e.message ?: "unknown"))
        }
    }

    /** 删除用户动画。内置动画不可删。 */
    fun deleteAnimation(id: String): Result<Unit> {
        if (BuiltinAnimationConfigs.isBuiltinId(id)) {
            return Result.failure(AnimationInstallError.IsBuiltin(id))
        }
        val file = animationFile(id)
        if (!file.exists()) return Result.failure(AnimationInstallError.NotFound(id))
        return if (file.delete()) Result.success(Unit)
        else Result.failure(AnimationInstallError.SaveFailed("delete failed"))
    }

    /**
     * 检查动画更新：按 [DreamAnimation.sourceUrl] 重新拉取并对比 version。
     * 返回 null 表示无更新；动画没有 sourceUrl 时返回失败。
     */
    suspend fun checkForUpdate(animation: DreamAnimation): Result<DreamAnimation?> = withContext(Dispatchers.IO) {
        val url = animation.sourceUrl?.trim()
            ?: return@withContext Result.failure(AnimationInstallError.NoSourceUrl)
        val json = try {
            downloadText(url)
        } catch (e: Exception) {
            Timber.w(e, "AnimationRepository: update check failed: $url")
            return@withContext Result.failure(AnimationInstallError.DownloadFailed(e.message ?: "unknown"))
        }
        val remote = parseAnimationJson(json).getOrElse { e ->
            return@withContext Result.failure(
                when (e) {
                    is AnimationParseError -> AnimationInstallError.ParseError(e)
                    else -> AnimationInstallError.ParseError(AnimationParseError.NotJson(e.message ?: "unknown"))
                }
            )
        }
        if (remote.id != animation.id) {
            return@withContext Result.failure(
                AnimationInstallError.ParseError(AnimationParseError.InvalidId("id changed: ${remote.id}"))
            )
        }
        if (remote.version == animation.version) {
            return@withContext Result.success(null)
        }
        val updated = remote.copy(sourceUrl = url, builtin = false)
        return@withContext try {
            animationFile(animation.id).writeText(updated.toJson())
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(AnimationInstallError.SaveFailed(e.message ?: "unknown"))
        }
    }

    /** 按 id 读取动画（含内置）；用户动画文件损坏时返回 null。 */
    fun loadAnimation(id: String): DreamAnimation? {
        BuiltinAnimationConfigs.config(id)?.let { return null } // 内置由 UI 层组装本地化名称
        val file = animationFile(id)
        if (!file.isFile) return null
        return runCatching {
            parseAnimationJson(file.readText()).getOrThrow().copy(builtin = false)
        }.onFailure { e ->
            Timber.w(e, "AnimationRepository: load failed: $id")
        }.getOrNull()
    }

    /** 下载小体积文本（动画 JSON），限制 256KB。 */
    private fun downloadText(urlString: String): String {
        val url = URL(urlString)
        require(url.protocol == "http" || url.protocol == "https") { "unsupported protocol: ${url.protocol}" }
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "DreamBox/AnimationStore")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.size > 256 * 1024) throw java.io.IOException("animation json too large")
            return bytes.toString(Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }
}

/** 动画安装/更新/删除失败的原因。 */
sealed class AnimationInstallError(message: String) : Exception(message) {
    data object EmptyUrl : AnimationInstallError("empty_url")
    class DownloadFailed(val detail: String) : AnimationInstallError("download_failed:$detail")
    class ReadFailed(val detail: String) : AnimationInstallError("read_failed:$detail")
    class SaveFailed(val detail: String) : AnimationInstallError("save_failed:$detail")
    class ParseError(val parseError: AnimationParseError) : AnimationInstallError("parse:${parseError.message}")
    class AlreadyExists(val id: String) : AnimationInstallError("already_exists:$id")
    class IsBuiltin(val id: String) : AnimationInstallError("is_builtin:$id")
    class NotFound(val id: String) : AnimationInstallError("not_found:$id")
    data object NoSourceUrl : AnimationInstallError("no_source_url")
}
