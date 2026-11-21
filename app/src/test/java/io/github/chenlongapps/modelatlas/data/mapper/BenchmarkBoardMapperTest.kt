package io.github.chenlongapps.modelatlas.data.mapper

import io.github.chenlongapps.modelatlas.data.local.BenchmarkRankingModel
import io.github.chenlongapps.modelatlas.data.local.BenchmarkRankingRecord
import io.github.chenlongapps.modelatlas.data.local.BenchmarkRankingScoreRecord
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkDefinitionEntity
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkScoreEntity
import org.junit.Assert.*
import org.junit.Test

class BenchmarkBoardMapperTest {
    private val definition = BenchmarkDefinitionEntity("bench", "Benchmark", "1", "accuracy", unit = "percent", higherIsBetter = true)
    private fun result(id: String, model: String, value: Double?, text: String? = null) = BenchmarkRankingScoreRecord(
        BenchmarkScoreEntity(id, model, "bench", value, metric = "accuracy", unit = "percent", higherIsBetter = true,
            version = "1", scoreText = text, reasoningEffort = "High", harness = "Agent"),
        BenchmarkRankingModel(model, model, "llm", null, null),
    )

    @Test fun sortsByDescendingPopularityWithMissingLastAndStableNameVersionIdTies() {
        fun record(id: String, name: String, popularity: Double?, version: String = "1") =
            BenchmarkRankingRecord(definition.copy(id = id, name = name, version = version, popularity = popularity), emptyList())
        val records = listOf(
            record("missing-z", "Zulu", null), record("zero", "Zero", 0.0),
            record("tie-z", "alpha", 0.9, "2"), record("popular", "Zulu", 0.98),
            record("tie-b", "Alpha", 0.9), record("tie-a", "Alpha", 0.9),
            record("missing-a", "Alpha", null), record("lower", "Alpha", 0.6),
        )
        val boards = records.toBenchmarkBoards()
        assertEquals(listOf("popular", "tie-a", "tie-b", "tie-z", "lower", "zero", "missing-a", "missing-z"),
            boards.map { it.benchmarkId })
        assertEquals(boards, records.reversed().toBenchmarkBoards())
        assertEquals(listOf(0.98, 0.9, 0.9, 0.9, 0.6, 0.0, null, null), boards.map { it.popularity })
    }

    @Test fun compatibleVersionsInheritPopularityWithoutChangingModelRankings() {
        val rows = listOf(result("a", "lab/a", 10.0), result("b", "lab/b", 20.0),
            result("c", "lab/c", 30.0).let { it.copy(score = it.score.copy(version = "1.0")) })
        val record = BenchmarkRankingRecord(definition.copy(compatibleVersions = listOf("1.0")), rows)
        val original = listOf(record).toBenchmarkBoards().single()
        val updated = listOf(record.copy(definition = record.definition.copy(popularity = 0.98)))
            .toBenchmarkBoards().single()
        assertEquals(0.98, updated.popularity!!, 0.0)
        assertEquals(original.rankings, updated.rankings)
        assertEquals(original.id, updated.id)
        assertEquals("1", updated.version)
    }

    @Test fun takesHighestRawScorePerCanonicalModelAndRetainsWinningConditions() {
        val low = result("a", "deepseek/a", 65.04)
        val high = result("b", "deepseek/a", 65.049).let { it.copy(score = it.score.copy(reasoningEffort = "Max")) }
        val other = result("c", "google/b", 65.045)
        val board = listOf(BenchmarkRankingRecord(definition, listOf(low, other, high))).toBenchmarkBoards().single()
        assertEquals(listOf("deepseek/a", "google/b"), board.rankings.map { it.modelId })
        assertEquals(65.049, board.rankings.first().score!!, 0.0)
        assertEquals("Max", board.rankings.first().reasoningEffort)
        assertEquals("Agent", board.rankings.first().harness)
        assertEquals("deepseek", board.rankings.first().lab)
    }

