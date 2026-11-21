package io.github.chenlongapps.modelatlas.ui.modelcompare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chenlongapps.modelatlas.data.model.CompareModelSelection
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.data.repository.FavoriteRepository
import io.github.chenlongapps.modelatlas.data.repository.ModelRepository
import io.github.chenlongapps.modelatlas.data.repository.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val favoriteIds: Set<String> = emptySet(),
    val selectionStatus: CompareReadStatus = CompareReadStatus.CONTENT,
    val selectionSaveFailed: Boolean = false,
) {
    fun slot(index: Int): CompareModelState = if (index == 0) first else second
    // 目录默认报价也必须就绪；否则详情先到时，较短的正文会夹回恢复的滚动位置。
    val isReady: Boolean get() = catalogStatus == CompareReadStatus.CONTENT && first.detail != null && second.detail != null
}

/** 只保存两个规范模型 ID；离屏停止本地观察，不发起任何网络请求。 */
@HiltViewModel
class ModelCompareViewModel @Inject constructor(
    private val repository: ModelRepository,
    private val savedStateHandle: SavedStateHandle,
    private val favoriteRepository: FavoriteRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    // 保存的双空槽也是明确恢复结果，不能用 DataStore 重新填入旧 ID。
    private val restoredSelection = if (savedStateHandle.contains(FIRST_KEY) || savedStateHandle.contains(SECOND_KEY)) {
        CompareModelSelection(savedStateHandle[FIRST_KEY], savedStateHandle[SECOND_KEY]).normalized()
    } else null
    private val _uiState = MutableStateFlow(ModelCompareUiState(
        first = CompareModelState(restoredSelection?.firstId),
        second = CompareModelState(restoredSelection?.secondId),
        selectionStatus = if (restoredSelection == null) CompareReadStatus.LOADING else CompareReadStatus.CONTENT,
    ))
    val uiState = _uiState.asStateFlow()
    private var active = false
    private var catalogJob: Job? = null
    private var favoritesJob: Job? = null
    private val detailJobs = arrayOfNulls<Job>(2)
    private var selectionLoadJob: Job? = null
    private var latestWrite: CompareModelSelection? = null

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value) {
            observeCatalog()
            observeFavorites()
            when (_uiState.value.selectionStatus) {
                CompareReadStatus.LOADING -> retrySelectionLoad()
                CompareReadStatus.CONTENT -> observeSelectedDetails()
                else -> Unit
            }
        } else {
            catalogJob?.cancel()
            favoritesJob?.cancel()
            detailJobs.forEach { it?.cancel() }
        }
    }

    fun select(index: Int, modelId: String) {
        if (index !in 0..1 || _uiState.value.selectionStatus != CompareReadStatus.CONTENT ||
            modelId == _uiState.value.slot(1 - index).modelId) return
        if (_uiState.value.catalogStatus != CompareReadStatus.CONTENT || _uiState.value.models.none { it.id == modelId }) return
        if (_uiState.value.slot(index).modelId == modelId) return
        detailJobs[index]?.cancel()
        updateSlot(index, CompareModelState(modelId))
        saveSelection()
        if (active) observeDetail(index, modelId)
    }

    fun remove(index: Int) {
        if (index !in 0..1 || _uiState.value.selectionStatus != CompareReadStatus.CONTENT ||
            _uiState.value.slot(index).modelId == null) return
        detailJobs[index]?.cancel()
        updateSlot(index, CompareModelState())
        saveSelection()
    }

    fun retrySelectionLoad() {
        if (!active || selectionLoadJob?.isActive == true || _uiState.value.selectionStatus == CompareReadStatus.CONTENT) return
        _uiState.update { it.copy(selectionStatus = CompareReadStatus.LOADING) }
        selectionLoadJob = viewModelScope.launch {
            try {
                val selection = settingsRepository.readCompareSelection().normalized()
                saveToHandle(selection)
                _uiState.update {
                    it.copy(first = CompareModelState(selection.firstId), second = CompareModelState(selection.secondId),
                        selectionStatus = CompareReadStatus.CONTENT)
                }
                if (active) observeSelectedDetails()
            } catch (_: IOException) {
                // 不把读取失败当成空选择，也不写回空 ID；等待重试。
                _uiState.update { it.copy(selectionStatus = CompareReadStatus.ERROR) }
            }
        }
    }

    fun retrySelectionSave() {
        if (_uiState.value.selectionSaveFailed) saveSelection()
    }

    private fun observeSelectedDetails() {
        repeat(2) { index -> _uiState.value.slot(index).modelId?.let { observeDetail(index, it) } }
    }

    private fun saveSelection() {
        val state = _uiState.value
        val selection = CompareModelSelection(state.first.modelId, state.second.modelId)
        saveToHandle(selection)
        latestWrite = selection
        _uiState.update { it.copy(selectionSaveFailed = false) }
        // 立即排入仓库，已接收的写入不随切页或 ViewModel 清除取消。
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                try {
                    settingsRepository.saveCompareSelection(selection)
                } catch (_: IOException) {
                    if (latestWrite === selection) _uiState.update { it.copy(selectionSaveFailed = true) }
                }
            }
        }
    }

    private fun saveToHandle(selection: CompareModelSelection) {
        savedStateHandle[FIRST_KEY] = selection.firstId
        savedStateHandle[SECOND_KEY] = selection.secondId
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

    private fun observeFavorites() {
        favoritesJob = viewModelScope.launch {
            try {
                favoriteRepository.observeFavorites().collect { favorites ->
                    _uiState.update { it.copy(favoriteIds = favorites.map { favorite -> favorite.identity.id }.toSet()) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 收藏只影响候选顺序，读取失败时恢复目录顺序，不阻断选择或对比。
                _uiState.update { it.copy(favoriteIds = emptySet()) }
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
