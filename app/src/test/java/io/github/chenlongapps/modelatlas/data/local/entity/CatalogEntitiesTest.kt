package io.github.chenlongapps.modelatlas.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogEntitiesTest {
    private val fetchedAt = 1_792_368_000_000L

    @Test
    fun modelKeepsDictionaryKeyRemoteIdAndOptionalMetadataSeparate() {
        val model = ModelEntity(
            id = "example/canonical-key",
            remoteId = "example/remote-id",
            name = "Example model",
            fetchedAtEpochMillis = fetchedAt,
        )

        assertEquals("example/canonical-key", model.id)
        assertEquals("example/remote-id", model.remoteId)
        assertEquals("models.dev", model.source)
        assertEquals(fetchedAt, model.fetchedAtEpochMillis)
        assertNull(model.sourceLastUpdated)
        assertNull(model.attachment)
        assertNull(model.reasoning)
        assertNull(model.toolCall)
        assertNull(model.structuredOutput)
        assertNull(model.temperature)
        assertNull(model.openWeights)
        assertNull(model.contextLimitTokens)
        assertNull(model.inputLimitTokens)
        assertNull(model.outputLimitTokens)
        assertNull(model.inputModalities)
        assertNull(model.outputModalities)
        assertNull(model.license)
        assertNull(model.weights)
        assertNull(model.links)
    }

    @Test
    fun providerDoesNotInventApiConfiguration() {
        val provider = ProviderEntity(
            id = "example-provider-key",
            remoteId = "example-provider-id",
            name = "Example API",
            fetchedAtEpochMillis = fetchedAt,
        )

        assertEquals("example-provider-key", provider.id)
        assertEquals("example-provider-id", provider.remoteId)
        assertEquals("models.dev", provider.source)
        assertEquals(fetchedAt, provider.fetchedAtEpochMillis)
        assertNull(provider.apiUrl)
        assertNull(provider.documentationUrl)
        assertNull(provider.npmPackage)
        assertNull(provider.environmentVariables)
    }

    @Test
    fun offeringAllowsMissingCanonicalAssociationAndUnknownPricing() {
        val offering = ModelOfferingEntity(
            providerId = "example-api",
            id = "service-key",
            remoteId = "service-id",
            name = "Example service",
            fetchedAtEpochMillis = fetchedAt,
        )

        assertEquals("example-api", offering.providerId)
        assertEquals("service-key", offering.id)
        assertEquals("service-id", offering.remoteId)
        assertEquals("models.dev", offering.source)
        assertEquals(fetchedAt, offering.fetchedAtEpochMillis)
        assertNull(offering.canonicalModelId)
        assertNull(offering.attachment)
        assertNull(offering.reasoning)
        assertNull(offering.toolCall)
        assertNull(offering.structuredOutput)
        assertNull(offering.temperature)
        assertNull(offering.openWeights)
        assertNull(offering.inputModalities)
        assertNull(offering.outputModalities)
        assertNull(offering.contextLimitTokens)
        assertNull(offering.inputLimitTokens)
        assertNull(offering.outputLimitTokens)
        assertNull(offering.status)
        assertNull(offering.reasoningOptions)
        assertNull(offering.interleaved)
        assertNull(offering.pricing)
        assertNull(offering.providerOverride)
        assertNull(offering.experimentalModes)
    }

    @Test
    fun modelAndServiceKeepTheirOwnCapabilitiesLimitsAndSourceDates() {
        val model = ModelEntity(
            id = "example/model",
            remoteId = "example/model",
            name = "Example model",
            fetchedAtEpochMillis = fetchedAt,
            knowledgeCutoff = "2025-03",
            releaseDate = "2026-01",
            sourceLastUpdated = "2026-10-19",
            attachment = true,
            contextLimitTokens = 4_294_967_296L,
        )
        val offering = ModelOfferingEntity(
            providerId = "example-api",
            id = "chat-v1",
            remoteId = "chat-v1",
            name = "Example chat",
            fetchedAtEpochMillis = fetchedAt,
            canonicalModelId = model.id,
            attachment = false,
            contextLimitTokens = 262_144L,
            inputModalities = emptyList(),
            status = "future-status",
        )

        assertTrue(requireNotNull(model.attachment))
        assertFalse(requireNotNull(offering.attachment))
        assertEquals(4_294_967_296L, model.contextLimitTokens)
        assertEquals(262_144L, offering.contextLimitTokens)
        assertEquals("2025-03", model.knowledgeCutoff)
        assertEquals("2026-01", model.releaseDate)
        assertEquals("2026-10-19", model.sourceLastUpdated)
        assertNull(offering.sourceLastUpdated)
        assertEquals(emptyList<String>(), offering.inputModalities)
        assertEquals("future-status", offering.status)
    }
}
