package io.github.chenlongapps.modelatlas.ui.modellist

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chenlongapps.modelatlas.data.model.AiModel
import io.github.chenlongapps.modelatlas.data.repository.ModelRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ModelListUiState(
    val models: List<AiModel> = emptyList(),
)

@HiltViewModel
class ModelListViewModel @Inject constructor(
    modelRepository: ModelRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ModelListUiState(models = modelRepository.getModels()),
    )
    val uiState: StateFlow<ModelListUiState> = _uiState.asStateFlow()
}
