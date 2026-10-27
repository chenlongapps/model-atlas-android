package io.github.chenlongapps.modelatlas.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chenlongapps.modelatlas.R
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class DatabaseDestination { OVERVIEW, TABLE, DETAIL, EDIT }

internal data class DatabaseUiState(
    val destination: DatabaseDestination = DatabaseDestination.OVERVIEW,
    val overview: DatabaseOverview? = null,
    val table: DatabaseTable? = null,
    val search: String = "",
    val page: DatabasePage? = null,
    val row: DatabaseRow? = null,
    val selectedKey: Map<String, String>? = null,
    val draft: Map<String, String?> = emptyMap(),
    val initialDraft: Map<String, String?> = emptyMap(),
    val creating: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: Int? = null,
    val fieldError: DatabaseValidationError? = null,
    val message: Int? = null,
    val deleteImpact: DatabaseDeleteImpact? = null,
    val confirmDiscard: Boolean = false,
)

@HiltViewModel
internal class DebugDatabaseViewModel(private val store: DatabaseAccess) : ViewModel() {
    @Inject constructor(store: DebugDatabaseStore) : this(store as DatabaseAccess)
    private val _state = MutableStateFlow(DatabaseUiState())
    val state = _state.asStateFlow()
    private var readJob: Job? = null

    fun loadOverview() {
        if (_state.value.saving) return
        readJob?.cancel()
        _state.update { it.copy(destination = DatabaseDestination.OVERVIEW, table = null, page = null, row = null,
            selectedKey = null, draft = emptyMap(), initialDraft = emptyMap(), loading = true, error = null, deleteImpact = null) }
        readJob = viewModelScope.launch {
            try {
                val overview = store.overview()
                _state.update { it.copy(overview = overview, loading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = error.readMessage()) }
            }
        }
    }

    fun openTable(table: DatabaseTable) {
        if (_state.value.saving) return
        _state.update { it.copy(destination = DatabaseDestination.TABLE, table = table, search = "", page = null, row = null, selectedKey = null, message = null) }
        loadPage(0)
    }

    fun search(value: String) {
        if (_state.value.saving) return
        _state.update { it.copy(search = value, page = null, error = null) }
        loadPage(0, debounce = true)
    }

    fun loadPage(offset: Int, debounce: Boolean = false) {
        val table = _state.value.table ?: return
        val search = _state.value.search.trim()
        readJob?.cancel()
        _state.update { it.copy(destination = DatabaseDestination.TABLE, row = null, selectedKey = null,
            draft = emptyMap(), initialDraft = emptyMap(), loading = true, error = null, deleteImpact = null) }
        readJob = viewModelScope.launch {
            try {
                if (debounce) delay(250)
                val page = store.page(table, search, offset)
                _state.update { it.copy(destination = DatabaseDestination.TABLE, page = page, loading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = error.readMessage()) }
            }
        }
    }

    fun openRow(key: Map<String, String>) {
        val table = _state.value.table ?: return
        readJob?.cancel()
        _state.update { it.copy(destination = DatabaseDestination.DETAIL, row = null, selectedKey = key,
            draft = emptyMap(), initialDraft = emptyMap(), loading = true, error = null, deleteImpact = null) }
        readJob = viewModelScope.launch {
            try {
                val row = store.read(table, key)
                _state.update { it.copy(row = row, loading = false, error = if (row == null) R.string.debug_database_missing else null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = error.readMessage()) }
            }
        }
    }

    fun create() {
        val table = _state.value.table ?: return
        readJob?.cancel()
        val values = table.columns.associate { it.name to if (it.required) "" else null }
        _state.update { it.copy(destination = DatabaseDestination.EDIT, row = null, selectedKey = null, draft = values, initialDraft = values,
            creating = true, loading = false, error = null, fieldError = null, message = null) }
    }

    fun edit() {
        val row = _state.value.row ?: return
        _state.update { it.copy(destination = DatabaseDestination.EDIT, draft = row.values, initialDraft = row.values,
            creating = false, error = null, fieldError = null, message = null) }
    }

    fun change(field: String, value: String?) {
        val state = _state.value
        val column = state.table?.columns?.find { it.name == field } ?: return
        if (state.saving || (!state.creating && column.primary) || (value == null && column.required)) return
        _state.update { it.copy(draft = it.draft + (field to value), fieldError = null, error = null) }
    }

