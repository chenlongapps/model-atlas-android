package io.github.chenlongapps.modelatlas.ui.favorites

import androidx.lifecycle.ViewModelStore
import io.github.chenlongapps.modelatlas.data.model.FavoriteIdentity
import io.github.chenlongapps.modelatlas.data.model.FavoriteModel
import io.github.chenlongapps.modelatlas.data.repository.FavoriteRepository
import io.github.chenlongapps.modelatlas.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val store = ViewModelStore()
    private val repository = FakeFavorites()
    @After fun close() = store.clear()
    private fun detail() = FavoriteDetailViewModel(repository).also { store.put("detail", it) }

    @Test fun listDistinguishesLoadingEmptyReadFailureAndLocalRetry() = runTest {
        repository.readFailure = true
        val vm = FavoritesViewModel(repository).also { store.put("list", it) }
        assertTrue(vm.uiState.value.isLoading)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasError)
        assertFalse(vm.uiState.value.isLoading)
        repository.readFailure = false
        vm.retry()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasError)
        assertTrue(vm.uiState.value.favorites.isEmpty())
        repository.items.value = listOf(favorite("lab/a"))
        advanceUntilIdle()
        assertEquals("lab/a", vm.uiState.value.favorites.single().identity.id)
        assertEquals(0, repository.writes)
    }

    @Test fun addRemainsInDetailAndRemoveReturnsOnlyAfterSuccessfulPersistence() = runTest {
        val vm = detail()
        vm.open("lab/a")
        advanceUntilIdle()
        var backs = 0
        vm.toggle(canAdd = true) { backs++ }
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isFavorite)
        assertEquals(0, backs)
        repository.gate = CompletableDeferred()
        vm.toggle(canAdd = true) { backs++ }
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSaving)
        assertTrue(vm.uiState.value.isFavorite)
        assertEquals(0, backs)
        vm.toggle(canAdd = true) { backs++ }
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isFavorite)
        assertEquals(1, backs)
        assertEquals(2, repository.writes)
    }

    @Test fun failedRemovalKeepsFavoriteAndDoesNotNavigateThenCanRetry() = runTest {
        repository.items.value = listOf(favorite("lab/a"))
        val vm = detail()
        vm.open("lab/a")
        advanceUntilIdle()
        repository.writeFailure = true
        var backs = 0
        vm.toggle(false) { backs++ }
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isFavorite)
        assertTrue(vm.uiState.value.hasWriteError)
        assertFalse(vm.uiState.value.isSaving)
        assertEquals(0, backs)
        repository.writeFailure = false
        vm.toggle(false) { backs++ }
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasWriteError)
        assertEquals(1, backs)
    }

    @Test fun unavailableModelCanBeRemovedButNotAddedAndUnreadStateCannotWrite() = runTest {
        val vm = detail()
        vm.open("lab/a")
        vm.toggle(true) { fail("Unread state must not write") }
        advanceUntilIdle()
        vm.toggle(false) { fail("Unavailable model cannot be added") }
        advanceUntilIdle()
        assertEquals(0, repository.writes)
        repository.items.value = listOf(favorite("lab/a"))
        advanceUntilIdle()
        var backs = 0
        vm.toggle(false) { backs++ }
        advanceUntilIdle()
        assertEquals(1, backs)
    }

    @Test fun readFailureDisablesActionsAndRetryIsLocal() = runTest {
        repository.readFailure = true
        val vm = detail()
        vm.open("lab/a")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasReadError)
        vm.toggle(true) { fail() }
        assertEquals(0, repository.writes)
        repository.readFailure = false
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isLoaded)
        assertFalse(vm.uiState.value.hasReadError)
    }

    @Test fun switchingAndClosingCancelSubscriptionsAndDoNotLeakOldIdentity() = runTest {
        repository.items.value = listOf(favorite("lab/a"))
        val vm = detail()
        vm.open("lab/a")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isFavorite)
        vm.open("lab/b")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isFavorite)
        assertEquals(1, repository.active)
        vm.close("lab/a")
        assertEquals("lab/b", vm.uiState.value.modelId)
        vm.close("lab/b")
        advanceUntilIdle()
        assertEquals(0, repository.active)
        assertNull(vm.uiState.value.modelId)
    }

    @Test fun closingDuringWriteDoesNotInvokeOldNavigationOrReportCancellationAsFailure() = runTest {
        repository.items.value = listOf(favorite("lab/a"))
        repository.gate = CompletableDeferred()
        val vm = detail()
        vm.open("lab/a")
        advanceUntilIdle()
        vm.toggle(false) { fail("Closed detail must not navigate") }
        advanceUntilIdle()
        vm.close("lab/a")
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(FavoriteActionState(), vm.uiState.value)
    }

    @Test fun removalSourceIsCapturedBeforeListEmissionAndFailureOrCancellationReleasesIt() = runTest {
        repository.items.value = listOf(favorite("lab/a"))
        val vm = detail()
        vm.open("lab/a")
        advanceUntilIdle()
        val events = mutableListOf<String>()
        val start = {
            assertEquals("必须先捕获尚未删除的来源", "lab/a", repository.items.value.single().identity.id)
            events += "start"
            Unit
        }
        repository.writeFailure = true
        vm.toggle(false, start, { events += "abort" }) { fail("失败不能返回") }
        advanceUntilIdle()
        assertEquals(listOf("start", "abort"), events)
        repository.writeFailure = false
        repository.gate = CompletableDeferred()
        vm.toggle(false, start, { events += "abort" }) { fail("取消不能返回") }
        advanceUntilIdle()
        vm.close("lab/a")
        advanceUntilIdle()
        assertEquals(listOf("start", "abort", "start", "abort"), events)
        repository.gate = null
        vm.open("lab/a")
        advanceUntilIdle()
        vm.toggle(false, start, { fail("成功不释放来源") }) {
            assertTrue(repository.items.value.isEmpty())
            events += "return"
        }
        advanceUntilIdle()
        assertEquals(listOf("start", "abort", "start", "abort", "start", "return"), events)
    }

    private fun favorite(id: String) = FavoriteModel(FavoriteIdentity(id, "Model $id", "lab"), 10, null)

    private class FakeFavorites : FavoriteRepository {
        val items = MutableStateFlow<List<FavoriteModel>>(emptyList())
        var readFailure = false
        var writeFailure = false
        var writes = 0
        var active = 0
        var gate: CompletableDeferred<Unit>? = null
        override fun observeFavorites() = flow {
            if (readFailure) error("read")
            items.collect { emit(it) }
        }
        override fun observeFavorite(id: String) = flow {
            if (readFailure) error("read")
            active++
            try { items.map { all -> all.find { it.identity.id == id }?.identity }.collect { emit(it) } }
            finally { active-- }
        }
        override suspend fun add(id: String) {
            writes++
            gate?.await()
            if (writeFailure) error("write")
            items.value = items.value + FavoriteModel(FavoriteIdentity(id, id, "lab"), 20, null)
        }
        override suspend fun remove(id: String) {
            writes++
            gate?.await()
            if (writeFailure) error("write")
            items.value = items.value.filterNot { it.identity.id == id }
        }
    }
}
