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

    @Test fun nonBlockingResultsAndPendingUpdatesHaveAnInlineNotice() {
        listOf(
            idle.copy(updateAvailable = true),
            idle.copy(message = ModelListMessage.UPDATED),
            idle.copy(message = ModelListMessage.UP_TO_DATE),
        ).forEach { assertTrue(it.hasCatalogNotice()) }
        ModelListError.entries.forEach { assertTrue(idle.copy(refreshError = it).hasCatalogNotice()) }
    }

    @Test fun refreshingNeverReservesInlineNoticeSpace() {
        listOf(
            idle,
            idle.copy(updateAvailable = true),
            idle.copy(message = ModelListMessage.UPDATED),
            idle.copy(message = ModelListMessage.UP_TO_DATE),
        ).forEach { assertFalse(it.copy(isRefreshing = true).hasCatalogNotice()) }
        ModelListError.entries.forEach {
            assertFalse(idle.copy(isRefreshing = true, refreshError = it).hasCatalogNotice())
        }
    }

    @Test fun initialLoadingAndBlockingErrorsUseTheirExistingStateCardsInstead() {
        val pending = idle.copy(updateAvailable = true, refreshError = ModelListError.NETWORK)
        assertFalse(pending.copy(isLoading = true).hasCatalogNotice())
        ModelListError.entries.forEach { assertFalse(pending.copy(error = it).hasCatalogNotice()) }
    }

    @Test fun dismissingResultsDoesNotLoseThePendingUpdate() {
        val pending = idle.copy(updateAvailable = true, refreshError = ModelListError.STORAGE)
        assertTrue(pending.copy(message = null, refreshError = null).hasCatalogNotice())
        assertFalse(idle.copy(message = null, refreshError = null).hasCatalogNotice())
    }

    @Test fun hiddenStatesHaveNoAnimationTarget() {
        assertEquals(null, idle.catalogNotice())
        assertEquals(null, idle.copy(isRefreshing = true, updateAvailable = true).catalogNotice())
        assertEquals(null, idle.copy(isLoading = true, message = ModelListMessage.UPDATED).catalogNotice())
    }

    @Test fun animationTargetOnlyChangesWhenNoticeContentChanges() {
        val pending = idle.copy(updateAvailable = true, refreshError = ModelListError.NETWORK)
        assertEquals(CatalogNotice(ModelListError.NETWORK, updateAvailable = true), pending.catalogNotice())
        assertEquals(pending.catalogNotice(), pending.copy(
            models = listOf(ModelSummary("lab/new", "新模型")),
        ).catalogNotice())
    }
}
