package com.suanran.dreambox.presentation.component.sortable

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex

/**
 * 可拖拽排序状态：长按抓起 → 拖动 → 磁吸换位（带震动反馈）。
 *
 * 用法：
 * ```
 * val dragState = rememberDragDropState(lazyListState) { from, to -> onMove(from, to) }
 * LazyColumn(state = lazyListState) {
 *     itemsIndexed(modules, key = { _, m -> m.key }) { index, module ->
 *         DraggableItem(dragState, index) { isDragging ->
 *             ModuleCard(module, isDragging)
 *         }
 *     }
 * }
 * ```
 */
class DragDropState(
    private val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
) {
    var draggingIndex by mutableStateOf<Int?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    private var dragStartOffset = 0f
    private var lastTargetIndex: Int? = null

    fun onDragStart(index: Int, itemOffset: Int) {
        draggingIndex = index
        dragStartOffset = itemOffset.toFloat()
        dragOffset = 0f
        lastTargetIndex = index
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun onDrag(offset: Offset) {
        val idx = draggingIndex ?: return
        dragOffset += offset.y
        val current = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx } ?: return
        val center = dragStartOffset + dragOffset + current.size / 2f
        val target = findTargetIndex(center, current)
        if (target != null && target != lastTargetIndex) {
            lastTargetIndex = target
            onMove(idx, target)
            draggingIndex = target
            // 换位时给一个轻震动，模拟"磁吸"吸附感
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun onDragEnd() {
        if (draggingIndex != null) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        }
        draggingIndex = null
        dragOffset = 0f
        lastTargetIndex = null
    }

    private fun findTargetIndex(center: Float, dragged: LazyListItemInfo): Int? {
        val infos = listState.layoutInfo.visibleItemsInfo
        // 找到中心点落在哪一项上
        val over = infos.firstOrNull { info ->
            info.index != dragged.index && center in info.offset.toFloat()..(info.offset + info.size).toFloat()
        } ?: return null
        return over.index
    }
}

@Composable
fun rememberDragDropState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
): DragDropState {
    val haptic = LocalHapticFeedback.current
    return remember(listState) { DragDropState(listState, onMove, haptic) }
}

/**
 * 包裹每一项，使其可长按拖拽。拖拽中的项会上浮（zIndex + 阴影由调用方按 isDragging 处理）。
 */
@Composable
fun DraggableItem(
    state: DragDropState,
    index: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable (isDragging: Boolean) -> Unit,
) {
    val isDragging = state.draggingIndex == index
    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = if (isDragging) state.dragOffset else 0f
                scaleX = if (isDragging) 1.03f else 1f
                scaleY = if (isDragging) 1.03f else 1f
            }
            .pointerInput(index, enabled) {
                if (!enabled) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        state.onDragStart(index, 0)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        state.onDrag(dragAmount)
                    },
                    onDragEnd = { state.onDragEnd() },
                    onDragCancel = { state.onDragEnd() },
                )
            },
    ) {
        content(isDragging)
    }
}
