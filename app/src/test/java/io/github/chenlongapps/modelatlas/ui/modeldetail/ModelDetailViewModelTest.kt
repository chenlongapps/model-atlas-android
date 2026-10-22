package io.github.chenlongapps.modelatlas.ui.modeldetail

import androidx.lifecycle.ViewModelStore
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.data.repository.CatalogUpdateCheckResult
import io.github.chenlongapps.modelatlas.data.repository.ModelRepository
import io.github.chenlongapps.modelatlas.testing.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelDetailViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val store = ViewModelStore()
    private val repository = FakeRepository()
    private fun create() = ModelDetailViewModel(repository).also { store.put("detail", it) }
    @After fun close() = store.clear()

    @Test fun loadsLocalDetailAndContinuouslyObservesChangesWithoutNetwork() = runTest {
        val vm = create()
        vm.open("lab/a")
        assertEquals(ModelDetailStatus.LOADING, vm.uiState.value.status)
        advanceUntilIdle()
        assertEquals(repository.details.value, vm.uiState.value.detail)
        repository.details.value = repository.details.value!!.copy(name = "Updated")
        advanceUntilIdle()
        assertEquals("Updated", vm.uiState.value.detail!!.name)
        assertEquals(0, repository.networkCalls)
        assertFalse(vm.uiState is MutableStateFlow<*>)
    }

    @Test fun missingAndRemovedModelsDoNotDisplayStaleContentAndCanReappear() = runTest {
        val vm = create()
        vm.open("lab/a")
        advanceUntilIdle()
        val detail = repository.details.value
        repository.details.value = null
        advanceUntilIdle()
        assertEquals(ModelDetailStatus.NOT_FOUND, vm.uiState.value.status)
        assertNull(vm.uiState.value.detail)
        repository.details.value = detail
        advanceUntilIdle()
        assertEquals(ModelDetailStatus.CONTENT, vm.uiState.value.status)
    }

    @Test fun readFailureRetriesLocalObservationAndDeduplicatesActiveOpens() = runTest {
        repository.failure = IllegalStateException("database")
        val vm = create()
        vm.open("lab/a")
        advanceUntilIdle()
        assertEquals(ModelDetailStatus.ERROR, vm.uiState.value.status)
        repository.failure = null
        vm.retry()
        repeat(5) { vm.open("lab/a") }
        advanceUntilIdle()
        assertEquals(ModelDetailStatus.CONTENT, vm.uiState.value.status)
        assertEquals(2, repository.subscriptions)
        assertEquals(0, repository.networkCalls)
    }

    @Test fun switchesModelsWithoutKeepingOldSubscriptionOrOldData() = runTest {
        val vm = create()
        vm.open("lab/a")
        advanceUntilIdle()
        vm.open("lab/b")
        assertNull(vm.uiState.value.detail)
        advanceUntilIdle()
        assertEquals(1, repository.active)
        assertEquals(ModelDetailStatus.NOT_FOUND, vm.uiState.value.status)
        vm.close("lab/a") // 过期页面不能关闭新订阅。
        assertEquals(1, repository.active)
        vm.close("lab/b")
        advanceUntilIdle()
        assertEquals(0, repository.active)
        assertNull(vm.uiState.value.modelId)
    }

    @Test fun closeAndReopenReadsLatestLocalState() = runTest {
        val vm = create()
        vm.open("lab/a")
        advanceUntilIdle()
        vm.close("lab/a")
        repository.details.value = repository.details.value!!.copy(name = "Latest")
        vm.open("lab/a")
        advanceUntilIdle()
        assertEquals("Latest", vm.uiState.value.detail!!.name)
        assertEquals(1, repository.active)
    }

    @Test fun cancellationDoesNotBecomeAReadError() = runTest {
        repository.failure = CancellationException("cancelled")
        val vm = create()
        vm.open("lab/a")
        advanceUntilIdle()
        assertNotEquals(ModelDetailStatus.ERROR, vm.uiState.value.status)
    }

    private class FakeRepository : ModelRepository {
        val details = MutableStateFlow<ModelDetail?>(ModelDetail("lab/a", "Model", fetchedAtEpochMillis = 100))
        var failure: Exception? = null
        var subscriptions = 0
        var active = 0
        var networkCalls = 0
        override fun observeModelDetail(id: String) = flow {
            subscriptions++
            failure?.let { throw it }
            active++
            try {
                details.collect { emit(it?.takeIf { it.id == id }) }
            } finally { active-- }
        }
        override fun observeModels() = flowOf(emptyList<ModelSummary>())
        override suspend fun hasLocalCatalog() = true
        override suspend fun checkForUpdates(): CatalogUpdateCheckResult {
            networkCalls++
            return CatalogUpdateCheckResult.UNCHANGED
        }
        override suspend fun refresh(): Boolean { networkCalls++; return false }
        override suspend fun applyPendingUpdate() = false
    }
}
