package io.github.chenlongapps.modelatlas.ui.modellist

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelFilterFormatTest {
    @Test fun presetThresholdsAreCompactAndExact() {
        assertEquals("32K", formatFilterTokenCount(32_000))
        assertEquals("128K", formatFilterTokenCount(128_000))
        assertEquals("200K", formatFilterTokenCount(200_000))
        assertEquals("1M", formatFilterTokenCount(1_000_000))
        assertEquals("4K", formatFilterTokenCount(4_000))
    }

    @Test fun restoredNonRoundThresholdsNeverLosePrecision() {
        assertEquals("131.072K", formatFilterTokenCount(131_072))
        assertEquals("1.048576M", formatFilterTokenCount(1_048_576))
        assertEquals("9223372036854.775807M", formatFilterTokenCount(Long.MAX_VALUE))
        assertEquals("0", formatFilterTokenCount(0))
        assertEquals("999", formatFilterTokenCount(999))
    }
}
