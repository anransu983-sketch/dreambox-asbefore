package com.suanran.dreambox.feature.settings.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suanran.dreambox.core.contract.AppSettingsReader
import com.suanran.dreambox.presentation.component.misc.BocchiCard
import com.suanran.dreambox.presentation.component.layout.ScreenLazyColumn
import com.suanran.dreambox.presentation.component.layout.combinePaddingValues
import com.suanran.dreambox.presentation.component.layout.rememberStandalonePageMainPadding
import com.suanran.dreambox.presentation.component.navigation.NavigationBackIcon
import com.suanran.dreambox.presentation.component.navigation.TopBar
import com.suanran.dreambox.presentation.navigation.Navigator
import com.suanran.dreambox.presentation.theme.AppTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 歌词样式设置页：字号滑杆 + 颜色预设 + 实时预览。
 */
@Composable
fun LyricStyleScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val settings = koinInject<AppSettingsReader>()
    val spacing = AppTheme.spacing
    val fontSize by settings.musicLyricFontSize.state.collectAsStateWithLifecycle()
    val lyricColor by settings.musicLyricColor.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val presetColors = listOf(
        0xFF4CAF50.toInt(), 0xFFFFEB3B.toInt(), 0xFF2196F3.toInt(),
        0xFFF44336.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(),
        0xFFFF9800.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt(),
        0xFF138A74.toInt(),
    )

    Scaffold(
        topBar = {
            TopBar(
                title = "歌词样式",
                scrollBehavior = scrollBehavior,
                navigationIcon = { NavigationBackIcon(navigator = navigator) },
            )
        },
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
            verticalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            item {
                BocchiCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = spacing.space16,
                            vertical = spacing.space16,
                        ),
                    ) {
                        // 字号
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "字号",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.width(48.dp),
                            )
                            Slider(
                                value = fontSize,
                                onValueChange = { scope.launch { settings.musicLyricFontSize.set(it) } },
                                valueRange = 12f..28f,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${fontSize.toInt()}",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.width(32.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(spacing.space12))
                        // 颜色
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "颜色",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.width(48.dp),
                            )
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                presetColors.forEach { c ->
                                    val selected = lyricColor == c
                                    Box(
                                        modifier = Modifier.size(if (selected) 30.dp else 26.dp)
                                            .clip(RoundedCornerShape(15.dp))
                                            .background(Color(c))
                                            .border(
                                                width = if (selected) 2.dp else 1.dp,
                                                color = if (selected) MiuixTheme.colorScheme.primary else Color(0x33000000),
                                                shape = RoundedCornerShape(15.dp),
                                            )
                                            .clickable { scope.launch { settings.musicLyricColor.set(c) } },
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(spacing.space16))
                        // 预览
                        Text(
                            text = "预览效果",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(modifier = Modifier.height(spacing.space8))
                        Text(
                            text = "歌词预览效果",
                            fontSize = fontSize.sp,
                            color = Color(lyricColor),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
