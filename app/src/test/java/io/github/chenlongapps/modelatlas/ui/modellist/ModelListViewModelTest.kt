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

    @Test fun failureDoesNotShowOldRowsAndRetryCanSucceed() = runTest {
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

    private fun create(repository: ModelRepository) = ModelListViewModel(repository).also {
        store.put("model-list", it)
    }

    private class FakeRepository : ModelRepository {
        val models = MutableStateFlow(listOf(ModelSummary("lab/canonical", "Remote model")))
        var failure: Exception? = null
        var observationFailure: Exception? = null
        var refreshes = 0
        var gate: CompletableDeferred<Unit>? = null
        override fun observeModels(): Flow<List<ModelSummary>> = observationFailure?.let { failure ->
            flow { throw failure }
        } ?: models
        override suspend fun refresh() {
            refreshes++
            gate?.await()
            failure?.let { throw it }
        }
    }
}
