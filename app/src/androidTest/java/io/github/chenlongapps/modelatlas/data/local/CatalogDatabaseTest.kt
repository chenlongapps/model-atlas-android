package io.github.chenlongapps.modelatlas.data.local

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.chenlongapps.modelatlas.data.local.entity.ModelEntity
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkScoreEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ModelOfferingEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ProviderEntity
import io.github.chenlongapps.modelatlas.data.local.model.OfferingPricingLocal
import io.github.chenlongapps.modelatlas.data.mapper.toModelSummaries
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database = Room.inMemoryDatabaseBuilder<CatalogDatabase>(context)
        .setDriver(AndroidSQLiteDriver()).build()
    private val dao = database.catalogDao()

    @After fun close() = database.close()

    @Test fun benchmarkSnapshotsAreAtomicVersionedAndDoNotOverwriteExistingOnInitialization() = runBlocking {
        val scores = database.benchmarkScoreDao()
        val existing = BenchmarkScoreEntity("existing", "model", "bench", 1.0)
        scores.upsert(existing)
        scores.initialize(1, listOf(BenchmarkScoreEntity("sample", "model", "bench", 2.0)))
        assertEquals(0L, scores.version())
        assertEquals(listOf(existing), scores.observeForModel("model").first())
        assertTrue(scores.replaceIfNewer(2, emptyList()))
        assertEquals(2L, scores.version())
        assertTrue(scores.observeForModel("model").first().isEmpty())
        scores.initialize(1, listOf(existing))
        assertEquals(false, scores.replaceIfNewer(1, listOf(existing)))
        assertTrue(scores.observeForModel("model").first().isEmpty())
        try {
            scores.replaceIfNewer(3, listOf(existing, existing))
            throw AssertionError("duplicate key must fail")
        } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(2L, scores.version())
        assertTrue(scores.observeForModel("model").first().isEmpty())
    }

    @Test fun independentBenchmarkScoresSurviveCatalogReplacementAndPreserveConfigurations() = runBlocking {
        val scores = database.benchmarkScoreDao()
        val first = BenchmarkScoreEntity(
            id = "score-1", modelId = "model", benchmarkId = "bench-v1", score = 0.003,
            metric = "Accuracy", unit = "%", higherIsBetter = true, subset = "test",
            harness = "agent", reasoningEffort = "high", toolsEnabled = false,
            sourceUrl = "https://example.org", sourceType = "third-party", evaluatedAt = "2026-10", cost = 0.003,
        )
        val second = BenchmarkScoreEntity("score-2", "model", "bench-v1", 0.0)
        scores.upsert(first)
        scores.upsert(second)
        assertEquals(listOf(first, second), scores.observeForModel("model").first())
        assertEquals(listOf(first, second), scores.observeForBenchmark("bench-v1").first())
        assertTrue(scores.observeForModel("other").first().isEmpty())
        dao.replaceCatalog(snapshot())
        dao.replaceCatalog(CatalogSnapshot(emptyList(), emptyList(), emptyList()))
        assertEquals(listOf(first, second), scores.observeForModel("model").first())
        scores.upsert(first.copy(score = -1.25, higherIsBetter = null))
        assertEquals(-1.25, scores.observeForModel("model").first().first().score, 0.0)
        scores.delete("score-1")
        assertEquals(listOf(second), scores.observeForModel("model").first())
    }

    @Test fun detailReadsExplicitServicesIncludingUnpricedOnesAndTracksRemoval() = runBlocking {
        val initial = snapshot()
        dao.replaceCatalog(initial.copy(offerings = initial.offerings.map { it.copy(pricing = null) }))
        val detail = dao.observeModelDetail("model").first()!!
        assertEquals("model", detail.model.id)
        assertEquals(3, detail.offerings.size)
        assertEquals(setOf("p1", "p2"), detail.offerings.map { it.provider.id }.toSet())
        assertTrue(detail.offerings.all { it.offering.canonicalModelId == "model" && it.offering.pricing == null })
        assertTrue(dao.observeModelDetail("no-provider").first()!!.offerings.isEmpty())
        assertNull(dao.observeModelDetail("missing").first())
        dao.replaceCatalog(CatalogSnapshot(emptyList(), emptyList(), emptyList()))
        assertNull(dao.observeModelDetail("model").first())
    }

    @Test fun readsCompleteSnapshotIncludingUnlinkedOfferings() = runBlocking {
        assertTrue(dao.readCatalog().isEmpty)
        assertEquals(false, dao.hasCatalog())
        val initial = snapshot()
        dao.replaceCatalog(initial)
        val persisted = dao.readCatalog()
        assertTrue(dao.hasCatalog())
        assertTrue(initial.hasSameContentAs(persisted))
        assertEquals(initial.models.associateBy { it.id }, persisted.models.associateBy { it.id })
        assertEquals(initial.providers.associateBy { it.id }, persisted.providers.associateBy { it.id })
        assertEquals(
            initial.offerings.associateBy { it.providerId to it.id },
            persisted.offerings.associateBy { it.providerId to it.id },
        )
    }

    @Test fun joinsOnlyExplicitAssociationsAndAllowsDuplicateOfferingIdsAcrossProviders() = runBlocking {
        dao.replaceCatalog(snapshot())
        val rows = dao.observeModelRows().first()
        val models = rows.toModelSummaries()
        assertEquals(listOf("model", "no-provider"), models.map { it.id })
        assertEquals(listOf("p1", "p2"), rows.filter { it.id == "model" }.map { it.providerId })
        assertNull(rows.single { it.id == "no-provider" }.providerId)
    }

    @Test fun projectsCanonicalMetadataInsteadOfServiceOverrides() = runBlocking {
        val initial = snapshot()
        dao.replaceCatalog(initial.copy(
            models = initial.models.map { it.copy(
                releaseDate = "2026-10",
                contextLimitTokens = 5_000_000_000L,
                outputLimitTokens = 16_384L,
                inputModalities = listOf("text", "image", "future-modality"),
                outputModalities = listOf("text", "audio"),
                openWeights = true,
                license = "MIT",
            ) },
            offerings = initial.offerings.map { it.copy(
                contextLimitTokens = 1_024L,
                outputLimitTokens = 512L,
                inputModalities = listOf("text"),
                outputModalities = listOf("text"),
                openWeights = false,
            ) },
        ))
        val models = dao.observeModelRows().first().toModelSummaries()
        assertEquals(2, models.size)
        models.forEach { model ->
            assertEquals("2026-10", model.releaseDate)
            assertEquals(5_000_000_000L, model.contextLimitTokens)
            assertEquals(16_384L, model.outputLimitTokens)
            assertEquals(listOf("text", "image", "future-modality"), model.inputModalities)
            assertEquals(listOf("text", "audio"), model.outputModalities)
            assertEquals(true, model.openWeights)
            assertEquals("MIT", model.license)
        }
    }

    @Test fun projectsPricesOnlyForExplicitlyLinkedAndPricedOfferings() = runBlocking {
        dao.replaceCatalog(snapshot())
        val rows = dao.observeOfferingPriceRows().first()
        // 未关联（canonicalModelId 为 null）的服务不生成报价行。
        assertTrue(rows.none { it.offeringId == "no-provider" })
        // 关联到不存在规范模型的报价行仍会返回；由映射层按规范模型 ID 分组时自然忽略。
        assertEquals(listOf("model", "not-in-models"), rows.map { it.canonicalModelId }.distinct())
        assertEquals(listOf("p1", "p1", "p2"), rows.filter { it.canonicalModelId == "model" }.map { it.providerId })
        assertEquals(
            listOf("duplicate-link", "shared", "shared"),
            rows.filter { it.canonicalModelId == "model" }.map { it.offeringId },
        )
    }

    @Test fun unmatchedPriceRowsDoNotBecomeModels() = runBlocking {
        dao.replaceCatalog(snapshot())
        val priceRows = dao.observeOfferingPriceRows().first().groupBy { it.canonicalModelId }
        val models = dao.observeModelRows().first().toModelSummaries(priceRows)
        assertEquals(listOf("model", "no-provider"), models.map { it.id })
        assertNotNull(models.single { it.id == "model" }.price)
        assertNull(models.single { it.id == "no-provider" }.price)
    }

    @Test fun priceProjectionSkipsOfferingsWithoutPricing() = runBlocking {
        val initial = snapshot()
        dao.replaceCatalog(initial.copy(
            offerings = initial.offerings.map { it.copy(pricing = null) },
        ))
        assertTrue(dao.observeOfferingPriceRows().first().isEmpty())
    }

    @Test fun invalidSnapshotRollsBackAllThreeTables() = runBlocking {
        val initial = snapshot()
        dao.replaceCatalog(initial)
        val before = dao.observeModelRows().first()
        val invalid = initial.copy(
            models = listOf(model("new", "New model")),
            providers = listOf(provider("new-provider")),
            offerings = listOf(offering("missing-provider", "new-service", "new")),
        )
        var failed = false
        try {
            dao.replaceCatalog(invalid)
        } catch (_: Exception) {
            failed = true
        }
        assertTrue("Foreign-key violation must fail", failed)
        assertEquals(before, dao.observeModelRows().first())
    }

    @Test fun replacesSnapshotIncludingRemovedModelsProvidersAndServices() = runBlocking {
        val initial = snapshot()
        dao.replaceCatalog(initial)
        dao.replaceCatalog(initial.copy(
            models = listOf(model("model", "Updated")),
            providers = listOf(provider("p1").copy(name = "Renamed provider")),
            offerings = listOf(offering("p1", "new-service", "model")),
        ))
        val rows = dao.observeModelRows().first()
        assertEquals(1, rows.size)
        assertEquals("Updated", rows.single().name)
        assertEquals("Renamed provider", rows.single().providerName)
        dao.replaceCatalog(CatalogSnapshot(emptyList(), emptyList(), emptyList()))
        assertTrue(dao.observeModelRows().first().isEmpty())
    }

    @Test fun fileDatabaseRetainsDataAfterReopening() = runBlocking {
        val name = "catalog-test-${System.nanoTime()}.db"
        try {
            val first = Room.databaseBuilder<CatalogDatabase>(context, name).setDriver(AndroidSQLiteDriver()).build()
            try {
                first.catalogDao().replaceCatalog(snapshot())
            } finally {
                first.close()
            }
            val reopened = Room.databaseBuilder<CatalogDatabase>(context, name).setDriver(AndroidSQLiteDriver()).build()
            try {
                assertEquals(2, reopened.catalogDao().observeModelRows().first().toModelSummaries().size)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private fun snapshot() = CatalogSnapshot(
        models = listOf(model("model", "A model"), model("no-provider", "B model")),
        providers = listOf(provider("p1"), provider("p2")),
        offerings = listOf(
            offering("p1", "shared", "model"), offering("p2", "shared", "model"),
            offering("p1", "duplicate-link", "model"),
            offering("p1", "no-provider", null), // ID 相同也不猜测关联。
            offering("p2", "unmatched", "not-in-models"),
        ),
    )

    private fun model(id: String, name: String) = ModelEntity(
        id, "remote-$id", name, 123, inputModalities = listOf("text", "image"), outputModalities = listOf("text"),
    )
    private fun provider(id: String) = ProviderEntity(id, "remote-$id", id, 456)
    private fun offering(provider: String, id: String, canonicalId: String?) = ModelOfferingEntity(
        provider, id, "remote-$id", id, 456, canonicalModelId = canonicalId,
        pricing = OfferingPricingLocal(input = 0.1, output = 0.2),
    )
}
