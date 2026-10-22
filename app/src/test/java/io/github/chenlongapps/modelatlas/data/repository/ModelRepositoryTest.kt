package io.github.chenlongapps.modelatlas.data.repository

import io.github.chenlongapps.modelatlas.data.local.CatalogSnapshot
import io.github.chenlongapps.modelatlas.data.local.CatalogStore
import io.github.chenlongapps.modelatlas.data.local.ModelListRow
import io.github.chenlongapps.modelatlas.data.local.ModelOfferingPriceRow
import io.github.chenlongapps.modelatlas.data.local.ModelDetailRecord
import io.github.chenlongapps.modelatlas.data.local.entity.ModelEntity
import io.github.chenlongapps.modelatlas.data.local.model.OfferingPricingLocal
import io.github.chenlongapps.modelatlas.data.mapper.CatalogMapper
import io.github.chenlongapps.modelatlas.data.remote.ModelsDevApi
import io.github.chenlongapps.modelatlas.data.remote.dto.CostDto
import io.github.chenlongapps.modelatlas.data.remote.dto.ModelDto
import io.github.chenlongapps.modelatlas.data.remote.dto.ModelLimitDto
import io.github.chenlongapps.modelatlas.data.remote.dto.ModelOfferingDto
import io.github.chenlongapps.modelatlas.data.remote.dto.ProviderDto
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ModelRepositoryTest {
    private val api = FakeApi()
    private val store = FakeStore()
    private val clock = Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC)

    @Test fun observesLocalDetailByStableIdWithoutNetworkOrWrites() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        store.detail.value = ModelDetailRecord(ModelEntity("lab/saved", "remote", "Saved", 100, type = "llm"), emptyList())
        assertEquals("Saved", repository.observeModelDetail("lab/saved").first()!!.name)
        assertEquals("lab/saved", store.detailId)
        store.detail.value = null
        assertNull(repository.observeModelDetail("lab/saved").first())
        assertEquals(0, api.providerRequests)
        assertEquals(0, store.writes)
    }

    @Test fun refreshMapsBothResponsesAndWritesOneSnapshot() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        repository.refresh()
        val snapshot = store.snapshot!!
        assertEquals(1, store.writes)
        assertEquals("model-key", snapshot.models.single().id)
        assertEquals("provider-key", snapshot.providers.single().id)
        assertEquals("provider-key", snapshot.offerings.single().providerId)
        assertEquals(clock.millis(), snapshot.models.single().fetchedAtEpochMillis)
        assertEquals(clock.millis(), snapshot.providers.single().fetchedAtEpochMillis)
        assertNull(snapshot.offerings.single().canonicalModelId)
    }

    @Test fun endpointFailureDoesNotWritePartialOrEmptySnapshot() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        repository.refresh()
        val previous = store.snapshot
        api.providerFailure = IOException("offline")
        try {
            repository.refresh()
            error("Expected request failure")
        } catch (_: IOException) {
            assertEquals(1, store.writes)
            assertEquals(previous, store.snapshot)
        }
    }

    @Test fun cancellationIsNotSwallowedOrWrittenAsEmptyData() = runTest {
        api.providerFailure = CancellationException("cancelled")
        val repository = repository(StandardTestDispatcher(testScheduler))
        try {
            repository.refresh()
            error("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(0, store.writes)
        }
    }

    @Test fun observesPersistedRowsNotRemoteDtoOrHardcodedData() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        store.rows.value = listOf(
            ModelListRow("saved", "Saved model", null, "llm", null, null, null, null),
            ModelListRow("embedding", "Embedding", null, "embedding", null, null, null, null),
        )
        val models = repository.observeModels().first()
        assertEquals(listOf("saved"), models.map { it.id })
        assertNull(models.single().lab)
        assertNull(models.single().releaseDate)
        assertNull(models.single().contextLimitTokens)
        assertNull(models.single().price)
        assertEquals(0, api.providerRequests)
    }

    @Test fun combinesCanonicalRowsWithSeparatePriceProjection() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        store.rows.value = listOf(
            ModelListRow("openai/gpt-x", "GPT X", null, "llm", null, null, "openai", "OpenAI"),
            ModelListRow("lab/unpriced", "Unpriced", null, "llm", null, null, "openrouter", "OpenRouter"),
        )
        store.priceRows.value = listOf(
            ModelOfferingPriceRow(
                "openai/gpt-x", "openrouter", "OpenRouter", "gpt-x",
                OfferingPricingLocal(input = 0.5, output = 1.5),
            ),
            ModelOfferingPriceRow(
                "openai/gpt-x", "openai", "OpenAI", "gpt-x",
                OfferingPricingLocal(input = 2.0, output = 10.0),
            ),
        )
        val byId = repository.observeModels().first().associateBy { it.id }
        assertEquals("openai", byId.getValue("openai/gpt-x").price?.providerId)
        assertTrue(byId.getValue("openai/gpt-x").price?.isOfficial == true)
        assertNull(byId.getValue("lab/unpriced").price)
    }

    @Test fun honorsRetryAfterSecondsAndDoesNotRequestAgainDuringCooldown() = runTest {
        api.providerFailure = httpError(429, "120")
        val repository = repository(StandardTestDispatcher(testScheduler))
        val first = tryRefresh(repository)
        assertEquals(clock.millis() + 120_000, first.retryAtEpochMillis)
        api.providerFailure = null
        val second = tryRefresh(repository)
        assertEquals(first.retryAtEpochMillis, second.retryAtEpochMillis)
        assertEquals(1, api.providerRequests)
        assertEquals(0, store.writes)
    }

    @Test fun honorsRetryAfterHttpDateAndUsesBoundedDefaultForInvalidHeader() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val retryAt = clock.instant().plusSeconds(300)
        api.providerFailure = httpError(503, DateTimeFormatter.RFC_1123_DATE_TIME.format(retryAt.atZone(ZoneOffset.UTC)))
        assertEquals(retryAt.toEpochMilli(), tryRefresh(repository(dispatcher)).retryAtEpochMillis)
        api.providerFailure = httpError(429, "invalid")
        assertEquals(clock.millis() + 60_000, tryRefresh(repository(dispatcher)).retryAtEpochMillis)
    }

    @Test fun successfulEmptyResponsesAreNotErrors() = runTest {
        api.models = emptyMap()
        api.providers = emptyMap()
        assertEquals(false, repository(StandardTestDispatcher(testScheduler)).refresh())
        assertEquals(0, store.writes)
    }

    @Test fun modelAdditionStagesFullSnapshotAndApplyDoesNotDownloadAgain() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        assertEquals(false, repository.hasLocalCatalog())
        repository.refresh()
        val previous = store.snapshot
        api.models = api.models.mapValues { (_, model) -> model.copy(name = "Updated") }
        api.models = api.models + ("new-key" to ModelDto("new", "New"))
        assertEquals(CatalogUpdateCheckResult.UPDATE_AVAILABLE, repository.checkForUpdates())
        assertEquals(previous, store.snapshot)
        assertEquals(1, store.writes)
        assertTrue(repository.applyPendingUpdate())
        assertEquals(setOf("model-key", "new-key"), store.snapshot!!.models.map { it.id }.toSet())
        assertEquals("Updated", store.snapshot!!.models.first { it.id == "model-key" }.name)
        assertEquals(2, api.providerRequests)
        assertEquals(2, store.writes)
        assertEquals(false, repository.applyPendingUpdate())
        assertTrue(repository.hasLocalCatalog())
    }

    @Test fun sameContentWithDifferentFetchTimesDoesNotSuggestUpdateOrWrite() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        repository.refresh()
        store.snapshot = store.snapshot!!.let { snapshot -> snapshot.copy(
            models = snapshot.models.map { it.copy(fetchedAtEpochMillis = 1) },
            providers = snapshot.providers.map { it.copy(fetchedAtEpochMillis = 2) },
            offerings = snapshot.offerings.map { it.copy(fetchedAtEpochMillis = 2) },
        ) }
        assertEquals(CatalogUpdateCheckResult.UNCHANGED, repository.checkForUpdates())
        assertEquals(false, repository.refresh())
        assertEquals(1, store.writes)
    }

    @Test fun modelDeletionAndEqualCountReplacementRequireConfirmation() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        repository.refresh()
        val originalModels = api.models
        val previous = store.snapshot
        listOf(emptyMap(), mapOf("replacement" to ModelDto("replacement", "Replacement"))).forEach { models ->
            api.models = models
            assertEquals(CatalogUpdateCheckResult.UPDATE_AVAILABLE, repository.checkForUpdates())
            assertEquals(previous, store.snapshot)
            assertEquals(1, store.writes)
        }
        api.models = originalModels
        assertEquals(CatalogUpdateCheckResult.UNCHANGED, repository.checkForUpdates())
        assertEquals(false, repository.applyPendingUpdate())
        assertEquals(1, store.writes)
    }

    @Test fun metadataChangesAreCommittedSilentlyWithoutPendingUpdate() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        repository.refresh()
        api.models = api.models.mapValues { (_, model) -> model.copy(
            name = "Updated", type = "embedding", limit = ModelLimitDto(context = 200_000),
            openWeights = true, license = "MIT", lastUpdated = "2026-10-21",
        ) }
        assertEquals(CatalogUpdateCheckResult.UPDATED_SILENTLY, repository.checkForUpdates())
        assertEquals(CatalogMapper().map(api.models, api.providers, clock.millis(), clock.millis()), store.snapshot)
        assertEquals(2, store.writes)
        assertEquals(false, repository.applyPendingUpdate())
        assertEquals(CatalogUpdateCheckResult.UNCHANGED, repository.checkForUpdates())
        assertEquals(2, store.writes)
    }

    @Test fun providerAndOfferingChangesIncludingPricesAreCommittedSilently() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        repository.refresh()
        val provider = api.providers.getValue("provider-key")
        val offering = provider.models.getValue("offering-key")
        val changes = listOf(
            api.providers.mapValues { (_, value) -> value.copy(name = "Renamed") },
            api.providers + ("new-provider" to provider),
            mapOf("provider-key" to provider.copy(models = provider.models + ("new-offering" to offering))),
            mapOf("provider-key" to provider.copy(models = mapOf("offering-key" to offering.copy(cost = CostDto(input = 0.0, output = 2.0))))),
            mapOf("provider-key" to provider.copy(models = emptyMap())),
            emptyMap(),
        )
        changes.forEachIndexed { index, providers ->
            api.providers = providers
            assertEquals(CatalogUpdateCheckResult.UPDATED_SILENTLY, repository.checkForUpdates())
            assertEquals(CatalogMapper().map(api.models, api.providers, clock.millis(), clock.millis()), store.snapshot)
            assertEquals(index + 2, store.writes)
            assertEquals(false, repository.applyPendingUpdate())
        }
    }

    @Test fun contentOnlyCheckClearsStalePendingModelAddition() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        repository.refresh()
        val originalModels = api.models
        api.models = api.models + ("new-key" to ModelDto("new", "New"))
        assertEquals(CatalogUpdateCheckResult.UPDATE_AVAILABLE, repository.checkForUpdates())
        api.models = originalModels.mapValues { (_, model) -> model.copy(name = "Updated") }
        assertEquals(CatalogUpdateCheckResult.UPDATED_SILENTLY, repository.checkForUpdates())
        assertEquals(false, repository.applyPendingUpdate())
        assertEquals("Updated", store.snapshot!!.models.single().name)
        assertEquals(2, store.writes)
    }

    @Test fun silentWriteFailureAndCancellationPreserveCatalogAndCanBeRetried() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        repository.refresh()
        val previous = store.snapshot
        api.models = api.models.mapValues { (_, model) -> model.copy(name = "Updated") }
        listOf(IllegalStateException("disk"), CancellationException("cancelled")).forEach { failure ->
            store.writeFailure = failure
            try {
                repository.checkForUpdates()
                error("Expected write failure")
            } catch (error: Exception) {
                assertEquals(failure.javaClass, error.javaClass)
                assertEquals(failure.message, error.message)
                assertEquals(previous, store.snapshot)
                assertEquals(1, store.writes)
            }
        }
        store.writeFailure = null
        assertEquals(CatalogUpdateCheckResult.UPDATED_SILENTLY, repository.checkForUpdates())
        assertEquals("Updated", store.snapshot!!.models.single().name)
        assertEquals(2, store.writes)
    }

    @Test fun manualRefreshStillCommitsContentOnlyChanges() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        repository.refresh()
        api.models = api.models.mapValues { (_, model) -> model.copy(name = "Updated") }
        assertTrue(repository.refresh())
        assertEquals("Updated", store.snapshot!!.models.single().name)
        assertEquals(2, store.writes)
    }

    @Test fun manualRefreshSupersedesStagedUpdateAndCanClearCatalog() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        repository.refresh()
        api.models = api.models + ("staged-key" to ModelDto("staged", "Staged"))
        repository.checkForUpdates()
        api.models = emptyMap()
        api.providers = emptyMap()
        assertTrue(repository.refresh())
        assertTrue(store.snapshot!!.isEmpty)
        assertEquals(false, repository.applyPendingUpdate())
    }

    @Test fun failedCheckPreservesDatabaseAndPendingSnapshotCanBeRetriedAfterWriteFailure() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        repository.refresh()
        val previous = store.snapshot
        api.models = mapOf("new-key" to ModelDto("new", "New"))
        repository.checkForUpdates()
        api.providerFailure = IOException("offline")
        try {
            repository.checkForUpdates()
            error("Expected network failure")
        } catch (_: IOException) {
            assertEquals(previous, store.snapshot)
        }
        store.writeFailure = IllegalStateException("disk")
        try {
            repository.applyPendingUpdate()
            error("Expected write failure")
        } catch (_: IllegalStateException) {
            assertEquals(previous, store.snapshot)
        }
        store.writeFailure = null
        assertTrue(repository.applyPendingUpdate())
        assertEquals("New", store.snapshot!!.models.single().name)
    }

    private fun repository(dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        DefaultModelRepository(api, store, CatalogMapper(), clock, dispatcher)

    private suspend fun tryRefresh(repository: ModelRepository): CatalogRateLimitException {
        try {
            repository.refresh()
        } catch (error: CatalogRateLimitException) {
            return error
        }
        error("Expected throttling")
    }

    private fun httpError(code: Int, retryAfter: String): HttpException {
        val raw = okhttp3.Response.Builder()
            .request(Request.Builder().url("https://models.dev/api.json?type=all").build())
            .protocol(Protocol.HTTP_1_1).code(code).message("Throttled")
            .header("Retry-After", retryAfter).build()
        return HttpException(Response.error<Any>("".toResponseBody(), raw))
    }

    private class FakeApi : ModelsDevApi {
        var models = mapOf("model-key" to ModelDto("remote-model", "Model"))
        var providers = mapOf("provider-key" to ProviderDto("remote-provider", "API", mapOf(
            "offering-key" to ModelOfferingDto("remote-offering", "Model"),
        )))
        var providerFailure: Exception? = null
        var providerRequests = 0
        override suspend fun getModels() = models
        override suspend fun getProviders(): Map<String, ProviderDto> {
            providerRequests++
            providerFailure?.let { throw it }
            return providers
        }
    }

    private class FakeStore : CatalogStore {
        val rows = MutableStateFlow<List<ModelListRow>>(emptyList())
        val priceRows = MutableStateFlow<List<ModelOfferingPriceRow>>(emptyList())
        val detail = MutableStateFlow<ModelDetailRecord?>(null)
        var detailId: String? = null
        var snapshot: CatalogSnapshot? = null
        var writes = 0
        var writeFailure: Exception? = null
        override suspend fun hasCatalog() = snapshot?.isEmpty == false
        override suspend fun readCatalog() = snapshot ?: CatalogSnapshot(emptyList(), emptyList(), emptyList())
        override fun observeModelRows() = rows
        override fun observeOfferingPriceRows() = priceRows
        override fun observeModelDetail(id: String) = detail.also { detailId = id }
        override suspend fun replaceCatalog(snapshot: CatalogSnapshot) {
            writeFailure?.let { throw it }
            writes++
            this.snapshot = snapshot
        }
    }
}
