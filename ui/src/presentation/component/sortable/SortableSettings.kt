package com.suanran.dreambox.presentation.component.sortable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suanran.dreambox.presentation.component.card.Card
import com.tencent.mmkv.MMKV
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 可拖拽的设置分节卡片：包住原有分节内容，长按拖动排序。
 * 底部操作条有"..."菜单（隐藏）和拖拽把手。
 */
@Composable
fun SortableSectionCard(
    isDragging: Boolean,
    isEditing: Boolean = false,
    onDelete: () -> Unit = {},
    onHide: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .then(
                if (isDragging) Modifier.shadow(12.dp, RoundedCornerShape(20.dp))
                else Modifier,
            ),
    ) {
        content()
        // 底部操作条："..."菜单 + 拖拽把手
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onHide != null) {
                ModuleMenuButton(onHide = onHide)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            } else if (isEditing) {
                Icon(
                    imageVector = Icons.Filled.RemoveCircle,
                    contentDescription = "删除",
                    tint = MiuixTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                        .clickable(onClick = onDelete),
                )
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            }
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = "拖动排序",
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
            )
        }
    }
}

/** 重置排序按钮：恢复默认顺序。 */
@Composable
fun ResetOrderButton(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.RestartAlt,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Text(
                text = "重置位置",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "长按卡片拖动可排序",
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

/** 加载分节排序，key 不在默认列表的会被丢弃，新分节追加到末尾。 */
fun loadSectionOrder(mmkv: MMKV, pageKey: String, defaults: List<String>): List<String> {
    val saved = mmkv.decodeString("section_order_$pageKey", "").orEmpty()
    if (saved.isBlank()) return defaults
    val order = saved.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val ordered = order.filter { it in defaults }
    val missing = defaults.filter { it !in ordered }
    return (ordered + missing).distinct()
}

/** 保存分节排序。 */
fun saveSectionOrder(mmkv: MMKV, pageKey: String, order: List<String>) {
    mmkv.encode("section_order_$pageKey", order.joinToString(","))
}

/** 清除分节排序（恢复默认）。 */
fun clearSectionOrder(mmkv: MMKV, pageKey: String) {
    mmkv.remove("section_order_$pageKey")
}

/** 加载被隐藏的分节 key 集合。 */
fun loadHiddenSections(mmkv: MMKV, pageKey: String): Set<String> {
    return mmkv.decodeString("section_hidden_$pageKey", "").orEmpty()
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

/** 保存被隐藏的分节 key 集合。 */
fun saveHiddenSections(mmkv: MMKV, pageKey: String, hidden: Set<String>) {
    mmkv.encode("section_hidden_$pageKey", hidden.joinToString(","))
}

/**
 * 已隐藏分节的恢复列表：显示在页面底部，点击恢复。
 * 在 LazyColumn 的 scope 内调用。
 *
 * @param hidden 按顺序的隐藏 key 列表
 * @param titleOf 根据 key 获取显示标题
 * @param onRestore 恢复回调
 */
fun androidx.compose.foundation.lazy.LazyListScope.hiddenSectionsRestore(
    hidden: List<String>,
    titleOf: (String) -> String,
    onRestore: (String) -> Unit,
) {
    if (hidden.isEmpty()) return
    item {
        Text(
            text = "已隐藏的分节（点击恢复）",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    items(hidden.size) { i ->
        val key = hidden[i]
        Card(
            modifier = Modifier.fillMaxWidth()
                .clickable { onRestore(key) },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "恢复",
                    tint = MiuixTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text(
                    text = titleOf(key),
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}
