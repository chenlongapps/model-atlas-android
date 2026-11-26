package io.github.chenlongapps.modelatlas.ui.navigation

/** 胶囊在起始侧、AI 入口在末侧；双击命中范围按胶囊实际宽度、栏数与 RTL 布局计算。 */
internal fun navigationModelsRange(
    width: Float,
    aiWidth: Float,
    gap: Float,
    capsulePadding: Float,
    spacing: Float,
    itemCount: Int,
    isRtl: Boolean,
): ClosedFloatingPointRange<Float> {
    val contentWidth = width - aiWidth - gap - capsulePadding * 2
    val range = modelsTabRange(contentWidth, spacing, itemCount, isRtl)
    val capsuleStart = if (isRtl) aiWidth + gap else 0f
    return (capsuleStart + capsulePadding + range.start)..(capsuleStart + capsulePadding + range.endInclusive)
}

/** 模型固定为首个标签；双击命中范围必须与实际栏数、间距及 RTL 布局一致。 */
internal fun modelsTabRange(width: Float, spacing: Float, itemCount: Int, isRtl: Boolean): ClosedFloatingPointRange<Float> {
    val itemWidth = NavigationTabGeometry(width, spacing, itemCount, isRtl).itemWidth
    return if (isRtl) (width - itemWidth)..width else 0f..itemWidth
}

internal class NavigationTabGeometry(
    private val width: Float,
    spacing: Float,
    private val itemCount: Int,
    private val isRtl: Boolean,
) {
    init { require(itemCount > 0) }

    val itemWidth = ((width - spacing * (itemCount - 1)) / itemCount).coerceAtLeast(0f)
    val step = itemWidth + spacing
    val direction get() = if (isRtl) -1f else 1f

    fun logicalX(x: Float): Float = if (isRtl) width - x else x
    fun center(position: Float): Float = position * step + itemWidth * 0.5f
    fun positionAt(logicalX: Float): Float = (logicalX - center(0f)) / step

    fun tabAt(logicalX: Float): Int? {
        if (step <= 0f || logicalX < 0f || logicalX > width) return null
        val index = (logicalX / step).toInt().coerceAtMost(itemCount - 1)
        return index.takeIf { logicalX - index * step <= itemWidth }
    }
}
