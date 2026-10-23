package io.github.chenlongapps.modelatlas.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ModelContainerKeyTest {
    @Test fun matchesOnlyTheSameCanonicalModelNotAnotherModelFromTheSameLab() {
        val card = ModelContainerKey("openai/model-a")
        assertEquals(card, ModelContainerKey("openai/model-a"))
        assertEquals(card.hashCode(), ModelContainerKey("openai/model-a").hashCode())
        assertNotEquals(card, ModelContainerKey("openai/model-b"))
    }
}
