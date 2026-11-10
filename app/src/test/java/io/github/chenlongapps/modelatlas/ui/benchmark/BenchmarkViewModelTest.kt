package io.github.chenlongapps.modelatlas.ui.benchmark

import androidx.lifecycle.ViewModelStore
import io.github.chenlongapps.modelatlas.data.model.BenchmarkBoard
import io.github.chenlongapps.modelatlas.data.repository.BenchmarkRepository
import io.github.chenlongapps.modelatlas.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
    private fun create() = BenchmarkViewModel(repository).also { store.put("bench", it) }
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
}
