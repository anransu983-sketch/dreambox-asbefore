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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.suanran.dreambox.core.contract.AppSettingsReader
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suanran.dreambox.core.music.MusicPlayer
import com.suanran.dreambox.feature.settings.presentation.music.MusicCover
import com.suanran.dreambox.feature.settings.presentation.music.formatDuration
import com.suanran.dreambox.feature.settings.presentation.music.rememberSubsonicClient
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.navigation.Navigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 全屏播放页：大封面 / 歌名 / 当前歌词 / 进度条 / 控制条 / 收藏。
 */
@Composable
fun NowPlayingScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val player = koinInject<MusicPlayer>()
    val client = rememberSubsonicClient()
    val txt = FlyTxt.Settings.Experimental.Music

    val track by player.currentTrack.collectAsStateWithLifecycle()
    val playing by player.isPlaying.collectAsStateWithLifecycle()
    val lyric by player.lyricLine.collectAsStateWithLifecycle()
    val position by player.positionMs.collectAsStateWithLifecycle()
    val duration by player.durationMs.collectAsStateWithLifecycle()
    val shuffle by player.shuffleEnabled.collectAsStateWithLifecycle()
    val repeatOne by player.repeatOne.collectAsStateWithLifecycle()
    // 歌词样式设置
    val settings = koinInject<AppSettingsReader>()
    val lyricFontSize by settings.musicLyricFontSize.state.collectAsStateWithLifecycle()
    val lyricColor by settings.musicLyricColor.state.collectAsStateWithLifecycle()

    val t = track
    val scope = rememberCoroutineScope()
    var starred by remember(t?.id) { mutableStateOf(t?.starred == true) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 顶栏：返回 + 正在播放
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavigationBackIcon(navigator = navigator)
                Text(
                    text = txt.NowPlaying,
                    fontSize = MiuixTheme.textStyles.title3.fontSize,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.size(48.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 大封面（仿参考图：方形大圆角）
            MusicCover(
                url = t?.coverArtUrl.orEmpty(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)),
                artist = t?.artist.orEmpty(),
                title = t?.title.orEmpty(),
                preferScraped = true,
            )
            Spacer(modifier = Modifier.height(20.dp))
            // 歌名 + 歌手 + 分享/收藏按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (t != null) "《${t.title}》" else txt.NoTrack,
                        fontSize = MiuixTheme.textStyles.title2.fontSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = t?.artist.orEmpty(),
                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 收藏按钮（黑底圆）
                if (t != null && client != null) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.size(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MiuixTheme.colorScheme.onSurface)
                            .clickable {
                                starred = !starred
                                scope.launch(Dispatchers.IO) {
                                    runCatching { if (starred) client.star(t.id) else client.unstar(t.id) }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (starred) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.surface,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 当前歌词行（字号/颜色可设置）
            Text(
                text = lyric ?: t?.album.orEmpty(),
                fontSize = lyricFontSize.sp,
                color = Color(lyricColor),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            // 进度条
            Slider(
                value = if (duration > 0) position.toFloat() / duration.toFloat() else 0f,
                onValueChange = { frac -> player.seekTo((frac * duration).toLong()) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = formatDuration(position), fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text(text = formatDuration(duration), fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Spacer(modifier = Modifier.height(24.dp))
            // 控制条：上一首 | 大播放键 | 下一首（仿参考图）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 上一首（浅色圆）
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(64.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant)
                        .clickable { player.previous() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = Icons.Filled.SkipPrevious, contentDescription = null, modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.width(24.dp))
                // 播放/暂停（黑色大胶囊）
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.height(64.dp).width(160.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(MiuixTheme.colorScheme.onSurface)
                        .clickable { player.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.surface,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (playing) "暂停" else "播放",
                            color = MiuixTheme.colorScheme.surface,
                            fontSize = MiuixTheme.textStyles.title4.fontSize,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(24.dp))
                // 下一首（浅色圆）
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(64.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant)
                        .clickable { player.next() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = Icons.Filled.SkipNext, contentDescription = null, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            // 底部小按钮行：随机 / 单曲循环
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { player.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = null,
                        tint = if (shuffle) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.width(32.dp))
                IconButton(onClick = { player.toggleRepeatOne() }) {
                    Icon(
                        imageVector = if (repeatOne) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = null,
                        tint = if (repeatOne) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
