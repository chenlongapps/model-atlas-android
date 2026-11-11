package io.github.chenlongapps.modelatlas.ui.benchmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chenlongapps.modelatlas.data.model.BenchmarkBoard
import io.github.chenlongapps.modelatlas.data.repository.BenchmarkRepository
import io.github.chenlongapps.modelatlas.data.repository.ModelRepository
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListError
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListMessage
import io.github.chenlongapps.modelatlas.ui.modellist.toListError
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BenchmarkUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val boards: List<BenchmarkBoard> = emptyList(),
    val isRefreshing: Boolean = false,
    val message: ModelListMessage? = null,
    val refreshError: ModelListError? = null,
)

/** 顶层页/排行榜可见时才观察本地数据；只有总览主动下拉才复用目录联网刷新。 */
@HiltViewModel
class BenchmarkViewModel @Inject constructor(
    private val repository: BenchmarkRepository,
    private val modelRepository: ModelRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BenchmarkUiState())
    val uiState = _uiState.asStateFlow()
    private var active = false
    private var job: Job? = null
    private var refreshJob: Job? = null

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value) observe() else job?.cancel()
    }

    fun retry() {
        if (active && job?.isActive != true) observe()
    }

    fun refresh() {
        if (!active || _uiState.value.isLoading || refreshJob?.isActive == true) return
        val previousBoards = _uiState.value.boards
        _uiState.update { it.copy(isRefreshing = true, message = null, refreshError = null) }
        refreshJob = viewModelScope.launch {
            try {
                val changed = modelRepository.refresh()
                // 等事务提交后的查询完成再结束指示器，不能仅等待网络或观察 Flow 的异步通知。
                val boards = repository.observeBoards().first()
                _uiState.update {
                    it.copy(isLoading = false, hasError = false, boards = boards,
                        message = if (changed || boards != previousBoards) ModelListMessage.UPDATED else ModelListMessage.UP_TO_DATE)
                }
                if (active && job?.isActive != true) observe()
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

    private fun observe() {
        // 恢复观察时保留上次成功结果（包括空榜单），避免停稳后内容被 loading 替换。
        // 首读沿用初始 loading；失败后重试才重新进入 loading，真实读取失败仍清旧数据。
        if (_uiState.value.hasError) _uiState.update { it.copy(isLoading = true, hasError = false) }
        job = viewModelScope.launch {
            try {
                repository.observeBoards().collect { boards ->
                    _uiState.update { it.copy(isLoading = false, hasError = false, boards = boards) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 不将旧成绩冒充当前目录；加载/错误使用独立滚动状态。
                _uiState.update { it.copy(isLoading = false, hasError = true, boards = emptyList()) }
            }
        }
    }
}
