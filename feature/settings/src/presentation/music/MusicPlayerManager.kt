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

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.suanran.dreambox.core.contract.AppSettingsReader
import com.suanran.dreambox.core.music.MusicLyricLine
import com.suanran.dreambox.core.music.MusicPlayer
import com.suanran.dreambox.core.music.MusicTrack
import com.suanran.dreambox.feature.settings.data.music.SubsonicClient
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * 基于 ExoPlayer 的音乐播放器（应用级单例）。
 * 歌词逐行同步：切歌时拉取 OpenSubsonic 歌词，按播放进度更新 [lyricLine]，
 * 首页签名区订阅该字段即可显示当前歌词。
 */
class MusicPlayerManager(
    private val appContext: Context,
    private val mmkv: MMKV,
    private val settings: AppSettingsReader,
) : MusicPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val json = Json { ignoreUnknownKeys = true }

    val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(appContext).build().apply {
            addListener(playerListener)
        }
    }

    override val currentTrack = MutableStateFlow<MusicTrack?>(null)
    override val isPlaying = MutableStateFlow(false)
    override val isLoading = MutableStateFlow(false)
    override val lyricLine = MutableStateFlow<String?>(null)
    override val positionMs = MutableStateFlow(0L)
    override val durationMs = MutableStateFlow(0L)
    override val queue = MutableStateFlow<List<MusicTrack>>(emptyList())
    override val queueIndex = MutableStateFlow(0)
    override val shuffleEnabled = MutableStateFlow(false)
    override val repeatOne = MutableStateFlow(false)

    /** 最近播放（本地持久化，供音乐库首页展示）。 */
    override val recentTracks = MutableStateFlow<List<MusicTrack>>(loadRecents())

    /** 音乐漫游模式：播完自动续拉随机歌曲。 */
    var roamMode: Boolean = false

    private var tracks: List<MusicTrack> = emptyList()
    private var lyrics: List<MusicLyricLine> = emptyList()
    private var tickerJob: Job? = null
    private var roamRefillJob: Job? = null

    private fun subsonic(): SubsonicClient? {
        val url = settings.musicServerUrl.value.ifBlank { return null }
        val user = settings.musicServerUser.value
        val pass = settings.musicServerPassword.value
        if (user.isBlank()) return null
        return SubsonicClient(url, user, pass)
    }

    private val playerListener =
        object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying.value = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isLoading.value = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED && roamMode) refillRoam()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = exoPlayer.currentMediaItemIndex
                if (index in tracks.indices) {
                    queueIndex.value = index
                    onTrackChanged(tracks[index])
                }
            }

            override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                shuffleEnabled.value = enabled
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                repeatOne.value = repeatMode == Player.REPEAT_MODE_ONE
            }
        }

    override fun playTracks(tracks: List<MusicTrack>, index: Int) {
        if (tracks.isEmpty()) return
        this.tracks = tracks
        queue.value = tracks
        val items = tracks.map { it.toMediaItem() }
        // 先启动前台服务（创建 MediaSession），再开始播放，
        // 否则 MediaSession 建好时播放器已在播，触发不了 startForeground()，
        // Android 12+ 会抛 ForegroundServiceDidNotStartInTimeException 闪退。
        startService()
        exoPlayer.setMediaItems(items, index.coerceIn(tracks.indices), 0L)
        exoPlayer.prepare()
        exoPlayer.play()
        queueIndex.value = index.coerceIn(tracks.indices)
        startTicker()
        onTrackChanged(tracks[index.coerceIn(tracks.indices)])
    }

    private fun MusicTrack.toMediaItem(): MediaItem {
        val metadata =
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .apply { if (coverArtUrl.isNotBlank()) setArtworkUri(Uri.parse(coverArtUrl)) }
                .build()
        return MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun onTrackChanged(track: MusicTrack) {
        currentTrack.value = track
        lyricLine.value = null
        lyrics = emptyList()
        pushRecent(track)
        scope.launch(Dispatchers.IO) {
            val lines =
                try {
                    subsonic()?.getLyrics(track.id).orEmpty()
                } catch (e: Exception) {
                    Timber.w(e, "拉取歌词失败")
                    emptyList()
                }
            lyrics = lines
        }
    }

    /** 供外部替换 MediaItem 用的公开版本。 */
    fun MusicTrack.toMediaItemPublic(): MediaItem = toMediaItem()

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val pos = exoPlayer.currentPosition
                val dur = exoPlayer.duration.coerceAtLeast(0L)
                positionMs.value = pos
                durationMs.value = if (dur == Long.MIN_VALUE) 0L else dur
                lyricLine.value = currentLyricText(pos)
                delay(300L)
            }
        }
    }

    private fun currentLyricText(posMs: Long): String? {
        val lines = lyrics
        if (lines.isEmpty()) return null
        val synced = lines.firstOrNull()?.timeMs?.let { it >= 0 } == true
        if (!synced) {
            // 无时间轴：按播放进度匀速滚动显示
            val dur = durationMs.value.coerceAtLeast(1L)
            val idx = ((posMs.toDouble() / dur) * lines.size).toInt().coerceIn(lines.indices)
            return lines[idx].text
        }
        var current: String? = null
        for (l in lines) {
            if (l.timeMs <= posMs) current = l.text else break
        }
        return current
    }

    /** 漫游模式播完自动续 30 首随机歌曲。 */
    private fun refillRoam() {
        if (roamRefillJob?.isActive == true) return
        roamRefillJob = scope.launch(Dispatchers.IO) {
            try {
                val more = subsonic()?.getRandomSongs(30).orEmpty()
                if (more.isNotEmpty()) {
                    val base = tracks + more
                    tracks = base
                    queue.value = base
                    launch(Dispatchers.Main) {
                        exoPlayer.addMediaItems(more.map { it.toMediaItem() })
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "漫游续歌失败")
            }
        }
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(appContext, Intent(appContext, MusicPlaybackService::class.java))
        } catch (e: Exception) {
            Timber.w(e, "启动播放服务失败")
        }
    }

    override fun toggle() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    override fun play() { exoPlayer.play() }

    override fun pause() { exoPlayer.pause() }

    override fun next() {
        if (exoPlayer.hasNextMediaItem()) exoPlayer.seekToNextMediaItem()
    }

    override fun previous() {
        if (exoPlayer.hasPreviousMediaItem()) exoPlayer.seekToPreviousMediaItem()
    }

    override fun seekTo(ms: Long) { exoPlayer.seekTo(ms) }

    override fun toggleShuffle() {
        exoPlayer.shuffleModeEnabled = !exoPlayer.shuffleModeEnabled
    }

    override fun toggleRepeatOne() {
        exoPlayer.repeatMode =
            if (exoPlayer.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
    }

    override fun stop() {
        roamMode = false
        exoPlayer.stop()
        currentTrack.value = null
        lyricLine.value = null
        tickerJob?.cancel()
    }

    // ── 最近播放持久化 ──

    private fun loadRecents(): List<MusicTrack> =
        try {
            mmkv.decodeString(KEY_RECENTS)?.let { json.decodeFromString(ListSerializer(MusicTrack.serializer()), it) }.orEmpty()
        } catch (e: Exception) {
            emptyList()
        }

    private fun pushRecent(track: MusicTrack) {
        val updated = (listOf(track) + recentTracks.value.filterNot { it.id == track.id }).take(30)
        recentTracks.value = updated
        try {
            mmkv.encode(KEY_RECENTS, json.encodeToString(ListSerializer(MusicTrack.serializer()), updated))
        } catch (e: Exception) {
            Timber.w(e, "保存最近播放失败")
        }
    }

    companion object {
        private const val KEY_RECENTS = "music_recent_tracks"
    }
}
