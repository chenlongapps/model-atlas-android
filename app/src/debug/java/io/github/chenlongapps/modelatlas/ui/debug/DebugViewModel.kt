package io.github.chenlongapps.modelatlas.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chenlongapps.modelatlas.data.local.CatalogStore
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

internal data class DebugCatalogStatistics(val models: Int, val providers: Int, val offerings: Int)

internal enum class DebugRefreshResult {
    UPDATED, UNCHANGED, NETWORK_ERROR, SERVER_ERROR, INVALID_DATA, RATE_LIMITED, STORAGE_ERROR,
}

internal data class DebugUiState(
    val statistics: DebugCatalogStatistics? = null,
    val isReading: Boolean = false,
    val statisticsError: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshResult: DebugRefreshResult? = null,
) {
    val busy: Boolean get() = isReading || isRefreshing
}

@HiltViewModel
internal class DebugViewModel @Inject constructor(
    private val repository: ModelRepository,
    private val store: CatalogStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState = _uiState.asStateFlow()

    fun readStatistics() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(isReading = true, statisticsError = false) }
        viewModelScope.launch {
            try { loadStatistics() } finally {
                _uiState.update { it.copy(isReading = false) }
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
            // 即使联网失败，也独立重读本地统计；读取失败不覆盖刷新结果。
            readStatistics()
        }
    }

    private suspend fun loadStatistics() {
        try {
            val statistics = withContext(ioDispatcher) {
                store.readCatalog().let { DebugCatalogStatistics(it.models.size, it.providers.size, it.offerings.size) }
            }
            _uiState.update { it.copy(statistics = statistics, statisticsError = false) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 保留上次成功统计，并明确标记不是最新读取结果。
            _uiState.update { it.copy(statisticsError = true) }
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
