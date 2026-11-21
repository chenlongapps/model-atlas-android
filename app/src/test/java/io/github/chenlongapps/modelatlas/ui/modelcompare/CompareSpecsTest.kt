package io.github.chenlongapps.modelatlas.ui.modelcompare

import io.github.chenlongapps.modelatlas.data.model.ModelDetailMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareSpecsTest {
    @Test fun sameModalitiesIgnoreOrderDuplicatesAndEquivalentMimeCategories() {
        val specs = CompareSpecs(
            ModelDetailMetadata(inputModalities = listOf("text", "image/png", "image/jpeg", "future")),
            ModelDetailMetadata(inputModalities = listOf("future", "image", "text/plain")),
        )
        assertEquals(SpecDifference.SAME, specs.inputModalities.difference)
        assertTrue(specs.inputModalities.isVisible(false))
        assertFalse(specs.inputModalities.isVisible(true))
    }

    @Test fun missingStaysVisibleWhenTheOtherValueIsExplicitlyEmptyFalseOrZero() {
        val specs = CompareSpecs(
            ModelDetailMetadata(outputModalities = emptyList(), openWeights = false, inputLimitTokens = 0),
            ModelDetailMetadata(),
        )
        listOf(specs.outputModalities, specs.weights, specs.input).forEach {
            assertEquals(SpecDifference.MISSING, it.difference)
            assertTrue(it.isVisible(true))
        }
        assertFalse(specs.output.isVisible(false))
        assertFalse(specs.output.isVisible(true))
    }

    @Test fun exactTokenValuesAndExplicitEmptyModalitiesCompareBeforeFormatting() {
        val specs = CompareSpecs(
            ModelDetailMetadata(contextLimitTokens = 131_070, outputModalities = emptyList(), openWeights = false),
            ModelDetailMetadata(contextLimitTokens = 131_072, outputModalities = listOf("text"), openWeights = false),
        )
        assertEquals(SpecDifference.DIFFERENT, specs.context.difference)
        assertEquals(SpecDifference.DIFFERENT, specs.outputModalities.difference)
        assertFalse(specs.weights.isVisible(true))
        assertTrue(specs.context.isVisible(true))
    }
}
