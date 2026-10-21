package io.github.chenlongapps.modelatlas.ui.modellist

import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class LabFilterOrderTest {
    @Test fun firstEightLabsKeepFixedPriorityRegardlessOfModelCount() {
        val expected = listOf(
            "openai", "anthropic", "google", "deepseek", "alibaba", "meta", "xai", "moonshotai",
        )
        val counts = expected.withIndex().associate { (index, lab) -> lab to index }
        assertEquals(expected, sortFilterLabs(expected.reversed(), counts))
    }

    @Test fun otherLabsFollowModelCountIncludingPreviouslyUnknownLabs() {
        val labs = listOf("ai21", "mistral", "new-lab", "openai", "moonshotai", "missing-lab")
        val counts = mapOf("ai21" to 2, "mistral" to 5, "new-lab" to 100, "openai" to 1, "moonshotai" to 3)
        val expected = listOf("openai", "moonshotai", "new-lab", "mistral", "ai21", "missing-lab")
        assertEquals(expected, sortFilterLabs(labs, counts))
        assertEquals(expected, sortFilterLabs(labs.reversed(), counts))
    }

    @Test fun equalCountsUseAlphabeticalFallbackAndMissingCountsAreZero() {
        val labs = listOf("zzz", "bbb", "missing", "aaa")
        assertEquals(
            listOf("aaa", "bbb", "missing", "zzz"),
            sortFilterLabs(labs, mapOf("aaa" to 2, "bbb" to 2, "zzz" to 0)),
        )
    }

    @Test fun countsUseAllModelsWithValidLabPrefixes() {
        val models = listOf(
            ModelSummary("mistral/one", "One"),
            ModelSummary("mistral/two", "Two"),
            ModelSummary("new-lab/one", "Three"),
            ModelSummary("no-lab", "Four"),
        )
        assertEquals(mapOf("mistral" to 2, "new-lab" to 1), countModelsByLab(models))
    }

    @Test fun selectedSubsetUsesSamePriorityWithoutChangingOriginalSelection() {
        val selected = linkedSetOf("missing-lab", "meta", "anthropic", "openai")
        assertEquals(listOf("openai", "anthropic", "meta", "missing-lab"), sortFilterLabs(selected, emptyMap()))
        assertEquals(listOf("missing-lab", "meta", "anthropic", "openai"), selected.toList())
    }

    @Test fun emptyAndSingleLabArePreserved() {
        assertEquals(emptyList<String>(), sortFilterLabs(emptyList(), emptyMap()))
        assertEquals(listOf("unknown"), sortFilterLabs(setOf("unknown"), emptyMap()))
    }
}
