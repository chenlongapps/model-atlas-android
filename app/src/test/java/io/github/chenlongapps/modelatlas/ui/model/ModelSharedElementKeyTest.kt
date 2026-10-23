package io.github.chenlongapps.modelatlas.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ModelSharedElementKeyTest {
    @Test fun matchesOnlyTheSameCanonicalModelAndIdentityRole() {
        val logo = ModelSharedElementKey("openai/model-a", ModelIdentityRole.LOGO)
        assertEquals(logo, ModelSharedElementKey("openai/model-a", ModelIdentityRole.LOGO))
        assertEquals(logo.hashCode(), ModelSharedElementKey("openai/model-a", ModelIdentityRole.LOGO).hashCode())
        assertNotEquals(logo, ModelSharedElementKey("openai/model-b", ModelIdentityRole.LOGO))
        ModelIdentityRole.entries.filterNot { it == ModelIdentityRole.LOGO }.forEach { role ->
            assertNotEquals(logo, ModelSharedElementKey("openai/model-a", role))
        }
    }

    @Test fun identityKeysNeverMatchTheirOuterContainer() {
        ModelIdentityRole.entries.forEach { role ->
            assertNotEquals(ModelContainerKey("openai/model-a"), ModelSharedElementKey("openai/model-a", role))
        }
    }
}
