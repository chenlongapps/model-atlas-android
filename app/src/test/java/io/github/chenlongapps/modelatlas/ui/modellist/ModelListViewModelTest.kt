package io.github.chenlongapps.modelatlas.ui.modellist

import io.github.chenlongapps.modelatlas.data.repository.ModelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ModelListViewModelTest {
    @Test
    fun initialStateContainsRepositoryModels() {
        val repository = ModelRepository()
        val viewModel = ModelListViewModel(repository)

        assertEquals(repository.getModels(), viewModel.uiState.value.models)
        assertFalse(viewModel.uiState.value.models.isEmpty())
    }

    @Test
    fun uiStateDoesNotExposeMutableStateFlow() {
        val viewModel = ModelListViewModel(ModelRepository())

        assertFalse(viewModel.uiState is MutableStateFlow<*>)
    }
}
