package io.github.chenlongapps.modelatlas.data.mapper

import io.github.chenlongapps.modelatlas.data.local.ModelDetailOfferingRecord
import io.github.chenlongapps.modelatlas.data.local.ModelDetailRecord
import io.github.chenlongapps.modelatlas.data.local.entity.ModelEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ModelOfferingEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ProviderEntity
import io.github.chenlongapps.modelatlas.data.local.model.ExperimentalModeLocal
import io.github.chenlongapps.modelatlas.data.local.model.InterleavedLocal
import io.github.chenlongapps.modelatlas.data.local.model.ModelLinkLocal
import io.github.chenlongapps.modelatlas.data.local.model.ModelWeightLocal
import io.github.chenlongapps.modelatlas.data.local.model.OfferingPriceTierLocal
import io.github.chenlongapps.modelatlas.data.local.model.OfferingPricingLocal
import io.github.chenlongapps.modelatlas.data.local.model.PriceTierConditionLocal
import io.github.chenlongapps.modelatlas.data.local.model.ReasoningOptionLocal
import org.junit.Assert.*
import org.junit.Test

class ModelDetailMapperTest {
    private val model = ModelEntity(
        "openai/canonical", "remote-id", "Canonical", 100, type = "llm",
        contextLimitTokens = 5_000_000_000L, inputModalities = listOf("text", "future"),
        openWeights = true, license = "A complete license name longer than sixteen characters",
    )

    @Test fun retainsCompleteMetadataAndSeparateServiceValues() {
        val detail = ModelDetailRecord(model, listOf(service("openai", "s"))).toModelDetail()!!
        assertEquals("openai/canonical", detail.id)
        assertEquals("openai", detail.lab)
        assertEquals(5_000_000_000L, detail.metadata.contextLimitTokens)
        assertEquals(model.license, detail.license)
        val offering = detail.providers.single().offerings.single()
        assertNull(offering.metadata.contextLimitTokens)
        assertNull(offering.metadata.reasoning)
        assertEquals(false, offering.metadata.openWeights)
        assertNull(offering.price)
        assertEquals(200L, offering.fetchedAtEpochMillis)
    }

    @Test fun keepsEveryServiceIncludingMissingPricesAndUsesStableProviderOrder() {
        val records = listOf(service("zzz", "a"), service("openrouter", "b"), service("openai", "c"),
            service("openai", "a"), service("aaa", "a"))
        val detail = ModelDetailRecord(model, records.reversed()).toModelDetail()!!
        assertEquals(listOf("openai", "openrouter", "aaa", "zzz"), detail.providers.map { it.id })
        assertEquals(listOf("a", "c"), detail.providers.first().offerings.map { it.id })
        assertEquals(5, detail.providers.sumOf { it.offerings.size })
    }

    @Test fun neverGuessesAssociationsFromNamesOrOfferingIds() {
        val record = service("openai", model.id)
        val detail = ModelDetailRecord(model, listOf(
            record.copy(offering = record.offering.copy(canonicalModelId = null)),
            record.copy(offering = record.offering.copy(canonicalModelId = "other")),
        )).toModelDetail()!!
        assertTrue(detail.providers.isEmpty())
    }

    @Test fun blankProviderNameDoesNotFallBackToProviderIdAndServiceUsesItsStableKey() {
        val service = service("provider-id", "stable-service-key").let {
            it.copy(provider = it.provider.copy(name = " "))
        }
        val provider = ModelDetailRecord(model, listOf(service)).toModelDetail()!!.providers.single()
        assertTrue(provider.name.isBlank())
        assertEquals("provider-id", provider.id)
        assertEquals("stable-service-key", provider.offerings.single().id)
        assertNotEquals(service.offering.remoteId, provider.offerings.single().id)
    }

    @Test fun preservesZeroHighPrecisionEveryTierCategoryAndIndependentModes() {
        val price = OfferingPricingLocal(input = 0.0, output = 0.003, reasoning = 2.0,
            tiers = listOf(OfferingPriceTierLocal(PriceTierConditionLocal("context", 272_000),
                input = 4.0, inputAudio = 5.0, outputAudio = 6.0, reasoning = 7.0)),
            contextOver200k = OfferingPricingLocal(input = 999.0))
        val service = service("openai", "s").let { it.copy(offering = it.offering.copy(
            pricing = price, experimentalModes = mapOf("fast" to ExperimentalModeLocal(OfferingPricingLocal(input = 8.0))),
        )) }
        val offering = ModelDetailRecord(model, listOf(service)).toModelDetail()!!.providers.single().offerings.single()
        assertEquals(0.0, offering.price!!.input)
        assertEquals(0.003, offering.price.output)
        assertEquals(272_000L, offering.price.tiers!!.single().size)
        assertEquals(5.0, offering.price.tiers.single().price.inputAudio)
        assertEquals(6.0, offering.price.tiers.single().price.outputAudio)
        assertEquals(7.0, offering.price.tiers.single().price.reasoning)
        assertEquals(8.0, offering.modes!!["fast"]!!.input)
        assertEquals(1, offering.price.tiers.size)
    }

    @Test fun retainsNullEmptyFalseAndRawReasoningChoices() {
        val s = service("openai", "s").let { it.copy(offering = it.offering.copy(
            reasoningOptions = listOf(ReasoningOptionLocal("future", values = listOf(null, "high"))),
            interleaved = InterleavedLocal.Field("true"),
        )) }
        val detail = ModelDetailRecord(model.copy(inputModalities = emptyList(), links = emptyList()), listOf(s)).toModelDetail()!!
        assertEquals(emptyList<String>(), detail.metadata.inputModalities)
        assertNull(detail.metadata.outputModalities)
        assertEquals(emptyList<Any>(), detail.links)
        assertNull(detail.weights)
        val offering = detail.providers.single().offerings.single()
        assertEquals(listOf(null, "high"), offering.reasoningOptions!!.single().values)
        assertEquals("true", offering.interleaved!!.field)
        assertNull(offering.interleaved.supported)
    }

    @Test fun retainsWeightAndReferenceLinksAndFallsBackToStableIds() {
        val detail = ModelDetailRecord(model.copy(name = " ", description = " ",
            weights = listOf(ModelWeightLocal("https://example.org/weight", "Weights", "Q4", "GGUF")),
            links = listOf(ModelLinkLocal("https://example.org/paper", "Paper", "paper"))), emptyList()).toModelDetail()!!
        assertEquals(model.id, detail.name)
        assertNull(detail.metadata.description)
        assertEquals("GGUF", detail.weights!!.single().format)
        assertEquals("paper", detail.links!!.single().type)
        assertEquals(100L, detail.fetchedAtEpochMillis)
    }

    @Test fun usesTheSameInclusionRulesAsTheList() {
        assertNull(ModelDetailRecord(model.copy(type = "embedding"), emptyList()).toModelDetail())
        assertNull(ModelDetailRecord(model.copy(type = null, inputModalities = null), emptyList()).toModelDetail())
        assertNotNull(ModelDetailRecord(model.copy(type = null, inputModalities = listOf("text"),
            outputModalities = listOf("text")), emptyList()).toModelDetail())
    }

    @Test fun detailDoesNotUseUnverifiedBenchmarkData() {
        assertNull(ModelDetailRecord(model, emptyList()).toModelDetail()!!.benchmarks)
    }

    private fun service(provider: String, id: String) = ModelDetailOfferingRecord(
        ModelOfferingEntity(provider, id, "remote-service", "Service", 200,
            canonicalModelId = model.id, openWeights = false),
        ProviderEntity(provider, "remote-provider", provider, 200),
    )
}
