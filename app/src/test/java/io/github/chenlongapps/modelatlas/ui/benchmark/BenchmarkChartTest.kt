package io.github.chenlongapps.modelatlas.ui.benchmark

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.chenlongapps.modelatlas.data.model.BenchmarkBoardEntry
import java.math.BigDecimal
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.*
import org.junit.Test

class BenchmarkChartTest {
    @Test fun referenceScoresProduce55To75AndLocallyScaledBars() {
        val range = benchmarkChartRange(listOf(72.4, 65.1, 58.7))
        assertEquals("55", formatChartTick(range.start))
        assertEquals("75", formatChartTick(range.end))
        assertEquals(0.87f, range.fraction(72.4), 0.0001f)
        assertEquals(0.185f, range.fraction(58.7), 0.0001f)
    }

    @Test fun rangeUsesTheProvidedResultSetNotADefaultPercentageDomain() {
        assertNotEquals(benchmarkChartRange(listOf(70.0, 72.0, 74.0)), benchmarkChartRange(listOf(10.0, 70.0, 74.0)))
        val elo = benchmarkChartRange(listOf(1234.0, 1300.0, 1450.0))
        assertTrue(elo.end > BigDecimal(1450))
        assertTrue(elo.start > BigDecimal(100))
    }

    @Test fun singletonEqualZeroNegativeAndTinyRangesStayFiniteAndVisible() {
        for (values in listOf(listOf(0.0), listOf(80.0), listOf(1.0, 1.0), listOf(-10.0, -9.9),
            listOf(0.000000001, 0.00000000101), listOf(Double.MIN_VALUE), listOf(-Double.MAX_VALUE, Double.MAX_VALUE))) {
            val range = benchmarkChartRange(values)
            assertTrue(range.end > range.start)
            values.forEach { value ->
                assertTrue(range.start <= BigDecimal.valueOf(value))
                assertTrue(range.end >= BigDecimal.valueOf(value))
                assertTrue(range.fraction(value).isFinite())
            }
        }
    }

    @Test fun honorsOnlyExplicitValidBoundsAndHandlesInvalidInput() {
        val range = benchmarkChartRange(listOf(0.0, 100.0), 0.0, 100.0)
        assertEquals(0, range.start.compareTo(BigDecimal.ZERO))
        assertEquals(0, range.end.compareTo(BigDecimal(100)))
        val invalidBounds = benchmarkChartRange(listOf(0.0, 100.0), 90.0, 10.0)
        assertTrue(invalidBounds.start <= BigDecimal.ZERO)
        assertTrue(invalidBounds.end >= BigDecimal(100))
        assertEquals(benchmarkChartRange(emptyList()), benchmarkChartRange(listOf(Double.NaN, Double.POSITIVE_INFINITY)))
    }

    @Test fun brandingIsStableAcrossRankingsAndReadableOnBothTracks() {
        val labs = listOf("deepseek", "anthropic", "google", "openai", "xai", "nvidia", "unknown", null)
        for (track in listOf(Color(0xFFF0EFF0), Color(0xFF333333))) {
            labs.forEach { lab ->
                val color = labChartColor(lab, track)
                assertEquals(color, labChartColor(lab, track))
                val contrast = (max(color.luminance(), track.luminance()) + .05f) / (min(color.luminance(), track.luminance()) + .05f)
                assertTrue("$lab: $contrast", contrast >= 3f)
            }
            assertNotEquals(labChartColor("deepseek", track), labChartColor("anthropic", track))
        }
    }

    @Test fun formatsNumericOneDecimalAndTextVerbatimWithoutInferringPercentages() {
        val entry = BenchmarkBoardEntry("a", "lab/a", "A", "lab", 95.15678, null)
        assertEquals("95.2%", formatBoardScore(entry, "percent"))
        assertEquals("95.2 points", formatBoardScore(entry, "points"))
        assertEquals("95.2", formatBoardScore(entry, null))
        assertEquals("899 ± 18", formatBoardScore(entry.copy(scoreText = "899 ± 18"), "points"))
        assertEquals("0.0%", formatBoardScore(entry.copy(score = 0.0), "percent"))
    }

    @Test fun compactPointsAliasDoesNotChangeOtherUnitsTextOrFullLeaderboard() {
        val entry = BenchmarkBoardEntry("a", "lab/a", "A", "lab", 60.0, null)
        assertEquals("60.0 pts", formatCompactBoardScore(entry, "points"))
        assertEquals("60.0 points", formatBoardScore(entry, "points"))
        assertEquals("60.0 Elo points", formatCompactBoardScore(entry, "Elo points"))
        assertEquals("60.0%", formatCompactBoardScore(entry, "percent"))
        assertEquals("60.0", formatCompactBoardScore(entry, null))
        assertEquals("899 ± 18", formatCompactBoardScore(entry.copy(score = null, scoreText = "899 ± 18"), "points"))
    }
}
