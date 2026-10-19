package io.github.chenlongapps.modelatlas.data.remote

import io.github.chenlongapps.modelatlas.data.remote.dto.InterleavedDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsDevParserTest {
    private val parser = ModelsDevParser()
    private val models by lazy { parser.parseModels(readFixture("models.json")) }
    private val providers by lazy { parser.parseProviders(readFixture("api.json")) }

    @Test
    fun parseModelsDecodesCanonicalMetadataAndCapabilities() {
        val model = models.getValue("example/atlas")

        assertEquals(setOf("example/atlas", "example/embedding"), models.keys)
        assertEquals("example/atlas", model.id)
        assertEquals("Example Atlas", model.name)
        assertEquals("Synthetic model for parser tests", model.description)
        assertEquals("atlas", model.family)
        assertEquals(true, model.attachment)
        assertEquals(true, model.reasoning)
        assertEquals(true, model.toolCall)
        assertEquals(false, model.structuredOutput)
        assertEquals(false, model.temperature)
        assertEquals("2025-03", model.knowledge)
        assertEquals("2026-01-15", model.releaseDate)
        assertEquals("2026-10-19", model.lastUpdated)
        assertEquals(listOf("text", "image", "audio", "pdf", "video"), model.modalities?.input)
        assertEquals(listOf("text"), model.modalities?.output)
        assertEquals(true, model.openWeights)
        assertEquals(262_144L, model.limit?.context)
        assertEquals(245_760L, model.limit?.input)
        assertEquals(16_384L, model.limit?.output)
        assertEquals("Apache-2.0", model.license)
    }

    @Test
    fun parseModelsDecodesWeightsLinksAndBenchmarks() {
        val model = models.getValue("example/atlas")
        val weights = requireNotNull(model.weights)
        val links = requireNotNull(model.links)
        val benchmarks = requireNotNull(model.benchmarks)
        val weight = weights.first()
        val link = links.first()
        val benchmark = benchmarks.first()

        assertEquals("Example weights", weight.label)
        assertEquals("https://example.com/weights", weight.url)
        assertEquals("fp8", weight.quantization)
        assertEquals("safetensors", weight.format)
        assertNull(weights.last().label)
        assertEquals("Model card", link.label)
        assertEquals("https://example.com/model-card", link.url)
        assertEquals("model_card", link.type)
        assertNull(links.last().type)
        assertEquals("Example benchmark", benchmark.name)
        assertEquals(95.5, benchmark.score, 0.0)
        assertEquals("accuracy", benchmark.metric)
        assertEquals("https://example.com/benchmark", benchmark.source)
        assertEquals("2026-10-19", benchmark.date)
        assertEquals("2", benchmark.version)
        assertEquals("no tools", benchmark.variant)
        assertEquals("public", benchmark.dataset)
        assertEquals("example-harness", benchmark.harness)
        assertEquals(96.0, benchmarks.last().score, 0.0)
    }

    @Test
    fun parseModelsKeepsNonLlmTypesAndMissingOutputLimits() {
        val model = models.getValue("example/embedding")

        assertEquals("embedding", model.type)
        assertEquals(8192L, model.limit?.context)
        assertNull(model.limit?.output)
        assertNull(model.limit?.input)
        assertNull(model.description)
        assertNull(model.reasoning)
        assertNull(model.weights)
    }

    @Test
    fun parseProvidersKeepsProviderAndOfferingIdsSeparateFromCanonicalIds() {
        val provider = providers.getValue("example-api")
        val offering = provider.models.getValue("atlas-chat-v1")
        val secondOffering = providers.getValue("second-api").models.getValue("atlas-chat-v1")

        assertEquals(setOf("example-api", "second-api"), providers.keys)
        assertEquals("example-api", provider.id)
        assertEquals("Example API", provider.name)
        assertEquals(listOf("EXAMPLE_API_KEY", "EXAMPLE_REGION"), provider.env)
        assertEquals("@ai-sdk/openai-compatible", provider.npm)
        assertEquals("https://example.com/v1", provider.api)
        assertEquals("https://example.com/docs", provider.doc)
        assertEquals("atlas-chat-v1", offering.id)
        assertEquals("example/atlas", offering.canonicalModelId)
        assertEquals(offering.id, secondOffering.id)
        assertEquals(offering.canonicalModelId, secondOffering.canonicalModelId)
        assertEquals("Example Atlas on Second API", secondOffering.name)
        assertNull(providers.getValue("second-api").api)
    }

    @Test
    fun parseProvidersDecodesOfferingCapabilitiesAndProviderOverrides() {
        val offering = providers.getValue("example-api").models.getValue("atlas-chat-v1")

        assertEquals("Synthetic offering for parser tests", offering.description)
        assertEquals("atlas", offering.family)
        assertEquals(false, offering.attachment)
        assertEquals(true, offering.reasoning)
        assertEquals(true, offering.toolCall)
        assertEquals(true, offering.structuredOutput)
        assertEquals(false, offering.temperature)
        assertEquals("2025-03", offering.knowledge)
        assertEquals("2026-01-15", offering.releaseDate)
        assertEquals("2026-10-19", offering.lastUpdated)
        assertEquals(listOf("text", "image"), offering.modalities?.input)
        assertEquals(listOf("text"), offering.modalities?.output)
        assertEquals(true, offering.openWeights)
        assertEquals(262_144L, offering.limit?.context)
        assertEquals(245_760L, offering.limit?.input)
        assertEquals(16_384L, offering.limit?.output)
        assertEquals("deprecated", offering.status)
        assertEquals("@ai-sdk/openai", offering.provider?.npm)
        assertEquals("https://example.com/alternate/v1", offering.provider?.api)
        assertEquals("responses", offering.provider?.shape)
        assertEquals("embedding", providers.getValue("second-api").models.getValue("embedding-v1").type)
    }

    @Test
    fun parseProvidersDecodesNullableReasoningValuesAndTokenBudgets() {
        val options = requireNotNull(
            providers.getValue("example-api").models.getValue("atlas-chat-v1").reasoningOptions,
        )

        assertEquals("effort", options[0].type)
        assertEquals(listOf(null, "low", "medium", "high"), options[0].values)
        assertEquals("budget_tokens", options[1].type)
        assertEquals(0L, options[1].min)
        assertEquals(65_536L, options[1].max)
        assertNull(options[1].values)
        assertEquals("toggle", options[2].type)
        assertNull(options[2].min)
    }

    @Test
    fun parseProvidersDecodesBaseAudioAndReasoningCostsIncludingZero() {
        val cost = requireNotNull(
            providers.getValue("example-api").models.getValue("atlas-chat-v1").cost,
        )

        assertPrice(0.0, cost.input)
        assertPrice(3.0, cost.output)
        assertPrice(0.1, cost.cacheRead)
        assertPrice(0.2, cost.cacheWrite)
        assertPrice(1.5, cost.inputAudio)
        assertPrice(2.0, cost.outputAudio)
        assertPrice(4.0, cost.reasoning)
    }

    @Test
    fun parseProvidersDecodesPriceTiersAndLegacyContextPrices() {
        val cost = requireNotNull(
            providers.getValue("example-api").models.getValue("atlas-chat-v1").cost,
        )
        val tier = requireNotNull(cost.tiers).single()
        val contextPrices = requireNotNull(cost.contextOver200k)

        assertEquals("context", tier.tier.type)
        assertEquals(200_000L, tier.tier.size)
        assertPrice(1.0, tier.input)
        assertPrice(2.0, tier.output)
        assertPrice(0.5, tier.cacheRead)
        assertPrice(0.75, tier.cacheWrite)
        assertPrice(3.0, tier.inputAudio)
        assertPrice(4.0, tier.outputAudio)
        assertPrice(2.5, tier.reasoning)
        assertPrice(1.0, contextPrices.input)
        assertPrice(2.0, contextPrices.output)
        assertPrice(0.5, contextPrices.cacheRead)
        assertPrice(0.75, contextPrices.cacheWrite)
        assertNull(contextPrices.inputAudio)
    }

    @Test
    fun parseProvidersKeepsDynamicModesAndArbitraryRequestParameters() {
        val modes = requireNotNull(
            providers.getValue("example-api").models.getValue("atlas-chat-v1").experimental?.modes,
        )
        val mode = modes.getValue("fast")
        val provider = requireNotNull(mode.provider)
        val body = requireNotNull(provider.body)

        assertEquals(setOf("fast", "future-mode"), modes.keys)
        assertPrice(1.25, mode.cost?.input)
        assertPrice(7.5, mode.cost?.output)
        assertPrice(0.125, mode.cost?.cacheRead)
        assertEquals("priority", body.getValue("service_tier").jsonPrimitive.content)
        assertEquals("pro", body.getValue("reasoning").jsonObject.getValue("mode").jsonPrimitive.content)
        assertEquals(
            "reserved",
            body.getValue("additionalModelRequestFields").jsonObject
                .getValue("service_tier").jsonPrimitive.content,
        )
        assertEquals(Json.parseToJsonElement("[true,42,null]"), body.getValue("custom").jsonArray)
        assertEquals("example-fast-mode", provider.headers?.get("anthropic-beta"))
        assertNull(modes.getValue("future-mode").cost)
    }

    @Test
    fun parseProvidersDecodesBooleanAndObjectInterleavedValues() {
        val offerings = providers.getValue("example-api").models

        assertEquals(InterleavedDto.Field("reasoning_content"), offerings.getValue("atlas-chat-v1").interleaved)
        assertEquals(InterleavedDto.Toggle(false), offerings.getValue("@example/unlinked").interleaved)
        assertEquals(
            InterleavedDto.Toggle(true),
            providers.getValue("second-api").models.getValue("atlas-chat-v1").interleaved,
        )
    }

    @Test
    fun missingOfferingDataDoesNotBecomeFreeOrUnsupported() {
        val offering = providers.getValue("example-api").models.getValue("@example/unlinked")
        val embedding = providers.getValue("second-api").models.getValue("embedding-v1")
        val embeddingCost = requireNotNull(embedding.cost)

        assertNull(offering.canonicalModelId)
        assertNull(offering.cost)
        assertNull(offering.reasoning)
        assertNull(offering.toolCall)
        assertNull(offering.reasoningOptions)
        assertNull(offering.limit)
        assertTrue(requireNotNull(embedding.reasoningOptions).isEmpty())
        assertNull(embeddingCost.input)
        assertNull(embeddingCost.output)
    }

    @Test
    fun explicitNullsRemainNull() {
        val model = parser.parseModels(
            """{"model":{"id":"model","name":"Model","description":null,"reasoning":null,"limit":null,"weights":null}}""",
        ).getValue("model")
        val provider = parser.parseProviders(
            """{"api":{"id":"api","name":"API","api":null,"models":{"m":{"id":"m","name":"M","canonical_model_id":null,"cost":null,"interleaved":null,"reasoning_options":null,"experimental":null}}}}""",
        ).getValue("api")
        val offering = provider.models.getValue("m")

        assertNull(model.description)
        assertNull(model.reasoning)
        assertNull(model.limit)
        assertNull(model.weights)
        assertNull(provider.api)
        assertNull(offering.canonicalModelId)
        assertNull(offering.cost)
        assertNull(offering.interleaved)
        assertNull(offering.reasoningOptions)
        assertNull(offering.experimental)
    }

    @Test
    fun unknownFieldsAreIgnoredAtEveryTypedLevel() {
        val model = parser.parseModels(
            """
            {
              "model": {
                "id": "model", "name": "Model", "future": {"value": 1},
                "limit": {"context": 42, "future": true},
                "benchmarks": [{"name": "Test", "score": 1, "future": []}]
              }
            }
            """.trimIndent(),
        ).getValue("model")
        val offering = parser.parseProviders(
            """
            {
              "api": {
                "id": "api", "name": "API", "future": true,
                "models": {
                  "model": {
                    "id": "model", "name": "Model", "future": [],
                    "interleaved": {"field": "reasoning", "future": 1},
                    "reasoning_options": [{"type": "future-option", "future": true}],
                    "cost": {
                      "input": 1, "future": {},
                      "tiers": [{"input": 2, "future": [], "tier": {"type": "future-tier", "size": 42, "future": true}}]
                    },
                    "experimental": {"future": true, "modes": {"new": {"future": 1, "provider": {"future": []}}}}
                  }
                }
              }
            }
            """.trimIndent(),
        ).getValue("api").models.getValue("model")

        assertEquals(42L, model.limit?.context)
        assertEquals(InterleavedDto.Field("reasoning"), offering.interleaved)
        assertEquals("future-option", offering.reasoningOptions?.single()?.type)
        assertEquals("future-tier", offering.cost?.tiers?.single()?.tier?.type)
        assertTrue(requireNotNull(offering.experimental?.modes).containsKey("new"))
    }

    @Test
    fun limitsCanExceedIntRangeAndModalityNamesCanEvolve() {
        val model = parser.parseModels(
            """{"model":{"id":"model","name":"Model","type":"future-type","limit":{"context":4294967296},"modalities":{"input":["future-modality"],"output":[]}}}""",
        ).getValue("model")

        assertEquals(4_294_967_296L, model.limit?.context)
        assertEquals("future-type", model.type)
        assertEquals(listOf("future-modality"), model.modalities?.input)
        assertTrue(requireNotNull(model.modalities?.output).isEmpty())
    }

    @Test
    fun emptyCatalogsAndProvidersWithoutOfferingsAreValid() {
        assertTrue(parser.parseModels("{}").isEmpty())
        assertTrue(parser.parseProviders("{}").isEmpty())
        assertTrue(
            parser.parseProviders("""{"api":{"id":"api","name":"API","models":{}}}""")
                .getValue("api").models.isEmpty(),
        )
    }

    @Test
    fun malformedJsonAndInvalidTopLevelShapesAreRejected() {
        listOf("not json", "{", "[]", "null", "true").forEach { source ->
            assertThrows(SerializationException::class.java) { parser.parseModels(source) }
            assertThrows(SerializationException::class.java) { parser.parseProviders(source) }
        }
    }

    @Test
    fun requiredIdentifiersNamesAndOfferingMapsCannotBeMissingOrNull() {
        listOf(
            """{"m":{"name":"Model"}}""",
            """{"m":{"id":"m"}}""",
            """{"m":{"id":null,"name":"Model"}}""",
            """{"m":{"id":"m","name":null}}""",
        ).forEach { source ->
            assertThrows(SerializationException::class.java) { parser.parseModels(source) }
        }
        listOf(
            """{"api":{"name":"API","models":{}}}""",
            """{"api":{"id":"api","models":{}}}""",
            """{"api":{"id":"api","name":"API"}}""",
            """{"api":{"id":"api","name":"API","models":null}}""",
            """{"api":{"id":"api","name":"API","models":{"m":{"name":"Model"}}}}""",
        ).forEach { source ->
            assertThrows(SerializationException::class.java) { parser.parseProviders(source) }
        }
    }

    @Test
    fun invalidFieldTypesAreNotSilentlyCoercedToDefaults() {
        assertThrows(SerializationException::class.java) {
            parser.parseModels("""{"m":{"id":"m","name":"Model","reasoning":[]}}""")
        }
        assertThrows(SerializationException::class.java) {
            parser.parseProviders(
                """{"api":{"id":"api","name":"API","models":{"m":{"id":"m","name":"Model","cost":{"input":"unknown"}}}}}""",
            )
        }
    }

    private fun readFixture(name: String): String =
        requireNotNull(javaClass.getResource("/modelsdev/$name")).readText()

    private fun assertPrice(expected: Double, actual: Double?) {
        assertEquals(expected, requireNotNull(actual), 0.0)
    }
}
