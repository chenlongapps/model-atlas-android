package io.github.chenlongapps.modelatlas.data.mapper

import io.github.chenlongapps.modelatlas.data.local.model.InterleavedLocal
import io.github.chenlongapps.modelatlas.data.remote.ModelsDevParser
import io.github.chenlongapps.modelatlas.data.remote.dto.ModelDto
import io.github.chenlongapps.modelatlas.data.remote.dto.ModelOfferingDto
import io.github.chenlongapps.modelatlas.data.remote.dto.ProviderDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogMapperTest {
    private val mapper = CatalogMapper()

    @Test fun mapsFullFixturesWithoutMixingCanonicalAndOfferingValues() {
        val parser = ModelsDevParser()
        val snapshot = mapper.map(
            parser.parseModels(fixture("models")), parser.parseProviders(fixture("api")), 100, 200,
        )
        val model = snapshot.models.first()
        val offering = snapshot.offerings.first()
        assertEquals(2, snapshot.models.size)
        assertEquals(2, snapshot.providers.size)
        assertEquals(4, snapshot.offerings.size)
        assertEquals(100L, model.fetchedAtEpochMillis)
        assertEquals("models.dev", model.source)
        assertEquals("2026-10-19", model.sourceLastUpdated)
        assertEquals(262144L, model.contextLimitTokens)
        assertEquals("fp8", model.weights!!.first().quantization)
        assertEquals("model_card", model.links!!.first().type)
        assertEquals("example-harness", model.benchmarks!!.first().harness)
        assertEquals(200L, offering.fetchedAtEpochMillis)
        assertFalse(offering.attachment!!)
        assertEquals("example/atlas", offering.canonicalModelId)
        assertEquals(InterleavedLocal.Field("reasoning_content"), offering.interleaved)
        assertNull(offering.reasoningOptions!!.first().values!!.first())
        val pricing = offering.pricing!!
        assertEquals(0.0, pricing.input!!, 0.0)
        assertEquals(200000L, pricing.tiers!!.single().condition.size)
        assertEquals(2.5, pricing.tiers.single().reasoning!!, 0.0)
        assertEquals(0.5, pricing.contextOver200k!!.cacheRead!!, 0.0)
        assertEquals("responses", offering.providerOverride!!.shape)
        assertEquals(7.5, offering.experimentalModes!!["fast"]!!.pricing!!.output!!, 0.0)
        assertEquals("example-fast-mode", offering.experimentalModes["fast"]!!.providerOverride!!.headers!!["anthropic-beta"])
        assertEquals(InterleavedLocal.Toggle(false), snapshot.offerings[1].interleaved)
        assertEquals(InterleavedLocal.Toggle(true), snapshot.offerings[2].interleaved)
        assertNull(snapshot.offerings[2].pricing)
        assertEquals(emptyList<Any>(), snapshot.offerings.last().reasoningOptions)
        assertNull(snapshot.offerings.last().pricing!!.input)
    }

    @Test fun dictionaryKeysAreStableIdsAndUnmatchedAssociationsAreNotRepaired() {
        val snapshot = mapper.map(
            mapOf("canonical-key" to ModelDto("different-remote-id", "Model")),
            mapOf("provider-key" to ProviderDto("remote-provider", "API", mapOf(
                "offering-key" to ModelOfferingDto("remote-offering", "Model", canonicalModelId = "missing"),
                "canonical-key" to ModelOfferingDto("canonical-key", "Model"),
            ))),
            10, 20,
        )
        assertEquals("canonical-key", snapshot.models.single().id)
        assertEquals("different-remote-id", snapshot.models.single().remoteId)
        assertEquals("provider-key", snapshot.providers.single().id)
        assertEquals("remote-provider", snapshot.providers.single().remoteId)
        assertEquals("offering-key", snapshot.offerings.first().id)
        assertEquals("remote-offering", snapshot.offerings.first().remoteId)
        assertEquals("provider-key", snapshot.offerings.first().providerId)
        assertEquals("missing", snapshot.offerings.first().canonicalModelId)
        assertNull(snapshot.offerings.last().canonicalModelId)
        assertNull(snapshot.offerings.first().toolCall)
    }

    private fun fixture(name: String) = javaClass.getResource("/modelsdev/$name.json")!!.readText()
}
