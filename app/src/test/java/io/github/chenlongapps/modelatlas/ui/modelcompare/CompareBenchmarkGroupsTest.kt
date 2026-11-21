package io.github.chenlongapps.modelatlas.ui.modelcompare

import io.github.chenlongapps.modelatlas.data.model.ModelBenchmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareBenchmarkGroupsTest {
    private val record = ModelBenchmark("Benchmark", 10.0, benchmarkId = "benchmark", version = "1",
        metric = "accuracy", unit = "%", dataset = "full", harness = "runner 1", reasoningEffort = "high",
        toolsEnabled = false, variant = "standard", higherIsBetter = true, date = "2026-11-01",
        source = "https://example.org/leaderboard")

    @Test fun onlySharedBenchmarkIdentitiesWithValidScoresAreReturned() {
        val left = record.copy(score = 0.0)
        val right = record.copy(name = "A different display name", score = null, scoreText = "16 ± 2")
        val group = compareBenchmarkGroups(
            listOf(left, record.copy(benchmarkId = "left")),
            listOf(right, record.copy(benchmarkId = "right")),
        ).single()
        assertEquals(left, group.first)
        assertEquals(right, group.second)
        assertFalse(group.referenceOnly)
        assertTrue(compareBenchmarkGroups(null, listOf(right)).isEmpty())
    }

    @Test fun idActualVersionMetricAndUnitMustMatchAndBeExplicit() {
        val mismatches = listOf(record.copy(benchmarkId = "another"), record.copy(version = "2"),
            record.copy(metric = "error"), record.copy(unit = "points"), record.copy(sourceType = "test"))
        val incomplete = listOf(record.copy(benchmarkId = null), record.copy(benchmarkId = "unmapped:id"),
            record.copy(version = "unspecified"), record.copy(version = null),
            record.copy(metric = " "), record.copy(unit = null))
        mismatches.forEach { assertTrue(compareBenchmarkGroups(listOf(record), listOf(it)).isEmpty()) }
        incomplete.forEach { assertTrue(compareBenchmarkGroups(listOf(it), listOf(it)).isEmpty()) }
    }

    @Test fun highestRecognizedTierIsChosenIndependentlyOfTheOtherSideAndScore() {
        val left = record.copy(score = 2.0, reasoningEffort = "  X-HIGH Reasoning ")
        val right = record.copy(score = 3.0, reasoningEffort = "maximum reasoning effort")
        val lower = listOf("off", "minimal", "LOW", "mid", "High thinking").map {
            record.copy(score = 99.0, reasoningEffort = it)
        }
        val group = compareBenchmarkGroups(lower + left, listOf(left, right) + lower).single()
        assertEquals(left, group.first)
        assertEquals(right, group.second)
    }

    @Test fun unknownAndMissingEffortsDoNotOutrankKnownEffortOrGetGuessed() {
        val unranked = listOf(null, " ", "Adaptive/Max", "0.99", "default", "High/Low", "unknown").map {
            record.copy(score = 99.0, reasoningEffort = it)
        }
        val group = compareBenchmarkGroups(listOf(record) + unranked, unranked + record).single()
        assertEquals(record, group.first)
        assertEquals(record, group.second)
        unranked.forEach { assertTrue(compareBenchmarkGroups(listOf(it), listOf(record)).isEmpty()) }
        assertTrue(compareBenchmarkGroups(unranked, unranked).isEmpty())
    }

    @Test fun bothUnreportedEffortsCanPairOnlyWithAnExplicitSharedProtocolAndSource() {
        val unreported = record.copy(reasoningEffort = null, toolsEnabled = null, variant = null)
        val blank = unreported.copy(reasoningEffort = " ", score = 13.0)
        assertEquals(blank, compareBenchmarkGroups(listOf(unreported), listOf(blank)).single().second)
        val noSource = unreported.copy(source = null)
        assertTrue(compareBenchmarkGroups(listOf(noSource), listOf(noSource)).isEmpty())
    }

    @Test fun invalidScoresCannotBlockAValidLowerTierOrMakeACommonBenchmark() {
        val invalid = listOf(record.copy(score = null), record.copy(score = Double.NaN, scoreText = " "),
            record.copy(score = Double.POSITIVE_INFINITY)).map { it.copy(reasoningEffort = "max") }
        assertTrue(compareBenchmarkGroups(invalid, listOf(record)).isEmpty())
        assertEquals(record, compareBenchmarkGroups(invalid + record, listOf(record)).single().first)
    }

    @Test fun mismatchedOrIncompleteConditionsAreReferenceOnlyWithoutFallingBackToLowerEffort() {
        val mismatches = listOf(record.copy(dataset = "subset"), record.copy(harness = "runner 2"),
            record.copy(toolsEnabled = true), record.copy(toolsEnabled = null), record.copy(variant = "other"),
            record.copy(source = "https://example.org/other"), record.copy(date = null),
            record.copy(sourceType = "official"), record.copy(higherIsBetter = false))
        mismatches.forEach { mismatch ->
            val highest = mismatch.copy(reasoningEffort = "max")
            val group = compareBenchmarkGroups(listOf(record, highest), listOf(record)).single()
            assertTrue(group.referenceOnly)
            assertEquals(highest, group.first)
        }
        listOf(record.copy(dataset = "unknown"), record.copy(harness = null)).forEach { incomplete ->
            assertTrue(compareBenchmarkGroups(listOf(incomplete), listOf(incomplete)).single().referenceOnly)
        }
        val noSource = record.copy(source = null, toolsEnabled = null)
        assertTrue(compareBenchmarkGroups(listOf(noSource), listOf(noSource)).isEmpty())
    }

    @Test fun sameTierDuplicatesOrMultipleMatchedProtocolsNeverChooseArbitrarily() {
        val alternative = record.copy(score = 90.0, reasoningEffort = " HIGH reasoning ", variant = "alternate")
        assertTrue(compareBenchmarkGroups(listOf(record, alternative.copy(variant = record.variant)), listOf(record)).isEmpty())
        assertTrue(compareBenchmarkGroups(listOf(record, record), listOf(record)).isEmpty())
        assertTrue(compareBenchmarkGroups(listOf(record, alternative), listOf(record, alternative)).isEmpty())
        assertEquals(record, compareBenchmarkGroups(listOf(alternative, record), listOf(record)).single().first)
    }

    @Test fun ordersCommonRowsByNameThenVersionWithStableKeys() {
        val a = record.copy(name = "alpha", version = "2")
        val b = record.copy(name = "Zoo", benchmarkId = "zoo")
        val c = a.copy(version = "1")
        val groups = compareBenchmarkGroups(listOf(b, a, c), listOf(a, c, b))
        assertEquals(listOf(c, a, b), groups.map { it.first })
        assertEquals(groups.size, groups.map { it.key.itemKey }.toSet().size)
    }

    @Test fun realAtlasIndexScoresFromDifferentModelPagesAreShownAsReference() {
        val claude = ModelBenchmark("Artificial Analysis Intelligence Index", 64.563595756626,
            benchmarkId = "artificial-analysis-intelligence-index-v5", version = "5.0.1", unit = "points",
            metric = "index score", reasoningEffort = "Max", variant = "Default Fallback",
            source = "https://artificialanalysis.ai/models/claude-fable-5-2")
        val gpt = claude.copy(score = 62.1387990593969, reasoningEffort = "max", variant = null,
            source = "https://artificialanalysis.ai/models/gpt-6-2-astra")
        val sideBySide = compareBenchmarkGroups(listOf(claude), listOf(gpt)).single()
        assertTrue(sideBySide.referenceOnly)
        assertEquals(claude, sideBySide.first)
        assertEquals(gpt, sideBySide.second)
    }

    @Test fun preferExactLeaderboardUrlAndNeverChooseAmongAmbiguousSameHostRecords() {
        val published = record.copy(source = "https://www.vals.ai/benchmarks/vals_index", variant = "fallback included")
        val rescored = published.copy(source = "https://www.vals.ai/rescored", score = 99.0)
        val gpt = published.copy(variant = null, reasoningEffort = "Max")
        val group = compareBenchmarkGroups(listOf(rescored, published), listOf(gpt)).single()
        assertTrue(group.referenceOnly)
        assertEquals(published, group.first)
        val anotherPage = gpt.copy(source = "https://www.vals.ai/another-page")
        assertTrue(compareBenchmarkGroups(listOf(rescored, published), listOf(anotherPage)).isEmpty())
        assertTrue(compareBenchmarkGroups(listOf(published, published), listOf(gpt)).isEmpty())
    }

    @Test fun explicitVersionAliasesAreReferenceOnlyAndNeverChangeOriginalVersions() {
        val canonical = record.copy(version = "4.0.0", benchmarkDefinitionVersion = "4.0.0")
        val alias = canonical.copy(version = "4.0")
        val group = compareBenchmarkGroups(listOf(canonical), listOf(alias)).single()
        assertTrue(group.referenceOnly)
        assertEquals("4.0.0", group.key.version)
        assertEquals("4.0.0", group.first.version)
        assertEquals("4.0", group.second.version)
        assertTrue(compareBenchmarkGroups(listOf(canonical), listOf(alias.copy(benchmarkDefinitionVersion = null))).isEmpty())
        assertTrue(compareBenchmarkGroups(listOf(canonical), listOf(alias.copy(version = null))).isEmpty())
    }

    @Test fun protocolMatchesTakePrecedenceAndRowsWithUnrelatedSourcesStayHidden() {
        val reference = record.copy(benchmarkId = "reference", name = "AAA", dataset = null, harness = null)
        val unrelated = record.copy(benchmarkId = "unrelated", name = "BBB")
        val groups = compareBenchmarkGroups(listOf(record, reference, unrelated), listOf(record, reference,
            unrelated.copy(source = "https://example.net/leaderboard")))
        assertEquals(listOf("benchmark", "reference"), groups.map { it.key.id })
        assertEquals(listOf(false, true), groups.map { it.referenceOnly })
        val invalidUrl = reference.copy(source = "file:///tmp/local-standings")
        assertTrue(compareBenchmarkGroups(listOf(invalidUrl), listOf(invalidUrl)).isEmpty())
    }
}
