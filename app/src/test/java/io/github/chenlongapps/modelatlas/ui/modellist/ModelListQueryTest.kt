package io.github.chenlongapps.modelatlas.ui.modellist

import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelListQueryTest {
    private val open = ModelSummary(
        id = "alpha/vision", name = "Atlas Vision", openWeights = true,
        inputModalities = listOf("text", "image", "custom"), outputModalities = listOf("text"),
        contextLimitTokens = 128_000, outputLimitTokens = 32_000,
        price = ModelPriceSummary(providerId = "alpha", providerName = "Alpha", isOfficial = true, input = 0.0),
    )
    private val closed = ModelSummary(
        id = "beta/atlas", name = "Atlas Chat", openWeights = false,
        inputModalities = listOf("text"), outputModalities = listOf("text", "image"),
        contextLimitTokens = 1_000_000, outputLimitTokens = 4_000,
    )
    private val unknown = ModelSummary("orphan", "未知模型")
    private val models = listOf(closed, unknown, open)

    @Test fun defaultAndWhitespaceQueryKeepOriginalListAndOrder() {
        assertSame(models, filterModels(models, ModelListQuery()))
        assertSame(models, filterModels(models, ModelListQuery(search = "  \t\n ")))
    }

    @Test fun searchesNameIdAndLabIgnoringCaseAndSurroundingWhitespace() {
        assertEquals(listOf(open), filterModels(models, ModelListQuery(search = "  VISION  ")))
        assertEquals(listOf(closed), filterModels(models, ModelListQuery(search = "beta/ATLAS")))
        assertEquals(listOf(open), filterModels(models, ModelListQuery(search = "ALPHA")))
        assertEquals(listOf(unknown), filterModels(models, ModelListQuery(search = "未知")))
    }

    @Test fun termsCanMatchDifferentFieldsButEveryTermMustMatch() {
        assertEquals(listOf(open), filterModels(models, ModelListQuery(search = "  ALPHA\tAtlas \n")))
        assertTrue(filterModels(models, ModelListQuery(search = "alpha missing")).isEmpty())
    }

    @Test fun labsAreOrAndMissingLabDoesNotMatch() {
        assertEquals(listOf(closed, open), filterModels(models, ModelListQuery(labs = setOf("alpha", "beta"))))
        assertTrue(filterModels(models, ModelListQuery(labs = setOf("gone"))).isEmpty())
    }

    @Test fun weightsRetainAllThreeStates() {
        assertEquals(listOf(open), filterModels(models, ModelListQuery(weights = WeightFilter.OPEN)))
        assertEquals(listOf(closed), filterModels(models, ModelListQuery(weights = WeightFilter.CLOSED)))
        assertEquals(listOf(unknown), filterModels(models, ModelListQuery(weights = WeightFilter.UNKNOWN)))
    }

    @Test fun inputAndOutputNeverMixAndAllSelectedModalitiesAreRequired() {
        assertEquals(listOf(open), filterModels(models, ModelListQuery(inputModalities = setOf("text", "image"))))
        assertEquals(listOf(closed), filterModels(models, ModelListQuery(outputModalities = setOf("image"))))
        assertEquals(listOf(open), filterModels(models, ModelListQuery(inputModalities = setOf("custom"))))
        assertTrue(filterModels(models, ModelListQuery(inputModalities = setOf("image", "audio"))).isEmpty())
    }

    @Test fun nullAndEmptyModalitiesDoNotSatisfyRequiredModality() {
        val data = listOf(unknown, unknown.copy(id = "empty", inputModalities = emptyList()))
        assertTrue(filterModels(data, ModelListQuery(inputModalities = setOf("text"))).isEmpty())
        assertEquals(data, filterModels(data, ModelListQuery()))
    }

    @Test fun tokenMinimumIsInclusiveAndMissingIsNotZero() {
        assertEquals(listOf(closed, open), filterModels(models, ModelListQuery(minContextTokens = 128_000)))
        assertEquals(listOf(closed), filterModels(models, ModelListQuery(minContextTokens = 128_001)))
        assertEquals(listOf(open), filterModels(models, ModelListQuery(minOutputTokens = 32_000)))
        assertTrue(filterModels(models, ModelListQuery(minOutputTokens = 32_001)).isEmpty())
        assertEquals(listOf(closed, open), filterModels(models, ModelListQuery(minContextTokens = 0)))
        val huge = open.copy(contextLimitTokens = Long.MAX_VALUE)
        assertEquals(listOf(huge), filterModels(listOf(huge, open), ModelListQuery(minContextTokens = Long.MAX_VALUE)))
    }

    @Test fun zeroQuoteIsAvailableAndMissingQuoteIsNotFree() {
        assertEquals(listOf(open), filterModels(models, ModelListQuery(hasPrice = true)))
        assertEquals(listOf(closed, unknown), filterModels(models, ModelListQuery(hasPrice = false)))
    }

    @Test fun differentFieldsIntersectWithoutReordering() {
        val query = ModelListQuery(
            search = "atlas", labs = setOf("alpha", "beta"), weights = WeightFilter.OPEN,
            inputModalities = setOf("image"), outputModalities = setOf("text"),
            minContextTokens = 128_000, minOutputTokens = 32_000, hasPrice = true,
        )
        assertEquals(listOf(open), filterModels(models, query))
        assertTrue(filterModels(models, query.copy(hasPrice = false)).isEmpty())
        assertEquals(listOf(closed, open), filterModels(models, ModelListQuery(search = "atlas")))
    }

    @Test fun resetFiltersKeepsSearchAndResetAllIsInactive() {
        val query = ModelListQuery(
            search = "atlas", labs = setOf("alpha", "beta"), weights = WeightFilter.UNKNOWN,
            inputModalities = setOf("text", "image"), outputModalities = setOf("text"),
            minContextTokens = 1, minOutputTokens = 1, hasPrice = false,
        )
        assertEquals(8, query.filterCount)
        assertEquals(ModelListQuery(search = "atlas"), query.resetFilters())
        assertEquals(0, query.resetFilters().filterCount)
        assertTrue(query.resetFilters().isActive)
        assertFalse(ModelListQuery().isActive)
    }

    @Test fun emptyCatalogAndNoResultsAreDifferentStates() {
        val query = ModelListQuery(search = "not-found")
        val noResults = ModelListUiState(models = models, isLoading = false, query = query)
        assertTrue(noResults.visibleModels.isEmpty())
        assertEquals(3, noResults.models.size)
        val empty = ModelListUiState(isLoading = false, query = query)
        assertTrue(empty.models.isEmpty())
    }
}
