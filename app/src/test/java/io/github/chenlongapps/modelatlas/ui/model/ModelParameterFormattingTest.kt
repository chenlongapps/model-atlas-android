package io.github.chenlongapps.modelatlas.ui.model

import io.github.chenlongapps.modelatlas.data.model.ModelParameterComponent
import io.github.chenlongapps.modelatlas.data.model.ModelParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelParameterFormattingTest {
    @Test fun keepsBillionUnitAndAllMeaningfulDecimalsWithoutInventedIntegerPrecision() {
        mapOf("671" to "671B", "3.300" to "3.3B", "70.553706496123456789" to "70.553706496123456789B",
            "0.054534144" to "0.054534144B", "2400" to "2400B", "1E-9" to "0.000000001B").forEach { (raw, text) ->
            assertEquals(text, formatParameterCount(ModelParameters(totalBillion = raw.toBigDecimal())))
        }
    }

    @Test fun combinesOnlyDisclosedCanonicalCounts() {
        val counts = ModelParameters(totalBillion = "671".toBigDecimal(), activeBillion = "37".toBigDecimal())
        assertEquals("671B-A37B", formatParameterCount(counts))
        assertEquals("A37B", formatParameterCount(counts.copy(totalBillion = null)))
        assertEquals("70.553706496123456789B-A0.054534144B", formatParameterCount(
            ModelParameters(totalBillion = "70.553706496123456789".toBigDecimal(), activeBillion = "0.054534144".toBigDecimal()),
        ))
        assertNull(formatParameterCount(null))
        assertNull(formatParameterCount(ModelParameters(components = listOf(
            ModelParameterComponent("backbone", totalBillion = "12.4".toBigDecimal(), sources = emptyList()),
        ))))
    }
}
