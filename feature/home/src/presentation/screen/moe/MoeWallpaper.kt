/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * Copyright (c) YumeYucca 2025 - Present
 * Based on YumeBox by YumeYucca
 */

package com.suanran.dreambox.feature.home.presentation.screen.moe

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.suanran.dreambox.presentation.component.misc.calculateWallpaperViewportLayout
import com.github.panpf.sketch.cache.CachePolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suanran.dreambox.core.contract.AppSettingsReader
import com.github.panpf.sketch.rememberAsyncImagePainter
import org.koin.compose.koinInject
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.resize.Precision
import com.github.panpf.sketch.resize.Scale
import com.github.panpf.sketch.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun MoeWallpaperBackground(
    wallpaperUri: String,
    wallpaperZoom: Float = 1f,
    wallpaperBiasX: Float = 0f,
    wallpaperBiasY: Float = 0f,
    qualityMode: MoeWallpaperQualityMode = MoeWallpaperQualityMode.Foreground,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val model by
        produceState(wallpaperUri.ifBlank { BUNDLED_WALLPAPER }, wallpaperUri) {
            value = withContext(Dispatchers.IO) { resolveWallpaperModel(context, wallpaperUri) }
        }
    val imageBounds by produceState<Pair<Int, Int>?>(null, model) {
        value = if (model.startsWith("file:///android_asset/")) null else readImageBounds(context, model)
    }

    // 视频壁纸：ExoPlayer 循环静音播放
    val isVideo = remember(model) { isVideoWallpaperUri(context, model) }
    if (isVideo) {
        VideoWallpaperBackground(model, modifier = modifier)
        return
    }

    BoxWithConstraints(modifier = modifier) {
        val width = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val height = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val painter = rememberWallpaperPainter(context, model, width, height, qualityMode)
        val intrinsic = painter.intrinsicSize
        val layout = calculateWallpaperViewportLayout(
            containerWidthPx = width,
            containerHeightPx = height,
            imageWidthPx = intrinsic.width.takeIf { it > 0f && it.isFinite() } ?: imageBounds?.first?.toFloat(),
            imageHeightPx = intrinsic.height.takeIf { it > 0f && it.isFinite() } ?: imageBounds?.second?.toFloat(),
            zoom = wallpaperZoom.coerceIn(1f, 5f),
            biasX = wallpaperBiasX,
            biasY = wallpaperBiasY,
        )
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(layout.biasX, layout.biasY),
            modifier = Modifier.align(Alignment.Center).fillMaxSize(),
        )
    }
}

@Composable
private fun rememberWallpaperPainter(
    context: Context,
    model: String,
    width: Float,
    height: Float,
    quality: MoeWallpaperQualityMode,
) = rememberAsyncImagePainter(
    request = ImageRequest(context, model) {
        scale(Scale.CENTER_CROP)
        memoryCachePolicy(CachePolicy.DISABLED)
        downloadCachePolicy(CachePolicy.DISABLED)
        resultCachePolicy(CachePolicy.DISABLED)
        if (quality == MoeWallpaperQualityMode.BackgroundBlur) {
            size(kotlin.math.ceil(width * 1.2f).toInt(), kotlin.math.ceil(height * 1.2f).toInt())
            precision(Precision.LESS_PIXELS)
        } else {
            size(Size.Origin)
            precision(Precision.EXACTLY)
        }
    },
)

private suspend fun readImageBounds(context: Context, model: String): Pair<Int, Int>? =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(model))?.use { input ->
                BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { options ->
                    BitmapFactory.decodeStream(input, null, options)
                }.takeIf { it.outWidth > 0 && it.outHeight > 0 }?.let { it.outWidth to it.outHeight }
            }
        }.getOrNull()
    }

private const val BUNDLED_WALLPAPER = "file:///android_asset/wallpaper.jpg"

/** 是否为视频壁纸（content 类型或扩展名判断）。 */
internal fun isVideoWallpaperUri(context: Context, uri: String): Boolean {
    if (uri.isBlank()) return false
    return runCatching {
        val mime = context.contentResolver.getType(Uri.parse(uri))
        if (mime != null) return@runCatching mime.startsWith("video")
        val lower = uri.lowercase()
        lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mkv") ||
            lower.endsWith(".mov") || lower.endsWith(".3gp") || lower.endsWith(".ts")
    }.getOrDefault(false)
}

@Composable
private fun VideoWallpaperBackground(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val muted by koinInject<AppSettingsReader>().moeWallpaperVideoMuted.state.collectAsStateWithLifecycle()
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(muted) { player.volume = if (muted) 0f else 1f; onDispose {} }
    DisposableEffect(player) { onDispose { player.release() } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> player.play()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}

private fun resolveWallpaperModel(context: Context, uri: String): String {
    if (uri.isBlank()) return BUNDLED_WALLPAPER
    if (uri.startsWith("file://")) {
        val path = uri.removePrefix("file://")
        return if (path.startsWith("/android_asset/") || File(path).exists()) uri else BUNDLED_WALLPAPER
    }
    val readable =
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { true } ?: false
        }.getOrDefault(false)
    return if (readable) uri else BUNDLED_WALLPAPER
}
