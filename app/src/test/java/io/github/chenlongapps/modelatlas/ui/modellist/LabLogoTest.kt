package io.github.chenlongapps.modelatlas.ui.modellist

import io.github.chenlongapps.modelatlas.R
import org.junit.Assert.assertEquals
import org.junit.Test

class LabLogoTest {
    @Test fun knownLabsMapToTheirOwnLogo() {
        val expected = mapOf(
            "ai21" to R.drawable.ic_lab_ai21,
            "alibaba" to R.drawable.ic_lab_alibaba,
            "anthropic" to R.drawable.ic_lab_anthropic,
            "cohere" to R.drawable.ic_lab_cohere,
            "deepseek" to R.drawable.ic_lab_deepseek,
            "google" to R.drawable.ic_lab_google,
            "inception" to R.drawable.ic_lab_inception,
            "meta" to R.drawable.ic_lab_meta,
            "minimax" to R.drawable.ic_lab_minimax,
            "mistral" to R.drawable.ic_lab_mistral,
            "moonshotai" to R.drawable.ic_lab_moonshotai,
            "nvidia" to R.drawable.ic_lab_nvidia,
            "openai" to R.drawable.ic_lab_openai,
            "perplexity" to R.drawable.ic_lab_perplexity,
            "poolside" to R.drawable.ic_lab_poolside,
            "reflection" to R.drawable.ic_lab_reflection,
            "stepfun" to R.drawable.ic_lab_stepfun,
            "thinkingmachines" to R.drawable.ic_lab_thinkingmachines,
            "vispark" to R.drawable.ic_lab_vispark,
            "vivgrid" to R.drawable.ic_lab_vivgrid,
            "xai" to R.drawable.ic_lab_xai,
            "xiaomi" to R.drawable.ic_lab_xiaomi,
            "zhipuai" to R.drawable.ic_lab_zhipuai,
        )
        expected.forEach { (lab, res) ->
            assertEquals(res, labLogoResOrNull(lab))
            assertEquals(res, labLogoRes(lab))
        }
        assertEquals(23, expected.size)
    }

    @Test fun unknownLabDoesNotBorrowAnotherLogo() {
        listOf("acme", "openai-2", "OpenAI", "unknown").forEach { lab ->
            assertEquals(null, labLogoResOrNull(lab))
            assertEquals(R.drawable.ic_lab_logo_fallback, labLogoRes(lab))
        }
    }

    @Test fun missingLabUsesFallback() {
        assertEquals(null, labLogoResOrNull(null))
        assertEquals(null, labLogoResOrNull(""))
        assertEquals(R.drawable.ic_lab_logo_fallback, labLogoRes(null))
        assertEquals(R.drawable.ic_lab_logo_fallback, labLogoRes(""))
    }

    @Test fun labComesFromStableIdPrefix() {
        // LAB 由稳定 ID 前缀派生，因此前缀决定 logo，名称不影响选取。
        assertEquals(R.drawable.ic_lab_openai, labLogoRes("openai/gpt-5".substringBefore('/')))
        assertEquals(
            R.drawable.ic_lab_logo_fallback,
            labLogoRes("unlinked".substringBefore('/', missingDelimiterValue = "")),
        )
    }
}
