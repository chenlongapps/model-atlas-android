package io.github.chenlongapps.modelatlas.ui.modelcompare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.chenlongapps.modelatlas.data.model.AppearanceSettings
import io.github.chenlongapps.modelatlas.data.model.CompareModelSelection
import io.github.chenlongapps.modelatlas.data.model.FavoriteIdentity
import io.github.chenlongapps.modelatlas.data.model.FavoriteModel
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.data.repository.FavoriteRepository
import io.github.chenlongapps.modelatlas.data.repository.ModelRepository
import io.github.chenlongapps.modelatlas.data.repository.SettingsRepository
import io.github.chenlongapps.modelatlas.testing.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val favorites = FakeFavorites()
    private val settings = FakeSettings()
    private val store = ViewModelStore()
    private fun create(handle: SavedStateHandle = SavedStateHandle()) =
        ModelCompareViewModel(repository, handle, favorites, settings).also { store.put("compare", it) }
    @After fun close() = store.clear()

    @Test fun offscreenDoesNotReadCatalogOrNetworkAndOnlySelectedModelsAreObserved() = runTest {
        val vm = create()
        advanceUntilIdle()
        assertEquals(0, repository.catalogReads)
        assertEquals(0, favorites.reads)
        assertEquals(0, settings.reads)
        vm.setActive(true)
        advanceUntilIdle()
        assertEquals(1, repository.catalogReads)
        assertEquals(1, favorites.active)
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
        assertEquals(listOf(CompareModelSelection("lab/a")), settings.writes)
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
        assertEquals(listOf(CompareModelSelection("lab/a"), CompareModelSelection("lab/b"), CompareModelSelection()),
            settings.writes)
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

    @Test fun waitsForDiskRestoreBeforeEditingAndReadsNewestLocalDetailsInSlotOrder() = runTest {
        settings.saved = CompareModelSelection("lab/b", "lab/a")
        val gate = CompletableDeferred<Unit>().also { settings.readGate = it }
        val handle = SavedStateHandle()
        val vm = create(handle)
        vm.setActive(true)
        advanceUntilIdle()
        assertEquals(CompareReadStatus.CONTENT, vm.uiState.value.catalogStatus)
        assertEquals(CompareReadStatus.LOADING, vm.uiState.value.selectionStatus)
        vm.select(0, "lab/a")
        vm.remove(1)
        assertTrue(settings.writes.isEmpty())
        assertEquals(0, repository.activeDetails)
        repository.details.getValue("lab/b").value = detail("lab/b").copy(name = "Updated locally")
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("Updated locally", vm.uiState.value.first.detail?.name)
        assertEquals("lab/a", vm.uiState.value.second.detail?.id)
        assertEquals("lab/b", handle.get<String>(ModelCompareViewModel.FIRST_KEY))
        assertEquals("lab/a", handle.get<String>(ModelCompareViewModel.SECOND_KEY))
        assertTrue(settings.writes.isEmpty())
    }

    @Test fun savedStateTakesPrecedenceOverDiskIncludingExplicitRemovalOfBothSlots() = runTest {
        settings.saved = CompareModelSelection("lab/a", "lab/b")
        val partial = create(SavedStateHandle(mapOf(ModelCompareViewModel.SECOND_KEY to "lab/a")))
        partial.setActive(true)
        advanceUntilIdle()
        assertNull(partial.uiState.value.first.modelId)
        assertEquals("lab/a", partial.uiState.value.second.detail?.id)
        val removed = create(SavedStateHandle(mapOf(
            ModelCompareViewModel.FIRST_KEY to null, ModelCompareViewModel.SECOND_KEY to null,
        )))
        removed.setActive(true)
        advanceUntilIdle()
        assertNull(removed.uiState.value.first.modelId)
        assertNull(removed.uiState.value.second.modelId)
        assertEquals(0, settings.reads)
        assertTrue(settings.writes.isEmpty())
    }

    @Test fun failedReadDoesNotClearPersistedSelectionAndMissingModelCanReappearAfterRetry() = runTest {
        settings.saved = CompareModelSelection("lab/a", "lab/b")
        settings.failRead = true
        repository.details.getValue("lab/a").value = null
        val handle = SavedStateHandle()
        val vm = create(handle)
        vm.setActive(true)
        advanceUntilIdle()
        assertEquals(CompareReadStatus.ERROR, vm.uiState.value.selectionStatus)
        assertFalse(handle.contains(ModelCompareViewModel.FIRST_KEY))
        vm.select(0, "lab/b")
        vm.remove(1)
        settings.failRead = false
        vm.retrySelectionLoad()
        advanceUntilIdle()
        assertEquals(CompareReadStatus.NOT_FOUND, vm.uiState.value.first.status)
        assertEquals("lab/a", vm.uiState.value.first.modelId)
        assertEquals("lab/b", vm.uiState.value.second.detail?.id)
        assertTrue(settings.writes.isEmpty())
        repository.details.getValue("lab/a").value = detail("lab/a")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isReady)
    }

    @Test fun rapidEditsSaveInOrderEvenAfterLeavingAndRestoreIntoNewViewModel() = runTest {
        val gate = CompletableDeferred<Unit>().also { settings.writeGate = it }
        val vm = create()
        vm.setActive(true)
        advanceUntilIdle()
        vm.select(1, "lab/b")
        vm.select(0, "lab/a")
        vm.remove(1)
        vm.select(1, "lab/b")
        vm.remove(0)
        vm.setActive(false)
        store.clear()
        val reopened = create()
        reopened.setActive(true)
        advanceUntilIdle()
        assertEquals(CompareReadStatus.LOADING, reopened.uiState.value.selectionStatus)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(
            CompareModelSelection(secondId = "lab/b"), CompareModelSelection("lab/a", "lab/b"),
            CompareModelSelection("lab/a"), CompareModelSelection("lab/a", "lab/b"),
            CompareModelSelection(secondId = "lab/b"),
        ), settings.writes)
        assertNull(reopened.uiState.value.first.modelId)
        assertEquals("lab/b", reopened.uiState.value.second.detail?.id)
    }

    @Test fun failedWriteKeepsNewSelectionInMemoryAndRetrySavesIt() = runTest {
        settings.saved = CompareModelSelection("lab/a")
        settings.failWrite = true
        val handle = SavedStateHandle()
        val vm = create(handle)
        vm.setActive(true)
        advanceUntilIdle()
        vm.select(0, "lab/b")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.selectionSaveFailed)
        assertEquals("lab/b", vm.uiState.value.first.detail?.id)
        assertEquals("lab/b", handle.get<String>(ModelCompareViewModel.FIRST_KEY))
        assertEquals(CompareModelSelection("lab/a"), settings.saved)
        settings.failWrite = false
        vm.retrySelectionSave()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.selectionSaveFailed)
        assertEquals(CompareModelSelection("lab/b"), settings.saved)
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
        assertEquals(0, favorites.active)
        assertEquals(0, repository.activeDetails)
        repository.details.getValue("lab/a").value = detail("lab/a").copy(name = "Updated offline")
        vm.setActive(true)
        vm.setActive(true)
        advanceUntilIdle()
        assertEquals(1, repository.activeCatalogs)
        assertEquals(1, favorites.active)
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

    @Test fun favoriteChangesUpdateOnlyPickerOrderAndDoNotChangeCatalogOrSelection() = runTest {
        favorites.items.value = listOf(favorite("lab/b"), favorite("lab/missing"))
        val vm = create()
        vm.setActive(true)
        advanceUntilIdle()
        vm.select(0, "lab/a")
        advanceUntilIdle()
        assertEquals(setOf("lab/b", "lab/missing"), vm.uiState.value.favoriteIds)
        assertEquals(listOf("lab/b", "lab/a"), pickerIds(vm))
        assertEquals(repository.models.value, vm.uiState.value.models)
        vm.select(1, "lab/missing")
        assertNull(vm.uiState.value.second.modelId)
        favorites.items.value = listOf(favorite("lab/a"))
        advanceUntilIdle()
        assertEquals(listOf("lab/a", "lab/b"), pickerIds(vm))
        assertEquals("lab/a", vm.uiState.value.first.modelId)
        assertEquals(1, repository.activeDetails)
        assertEquals(0, repository.networkCalls)
    }

    @Test fun favoriteObservationStopsOffscreenAndReentersWithLatestFavorites() = runTest {
        val vm = create()
        vm.setActive(true)
        advanceUntilIdle()
        vm.setActive(false)
        advanceUntilIdle()
        assertEquals(0, favorites.active)
        favorites.items.value = listOf(favorite("lab/b"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.favoriteIds.isEmpty())
        vm.setActive(true)
        vm.setActive(true)
        advanceUntilIdle()
        assertEquals(2, favorites.reads)
        assertEquals(1, favorites.active)
        assertEquals(listOf("lab/b", "lab/a"), pickerIds(vm))
        store.clear()
        advanceUntilIdle()
        assertEquals(0, favorites.active)
    }

    @Test fun favoriteReadFailureFallsBackWithoutBlockingComparisonAndRecoversOnReentry() = runTest {
        favorites.items.value = listOf(favorite("lab/b"))
        val vm = create()
        vm.setActive(true)
        advanceUntilIdle()
        vm.setActive(false)
        advanceUntilIdle()
        favorites.failure = IllegalStateException("favorites")
        vm.setActive(true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.favoriteIds.isEmpty())
        assertEquals(listOf("lab/a", "lab/b"), pickerIds(vm))
        vm.select(0, "lab/a")
        vm.select(1, "lab/b")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isReady)
        vm.setActive(false)
        advanceUntilIdle()
        favorites.failure = null
        vm.setActive(true)
        advanceUntilIdle()
        assertEquals(listOf("lab/b", "lab/a"), pickerIds(vm))
        assertEquals(0, repository.networkCalls)
    }

    @Test fun favoriteCancellationDoesNotApplyFailureFallback() = runTest {
        favorites.items.value = listOf(favorite("lab/b"))
        val vm = create()
        vm.setActive(true)
        advanceUntilIdle()
        vm.setActive(false)
        advanceUntilIdle()
        favorites.failure = CancellationException()
        vm.setActive(true)
        advanceUntilIdle()
        assertEquals(setOf("lab/b"), vm.uiState.value.favoriteIds)
        assertEquals(CompareReadStatus.CONTENT, vm.uiState.value.catalogStatus)
    }

    private fun pickerIds(vm: ModelCompareViewModel) = vm.uiState.value.let {
        filterCompareModels(it.models, it.favoriteIds, "").map(ModelSummary::id)
    }

    private fun favorite(id: String) = FavoriteModel(FavoriteIdentity(id, id, "lab"), 10, null)

    private class FakeSettings : SettingsRepository {
        private val selectionLock = Mutex()
        var saved = CompareModelSelection()
        var reads = 0
        val writes = mutableListOf<CompareModelSelection>()
        var readGate: CompletableDeferred<Unit>? = null
        var writeGate: CompletableDeferred<Unit>? = null
        var failRead = false
        var failWrite = false
        override suspend fun readCompareSelection(): CompareModelSelection = selectionLock.withLock {
            reads++
            readGate?.await()
            if (failRead) throw IOException("read")
            saved
        }
        override suspend fun saveCompareSelection(selection: CompareModelSelection) = selectionLock.withLock {
            writeGate?.await()
            if (failWrite) throw IOException("write")
            saved = selection
            writes += selection
        }
        override fun observeAppearance(): Flow<AppearanceSettings> = error("Not used")
        override suspend fun updateAppearance(transform: (AppearanceSettings) -> AppearanceSettings): AppearanceSettings =
            error("Not used")
    }

    private class FakeFavorites : FavoriteRepository {
        val items = MutableStateFlow<List<FavoriteModel>>(emptyList())
        var failure: Exception? = null
        var reads = 0
        var active = 0
        override fun observeFavorites() = flow {
            reads++
            failure?.let { throw it }
            active++
            try { items.collect { emit(it) } } finally { active-- }
        }
        override fun observeFavorite(id: String): Flow<FavoriteIdentity?> = error("Not used")
        override suspend fun add(id: String): Unit = error("Not used")
        override suspend fun remove(id: String): Unit = error("Not used")
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
        override suspend fun refresh(): Boolean { networkCalls++; return false }
    }

    companion object {
        private fun detail(id: String) = ModelDetail(id, id, fetchedAtEpochMillis = 0)
    }
}
