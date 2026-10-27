package io.github.chenlongapps.modelatlas.ui.modellist

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.SavedStateHandle
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.repository.CatalogRateLimitException
import io.github.chenlongapps.modelatlas.data.repository.CatalogUpdateCheckResult
import io.github.chenlongapps.modelatlas.data.repository.ModelRepository
import io.github.chenlongapps.modelatlas.testing.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ModelListViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val store = ViewModelStore()

    @After fun tearDown() = store.clear()

    @Test fun loadsThenObservesRealRepositoryData() = runTest {
        val repository = FakeRepository()
        val viewModel = create(repository)
        assertTrue(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.models.isEmpty())
        advanceUntilIdle()
        assertEquals(repository.models.value, viewModel.uiState.value.models)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState is MutableStateFlow<*>)
        repository.models.value = emptyList()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.models.isEmpty())
    }

    @Test fun firstLoadFailureWithoutCacheCanBeRetried() = runTest {
        val repository = FakeRepository().apply { failure = IOException("offline") }
        val viewModel = create(repository)
        advanceUntilIdle()
        assertEquals(ModelListError.NETWORK, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.models.isEmpty())
        repository.failure = null
        viewModel.retry()
        assertTrue(viewModel.uiState.value.isLoading)
        advanceUntilIdle()
        assertEquals(ModelListUiState(repository.models.value, isLoading = false), viewModel.uiState.value)
        assertEquals(2, repository.refreshes)
    }

    @Test fun firstLoadKeepsLoadingUntilCommittedModelsAreRead() = runTest {
        val repository = FakeRepository().apply { refreshObservationGate = CompletableDeferred() }
        val viewModel = create(repository)
        val states = mutableListOf<ModelListUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states += it }
        }

        advanceUntilIdle()
        assertEquals(1, repository.refreshes)
        assertTrue(repository.local)
        assertTrue(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.models.isEmpty())

        repository.refreshObservationGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(repository.remoteModels, viewModel.uiState.value.models)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(states.none { !it.isLoading && it.models.isEmpty() && it.error == null })
    }

    @Test fun firstLoadFinishesWhenCommittedCatalogReallyIsEmpty() = runTest {
        val repository = FakeRepository().apply {
            remoteModels = emptyList()
            refreshObservationGate = CompletableDeferred()
        }
        val viewModel = create(repository)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoading)
        repository.refreshObservationGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(ModelListUiState(isLoading = false), viewModel.uiState.value)
    }

    @Test fun firstLoadRetryKeepsLoadingUntilCommittedModelsAreRead() = runTest {
        val repository = FakeRepository().apply { failure = IOException("offline") }
        val viewModel = create(repository)
        advanceUntilIdle()
        assertEquals(ModelListError.NETWORK, viewModel.uiState.value.error)

        repository.failure = null
        repository.refreshObservationGate = CompletableDeferred()
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(2, repository.refreshes)
        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.error)

        repository.refreshObservationGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(ModelListUiState(repository.remoteModels, isLoading = false), viewModel.uiState.value)
    }

    @Test fun repeatedRetryWhileLoadingDoesNotLaunchMoreRequests() = runTest {
        val repository = FakeRepository().apply { gate = CompletableDeferred() }
        val viewModel = create(repository)
        advanceUntilIdle()
        repeat(5) { viewModel.retry() }
        advanceUntilIdle()
        assertEquals(1, repository.refreshes)
        assertTrue(viewModel.uiState.value.isLoading)
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test fun exposesParsingRateLimitAndStorageErrors() = runTest {
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

    @Test fun databaseObservationFailureCanBeRetried() = runTest {
        val repository = FakeRepository().apply { observationFailure = IllegalStateException("database read") }
        val viewModel = create(repository)
        advanceUntilIdle()
        assertEquals(ModelListError.STORAGE, viewModel.uiState.value.error)
        repository.observationFailure = null
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(repository.models.value, viewModel.uiState.value.models)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test fun clearingViewModelCancelsRequestWithoutReportingFailure() = runTest {
        val repository = FakeRepository().apply { gate = CompletableDeferred() }
        val viewModel = create(repository)
        advanceUntilIdle()
        store.clear()
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test fun cachedModelsAreVisibleWhileBackgroundCheckIsStillRunning() = runTest {
        val repository = cachedRepository().apply { gate = CompletableDeferred() }
        val viewModel = create(repository)
        advanceUntilIdle()
        assertEquals(repository.models.value, viewModel.uiState.value.models)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(1, repository.checks)
        assertEquals(0, repository.refreshes)
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
    }

    @Test fun backgroundCheckOnlyPromptsAndApplyUsesStagedData() = runTest {
        val repository = cachedRepository().apply {
            remoteModels = listOf(ModelSummary("lab/new", "New"))
        }
        val previous = repository.models.value
        val viewModel = create(repository)
        advanceUntilIdle()
        assertEquals(previous, viewModel.uiState.value.models)
        assertTrue(viewModel.uiState.value.updateAvailable)
        viewModel.applyUpdate()
        advanceUntilIdle()
        assertEquals(repository.remoteModels, viewModel.uiState.value.models)
        assertFalse(viewModel.uiState.value.updateAvailable)
        assertEquals(ModelListMessage.UPDATED, viewModel.uiState.value.message)
        assertEquals(1, repository.checks)
        assertEquals(0, repository.refreshes)
        assertEquals(1, repository.applies)
    }

    @Test fun backgroundFailureIsSilentAndExplicitRefreshFailureKeepsLocalData() = runTest {
        val repository = cachedRepository().apply { failure = IOException("offline") }
        val viewModel = create(repository)
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.error)
        assertEquals(null, viewModel.uiState.value.refreshError)
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(repository.models.value, viewModel.uiState.value.models)
        assertEquals(null, viewModel.uiState.value.error)
        assertEquals(ModelListError.NETWORK, viewModel.uiState.value.refreshError)
        assertFalse(viewModel.uiState.value.isRefreshing)
        viewModel.clearMessage()
        assertEquals(null, viewModel.uiState.value.refreshError)
    }

    @Test fun contentOnlyCheckUpdatesObservedModelsAndQueryWithoutAnyNotice() = runTest {
        val repository = cachedRepository().apply {
            gate = CompletableDeferred()
            remoteModels = models.value.map { it.copy(name = "Updated") }
        }
        val viewModel = create(repository)
        val query = ModelListQuery(search = "Updated")
        viewModel.setQuery(query)
        val states = mutableListOf<ModelListUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states += it }
        }
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.visibleModels.isEmpty())
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(repository.remoteModels, viewModel.uiState.value.models)
        assertEquals(repository.remoteModels, viewModel.uiState.value.visibleModels)
        assertEquals(query, viewModel.uiState.value.query)
        assertTrue(states.none { it.updateAvailable || it.message != null || it.refreshError != null || it.isRefreshing })
        viewModel.applyUpdate()
        assertEquals(0, repository.applies)
        assertEquals(0, repository.refreshes)
        assertEquals(1, repository.checks)
    }

    @Test fun silentWriteFailurePreservesLocalModelsWithoutNotice() = runTest {
        val repository = cachedRepository().apply {
            remoteModels = models.value.map { it.copy(name = "Updated") }
            applyFailure = IllegalStateException("disk")
        }
        val previous = repository.models.value
        val viewModel = create(repository)
        advanceUntilIdle()
        assertEquals(ModelListUiState(previous, isLoading = false), viewModel.uiState.value)
    }

    @Test fun pullDuringCheckReusesRequestAndAppliesItsResult() = runTest {
        val repository = cachedRepository().apply {
            gate = CompletableDeferred()
            remoteModels = emptyList()
        }
        val viewModel = create(repository)
        advanceUntilIdle()
        repeat(5) { viewModel.refresh() }
        assertTrue(viewModel.uiState.value.isRefreshing)
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.models.isEmpty())
        assertEquals(1, repository.checks)
        assertEquals(0, repository.refreshes)
        assertEquals(1, repository.applies)
        assertFalse(viewModel.uiState.value.updateAvailable)
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(ModelListMessage.UPDATED, viewModel.uiState.value.message)
    }

    @Test fun pullDuringContentOnlyCheckReusesSilentWriteAndReportsUpdated() = runTest {
        val repository = cachedRepository().apply {
            gate = CompletableDeferred()
            remoteModels = models.value.map { it.copy(name = "Updated") }
        }
        val viewModel = create(repository)
        advanceUntilIdle()
        repeat(5) { viewModel.refresh() }
        assertTrue(viewModel.uiState.value.isRefreshing)
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(repository.remoteModels, viewModel.uiState.value.models)
        assertEquals(ModelListMessage.UPDATED, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.updateAvailable)
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(1, repository.checks)
        assertEquals(0, repository.refreshes)
        assertEquals(0, repository.applies)
    }

    @Test fun pullDuringUnchangedCheckReportsLatestWithoutApplyingOrDownloading() = runTest {
        val repository = cachedRepository().apply { gate = CompletableDeferred() }
        val viewModel = create(repository)
        advanceUntilIdle()
        viewModel.refresh()
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(ModelListMessage.UP_TO_DATE, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.updateAvailable)
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(1, repository.checks)
        assertEquals(0, repository.refreshes)
        assertEquals(0, repository.applies)
    }

    @Test fun failedSilentWritePromotedByPullReportsStorageError() = runTest {
        val repository = cachedRepository().apply {
            gate = CompletableDeferred()
            remoteModels = models.value.map { it.copy(name = "Updated") }
            applyFailure = IllegalStateException("disk")
        }
        val previous = repository.models.value
        val viewModel = create(repository)
        advanceUntilIdle()
        viewModel.refresh()
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(previous, viewModel.uiState.value.models)
        assertEquals(ModelListError.STORAGE, viewModel.uiState.value.refreshError)
        assertFalse(viewModel.uiState.value.updateAvailable)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test fun failedCheckPromotedByPullReportsNonBlockingError() = runTest {
        val repository = cachedRepository().apply {
            gate = CompletableDeferred()
            failure = IOException("offline")
        }
        val viewModel = create(repository)
        advanceUntilIdle()
        viewModel.refresh()
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(ModelListError.NETWORK, viewModel.uiState.value.refreshError)
        assertEquals(repository.models.value, viewModel.uiState.value.models)
    }

    @Test fun repeatedPullDoesNotDuplicateRequestsAndNoChangesShowsLatestMessage() = runTest {
        val repository = cachedRepository()
        val viewModel = create(repository)
        advanceUntilIdle()
        repository.gate = CompletableDeferred()
        repeat(5) { viewModel.refresh() }
        advanceUntilIdle()
        assertEquals(1, repository.refreshes)
        assertTrue(viewModel.uiState.value.isRefreshing)
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(ModelListMessage.UP_TO_DATE, viewModel.uiState.value.message)
    }

    @Test fun localCatalogWithNoIncludedModelsStillChecksWithoutBlocking() = runTest {
        val repository = cachedRepository().apply {
            models.value = emptyList()
            remoteModels = emptyList()
        }
        val viewModel = create(repository)
        advanceUntilIdle()
        assertEquals(1, repository.checks)
        assertEquals(0, repository.refreshes)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test fun unchangedBackgroundCheckDoesNotPromptOrShowManualRefreshMessage() = runTest {
        val viewModel = create(cachedRepository())
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.updateAvailable)
        assertEquals(null, viewModel.uiState.value.message)
    }

    @Test fun failedApplyRetainsCachedModelsAndUpdateActionForRetry() = runTest {
        val repository = cachedRepository().apply {
            remoteModels = listOf(ModelSummary("lab/new", "New"))
            applyFailure = IllegalStateException("disk")
        }
        val previous = repository.models.value
        val viewModel = create(repository)
        advanceUntilIdle()
        viewModel.applyUpdate()
        advanceUntilIdle()
        assertEquals(previous, viewModel.uiState.value.models)
        assertTrue(viewModel.uiState.value.updateAvailable)
        assertEquals(ModelListError.STORAGE, viewModel.uiState.value.refreshError)
        repository.applyFailure = null
        viewModel.applyUpdate()
        advanceUntilIdle()
        assertEquals(repository.remoteModels, viewModel.uiState.value.models)
        assertFalse(viewModel.uiState.value.updateAvailable)
        assertEquals(1, repository.checks)
    }

    @Test fun cancellationDuringPromotedCheckKeepsCacheWithoutReportingError() = runTest {
        val repository = cachedRepository().apply { gate = CompletableDeferred() }
        val viewModel = create(repository)
        advanceUntilIdle()
        viewModel.refresh()
        store.clear()
        advanceUntilIdle()
        assertEquals(repository.models.value, viewModel.uiState.value.models)
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(null, viewModel.uiState.value.error)
        assertEquals(null, viewModel.uiState.value.refreshError)
        assertEquals(0, repository.applies)
    }

    @Test fun queryOnlyFiltersLocalDataAndSurvivesRefreshAndFailure() = runTest {
        val repository = cachedRepository()
        val viewModel = create(repository)
        advanceUntilIdle()
        val refreshes = repository.refreshes
        val checks = repository.checks
        val query = ModelListQuery(search = "missing", weights = WeightFilter.OPEN)
        viewModel.setQuery(query)
        assertTrue(viewModel.uiState.value.visibleModels.isEmpty())
        assertEquals(repository.models.value, viewModel.uiState.value.models)
        assertEquals(refreshes, repository.refreshes)
        assertEquals(checks, repository.checks)

        val matching = ModelSummary("lab/missing", "Missing", openWeights = true)
        repository.remoteModels = listOf(matching)
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(query, viewModel.uiState.value.query)
        assertEquals(listOf(matching), viewModel.uiState.value.visibleModels)

        repository.failure = IOException("offline")
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(query, viewModel.uiState.value.query)
        assertEquals(listOf(matching), viewModel.uiState.value.visibleModels)
        assertEquals(ModelListError.NETWORK, viewModel.uiState.value.refreshError)
        viewModel.setQuery(ModelListQuery())
        assertEquals(repository.models.value, viewModel.uiState.value.visibleModels)
        assertEquals(ModelListError.NETWORK, viewModel.uiState.value.refreshError)
    }

    @Test fun queryBeforeInitialLoadIsAppliedToCommittedResults() = runTest {
        val repository = FakeRepository()
        val viewModel = create(repository)
        viewModel.setQuery(ModelListQuery(search = "not-found"))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(repository.remoteModels, viewModel.uiState.value.models)
        assertTrue(viewModel.uiState.value.visibleModels.isEmpty())
    }

    @Test fun restoresAllQueryConditionsFromSavedStateWithoutSavingCatalog() = runTest {
        val handle = SavedStateHandle()
        val query = ModelListQuery(
            search = "模型", labs = setOf("alpha", "beta"), weights = WeightFilter.UNKNOWN,
            inputModalities = setOf("image", "custom"), outputModalities = setOf("text"),
        )
        val first = ModelListViewModel(cachedRepository(), handle).also { store.put("first", it) }
        first.setQuery(query)
        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val restored = ModelListViewModel(cachedRepository(), restoredHandle).also { store.put("restored", it) }
        assertEquals(query, restored.uiState.value.query)
        assertEquals(setOf("model-list-query"), handle.keys())
        advanceUntilIdle()
        assertEquals(query, restored.uiState.value.query)
        restored.setQuery(ModelListQuery())
        val reset = ModelListViewModel(cachedRepository(), restoredHandle).also { store.put("reset", it) }
        assertEquals(ModelListQuery(), reset.uiState.value.query)
    }

    @Test fun malformedSavedQueryFallsBackToUnfilteredCatalog() = runTest {
        val viewModel = ModelListViewModel(
            cachedRepository(), SavedStateHandle(mapOf("model-list-query" to "invalid")),
        ).also { store.put("invalid", it) }
        advanceUntilIdle()
        assertEquals(ModelListQuery(), viewModel.uiState.value.query)
        assertEquals(viewModel.uiState.value.models, viewModel.uiState.value.visibleModels)
    }

    @Test fun restoresLegacyQueryWithoutApplyingRemovedFiltersOrLosingSearch() = runTest {
        val viewModel = ModelListViewModel(cachedRepository(), SavedStateHandle(mapOf(
            "model-list-query" to """{"search":"lab","weights":"OPEN","minContextTokens":9223372036854775807,"minOutputTokens":32000,"hasPrice":false}""",
        ))).also { store.put("legacy", it) }
        advanceUntilIdle()
        assertEquals(ModelListQuery(search = "lab", weights = WeightFilter.OPEN), viewModel.uiState.value.query)
        assertEquals(filterModels(viewModel.uiState.value.models, viewModel.uiState.value.query), viewModel.uiState.value.visibleModels)
    }

    @Test fun externalRefreshClearsStalePendingNoticeWithoutAnotherRequestAndPreservesQuery() = runTest {
        val repository = cachedRepository().apply { remoteModels = listOf(ModelSummary("lab/new", "New")) }
        val viewModel = create(repository)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.updateAvailable)
        val query = ModelListQuery(search = "New")
        viewModel.setQuery(query)
        repository.refresh() // 其他页面已经成功提交。
        viewModel.onCatalogRefreshedExternally()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.updateAvailable)
        assertEquals(query, viewModel.uiState.value.query)
        assertEquals(repository.remoteModels, viewModel.uiState.value.visibleModels)
        assertEquals(1, repository.refreshes)
        assertEquals(1, repository.checks)
        assertEquals(null, viewModel.uiState.value.message)
    }

    @Test fun externalMutationKeepsPendingUpdateAndQueryWithoutAnotherRequest() = runTest {
        val repository = cachedRepository().apply { remoteModels = listOf(ModelSummary("lab/new", "New")) }
        val viewModel = create(repository)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.updateAvailable)
        val query = ModelListQuery(search = "Edited")
        viewModel.setQuery(query)
        repository.models.value = listOf(ModelSummary("lab/edited", "Edited"))
        viewModel.onCatalogMutatedExternally()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.updateAvailable)
        assertEquals(query, viewModel.uiState.value.query)
        assertEquals(repository.models.value, viewModel.uiState.value.visibleModels)
        assertEquals(1, repository.checks)
        assertEquals(0, repository.refreshes)
    }

    @Test fun externalMutationWaitsForActiveCheckAndPreservesItsPendingResult() = runTest {
        val repository = cachedRepository().apply {
            gate = CompletableDeferred()
            remoteModels = listOf(ModelSummary("lab/new", "New"))
        }
        val viewModel = create(repository)
        advanceUntilIdle()
        viewModel.onCatalogMutatedExternally()
        advanceUntilIdle()
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.updateAvailable)
        assertEquals(1, repository.checks)
        assertEquals(0, repository.refreshes)
    }

    @Test fun externalMutationRecoversInitialFailureThroughLocalObservationOnly() = runTest {
        val repository = FakeRepository().apply { failure = IOException("offline") }
        val viewModel = create(repository)
        advanceUntilIdle()
        assertEquals(ModelListError.NETWORK, viewModel.uiState.value.error)
        repository.models.value = listOf(ModelSummary("lab/local", "Local"))
        viewModel.onCatalogMutatedExternally()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.error)
        assertEquals(repository.models.value, viewModel.uiState.value.models)
        repository.models.value = listOf(ModelSummary("lab/next", "Next"))
        advanceUntilIdle()
        assertEquals(repository.models.value, viewModel.uiState.value.models)
        assertEquals(1, repository.refreshes)
    }

    @Test fun externalRefreshRecoversFirstLoadFailureAndResumesLocalObservation() = runTest {
        val repository = FakeRepository().apply { failure = IOException("offline") }
        val viewModel = create(repository)
        advanceUntilIdle()
        assertEquals(ModelListError.NETWORK, viewModel.uiState.value.error)
        repository.failure = null
        repository.refresh()
        viewModel.onCatalogRefreshedExternally()
        advanceUntilIdle()
        assertEquals(ModelListUiState(repository.remoteModels, isLoading = false), viewModel.uiState.value)
        repository.models.value = listOf(ModelSummary("lab/later", "Later"))
        advanceUntilIdle()
        assertEquals(repository.models.value, viewModel.uiState.value.models)
        assertEquals(2, repository.refreshes)
    }

    @Test fun externalSynchronizationWaitsForActiveCheckAndClearsItsLateNotice() = runTest {
        val repository = cachedRepository().apply {
            gate = CompletableDeferred()
            remoteModels = listOf(ModelSummary("lab/new", "New"))
        }
        val viewModel = create(repository)
        advanceUntilIdle()
        viewModel.onCatalogRefreshedExternally()
        advanceUntilIdle()
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.updateAvailable)
        assertEquals(1, repository.checks)
        assertEquals(0, repository.refreshes)
    }

    @Test fun externalSynchronizationReadFailureKeepsModelsAndQuery() = runTest {
        val repository = cachedRepository()
        val viewModel = create(repository)
        advanceUntilIdle()
        val query = ModelListQuery(search = "Remote")
        viewModel.setQuery(query)
        repository.observationFailure = IllegalStateException("read")
        viewModel.onCatalogRefreshedExternally()
        advanceUntilIdle()
        assertEquals(ModelListError.STORAGE, viewModel.uiState.value.error)
        assertEquals(repository.models.value, viewModel.uiState.value.models)
        assertEquals(query, viewModel.uiState.value.query)
        assertEquals(0, repository.refreshes)
    }

    private fun cachedRepository() = FakeRepository().apply {
        local = true
        models.value = remoteModels
    }

    private fun create(repository: ModelRepository) = ModelListViewModel(repository, SavedStateHandle()).also {
        store.put("model-list", it)
    }

    private class FakeRepository : ModelRepository {
        override fun observeModelDetail(id: String) = flowOf<ModelDetail?>(null)
        val models = MutableStateFlow<List<ModelSummary>>(emptyList())
        var remoteModels = listOf(ModelSummary("lab/canonical", "Remote model"))
        var local = false
        var failure: Exception? = null
        var applyFailure: Exception? = null
        var observationFailure: Exception? = null
        var refreshes = 0
        var checks = 0
        var applies = 0
        private var pending: List<ModelSummary>? = null
        var gate: CompletableDeferred<Unit>? = null
        var refreshObservationGate: CompletableDeferred<Unit>? = null
        override fun observeModels(): Flow<List<ModelSummary>> = flow {
            observationFailure?.let { throw it }
            models.collect { models ->
                if (local && refreshes > 0) refreshObservationGate?.await()
                emit(models)
            }
        }
        override suspend fun hasLocalCatalog() = local
        override suspend fun checkForUpdates(): CatalogUpdateCheckResult {
            checks++
            gate?.await()
            failure?.let { throw it }
            if (remoteModels.map { it.id }.toSet() != models.value.map { it.id }.toSet()) {
                pending = remoteModels
                return CatalogUpdateCheckResult.UPDATE_AVAILABLE
            }
            val changed = remoteModels != models.value
            if (changed) {
                applyFailure?.let { throw it }
                models.value = remoteModels
            }
            pending = null
            return if (changed) CatalogUpdateCheckResult.UPDATED_SILENTLY else CatalogUpdateCheckResult.UNCHANGED
        }
        override suspend fun applyPendingUpdate(): Boolean {
            applies++
            applyFailure?.let { throw it }
            val data = pending ?: return false
            models.value = data
            pending = null
            return true
        }
        override suspend fun refresh(): Boolean {
            refreshes++
            gate?.await()
            failure?.let { throw it }
            val changed = models.value != remoteModels
            models.value = remoteModels
            pending = null
            local = true
            return changed
        }
    }
}
