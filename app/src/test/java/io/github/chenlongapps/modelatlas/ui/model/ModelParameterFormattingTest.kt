package io.github.chenlongapps.modelatlas.ui.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelParameterFormattingTest {
    @Test fun keepsBillionUnitAndAllMeaningfulDecimalsWithoutInventedIntegerPrecision() {
        mapOf("671" to "671 B", "3.300" to "3.3 B", "70.553706496123456789" to "70.553706496123456789 B",
            "0.054534144" to "0.054534144 B", "2400" to "2400 B", "1E-9" to "0.000000001 B").forEach { (raw, text) ->
            assertEquals(text, formatBillionParameters(raw.toBigDecimal()))
        }
    }
}
