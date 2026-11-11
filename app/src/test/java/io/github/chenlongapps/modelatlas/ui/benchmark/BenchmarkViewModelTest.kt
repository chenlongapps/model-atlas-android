package io.github.chenlongapps.modelatlas.ui.benchmark

import androidx.lifecycle.ViewModelStore
import io.github.chenlongapps.modelatlas.data.model.BenchmarkBoard
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.repository.BenchmarkRepository
import io.github.chenlongapps.modelatlas.data.repository.ModelRepository
import io.github.chenlongapps.modelatlas.data.repository.CatalogRateLimitException
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListError
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListMessage
import java.io.IOException
import kotlinx.coroutines.CancellationException
import io.github.chenlongapps.modelatlas.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BenchmarkViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val store = ViewModelStore()
    private val boards = MutableStateFlow<List<BenchmarkBoard>>(emptyList())
    private var active = 0
    private var reads = 0
    private var fail = false
    private var readGate: CompletableDeferred<Unit>? = null
    private var refreshGate: CompletableDeferred<Unit>? = null
    private var refreshFailure: Exception? = null
    private var refreshes = 0
    private var changed = false
    private var remoteBoards: List<BenchmarkBoard> = emptyList()
    private val modelRepository = object : ModelRepository {
        override fun observeModels() = flowOf(emptyList<ModelSummary>())
        override fun observeModelDetail(id: String) = flowOf<ModelDetail?>(null)
        override suspend fun hasLocalCatalog() = true
        override suspend fun refresh(): Boolean {
            refreshes++
            refreshGate?.await()
            refreshFailure?.let { throw it }
            boards.value = remoteBoards
            return changed
        }
    }
    private val repository = object : BenchmarkRepository {
        override fun observeBoards() = flow {
            reads++
            active++
            try {
                readGate?.await()
                if (fail) error("read failed")
                emitAll(boards)
            } finally { active-- }
        }
    }
    private fun create() = BenchmarkViewModel(repository, modelRepository).also { store.put("bench", it) }
    @After fun close() = store.clear()

    @Test fun offscreenDoesNotReadAndRepeatedActivationDoesNotDuplicateObservers() = runTest {
        val vm = create()
        advanceUntilIdle()
        assertEquals(0, reads)
        vm.setActive(true)
        vm.setActive(true)
        advanceUntilIdle()
        assertEquals(1, reads)
        assertEquals(1, active)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test fun stopsOffscreenAndResumesWithCurrentLocalResults() = runTest {
        val vm = create()
        vm.setActive(true)
        advanceUntilIdle()
        vm.setActive(false)
        advanceUntilIdle()
        assertEquals(0, active)
        boards.value = listOf(benchmarkPreviewBoard())
        vm.setActive(true)
        advanceUntilIdle()
        assertEquals("preview", vm.uiState.value.boards.single().id)
        assertEquals(2, reads)
        vm.setActive(false)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasError)
    }

    @Test fun failuresClearOldDataAndRetryIsLocalAndOnlyRunsWhenVisible() = runTest {
        val vm = create()
        boards.value = listOf(benchmarkPreviewBoard())
        vm.setActive(true)
        advanceUntilIdle()
        vm.setActive(false)
        advanceUntilIdle()
        fail = true
        vm.setActive(true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasError)
        assertTrue(vm.uiState.value.boards.isEmpty())
        vm.setActive(false)
        vm.retry()
        advanceUntilIdle()
        assertEquals(2, reads)
        fail = false
        vm.setActive(true)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasError)
        assertEquals(1, vm.uiState.value.boards.size)
        vm.retry()
        advanceUntilIdle()
        assertEquals(3, reads)
    }

    @Test fun firstReadRemainsLoadingUntilLocalResultsArrive() = runTest {
        val gate = CompletableDeferred<Unit>().also { readGate = it }
        val vm = create()
        vm.setActive(true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isLoading)
        assertEquals(1, active)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(BenchmarkUiState(isLoading = false), vm.uiState.value)
    }

    @Test fun reentryKeepsContentUntilNewLocalResultsArrive() = runTest {
        val original = benchmarkPreviewBoard()
        boards.value = listOf(original)
        val vm = create()
        vm.setActive(true)
        advanceUntilIdle()
        val ready = vm.uiState.value
        vm.setActive(false)
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>().also { readGate = it }
        boards.value = listOf(original.copy(name = "更新后的榜单"))
        vm.setActive(true)
        assertEquals(ready, vm.uiState.value)
        advanceUntilIdle()
        assertEquals(ready, vm.uiState.value)
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        assertEquals("更新后的榜单", vm.uiState.value.boards.single().name)
    }

    @Test fun reentryAlsoKeepsSuccessfulEmptyResults() = runTest {
        val vm = create()
        vm.setActive(true)
        advanceUntilIdle()
        vm.setActive(false)
        advanceUntilIdle()
        readGate = CompletableDeferred()
        vm.setActive(true)
        advanceUntilIdle()
        assertEquals(BenchmarkUiState(isLoading = false), vm.uiState.value)
    }

    @Test fun failedReentryClearsRetainedContentAndRetryShowsLoading() = runTest {
        boards.value = listOf(benchmarkPreviewBoard())
        val vm = create()
        vm.setActive(true)
        advanceUntilIdle()
        vm.setActive(false)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>().also { readGate = it }
        fail = true
        vm.setActive(true)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.boards.size)
        assertFalse(vm.uiState.value.isLoading)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(BenchmarkUiState(isLoading = false, hasError = true), vm.uiState.value)

        val retryGate = CompletableDeferred<Unit>().also { readGate = it }
        fail = false
        vm.retry()
        advanceUntilIdle()
        assertEquals(BenchmarkUiState(), vm.uiState.value)
        retryGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.boards.size)
        assertFalse(vm.uiState.value.hasError)
    }

    @Test fun rapidReentryCancelsPendingReadsWithoutClearingContentOrDuplicatingObservers() = runTest {
        boards.value = listOf(benchmarkPreviewBoard())
        val vm = create()
        vm.setActive(true)
        advanceUntilIdle()
        val ready = vm.uiState.value
        vm.setActive(false)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>().also { readGate = it }
        repeat(3) {
            vm.setActive(true)
            vm.setActive(true)
            advanceUntilIdle()
            assertEquals(ready, vm.uiState.value)
            assertEquals(1, active)
            vm.setActive(false)
            advanceUntilIdle()
            assertEquals(0, active)
            assertEquals(ready, vm.uiState.value)
        }
        vm.setActive(true)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(5, reads)
        assertEquals(1, active)
        assertEquals(ready, vm.uiState.value)
    }

    @Test fun refreshOnlyRunsOnExplicitActionWhenVisibleAndLoadedAndDoesNotReenter() = runTest {
        val vm = create()
        vm.refresh()
        vm.setActive(true)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(0, refreshes)
        refreshGate = CompletableDeferred()
        vm.refresh()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, refreshes)
        assertTrue(vm.uiState.value.isRefreshing)
        refreshGate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isRefreshing)
        assertEquals(ModelListMessage.UP_TO_DATE, vm.uiState.value.message)
        vm.setActive(false)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, refreshes)
    }

    @Test fun refreshPreservesContentAndWaitsForCommittedLocalReadBeforeEndingIndicator() = runTest {
        val original = benchmarkPreviewBoard()
        boards.value = listOf(original)
        remoteBoards = listOf(original.copy(name = "新榜单"))
        val vm = create()
        vm.setActive(true)
        advanceUntilIdle()
        refreshGate = CompletableDeferred()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(listOf(original), vm.uiState.value.boards)
        assertFalse(vm.uiState.value.isLoading)
        readGate = CompletableDeferred()
        refreshGate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isRefreshing)
        // 持续观察到新值不能提前结束主动刷新；还需完成提交后的显式读取。
        assertEquals(remoteBoards, vm.uiState.value.boards)
        assertNull(vm.uiState.value.message)
        readGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(ModelListMessage.UPDATED, vm.uiState.value.message)
        assertFalse(vm.uiState.value.isRefreshing)
        assertEquals(1, active)
    }

    @Test fun changedCatalogReportsUpdatedEvenWhenRankingsStayEqualAndEmptyResultsRemainValid() = runTest {
        val vm = create()
        vm.setActive(true)
        advanceUntilIdle()
        changed = true
        vm.refresh()
        advanceUntilIdle()
        assertEquals(ModelListMessage.UPDATED, vm.uiState.value.message)
        assertFalse(vm.uiState.value.hasError)
        assertTrue(vm.uiState.value.boards.isEmpty())
        vm.clearMessage()
        assertNull(vm.uiState.value.message)
        changed = false
        vm.refresh()
        advanceUntilIdle()
        assertEquals(ModelListMessage.UP_TO_DATE, vm.uiState.value.message)
    }

    @Test fun networkFailureKeepsRankingsCanBeDismissedAndRefreshRetryClearsOldFeedback() = runTest {
        val original = benchmarkPreviewBoard()
        boards.value = listOf(original)
        remoteBoards = listOf(original)
        val vm = create()
        vm.setActive(true)
        advanceUntilIdle()
        for ((failure, expected) in listOf(
            IOException("offline") to ModelListError.NETWORK,
            CatalogRateLimitException(1) to ModelListError.RATE_LIMITED,
            kotlinx.serialization.SerializationException("invalid") to ModelListError.INVALID_DATA,
        )) {
            refreshFailure = failure
            vm.refresh()
            advanceUntilIdle()
            assertEquals(expected, vm.uiState.value.refreshError)
            assertEquals(listOf(original), vm.uiState.value.boards)
            assertFalse(vm.uiState.value.hasError)
            assertFalse(vm.uiState.value.isRefreshing)
            vm.clearMessage()
            assertNull(vm.uiState.value.refreshError)
        }
        refreshFailure = null
        vm.refresh()
        assertNull(vm.uiState.value.refreshError)
        advanceUntilIdle()
        assertEquals(ModelListMessage.UP_TO_DATE, vm.uiState.value.message)
    }

    @Test fun postRefreshReadFailureIsNonBlockingAndCanBeRetried() = runTest {
        val original = benchmarkPreviewBoard()
        boards.value = listOf(original)
        remoteBoards = listOf(original)
        val vm = create()
        vm.setActive(true)
        advanceUntilIdle()
        fail = true
        vm.refresh()
        advanceUntilIdle()
        assertEquals(listOf(original), vm.uiState.value.boards)
        assertEquals(ModelListError.STORAGE, vm.uiState.value.refreshError)
        assertFalse(vm.uiState.value.hasError)
        fail = false
        vm.refresh()
        advanceUntilIdle()
        assertNull(vm.uiState.value.refreshError)
        assertEquals(ModelListMessage.UP_TO_DATE, vm.uiState.value.message)
    }

    @Test fun refreshCanRecoverLocalObservationErrorWithoutChangingLocalRetryIntoNetworkRetry() = runTest {
        fail = true
        val vm = create()
        vm.setActive(true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasError)
        fail = false
        remoteBoards = listOf(benchmarkPreviewBoard())
        vm.refresh()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasError)
        assertEquals(remoteBoards, vm.uiState.value.boards)
        assertEquals(1, active)
        assertEquals(1, refreshes)
        vm.retry()
        advanceUntilIdle()
        assertEquals(1, refreshes)
    }

    @Test fun leavingDuringExplicitRefreshStopsContinuousObservationButKeepsResultForReentry() = runTest {
        val vm = create()
        vm.setActive(true)
        advanceUntilIdle()
        refreshGate = CompletableDeferred()
        remoteBoards = listOf(benchmarkPreviewBoard())
        vm.refresh()
        advanceUntilIdle()
        vm.setActive(false)
        advanceUntilIdle()
        assertEquals(0, active)
        refreshGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(0, active)
        assertEquals(remoteBoards, vm.uiState.value.boards)
        vm.setActive(true)
        advanceUntilIdle()
        assertEquals(1, active)
        assertEquals(1, refreshes)
        assertEquals(ModelListMessage.UPDATED, vm.uiState.value.message)
    }

    @Test fun cancellationEndsIndicatorWithoutFailureAndAllowsNextRefresh() = runTest {
        val vm = create()
        vm.setActive(true)
        advanceUntilIdle()
        refreshFailure = CancellationException("cancelled")
        vm.refresh()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isRefreshing)
        assertNull(vm.uiState.value.refreshError)
        assertNull(vm.uiState.value.message)
        refreshFailure = null
        vm.refresh()
        advanceUntilIdle()
        assertEquals(ModelListMessage.UP_TO_DATE, vm.uiState.value.message)
    }
}
