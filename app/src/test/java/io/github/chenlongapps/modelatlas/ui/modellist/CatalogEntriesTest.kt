package io.github.chenlongapps.modelatlas.ui.modellist

import io.github.chenlongapps.modelatlas.data.model.FavoriteIdentity
import io.github.chenlongapps.modelatlas.data.model.FavoriteModel
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogEntriesTest {
    private val a = ModelSummary("lab/a", "Alpha", releaseDate = "2025")
    private val b = ModelSummary("lab/b", "Beta", releaseDate = "2024")
    private val c = ModelSummary("lab/c", "Gamma", releaseDate = "2023")
    private fun favorite(model: ModelSummary) = FavoriteModel(FavoriteIdentity(model.id, model.name, model.lab), 1, model)
    private val missing = FavoriteModel(FavoriteIdentity("old/gone", "Historical", "old"), 2, null)

    @Test fun favoritesStableFirstAndUnavailableRemainsAccessible() {
        assertEquals(listOf("lab/b", "lab/c", "old/gone", "lab/a"),
            catalogEntries(listOf(a, b, c), listOf(favorite(c), missing, favorite(b)), ModelListQuery()).map { it.id })
    }

    @Test fun searchAndLabCanMatchHistoricalIdentityButUnknownPropertiesNeverMatch() {
        assertEquals(listOf("old/gone"), catalogEntries(listOf(a), listOf(missing),
            ModelListQuery(search = "historical old", labs = setOf("old"))).map { it.id })
        assertEquals(emptyList<String>(), catalogEntries(emptyList(), listOf(missing),
            ModelListQuery(weights = WeightFilter.UNKNOWN)).map { it.id })
        assertEquals(emptyList<String>(), catalogEntries(emptyList(), listOf(missing),
            ModelListQuery(inputModalities = setOf("text"))).map { it.id })
    }

    @Test fun freshCatalogWinsOverStaleUnavailableProjectionWithoutDuplicating() {
        val stale = FavoriteModel(FavoriteIdentity(a.id, "Old name", "lab"), 1, null)
        assertEquals(listOf(CatalogEntry(a.id, a, stale)), catalogEntries(listOf(a), listOf(stale), ModelListQuery()))
    }

    @Test fun availableFavoriteNotInCatalogIsNotInventedFromStaleProjection() {
        assertEquals(emptyList<CatalogEntry>(), catalogEntries(emptyList(), listOf(favorite(a)), ModelListQuery()))
    }
}
