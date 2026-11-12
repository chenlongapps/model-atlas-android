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
    require(itemCount > 0)
    val itemWidth = ((width - spacing * (itemCount - 1)) / itemCount).coerceAtLeast(0f)
    return if (isRtl) (width - itemWidth)..width else 0f..itemWidth
}
