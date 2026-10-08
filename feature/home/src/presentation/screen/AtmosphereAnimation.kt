package com.suanran.dreambox.feature.home.presentation.screen

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suanran.dreambox.core.contract.AppSettingsReader
import com.suanran.dreambox.core.model.animation.BuiltinAnimationConfigs
import com.suanran.dreambox.core.model.animation.DreamAnimation
import com.suanran.dreambox.core.model.animation.parseAnimationJson
import kotlinx.coroutines.isActive
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.theme.MiuixTheme
import timber.log.Timber
import java.io.File
import kotlin.random.Random

/** 首页氛围动画覆盖层：不拦截点击，仅在首页可见且开屏时运行。 */
@Composable
fun AtmosphereOverlay(isActive: Boolean) {
    val appSettings = koinInject<AppSettingsReader>()
    val context = LocalContext.current
    val enabled by appSettings.animationEnabled.state.collectAsStateWithLifecycle()
    val activeId by appSettings.activeAnimationId.state.collectAsStateWithLifecycle()
    if (!enabled || activeId.isBlank()) return

    // 解析当前动画：内置查配置表，用户动画读 filesDir/animations/<id>.json
    val animation = remember(activeId) { loadDreamAnimation(context, activeId) } ?: return

    // 参数覆盖（0 = 用 JSON 默认值）
    val countOverride by appSettings.animCountOverride.state.collectAsStateWithLifecycle()
    val speedOverride by appSettings.animSpeedOverride.state.collectAsStateWithLifecycle()
    val opacityOverride by appSettings.animOpacityOverride.state.collectAsStateWithLifecycle()

    val baseColor = MiuixTheme.colorScheme.onBackground

    val particles = remember(activeId) { mutableListOf<AnimParticle>() }
    var tick by remember(activeId) { mutableStateOf(0L) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(activeId) {
        var lastNanos = 0L
        val random = Random(System.currentTimeMillis())
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos == 0L) lastNanos = nanos
                val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastNanos = nanos
                val w = canvasSize.width.toFloat()
                val h = canvasSize.height.toFloat()
                if (w > 0 && h > 0) {
                    val p = animation.particles
                    val count = (if (countOverride > 0) countOverride else p.count)
                        .coerceIn(1, ANIMATION_MAX_PARTICLES)
                    val speedMul = if (speedOverride > 0f) speedOverride else 1f
                    updateAnimationParticles(
                        particles, animation, dt, w, h, random, baseColor, count, speedMul,
                    )
                }
                tick++
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (canvasSize != size.toIntSize()) canvasSize = size.toIntSize()
        // 读取 tick 触发重绘
        @Suppress("UNUSED_EXPRESSION")
        tick
        val opacityMul = if (opacityOverride > 0f) opacityOverride else 1f
        drawAnimationParticles(particles, animation, baseColor, opacityMul)
    }
}

private fun Size.toIntSize(): IntSize = IntSize(width.toInt(), height.toInt())

/**
 * 加载动画定义：内置返回硬编码配置（名称用 id 占位，覆盖层只负责渲染）；
 * 用户动画从 `filesDir/animations/<id>.json` 读取解析。
 */
private fun loadDreamAnimation(context: Context, id: String): DreamAnimation? {
    BuiltinAnimationConfigs.config(id)?.let { particles ->
        return DreamAnimation(
            id = id,
            name = id,
            version = "1.0",
            author = "",
            description = "",
            particles = particles,
            sourceUrl = null,
            builtin = true,
        )
    }
    val file = File(File(context.filesDir, "animations"), "$id.json")
    if (!file.isFile) return null
    return runCatching {
        parseAnimationJson(file.readText()).getOrThrow().copy(builtin = false)
    }.onFailure { e ->
        Timber.w(e, "AtmosphereOverlay: parse failed: $id")
    }.getOrNull()
}
