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

package com.suanran.dreambox.feature.settings.presentation.music

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.suanran.dreambox.core.music.MusicPlayer
import org.koin.android.ext.android.getKoin

/**
 * 音乐后台播放服务：锁屏 / 通知栏控制条，复用 [MusicPlayerManager] 的单例 ExoPlayer。
 */
class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // 立刻进前台保命：Media3 的 MediaSessionService 只有在有 Controller
        // 连接且 session 活跃时才会自动 startForeground()，我们 UI 直连
        // MusicPlayerManager、无 Controller，自动逻辑永远不会触发，
        // Android 12+ 10 秒超时必崩。先上占位通知，后续 Media3 会更新它。
        startForegroundPlaceholder()
        val manager = getKoin().get<MusicPlayer>() as MusicPlayerManager
        mediaSession =
            MediaSession.Builder(this, manager.exoPlayer)
                .build()
    }

    private fun startForegroundPlaceholder() {
        val channelId = "dreambox_music_playback"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(channelId, "音乐播放", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val notification =
            NotificationCompat.Builder(this, channelId)
                .setContentTitle("DreamBox 音乐")
                .setContentText("正在准备播放…")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build()
        // manifest 已声明 foregroundServiceType="mediaPlayback"，用双参版本即可
        startForeground(1, notification)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        // 只释放 session，播放器单例随应用进程常驻，保证切回应用可继续控制。
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
