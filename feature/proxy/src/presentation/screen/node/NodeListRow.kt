package com.suanran.dreambox.feature.proxy.presentation.screen.node

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suanran.dreambox.core.model.proxy.Proxy
import com.suanran.dreambox.feature.proxy.presentation.util.NodeFlagBadge
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.component.misc.EmojiAwareText
import com.suanran.dreambox.presentation.theme.RowRevealAll
import top.yukonga.miuix.kmp.basic.Text
import com.suanran.dreambox.presentation.theme.UiDp
import com.suanran.dreambox.presentation.theme.rememberRowShown
import com.suanran.dreambox.presentation.theme.rowReveal
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun LazyListScope.nodeListRows(
    proxies: List<Proxy>,
    selectedProxyName: String,
    onProxyClick: ((String) -> Unit)? = null,
    isDelayTesting: Boolean = false,
    testingProxyNames: Set<String> = emptySet(),
    onSingleNodeTestClick: ((String) -> Unit)? = null,
    itemVerticalPadding: Dp = UiDp.dp6,
    revealCount: State<Int> = RowRevealAll,
) {
    itemsIndexed(
        items = proxies,
        key = { _, proxy -> proxy.name },
        contentType = { _, _ -> "NodeListRow" },
    ) { index, proxy ->
        NodeListRow(
            proxy = proxy,
            isSelected = proxy.name == selectedProxyName,
            onClick = onProxyClick,
            isTesting = isDelayTesting || proxy.name in testingProxyNames,
            onTestClick = onSingleNodeTestClick,
            modifier = Modifier
                .animateItem()
                .rowReveal(rememberRowShown(index, revealCount))
                .padding(vertical = itemVerticalPadding),
        )
    }
}

/**
 * 节点列表行（对标参考 App）：
 * 左国旗徽章，中节点名+协议徽章，右延迟数字。
 */
@Composable
internal fun NodeListRow(
    proxy: Proxy,
    isSelected: Boolean,
    onClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    isTesting: Boolean = false,
    onTestClick: ((String) -> Unit)? = null,
) {
    val delayLabel = nodeLatencyLabel(proxy.delay, withUnit = true)
    val textColor = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
    NodeSelectableCard(
        isSelected = isSelected,
        onClick = remember(proxy.name, onClick) { onClick?.let { click -> { click(proxy.name) } } },
        modifier = modifier,
        paddingVertical = UiDp.dp12,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiDp.dp12),
        ) {
            NodeFlagBadge(nodeName = proxy.name, size = 48.dp, emojiSize = 26.sp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    EmojiAwareText(
                        text = proxy.name,
                        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                        color = textColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NodeTypeChip(label = displayName(proxy.type))
                    if (proxy.delay != null && proxy.delay != 0) {
                        NodePingChip()
                    }
                }
            }
            ProxyDelayIndicator(
                delayLabel = delayLabel,
                isDelayTesting = isTesting,
                onDelayTestClick = remember(proxy.name, onTestClick) {
                    onTestClick?.let { click -> { click(proxy.name) } }
                },
            )
        }
    }
}

@Composable
private fun NodeTypeChip(label: String) {
    val primary = MiuixTheme.colorScheme.primary
    Text(
        text = label,
        style = MiuixTheme.textStyles.footnote1.copy(fontSize = 10.sp),
        color = primary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(primary.copy(alpha = 0.1f))
            .padding(horizontal = UiDp.dp8, vertical = 2.dp),
    )
}

@Composable
private fun NodePingChip() {
    val neutral = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Text(
        text = FlyTxt.Proxy.Node.Pinged,
        style = MiuixTheme.textStyles.footnote1.copy(fontSize = 10.sp),
        color = neutral,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(neutral.copy(alpha = 0.12f))
            .padding(horizontal = UiDp.dp8, vertical = 2.dp),
    )
}
