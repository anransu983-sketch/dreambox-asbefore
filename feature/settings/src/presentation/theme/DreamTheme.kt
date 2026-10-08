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

package com.suanran.dreambox.feature.settings.presentation.theme

import com.suanran.dreambox.core.model.ThemeMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 主题商店的主题数据模型。
 *
 * 内置主题由 [DreamThemePresets] 硬编码提供；用户安装的主题以 JSON 文件
 * 存放在 `context.filesDir/themes/<id>.json`，通过 URL 或文件安装。
 */
data class DreamTheme(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val themeMode: ThemeMode,
    val seedColorArgb: Long,
    /** 安装来源 URL（可空）。为空时不支持在线更新。 */
    val sourceUrl: String?,
    val builtin: Boolean,
) {
    /** 版本徽标文本：内置显示 "v1.0·内置"，用户主题显示 "v1.0"。 */
    fun versionBadge(builtInLabel: String): String =
        if (builtin) "v$version·$builtInLabel" else "v$version"
}

/**
 * 主题 JSON 的线上传输格式（供 URL / 文件安装）。
 *
 * ```json
 * {"id":"my-theme","name":"我的主题","version":"1.0","author":"作者",
 *  "description":"描述","themeMode":"dark","seedColor":"#FF6B9D",
 *  "sourceUrl":"https://..."}
 * ```
 */
@Serializable
data class ThemeJson(
    val id: String = "",
    val name: String = "",
    val version: String = "1.0",
    val author: String = "",
    val description: String = "",
    /** dark / light / system（大小写不敏感），缺省 system。 */
    val themeMode: String = "system",
    /** #RRGGBB 或 #AARRGGBB。 */
    val seedColor: String = "",
    val sourceUrl: String? = null,
)

/** 主题 JSON 解析/校验失败的原因（带可展示的明细）。 */
sealed class ThemeParseError(message: String) : Exception(message) {
    class NotJson(detail: String) : ThemeParseError("not_json:$detail")
    class MissingId : ThemeParseError("missing_id")
    class InvalidId(val id: String) : ThemeParseError("invalid_id:$id")
    class MissingName : ThemeParseError("missing_name")
    class InvalidThemeMode(val value: String) : ThemeParseError("invalid_mode:$value")    class InvalidSeedColor(val value: String) : ThemeParseError("invalid_color:$value")
    class BuiltinConflict(val id: String) : ThemeParseError("builtin_conflict:$id")
}

private val themeJsonParser = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

private val THEME_ID_REGEX = Regex("^[a-z0-9][a-z0-9-]{0,31}$")

/** 校验主题 id（小写字母/数字/连字符，防目录穿越）。 */
fun isValidThemeId(id: String): Boolean = THEME_ID_REGEX.matches(id)

/**
 * 解析主题 JSON 文本为 [DreamTheme]（builtin 恒为 false，来源为用户安装）。
 *
 * 失败时返回 [ThemeParseError] 子类，调用方可据此给出明确错误提示。
 */
fun parseThemeJson(json: String, builtin: Boolean = false): Result<DreamTheme> {
    val wire: ThemeJson = try {
        themeJsonParser.decodeFromString(ThemeJson.serializer(), json)
    } catch (e: Exception) {
        return Result.failure(ThemeParseError.NotJson(e.message ?: "unknown"))
    }
    if (wire.id.isBlank()) return Result.failure(ThemeParseError.MissingId())
    val id = wire.id.trim()
    if (!isValidThemeId(id)) return Result.failure(ThemeParseError.InvalidId(id))
    if (builtin && DreamThemePresets.isBuiltinId(id)) {
        return Result.failure(ThemeParseError.BuiltinConflict(id))
    }
    if (wire.name.isBlank()) return Result.failure(ThemeParseError.MissingName())
    val mode = parseThemeMode(wire.themeMode).getOrElse { return Result.failure(it) }
    val seedArgb = parseSeedColor(wire.seedColor).getOrElse { return Result.failure(it) }
    return Result.success(
        DreamTheme(
            id = id,
            name = wire.name.trim(),
            version = wire.version.trim().ifEmpty { "1.0" },
            author = wire.author.trim(),
            description = wire.description.trim(),
            themeMode = mode,
            seedColorArgb = seedArgb,
            sourceUrl = wire.sourceUrl?.trim()?.ifEmpty { null },
            builtin = builtin,
        )
    )
}

