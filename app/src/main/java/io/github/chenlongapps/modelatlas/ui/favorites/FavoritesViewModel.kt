package io.github.chenlongapps.modelatlas.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chenlongapps.modelatlas.data.model.FavoriteModel
import io.github.chenlongapps.modelatlas.data.repository.FavoriteRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val isLoading: Boolean = true,
    val favorites: List<FavoriteModel> = emptyList(),
    val hasError: Boolean = false,
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(private val repository: FavoriteRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState = _uiState.asStateFlow()
    private var observation: Job? = null

    init { retry() }

    fun retry() {
        if (observation?.isActive == true) return
        _uiState.value = FavoritesUiState()
        observation = viewModelScope.launch {
            try {
                repository.observeFavorites().collect { _uiState.value = FavoritesUiState(false, it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = FavoritesUiState(isLoading = false, hasError = true)
            }
        }
    }
}
