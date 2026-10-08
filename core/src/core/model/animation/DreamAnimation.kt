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

package com.suanran.dreambox.core.model.animation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 动画商店的动画数据模型（对标主题商店 DreamTheme）。
 *
 * 内置动画由 [BuiltinAnimationConfigs] 提供粒子参数（本仓库硬编码）；
 * 用户安装的动画以 JSON 文件存放在 `context.filesDir/animations/<id>.json`。
 */
data class DreamAnimation(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val particles: AnimationParticles,
    /** 安装来源 URL（可空）。为空时不支持在线更新。 */
    val sourceUrl: String?,
    val builtin: Boolean,
) {
    /** 版本徽标文本：内置显示 "v1.0·内置"，用户动画显示 "v1.0"。 */
    fun versionBadge(builtInLabel: String): String =
        if (builtin) "v$version·$builtInLabel" else "v$version"
}

/** 粒子形状。 */
enum class AnimationParticleType {
    Circle, Line, Petal, Rect,
}

/** 粒子生成位置。 */
enum class AnimationSpawn {
    Top, Random, Bottom, Center,
}

/** 粒子行为。 */
enum class AnimationBehavior {
    Fall, Rise, Explode, Float,
}

/** 解析后的粒子参数（领域模型）。 */
data class AnimationParticles(
    val type: AnimationParticleType,
    val count: Int,
    /** 基准尺寸：圆点=半径，线=长度，花瓣/方块=半宽。 */
    val size: Float,
    val opacity: Float,
    /** 粒子颜色 ARGB；null 表示跟随主题自适应。 */
    val colorArgb: Long?,
    val spawn: AnimationSpawn,
    val behavior: AnimationBehavior,
    /** 速度倍率（基准 1.0）。 */
    val speed: Float,
    /** 风力（影响水平漂移/倾斜）。 */
    val wind: Float,
    val sway: Boolean,
    val rotation: Boolean,
    /** 重力加速度（爆炸粒子下落用）。 */
    val gravity: Float,
)

/**
 * 动画 JSON 的线上传输格式（供 URL / ZIP 安装）。
 *
 * ```json
 * {"id":"sakura","name":"樱花飘落","version":"1.0","author":"梦盒",
 *  "description":"粉色花瓣飘落","sourceUrl":"https://...",
 *  "particles":{"type":"petal","count":80,"size":8.0,"opacity":0.8,
 *   "color":"#FFB7C5","spawn":"top","behavior":"fall",
 *   "speed":1.0,"wind":0.5,"sway":true,"rotation":true,"gravity":0.0}}
 * ```
 */
@Serializable
data class AnimationJson(
    val id: String = "",
    val name: String = "",
    val version: String = "1.0",
    val author: String = "",
    val description: String = "",
    val sourceUrl: String? = null,
    val particles: AnimationParticlesJson = AnimationParticlesJson(),
)

@Serializable
data class AnimationParticlesJson(
    /** circle / line / petal / rect（大小写不敏感），缺省 circle。 */
    val type: String = "circle",
    val count: Int = 60,
    val size: Float = 4f,
    val opacity: Float = 0.8f,
    /** #RRGGBB 或 #AARRGGBB；空表示跟随主题。 */
    val color: String = "",
    /** top / random / bottom / center，缺省 top。 */
    val spawn: String = "top",
    /** fall / rise / explode / float，缺省 fall。 */
    val behavior: String = "fall",
    val speed: Float = 1f,
    val wind: Float = 0f,
    val sway: Boolean = false,
    val rotation: Boolean = false,
    val gravity: Float = 0f,
)

/** 动画 JSON 解析/校验失败的原因。 */
sealed class AnimationParseError(message: String) : Exception(message) {
    class NotJson(detail: String) : AnimationParseError("not_json:$detail")
    class MissingId : AnimationParseError("missing_id")
    class InvalidId(val id: String) : AnimationParseError("invalid_id:$id")
    class MissingName : AnimationParseError("missing_name")
    class InvalidParticleType(val value: String) : AnimationParseError("invalid_type:$value")
    class InvalidSpawn(val value: String) : AnimationParseError("invalid_spawn:$value")
    class InvalidBehavior(val value: String) : AnimationParseError("invalid_behavior:$value")
    class InvalidColor(val value: String) : AnimationParseError("invalid_color:$value")
    class BuiltinConflict(val id: String) : AnimationParseError("builtin_conflict:$id")
}

