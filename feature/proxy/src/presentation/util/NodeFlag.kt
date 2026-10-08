package com.suanran.dreambox.feature.proxy.presentation.util

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suanran.dreambox.presentation.util.extractFlaggedName
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val REGIONAL_INDICATOR_BASE = 0x1F1E6

/** ISO 3166-1 二位国家码转国旗 emoji，非法返回 null */
fun countryCodeToFlagEmoji(code: String): String? {
    val upper = code.trim().uppercase()
    if (upper.length != 2 || !upper.all { it in 'A'..'Z' }) return null
    return buildString {
        upper.forEach { appendCodePoint(REGIONAL_INDICATOR_BASE + (it.code - 'A'.code)) }
    }
}

/** 从节点名提取国旗 emoji；无匹配返回 null */
fun nodeFlagEmoji(name: String): String? {
    val code = extractFlaggedName(name).countryCode ?: return null
    return countryCodeToFlagEmoji(code)
}

/** 国旗徽章：圆角方块底 + emoji；无匹配显示默认圆点 */
@Composable
fun NodeFlagBadge(
    nodeName: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    emojiSize: TextUnit = 24.sp,
) {
    val emoji = remember(nodeName) { nodeFlagEmoji(nodeName) }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        if (emoji != null) {
            Text(
                text = emoji,
                style = MiuixTheme.textStyles.body1.copy(fontSize = emojiSize),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.onSurfaceVariantSummary),
            )
        }
    }
}
