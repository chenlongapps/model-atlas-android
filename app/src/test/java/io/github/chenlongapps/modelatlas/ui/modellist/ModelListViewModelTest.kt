package io.github.chenlongapps.modelatlas.ui.modellist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.data.repository.CatalogRateLimitException
import io.github.chenlongapps.modelatlas.data.repository.ModelRepository
import io.github.chenlongapps.modelatlas.testing.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ModelListViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val store = ViewModelStore()
    private var nextKey = 0

    @After fun tearDown() = store.clear()

    @Test fun firstLoadPublishesCommittedSnapshotWithoutContinuingToObserve() = runTest {
        val repository = FakeRepository()
        val viewModel = create(repository)
        assertTrue(viewModel.uiState.value.isLoading)
        advanceUntilIdle()
        assertEquals(ModelListUiState(repository.remoteModels, isLoading = false), viewModel.uiState.value)
        assertFalse(viewModel.uiState is MutableStateFlow<*>)
        repository.models.value = emptyList()
        advanceUntilIdle()
        assertEquals(repository.remoteModels, viewModel.uiState.value.models)
        assertEquals(0, repository.activeObservers)
    }

    @Test fun firstLoadFailureWithoutCacheCanBeRetried() = runTest {
        val repository = FakeRepository().apply { failure = IOException("offline") }
        val viewModel = create(repository)
        advanceUntilIdle()
        assertEquals(ModelListError.NETWORK, viewModel.uiState.value.error)
        repository.failure = null
        viewModel.retry()
        assertTrue(viewModel.uiState.value.isLoading)
        advanceUntilIdle()
        assertEquals(ModelListUiState(repository.models.value, isLoading = false), viewModel.uiState.value)
        assertEquals(2, repository.refreshes)
    }

    @Test fun firstLoadKeepsLoadingUntilCommittedModelsAreRead() = runTest {
        val repository = FakeRepository().apply { observationGate = CompletableDeferred() }
        val viewModel = create(repository)
        val states = mutableListOf<ModelListUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { states += it } }
        advanceUntilIdle()
        assertTrue(repository.local)
        assertTrue(viewModel.uiState.value.isLoading)
        repository.observationGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(repository.remoteModels, viewModel.uiState.value.models)
        assertTrue(states.none { !it.isLoading && it.models.isEmpty() && it.error == null })
    }

    @Test fun firstLoadFinishesWhenCommittedCatalogReallyIsEmpty() = runTest {
        val repository = FakeRepository().apply { remoteModels = emptyList() }
        val viewModel = create(repository)
        advanceUntilIdle()
        assertEquals(ModelListUiState(isLoading = false), viewModel.uiState.value)
    }

    @Test fun firstLoadRetryWaitsForCommittedSnapshot() = runTest {
        val repository = FakeRepository().apply { failure = IOException("offline") }
        val viewModel = create(repository)
        advanceUntilIdle()
        repository.failure = null
        repository.observationGate = CompletableDeferred()
        viewModel.retry()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoading)
        repository.observationGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(ModelListUiState(repository.remoteModels, isLoading = false), viewModel.uiState.value)
    }

    @Test fun bundledImportIsReadBeforeFreezingTheInitialSnapshot() = runTest {
        val bundled = listOf(ModelSummary("lab/bundled", "Bundled"))
        val repository = FakeRepository().apply { bundle = bundled; gate = CompletableDeferred() }
        val viewModel = create(repository)
        advanceUntilIdle()
        assertEquals(ModelListUiState(bundled, isLoading = false), viewModel.uiState.value)
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(repository.remoteModels, repository.models.value)
        assertEquals(bundled, viewModel.uiState.value.models)
    }

    @Test fun repeatedRetryOrPullWhileFirstLoadIsRunningDoesNotDuplicateRequests() = runTest {
        val repository = FakeRepository().apply { gate = CompletableDeferred() }
        val viewModel = create(repository)
        advanceUntilIdle()
        repeat(5) { viewModel.retry(); viewModel.refresh() }
        advanceUntilIdle()
        assertEquals(1, repository.refreshes)
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test fun exposesParsingServerRateLimitAndStorageErrors() = runTest {
        val repository = FakeRepository()
        val failures = listOf(
            SerializationException("bad JSON") to ModelListError.INVALID_DATA,
            HttpException(Response.error<Any>(500, "server error".toResponseBody())) to ModelListError.SERVER,
            CatalogRateLimitException(100) to ModelListError.RATE_LIMITED,
            IllegalStateException("disk") to ModelListError.STORAGE,
        )
        repository.failure = failures.first().first
        val viewModel = create(repository)
        failures.forEachIndexed { index, (failure, expected) ->
            repository.failure = failure
            if (index > 0) viewModel.retry()
            advanceUntilIdle()
            assertEquals(expected, viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isLoading)
        }
    }

    @Test fun cachedReadFailureCanBeRetriedWithoutPublishingAnEmptyList() = runTest {
        val repository = cachedRepository().apply { observationFailure = IllegalStateException("read") }
        val viewModel = create(repository)
        advanceUntilIdle()
        assertEquals(ModelListError.STORAGE, viewModel.uiState.value.error)
        repository.observationFailure = null
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(ModelListUiState(repository.models.value, isLoading = false), viewModel.uiState.value)
    }

    @Test fun cachedModelsAreVisibleWhileBackgroundRefreshIsRunning() = runTest {
        val repository = cachedRepository().apply { gate = CompletableDeferred() }
        val viewModel = create(repository)
        advanceUntilIdle()
        assertEquals(ModelListUiState(repository.models.value, isLoading = false), viewModel.uiState.value)
        assertEquals(1, repository.refreshes)
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
    }

    @Test fun backgroundAdditionDeletionReplacementAndMetadataChangesOnlyUpdateCache() = runTest {
        val cached = listOf(ModelSummary("lab/cached", "Cached"))
        for (remote in listOf(
            emptyList(), cached + ModelSummary("lab/new", "New"),
            listOf(ModelSummary("lab/replacement", "Replacement")), cached.map { it.copy(name = "Updated", contextLimitTokens = 200_000) },
        )) {
            val repository = cachedRepository().apply { models.value = cached; remoteModels = remote }
            val viewModel = create(repository)
            advanceUntilIdle()
            assertEquals(remote, repository.models.value)
            assertEquals(ModelListUiState(cached, isLoading = false), viewModel.uiState.value)
            assertEquals(0, repository.activeObservers)
        }
    }

    @Test fun nextListSessionStartsWithTheUpdatedCache() = runTest {
        val repository = cachedRepository().apply { remoteModels = listOf(ModelSummary("lab/new", "New")) }
        val first = create(repository)
        advanceUntilIdle()
        assertFalse(first.uiState.value.models == repository.remoteModels)
        val next = create(repository)
        advanceUntilIdle()
        assertEquals(repository.remoteModels, next.uiState.value.models)
    }

    @Test fun backgroundFailureIsSilentAndExplicitRefreshFailureKeepsDisplaySnapshot() = runTest {
        val repository = cachedRepository().apply { failure = IOException("offline") }
        val viewModel = create(repository)
        advanceUntilIdle()
        assertEquals(ModelListUiState(repository.models.value, isLoading = false), viewModel.uiState.value)
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(repository.models.value, viewModel.uiState.value.models)
        assertNull(viewModel.uiState.value.error)
        assertEquals(ModelListError.NETWORK, viewModel.uiState.value.refreshError)
        assertFalse(viewModel.uiState.value.isRefreshing)
        viewModel.clearMessage()
        assertNull(viewModel.uiState.value.refreshError)
    }

    @Test fun backgroundWriteFailurePreservesDisplayWithoutNotice() = runTest {
        val repository = cachedRepository().apply { writeFailure = IllegalStateException("disk") }
        val viewModel = create(repository)
        advanceUntilIdle()
        assertEquals(ModelListUiState(repository.models.value, isLoading = false), viewModel.uiState.value)
    }

    @Test fun queryRemainsBoundToDisplaySnapshotUntilExplicitRefresh() = runTest {
        val repository = cachedRepository().apply {
            gate = CompletableDeferred()
            remoteModels = models.value.map { it.copy(name = "Updated") }
        }
        val cached = repository.models.value
        val viewModel = create(repository)
        val query = ModelListQuery(search = "Updated")
        viewModel.setQuery(query)
        advanceUntilIdle()
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(cached, viewModel.uiState.value.models)
        assertTrue(viewModel.uiState.value.visibleModels.isEmpty())
        assertNull(viewModel.uiState.value.message)
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(repository.remoteModels, viewModel.uiState.value.visibleModels)
        assertEquals(query, viewModel.uiState.value.query)
        // 联网无新变化，但把后台缓存应用到当前列表也应反馈“已更新”。
        assertEquals(ModelListMessage.UPDATED, viewModel.uiState.value.message)
        assertEquals(2, repository.refreshes)
    }

    @Test fun pullDuringBackgroundRequestReusesItAndPublishesLatestSnapshot() = runTest {
        val repository = cachedRepository().apply { gate = CompletableDeferred(); remoteModels = emptyList() }
        val viewModel = create(repository)
        advanceUntilIdle()
        repeat(5) { viewModel.refresh() }
        assertTrue(viewModel.uiState.value.isRefreshing)
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.models.isEmpty())
        assertEquals(1, repository.refreshes)
        assertEquals(ModelListMessage.UPDATED, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test fun pullDuringUnchangedBackgroundRequestReportsLatest() = runTest {
        val repository = cachedRepository().apply { gate = CompletableDeferred() }
        val viewModel = create(repository)
        advanceUntilIdle()
        viewModel.refresh()
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(ModelListMessage.UP_TO_DATE, viewModel.uiState.value.message)
        assertEquals(1, repository.refreshes)
    }

    @Test fun pullPromotesBackgroundNetworkAndWriteFailuresToNonBlockingErrors() = runTest {
        for ((failure, expected) in listOf(IOException("offline") to ModelListError.NETWORK, IllegalStateException("disk") to ModelListError.STORAGE)) {
            val repository = cachedRepository().apply { gate = CompletableDeferred(); this.failure = failure }
            val viewModel = create(repository)
            advanceUntilIdle()
            viewModel.refresh()
            repository.gate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(expected, viewModel.uiState.value.refreshError)
            assertEquals(repository.models.value, viewModel.uiState.value.models)
            assertFalse(viewModel.uiState.value.isRefreshing)
        }
    }

    @Test fun repeatedPullsDoNotDuplicateRequestsAndUnchangedResultsShowLatest() = runTest {
        val repository = cachedRepository()
        val viewModel = create(repository)
        advanceUntilIdle()
        repository.gate = CompletableDeferred()
        repeat(5) { viewModel.refresh() }
        advanceUntilIdle()
        assertEquals(2, repository.refreshes)
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(ModelListMessage.UP_TO_DATE, viewModel.uiState.value.message)
    }

    @Test fun manualRefreshKeepsOldDisplayUntilPostCommitReadCompletes() = runTest {
        val repository = cachedRepository()
        val viewModel = create(repository)
        advanceUntilIdle()
        val old = viewModel.uiState.value.models
        repository.remoteModels = emptyList()
        repository.observationGate = CompletableDeferred()
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(old, viewModel.uiState.value.models)
        assertTrue(viewModel.uiState.value.isRefreshing)
        repository.observationGate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.models.isEmpty())
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test fun manualReadFailurePreservesSnapshotAndCanBeRetried() = runTest {
        val repository = cachedRepository()
        val viewModel = create(repository)
        advanceUntilIdle()
        val old = viewModel.uiState.value.models
        repository.remoteModels = emptyList()
        repository.observationFailure = IllegalStateException("read")
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(old, viewModel.uiState.value.models)
        assertEquals(ModelListError.STORAGE, viewModel.uiState.value.refreshError)
        repository.observationFailure = null
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(ModelListMessage.UPDATED, viewModel.uiState.value.message)
        assertTrue(viewModel.uiState.value.models.isEmpty())
    }

    @Test fun localCatalogWithoutIncludedLlmsStillChecksSilently() = runTest {
        val repository = cachedRepository().apply { models.value = emptyList() }
        val viewModel = create(repository)
        advanceUntilIdle()
        assertEquals(ModelListUiState(isLoading = false), viewModel.uiState.value)
        assertEquals(1, repository.refreshes)
    }

    @Test fun cancellationDuringPromotedRequestKeepsCacheWithoutError() = runTest {
        val repository = cachedRepository().apply { gate = CompletableDeferred() }
        val viewModel = create(repository)
        advanceUntilIdle()
        viewModel.refresh()
        store.clear()
        advanceUntilIdle()
        assertEquals(ModelListUiState(repository.models.value, isLoading = false), viewModel.uiState.value)
    }

    @Test fun clearingViewModelCancelsFirstLoadWithoutReportingFailure() = runTest {
        val repository = FakeRepository().apply { gate = CompletableDeferred() }
        val viewModel = create(repository)
        advanceUntilIdle()
        store.clear()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test fun externalUserActionsPublishLocalSnapshotWithoutNetworkingOrRestoringObservation() = runTest {
        val repository = cachedRepository()
        val viewModel = create(repository)
        advanceUntilIdle()
        val query = ModelListQuery(search = "Edited")
        viewModel.setQuery(query)
        repository.models.value = listOf(ModelSummary("lab/edited", "Edited"))
        viewModel.onCatalogMutatedExternally()
        advanceUntilIdle()
        assertEquals(repository.models.value, viewModel.uiState.value.visibleModels)
        repository.models.value = emptyList()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.models.isEmpty())
        viewModel.onCatalogRefreshedExternally()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.models.isEmpty())
        assertEquals(query, viewModel.uiState.value.query)
        assertEquals(1, repository.refreshes)
    }

    @Test fun externalSynchronizationWaitsForActiveStartupRequest() = runTest {
        val repository = cachedRepository().apply { gate = CompletableDeferred(); remoteModels = emptyList() }
        val viewModel = create(repository)
        advanceUntilIdle()
        viewModel.onCatalogRefreshedExternally()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.models.isEmpty())
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(ModelListUiState(isLoading = false), viewModel.uiState.value)
        assertEquals(1, repository.refreshes)
    }

    @Test fun externalSynchronizationRecoversInitialFailureThroughLocalReadOnly() = runTest {
        val repository = FakeRepository().apply { failure = IOException("offline") }
        val viewModel = create(repository)
        advanceUntilIdle()
        repository.models.value = listOf(ModelSummary("lab/local", "Local"))
        viewModel.onCatalogMutatedExternally()
        advanceUntilIdle()
        assertEquals(ModelListUiState(repository.models.value, isLoading = false), viewModel.uiState.value)
        assertEquals(1, repository.refreshes)
    }

    @Test fun externalReadFailurePreservesDisplayAndQuery() = runTest {
        val repository = cachedRepository()
        val viewModel = create(repository)
        advanceUntilIdle()
        val query = ModelListQuery(search = "Remote")
        viewModel.setQuery(query)
        repository.observationFailure = IllegalStateException("read")
        viewModel.onCatalogRefreshedExternally()
        advanceUntilIdle()
        assertEquals(ModelListError.STORAGE, viewModel.uiState.value.refreshError)
        assertNull(viewModel.uiState.value.error)
        assertEquals(repository.models.value, viewModel.uiState.value.models)
        assertEquals(query, viewModel.uiState.value.query)
    }

    @Test fun queryOnlyFiltersDisplayAndSurvivesRefreshAndFailure() = runTest {
        val repository = cachedRepository()
        val viewModel = create(repository)
        advanceUntilIdle()
        val query = ModelListQuery(search = "missing", weights = WeightFilter.OPEN)
        viewModel.setQuery(query)
        assertTrue(viewModel.uiState.value.visibleModels.isEmpty())
        assertEquals(1, repository.refreshes)
        val matching = ModelSummary("lab/missing", "Missing", openWeights = true)
        repository.remoteModels = listOf(matching)
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(listOf(matching), viewModel.uiState.value.visibleModels)
        repository.failure = IOException("offline")
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(query, viewModel.uiState.value.query)
        assertEquals(listOf(matching), viewModel.uiState.value.visibleModels)
        assertEquals(ModelListError.NETWORK, viewModel.uiState.value.refreshError)
    }

    @Test fun queryBeforeInitialLoadIsAppliedToCommittedResults() = runTest {
        val repository = FakeRepository()
        val viewModel = create(repository)
        viewModel.setQuery(ModelListQuery(search = "not-found"))
        advanceUntilIdle()
        assertEquals(repository.remoteModels, viewModel.uiState.value.models)
        assertTrue(viewModel.uiState.value.visibleModels.isEmpty())
    }

    @Test fun normalizesMimeQueriesAndRestoresAllConditionsWithoutSavingCatalog() = runTest {
        val handle = SavedStateHandle()
        val query = ModelListQuery(search = "模型", labs = setOf("alpha", "beta"), weights = WeightFilter.UNKNOWN,
            inputModalities = setOf("image/png", "image/jpeg", "custom"), outputModalities = setOf("text"))
        val first = create(cachedRepository(), handle)
        first.setQuery(query)
        val restored = create(cachedRepository(), SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        advanceUntilIdle()
        assertEquals(query.withModalityCategories(), restored.uiState.value.query)
        assertEquals(setOf("model-list-query"), handle.keys())
        restored.setQuery(ModelListQuery())
        assertEquals(ModelListQuery(), restored.uiState.value.query)
    }

    @Test fun malformedSavedQueryFallsBackAndLegacyQueryIgnoresRemovedFilters() = runTest {
        for ((json, expected) in listOf(
            "invalid" to ModelListQuery(),
            """{"search":"lab","weights":"OPEN","minContextTokens":9223372036854775807,"minOutputTokens":32000,"hasPrice":false}""" to ModelListQuery(search = "lab", weights = WeightFilter.OPEN),
        )) {
            val viewModel = create(cachedRepository(), SavedStateHandle(mapOf("model-list-query" to json)))
            advanceUntilIdle()
            assertEquals(expected, viewModel.uiState.value.query)
        }
    }

    private fun cachedRepository() = FakeRepository().apply { local = true; models.value = remoteModels }
    private fun create(repository: ModelRepository, handle: SavedStateHandle = SavedStateHandle()) =
        ModelListViewModel(repository, handle).also { store.put("list-${nextKey++}", it) }

    private class FakeRepository : ModelRepository {
        override fun observeModelDetail(id: String) = flowOf<ModelDetail?>(null)
        val models = MutableStateFlow<List<ModelSummary>>(emptyList())
        var remoteModels = listOf(ModelSummary("lab/canonical", "Remote model"))
        var bundle: List<ModelSummary>? = null
        var local = false
        var failure: Exception? = null
        var writeFailure: Exception? = null
        var observationFailure: Exception? = null
        var refreshes = 0
        var activeObservers = 0
        var gate: CompletableDeferred<Unit>? = null
        var observationGate: CompletableDeferred<Unit>? = null
        override fun observeModels() = flow {
            activeObservers++
            try {
                observationFailure?.let { throw it }
                observationGate?.await()
                models.collect { emit(it) }
            } finally { activeObservers-- }
        }
        override suspend fun hasLocalCatalog(): Boolean {
            if (!local) bundle?.let { models.value = it; local = true }
            return local
        }
        override suspend fun refresh(): Boolean {
            refreshes++
            gate?.await()
            failure?.let { throw it }
            writeFailure?.let { throw it }
            val changed = models.value != remoteModels
            models.value = remoteModels
            local = true
            return changed
        }
    }
}
