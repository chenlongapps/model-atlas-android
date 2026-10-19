package io.github.chenlongapps.modelatlas.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmInclusionPolicyTest {
    @Test fun acceptsExplicitLlmAndTextGeneratingMultimodalModels() {
        assertTrue(LlmInclusionPolicy.includes("llm", null, null))
        assertTrue(LlmInclusionPolicy.includes(null, listOf("text", "image"), listOf("text", "image")))
    }

    @Test fun excludesNonLlmUnknownTypesAndMissingModalities() {
        listOf("embedding", "reranking", "decision", "video", "future", "").forEach {
            assertFalse(LlmInclusionPolicy.includes(it, listOf("text"), listOf("text")))
        }
        assertFalse(LlmInclusionPolicy.includes(null, null, listOf("text")))
        assertFalse(LlmInclusionPolicy.includes(null, listOf("text"), null))
        assertFalse(LlmInclusionPolicy.includes(null, listOf("audio"), listOf("text")))
        assertFalse(LlmInclusionPolicy.includes(null, listOf("text"), listOf("image")))
        assertFalse(LlmInclusionPolicy.includes(null, emptyList(), emptyList()))
    }
}
