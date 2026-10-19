package io.github.chenlongapps.modelatlas.ui.modellist

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
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

data class ModelListUiState(
    val models: List<ModelSummary> = emptyList(),
    val isLoading: Boolean = true,
    val error: ModelListError? = null,
)

enum class ModelListError { NETWORK, SERVER, INVALID_DATA, RATE_LIMITED, STORAGE }

@HiltViewModel
class ModelListViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ModelListUiState())
    val uiState: StateFlow<ModelListUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        retry()
    }

    fun retry() {
        if (loadJob?.isActive == true) return
        _uiState.value = ModelListUiState()
        loadJob = viewModelScope.launch {
            try {
                // 本阶段只在本次请求成功后订阅落库结果，不承诺离线缓存回退。
                modelRepository.refresh()
                modelRepository.observeModels().collect { models ->
                    _uiState.value = ModelListUiState(models = models, isLoading = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = ModelListUiState(
                    isLoading = false,
                    error = when (error) {
                        is CatalogRateLimitException -> ModelListError.RATE_LIMITED
                        is SerializationException -> ModelListError.INVALID_DATA
                        is HttpException -> ModelListError.SERVER
                        is IOException -> ModelListError.NETWORK
                        else -> ModelListError.STORAGE
                    },
                )
            }
        }
    }
}
