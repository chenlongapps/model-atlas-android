package io.github.chenlongapps.modelatlas.ui.benchmark

import androidx.lifecycle.ViewModelStore
import io.github.chenlongapps.modelatlas.data.model.BenchmarkBoard
import io.github.chenlongapps.modelatlas.data.repository.BenchmarkRepository
import io.github.chenlongapps.modelatlas.testing.MainDispatcherRule
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
    private val repository = object : BenchmarkRepository {
        override fun observeBoards() = flow {
            reads++
            if (fail) error("read failed")
            active++
            try { emitAll(boards) } finally { active-- }
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
}
