package io.github.chenlongapps.modelatlas.ui.modellist

import androidx.lifecycle.ViewModelStore
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.data.repository.CatalogRateLimitException
import io.github.chenlongapps.modelatlas.data.repository.ModelRepository
import io.github.chenlongapps.modelatlas.testing.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun cachedRepository() = FakeRepository().apply {
        local = true
        models.value = remoteModels
    }

    private fun create(repository: ModelRepository) = ModelListViewModel(repository).also {
        store.put("model-list", it)
    }

    private class FakeRepository : ModelRepository {
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
        override suspend fun checkForUpdates(): Boolean {
            checks++
            gate?.await()
            failure?.let { throw it }
            pending = remoteModels.takeUnless { it == models.value }
            return pending != null
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
