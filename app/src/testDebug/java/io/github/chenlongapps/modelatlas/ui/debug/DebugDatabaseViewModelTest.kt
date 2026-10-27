package io.github.chenlongapps.modelatlas.ui.debug

import androidx.lifecycle.ViewModelStore
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebugDatabaseViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val viewModels = ViewModelStore()
    private val store = FakeDatabase()
    private fun create() = DebugDatabaseViewModel(store).also { viewModels.put("db", it) }
    @After fun close() = viewModels.clear()

    @Test fun navigationAndDirtyDraftRequireExplicitDiscard() = runTest {
        val vm = create()
        vm.loadOverview()
        advanceUntilIdle()
        vm.openTable(DatabaseTable.FAVORITES)
        advanceUntilIdle()
        vm.openRow(store.row.key)
        advanceUntilIdle()
        vm.edit()
        vm.change("name", "Changed")
        assertFalse(vm.back())
        assertTrue(vm.state.value.confirmDiscard)
        vm.dismissDiscard()
        assertEquals("Changed", vm.state.value.draft["name"])
        vm.back()
        vm.discard()
        assertEquals(DatabaseDestination.DETAIL, vm.state.value.destination)
        assertEquals("Original", vm.state.value.row?.values?.get("name"))
        vm.back()
        advanceUntilIdle()
        assertEquals(DatabaseDestination.TABLE, vm.state.value.destination)
        vm.back()
        advanceUntilIdle()
        assertTrue(vm.back())
        assertEquals(DatabaseUiState(), vm.state.value)
    }

    @Test fun invalidAndConflictingWritesKeepDraftAndSuccessfulWriteNotifiesOnce() = runTest {
        val vm = create()
        vm.openTable(DatabaseTable.FAVORITES)
        advanceUntilIdle()
        vm.openRow(store.row.key)
        advanceUntilIdle()
        vm.edit()
        vm.change("favoritedAtEpochMillis", "bad")
        var mutations = 0
        vm.save { mutations++ }
        advanceUntilIdle()
        assertEquals(R.string.debug_database_invalid_integer, vm.state.value.fieldError?.messageResource)
        assertEquals(0, store.saves)
        vm.change("favoritedAtEpochMillis", "123")
        store.result = DatabaseWriteResult.Conflict
        vm.save { mutations++ }
        advanceUntilIdle()
        assertEquals(R.string.debug_database_conflict, vm.state.value.error)
        assertEquals("123", vm.state.value.draft["favoritedAtEpochMillis"])
        assertEquals(0, mutations)
        store.result = DatabaseWriteResult.Success
        vm.save { mutations++ }
        advanceUntilIdle()
        assertEquals(1, mutations)
        assertEquals(DatabaseDestination.DETAIL, vm.state.value.destination)
        assertEquals("123", vm.state.value.row?.values?.get("favoritedAtEpochMillis"))
    }

    @Test fun searchDebouncesAndDeletionRequiresConfirmation() = runTest {
        val vm = create()
        vm.openTable(DatabaseTable.FAVORITES)
        advanceUntilIdle()
        vm.search("a")
        advanceTimeBy(100)
        vm.search("abc")
        advanceTimeBy(249)
        runCurrent()
        assertEquals("", store.lastSearch)
        advanceTimeBy(1)
        runCurrent()
        assertEquals("abc", store.lastSearch)
        vm.openRow(store.row.key)
        advanceUntilIdle()
        vm.requestDelete()
        advanceUntilIdle()
        assertNotNull(vm.state.value.deleteImpact)
        assertEquals(0, store.deletes)
        vm.dismissDelete()
        assertNull(vm.state.value.deleteImpact)
        vm.requestDelete()
        advanceUntilIdle()
        var mutations = 0
        vm.delete { mutations++ }
        advanceUntilIdle()
        assertEquals(1, store.deletes)
        assertEquals(1, mutations)
        assertEquals(DatabaseDestination.TABLE, vm.state.value.destination)
    }

    @Test fun pendingSaveRejectsDuplicateSubmissionsAndBack() = runTest {
        val vm = create()
        vm.openTable(DatabaseTable.FAVORITES)
        advanceUntilIdle()
        vm.openRow(store.row.key)
        advanceUntilIdle()
        vm.edit()
        vm.change("name", "New")
        store.gate = CompletableDeferred()
        var mutations = 0
        vm.save { mutations++ }
        vm.save { mutations++ }
        runCurrent()
        assertEquals(1, store.saves)
        assertTrue(vm.state.value.saving)
        assertFalse(vm.back())
        store.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, mutations)
        assertFalse(vm.state.value.saving)
    }

    private class FakeDatabase : DatabaseAccess {
        var row = DatabaseRow(DatabaseTable.FAVORITES, mapOf("modelId" to "lab/model", "name" to "Original", "lab" to "lab", "favoritedAtEpochMillis" to "1"))
        var result: DatabaseWriteResult = DatabaseWriteResult.Success
        var saves = 0
        var deletes = 0
        var lastSearch = ""
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun overview() = DatabaseOverview(1, DatabaseTable.entries.associateWith { 0L }, 0, 0, 0)
        override suspend fun page(table: DatabaseTable, search: String, requestedOffset: Int): DatabasePage {
            lastSearch = search
            return DatabasePage(emptyList(), 0, 0)
        }
        override suspend fun read(table: DatabaseTable, key: Map<String, String>) = row
        override suspend fun deleteImpact(original: DatabaseRow) = DatabaseDeleteImpact(original, 0)
        override suspend fun save(table: DatabaseTable, values: Map<String, String?>, original: DatabaseRow?): DatabaseWriteResult {
            validateDatabaseRow(table, values, original)?.let { return DatabaseWriteResult.Invalid(it) }
            saves++
            gate?.await()
            if (result == DatabaseWriteResult.Success) row = DatabaseRow(table, values)
            return result
        }
        override suspend fun delete(impact: DatabaseDeleteImpact): DatabaseWriteResult { deletes++; return result }
    }
}
