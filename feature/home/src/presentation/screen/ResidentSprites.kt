package com.suanran.dreambox.feature.home.presentation.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 一张已就位的精灵帧。
 * 加载时做了三件事，对应失败档案 1 和 5：
 *   1. 把底部全透明的行裁掉 → 图片最底一行 = 最低的不透明像素 = 脚底；美术留的空边不会让人飘起来。
 *   2. 脚底中心 x 从「最底 8% 行的不透明像素」量出来，不靠猜 → 不会再有「按正方形居中」的 13dp 错位。
 *   3. 全部帧共用同一个缩放（站姿帧高 → 屏幕目标高），并一次性缩到屏幕像素尺寸 → 绘制时 1:1，不闪烁。
 */
internal class ResidentFrame(
    val image: ImageBitmap,
    /** 脚底中心距帧左边的像素（已缩放）。 */
    val footX: Float,
    val w: Int,
    val h: Int,
)

internal class ResidentSprites(
    val stand: ResidentFrame,
    val walk: List<ResidentFrame>,
    val actions: Map<String, List<ResidentFrame>>,
) {
    fun walkFrame(i: Int): ResidentFrame = walk[i.coerceIn(0, walk.size - 1)]

    fun actionFrame(key: String?, i: Int): ResidentFrame {
        val list = key?.let { actions[it] }
        return if (list.isNullOrEmpty()) stand else list[i.coerceIn(0, list.size - 1)]
    }
}

internal object ResidentSpriteLoader {
    private const val TAG = "ResidentSprites"
    private const val ALPHA_SOLID = 40
    private const val FOOT_BAND = 0.08f

    private class Raw(val bmp: Bitmap, val footX: Float)

    /**
     * 在 IO 线程调用。bodyPx = 站姿帧在屏幕上应有的像素高（heightFrac × 底图屏幕高）。
     * 任何一张缺失就整体返回 null（该居民不显示，不崩）。
     */
    fun load(ctx: Context, m: SpriteManifest, bodyPx: Float, expectWalkFrames: Int): ResidentSprites? =
        runCatching {
            val standRaw = decode(ctx, m.stand) ?: return null
            val k = bodyPx / standRaw.bmp.height // 屏幕像素 / 原图像素：所有帧共用
            val stand = finish(standRaw, k)
            val walkRaw = m.walk.map { decode(ctx, it) ?: return null }
            if (walkRaw.size != expectWalkFrames) {
                Log.w(TAG, "走帧数 ${walkRaw.size} ≠ 配置 $expectWalkFrames（${m.stand}）")
                return null
            }
            // 走帧高度应和站姿接近（同一画布比例导出）；差太多多半是导出时各自拉伸过
            walkRaw.forEachIndexed { i, r ->
                val ratio = r.bmp.height.toFloat() / standRaw.bmp.height
                if (abs(ratio - 1f) > 0.18f) Log.w(TAG, "走帧 $i 高度比站姿 ${"%.2f".format(ratio)}：${m.walk[i]} 是否各自缩放过？")
            }
            val walk = walkRaw.map { finish(it, k) }
            val actions =
                m.actions.mapValues { (_, files) -> files.map { finish(decode(ctx, it) ?: return null, k) } }
            ResidentSprites(stand, walk, actions)
        }.onFailure { Log.w(TAG, "load failed: ${m.stand}", it) }.getOrNull()

    private fun decode(ctx: Context, path: String): Raw? {
        val src =
            ctx.assets.open(path).use { BitmapFactory.decodeStream(it) } ?: return null
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        // 最低的不透明行
        var bottom = -1
        for (y in h - 1 downTo 0) {
            var hit = false
            val row = y * w
            for (x in 0 until w) if ((px[row + x] ushr 24) >= ALPHA_SOLID) { hit = true; break }
            if (hit) { bottom = y; break }
        }
        if (bottom < 0) return null
        if (h - 1 - bottom > 1) Log.i(TAG, "$path 底部有 ${h - 1 - bottom}px 透明空边，已裁（脚底=最低不透明行）")
        // 脚底中心：最底 FOOT_BAND 行里不透明像素的 x 均值
        val band = max(2, ((bottom + 1) * FOOT_BAND).roundToInt())
        var sum = 0.0
        var cnt = 0
        for (y in max(0, bottom - band + 1)..bottom) {
            val row = y * w
            for (x in 0 until w) if ((px[row + x] ushr 24) >= ALPHA_SOLID) { sum += x + 0.5; cnt++ }
        }
        val footX = if (cnt > 0) (sum / cnt).toFloat() else w / 2f
        val trimmed = if (bottom == h - 1) src else Bitmap.createBitmap(src, 0, 0, w, bottom + 1)
        return Raw(trimmed, footX)
    }

    private fun finish(r: Raw, k: Float): ResidentFrame {
        val tw = max(1, (r.bmp.width * k).roundToInt())
        val th = max(1, (r.bmp.height * k).roundToInt())
        val scaled = scaleTo(r.bmp, tw, th)
        return ResidentFrame(scaled.asImageBitmap(), r.footX * tw / r.bmp.width, tw, th)
    }

    /** 逐级对半缩小再收尾：一次缩 3 倍以上会走样闪烁。 */
    private fun scaleTo(src: Bitmap, tw: Int, th: Int): Bitmap {
        var cur = src
        while (cur.width / 2 >= tw && cur.height / 2 >= th) {
            val next = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
            if (cur !== src) cur.recycle()
            cur = next
        }
        val out = if (cur.width == tw && cur.height == th) cur else Bitmap.createScaledBitmap(cur, tw, th, true)
        if (cur !== src && cur !== out) cur.recycle()
        return out
    }
}
