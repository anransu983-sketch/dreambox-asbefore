package com.suanran.dreambox.presentation.component.misc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream

/**
 * 自定义图标存储：用户选的图片按 key 存成 filesDir/custom_icons/<key>.png（居中裁方、192px）。
 * 用在：底部导航栏图标（tab_*）、设置主页模块图标（module_*）、返回按钮（back_button）。
 * version 是全局刷新计数，保存/清除后自增，所有渲染点 remember 到它就会重读。
 */
object CustomIcons {
    private const val DIR_NAME = "custom_icons"
    private const val SIZE_PX = 192

    var version by mutableStateOf(0)
        private set

    fun file(context: Context, key: String): File {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        return File(dir, "$key.png")
    }

    fun exists(context: Context, key: String): Boolean = file(context, key).exists()

    fun saveFromUri(context: Context, key: String, uri: Uri): Boolean {
        return runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: return false
            val side = minOf(raw.width, raw.height)
            val cropped = Bitmap.createBitmap(
                raw,
                (raw.width - side) / 2,
                (raw.height - side) / 2,
                side,
                side,
            )
            val scaled = Bitmap.createScaledBitmap(cropped, SIZE_PX, SIZE_PX, true)
            FileOutputStream(file(context, key)).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            version++
            true
        }.getOrElse { false }
    }

    fun clear(context: Context, key: String) {
        runCatching { file(context, key).delete() }
        version++
    }
}

@Composable
fun rememberCustomIconBitmap(key: String): ImageBitmap? {
    val context = LocalContext.current
    val version = CustomIcons.version
    return remember(key, version) {
        runCatching {
            val f = CustomIcons.file(context, key)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
        }.getOrNull()
    }
}
