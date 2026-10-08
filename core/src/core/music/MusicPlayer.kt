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

package com.suanran.dreambox.core.music

import kotlinx.coroutines.flow.StateFlow

/**
 * 音乐播放器抽象。实现（ExoPlayer）由 feature 层提供并注入，
 * 首页侧边栏与签名歌词都只依赖这个接口。
 */
interface MusicPlayer {
    /** 当前曲目，null 表示从未播放。 */
    val currentTrack: StateFlow<MusicTrack?>

    val isPlaying: StateFlow<Boolean>
    val isLoading: StateFlow<Boolean>

    /** 当前歌词行（逐行同步）；无歌词时为 null，调用方可退回显示歌名。 */
    val lyricLine: StateFlow<String?>

    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val queue: StateFlow<List<MusicTrack>>
    val queueIndex: StateFlow<Int>
    val shuffleEnabled: StateFlow<Boolean>
    val repeatOne: StateFlow<Boolean>

    /** 最近播放（本地持久化）。 */
    val recentTracks: StateFlow<List<MusicTrack>>

    /** 播放队列并从 index 开始。 */
    fun playTracks(tracks: List<MusicTrack>, index: Int = 0)

    fun toggle()
    fun play()
    fun pause()
    fun next()
    fun previous()
    fun seekTo(ms: Long)
    fun toggleShuffle()
    fun toggleRepeatOne()
    fun stop()
}
