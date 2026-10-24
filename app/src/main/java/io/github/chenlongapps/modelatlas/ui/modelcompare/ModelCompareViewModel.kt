package io.github.chenlongapps.modelatlas.ui.modelcompare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.data.repository.ModelRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CompareReadStatus { LOADING, CONTENT, NOT_FOUND, ERROR }

data class CompareModelState(
    val modelId: String? = null,
    val status: CompareReadStatus = CompareReadStatus.LOADING,
    val detail: ModelDetail? = null,
)

data class ModelCompareUiState(
    val models: List<ModelSummary> = emptyList(),
    val catalogStatus: CompareReadStatus = CompareReadStatus.LOADING,
    val first: CompareModelState = CompareModelState(),
    val second: CompareModelState = CompareModelState(),
) {
    fun slot(index: Int): CompareModelState = if (index == 0) first else second
    // 目录默认报价也必须就绪；否则详情先到时，短的无档位表格会夹回恢复的滚动位置。
    val isReady: Boolean get() = catalogStatus == CompareReadStatus.CONTENT && first.detail != null && second.detail != null
}

/** 只保存两个规范模型 ID；离屏停止本地观察，不发起任何网络请求。 */
@HiltViewModel
class ModelCompareViewModel @Inject constructor(
    private val repository: ModelRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val firstId = savedStateHandle.get<String>(FIRST_KEY)?.takeIf(String::isNotBlank)
    private val secondId = savedStateHandle.get<String>(SECOND_KEY)?.takeIf { it.isNotBlank() && it != firstId }
    private val _uiState = MutableStateFlow(ModelCompareUiState(
        first = CompareModelState(firstId), second = CompareModelState(secondId),
    ))
    val uiState = _uiState.asStateFlow()
    private var active = false
    private var catalogJob: Job? = null
    private val detailJobs = arrayOfNulls<Job>(2)

    init {
        savedStateHandle[FIRST_KEY] = firstId
        savedStateHandle[SECOND_KEY] = secondId
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value) {
            observeCatalog()
            repeat(2) { index -> _uiState.value.slot(index).modelId?.let { observeDetail(index, it) } }
        } else {
            catalogJob?.cancel()
            detailJobs.forEach { it?.cancel() }
        }
    }

    fun select(index: Int, modelId: String) {
        if (index !in 0..1 || modelId == _uiState.value.slot(1 - index).modelId) return
        if (_uiState.value.catalogStatus != CompareReadStatus.CONTENT || _uiState.value.models.none { it.id == modelId }) return
        if (_uiState.value.slot(index).modelId == modelId) return
        detailJobs[index]?.cancel()
        savedStateHandle[if (index == 0) FIRST_KEY else SECOND_KEY] = modelId
        updateSlot(index, CompareModelState(modelId))
        if (active) observeDetail(index, modelId)
    }

    fun remove(index: Int) {
        if (index !in 0..1) return
        detailJobs[index]?.cancel()
        savedStateHandle[if (index == 0) FIRST_KEY else SECOND_KEY] = null
        updateSlot(index, CompareModelState())
    }

    fun retryCatalog() {
        if (!active || catalogJob?.isActive == true) return
        _uiState.update { it.copy(catalogStatus = CompareReadStatus.LOADING) }
        observeCatalog()
    }

    fun retry(index: Int) {
        if (!active || index !in 0..1 || _uiState.value.slot(index).status != CompareReadStatus.ERROR) return
        _uiState.value.slot(index).modelId?.let {
            updateSlot(index, CompareModelState(it))
            observeDetail(index, it)
        }
    }

    private fun observeCatalog() {
        catalogJob = viewModelScope.launch {
            try {
                repository.observeModels().collect { models ->
                    _uiState.update { it.copy(models = models, catalogStatus = CompareReadStatus.CONTENT) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(models = emptyList(), catalogStatus = CompareReadStatus.ERROR) }
            }
        }
    }

    private fun observeDetail(index: Int, id: String) {
        detailJobs[index] = viewModelScope.launch {
            try {
                repository.observeModelDetail(id).collect { detail ->
                    if (_uiState.value.slot(index).modelId == id) {
                        updateSlot(index, CompareModelState(id,
                            if (detail == null) CompareReadStatus.NOT_FOUND else CompareReadStatus.CONTENT, detail))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (_uiState.value.slot(index).modelId == id) updateSlot(index, CompareModelState(id, CompareReadStatus.ERROR))
            }
        }
    }

    private fun updateSlot(index: Int, value: CompareModelState) {
        _uiState.update { if (index == 0) it.copy(first = value) else it.copy(second = value) }
    }

    companion object {
        internal const val FIRST_KEY = "compare.firstId"
        internal const val SECOND_KEY = "compare.secondId"
    }
}
