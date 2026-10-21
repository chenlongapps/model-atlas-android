package io.github.chenlongapps.modelatlas.ui.modellist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.data.repository.CatalogRateLimitException
import io.github.chenlongapps.modelatlas.data.repository.ModelRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

data class ModelListUiState(
    val models: List<ModelSummary> = emptyList(),
    val isLoading: Boolean = true,
    val error: ModelListError? = null,
    val isRefreshing: Boolean = false,
    val updateAvailable: Boolean = false,
    val message: ModelListMessage? = null,
    val refreshError: ModelListError? = null,
    val query: ModelListQuery = ModelListQuery(),
) {
    val visibleModels: List<ModelSummary> = filterModels(models, query)
}

enum class ModelListError { NETWORK, SERVER, INVALID_DATA, RATE_LIMITED, STORAGE }
enum class ModelListMessage { UPDATED, UP_TO_DATE }

@HiltViewModel
class ModelListViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ModelListUiState(query = restoreQuery()))
    val uiState: StateFlow<ModelListUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var observationJob: Job? = null
    private var checking = false
    private var refreshAfterCheck = false

    init {
        load(initial = true)
    }

    fun setQuery(query: ModelListQuery) {
        if (_uiState.value.query == query) return
        // 仅保存小型查询条件，不保存目录或派生结果。
        savedStateHandle[QUERY_KEY] = Json.encodeToString(query)
        _uiState.update { it.copy(query = query) }
    }

    private fun restoreQuery(): ModelListQuery = try {
        savedStateHandle.get<String>(QUERY_KEY)?.let { Json.decodeFromString<ModelListQuery>(it) }
            ?: ModelListQuery()
    } catch (_: IllegalArgumentException) {
        ModelListQuery()
    }

    fun retry() {
        load(initial = _uiState.value.error != null || observationJob?.isActive != true)
    }

    fun refresh() {
        // 用户在后台检查期间下拉时复用当前请求，检查完成后直接提交。
        if (loadJob?.isActive == true) {
            if (checking) {
                refreshAfterCheck = true
                _uiState.update { it.copy(isRefreshing = true, message = null, refreshError = null) }
            }
            return
        }
        load(initial = _uiState.value.error != null || observationJob?.isActive != true)
    }

    fun applyUpdate() {
        if (!_uiState.value.updateAvailable) return
        load(applyPending = true)
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, refreshError = null) }
    }

    private fun load(initial: Boolean = false, applyPending: Boolean = false) {
        if (loadJob?.isActive == true) return
        _uiState.update {
            it.copy(isLoading = initial, isRefreshing = !initial, error = null, message = null, refreshError = null)
        }
        loadJob = viewModelScope.launch {
            var backgroundCheck = false
            try {
                if (initial) {
                    val models = modelRepository.observeModels().first()
                    val hasLocal = modelRepository.hasLocalCatalog()
                    _uiState.update { it.copy(models = models, isLoading = !hasLocal) }
                    if (hasLocal) observeCatalog()
                    backgroundCheck = hasLocal
                }
                if (backgroundCheck) {
                    checking = true
                    val available = modelRepository.checkForUpdates()
                    _uiState.update { it.copy(updateAvailable = available) }
                    if (refreshAfterCheck) {
                        val changed = modelRepository.applyPendingUpdate()
                        refreshed(changed)
                    }
                } else {
                    val changed = if (applyPending) modelRepository.applyPendingUpdate() else modelRepository.refresh()
                    // 写库完成不等于观察者已收到结果；首次加载重新查询已提交目录，
                    // 再原子发布列表与加载结束，避免短暂显示空目录（真实空结果仍可结束加载）。
                    val models = if (initial) modelRepository.observeModels().first() else null
                    _uiState.update { it.copy(models = models ?: it.models, isLoading = false, updateAvailable = false) }
                    if (initial) observeCatalog()
                    if (!initial) refreshed(changed)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    when {
                        it.isLoading -> it.copy(isLoading = false, error = error.toListError())
                        !backgroundCheck || refreshAfterCheck -> it.copy(refreshError = error.toListError())
                        else -> it // 静默检查失败不干扰离线浏览。
                    }
                }
            } finally {
                checking = false
                refreshAfterCheck = false
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun refreshed(changed: Boolean) {
        _uiState.update {
            it.copy(updateAvailable = false, message = if (changed) ModelListMessage.UPDATED else ModelListMessage.UP_TO_DATE)
        }
    }

    private fun observeCatalog() {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            try {
                modelRepository.observeModels().collect { models ->
                    _uiState.update { it.copy(models = models) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, error = ModelListError.STORAGE) }
            }
        }
    }

    private companion object {
        const val QUERY_KEY = "model-list-query"
    }
}

private fun Exception.toListError() = when (this) {
    is CatalogRateLimitException -> ModelListError.RATE_LIMITED
    is SerializationException -> ModelListError.INVALID_DATA
    is HttpException -> ModelListError.SERVER
    is IOException -> ModelListError.NETWORK
    else -> ModelListError.STORAGE
}
