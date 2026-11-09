package io.github.chenlongapps.modelatlas.ui.modeldetail

import io.github.chenlongapps.modelatlas.data.model.ModelBenchmark
import org.junit.Assert.*
import org.junit.Test

class BenchmarkPresentationTest {
    @Test fun numericScoresRoundHalfUpToExactlyOneDecimalWithoutChangingRawData() {
        mapOf(95.15678 to "95.2", 95.14 to "95.1", 95.15 to "95.2", 99.99 to "100.0",
            42.0 to "42.0", 0.0 to "0.0", 0.003 to "0.0", -0.003 to "0.0", -1.25 to "-1.3",
            1e20 to "100000000000000000000.0").forEach { (raw, expected) ->
            val benchmark = ModelBenchmark("Example", raw)
            assertEquals(expected, formatBenchmarkScore(benchmark))
            assertEquals(raw, benchmark.score)
        }
    }

    @Test fun textScoresKeepUncertaintyUnitsAndQualifiersVerbatim() {
        listOf("899 ± 18", "95.12345%", "95.12345 (without tools)").forEach { text ->
            assertEquals(text, formatBenchmarkScore(ModelBenchmark("Example", 9.99, scoreText = text)))
        }
    }

    @Test fun missingScoresRemainUnknownAndBlankTextFallsBackToNumericValue() {
        assertNull(formatBenchmarkScore(ModelBenchmark("Example", null)))
        assertNull(formatBenchmarkScore(ModelBenchmark("Example", null, scoreText = " ")))
        assertNull(formatBenchmarkScore(ModelBenchmark("Example", Double.NaN)))
        assertEquals("1.3", formatBenchmarkScore(ModelBenchmark("Example", 1.25, scoreText = " ")))
    }

    @Test fun namesSortAtoZIgnoringCaseAndSameNameScoresSortDescendingKeepingSourceKeys() {
        val benchmarks = listOf(ModelBenchmark("Zulu", 1.0), ModelBenchmark("alpha", 1.0, variant = "first"),
            ModelBenchmark("Beta", 1.0), ModelBenchmark("Alpha", 2.0, variant = "second"))
        val sorted = alphabeticalBenchmarkEntries(benchmarks)
        assertEquals(listOf("Alpha", "alpha", "Beta", "Zulu"), sorted.map { it.value.name })
        assertEquals(listOf(3, 1, 2, 0), sorted.map { it.index })
        assertEquals(listOf("second", "first"), sorted.take(2).map { it.value.variant })
        assertEquals("Zulu", benchmarks.first().name)
        assertTrue(alphabeticalBenchmarkEntries(emptyList()).isEmpty())
    }

    @Test fun sameNameScoresUseUnroundedValuesAndEqualScoresKeepTheirOriginalOrder() {
        val entries = alphabeticalBenchmarkEntries(listOf(
            ModelBenchmark("Alpha", 89.11), ModelBenchmark("Alpha", 89.14), ModelBenchmark("Alpha", 89.14),
        ))
        assertEquals(listOf(1, 2, 0), entries.map { it.index })
        assertEquals(listOf("89.1", "89.1", "89.1"), entries.map { formatBenchmarkScore(it.value) })
    }

    @Test fun textAndMissingScoresStayStableAfterNumericScoresWithoutParsingText() {
        val entries = alphabeticalBenchmarkEntries(listOf(
            ModelBenchmark("Alpha", null, scoreText = "999 ± 18"), ModelBenchmark("Alpha", 0.0),
            ModelBenchmark("Alpha", null), ModelBenchmark("Alpha", 42.0),
            ModelBenchmark("Alpha", null, scoreText = "100%"), ModelBenchmark("Alpha", -1.0),
        ))
        assertEquals(listOf(3, 1, 5, 0, 2, 4), entries.map { it.index })
    }
}
