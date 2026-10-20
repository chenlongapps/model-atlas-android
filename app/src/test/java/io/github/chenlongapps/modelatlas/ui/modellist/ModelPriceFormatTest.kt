package io.github.chenlongapps.modelatlas.ui.modellist

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelPriceFormatTest {
    @Test fun keepsAtLeastTwoDecimalPlaces() {
        assertEquals("$0.00", formatUsdPerMillionTokens(0.0))
        assertEquals("$4.00", formatUsdPerMillionTokens(4.0))
        assertEquals("$0.60", formatUsdPerMillionTokens(0.6))
    }

    @Test fun keepsSmallPricesAndSourcePrecision() {
        assertEquals("$0.003", formatUsdPerMillionTokens(0.003))
        assertEquals("$0.0000001", formatUsdPerMillionTokens(0.0000001))
        assertEquals("$6.25", formatUsdPerMillionTokens(6.25))
    }
}
