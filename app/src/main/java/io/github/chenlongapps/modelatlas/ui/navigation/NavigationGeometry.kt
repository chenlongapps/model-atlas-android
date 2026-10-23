package io.github.chenlongapps.modelatlas.ui.navigation

/** 模型固定为首个标签；双击命中范围必须与实际栏数、间距及 RTL 布局一致。 */
internal fun modelsTabRange(width: Float, spacing: Float, itemCount: Int, isRtl: Boolean): ClosedFloatingPointRange<Float> {
    require(itemCount > 0)
    val itemWidth = ((width - spacing * (itemCount - 1)) / itemCount).coerceAtLeast(0f)
    return if (isRtl) (width - itemWidth)..width else 0f..itemWidth
}
