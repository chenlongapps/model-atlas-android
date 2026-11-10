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
import retrofit2.HttpException

data class ModelListUiState(
    val models: List<ModelSummary> = emptyList(),
    val isLoading: Boolean = true,
    val error: ModelListError? = null,
    val isRefreshing: Boolean = false,
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
    private var hasDisplaySnapshot = false
    private var checking = false
    private var refreshAfterCheck = false

    init { loadInitial() }

    fun setQuery(query: ModelListQuery) {
        val normalized = query.withModalityCategories()
        if (_uiState.value.query == normalized) return
        // 仅保存小型查询条件，不保存目录或结果；展示快照由本次 ViewModel 持有。
        savedStateHandle[QUERY_KEY] = ModelListQueryJson.encodeToString(normalized)
        _uiState.update { it.copy(query = normalized) }
    }

    private fun restoreQuery(): ModelListQuery = savedStateHandle.get<String>(QUERY_KEY)
        ?.let(::restoreModelListQuery) ?: ModelListQuery()

    fun retry() {
        if (hasDisplaySnapshot) refresh() else loadInitial()
    }

    fun refresh() {
        // 下拉时若启动检查仍在联网，复用该请求并在完成后发布展示快照。
        if (loadJob?.isActive == true) {
            if (checking) {
                refreshAfterCheck = true
                _uiState.update { it.copy(isRefreshing = true, message = null, refreshError = null) }
            }
            return
        }
        if (!hasDisplaySnapshot) {
            loadInitial()
            return
        }
        _uiState.update { it.copy(isRefreshing = true, error = null, message = null, refreshError = null) }
        loadJob = viewModelScope.launch {
            try {
                publishRefreshed(modelRepository.refresh())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(refreshError = error.toListError()) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, refreshError = null) }
    }

    /** Debug 用户主动刷新后只发布本地快照，不再次联网。 */
    fun onCatalogRefreshedExternally() = reloadLocalSnapshot()

    /** Debug 用户主动修改记录后只发布本地快照，不恢复持续目录观察。 */
    fun onCatalogMutatedExternally() = reloadLocalSnapshot()

    private fun reloadLocalSnapshot() {
        viewModelScope.launch {
            loadJob?.join()
            try {
                val models = modelRepository.observeModels().first()
                hasDisplaySnapshot = true
                _uiState.update {
                    it.copy(models = models, isLoading = false, error = null, message = null, refreshError = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update {
                    if (hasDisplaySnapshot) it.copy(refreshError = ModelListError.STORAGE)
                    else it.copy(isLoading = false, error = ModelListError.STORAGE)
                }
            }
        }
    }

    private fun loadInitial() {
        if (loadJob?.isActive == true) return
        _uiState.update { it.copy(isLoading = true, error = null, message = null, refreshError = null) }
        loadJob = viewModelScope.launch {
            var backgroundCheck = false
            try {
                // 先导入可能存在的随包数据，再读取展示快照，避免把导入前的空结果冻结。
                backgroundCheck = modelRepository.hasLocalCatalog()
                if (backgroundCheck) {
                    val models = modelRepository.observeModels().first()
                    hasDisplaySnapshot = true
                    _uiState.update { it.copy(models = models, isLoading = false) }
                    checking = true
                    val changed = modelRepository.refresh()
                    if (refreshAfterCheck) publishRefreshed(changed)
                } else {
                    modelRepository.refresh()
                    // 写库完成不等于读取结果已就绪；真实空结果也能结束首次加载。
                    val models = modelRepository.observeModels().first()
                    hasDisplaySnapshot = true
                    _uiState.update { it.copy(models = models, isLoading = false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    when {
                        it.isLoading -> it.copy(isLoading = false, error = error.toListError())
                        !backgroundCheck || refreshAfterCheck -> it.copy(refreshError = error.toListError())
                        else -> it // 自动更新失败不干扰本次缓存展示。
                    }
                }
            } finally {
                checking = false
                refreshAfterCheck = false
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private suspend fun publishRefreshed(changed: Boolean) {
        val models = modelRepository.observeModels().first()
        _uiState.update {
            it.copy(
                models = models,
                // 后台已更新过缓存时，本次联网无变化但展示变化仍反馈“已更新”。
                message = if (changed || models != it.models) ModelListMessage.UPDATED else ModelListMessage.UP_TO_DATE,
            )
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
