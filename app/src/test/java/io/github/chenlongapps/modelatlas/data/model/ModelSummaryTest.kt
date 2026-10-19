package io.github.chenlongapps.modelatlas.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelSummaryTest {
    @Test fun labUsesOnlyTheFirstStableIdSegment() {
        assertEquals("lab", ModelSummary("lab/model/version", "Model").lab)
        assertEquals("other", ModelSummary("lab/model", "Model").copy(id = "other/model").lab)
    }

    @Test fun missingOrBlankPrefixIsUnknown() {
        listOf("model", "", "/model", " /model").forEach { id ->
            assertNull(ModelSummary(id, "Model").lab)
        }
    }
}
