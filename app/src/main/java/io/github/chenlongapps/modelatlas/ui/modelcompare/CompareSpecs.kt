package io.github.chenlongapps.modelatlas.ui.modelcompare

import io.github.chenlongapps.modelatlas.data.model.ModelDetailMetadata
import io.github.chenlongapps.modelatlas.data.model.toModalityCategories

internal enum class SpecDifference { MISSING, SAME, DIFFERENT }

/** 非规格分组的文本／布尔行沿用规格行的显示规则。 */
internal fun isCompareRowVisible(first: Any?, second: Any?, onlyDifferences: Boolean): Boolean =
    (first != null || second != null) && (!onlyDifferences || first != second)

internal data class CompareSpec<T : Any>(val first: T?, val second: T?) {
    val difference: SpecDifference get() = when {
        first == null || second == null -> SpecDifference.MISSING
        first == second -> SpecDifference.SAME
        else -> SpecDifference.DIFFERENT
    }
    fun isVisible(onlyDifferences: Boolean): Boolean =
        (first != null || second != null) && (!onlyDifferences || difference != SpecDifference.SAME)
}

internal class CompareSpecs(first: ModelDetailMetadata, second: ModelDetailMetadata) {
    val context = CompareSpec(first.contextLimitTokens, second.contextLimitTokens)
    val output = CompareSpec(first.outputLimitTokens, second.outputLimitTokens)
    val input = CompareSpec(first.inputLimitTokens, second.inputLimitTokens)
    val inputModalities = CompareSpec(first.inputModalities.toModalityCategories()?.toSet(),
        second.inputModalities.toModalityCategories()?.toSet())
    val outputModalities = CompareSpec(first.outputModalities.toModalityCategories()?.toSet(),
        second.outputModalities.toModalityCategories()?.toSet())
    val weights = CompareSpec(first.openWeights, second.openWeights)
    val fields = listOf(context, output, input, inputModalities, outputModalities, weights)
}
