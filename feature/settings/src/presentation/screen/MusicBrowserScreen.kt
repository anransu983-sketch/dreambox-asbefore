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

package com.suanran.dreambox.feature.settings.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.suanran.dreambox.core.music.MusicAlbum
import com.suanran.dreambox.core.music.MusicArtist
import com.suanran.dreambox.core.music.MusicPlayer
import com.suanran.dreambox.core.music.MusicPlaylist
import com.suanran.dreambox.core.music.MusicTrack
import com.suanran.dreambox.feature.settings.data.music.SubsonicClient
import com.suanran.dreambox.feature.settings.presentation.music.MiniPlayer
import com.suanran.dreambox.feature.settings.presentation.music.MusicCover
import com.suanran.dreambox.feature.settings.presentation.music.MusicPlayerManager
import com.suanran.dreambox.feature.settings.presentation.music.MusicSectionTitle
import com.suanran.dreambox.feature.settings.presentation.music.SongRow
import com.suanran.dreambox.feature.settings.presentation.music.rememberSubsonicClient
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.navigation.Navigator
import com.suanran.dreambox.presentation.navigation.Route
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 音乐分类浏览：artists / albums / songs / playlists / starred / search，
 * 以及 album / artist / playlist 三种详情（kind 带 id）。
 */
@Composable
fun MusicBrowserScreen(
    navigator: Navigator,
    kind: String,
    id: String = "",
    title: String = "",
) {
    val scrollBehavior = MiuixScrollBehavior()
    val scope = rememberCoroutineScope()
    val player = koinInject<MusicPlayer>()
    val client = rememberSubsonicClient()
    val txt = FlyTxt.Settings.Experimental.Music

    val screenTitle = when {
        title.isNotBlank() -> title
        kind == "artists" -> txt.Artists
        kind == "albums" -> txt.Albums
        kind == "songs" -> txt.Songs
        kind == "playlists" -> txt.Playlists
        kind == "starred" -> txt.Favorites
        kind == "search" -> txt.SearchTitle
        else -> txt.Title
    }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var songs by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var albums by remember { mutableStateOf<List<MusicAlbum>>(emptyList()) }
    var artists by remember { mutableStateOf<List<MusicArtist>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<MusicPlaylist>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var albumSort by remember { mutableStateOf("recent") }

    fun load(c: SubsonicClient, sort: String = albumSort) {
        loading = true
        error = null
        scope.launch {
            try {
                when (kind) {
                    "artists" -> artists = c.getArtists()
                    "albums" -> albums = c.getAlbums(sort, 60)
                    "songs" -> songs = c.getRandomSongs(50)
                    "playlists" -> playlists = c.getPlaylists()
                    "starred" -> {
                        songs = c.getStarredSongs()
                        albums = c.getStarredAlbums()
                    }
                    "album" -> songs = c.getAlbumSongs(id)
                    "artist" -> albums = c.getArtistAlbums(id)
                    "playlist" -> songs = c.getPlaylistSongs(id)
                }
            } catch (e: Exception) {
                Timber.w(e, "音乐浏览加载失败")
                error = e.message
            }
            loading = false
        }
    }

    fun doSearch(c: SubsonicClient, q: String) {
        if (q.isBlank()) return
        loading = true
        error = null
        scope.launch {
            try {
                val (s, a, ar) = c.search(q)
                songs = s; albums = a; artists = ar
            } catch (e: Exception) {
                error = e.message
            }
            loading = false
        }
    }

    fun toggleStar(c: SubsonicClient, track: MusicTrack) {
        scope.launch {
            try {
                if (track.starred) c.unstar(track.id) else c.star(track.id)
                songs = songs.map { if (it.id == track.id) it.copy(starred = !track.starred) else it }
            } catch (e: Exception) {
                Timber.w(e, "收藏切换失败")
            }
        }
    }

    LaunchedEffect(client, kind, id) {
        val c = client ?: return@LaunchedEffect
        if (kind != "search") load(c)
    }

    Scaffold(
        topBar = {
            TopBar(
                title = screenTitle,
                scrollBehavior = scrollBehavior,
                navigationIcon = { NavigationBackIcon(navigator = navigator) },
                actions = {
                    if (kind == "albums" || kind == "songs") {
                        IconButton(onClick = { client?.let { load(it) } }) {
                            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            ScreenLazyColumn(
                scrollBehavior = scrollBehavior,
                innerPadding = mainLikePadding,
                modifier = Modifier.weight(1f),
            ) {
            if (kind == "search") {
                item {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        label = txt.SearchHint,
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        top.yukonga.miuix.kmp.basic.Button(
                            onClick = { client?.let { doSearch(it, query.trim()) } },
                        ) {
                            Text(text = txt.SearchAction)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (error != null) {
                item {
                    Text(
                        text = error ?: "",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                if (artists.isNotEmpty()) {
                    item { MusicSectionTitle(text = txt.Artists) }
                    items(artists) { artist ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                navigator.push(Route.MusicBrowser(kind = "artist", id = artist.id, title = artist.name))
                            }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MusicCover(url = artist.coverArtUrl, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (artist.albumCount > 0) {
                                    Text(
                                        text = "${artist.albumCount}${txt.CountAlbumUnit}",
                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                    }
                }
                if (albums.isNotEmpty()) {
                    item { MusicSectionTitle(text = txt.Albums) }
                    items(albums) { album ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                navigator.push(Route.MusicBrowser(kind = "album", id = album.id, title = album.name))
                            }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MusicCover(url = album.coverArtUrl, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = album.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = buildList {
                                    if (album.artist.isNotBlank()) add(album.artist)
                                    if (album.songCount > 0) add("${album.songCount}${txt.CountSongUnit}")
                                }.joinToString(" · ")
                                if (sub.isNotBlank()) {
                                    Text(text = sub, fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                if (playlists.isNotEmpty()) {
                    item { MusicSectionTitle(text = txt.Playlists) }
                    items(playlists) { pl ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                navigator.push(Route.MusicBrowser(kind = "playlist", id = pl.id, title = pl.name))
                            }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MusicCover(url = pl.coverArtUrl, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (pl.songCount > 0) {
                                    Text(text = "${pl.songCount}${txt.CountSongUnit}", fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }
                            }
                        }
                    }
                }
                if (songs.isNotEmpty()) {
                    if (kind == "starred" || kind == "search") item { MusicSectionTitle(text = txt.Songs) }
                    items(songs) { track ->
                        val c = client
                        SongRow(
                            track = track,
                            onClick = {
                                (player as? MusicPlayerManager)?.roamMode = false
                                player.playTracks(songs, songs.indexOf(track))
                            },
                            onToggleStar = if (c == null) null else ({ toggleStar(c, track) }),
                        )
                    }
                }
                if (!loading && error == null && songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()) {
                    item {
                        Text(
                            text = txt.Empty,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
        MiniPlayer(player = player, onOpenPlayer = { navigator.push(Route.NowPlaying) })
    }
}
}
