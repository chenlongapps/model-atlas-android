package io.github.chenlongapps.modelatlas.data.model

/** 两个有顺序的规范模型 ID，不保存目录内容。 */
data class CompareModelSelection(val firstId: String? = null, val secondId: String? = null) {
    internal fun normalized() = CompareModelSelection(
        firstId?.takeIf(String::isNotBlank),
        secondId?.takeIf { it.isNotBlank() && it != firstId },
    )
}
