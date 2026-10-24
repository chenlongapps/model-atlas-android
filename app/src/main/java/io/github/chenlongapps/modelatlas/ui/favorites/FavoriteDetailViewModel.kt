package io.github.chenlongapps.modelatlas.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chenlongapps.modelatlas.data.model.FavoriteIdentity
import io.github.chenlongapps.modelatlas.data.repository.FavoriteRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavoriteActionState(
    val modelId: String? = null,
    val isLoaded: Boolean = false,
    val identity: FavoriteIdentity? = null,
    val isSaving: Boolean = false,
    val hasReadError: Boolean = false,
    val hasWriteError: Boolean = false,
) {
    val isFavorite: Boolean get() = identity != null
}

/** 与详情同生命周期；按钮状态只来自已持久化的收藏，不乐观伪装写入成功。 */
@HiltViewModel
class FavoriteDetailViewModel @Inject constructor(private val repository: FavoriteRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(FavoriteActionState())
    val uiState = _uiState.asStateFlow()
    private var observation: Job? = null
    private var write: Job? = null
    private var generation = 0L

    fun open(id: String) {
        if (_uiState.value.modelId == id && observation?.isActive == true) return
        val currentGeneration = ++generation
        observation?.cancel()
        write?.cancel()
        _uiState.value = FavoriteActionState(modelId = id)
        observation = viewModelScope.launch {
            try {
                repository.observeFavorite(id).collect { identity ->
                    if (generation == currentGeneration) {
                        _uiState.update { it.copy(isLoaded = true, identity = identity, hasReadError = false) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == currentGeneration) {
                    _uiState.update { it.copy(isLoaded = false, identity = null, hasReadError = true) }
                }
            }
        }
    }

    fun retry() { _uiState.value.modelId?.let(::open) }

    fun toggle(
        canAdd: Boolean,
        onRemovalStarted: () -> Unit = {},
        onRemovalAborted: () -> Unit = {},
        onRemoved: () -> Unit,
    ) {
        val state = _uiState.value
        val id = state.modelId ?: return
        if (!state.isLoaded || state.isSaving || (!state.isFavorite && !canAdd)) return
        val currentGeneration = generation
        _uiState.update { it.copy(isSaving = true, hasWriteError = false) }
        write = viewModelScope.launch {
            var removed = false
            try {
                // 必须在数据库删除及列表 Flow 发射之前保住来源；成功回调时再捕获已经太晚。
                if (state.isFavorite) onRemovalStarted()
                if (state.isFavorite) repository.remove(id) else repository.add(id)
                if (generation == currentGeneration && state.isFavorite) {
                    removed = true
                    onRemoved()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == currentGeneration) _uiState.update { it.copy(hasWriteError = true) }
            } finally {
                if (state.isFavorite && !removed) onRemovalAborted()
                if (generation == currentGeneration) _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun close(id: String) {
        if (_uiState.value.modelId != id) return
        generation++
        observation?.cancel()
        observation = null
        write?.cancel()
        _uiState.value = FavoriteActionState()
    }
}
