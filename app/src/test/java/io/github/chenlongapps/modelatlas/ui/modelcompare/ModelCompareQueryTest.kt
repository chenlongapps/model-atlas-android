package io.github.chenlongapps.modelatlas.ui.modelcompare

import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelCompareQueryTest {
    private val models = listOf(
        ModelSummary("lab/a", "Alpha", releaseDate = "2026-10"),
        ModelSummary("lab/b", "Beta", releaseDate = "2026-09"),
        ModelSummary("other/c", "Alpha Preview", releaseDate = "2026-08"),
        ModelSummary("other/d", "Delta"),
    )

    @Test fun favoritesComeFirstWithBothGroupsKeepingCatalogOrderNotFavoriteInsertionOrder() {
        assertEquals(listOf("lab/b", "other/d", "lab/a", "other/c"), ids(setOf("other/d", "lab/b")))
        assertEquals(listOf("lab/a", "lab/b", "other/c", "other/d"), models.map(ModelSummary::id))
    }

    @Test fun searchKeepsFavoritePriorityWithoutShowingUnmatchedFavorites() {
        val favorites = setOf("other/c", "lab/b")
        assertEquals(listOf("other/c", "lab/a"), ids(favorites, " ALPHA "))
        assertEquals(listOf("lab/b", "lab/a"), ids(favorites, "lab"))
        assertEquals(listOf("other/c"), ids(favorites, "other preview"))
        assertEquals(emptyList<String>(), ids(favorites, "missing"))
    }

    @Test fun noFavoritesAllFavoritesAndUnavailableFavoritesKeepExistingOrder() {
        val expected = models.map(ModelSummary::id)
        assertEquals(expected, ids(emptySet()))
        assertEquals(expected, ids(expected.reversed().toSet()))
        assertEquals(expected, ids(setOf("removed/model")))
    }

    @Test fun emptyCatalogDoesNotUseUnavailableFavoritesAsCandidates() {
        assertEquals(emptyList<ModelSummary>(), filterCompareModels(emptyList(), setOf("removed/model"), ""))
    }

    private fun ids(favorites: Set<String>, search: String = "") = filterCompareModels(models, favorites, search).map(ModelSummary::id)
}
