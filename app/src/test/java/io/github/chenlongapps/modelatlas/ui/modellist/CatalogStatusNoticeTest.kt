package io.github.chenlongapps.modelatlas.ui.modellist

import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogStatusNoticeTest {
    private val idle = ModelListUiState(isLoading = false)

    @Test fun idleCatalogDoesNotReserveNoticeSpace() {
        assertFalse(idle.hasCatalogNotice())
    }

    @Test fun onlyExplicitResultsAndErrorsHaveAnInlineNotice() {
        ModelListMessage.entries.forEach { assertTrue(idle.copy(message = it).hasCatalogNotice()) }
        ModelListError.entries.forEach { assertTrue(idle.copy(refreshError = it).hasCatalogNotice()) }
    }

    @Test fun refreshingNeverReservesInlineNoticeSpace() {
        ModelListMessage.entries.forEach { assertFalse(idle.copy(isRefreshing = true, message = it).hasCatalogNotice()) }
        ModelListError.entries.forEach { assertFalse(idle.copy(isRefreshing = true, refreshError = it).hasCatalogNotice()) }
    }

    @Test fun initialLoadingAndBlockingErrorsUseTheirExistingStateCardsInstead() {
        val failed = idle.copy(refreshError = ModelListError.NETWORK)
        assertFalse(failed.copy(isLoading = true).hasCatalogNotice())
        ModelListError.entries.forEach { assertFalse(failed.copy(error = it).hasCatalogNotice()) }
    }

    @Test fun dismissingResultsLeavesNoNoticeOrAnimationTarget() {
        val failed = idle.copy(refreshError = ModelListError.STORAGE)
        assertFalse(failed.copy(message = null, refreshError = null).hasCatalogNotice())
        assertEquals(null, idle.catalogNotice())
        assertEquals(null, idle.copy(isRefreshing = true, message = ModelListMessage.UPDATED).catalogNotice())
    }

    @Test fun animationTargetOnlyChangesWhenNoticeContentChanges() {
        val failed = idle.copy(refreshError = ModelListError.NETWORK)
        assertEquals(CatalogNotice(ModelListError.NETWORK), failed.catalogNotice())
        assertEquals(failed.catalogNotice(), failed.copy(models = listOf(ModelSummary("lab/new", "新模型"))).catalogNotice())
    }
}
