package io.github.chenlongapps.modelatlas.ui.modelcompare

import io.github.chenlongapps.modelatlas.data.model.ModelDetailMetadata
import io.github.chenlongapps.modelatlas.data.model.ModelParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test fun modelInfoRowsReuseTheSpecVisibilityRules() {
        assertFalse(isCompareRowVisible(null, null, false))
        assertFalse(isCompareRowVisible(null, null, true))
        assertTrue(isCompareRowVisible(null, "MIT", true))
        assertTrue(isCompareRowVisible(true, false, true))
        assertTrue(isCompareRowVisible(true, true, false))
        assertFalse(isCompareRowVisible(true, true, true))
    }

    @Test fun parameterCountsCompareExactlyAndIgnoreScaleAndAncillaryFields() {
        val first = ModelParameters(totalBillion = "671.00".toBigDecimal(), activeBillion = "37.0".toBigDecimal())
        val same = ModelParameters(totalBillion = "671".toBigDecimal(), activeBillion = "37.000".toBigDecimal(),
            notes = "Different source notes")
        assertEquals(SpecDifference.SAME, compareParameterCounts(first, same))
        assertEquals(SpecDifference.DIFFERENT, compareParameterCounts(first,
            same.copy(activeBillion = "37.000000000000000001".toBigDecimal())))
        assertEquals(SpecDifference.SAME, compareParameterCounts(first.copy(activeBillion = null),
            same.copy(activeBillion = null)))
    }

    @Test fun parameterCountDisclosureAloneIsNotAConfirmedDifference() {
        val first = ModelParameters(totalBillion = "671".toBigDecimal(), activeBillion = "37".toBigDecimal())
        val totalOnly = first.copy(activeBillion = null)
        val activeOnly = first.copy(totalBillion = null)
        assertEquals(SpecDifference.MISSING, compareParameterCounts(first, totalOnly))
        assertEquals(SpecDifference.MISSING, compareParameterCounts(totalOnly, activeOnly))
        assertEquals(SpecDifference.MISSING, compareParameterCounts(null, activeOnly))
        assertEquals(SpecDifference.DIFFERENT, compareParameterCounts(first,
            totalOnly.copy(totalBillion = "672".toBigDecimal())))
        assertNull(compareParameterCounts(ModelParameters(), null))
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
