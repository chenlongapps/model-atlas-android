package io.github.chenlongapps.modelatlas.data.mapper

import io.github.chenlongapps.modelatlas.data.local.ModelListRow
import io.github.chenlongapps.modelatlas.data.local.ModelOfferingPriceRow
import io.github.chenlongapps.modelatlas.data.local.model.OfferingPriceTierLocal
import io.github.chenlongapps.modelatlas.data.local.model.OfferingPricingLocal
import io.github.chenlongapps.modelatlas.data.local.model.PriceTierConditionLocal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPriceMapperTest {
    @Test fun prefersOfficialProviderEvenWhenCheaperThirdPartyExists() {
        val price = listOf(
            priceRow("openai/gpt-x", "openrouter", "OpenRouter", "gpt-x", pricing(input = 0.5, output = 1.5)),
            priceRow("openai/gpt-x", "openai", "OpenAI", "gpt-x", pricing(input = 2.0, output = 10.0)),
        ).selectPriceFor("openai/gpt-x").let { requireNotNull(it) }
        assertEquals("openai", price.providerId)
        assertEquals("OpenAI", price.providerName)
        assertTrue(price.isOfficial)
        assertEquals(2.0, price.input!!, 0.0)
        assertEquals(10.0, price.output!!, 0.0)
    }

    @Test fun fallsBackToThirdPartyPriorityWhenNoOfficialOffer() {
        val price = listOf(
            priceRow("microsoft/phi-x", "novita-ai", "Novita AI", "phi-x", pricing(input = 0.1, output = 0.2)),
            priceRow("microsoft/phi-x", "openrouter", "OpenRouter", "phi-x", pricing(input = 0.9, output = 1.8)),
            priceRow("microsoft/phi-x", "zzz-gateway", "ZZZ Gateway", "phi-x", pricing(input = 0.01, output = 0.02)),
        ).selectPriceFor("microsoft/phi-x").let { requireNotNull(it) }
        assertEquals("openrouter", price.providerId)
        assertFalse(price.isOfficial)
    }

    @Test fun unlistedProvidersLoseToListedOnesAndAreOrderedById() {
        val price = listOf(
            priceRow("lab/m", "zzz-gateway", "ZZZ Gateway", "m", pricing(input = 0.01, output = 0.02)),
            priceRow("lab/m", "aaa-gateway", "AAA Gateway", "m", pricing(input = 0.03, output = 0.04)),
        ).selectPriceFor("lab/m").let { requireNotNull(it) }
        assertEquals("aaa-gateway", price.providerId)
    }

    @Test fun picksLowestPricedOfferingWithinTheSameProvider() {
        val price = listOf(
            priceRow("moonshotai/kimi-x", "moonshotai", "Moonshot AI", "kimi-x-highspeed", pricing(input = 1.9, output = 8.0)),
            priceRow("moonshotai/kimi-x", "moonshotai", "Moonshot AI", "kimi-x", pricing(input = 0.95, output = 4.0)),
        ).selectPriceFor("moonshotai/kimi-x").let { requireNotNull(it) }
        assertEquals(0.95, price.input!!, 0.0)
        assertEquals(4.0, price.output!!, 0.0)
        assertEquals("kimi-x", price.offeringId)
    }

    @Test fun breaksTiesWithinSameProviderByStableOfferingId() {
        val rows = listOf(
            priceRow("lab/m", "p", "P", "b", pricing(input = 1.0, output = 1.0)),
            priceRow("lab/m", "p", "P", "a", pricing(input = 1.0, output = 1.0)),
        )
        // 金额相同仍保留被选中的服务稳定 ID，方便对比页标注来源。
        val price = rows.selectPriceFor("lab/m").let { requireNotNull(it) }
        assertEquals("p", price.providerId)
        assertEquals("a", price.offeringId)
        assertEquals(1.0, price.input!!, 0.0)
    }

    @Test fun keepsZeroPriceButDropsMissingAndUnpricedOffers() {
        val zero = listOf(
            priceRow("lab/m", "p", "P", "m", pricing(input = 0.0, output = 0.0)),
        ).selectPriceFor("lab/m").let { requireNotNull(it) }
        assertEquals(0.0, zero.input!!, 0.0)
        assertEquals(0.0, zero.output!!, 0.0)

        assertNull(listOf(priceRow("lab/m", "p", "P", "m", OfferingPricingLocal())).selectPriceFor("lab/m"))
        assertNull(
            listOf(
                priceRow("lab/m", "p", "P", "m", OfferingPricingLocal(tiers = emptyList())),
            ).selectPriceFor("lab/m"),
        )
        assertNull(emptyList<ModelOfferingPriceRow>().selectPriceFor("lab/m"))
        assertNull(null.selectPriceFor("lab/m"))
    }

    @Test fun tierOnlyOfferStillCountsAsAvailable() {
        val price = listOf(
            priceRow(
                "lab/m",
                "p",
                "P",
                "m",
                OfferingPricingLocal(
                    tiers = listOf(
                        OfferingPriceTierLocal(
                            condition = PriceTierConditionLocal("context", 200_000),
                            input = 1.0,
                            output = 2.0,
                        ),
                    ),
                ),
            ),
        ).selectPriceFor("lab/m").let { requireNotNull(it) }
        assertEquals(1, price.tiers.size)
        assertEquals("context", price.tiers.single().conditionType)
        assertEquals(200_000L, price.tiers.single().conditionSizeTokens)
        assertEquals(1.0, price.tiers.single().input!!, 0.0)
        assertNull(price.tiers.single().cacheRead)
    }

    @Test fun skipsOffersWithoutProviderName() {
        assertNull(
            listOf(
                priceRow("lab/m", "p", null, "m", pricing(input = 1.0, output = 2.0)),
            ).selectPriceFor("lab/m"),
        )
        assertNull(
            listOf(
                priceRow("lab/m", "p", "  ", "m", pricing(input = 1.0, output = 2.0)),
            ).selectPriceFor("lab/m"),
        )
    }

    @Test fun keepsCacheReasoningAudioAndDoesNotTreatLegacyContextOver200kAsAnOffer() {
        val price = listOf(
            priceRow(
                "anthropic/claude-x",
                "anthropic",
                "Anthropic",
                "claude-x",
                OfferingPricingLocal(
                    input = 5.0,
                    output = 25.0,
                    cacheRead = 0.5,
                    cacheWrite = 6.25,
                    reasoning = 25.0,
                    inputAudio = 1.0,
                    outputAudio = 2.0,
                    contextOver200k = OfferingPricingLocal(input = 10.0, output = 37.5),
                ),
            ),
        ).selectPriceFor("anthropic/claude-x").let { requireNotNull(it) }
        assertEquals(5.0, price.input!!, 0.0)
        assertEquals(25.0, price.output!!, 0.0)
        assertEquals(0.5, price.cacheRead!!, 0.0)
        assertEquals(6.25, price.cacheWrite!!, 0.0)
        assertEquals(25.0, price.reasoning!!, 0.0)
        assertEquals(1.0, price.inputAudio!!, 0.0)
        assertEquals(2.0, price.outputAudio!!, 0.0)
        // 旧版 context_over_200k 只是兼容信息，不当作独立报价参与选择。
        assertTrue(price.tiers.isEmpty())
    }

    @Test fun officialMatchIsNotDerivedFromLabAlone() {
        // 服务商 ID 相同但 LAB 没有对应关系时不算官方；对应表之外的 LAB 也没有官方报价。
        val price = listOf(
            priceRow("openai/gpt-x", "openrouter", "OpenRouter", "gpt-x", pricing(input = 0.5, output = 1.5)),
        ).selectPriceFor("openai/gpt-x").let { requireNotNull(it) }
        assertFalse(price.isOfficial)
    }

    @Test fun selectedPriceKeepsAllTierCategoriesAndExactUnknownConditions() {
        val price = listOf(priceRow("lab/m", "p", "Provider", "service", OfferingPricingLocal(
            input = 0.0,
            tiers = listOf(OfferingPriceTierLocal(
                condition = PriceTierConditionLocal("future-condition", 272_001),
                input = 0.003, output = 2.0, cacheRead = 0.0, cacheWrite = 0.0001,
                reasoning = 1.5, inputAudio = 0.7, outputAudio = 0.9,
            )),
        ))).selectPriceFor("lab/m")!!
        assertEquals("service", price.offeringId)
        val tier = price.tiers.single()
        assertEquals("future-condition", tier.conditionType)
        assertEquals(272_001L, tier.conditionSizeTokens)
        assertEquals(0.003, tier.input!!, 0.0)
        assertEquals(0.0, tier.cacheRead!!, 0.0)
        assertEquals(0.0001, tier.cacheWrite!!, 0.0)
        assertEquals(1.5, tier.reasoning!!, 0.0)
        assertEquals(0.7, tier.inputAudio!!, 0.0)
        assertEquals(0.9, tier.outputAudio!!, 0.0)
    }

    private fun pricing(input: Double?, output: Double?) = OfferingPricingLocal(input = input, output = output)

    private fun priceRow(
        canonicalModelId: String,
        providerId: String,
        providerName: String?,
        offeringId: String,
        pricing: OfferingPricingLocal,
    ) = ModelOfferingPriceRow(canonicalModelId, providerId, providerName, offeringId, pricing)
}
