package io.github.chenlongapps.modelatlas.ui.debug

import androidx.lifecycle.ViewModelStore
import io.github.chenlongapps.modelatlas.data.local.CatalogSnapshot
import io.github.chenlongapps.modelatlas.data.local.CatalogStore
import io.github.chenlongapps.modelatlas.data.local.ModelDetailRecord
import io.github.chenlongapps.modelatlas.data.local.ModelListRow
import io.github.chenlongapps.modelatlas.data.local.ModelOfferingPriceRow
import io.github.chenlongapps.modelatlas.data.local.entity.ModelEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ModelOfferingEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ProviderEntity
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.data.repository.CatalogRateLimitException
import io.github.chenlongapps.modelatlas.data.repository.ModelRepository
import io.github.chenlongapps.modelatlas.testing.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class DebugViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val viewModels = ViewModelStore()
    private val store = FakeStore()
    private val repository = FakeRepository()
    private fun create() = DebugViewModel(repository, store, dispatcher.dispatcher).also { viewModels.put("debug", it) }
    @After fun close() = viewModels.clear()

    @Test fun readsFullSnapshotOnceWithoutNetworkOrLlmFiltering() = runTest {
        val vm = create()
        assertEquals(0, store.reads)
        vm.readStatistics()
        repeat(3) { vm.readStatistics(); vm.refresh() }
        assertTrue(vm.uiState.value.busy)
        advanceUntilIdle()
        assertEquals(DebugCatalogStatistics(2, 1, 3), vm.uiState.value.statistics)
        assertEquals(1, store.reads)
        assertEquals(0, repository.refreshes)
        assertFalse(vm.uiState.value.busy)
        assertFalse(vm.uiState is MutableStateFlow<*>)
    }

    @Test fun emptyCatalogHasRealZeroCounts() = runTest {
        store.snapshot = CatalogSnapshot(emptyList(), emptyList(), emptyList())
        val vm = create()
        vm.readStatistics()
        advanceUntilIdle()
        assertEquals(DebugCatalogStatistics(0, 0, 0), vm.uiState.value.statistics)
    }

    @Test fun refreshIsDeduplicatedAndRereadsCommittedStatistics() = runTest {
        val vm = create()
        repository.gate = CompletableDeferred()
        repository.onRefresh = { store.snapshot = store.snapshot.copy(models = emptyList()) }
        var completions = 0
        vm.refresh { completions++ }
        repeat(4) { vm.refresh(); vm.readStatistics() }
        runCurrent()
        assertTrue(vm.uiState.value.isRefreshing)
        assertEquals(1, repository.refreshes)
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(DebugRefreshResult.UPDATED, vm.uiState.value.refreshResult)
        assertEquals(DebugCatalogStatistics(0, 1, 3), vm.uiState.value.statistics)
        assertEquals(1, completions)
        assertEquals(1, store.reads)
        assertFalse(vm.uiState.value.busy)
    }

    @Test fun unchangedRefreshStillReportsCompletionAndReadsLocalStatistics() = runTest {
        repository.changed = false
        val vm = create()
        var completions = 0
        vm.refresh { completions++ }
        advanceUntilIdle()
        assertEquals(DebugRefreshResult.UNCHANGED, vm.uiState.value.refreshResult)
        assertEquals(1, completions)
        assertEquals(1, store.reads)
    }

    @Test fun failuresAreCategorizedWithoutLeakingExceptionMessages() = runTest {
        val vm = create()
        val failures = listOf(
            IOException("private network detail") to DebugRefreshResult.NETWORK_ERROR,
            HttpException(Response.error<Any>(503, "private response".toResponseBody())) to DebugRefreshResult.SERVER_ERROR,
            SerializationException("private data") to DebugRefreshResult.INVALID_DATA,
            CatalogRateLimitException(1000) to DebugRefreshResult.RATE_LIMITED,
            IllegalStateException("private path") to DebugRefreshResult.STORAGE_ERROR,
        )
        var completions = 0
        for ((failure, result) in failures) {
            repository.failure = failure
            vm.refresh { completions++ }
            advanceUntilIdle()
            assertEquals(result, vm.uiState.value.refreshResult)
            assertEquals(DebugCatalogStatistics(2, 1, 3), vm.uiState.value.statistics)
            assertFalse(vm.uiState.value.busy)
        }
        assertEquals(0, completions)
    }

    @Test fun statisticsFailurePreservesLastCountsAndDoesNotOverwriteSuccessfulRefresh() = runTest {
        val vm = create()
        vm.readStatistics()
        advanceUntilIdle()
        store.failure = IOException("database")
        vm.refresh()
        advanceUntilIdle()
        assertEquals(DebugRefreshResult.UPDATED, vm.uiState.value.refreshResult)
        assertEquals(DebugCatalogStatistics(2, 1, 3), vm.uiState.value.statistics)
        assertTrue(vm.uiState.value.statisticsError)
        store.failure = null
        vm.readStatistics()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.statisticsError)
        assertEquals(1, repository.refreshes)
    }

    @Test fun cancellationIsNotShownAsFailureAndDoesNotStartStatisticsRead() = runTest {
        repository.failure = CancellationException("cancelled")
        val vm = create()
        vm.refresh()
        advanceUntilIdle()
        assertNull(vm.uiState.value.refreshResult)
        assertEquals(0, store.reads)
        assertFalse(vm.uiState.value.busy)
    }

    @Test fun cancelledStatisticsReadIsNotShownAsFailure() = runTest {
        store.failure = CancellationException("cancelled")
        val vm = create()
        vm.readStatistics()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.statisticsError)
        assertFalse(vm.uiState.value.busy)
    }

    private class FakeRepository : ModelRepository {
        var refreshes = 0
        var changed = true
        var failure: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        var onRefresh: () -> Unit = {}
        override suspend fun refresh(): Boolean {
            refreshes++
            gate?.await()
            failure?.let { throw it }
            onRefresh()
            return changed
        }
        override fun observeModels() = flowOf(emptyList<ModelSummary>())
        override fun observeModelDetail(id: String) = flowOf<ModelDetail?>(null)
        override suspend fun hasLocalCatalog() = true
        override suspend fun checkForUpdates() = error("Debug must not check for pending updates")
        override suspend fun applyPendingUpdate() = error("Debug must use refresh")
    }

    private class FakeStore : CatalogStore {
        var reads = 0
        var failure: Exception? = null
        var snapshot = CatalogSnapshot(
            models = listOf(
                ModelEntity("lab/a", "a", "LLM", 1, type = "llm"),
                ModelEntity("lab/b", "b", "Image", 1, type = "image"),
            ),
            providers = listOf(ProviderEntity("provider", "provider", "Provider", 1)),
            offerings = List(3) { ModelOfferingEntity("provider", "$it", "$it", "Offering $it", 1) },
        )
        override suspend fun readCatalog(): CatalogSnapshot {
            reads++
            failure?.let { throw it }
            return snapshot
        }
        override suspend fun hasCatalog() = !snapshot.isEmpty
        override fun observeModelRows() = flowOf(emptyList<ModelListRow>())
        override fun observeOfferingPriceRows() = flowOf(emptyList<ModelOfferingPriceRow>())
        override fun observeModelDetail(id: String) = flowOf<ModelDetailRecord?>(null)
        override suspend fun replaceCatalog(snapshot: CatalogSnapshot) = error("Debug must not write directly")
    }
}
