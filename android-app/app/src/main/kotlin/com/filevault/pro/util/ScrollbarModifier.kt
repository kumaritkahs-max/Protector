package com.filevault.pro.util

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

fun Modifier.simpleScrollbar(
    state: LazyListState,
    color: Color = Color(0x66888888),
    widthPx: Float = 6f
): Modifier = this.drawWithContent {
    drawContent()
    val info = state.layoutInfo
    val totalItems = info.totalItemsCount
    val visibleCount = info.visibleItemsInfo.size
    if (totalItems <= visibleCount || totalItems == 0) return@drawWithContent

    val fraction = state.firstVisibleItemIndex.toFloat() / (totalItems - visibleCount).coerceAtLeast(1).toFloat()
    val barH = (size.height * visibleCount.toFloat() / totalItems.toFloat()).coerceAtLeast(40f)
    val barTop = fraction * (size.height - barH)

    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - widthPx - 2f, barTop),
        size = Size(widthPx, barH),
        cornerRadius = CornerRadius(widthPx / 2f)
    )
}

fun Modifier.gridScrollbar(
    state: LazyGridState,
    color: Color = Color(0x66888888),
    widthPx: Float = 6f
): Modifier = this.drawWithContent {
    drawContent()
    val info = state.layoutInfo
    val totalItems = info.totalItemsCount
    val visibleCount = info.visibleItemsInfo.size
    if (totalItems <= visibleCount || totalItems == 0) return@drawWithContent

    val fraction = state.firstVisibleItemIndex.toFloat() / (totalItems - visibleCount).coerceAtLeast(1).toFloat()
    val barH = (size.height * visibleCount.toFloat() / totalItems.toFloat()).coerceAtLeast(40f)
    val barTop = fraction * (size.height - barH)

    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - widthPx - 2f, barTop),
        size = Size(widthPx, barH),
        cornerRadius = CornerRadius(widthPx / 2f)
    )
}
