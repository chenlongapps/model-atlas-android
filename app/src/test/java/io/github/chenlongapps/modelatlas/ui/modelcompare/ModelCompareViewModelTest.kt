package io.github.chenlongapps.modelatlas.ui.modelcompare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.data.repository.CatalogUpdateCheckResult
import io.github.chenlongapps.modelatlas.data.repository.ModelRepository
import io.github.chenlongapps.modelatlas.testing.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelCompareViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val repository = FakeRepository()
    private val store = ViewModelStore()
    private fun create(handle: SavedStateHandle = SavedStateHandle()) = ModelCompareViewModel(repository, handle).also { store.put("compare", it) }
    @After fun close() = store.clear()

    @Test fun offscreenDoesNotReadCatalogOrNetworkAndOnlySelectedModelsAreObserved() = runTest {
        val vm = create()
        advanceUntilIdle()
        assertEquals(0, repository.catalogReads)
        vm.setActive(true)
        advanceUntilIdle()
        assertEquals(1, repository.catalogReads)
        assertEquals(0, repository.activeDetails)
        vm.select(0, "lab/a")
        vm.select(1, "lab/b")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isReady)
        assertEquals(2, repository.activeDetails)
        assertFalse(vm.uiState is MutableStateFlow<*>)
        assertEquals(0, repository.networkCalls)
    }

    @Test fun rejectsDuplicateUnknownAndInvalidSlotSelections() = runTest {
        val vm = create()
        vm.setActive(true)
        vm.select(0, "lab/a") // 目录首个结果尚未到达。
        assertNull(vm.uiState.value.first.modelId)
        advanceUntilIdle()
        vm.select(0, "lab/a")
        vm.select(1, "lab/a")
        vm.select(1, "missing")
        vm.select(4, "lab/b")
        advanceUntilIdle()
        assertEquals("lab/a", vm.uiState.value.first.modelId)
        assertNull(vm.uiState.value.second.modelId)
        assertEquals(1, repository.activeDetails)
    }

    @Test fun replacementAndRemovalCancelOldSubscriptionAndSaveOnlyIds() = runTest {
        val handle = SavedStateHandle()
        val vm = create(handle)
        vm.setActive(true)
        advanceUntilIdle()
        vm.select(0, "lab/a")
        advanceUntilIdle()
        vm.select(0, "lab/b")
        assertNull(vm.uiState.value.first.detail)
        advanceUntilIdle()
        assertEquals("lab/b", vm.uiState.value.first.detail!!.id)
        assertEquals(1, repository.activeDetails)
        assertEquals("lab/b", handle.get<String>(ModelCompareViewModel.FIRST_KEY))
        vm.remove(0)
        advanceUntilIdle()
        assertEquals(0, repository.activeDetails)
        assertNull(handle.get<String>(ModelCompareViewModel.FIRST_KEY))
        assertNull(vm.uiState.value.first.detail)
    }

    @Test fun restoresTwoIdsWithoutSerializingCatalogAndSanitizesDuplicateIds() = runTest {
        val vm = create(SavedStateHandle(mapOf(ModelCompareViewModel.FIRST_KEY to "lab/a", ModelCompareViewModel.SECOND_KEY to "lab/b")))
        assertEquals("lab/a", vm.uiState.value.first.modelId)
        assertFalse(vm.uiState.value.isReady)
        vm.setActive(true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isReady)
        val duplicate = create(SavedStateHandle(mapOf(ModelCompareViewModel.FIRST_KEY to "lab/a", ModelCompareViewModel.SECOND_KEY to "lab/a")))
        assertNull(duplicate.uiState.value.second.modelId)
    }

    @Test fun stopsAllSubscriptionsOffscreenAndReopensWithLatestLocalData() = runTest {
        val vm = create()
        vm.setActive(true)
        advanceUntilIdle()
        vm.select(0, "lab/a")
        advanceUntilIdle()
        vm.setActive(false)
        advanceUntilIdle()
        assertEquals(0, repository.activeCatalogs)
        assertEquals(0, repository.activeDetails)
        repository.details.getValue("lab/a").value = detail("lab/a").copy(name = "Updated offline")
        vm.setActive(true)
        vm.setActive(true)
        advanceUntilIdle()
        assertEquals(1, repository.activeCatalogs)
        assertEquals(1, repository.activeDetails)
        assertEquals("Updated offline", vm.uiState.value.first.detail!!.name)
        assertEquals(0, repository.networkCalls)
    }

    @Test fun removedModelClearsStaleComparisonButKeepsIdForReplacementAndCanReappear() = runTest {
        val vm = create(SavedStateHandle(mapOf(ModelCompareViewModel.FIRST_KEY to "lab/a", ModelCompareViewModel.SECOND_KEY to "lab/b")))
        vm.setActive(true)
        advanceUntilIdle()
        repository.details.getValue("lab/a").value = null
        advanceUntilIdle()
        assertEquals(CompareReadStatus.NOT_FOUND, vm.uiState.value.first.status)
        assertEquals("lab/a", vm.uiState.value.first.modelId)
        assertNull(vm.uiState.value.first.detail)
        assertFalse(vm.uiState.value.isReady)
        assertNotNull(vm.uiState.value.second.detail)
        repository.details.getValue("lab/a").value = detail("lab/a")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isReady)
    }

    @Test fun detailFailureIsIndependentAndRetryDoesNotUseNetwork() = runTest {
        repository.detailFailures["lab/a"] = IllegalStateException("database")
        val vm = create(SavedStateHandle(mapOf(ModelCompareViewModel.FIRST_KEY to "lab/a", ModelCompareViewModel.SECOND_KEY to "lab/b")))
        vm.setActive(true)
        advanceUntilIdle()
        assertEquals(CompareReadStatus.ERROR, vm.uiState.value.first.status)
        assertEquals(CompareReadStatus.CONTENT, vm.uiState.value.second.status)
        repository.detailFailures.clear()
        vm.retry(0)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isReady)
        assertEquals(0, repository.networkCalls)
    }

    @Test fun catalogReadErrorCanRetryAndDefaultPricesFollowCatalogWithoutChangingSelection() = runTest {
        repository.catalogFailure = IllegalStateException("database")
        val vm = create()
        vm.setActive(true)
        advanceUntilIdle()
        assertEquals(CompareReadStatus.ERROR, vm.uiState.value.catalogStatus)
        repository.catalogFailure = null
        vm.retryCatalog()
        advanceUntilIdle()
        vm.select(0, "lab/a")
        val price = ModelPriceSummary("p", "Provider", false, input = 0.0, output = 0.003)
        repository.models.value = listOf(ModelSummary("lab/a", "Updated", price = price))
        advanceUntilIdle()
        assertEquals(price, vm.uiState.value.models.single().price)
        assertEquals("lab/a", vm.uiState.value.first.modelId)
        assertEquals(0, repository.networkCalls)
    }

    @Test fun cancellationIsNotReportedAsReadFailure() = runTest {
        repository.catalogFailure = CancellationException()
        repository.detailFailures["lab/a"] = CancellationException()
        val vm = create(SavedStateHandle(mapOf(ModelCompareViewModel.FIRST_KEY to "lab/a")))
        vm.setActive(true)
        advanceUntilIdle()
        assertNotEquals(CompareReadStatus.ERROR, vm.uiState.value.catalogStatus)
        assertNotEquals(CompareReadStatus.ERROR, vm.uiState.value.first.status)
    }

    private class FakeRepository : ModelRepository {
        val models = MutableStateFlow(listOf(ModelSummary("lab/a", "A"), ModelSummary("lab/b", "B")))
        val details = listOf("lab/a", "lab/b").associateWith { MutableStateFlow<ModelDetail?>(detail(it)) }
        var catalogFailure: Exception? = null
        val detailFailures = mutableMapOf<String, Exception>()
        var catalogReads = 0
        var activeCatalogs = 0
        var activeDetails = 0
        var networkCalls = 0
        override fun observeModels() = flow {
            catalogReads++
            catalogFailure?.let { throw it }
            activeCatalogs++
            try { models.collect { emit(it) } } finally { activeCatalogs-- }
        }
        override fun observeModelDetail(id: String) = flow {
            detailFailures[id]?.let { throw it }
            activeDetails++
            try { details.getValue(id).collect { emit(it) } } finally { activeDetails-- }
        }
        override suspend fun hasLocalCatalog() = true
        override suspend fun checkForUpdates(): CatalogUpdateCheckResult { networkCalls++; return CatalogUpdateCheckResult.UNCHANGED }
        override suspend fun applyPendingUpdate(): Boolean { networkCalls++; return false }
        override suspend fun refresh(): Boolean { networkCalls++; return false }
    }

    companion object {
        private fun detail(id: String) = ModelDetail(id, id, fetchedAtEpochMillis = 0)
    }
}
