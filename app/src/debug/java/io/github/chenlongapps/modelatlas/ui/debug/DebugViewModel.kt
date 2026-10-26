package io.github.chenlongapps.modelatlas.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chenlongapps.modelatlas.data.repository.CatalogRateLimitException
import io.github.chenlongapps.modelatlas.data.repository.ModelRepository
import io.github.chenlongapps.modelatlas.di.IoDispatcher
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

internal data class DebugCatalogStatistics(
    val models: Long,
    val providers: Long,
    val offerings: Long,
    val favorites: Long = 0,
)

internal enum class DebugRefreshResult {
    UPDATED, UNCHANGED, NETWORK_ERROR, SERVER_ERROR, INVALID_DATA, RATE_LIMITED, STORAGE_ERROR,
}

internal data class DebugUiState(
    val statistics: DebugCatalogStatistics? = null,
    val databaseStatus: DebugDatabaseStatus? = null,
    val isReading: Boolean = false,
    val statisticsError: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshResult: DebugRefreshResult? = null,
    val expandedTable: String? = null,
    val schemaByTable: Map<String, List<DebugColumnSchema>> = emptyMap(),
    val schemaLoadingTable: String? = null,
    val schemaErrorTable: String? = null,
) {
    val busy: Boolean get() = isReading || isRefreshing
}

@HiltViewModel
internal class DebugViewModel @Inject constructor(
    private val repository: ModelRepository,
    private val inspector: DebugDatabaseInspector,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState = _uiState.asStateFlow()

    fun readStatistics() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(isReading = true, statisticsError = false) }
        viewModelScope.launch {
            try {
                val status = withContext(ioDispatcher) { inspector.readStatus() }
                val counts = status.tables.associate { it.name to it.rows }
                val statistics = DebugCatalogStatistics(
                    models = counts.getValue("models"),
                    providers = counts.getValue("providers"),
                    offerings = counts.getValue("model_offerings"),
                    favorites = counts.getValue("favorites"),
                )
                _uiState.update {
                    it.copy(
                        statistics = statistics,
                        databaseStatus = status,
                        statisticsError = false,
                        schemaByTable = emptyMap(),
                        schemaErrorTable = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep prior counts and visibly mark them as potentially stale.
                _uiState.update { it.copy(statisticsError = true) }
            } finally {
                _uiState.update { it.copy(isReading = false) }
            }
        }
    }

    /** Lazy, whitelisted schema inspection; no reading or writing of table records. */
    fun toggleTableSchema(tableName: String) {
        val previous = _uiState.value
        if (previous.databaseStatus?.tables?.none { it.name == tableName } != false) return
        if (previous.expandedTable == tableName) {
            _uiState.update { it.copy(expandedTable = null) }
            return
        }
        _uiState.update { it.copy(expandedTable = tableName, schemaErrorTable = null) }
        if (previous.schemaByTable.containsKey(tableName) || previous.schemaLoadingTable == tableName) return

        _uiState.update { it.copy(schemaLoadingTable = tableName) }
        viewModelScope.launch {
            try {
                val columns = withContext(ioDispatcher) { inspector.readColumns(tableName) }
                _uiState.update { it.copy(schemaByTable = it.schemaByTable + (tableName to columns)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(schemaErrorTable = tableName) }
            } finally {
                _uiState.update {
                    if (it.schemaLoadingTable == tableName) it.copy(schemaLoadingTable = null) else it
                }
            }
        }
    }

    fun refresh(onRefreshed: () -> Unit = {}) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(isRefreshing = true, refreshResult = null) }
        viewModelScope.launch {
            try {
                val changed = repository.refresh()
                _uiState.update {
                    it.copy(refreshResult = if (changed) DebugRefreshResult.UPDATED else DebugRefreshResult.UNCHANGED)
                }
                onRefreshed()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(refreshResult = error.toDebugResult()) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
            readStatistics()
        }
    }
}

private fun Exception.toDebugResult() = when (this) {
    is CatalogRateLimitException -> DebugRefreshResult.RATE_LIMITED
    is SerializationException -> DebugRefreshResult.INVALID_DATA
    is HttpException -> DebugRefreshResult.SERVER_ERROR
    is IOException -> DebugRefreshResult.NETWORK_ERROR
    else -> DebugRefreshResult.STORAGE_ERROR
}
