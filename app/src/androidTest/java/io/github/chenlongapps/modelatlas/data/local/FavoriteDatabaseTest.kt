package io.github.chenlongapps.modelatlas.data.local

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.chenlongapps.modelatlas.data.local.entity.ModelEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ModelOfferingEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ProviderEntity
import io.github.chenlongapps.modelatlas.data.local.model.OfferingPricingLocal
import io.github.chenlongapps.modelatlas.data.repository.RoomFavoriteRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database = Room.inMemoryDatabaseBuilder<CatalogDatabase>(context).setDriver(AndroidSQLiteDriver()).build()
    private val catalog = database.catalogDao()
    private val favorites = database.favoriteDao()
    private val repository = repository(database)
    @After fun close() = database.close()

    @Test fun orderingDuplicateAddsAndReAddUseFavoriteTimeRatherThanCatalogDate() = runBlocking {
        catalog.replaceCatalog(snapshot())
        favorites.add("lab/a", 10)
        favorites.add("lab/b", 20)
        favorites.add("lab/a", 30)
        assertEquals(listOf("lab/b", "lab/a"), repository.observeFavorites().first().map { it.identity.id })
        assertEquals(10L, repository.observeFavorites().first().last().favoritedAtEpochMillis)
        favorites.remove("lab/a")
        favorites.add("lab/a", 40)
        assertEquals(listOf("lab/a", "lab/b"), repository.observeFavorites().first().map { it.identity.id })
    }

    @Test fun replacingAndClearingCatalogPreservesFavoritesAndLastAvailableIdentity() = runBlocking {
        catalog.replaceCatalog(snapshot())
        repository.add("lab/a")
        catalog.replaceCatalog(snapshot().copy(models = listOf(model("lab/a", "Updated"))))
        assertEquals("Updated", repository.observeFavorite("lab/a").first()!!.name)
        catalog.replaceCatalog(snapshot().copy(models = listOf(model("lab/a", "Not LLM").copy(type = "image"))))
        val excluded = repository.observeFavorites().first().single()
        assertNull(excluded.model)
        assertEquals("Updated", excluded.identity.name)
        catalog.replaceCatalog(CatalogSnapshot(emptyList(), emptyList(), emptyList()))
        val missing = repository.observeFavorites().first().single()
        assertNull(missing.model)
        assertEquals("Updated", missing.identity.name)
        assertEquals("lab", missing.identity.lab)
        catalog.replaceCatalog(snapshot())
        assertNotNull(repository.observeFavorites().first().single().model)
        assertEquals("A", repository.observeFavorite("lab/a").first()!!.name)
        assertEquals(100L, repository.observeFavorites().first().single().favoritedAtEpochMillis)
    }

    @Test fun pricesReuseCatalogSelectionAndNeverPersistOldPriceWhenUnavailable() = runBlocking {
        catalog.replaceCatalog(snapshot().copy(
            providers = listOf(ProviderEntity("lab", "lab", "Lab", fetchedAtEpochMillis = 0)),
            offerings = listOf(ModelOfferingEntity("lab", "service", "service", "Service",
                canonicalModelId = "lab/a", fetchedAtEpochMillis = 0, pricing = OfferingPricingLocal(input = 0.0))),
        ))
        repository.add("lab/a")
        assertEquals(0.0, repository.observeFavorites().first().single().model!!.price!!.input!!, 0.0)
        catalog.replaceCatalog(snapshot())
        assertNull(repository.observeFavorites().first().single().model!!.price)
        catalog.replaceCatalog(CatalogSnapshot(emptyList(), emptyList(), emptyList()))
        assertNull(repository.observeFavorites().first().single().model)
        repository.remove("lab/a")
        assertTrue(repository.observeFavorites().first().isEmpty())
    }

    @Test fun invalidCatalogRollsBackIdentityAsWellAsDirectoryAndUnknownModelsCannotBeAdded() = runBlocking {
        catalog.replaceCatalog(snapshot())
        repository.add("lab/a")
        val bad = snapshot().copy(models = listOf(model("lab/a", "Should roll back")), offerings = listOf(
            ModelOfferingEntity("missing-provider", "service", "service", "Service", fetchedAtEpochMillis = 0),
        ))
        assertTrue(runCatching { catalog.replaceCatalog(bad) }.isFailure)
        assertEquals("A", repository.observeFavorite("lab/a").first()!!.name)
        assertTrue(runCatching { repository.add("missing") }.isFailure)
        assertEquals(1, repository.observeFavorites().first().size)
    }

    @Test fun concurrentRefreshAndRepeatedAddsKeepExactlyOneFavorite() = runBlocking {
        catalog.replaceCatalog(snapshot())
        (1..12).map { index -> async(Dispatchers.IO) {
            if (index % 3 == 0) catalog.replaceCatalog(snapshot()) else favorites.add("lab/a", index.toLong())
        } }.awaitAll()
        assertEquals(1, repository.observeFavorites().first().size)
        assertNotNull(repository.observeFavorites().first().single().model)
    }

    @Test fun renamedIdIsNotGuessedAndExcludedModelsCannotBeAdded() = runBlocking {
        catalog.replaceCatalog(snapshot())
        favorites.add("lab/b", 1)
        favorites.add("lab/a", 1)
        assertEquals(listOf("lab/a", "lab/b"), repository.observeFavorites().first().map { it.identity.id })
        catalog.replaceCatalog(snapshot().copy(models = listOf(model("lab/new-id", "A"), model("lab/b", "Excluded").copy(type = "image"))))
        assertTrue(repository.observeFavorites().first().all { it.model == null })
        assertEquals(setOf("lab/a", "lab/b"), repository.observeFavorites().first().map { it.identity.id }.toSet())
        assertTrue(runCatching { repository.add("lab/b") }.isFailure)
        assertEquals("B", repository.observeFavorite("lab/b").first()!!.name)
    }

    @Test fun fileDatabaseRetainsFavoritesIncludingUnavailableIdentityAfterReopening() = runBlocking {
        val name = "favorites-test-${System.nanoTime()}.db"
        try {
            val first = Room.databaseBuilder<CatalogDatabase>(context, name).setDriver(AndroidSQLiteDriver()).build()
            try {
                first.catalogDao().replaceCatalog(snapshot())
                first.favoriteDao().add("lab/a", 42)
                first.catalogDao().replaceCatalog(CatalogSnapshot(emptyList(), emptyList(), emptyList()))
            } finally { first.close() }
            val reopened = Room.databaseBuilder<CatalogDatabase>(context, name).setDriver(AndroidSQLiteDriver()).build()
            try {
                val favorite = repository(reopened).observeFavorites().first().single()
                assertEquals("A", favorite.identity.name)
                assertEquals(42L, favorite.favoritedAtEpochMillis)
                assertNull(favorite.model)
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }

    private fun repository(db: CatalogDatabase) = RoomFavoriteRepository(db.favoriteDao(),
        Clock.fixed(Instant.ofEpochMilli(100), ZoneOffset.UTC), Dispatchers.IO)
    private fun model(id: String, name: String) = ModelEntity(id, id, name, type = "llm", fetchedAtEpochMillis = 0)
    private fun snapshot() = CatalogSnapshot(listOf(model("lab/a", "A"), model("lab/b", "B")), emptyList(), emptyList())
}