    fun save(onMutation: () -> Unit) {
        val state = _state.value
        val table = state.table ?: return
        if (state.saving || state.destination != DatabaseDestination.EDIT) return
        val original = if (state.creating) null else state.row ?: return
        val values = state.draft.toMap()
        _state.update { it.copy(saving = true, error = null, fieldError = null) }
        viewModelScope.launch {
            try {
                when (val result = store.save(table, values, original)) {
                    DatabaseWriteResult.Success -> {
                        onMutation()
                        _state.update { it.copy(saving = false, message = R.string.debug_database_saved) }
                        openRow(DatabaseRow(table, values).key)
                    }
                    DatabaseWriteResult.Conflict -> failure(R.string.debug_database_conflict)
                    DatabaseWriteResult.Duplicate -> failure(R.string.debug_database_duplicate)
                    DatabaseWriteResult.CascadeChanged -> failure(R.string.debug_database_related_changed)
                    is DatabaseWriteResult.Invalid -> _state.update { it.copy(saving = false, fieldError = result.error) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failure(R.string.debug_database_save_failed)
            }
        }
    }

    fun requestDelete() {
        val row = _state.value.row ?: return
        if (_state.value.saving) return
        _state.update { it.copy(loading = true, error = null) }
        readJob?.cancel()
        readJob = viewModelScope.launch {
            try {
                val impact = store.deleteImpact(row)
                _state.update { it.copy(loading = false, deleteImpact = impact,
                    error = if (impact == null) R.string.debug_database_conflict else null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = error.readMessage()) }
            }
        }
    }

    fun dismissDelete() { _state.update { it.copy(deleteImpact = null) } }

    fun delete(onMutation: () -> Unit) {
        val impact = _state.value.deleteImpact ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                when (store.delete(impact)) {
                    DatabaseWriteResult.Success -> {
                        onMutation()
                        val offset = _state.value.page?.offset ?: 0
                        _state.update { it.copy(saving = false, deleteImpact = null, row = null, message = R.string.debug_database_deleted) }
                        loadPage(offset)
                    }
                    DatabaseWriteResult.Conflict -> failure(R.string.debug_database_conflict)
                    DatabaseWriteResult.CascadeChanged -> failure(R.string.debug_database_cascade_changed)
                    else -> failure(R.string.debug_database_delete_failed)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failure(R.string.debug_database_delete_failed)
            }
        }
    }

    private fun failure(message: Int) {
        _state.update { it.copy(saving = false, deleteImpact = null, error = message) }
    }

    /** 返回 true 表示已回到顶层，可关闭整个管理页。 */
    fun back(): Boolean {
        val state = _state.value
        if (state.saving) return false
        if (state.deleteImpact != null) { dismissDelete(); return false }
        if (state.confirmDiscard) { _state.update { it.copy(confirmDiscard = false) }; return false }
        if (state.destination == DatabaseDestination.EDIT && state.draft != state.initialDraft) {
            _state.update { it.copy(confirmDiscard = true) }
            return false
        }
        return leaveCurrent()
    }

    fun dismissDiscard() { _state.update { it.copy(confirmDiscard = false) } }
    fun discard() { _state.update { it.copy(confirmDiscard = false) }; leaveCurrent() }

    fun retry() {
        val state = _state.value
        when (state.destination) {
            DatabaseDestination.OVERVIEW -> loadOverview()
            DatabaseDestination.TABLE -> loadPage(state.page?.offset ?: 0)
            DatabaseDestination.DETAIL -> state.selectedKey?.let(::openRow)
            DatabaseDestination.EDIT -> Unit
        }
    }

    private fun leaveCurrent(): Boolean {
        val state = _state.value
        readJob?.cancel()
        when (state.destination) {
            DatabaseDestination.OVERVIEW -> {
                _state.value = DatabaseUiState()
                return true
            }
            DatabaseDestination.TABLE -> loadOverview()
            DatabaseDestination.DETAIL -> loadPage(state.page?.offset ?: 0)
            DatabaseDestination.EDIT -> if (state.creating) loadPage(state.page?.offset ?: 0) else {
                _state.update { it.copy(destination = DatabaseDestination.DETAIL, draft = emptyMap(), initialDraft = emptyMap(),
                    loading = false, error = null, fieldError = null) }
            }
        }
        return false
    }

    private fun Exception.readMessage() = if (this is DatabaseSchemaMismatch) {
        R.string.debug_database_schema_mismatch
    } else R.string.debug_database_read_failed
}
