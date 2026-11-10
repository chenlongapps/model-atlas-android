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

    @Test fun compatibleVersionAliasesRemainSeparateAndUnmatchedMetricsUnitsAreExcluded() {
        val initial = result("a", "lab/a", 80.0)
        val rows = listOf(initial, initial.copy(score = initial.score.copy(id = "v", version = "1.0", score = 99.0)),
            initial.copy(score = initial.score.copy(id = "old", version = "0")),
            initial.copy(score = initial.score.copy(id = "metric", metric = "different")),
            initial.copy(score = initial.score.copy(id = "unit", unit = "points")))
        val boards = listOf(BenchmarkRankingRecord(definition.copy(compatibleVersions = listOf("1.0")), rows)).toBenchmarkBoards()
        assertEquals(listOf("1", "1.0"), boards.map { it.version })
        assertEquals(2, boards.map { it.id }.toSet().size)
        assertEquals(listOf(80.0, 99.0), boards.map { it.rankings.single().score })
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
