package io.github.chenlongapps.modelatlas.ui.modeldetail

import io.github.chenlongapps.modelatlas.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderLogoTest {
    @Test fun knownProviderIdsUseTheirOwnAssets() {
        mapOf(
            "openrouter" to R.drawable.ic_provider_openrouter,
            "openai" to R.drawable.ic_provider_openai,
            "anthropic" to R.drawable.ic_provider_anthropic,
            "google" to R.drawable.ic_provider_google,
            "deepseek" to R.drawable.ic_provider_deepseek,
            "amazon-bedrock" to R.drawable.ic_provider_amazon_bedrock,
            "google-vertex" to R.drawable.ic_provider_google_vertex,
            "azure" to R.drawable.ic_provider_azure,
            "togetherai" to R.drawable.ic_provider_togetherai,
            "fireworks-ai" to R.drawable.ic_provider_fireworks_ai,
            "deepinfra" to R.drawable.ic_provider_deepinfra,
            "siliconflow" to R.drawable.ic_provider_siliconflow,
            "novita-ai" to R.drawable.ic_provider_novita_ai,
            "huggingface" to R.drawable.ic_provider_huggingface,
            "302ai" to R.drawable.ic_provider_302ai,
        ).forEach { (id, res) ->
            assertEquals(id, res, providerLogoResOrNull(id))
            assertEquals(id, res, providerLogoRes(id))
        }
    }

    @Test fun onlyExplicitlyCollectedIdenticalVariantsShareAssets() {
        mapOf(
            "azure-cognitive-services" to "azure",
            "alibaba-coding-plan" to "alibaba",
            "minimax-cn" to "minimax",
            "moonshotai-cn" to "moonshotai",
            "opencode-go" to "opencode",
            "siliconflow-cn" to "siliconflow",
            "xiaomi-token-plan-cn" to "xiaomi",
            "zhipuai" to "zai",
        ).forEach { (variant, base) ->
            assertEquals(variant, providerLogoRes(base), providerLogoRes(variant))
        }
        assertNotEquals(providerLogoRes("alibaba"), providerLogoRes("alibaba-cn"))
        assertNotEquals(providerLogoRes("google"), providerLogoRes("google-vertex"))
        assertNotEquals(providerLogoRes("openai"), providerLogoRes("openrouter"))
    }

    @Test fun unknownMissingDisplayNamesAndModelIdsDoNotInferAnotherBrand() {
        listOf(null, "", " ", "unknown", "OpenAI", "OpenRouter", "openai/example",
            "openai-unknown", " openai ", "fireworks").forEach(::assertFallback)
    }

    @Test fun upstreamGenericImagesUseTheNeutralFallback() {
        listOf("alibaba-token-plan-cn", "chutes", "cline-pass", "cortecs", "gmicloud", "morph",
            "requesty", "sakana", "sap-ai-core", "sarvam", "synthetic", "upstage").forEach(::assertFallback)
    }

    @Test fun unsupportedSvgFeaturesDoNotProducePartialOrBrokenLogos() {
        listOf("abliteration-ai", "atomic-chat", "hpc-ai", "kenari", "orcarouter",
            "umans-ai", "umans-ai-coding-plan", "wafer.ai").forEach(::assertFallback)
    }

    private fun assertFallback(id: String?) {
        assertNull(id, providerLogoResOrNull(id))
        assertEquals(id, R.drawable.ic_provider_logo_fallback, providerLogoRes(id))
    }
}
