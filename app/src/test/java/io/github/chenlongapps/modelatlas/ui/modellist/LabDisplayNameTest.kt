package io.github.chenlongapps.modelatlas.ui.modellist

import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class LabDisplayNameTest {
    @Test fun knownLabsUseBrandSpelling() {
        val expected = mapOf(
            "ai21" to "AI21 Labs",
            "alibaba" to "Alibaba",
            "anthropic" to "Anthropic",
            "cohere" to "Cohere",
            "deepseek" to "DeepSeek",
            "google" to "Google",
            "inception" to "Inception",
            "meta" to "Meta",
            "minimax" to "MiniMax",
            "mistral" to "Mistral AI",
            "moonshotai" to "Moonshot AI",
            "nvidia" to "NVIDIA",
            "openai" to "OpenAI",
            "perplexity" to "Perplexity",
            "poolside" to "poolside",
            "reflection" to "Reflection",
            "stepfun" to "StepFun",
            "thinkingmachines" to "Thinking Machines",
            "vispark" to "Vispark",
            "vivgrid" to "Vivgrid",
            "xai" to "xAI",
            "xiaomi" to "Xiaomi",
            "zhipuai" to "Zhipu AI",
        )
        expected.forEach { (lab, name) -> assertEquals(name, labDisplayName(lab)) }
    }

    @Test fun unknownAndAlreadyFormattedLabsKeepOriginalText() {
        listOf("acme", "openai-2", "OpenAI", "CustomLAB", "", " ").forEach { lab ->
            assertEquals(lab, labDisplayName(lab))
        }
    }

    @Test fun displayNameDoesNotChangeLabIdOrFiltering() {
        val model = ModelSummary("openai/gpt-5", "GPT-5")
        assertEquals("OpenAI", labDisplayName(requireNotNull(model.lab)))
        assertEquals("openai", model.lab)
        assertEquals(listOf(model), filterModels(listOf(model), ModelListQuery(labs = setOf("openai"))))
        assertEquals(listOf(model), filterModels(listOf(model), ModelListQuery(search = "OpenAI")))
    }
}