    @Test fun tiesShareCompetitionRankAndStableIdsSelectTiedConfiguration() {
        val rows = listOf(result("z", "lab/a", 90.0), result("b", "lab/b", 90.0),
            result("a", "lab/a", 90.0), result("c", "lab/c", 0.0))
        val board = listOf(BenchmarkRankingRecord(definition, rows)).toBenchmarkBoards().single()
        val reversed = listOf(BenchmarkRankingRecord(definition, rows.reversed())).toBenchmarkBoards().single()
        assertEquals(board, reversed)
        assertEquals(listOf(1, 1, 3), board.rankings.map { it.rank })
        assertEquals("a", board.rankings.first().scoreId)
        assertEquals(0.0, board.rankings.last().score!!, 0.0)
    }

    @Test fun compatibleVersionsMergeIntoDefinitionVersionWithPerModelDedupAndRawVersionsIntact() {
        val terminal = definition.copy(id = "terminal-bench-v4", name = "Terminal-Bench", version = "4.0.0",
            compatibleVersions = listOf("4.0"))
        fun row(id: String, model: String, value: Double?, version: String?, effort: String = "High") =
            BenchmarkRankingScoreRecord(
                BenchmarkScoreEntity(id, model, terminal.id, value, metric = "accuracy", unit = "percent",
                    higherIsBetter = true, version = version, reasoningEffort = effort, harness = "Agent"),
                BenchmarkRankingModel(model, model, "llm", null, null))
        val rows = listOf(
            row("old", "lab/a", 80.0, "4.0", "Medium"),
            row("new", "lab/a", 84.0, "4.0.0", "Max"),
            row("other", "lab/b", 75.0, "4.0.0"),
            row("incompatible", "lab/c", 99.0, "4.1"),
            row("missing", "lab/d", 70.0, null),
            row("metric", "lab/e", 100.0, "4.0").let { it.copy(score = it.score.copy(metric = "different")) },
            row("unit", "lab/f", 101.0, "4.0").let { it.copy(score = it.score.copy(unit = "points")) },
            row("direction", "lab/g", 102.0, "4.0").let { it.copy(score = it.score.copy(higherIsBetter = false)) },
        )
        val board = listOf(BenchmarkRankingRecord(terminal, rows)).toBenchmarkBoards().single()
        assertEquals("4.0.0", board.version)
        assertEquals(listOf("lab/a", "lab/b"), board.rankings.map { it.modelId })
        assertEquals(listOf(84.0, 75.0), board.rankings.map { it.score })
        assertEquals("new", board.rankings.first().scoreId)
        assertEquals("Max", board.rankings.first().reasoningEffort)
        assertEquals("Agent", board.rankings.first().harness)
        // 只统一榜单分组与展示版本，原始成绩及其条件保持不变。
        assertEquals(listOf("4.0", "4.0.0", "4.0.0", "4.1", null, "4.0", "4.0", "4.0"),
            rows.map { it.score.version })
        assertEquals("Medium", rows.first().score.reasoningEffort)
        assertEquals(board, listOf(BenchmarkRankingRecord(terminal, rows.reversed())).toBenchmarkBoards().single())
    }

    @Test fun differentDefinitionsRemainIndependentBoardsEvenWithSameName() {
        val v4 = definition.copy(id = "terminal-bench-v4", name = "Terminal-Bench", version = "4.0.0")
        val v5 = v4.copy(id = "terminal-bench-v5", version = "5.0")
        val v4Row = result("a", "lab/a", 80.0).let { it.copy(score = it.score.copy(benchmarkId = v4.id, version = "4.0.0")) }
        val v5Row = result("b", "lab/b", 90.0).let { it.copy(score = it.score.copy(benchmarkId = v5.id, version = "5.0")) }
        val boards = listOf(BenchmarkRankingRecord(v4, listOf(v4Row)),
            BenchmarkRankingRecord(v5, listOf(v5Row))).toBenchmarkBoards()
        assertEquals(listOf("terminal-bench-v4", "terminal-bench-v5"), boards.map { it.benchmarkId })
        assertEquals(listOf("4.0.0", "5.0"), boards.map { it.version })
        assertEquals(listOf("lab/a"), boards[0].rankings.map { it.modelId })
        assertEquals(listOf("lab/b"), boards[1].rankings.map { it.modelId })
    }

