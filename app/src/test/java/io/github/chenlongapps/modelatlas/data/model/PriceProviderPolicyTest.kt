package io.github.chenlongapps.modelatlas.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceProviderPolicyTest {
    @Test fun mapsVerifiedFirstPartyProviders() {
        assertEquals("openai", PriceProviderPolicy.officialProviderId("openai"))
        assertEquals("anthropic", PriceProviderPolicy.officialProviderId("anthropic"))
        assertEquals("amazon-bedrock", PriceProviderPolicy.officialProviderId("amazon"))
        assertEquals("arcee", PriceProviderPolicy.officialProviderId("arcee-ai"))
    }

    @Test fun labsWithoutVerifiedFirstPartyProviderHaveNoOfficialOffer() {
        listOf("microsoft", "baidu", "tencent", "bytedance-seed", "ibm", "writer", "unknown-lab")
            .forEach { lab -> assertNull(PriceProviderPolicy.officialProviderId(lab)) }
        assertNull(PriceProviderPolicy.officialProviderId(null))
    }

    @Test fun thirdPartyPriorityIsOrderedAndStable() {
        assertEquals(0, PriceProviderPolicy.thirdPartyRank("openrouter"))
        assertEquals(1, PriceProviderPolicy.thirdPartyRank("togetherai"))
        assertEquals(2, PriceProviderPolicy.thirdPartyRank("fireworks-ai"))
        assertEquals(3, PriceProviderPolicy.thirdPartyRank("deepinfra"))
        assertEquals(4, PriceProviderPolicy.thirdPartyRank("siliconflow"))
        assertEquals(5, PriceProviderPolicy.thirdPartyRank("novita-ai"))
        assertEquals(6, PriceProviderPolicy.thirdPartyRank("huggingface"))
        assertEquals(7, PriceProviderPolicy.thirdPartyRank("amazon-bedrock"))
        assertEquals(8, PriceProviderPolicy.thirdPartyRank("google-vertex"))
        assertEquals(9, PriceProviderPolicy.thirdPartyRank("azure"))
    }

    @Test fun unlistedProvidersRankAfterEveryListedProvider() {
        val listed = PriceProviderPolicy.thirdPartyPriority
        val unlisted = PriceProviderPolicy.thirdPartyRank("some-unlisted-provider")
        assertEquals(listed.size, unlisted)
        assertTrue(unlisted > PriceProviderPolicy.thirdPartyRank(listed.last()))
    }

    @Test fun officialMappingIsNotDerivedFromLabAlone() {
        // 官方认定来自显式对应表；LAB 相同但服务商不是对应表中的第一方时不算官方。
        assertFalse(PriceProviderPolicy.officialProviderId("openai") == "openrouter")
        assertTrue(PriceProviderPolicy.officialProviderId("openai") == "openai")
    }
}
