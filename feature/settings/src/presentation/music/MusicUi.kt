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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.panpf.sketch.rememberAsyncImagePainter
import com.github.panpf.sketch.request.ImageRequest
import com.suanran.dreambox.core.contract.AppSettingsReader
import com.suanran.dreambox.core.music.MusicPlayer
import com.suanran.dreambox.core.music.MusicTrack
import com.suanran.dreambox.feature.settings.data.music.SubsonicClient
import com.suanran.dreambox.presentation.theme.AppTheme
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 按当前配置创建 Subsonic 客户端；未配置（地址/账号为空）时返回 null。 */
@Composable
fun rememberSubsonicClient(): SubsonicClient? {
    val settings = koinInject<AppSettingsReader>()
    val url by settings.musicServerUrl.state.collectAsStateWithLifecycle()
    val user by settings.musicServerUser.state.collectAsStateWithLifecycle()
    val pass by settings.musicServerPassword.state.collectAsStateWithLifecycle()
    return remember(url, user, pass) {
        if (url.isBlank() || user.isBlank()) null else SubsonicClient(url, user, pass)
    }
}

/** 封面图：有地址加载网络图；无地址时尝试外部刮削（iTunes），都无则显示音符占位。 */
@Composable
fun MusicCover(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    artist: String = "",
    title: String = "",
    preferScraped: Boolean = false,
) {
    val context = LocalContext.current
    var scrapedUrl by remember(url, artist, title) { mutableStateOf<String?>(null) }

    // 有歌手/歌名就异步刮削；iTunes 免费接口，无 key
    if ((url.isBlank() || preferScraped) && (artist.isNotBlank() || title.isNotBlank())) {
        LaunchedEffect(artist, title) {
            scrapedUrl = ArtworkScraper.scrape(artist, title)
        }
    }

    // preferScraped 时优先用刮削结果（服务端大概率是蓝碟片占位图），刮不到再回退服务端
    val displayUrl = if (preferScraped) scrapedUrl?.ifBlank { url }.orEmpty().ifBlank { url } else url.ifBlank { scrapedUrl.orEmpty() }
    if (displayUrl.isNotBlank()) {
        androidx.compose.foundation.Image(
            painter = rememberAsyncImagePainter(ImageRequest(context, displayUrl)),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier.background(MiuixTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** 单曲行：封面 + 标题/艺术家/元信息 + 收藏按钮。 */
@Composable
fun SongRow(
    track: MusicTrack,
    onClick: () -> Unit,
    onToggleStar: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MusicCover(
            url = track.coverArtUrl,
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
            artist = track.artist,
            title = track.title,
            preferScraped = true,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontSize = MiuixTheme.textStyles.title4.fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta =
                buildList {
                    if (track.artist.isNotBlank()) add(track.artist)
                    if (track.bitrate > 0) add("${track.bitrate}kbps")
                    if (track.suffix.isNotBlank()) add(track.suffix.uppercase())
                    if (track.durationMs > 0) add(formatDuration(track.durationMs))
                }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onToggleStar != null) {
            IconButton(onClick = onToggleStar) {
                Icon(
                    imageVector = if (track.starred) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint =
                        if (track.starred) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/** 底部迷你播放器。 */
@Composable
fun MiniPlayer(
    player: MusicPlayer = koinInject(),
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = player.currentTrack.collectAsStateWithLifecycle().value ?: return
    val playing = player.isPlaying.collectAsStateWithLifecycle().value
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onOpenPlayer)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MusicCover(
            url = track.coverArtUrl,
            contentDescription = null,
            modifier = Modifier.size(44.dp).clip(CircleShape),
            artist = track.artist,
            title = track.title,
            preferScraped = true,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = track.artist,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { player.toggle() }) {
            Icon(
                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
            )
        }
    }
}

/** 分区标题（带右侧操作，如刷新）。 */
@Composable
fun MusicSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            fontSize = MiuixTheme.textStyles.title4.fontSize,
            fontWeight = FontWeight.Bold,
        )
        if (actionIcon != null && onAction != null) {
            IconButton(onClick = onAction) {
                Icon(imageVector = actionIcon, contentDescription = null)
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
