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

package com.suanran.dreambox.feature.settings.data.music

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubsonicResponse(
    @SerialName("subsonic-response") val body: SubsonicBody = SubsonicBody(),
)

@Serializable
data class SubsonicBody(
    val status: String = "",
    val version: String = "",
    val type: String = "",
    val serverVersion: String = "",
    val error: SubsonicError? = null,
    val randomSongs: SongList? = null,
    val albumList2: AlbumList? = null,
    val artists: ArtistsIndex? = null,
    val artist: ArtistDetail? = null,
    val album: AlbumDetail? = null,
    val song: SongDto? = null,
    val starred2: Starred2? = null,
    val searchResult3: SearchResult3? = null,
    val playlists: PlaylistsList? = null,
    val playlist: PlaylistDetail? = null,
    val lyricsList: LyricsList? = null,
)

@Serializable
data class SubsonicError(
    val code: Int = 0,
    val message: String = "",
)

@Serializable
data class SongList(val song: List<SongDto> = emptyList())

@Serializable
data class AlbumList(val album: List<AlbumDto> = emptyList())

@Serializable
data class SongDto(
    val id: String = "",
    val title: String = "",
    val album: String = "",
    val artist: String = "",
    val albumId: String = "",
    val artistId: String = "",
    val coverArt: String = "",
    val duration: Int = 0,
    val bitRate: Int = 0,
    val suffix: String = "",
    val track: Int = 0,
    val year: Int = 0,
    val genre: String = "",
    val starred: String? = null,
)

@Serializable
data class AlbumDto(
    val id: String = "",
    val name: String = "",
    val artist: String = "",
    val artistId: String = "",
    val coverArt: String = "",
    val songCount: Int = 0,
    val duration: Int = 0,
    val playCount: Int = 0,
    val created: String = "",
    val year: Int = 0,
    val genre: String = "",
)

@Serializable
data class ArtistsIndex(
    val index: List<ArtistIndex> = emptyList(),
    val ignoredArticles: String = "",
)

@Serializable
data class ArtistIndex(
    val name: String = "",
    val artist: List<ArtistDto> = emptyList(),
)

@Serializable
data class ArtistDto(
    val id: String = "",
    val name: String = "",
    val coverArt: String = "",
    val albumCount: Int = 0,
)

@Serializable
data class ArtistDetail(
    val id: String = "",
    val name: String = "",
    val albumCount: Int = 0,
    val album: List<AlbumDto> = emptyList(),
)

@Serializable
data class AlbumDetail(
    val id: String = "",
    val name: String = "",
    val artist: String = "",
    val coverArt: String = "",
    val song: List<SongDto> = emptyList(),
    val songCount: Int = 0,
    val year: Int = 0,
)

@Serializable
data class Starred2(
    val song: List<SongDto> = emptyList(),
    val album: List<AlbumDto> = emptyList(),
    val artist: List<ArtistDto> = emptyList(),
)

@Serializable
data class SearchResult3(
    val artist: List<ArtistDto> = emptyList(),
    val album: List<AlbumDto> = emptyList(),
    val song: List<SongDto> = emptyList(),
)

@Serializable
data class PlaylistsList(val playlist: List<PlaylistDto> = emptyList())

@Serializable
data class PlaylistDto(
    val id: String = "",
    val name: String = "",
    val songCount: Int = 0,
    val duration: Int = 0,
    val coverArt: String = "",
)

@Serializable
data class PlaylistDetail(
    val id: String = "",
    val name: String = "",
    val entry: List<SongDto> = emptyList(),
)

@Serializable
data class LyricsList(
    val structuredLyrics: List<StructuredLyrics> = emptyList(),
)

@Serializable
data class StructuredLyrics(
    val displayArtist: String = "",
    val displayTitle: String = "",
    val lang: String = "",
    val synced: Boolean = false,
    val line: List<LyricLineDto> = emptyList(),
    val offset: Long = 0L,
)

@Serializable
data class LyricLineDto(
    val start: Long = -1L,
    val value: String = "",
)