private val animationJsonParser = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

private val ANIMATION_ID_REGEX = Regex("^[a-z0-9][a-z0-9-]{0,31}$")

/** 校验动画 id（小写字母/数字/连字符，防目录穿越）。 */
fun isValidAnimationId(id: String): Boolean = ANIMATION_ID_REGEX.matches(id)

fun parseParticleType(value: String): Result<AnimationParticleType> =
    when (value.trim().lowercase()) {
        "circle", "" -> Result.success(AnimationParticleType.Circle)
        "line" -> Result.success(AnimationParticleType.Line)
        "petal" -> Result.success(AnimationParticleType.Petal)
        "rect" -> Result.success(AnimationParticleType.Rect)
        else -> Result.failure(AnimationParseError.InvalidParticleType(value))
    }

fun parseSpawn(value: String): Result<AnimationSpawn> =
    when (value.trim().lowercase()) {
        "top", "" -> Result.success(AnimationSpawn.Top)
        "random" -> Result.success(AnimationSpawn.Random)
        "bottom" -> Result.success(AnimationSpawn.Bottom)
        "center" -> Result.success(AnimationSpawn.Center)
        else -> Result.failure(AnimationParseError.InvalidSpawn(value))
    }

fun parseBehavior(value: String): Result<AnimationBehavior> =
    when (value.trim().lowercase()) {
        "fall", "" -> Result.success(AnimationBehavior.Fall)
        "rise" -> Result.success(AnimationBehavior.Rise)
        "explode" -> Result.success(AnimationBehavior.Explode)
        "float" -> Result.success(AnimationBehavior.Float)
        else -> Result.failure(AnimationParseError.InvalidBehavior(value))
    }

/** 解析颜色：#RRGGBB 或 #AARRGGBB；空返回 null（跟随主题）。 */
fun parseAnimationColor(value: String): Result<Long?> {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return Result.success(null)
    val hex = trimmed.removePrefix("#")
    val argb: Long = try {
        when (hex.length) {
            6 -> 0xFF000000L or hex.toLong(16)
            8 -> hex.toLong(16)
            else -> return Result.failure(AnimationParseError.InvalidColor(value))
        }
    } catch (e: NumberFormatException) {
        return Result.failure(AnimationParseError.InvalidColor(value))
    }
    return Result.success(argb)
}

/**
 * 解析动画 JSON 文本为 [DreamAnimation]（builtin 恒为 false，来源为用户安装）。
 */
fun parseAnimationJson(json: String): Result<DreamAnimation> {
    val wire: AnimationJson = try {
        animationJsonParser.decodeFromString(AnimationJson.serializer(), json)
    } catch (e: Exception) {
        return Result.failure(AnimationParseError.NotJson(e.message ?: "unknown"))
    }
    if (wire.id.isBlank()) return Result.failure(AnimationParseError.MissingId())
    val id = wire.id.trim()
    if (!isValidAnimationId(id)) return Result.failure(AnimationParseError.InvalidId(id))
    if (BuiltinAnimationConfigs.isBuiltinId(id)) {
        return Result.failure(AnimationParseError.BuiltinConflict(id))
    }
    if (wire.name.isBlank()) return Result.failure(AnimationParseError.MissingName())
    val p = wire.particles
    val type = parseParticleType(p.type).getOrElse { return Result.failure(it) }
    val spawn = parseSpawn(p.spawn).getOrElse { return Result.failure(it) }
    val behavior = parseBehavior(p.behavior).getOrElse { return Result.failure(it) }
    val colorArgb = parseAnimationColor(p.color).getOrElse { return Result.failure(it) }
    return Result.success(
        DreamAnimation(
            id = id,
            name = wire.name.trim(),
            version = wire.version.trim().ifEmpty { "1.0" },
            author = wire.author.trim(),
            description = wire.description.trim(),
            particles =
                AnimationParticles(
                    type = type,
                    count = p.count.coerceIn(1, 300),
                    size = p.size.coerceIn(0.5f, 100f),
                    opacity = p.opacity.coerceIn(0.05f, 1f),
                    colorArgb = colorArgb,
                    spawn = spawn,
                    behavior = behavior,
                    speed = p.speed.coerceIn(0.1f, 10f),
                    wind = p.wind.coerceIn(-5f, 5f),
                    sway = p.sway,
                    rotation = p.rotation,
                    gravity = p.gravity.coerceIn(0f, 2000f),
                ),
            sourceUrl = wire.sourceUrl?.trim()?.ifEmpty { null },
            builtin = false,
        )
    )
}

