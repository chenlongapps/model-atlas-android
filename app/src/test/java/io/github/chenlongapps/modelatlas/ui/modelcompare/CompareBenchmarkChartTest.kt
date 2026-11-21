package io.github.chenlongapps.modelatlas.ui.modelcompare

import io.github.chenlongapps.modelatlas.data.model.ModelBenchmark
import org.junit.Assert.*
import org.junit.Test

class CompareBenchmarkChartTest {
    private fun group(first: Double, second: Double, unit: String): CompareBenchmarkGroup {
        val record = ModelBenchmark("Benchmark", first, unit = unit)
        return CompareBenchmarkGroup(CompareBenchmarkKey("example", "1", "score", unit, false),
            record, record.copy(score = second))
    }

    @Test fun explicitPercentagesUseTheSameZeroToHundredScale() {
        listOf("%", "percent").forEach { unit ->
            val chart = requireNotNull(compareBenchmarkChart(group(59.6, 64.1, unit)))
            assertEquals(0f, chart.zero, 0f)
            assertEquals(0.596f, chart.range.fraction(59.6), 1e-6f)
            assertEquals(0.641f, chart.range.fraction(64.1), 1e-6f)
        }
        val outOfRange = requireNotNull(compareBenchmarkChart(group(99.0, 110.0, "%")))
        assertTrue(outOfRange.range.fraction(110.0) > outOfRange.range.fraction(99.0))
    }

    @Test fun pointsKeepACommonZeroAndNegativeValuesPlotOnTheOtherSide() {
        val points = requireNotNull(compareBenchmarkChart(group(64.6, 62.1, "points")))
        assertEquals(0f, points.zero, 0f)
        assertEquals(64.6f / 62.1f, points.range.fraction(64.6) / points.range.fraction(62.1), 1e-6f)
        val signed = requireNotNull(compareBenchmarkChart(group(-5.0, 10.0, "points")))
        assertTrue(signed.range.fraction(-5.0) < signed.zero)
        assertTrue(signed.range.fraction(10.0) > signed.zero)
        val bothNegative = requireNotNull(compareBenchmarkChart(group(-10.0, -5.0, "points")))
        assertEquals(1f, bothNegative.zero, 0f)
        val zeros = requireNotNull(compareBenchmarkChart(group(0.0, 0.0, "points")))
        assertEquals(0f, zeros.zero, 0f)
    }

    @Test fun textAndNonFiniteNumbersNeverGenerateBars() {
        val numeric = group(64.6, 62.1, "points")
        assertNull(compareBenchmarkChart(numeric.copy(first = numeric.first.copy(scoreText = "64 ± 2"))))
        assertNull(compareBenchmarkChart(numeric.copy(second = numeric.second.copy(score = null, scoreText = "62"))))
        assertNull(compareBenchmarkChart(numeric.copy(first = numeric.first.copy(score = Double.POSITIVE_INFINITY))))
    }
}
