package io.github.chenlongapps.modelatlas.data.mapper

import io.github.chenlongapps.modelatlas.data.local.ModelListRow
import io.github.chenlongapps.modelatlas.data.local.ModelOfferingPriceRow
import io.github.chenlongapps.modelatlas.data.local.model.OfferingPricingLocal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelListMapperTest {
    @Test fun emitsOneSummaryPerCanonicalModelRegardlessOfProviders() {
        val models = listOf(
            row("a", "p1", "First"), row("a", "p1", "First"), row("a", "p2", "Second"),
            row("b", null, null),
        ).toModelSummaries()
        assertEquals(listOf("a", "b"), models.map { it.id })
        assertNull(models[1].releaseDate)
        assertNull(models[1].contextLimitTokens)
        assertNull(models[1].outputLimitTokens)
    }

    @Test fun doesNotMergeModelsWithSameName() {
        val models = listOf(row("a", null, null), row("b", "p", " ")).toModelSummaries()
        assertEquals(2, models.size)
        assertEquals("Same name", models[0].name)
        assertEquals("Same name", models[1].name)
    }

    @Test fun mapsCanonicalMetadataWithoutLosingPrecisionOrModalityDirection() {
        val model = listOf(row("lab/model/version", "api-provider", "API").copy(
            releaseDate = "2026-10",
            contextLimitTokens = 5_000_000_000L,
            outputLimitTokens = 0L,
            inputModalities = listOf("text", "image", "future-modality"),
            outputModalities = listOf("text", "audio"),
        )).toModelSummaries().single()
        assertEquals("lab", model.lab)
        assertEquals("2026-10", model.releaseDate)
        assertEquals(5_000_000_000L, model.contextLimitTokens)
        assertEquals(0L, model.outputLimitTokens)
        assertEquals(listOf("text", "image", "future-modality"), model.inputModalities)
        assertEquals(listOf("text", "audio"), model.outputModalities)
    }

    @Test fun preservesUnknownAndEmptyModalitiesAndNormalizesBlankDate() {
        val model = listOf(row("lab/model", null, null).copy(
            type = "llm",
            releaseDate = " ",
            inputModalities = null,
            outputModalities = emptyList(),
        )).toModelSummaries().single()
        assertNull(model.releaseDate)
        assertNull(model.inputModalities)
        assertEquals(emptyList<String>(), model.outputModalities)
    }

    @Test fun appliesFilterAndUsesStableIdForBlankName() {
        val source = row("a", null, null)
        val models = listOf(
            source.copy(name = " "),
            source.copy(id = "embedding", type = "embedding"),
            source.copy(id = "unknown", inputModalities = null),
        ).toModelSummaries()
        assertEquals(listOf("a"), models.map { it.id })
        assertEquals("a", models.single().name)
    }

    @Test fun ordersByReleaseDateNewestFirstKeepingSourcePrecision() {
        val models = listOf(
            row("lab/old", null, null).copy(releaseDate = "2025"),
            row("lab/month", null, null).copy(releaseDate = "2025-06"),
            row("lab/day", null, null).copy(releaseDate = "2025-06-01"),
            row("lab/newest", null, null).copy(releaseDate = "2026-01-15"),
        ).toModelSummaries()
        assertEquals(
            listOf("lab/newest", "lab/day", "lab/month", "lab/old"),
            models.map { it.id },
        )
    }

    @Test fun placesMissingAndBlankReleaseDatesLast() {
        val models = listOf(
            row("lab/missing", null, null),
            row("lab/dated", null, null).copy(releaseDate = "2026-01"),
            row("lab/blank", null, null).copy(releaseDate = " "),
        ).toModelSummaries()
        assertEquals(
            listOf("lab/dated", "lab/blank", "lab/missing"),
            models.map { it.id },
        )
    }

    @Test fun breaksDateTiesByNameIgnoringCaseThenByStableId() {
        val models = listOf(
            row("x/one", null, null).copy(name = "Zeta", releaseDate = "2026-01"),
            row("x/two", null, null).copy(name = "alpha", releaseDate = "2026-01"),
            row("x/three", null, null).copy(name = "Alpha", releaseDate = "2026-01"),
        ).toModelSummaries()
        assertEquals(listOf("x/three", "x/two", "x/one"), models.map { it.id })
    }

    @Test fun mapsOpenWeightsThreeStatesAndBlankLicenseAsMissing() {
        val models = listOf(
            row("lab/open", null, null).copy(openWeights = true, license = "MIT"),
            row("lab/closed", null, null).copy(openWeights = false, license = "proprietary"),
            row("lab/unknown", null, null),
            row("lab/blank-license", null, null).copy(openWeights = true, license = "  "),
        ).toModelSummaries()
        val byId = models.associateBy { it.id }
        assertEquals(true, byId.getValue("lab/open").openWeights)
        assertEquals("MIT", byId.getValue("lab/open").license)
        assertEquals(false, byId.getValue("lab/closed").openWeights)
        assertEquals("proprietary", byId.getValue("lab/closed").license)
        assertNull(byId.getValue("lab/unknown").openWeights)
        assertNull(byId.getValue("lab/unknown").license)
        assertEquals(true, byId.getValue("lab/blank-license").openWeights)
        assertNull(byId.getValue("lab/blank-license").license)
    }

    @Test fun attachesSelectedPriceFromSeparateProjection() {
        val priceRows = mapOf(
            "openai/gpt-x" to listOf(
                ModelOfferingPriceRow("openai/gpt-x", "openrouter", "OpenRouter", "gpt-x", OfferingPricingLocal(input = 0.5, output = 1.5)),
                ModelOfferingPriceRow("openai/gpt-x", "openai", "OpenAI", "gpt-x", OfferingPricingLocal(input = 2.0, output = 10.0)),
            ),
            "microsoft/phi-x" to listOf(
                ModelOfferingPriceRow("microsoft/phi-x", "novita-ai", "Novita AI", "phi-x", OfferingPricingLocal(input = 0.1, output = 0.2)),
            ),
        )
        val models = listOf(
            row("openai/gpt-x", "openrouter", "OpenRouter"),
            row("microsoft/phi-x", "novita-ai", "Novita AI"),
            row("lab/no-price", "p", "P"),
        ).toModelSummaries(priceRows)
        val byId = models.associateBy { it.id }
        val official = byId.getValue("openai/gpt-x").price
        assertEquals("openai", official?.providerId)
        assertTrue(official?.isOfficial == true)
        assertEquals(2.0, official?.input!!, 0.0)

        val thirdParty = byId.getValue("microsoft/phi-x").price
        assertEquals("novita-ai", thirdParty?.providerId)
        assertFalse(thirdParty?.isOfficial == true)

        assertNull(byId.getValue("lab/no-price").price)
    }

    @Test fun priceRowsForUnknownModelsAreIgnored() {
        val priceRows = mapOf(
            "not-in-rows" to listOf(
                ModelOfferingPriceRow("not-in-rows", "p", "P", "m", OfferingPricingLocal(input = 1.0, output = 2.0)),
            ),
        )
        val models = listOf(row("lab/model", null, null)).toModelSummaries(priceRows)
        assertNull(models.single().price)
    }

    private fun row(id: String, providerId: String?, providerName: String?) = ModelListRow(
        id, "Same name", " ", null, listOf("text"), listOf("text"), providerId, providerName,
    )
}