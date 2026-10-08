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

import kotlinx.serialization.Serializable

/** 单曲（播放器直接消费的领域模型，串流地址由数据源在装配时填好）。 */
@Serializable
data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String = "",
    val coverArtId: String = "",
    val coverArtUrl: String = "",
    val streamUrl: String,
    val durationMs: Long = 0L,
    val bitrate: Int = 0,
    val suffix: String = "",
    val starred: Boolean = false,
)

/** 专辑。 */
@Serializable
data class MusicAlbum(
    val id: String,
    val name: String,
    val artist: String,
    val coverArtId: String = "",
    val coverArtUrl: String = "",
    val songCount: Int = 0,
    val year: Int = 0,
)

/** 艺术家。 */
@Serializable
data class MusicArtist(
    val id: String,
    val name: String,
    val albumCount: Int = 0,
    val coverArtId: String = "",
    val coverArtUrl: String = "",
)

/** 歌单。 */
@Serializable
data class MusicPlaylist(
    val id: String,
    val name: String,
    val songCount: Int = 0,
    val coverArtId: String = "",
    val coverArtUrl: String = "",
)

/** 一行歌词（逐行同步用，timeMs < 0 表示无时间轴）。 */
@Serializable
data class MusicLyricLine(
    val timeMs: Long,
    val text: String,
)
