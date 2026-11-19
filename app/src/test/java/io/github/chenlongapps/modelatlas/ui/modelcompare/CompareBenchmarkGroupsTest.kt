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

    @Test fun ignoresRecordsWithoutScoresAndNeverPairsSyntheticWithRealScores() {
        val missing = listOf(base.copy(score = null), base.copy(score = Double.NaN, scoreText = " "))
        assertTrue(compareBenchmarkGroups(missing, null).isEmpty())
        val groups = compareBenchmarkGroups(listOf(base), listOf(base.copy(sourceType = "test")) + missing)
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
}