/** 解析 themeMode：dark / light / system（大小写不敏感，含 auto 别名）。 */
fun parseThemeMode(value: String): Result<ThemeMode> {
    return when (value.trim().lowercase()) {
        "dark" -> Result.success(ThemeMode.Dark)
        "light" -> Result.success(ThemeMode.Light)
        "system", "auto", "" -> Result.success(ThemeMode.Auto)
        else -> Result.failure(ThemeParseError.InvalidThemeMode(value))
    }
}

/** 解析 seedColor：#RRGGBB 或 #AARRGGBB（# 可省略）。 */
fun parseSeedColor(value: String): Result<Long> {
    val hex = value.trim().removePrefix("#")
    if (hex.isEmpty()) return Result.failure(ThemeParseError.InvalidSeedColor(value))
    val argb: Long = try {
        when (hex.length) {
            6 -> 0xFF000000L or hex.toLong(16)
            8 -> hex.toLong(16)
            else -> return Result.failure(ThemeParseError.InvalidSeedColor(value))
        }
    } catch (e: NumberFormatException) {
        return Result.failure(ThemeParseError.InvalidSeedColor(value))
    }
    return Result.success(argb)
}

/** 将主题序列化为可存储/分享的 JSON 文本。 */
fun DreamTheme.toJson(): String {
    val modeString = when (themeMode) {
        ThemeMode.Dark -> "dark"
        ThemeMode.Light -> "light"
        ThemeMode.Auto -> "system"
    }
    val colorHex = "#%08X".format(seedColorArgb)
    val wire = ThemeJson(
        id = id,
        name = name,
        version = version,
        author = author,
        description = description,
        themeMode = modeString,
        seedColor = colorHex,
        sourceUrl = sourceUrl,
    )
    return Json { prettyPrint = true }.encodeToString(ThemeJson.serializer(), wire)
}

/** 主题市场目录地址（仓库内 theme-market/catalog.json 的 raw 地址）。 */
const val THEME_MARKET_CATALOG_URL =
    "https://raw.githubusercontent.com/anransu983-sketch/dreambox-asbefore/main/theme-market/catalog.json"

/**
 * 主题市场目录条目（对标 Komari theme-market 的 v1.json 格式）。
 */
@Serializable
data class ThemeCatalogEntry(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val version: String = "1.0",
    val author: String = "",
    val url: String = "",
    /** dark / light / system */
    val themeMode: String = "dark",
    /** #RRGGBB 或 #AARRGGBB */
    val seedColor: String = "",
    /** 预览色板 */
    val preview: List<String> = emptyList(),
    /** 主题 JSON 下载地址 */
    val download: String = "",
)

/** 主题市场目录。 */
@Serializable
data class ThemeCatalog(
    val version: Int = 1,
    val themes: List<ThemeCatalogEntry> = emptyList(),
)

/** 解析主题市场目录 JSON。 */
fun parseThemeCatalog(json: String): Result<ThemeCatalog> {
    return try {
        Result.success(themeJsonParser.decodeFromString(ThemeCatalog.serializer(), json))
    } catch (e: Exception) {
        Result.failure(ThemeParseError.NotJson(e.message ?: "unknown"))
    }
}

/** 目录条目转 DreamTheme（用于预览色板与安装前展示）。 */
fun ThemeCatalogEntry.toPreviewTheme(): DreamTheme? {
    val mode = parseThemeMode(themeMode).getOrNull() ?: return null
    val seedArgb = parseSeedColor(seedColor).getOrNull() ?: return null
    if (!isValidThemeId(id.trim())) return null
    return DreamTheme(
        id = id.trim(),
        name = name.trim().ifEmpty { id.trim() },
        version = version.trim().ifEmpty { "1.0" },
        author = author.trim(),
        description = description.trim(),
        themeMode = mode,
        seedColorArgb = seedArgb,
        sourceUrl = download.trim().ifEmpty { null },
        builtin = false,
    )
}