/** 将动画序列化为可存储/分享的 JSON 文本。 */
fun DreamAnimation.toJson(): String {
    val p = particles
    val wire =
        AnimationJson(
            id = id,
            name = name,
            version = version,
            author = author,
            description = description,
            sourceUrl = sourceUrl,
            particles =
                AnimationParticlesJson(
                    type =
                        when (p.type) {
                            AnimationParticleType.Circle -> "circle"
                            AnimationParticleType.Line -> "line"
                            AnimationParticleType.Petal -> "petal"
                            AnimationParticleType.Rect -> "rect"
                        },
                    count = p.count,
                    size = p.size,
                    opacity = p.opacity,
                    color = p.colorArgb?.let { "#%08X".format(it) } ?: "",
                    spawn =
                        when (p.spawn) {
                            AnimationSpawn.Top -> "top"
                            AnimationSpawn.Random -> "random"
                            AnimationSpawn.Bottom -> "bottom"
                            AnimationSpawn.Center -> "center"
                        },
                    behavior =
                        when (p.behavior) {
                            AnimationBehavior.Fall -> "fall"
                            AnimationBehavior.Rise -> "rise"
                            AnimationBehavior.Explode -> "explode"
                            AnimationBehavior.Float -> "float"
                        },
                    speed = p.speed,
                    wind = p.wind,
                    sway = p.sway,
                    rotation = p.rotation,
                    gravity = p.gravity,
                ),
        )
    return Json { prettyPrint = true }.encodeToString(AnimationJson.serializer(), wire)
}

/**
 * 内置动画的粒子参数（纯数据，不含本地化名称）。
 * 对应旧版硬编码的降雪/烟花/下雨行为。
 */
object BuiltinAnimationConfigs {
    const val SNOW_ID = "snow"
    const val FIREWORKS_ID = "fireworks"
    const val RAIN_ID = "rain"

    private val builtinIds = setOf(SNOW_ID, FIREWORKS_ID, RAIN_ID)

    fun isBuiltinId(id: String): Boolean = id in builtinIds

    /** 内置动画的粒子参数（不含名称/描述，UI 层负责本地化）。 */
    fun config(id: String): AnimationParticles? =
        when (id) {
            SNOW_ID ->
                AnimationParticles(
                    type = AnimationParticleType.Circle,
                    count = 55,
                    size = 4f,
                    opacity = 0.8f,
                    colorArgb = null,
                    spawn = AnimationSpawn.Top,
                    behavior = AnimationBehavior.Fall,
                    speed = 1f,
                    wind = 1f,
                    sway = true,
                    rotation = false,
                    gravity = 0f,
                )
            FIREWORKS_ID ->
                AnimationParticles(
                    type = AnimationParticleType.Circle,
                    count = 60,
                    size = 2.5f,
                    opacity = 1f,
                    colorArgb = null,
                    spawn = AnimationSpawn.Bottom,
                    behavior = AnimationBehavior.Explode,
                    speed = 1f,
                    wind = 0f,
                    sway = false,
                    rotation = false,
                    gravity = 160f,
                )
            RAIN_ID ->
                AnimationParticles(
                    type = AnimationParticleType.Line,
                    count = 90,
                    size = 15f,
                    opacity = 0.45f,
                    colorArgb = null,
                    spawn = AnimationSpawn.Top,
                    behavior = AnimationBehavior.Fall,
                    speed = 1f,
                    wind = 0.3f,
                    sway = false,
                    rotation = false,
                    gravity = 0f,
                )
            else -> null
        }
}
