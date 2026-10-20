package io.github.chenlongapps.modelatlas.ui.modellist

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelTokenFormatTest {
    @Test fun abbreviatesListLimitsUsingDecimalKAndM() {
        assertEquals("0", formatCompactTokenCount(0))
        assertEquals("999", formatCompactTokenCount(999))
        assertEquals("1K", formatCompactTokenCount(1_000))
        assertEquals("1M", formatCompactTokenCount(1_000_000))
        assertEquals("384K", formatCompactTokenCount(384_000))
        assertEquals("512K", formatCompactTokenCount(512_000))
        assertEquals("5000M", formatCompactTokenCount(5_000_000_000))
    }

    @Test fun roundsListLimitsToAtMostOneDecimalWithoutTrailingZero() {
        assertEquals("16.4K", formatCompactTokenCount(16_384))
        assertEquals("131.1K", formatCompactTokenCount(131_072))
        assertEquals("1K", formatCompactTokenCount(1_049))
        assertEquals("1.1K", formatCompactTokenCount(1_050))
        assertEquals("1.5M", formatCompactTokenCount(1_500_000))
        assertEquals("999.9K", formatCompactTokenCount(999_949))
        assertEquals("1M", formatCompactTokenCount(999_950))
    }

    @Test fun compactFormattingDoesNotOverflowOrUseFloatingPoint() {
        assertEquals("9223372036854.8M", formatCompactTokenCount(Long.MAX_VALUE))
        assertEquals("-9223372036854.8M", formatCompactTokenCount(Long.MIN_VALUE))
    }

    @Test fun groupsTokensWithoutAbbreviating() {
        assertEquals("0", formatTokenCount(0))
        assertEquals("999", formatTokenCount(999))
        assertEquals("16,384", formatTokenCount(16_384))
        assertEquals("1,000,000", formatTokenCount(1_000_000))
        assertEquals("5,000,000,000", formatTokenCount(5_000_000_000))
    }

    @Test fun preservesFullLongPrecision() {
        assertEquals("9,223,372,036,854,775,807", formatTokenCount(Long.MAX_VALUE))
    }
}