    @Test fun unspecifiedDefinitionCombinesMissingAndExplicitVersionsAndTakesHighestPerModel() {
        val initial = result("a", "lab/a", 80.0)
        val rows = listOf(initial.copy(score = initial.score.copy(version = null)),
            initial.copy(score = initial.score.copy(id = "b", version = "2", score = 99.0)),
            result("c", "lab/b", 90.0).let { it.copy(score = it.score.copy(version = "unspecified")) },
            result("d", "lab/c", 85.0),
            result("text", "lab/d", null, "100%"),
            initial.copy(score = initial.score.copy(id = "metric", metric = "different", score = 101.0)),
            initial.copy(score = initial.score.copy(id = "unit", unit = "points", score = 102.0)),
            initial.copy(score = initial.score.copy(id = "unmapped", benchmarkId = "unmapped:test", score = 103.0)))
        val record = BenchmarkRankingRecord(definition.copy(version = "unspecified"), rows)
        val board = listOf(record).toBenchmarkBoards().single()
        assertEquals("unspecified", board.version)
        assertEquals(listOf("lab/a", "lab/b", "lab/c"), board.rankings.map { it.modelId })
        assertEquals(listOf(99.0, 90.0, 85.0), board.rankings.map { it.score })
        assertEquals("b", board.rankings.first().scoreId)
        assertEquals(listOf(record.copy(scores = rows.reversed())).toBenchmarkBoards().single(), board)
        assertEquals(board.id, listOf(record.copy(scores = emptyList())).toBenchmarkBoards().single().id)
        assertNull(rows.first().score.version)
        assertEquals("2", rows[1].score.version)
    }

    @Test fun specifiedDefinitionStillRejectsMissingUnspecifiedAndUnknownVersions() {
        val row = result("a", "lab/a", 80.0)
        val rows = listOf(null, "unspecified", "2").mapIndexed { index, version ->
            row.copy(score = row.score.copy(id = "$index", version = version))
        }
        assertTrue(listOf(BenchmarkRankingRecord(definition, rows)).toBenchmarkBoards().single().rankings.isEmpty())
    }

    @Test fun textConfidenceIntervalsAndMissingNonFiniteScoresNeverEnterRankings() {
        val rows = listOf(result("a", "lab/a", null, "899 ± 18"), result("b", "lab/b", 100.0, "2% (rounded display)"),
            result("c", "lab/c", Double.NaN), result("d", "lab/d", Double.POSITIVE_INFINITY))
        val board = listOf(BenchmarkRankingRecord(definition, rows)).toBenchmarkBoards().single()
        assertTrue(board.rankings.isEmpty())
    }

    @Test fun unknownAndLowerBetterDefinitionsDoNotPickTheHighestAsBest() {
        for (direction in listOf(null, false)) {
            val rows = listOf(result("a", "lab/a", 10.0), result("b", "lab/a", 20.0))
                .map { it.copy(score = it.score.copy(higherIsBetter = direction)) }
            val board = listOf(BenchmarkRankingRecord(definition.copy(higherIsBetter = direction), rows)).toBenchmarkBoards().single()
            assertTrue(board.rankings.isEmpty())
        }
    }

    @Test fun excludesRemovedAndNonLlmModelsAndUsesChineseDescription() {
        val row = result("a", "lab/a", 1.0)
        val board = listOf(BenchmarkRankingRecord(definition.copy(description = "English", descriptionZhCn = "中文"),
            listOf(row.copy(model = null), row.copy(model = row.model!!.copy(type = "embedding")), row)))
            .toBenchmarkBoards().single()
        assertEquals(1, board.rankings.size)
        assertEquals("中文", board.description)
    }

    @Test fun emptyDefinitionStillOffersAnEmptyStateAndNoInventedRows() {
        val board = listOf(BenchmarkRankingRecord(definition, emptyList())).toBenchmarkBoards().single()
        assertEquals("1", board.version)
        assertTrue(board.rankings.isEmpty())
    }
}
