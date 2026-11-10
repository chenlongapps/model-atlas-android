package io.github.chenlongapps.modelatlas.data.mapper

import io.github.chenlongapps.modelatlas.data.local.BenchmarkRankingRecord
import io.github.chenlongapps.modelatlas.data.local.BenchmarkRankingScoreRecord
import io.github.chenlongapps.modelatlas.data.model.BenchmarkBoard
import io.github.chenlongapps.modelatlas.data.model.BenchmarkBoardEntry
import io.github.chenlongapps.modelatlas.data.model.LlmInclusionPolicy

/** 只对同版本/指标/单位的有限数值取每模型最高分；配置不合并成所谓「相同配置」。 */
internal fun List<BenchmarkRankingRecord>.toBenchmarkBoards(): List<BenchmarkBoard> = flatMap { record ->
    val definition = record.definition
    val eligible = record.scores.filter { (score, model) ->
        model != null && LlmInclusionPolicy.includes(model.type, model.inputModalities, model.outputModalities) &&
            score.benchmarkId == definition.id && score.metric == definition.metric &&
            (score.version == definition.version || score.version in definition.compatibleVersions.orEmpty()) &&
            score.unit == definition.unit && score.higherIsBetter == definition.higherIsBetter
    }
    val groups = eligible.groupBy { it.score.version!! }.ifEmpty { mapOf(definition.version to emptyList()) }
    groups.map { (version, records) ->
        val entries = records.sortedBy { it.score.id }.map(BenchmarkRankingScoreRecord::toBoardEntry)
        val canRank = definition.higherIsBetter == true
        val numeric = entries.filter { it.score != null && it.scoreText.isNullOrBlank() }
        val best = if (canRank) numeric.groupBy { it.modelId }.values.map { candidates ->
            // 稳定 ID 顺序决定同模型同分取哪条配置，不修改源成绩。
            candidates.maxBy { it.score!! }
        }.sortedWith(compareByDescending<BenchmarkBoardEntry> { it.score }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.modelName }.thenBy { it.modelId }) else emptyList()
        var rank = 0
        val ranked = best.mapIndexed { index, entry ->
            if (index == 0 || entry.score != best[index - 1].score) rank = index + 1
            entry.copy(rank = rank)
        }
        BenchmarkBoard(
            id = listOf(definition.id, version, definition.metric, definition.unit.orEmpty())
                .joinToString("|") { "${it.length}:$it" },
            benchmarkId = definition.id, name = definition.name, version = version,
            metric = definition.metric, unit = definition.unit, category = definition.category,
            description = definition.descriptionZhCn?.takeIf(String::isNotBlank) ?: definition.description,
            higherIsBetter = definition.higherIsBetter, minScore = definition.minScore, maxScore = definition.maxScore,
            rankings = ranked,
        )
    }
}.sortedWith(compareBy<BenchmarkBoard, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.version }.thenBy { it.id })

private fun BenchmarkRankingScoreRecord.toBoardEntry(): BenchmarkBoardEntry = BenchmarkBoardEntry(
    scoreId = score.id, modelId = model!!.id, modelName = model.name?.takeIf(String::isNotBlank) ?: model.id,
    lab = model.id.substringBefore('/', "").takeIf(String::isNotBlank),
    score = score.score?.takeIf(Double::isFinite), scoreText = score.scoreText?.takeIf(String::isNotBlank),
    reasoningEffort = score.reasoningEffort, variant = score.variant, harness = score.harness,
    dataset = score.subset, toolsEnabled = score.toolsEnabled,
)
