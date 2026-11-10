package io.github.chenlongapps.modelatlas.ui.benchmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chenlongapps.modelatlas.data.model.BenchmarkBoard
import io.github.chenlongapps.modelatlas.data.repository.BenchmarkRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BenchmarkUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val boards: List<BenchmarkBoard> = emptyList(),
)

/** 顶层页/排行榜可见时才观察本地数据；离屏不读库，不增加网络同步入口。 */
@HiltViewModel
class BenchmarkViewModel @Inject constructor(private val repository: BenchmarkRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(BenchmarkUiState())
    val uiState = _uiState.asStateFlow()
    private var active = false
    private var job: Job? = null

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value) observe() else job?.cancel()
    }

    fun retry() {
        if (active && job?.isActive != true) observe()
    }

    private fun observe() {
        _uiState.value = BenchmarkUiState()
        job = viewModelScope.launch {
            try {
                repository.observeBoards().collect { _uiState.value = BenchmarkUiState(isLoading = false, boards = it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 不将旧成绩冒充当前目录；加载/错误使用独立滚动状态。
                _uiState.value = BenchmarkUiState(isLoading = false, hasError = true)
            }
        }
    }
}
