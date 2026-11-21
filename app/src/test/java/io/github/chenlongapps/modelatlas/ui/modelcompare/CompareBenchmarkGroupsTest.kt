package io.github.chenlongapps.modelatlas.ui.modelcompare

import io.github.chenlongapps.modelatlas.data.model.ModelBenchmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareBenchmarkGroupsTest {
    private val base = ModelBenchmark("Benchmark", 10.0, benchmarkId = "benchmark", version = "1",
        metric = "accuracy", unit = "%")

    @Test fun sharedGroupsKeepAllConfigurationsZeroAndTextInOriginalOrder() {
        val first = listOf(base.copy(score = 0.0, toolsEnabled = false), base.copy(score = 23.0, reasoningEffort = "High"))
        val second = listOf(base.copy(score = null, scoreText = "16 ± 2"), base.copy(score = 9.0, variant = "alternate"))
        val group = compareBenchmarkGroups(first, second).single()
        assertTrue(group.shared)
        assertEquals(first, group.first)
        assertEquals(second, group.second)
    }

    @Test fun idActualVersionMetricAndUnitMustMatchExactly() {
        val alternatives = listOf(
            base.copy(benchmarkId = "another"), base.copy(version = "2"), base.copy(version = null),
            base.copy(version = "unspecified"), base.copy(metric = "error"), base.copy(unit = "points"),
            base.copy(unit = null),
        )
        val groups = compareBenchmarkGroups(listOf(base), alternatives)
        assertEquals(1 + alternatives.size, groups.size)
        assertEquals(groups.size, groups.map { it.key.itemKey }.toSet().size)
        assertFalse(groups.any { it.shared })
    }

    @Test fun absentVersionsAreNotReplacedAndMissingOrUnmappedIdentityDoesNotPairByName() {
        val noVersion = base.copy(version = null)
        val unidentified = listOf(base.copy(benchmarkId = null), base.copy(benchmarkId = "unmapped:hash"))
        val groups = compareBenchmarkGroups(unidentified + noVersion, unidentified + noVersion)
        assertEquals(5, groups.size)
        assertEquals(null, groups.single { it.shared }.key.version)
    }

    @Test fun keepsRecordsWithoutScoresAndNeverPairsSyntheticWithRealScores() {
        val missing = listOf(base.copy(score = null), base.copy(score = Double.NaN, scoreText = " "))
        assertEquals(missing, compareBenchmarkGroups(missing, null).single().first)
        val groups = compareBenchmarkGroups(listOf(configured), listOf(configured.copy(sourceType = "test")))
        assertEquals(2, groups.size)
        assertFalse(groups.any { it.shared })
        assertEquals(1, groups.count { it.key.test })
    }

    @Test fun ordersGroupsByNameAndVersionsIndependentOfInputOrder() {
        val a = base.copy(name = "alpha", version = "2")
        val b = base.copy(name = "Zoo", benchmarkId = "zoo")
        val c = a.copy(version = "1")
        val groups = compareBenchmarkGroups(listOf(b, a, c), emptyList())
        assertEquals(listOf(c, a, b), groups.map { it.first.single() })
    }

    private val configured = base.copy(dataset = "full", harness = "runner 1", reasoningEffort = "high",
        toolsEnabled = false, variant = "standard", higherIsBetter = true, date = "2026-11-01",
        source = "https://example.org/leaderboard")

    @Test fun uniqueExactConfigurationProvidesOriginalPairNotHighestScore() {
        val left = configured.copy(score = 0.0)
        val right = configured.copy(score = null, scoreText = "16 ± 2")
        val extra = configured.copy(score = 99.0, variant = "alternate")
        val group = compareBenchmarkGroups(listOf(extra, left), listOf(right)).single()
        assertEquals(1, group.comparableCount)
        assertEquals(left, group.representative?.first)
        assertEquals(right, group.representative?.second)
        assertEquals(extra, group.configurations.first().first)
    }

    @Test fun differentOrIncompleteConditionsNeverPair() {
        val alternatives = listOf(
            configured.copy(dataset = "subset"), configured.copy(harness = "runner 2"),
            configured.copy(reasoningEffort = "low"), configured.copy(toolsEnabled = true),
            configured.copy(toolsEnabled = null), configured.copy(variant = "other"),
            configured.copy(source = "https://example.org/other"), configured.copy(date = null),
            configured.copy(higherIsBetter = false),
        )
        alternatives.forEach { alternative ->
            val group = compareBenchmarkGroups(listOf(configured), listOf(alternative)).single()
            assertEquals(0, group.comparableCount)
            assertEquals(2, group.configurations.size)
        }
        listOf(configured.copy(version = "unspecified"), configured.copy(version = null), configured.copy(unit = null),
            configured.copy(dataset = "unknown"), configured.copy(harness = null),
            configured.copy(source = null, toolsEnabled = null),
        ).forEach { incomplete ->
            assertEquals(0, compareBenchmarkGroups(listOf(incomplete), listOf(incomplete)).single().comparableCount)
        }
    }

    @Test fun sharedSourceWithExplicitProtocolCanPairWhenAnOptionalFieldIsNotReported() {
        val record = configured.copy(toolsEnabled = null)
        val group = compareBenchmarkGroups(listOf(record), listOf(record.copy(score = 25.0))).single()
        assertEquals(1, group.comparableCount)
        assertEquals(null, group.representative?.first?.toolsEnabled)
    }

    @Test fun duplicateKeysAndMultipleComparablePairsDoNotChooseAnArbitrarySummary() {
        val duplicate = configured.copy(score = 90.0)
        val other = configured.copy(reasoningEffort = "low")
        val ambiguous = compareBenchmarkGroups(listOf(configured, duplicate), listOf(configured)).single()
        assertEquals(0, ambiguous.comparableCount)
        assertEquals(3, ambiguous.configurations.size)
        val multiple = compareBenchmarkGroups(listOf(configured, other), listOf(other, duplicate)).single()
        assertEquals(2, multiple.comparableCount)
        assertEquals(null, multiple.representative)
    }

    @Test fun matchedConfigurationWithMissingScoreKeepsBothRecordsWithoutSummary() {
        val missing = configured.copy(score = null)
        val group = compareBenchmarkGroups(listOf(missing), listOf(configured)).single()
        assertEquals(0, group.comparableCount)
        assertEquals(missing, group.configurations.single().first)
        assertEquals(configured, group.configurations.single().second)
    }
}
