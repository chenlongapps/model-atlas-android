package io.github.chenlongapps.modelatlas.ui.modeldetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.repository.ModelRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ModelDetailStatus { LOADING, CONTENT, NOT_FOUND, ERROR }

data class ModelDetailUiState(
    val modelId: String? = null,
    val status: ModelDetailStatus = ModelDetailStatus.LOADING,
    val detail: ModelDetail? = null,
)

/** 只观察当前模型；关闭页面即取消，避免 Activity 的 Hilt scope 累积每个模型的订阅。 */
@HiltViewModel
class ModelDetailViewModel @Inject constructor(private val repository: ModelRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ModelDetailUiState())
    val uiState = _uiState.asStateFlow()
    private var observation: Job? = null

    fun open(modelId: String) {
        if (_uiState.value.modelId == modelId && observation?.isActive == true) return
        observation?.cancel()
        _uiState.value = ModelDetailUiState(modelId)
        observation = viewModelScope.launch {
            try {
                repository.observeModelDetail(modelId).collect { detail ->
                    _uiState.value = ModelDetailUiState(
                        modelId, if (detail == null) ModelDetailStatus.NOT_FOUND else ModelDetailStatus.CONTENT, detail,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = ModelDetailUiState(modelId, ModelDetailStatus.ERROR)
            }
        }
    }

    fun retry() {
        if (_uiState.value.status == ModelDetailStatus.ERROR) _uiState.value.modelId?.let(::open)
    }

    fun close(modelId: String) {
        if (_uiState.value.modelId != modelId) return
        observation?.cancel()
        observation = null
        _uiState.value = ModelDetailUiState()
    }
}
