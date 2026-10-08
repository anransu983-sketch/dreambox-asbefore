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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suanran.dreambox.core.music.MusicPlayer
import com.suanran.dreambox.core.music.MusicTrack
import com.suanran.dreambox.core.music.MusicAlbum
import com.suanran.dreambox.feature.settings.presentation.music.MiniPlayer
import com.suanran.dreambox.feature.settings.presentation.music.MusicCover
import com.suanran.dreambox.feature.settings.presentation.music.MusicPlayerManager
import com.suanran.dreambox.feature.settings.presentation.music.MusicSectionTitle
import com.suanran.dreambox.feature.settings.presentation.music.SongRow
import com.suanran.dreambox.feature.settings.presentation.music.rememberSubsonicClient
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.card.Card
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.navigation.Navigator
import com.suanran.dreambox.presentation.navigation.Route
import com.suanran.dreambox.presentation.theme.AppTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class MusicCategory(
    val kind: String,
    val icon: ImageVector,
    val title: String,
    val tint: Color,
    val subtitle: String = "",
)

/**
 * 音乐库首页：分类入口 / 音乐漫游 / 随机歌曲 / 最近播放 + 迷你播放器。
 */
@Composable
fun MusicLibraryScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val scope = rememberCoroutineScope()
    val player = koinInject<MusicPlayer>()
    val client = rememberSubsonicClient()
    val txt = FlyTxt.Settings.Experimental.Music

    var randomSongs by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var randomLoading by remember { mutableStateOf(false) }
    var randomAlbums by remember { mutableStateOf<List<MusicAlbum>>(emptyList()) }
    var albumsLoading by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<com.suanran.dreambox.core.music.MusicPlaylist>>(emptyList()) }
    var starredCount by remember { mutableStateOf(0) }
    val recents by player.recentTracks.collectAsStateWithLifecycle()

    // 八宫格分类（仿流云音乐）：图标+标题+彩色底+数量副标题
    val categories = listOf(
        MusicCategory("songs", Icons.Filled.MusicNote, txt.Songs, Color(0xFF4A90D9)),
        MusicCategory("artists", Icons.Filled.Person, txt.Artists, Color(0xFFB678E0)),
        MusicCategory("albums", Icons.Filled.Album, txt.Albums, Color(0xFF5AC8A0)),
        MusicCategory("playlists", Icons.Filled.QueueMusic, txt.Playlists, Color(0xFFF0A04B), if (playlists.isNotEmpty()) "${playlists.size} 个" else ""),
        MusicCategory("starred", Icons.Filled.Favorite, txt.Favorites, Color(0xFFE86A6A), if (starredCount > 0) "$starredCount 首" else ""),
        MusicCategory("recent", Icons.Filled.History, "最近", Color(0xFF4AC8D9), if (recents.isNotEmpty()) "${recents.size} 首" else ""),
        MusicCategory("roam", Icons.Filled.Shuffle, txt.Roam, Color(0xFF7A8AE0)),
        MusicCategory("search", Icons.Filled.Search, "搜索", Color(0xFF9AA0A6)),
    )

    fun loadRandom() {
        val c = client ?: return
        if (randomLoading) return
        randomLoading = true
        scope.launch {
            try {
                randomSongs = c.getRandomSongs(20)
            } catch (e: Exception) {
                Timber.w(e, "随机歌曲加载失败")
            }
            randomLoading = false
        }
    }

    fun loadRandomAlbums() {
        val c = client ?: return
        if (albumsLoading) return
        albumsLoading = true
        scope.launch {
            try {
                // 专辑封面比歌曲封面可靠得多，优先展示
                randomAlbums = c.getAlbums("random", 10)
            } catch (e: Exception) {
                Timber.w(e, "随机专辑加载失败")
            }
            albumsLoading = false
        }
    }

    fun playAlbum(album: MusicAlbum) {
        val c = client ?: return
        scope.launch {
            try {
                val songs = c.getAlbumSongs(album.id)
                if (songs.isNotEmpty()) {
                    (player as? MusicPlayerManager)?.roamMode = false
                    player.playTracks(songs, 0)
                }
            } catch (e: Exception) {
                Timber.w(e, "专辑歌曲加载失败")
            }
        }
    }

    fun loadPlaylists() {
        val c = client ?: return
        scope.launch {
            try {
                playlists = c.getPlaylists()
            } catch (e: Exception) {
                Timber.w(e, "歌单加载失败")
            }
        }
    }

    fun loadStarredCount() {
        val c = client ?: return
        scope.launch {
            try {
                starredCount = c.getStarredSongs().size
            } catch (e: Exception) {
                Timber.w(e, "收藏数量加载失败")
            }
        }
    }

    fun startRoam() {
        val c = client ?: return
        scope.launch {
            try {
                val songs = c.getRandomSongs(50)
                if (songs.isNotEmpty()) {
                    (player as? MusicPlayerManager)?.roamMode = true
                    player.playTracks(songs.shuffled())
                }
            } catch (e: Exception) {
                Timber.w(e, "音乐漫游失败")
            }
        }
    }

    fun onCategoryClick(kind: String) {
        when (kind) {
            "songs", "artists", "albums", "playlists", "starred" -> navigator.push(Route.MusicBrowser(kind = kind))
            "roam" -> startRoam()
            "search" -> navigator.push(Route.MusicBrowser(kind = "search"))
            else -> {}
        }
    }

    LaunchedEffect(client) {
        loadRandom()
        loadRandomAlbums()
        loadPlaylists()
        loadStarredCount()
    }

    Scaffold(
        topBar = {
            TopBar(
                title = txt.Title,
                scrollBehavior = scrollBehavior,
                navigationIcon = { NavigationBackIcon(navigator = navigator) },
                actions = {
                    IconButton(onClick = { navigator.push(Route.MusicBrowser(kind = "search")) }) {
                        Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                    }
                    IconButton(onClick = { navigator.push(Route.MusicSettings) }) {
                        Icon(imageVector = Icons.Filled.Settings, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (client == null) {
                // 未配置音乐服：引导去配置
                ScreenLazyColumn(
                    scrollBehavior = scrollBehavior,
                    innerPadding = mainLikePadding,
                    modifier = Modifier.weight(1f),
                ) {
                    item {
                        Card(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp).clickable { navigator.push(Route.MusicServer) },
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(text = txt.NotConfigured, fontSize = MiuixTheme.textStyles.title4.fontSize)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = txt.NotConfiguredHint,
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                    // 网易云入口已移到音乐设置页「音乐服」下，此处不再单独展示
                }
            } else {
                ScreenLazyColumn(
                    scrollBehavior = scrollBehavior,
                    innerPadding = mainLikePadding,
                    modifier = Modifier.weight(1f),
                ) {
            // 欢迎卡片（仿流云音乐）：喜欢/最近/歌单统计
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "欢迎回来", fontSize = MiuixTheme.textStyles.title3.fontSize)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            WelcomeStat(icon = Icons.Filled.Favorite, tint = Color(0xFFE86A6A), count = "$starredCount", label = txt.Favorites)
                            WelcomeStat(icon = Icons.Filled.History, tint = Color(0xFF4A90D9), count = "${recents.size}", label = "最近")
                            WelcomeStat(icon = Icons.Filled.QueueMusic, tint = Color(0xFFF0A04B), count = "${playlists.size}", label = txt.Playlists)
                        }
                    }
                }
            }

            // 网易云入口已移到音乐设置页「音乐服」下，此处不再单独展示

            // 精选歌单：第一张歌单大卡
            if (playlists.isNotEmpty()) {
                item {
                    val featured = playlists.first()
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable { navigator.push(Route.MusicBrowser(kind = "playlists")) },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MusicCover(
                                url = featured.coverArtUrl,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                                title = featured.name,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = featured.name,
                                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${featured.songCount} 首歌曲",
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // 八宫格分类
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    for (row in 0..1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            for (col in 0..3) {
                                val cat = categories[row * 4 + col]
                                Card(
                                    modifier = Modifier.weight(1f).clickable { onCategoryClick(cat.kind) },
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        androidx.compose.foundation.layout.Box(
                                            modifier = Modifier.size(44.dp)
                                                .clip(RoundedCornerShape(22.dp))
                                                .background(cat.tint.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(imageVector = cat.icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = cat.tint)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(text = cat.title, fontSize = MiuixTheme.textStyles.body2.fontSize, maxLines = 1)
                                        if (cat.subtitle.isNotBlank()) {
                                            Text(
                                                text = cat.subtitle,
                                                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (row == 0) Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            // 随机专辑（封面更可靠）
            item {
                MusicSectionTitle(
                    text = "随机专辑",
                    actionIcon = Icons.Filled.Refresh,
                    onAction = { loadRandomAlbums() },
                )
            }
            item {
                if (albumsLoading && randomAlbums.isEmpty()) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(randomAlbums) { album ->
                            Column(
                                modifier = Modifier.width(120.dp).clickable { playAlbum(album) },
                            ) {
                                MusicCover(
                                    url = album.coverArtUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)),
                                    artist = album.artist,
                                    title = album.name,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = album.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = MiuixTheme.textStyles.body2.fontSize)
                                Text(
                                    text = album.artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }

            // 随机歌曲
            item {
                MusicSectionTitle(
                    text = txt.RandomSongs,
                    actionIcon = Icons.Filled.Refresh,
                    onAction = { loadRandom() },
                )
            }
            item {
                if (randomLoading && randomSongs.isEmpty()) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(randomSongs) { track ->
                            Column(
                                modifier = Modifier.width(120.dp).clickable {
                                    (player as? MusicPlayerManager)?.roamMode = false
                                    player.playTracks(randomSongs, randomSongs.indexOf(track))
                                },
                            ) {
                                MusicCover(
                                    url = track.coverArtUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)),
                                    artist = track.artist,
                                    title = track.title,
                                    preferScraped = true,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = MiuixTheme.textStyles.body2.fontSize)
                                Text(
                                    text = track.artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }

            // 最近播放
            item {
                MusicSectionTitle(text = txt.RecentPlayed)
            }
            item {
                if (recents.isEmpty()) {
                    Text(
                        text = txt.RecentEmpty,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(recents) { track ->
                            Column(
                                modifier = Modifier.width(120.dp).clickable {
                                    (player as? MusicPlayerManager)?.roamMode = false
                                    player.playTracks(recents, recents.indexOf(track))
                                },
                            ) {
                                MusicCover(
                                    url = track.coverArtUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)),
                                    artist = track.artist,
                                    title = track.title,
                                    preferScraped = true,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = MiuixTheme.textStyles.body2.fontSize)
                                Text(
                                    text = track.artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        MiniPlayer(player = player, onOpenPlayer = { navigator.push(Route.NowPlaying) })
            }
        }
    }
}

/** 欢迎卡片里的统计项：图标+数量+标签。 */
@Composable
private fun WelcomeStat(
    icon: ImageVector,
    tint: Color,
    count: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = count, fontSize = MiuixTheme.textStyles.title4.fontSize)
        Text(
            text = label,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
