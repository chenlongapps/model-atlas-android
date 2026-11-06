package io.github.chenlongapps.modelatlas.data.local.converter

import io.github.chenlongapps.modelatlas.data.local.model.ExperimentalModeLocal
import io.github.chenlongapps.modelatlas.data.local.model.InterleavedLocal
import io.github.chenlongapps.modelatlas.data.local.model.ModelLinkLocal
import io.github.chenlongapps.modelatlas.data.local.model.ModelWeightLocal
import io.github.chenlongapps.modelatlas.data.local.model.ModelParametersLocal
import io.github.chenlongapps.modelatlas.data.local.model.ModelParameterComponentLocal
import io.github.chenlongapps.modelatlas.data.local.model.OfferingPriceTierLocal
import io.github.chenlongapps.modelatlas.data.local.model.OfferingPricingLocal
import io.github.chenlongapps.modelatlas.data.local.model.PriceTierConditionLocal
import io.github.chenlongapps.modelatlas.data.local.model.ProviderOverrideLocal
import io.github.chenlongapps.modelatlas.data.local.model.ReasoningOptionLocal
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogTypeConvertersTest {
    private val converters = CatalogTypeConverters()

    @Test
    fun nullValuesRoundTripAsSqlNull() {
        assertNull(converters.stringListToJson(null))
        assertNull(converters.jsonToStringList(null))
        assertNull(converters.weightsToJson(null))
        assertNull(converters.jsonToWeights(null))
        assertNull(converters.linksToJson(null))
        assertNull(converters.jsonToLinks(null))
        assertNull(converters.parametersToJson(null))
        assertNull(converters.jsonToParameters(null))
        assertNull(converters.reasoningOptionsToJson(null))
        assertNull(converters.jsonToReasoningOptions(null))
        assertNull(converters.interleavedToJson(null))
        assertNull(converters.jsonToInterleaved(null))
        assertNull(converters.pricingToJson(null))
        assertNull(converters.jsonToPricing(null))
        assertNull(converters.providerOverrideToJson(null))
        assertNull(converters.jsonToProviderOverride(null))
        assertNull(converters.experimentalModesToJson(null))
        assertNull(converters.jsonToExperimentalModes(null))
    }

    @Test
    fun stringListsKeepUnknownValuesAndJsonEscaping() {
        val values = listOf("text", "image", "future-modality", "引号\"与反斜杠\\", "API_KEY")

        assertEquals(values, converters.jsonToStringList(converters.stringListToJson(values)))
        assertEquals("[]", converters.stringListToJson(emptyList()))
        assertEquals(emptyList<String>(), converters.jsonToStringList("[]"))
        assertNull(converters.jsonToStringList("null"))
    }

    @Test
    fun weightsRoundTripAllMetadataAndMissingOptionalFields() {
        val weights = listOf(
            ModelWeightLocal(
                url = "https://example.com/weights",
                label = "Example weights",
                quantization = "fp8",
                format = "safetensors",
            ),
            ModelWeightLocal(url = "https://example.com/other-weights"),
        )

        assertEquals(weights, converters.jsonToWeights(converters.weightsToJson(weights)))
        assertEquals(emptyList<ModelWeightLocal>(), converters.jsonToWeights("[]"))
    }

    @Test
    fun linksRoundTripWithoutInventingLabelsOrTypes() {
        val links = listOf(
            ModelLinkLocal(
                url = "https://example.com/model-card",
                label = "Model card",
                type = "future-link-type",
            ),
            ModelLinkLocal(url = "https://example.com/announcement"),
        )

        assertEquals(links, converters.jsonToLinks(converters.linksToJson(links)))
        assertEquals(emptyList<ModelLinkLocal>(), converters.jsonToLinks("[]"))
    }

    @Test fun parametersRoundTripExactDecimalsProvenanceAndComponentOrder() {
        val source = listOf("https://example.org/model")
        val values = listOf(
            ModelParametersLocal("70.553706496123456789".toBigDecimal(), sources = source),
            ModelParametersLocal(activeBillion = "16".toBigDecimal(), sources = source, notes = "Decode only"),
            ModelParametersLocal(components = listOf(
                ModelParameterComponentLocal("Decoder", "12".toBigDecimal(), sources = source),
                ModelParameterComponentLocal("Encoder", "0.4".toBigDecimal(), sources = source, notes = "Separate component"),
            )),
        )
        values.forEach { assertEquals(it, converters.jsonToParameters(converters.parametersToJson(it))) }
        assertEquals("70.553706496123456789".toBigDecimal(),
            converters.jsonToParameters(converters.parametersToJson(values[0]))!!.totalBillion)
        assertNull(converters.jsonToParameters(converters.parametersToJson(values[1]))!!.totalBillion)
        assertNull(converters.jsonToParameters(converters.parametersToJson(values[2]))!!.sources)
        assertNull(converters.jsonToParameters("null"))
    }

    @Test fun invalidParameterJsonNeverBecomesUnknownOrZero() {
        for (json in listOf("{}", "[]", """{"totalBillion":0,"sources":["https://example.org"]}""",
            """{"totalBillion":"7","sources":["https://example.org"]}""",
            """{"totalBillion":7,"activeBillion":8,"sources":["https://example.org"]}""",
            """{"totalBillion":7}""", """{"components":[{"name":"Decoder","sources":[]}]}""")) {
            assertThrows(Exception::class.java) { converters.jsonToParameters(json) }
        }
    }

    @Test
    fun reasoningOptionsKeepNullableValuesAndLongBudgets() {
        val options = listOf(
            ReasoningOptionLocal(type = "effort", values = listOf(null, "low", "high")),
            ReasoningOptionLocal(type = "budget_tokens", min = 0L, max = 4_294_967_296L),
            ReasoningOptionLocal(type = "toggle"),
            ReasoningOptionLocal(type = "future-option", values = emptyList()),
        )

        assertEquals(
            options,
            converters.jsonToReasoningOptions(converters.reasoningOptionsToJson(options)),
        )
        assertEquals(
            emptyList<ReasoningOptionLocal>(),
            converters.jsonToReasoningOptions(converters.reasoningOptionsToJson(emptyList())),
        )
    }

    @Test
    fun interleavedVariantsUseStableLocalDiscriminators() {
        val values = listOf(
            InterleavedLocal.Toggle(true),
            InterleavedLocal.Toggle(false),
            InterleavedLocal.Field("reasoning_content"),
        )

        values.forEach { value ->
            assertEquals(value, converters.jsonToInterleaved(converters.interleavedToJson(value)))
        }
        assertEquals(
            """{"type":"toggle","enabled":false}""",
            converters.interleavedToJson(InterleavedLocal.Toggle(false)),
        )
        assertEquals(
            """{"type":"field","field":"reasoning_content"}""",
            converters.interleavedToJson(InterleavedLocal.Field("reasoning_content")),
        )
    }

    @Test
    fun pricingKeepsBasePricesExactTierConditionsAndLegacyPricesSeparate() {
        val pricing = OfferingPricingLocal(
            input = 0.0,
            output = 3.0,
            cacheRead = 0.1,
            cacheWrite = 0.2,
            inputAudio = 1.5,
            outputAudio = 2.0,
            reasoning = 4.0,
            tiers = listOf(
                OfferingPriceTierLocal(
                    condition = PriceTierConditionLocal(type = "context", size = 272_000L),
                    input = 1.0,
                    output = 2.0,
                    cacheRead = 0.5,
                    cacheWrite = 0.75,
                    inputAudio = 3.0,
                    outputAudio = 4.0,
                    reasoning = 2.5,
                ),
                OfferingPriceTierLocal(
                    condition = PriceTierConditionLocal(type = "future-tier", size = 4_294_967_296L),
                ),
            ),
            contextOver200k = OfferingPricingLocal(input = 1.0, output = 2.0),
        )

        assertEquals(pricing, converters.jsonToPricing(converters.pricingToJson(pricing)))
    }

    @Test
    fun missingEmptyAndZeroPricingRemainDistinct() {
        val empty = OfferingPricingLocal()
        val zero = OfferingPricingLocal(input = 0.0)
        val emptyTiers = OfferingPricingLocal(tiers = emptyList())

        assertEquals("{}", converters.pricingToJson(empty))
        assertEquals(empty, converters.jsonToPricing("{}"))
        assertEquals(zero, converters.jsonToPricing(converters.pricingToJson(zero)))
        assertEquals(emptyTiers, converters.jsonToPricing(converters.pricingToJson(emptyTiers)))
        assertNotEquals(converters.pricingToJson(empty), converters.pricingToJson(zero))
        assertNotEquals(converters.pricingToJson(empty), converters.pricingToJson(emptyTiers))
        assertNull(converters.jsonToPricing("null"))
    }

    @Test
    fun providerOverridesPreserveArbitraryJsonAndHeaders() {
        val override = ProviderOverrideLocal(
            npmPackage = "@ai-sdk/openai-compatible",
            apiUrl = "https://example.com/v1",
            shape = "future-shape",
            body = Json.parseToJsonElement(
                """{"reasoning":{"mode":"pro"},"custom":[true,42,null],"service_tier":"priority"}""",
            ).jsonObject,
            headers = mapOf("example-beta" to "mode-2026", "quoted" to "value\"\\"),
        )

        assertEquals(
            override,
            converters.jsonToProviderOverride(converters.providerOverrideToJson(override)),
        )
        assertEquals(ProviderOverrideLocal(), converters.jsonToProviderOverride("{}"))
    }

    @Test
    fun experimentalModesKeepDynamicKeysAndIndependentPrices() {
        val modes = mapOf(
            "fast" to ExperimentalModeLocal(
                pricing = OfferingPricingLocal(input = 1.25, output = 7.5),
                providerOverride = ProviderOverrideLocal(headers = mapOf("mode" to "fast")),
            ),
            "future-mode" to ExperimentalModeLocal(
                providerOverride = ProviderOverrideLocal(
                    body = Json.parseToJsonElement("""{"enabled":true}""").jsonObject,
                ),
            ),
            "empty-mode" to ExperimentalModeLocal(),
        )

        assertEquals(
            modes,
            converters.jsonToExperimentalModes(converters.experimentalModesToJson(modes)),
        )
        assertEquals("{}", converters.experimentalModesToJson(emptyMap()))
        assertEquals(emptyMap<String, ExperimentalModeLocal>(), converters.jsonToExperimentalModes("{}"))
    }

    @Test
    fun emptyNestedObjectsAndCollectionsRemainPresent() {
        val override = ProviderOverrideLocal(
            body = Json.parseToJsonElement("{}").jsonObject,
            headers = emptyMap(),
        )
        val pricing = OfferingPricingLocal(contextOver200k = OfferingPricingLocal())

        assertEquals(
            override,
            converters.jsonToProviderOverride(converters.providerOverrideToJson(override)),
        )
        assertEquals(pricing, converters.jsonToPricing(converters.pricingToJson(pricing)))
    }

    @Test
    fun unknownObjectFieldsDoNotDiscardKnownMetadata() {
        assertEquals(
            OfferingPricingLocal(input = 0.0),
            converters.jsonToPricing("""{"input":0,"future":{"enabled":true}}"""),
        )
        assertEquals(
            InterleavedLocal.Toggle(false),
            converters.jsonToInterleaved("""{"type":"toggle","enabled":false,"future":true}"""),
        )
    }

    @Test
    fun malformedOrIncorrectlyTypedDataIsNotReplacedWithDefaults() {
        val invalidReads = listOf<() -> Any?>(
            { converters.jsonToStringList("not json") },
            { converters.jsonToStringList("{}") },
            { converters.jsonToStringList("[null]") },
            { converters.jsonToWeights("[{}]") },
            { converters.jsonToLinks("[{}]") },
            { converters.jsonToPricing("[]") },
            { converters.jsonToPricing("""{"input":false}""") },
            { converters.jsonToPricing("""{"tiers":[{"condition":{"type":"context"}}]}""") },
            { converters.jsonToReasoningOptions("""[{"type":"effort","values":false}]""") },
            { converters.jsonToInterleaved("false") },
            { converters.jsonToInterleaved("""{"type":"future-variant"}""") },
            { converters.jsonToProviderOverride("""{"headers":[]}""") },
            { converters.jsonToExperimentalModes("[]") },
        )

        invalidReads.forEach { read ->
            assertThrows(SerializationException::class.java) { read() }
        }
    }
}
