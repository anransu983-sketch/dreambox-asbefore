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

import com.suanran.dreambox.core.music.MusicAlbum
import com.suanran.dreambox.core.music.MusicArtist
import com.suanran.dreambox.core.music.MusicLyricLine
import com.suanran.dreambox.core.music.MusicPlaylist
import com.suanran.dreambox.core.music.MusicTrack
import com.suanran.dreambox.core.util.HttpClientProfile
import com.suanran.dreambox.core.util.createHttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Subsonic / OpenSubsonic REST 客户端（Navidrome 实测可用）。
 * 认证用明文账号密码查询参数，Navidrome 接受。
 */
class SubsonicClient(
    serverUrl: String,
    private val username: String,
    private val password: String,
) {
    private val baseUrl = serverUrl.trim().trimEnd('/')
    private val http =
        createHttpClient(
            HttpClientProfile.API,
            installContentNegotiation = false,
            userAgent = "DreamBox/1.0",
        )
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun endpoint(path: String, params: Map<String, String> = emptyMap()): String {
        val auth =
            mapOf(
                "u" to username,
                "p" to password,
                "v" to "1.16.1",
                "c" to "dreambox",
                "f" to "json",
            ) + params
        val query = auth.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        return "$baseUrl/rest/$path?$query"
    }

    /** 供播放器直接串流 / 取封面的完整地址。 */
    fun streamUrl(songId: String): String = endpoint("stream", mapOf("id" to songId))

    fun coverArtUrl(coverArtId: String, size: Int = 300): String =
        if (coverArtId.isBlank()) "" else endpoint("getCoverArt", mapOf("id" to coverArtId, "size" to size.toString()))

    private suspend fun call(path: String, params: Map<String, String> = emptyMap()): SubsonicBody =
        withContext(Dispatchers.IO) {
            val text = http.get(endpoint(path, params)).bodyAsText()
            val body = json.decodeFromString<SubsonicResponse>(text).body
            if (body.status != "ok") {
                val err = body.error
                throw IllegalStateException(err?.message?.ifBlank { "服务器返回 ${body.status}" } ?: "服务器返回 ${body.status}")
            }
            body
        }

    private fun SongDto.toTrack(): MusicTrack =
        // 歌曲无独立封面时用专辑 ID 兜底（Navidrome 的 getCoverArt 接受专辑 ID）
        MusicTrack(
            id = id,
            title = title.ifBlank { id },
            artist = artist,
            album = album,
            albumId = albumId,
            coverArtId = coverArt.ifBlank { albumId },
            coverArtUrl = coverArtUrl(coverArt.ifBlank { albumId }),
            streamUrl = streamUrl(id),
            durationMs = duration * 1000L,
            bitrate = bitRate,
            suffix = suffix,
            starred = !starred.isNullOrBlank(),
        )

    private fun AlbumDto.toAlbum(): MusicAlbum =
        MusicAlbum(
            id = id,
            name = name.ifBlank { id },
            artist = artist,
            coverArtId = coverArt,
            coverArtUrl = coverArtUrl(coverArt),
            songCount = songCount,
            year = year,
        )

    private fun ArtistDto.toArtist(): MusicArtist =
        MusicArtist(
            id = id,
            name = name.ifBlank { id },
            albumCount = albumCount,
            coverArtId = coverArt,
            coverArtUrl = coverArtUrl(coverArt),
        )

    /** 连接测试，成功返回服务器版本描述。 */
    suspend fun ping(): Result<String> = runCatching {
        val body = call("ping")
        listOfNotNull(
            body.type.ifBlank { null },
            body.serverVersion.ifBlank { null },
        ).joinToString(" ").ifBlank { "连接成功" }
    }

    /** 专辑列表：type = recent | newest | frequent | random | starred | alphabeticalByName。 */
    suspend fun getAlbums(type: String, size: Int = 30, offset: Int = 0): List<MusicAlbum> =
        call("getAlbumList2", mapOf("type" to type, "size" to size.toString(), "offset" to offset.toString()))
            .albumList2?.album.orEmpty().map { it.toAlbum() }

    suspend fun getRandomSongs(size: Int = 20): List<MusicTrack> =
        call("getRandomSongs", mapOf("size" to size.toString()))
            .randomSongs?.song.orEmpty().map { it.toTrack() }

    suspend fun getStarredSongs(): List<MusicTrack> =
        call("getStarred2").starred2?.song.orEmpty().map { it.toTrack() }

    suspend fun getStarredAlbums(): List<MusicAlbum> =
        call("getStarred2").starred2?.album.orEmpty().map { it.toAlbum() }

    suspend fun getArtists(): List<MusicArtist> =
        call("getArtists").artists?.index.orEmpty()
            .flatMap { it.artist }
            .map { it.toArtist() }
            .sortedBy { it.name.lowercase() }

    suspend fun getArtistAlbums(artistId: String): List<MusicAlbum> =
        call("getArtist", mapOf("id" to artistId)).artist?.album.orEmpty().map { it.toAlbum() }

    suspend fun getAlbumSongs(albumId: String): List<MusicTrack> =
        call("getAlbum", mapOf("id" to albumId)).album?.song.orEmpty().map { it.toTrack() }

    suspend fun search(query: String): Triple<List<MusicTrack>, List<MusicAlbum>, List<MusicArtist>> {
        val r = call("search3", mapOf("query" to query, "songCount" to "30", "albumCount" to "20", "artistCount" to "20")).searchResult3
        return Triple(
            r?.song.orEmpty().map { it.toTrack() },
            r?.album.orEmpty().map { it.toAlbum() },
            r?.artist.orEmpty().map { it.toArtist() },
        )
    }

    suspend fun getPlaylists(): List<MusicPlaylist> =
        call("getPlaylists").playlists?.playlist.orEmpty().map {
            MusicPlaylist(id = it.id, name = it.name, songCount = it.songCount, coverArtId = it.coverArt, coverArtUrl = coverArtUrl(it.coverArt))
        }

    suspend fun getPlaylistSongs(playlistId: String): List<MusicTrack> =
        call("getPlaylist", mapOf("id" to playlistId)).playlist?.entry.orEmpty().map { it.toTrack() }

    suspend fun star(id: String) { call("star", mapOf("id" to id)) }

    suspend fun unstar(id: String) { call("unstar", mapOf("id" to id)) }

    /** 歌词（OpenSubsonic getLyricsBySongId），同步歌词带时间轴，否则按行返回。 */
    suspend fun getLyrics(songId: String): List<MusicLyricLine> {
        val items = call("getLyricsBySongId", mapOf("id" to songId)).lyricsList?.structuredLyrics.orEmpty()
        val synced = items.firstOrNull { it.synced && it.line.isNotEmpty() }
        if (synced != null) {
            return synced.line.map { MusicLyricLine(timeMs = it.start + synced.offset, text = it.value) }
                .filter { it.text.isNotBlank() }
                .sortedBy { it.timeMs }
        }
        val plain = items.firstOrNull { it.line.isNotEmpty() }
        if (plain != null) {
            return plain.line.map { MusicLyricLine(timeMs = -1L, text = it.value) }
                .filter { it.text.isNotBlank() }
        }
        return emptyList()
    }
}
