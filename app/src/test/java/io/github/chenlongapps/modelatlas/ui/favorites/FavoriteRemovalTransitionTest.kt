package io.github.chenlongapps.modelatlas.ui.favorites

import io.github.chenlongapps.modelatlas.data.model.FavoriteIdentity
import io.github.chenlongapps.modelatlas.data.model.FavoriteModel
import org.junit.Assert.*
import org.junit.Test

class FavoriteRemovalTransitionTest {
    private val first = favorite("lab/a")
    private val second = favorite("lab/b")
    private val third = favorite("lab/c")

    @Test fun onlyExplicitRemovalRetainsOneSourceAtItsOriginalPositionWithoutFreezingOtherUpdates() {
        val transition = FavoriteRemovalTransition()
        transition.source = { RetainedFavorite(second, 1) }
        assertEquals(listOf(first, third), transition.displayFavorites(listOf(first, third)))
        transition.begin(second.identity.id)
        assertTrue(transition.awaitingReturn)
        assertEquals(listOf(first, second, third), transition.displayFavorites(listOf(first, third)))
        val updated = third.copy(identity = third.identity.copy(name = "Updated"))
        assertEquals(listOf(first, second, updated), transition.displayFavorites(listOf(first, updated)))
        transition.finishReturn()
        assertFalse(transition.awaitingReturn)
        assertNotNull("转场结束后仍要等卡片自己的淡出完成", transition.retained)
        transition.clear(second.identity.id)
        assertEquals(listOf(first, updated), transition.displayFavorites(listOf(first, updated)))
    }

    @Test fun liveSourceWinsOverSnapshotAndDelayedListEmissionDoesNotReleaseItEarly() {
        val transition = FavoriteRemovalTransition()
        transition.source = { RetainedFavorite(first, 0) }
        transition.begin(first.identity.id)
        val updated = first.copy(identity = first.identity.copy(name = "Latest identity"))
        assertEquals(listOf(updated), transition.displayFavorites(listOf(updated)))
        transition.finishReturn()
        assertNotNull(transition.retained)
        assertEquals(listOf(first), transition.displayFavorites(emptyList()))
        transition.clear(first.identity.id)
        assertTrue(transition.displayFavorites(emptyList()).isEmpty())
    }

    @Test fun failureClearsRetentionAndMissingSourceDoesNotCreateAFakeCard() {
        val transition = FavoriteRemovalTransition()
        transition.begin(first.identity.id)
        assertNull(transition.retained)
        assertFalse(transition.awaitingReturn)
        transition.source = { RetainedFavorite(second, 5) }
        transition.begin(second.identity.id)
        assertEquals(listOf(first, second), transition.displayFavorites(listOf(first)))
        transition.clear("another/id")
        assertNotNull(transition.retained)
        transition.clear(second.identity.id)
        assertNull(transition.retained)
        assertFalse(transition.awaitingReturn)
    }

    private fun favorite(id: String) = FavoriteModel(FavoriteIdentity(id, id, "lab"), 1, null)
}
