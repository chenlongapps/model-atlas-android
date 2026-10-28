package io.github.chenlongapps.modelatlas.data.benchmark

import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkScoreEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BenchmarkSnapshot(val schemaVersion: Int, val version: Long, val scores: List<BenchmarkScore>) {
    fun validated(): List<BenchmarkScoreEntity> {
        require(schemaVersion == 1 && version > 0 && scores.size <= 100_000)
        require(scores.map { it.id }.toSet().size == scores.size)
        return scores.map {
            require(it.id.isNotBlank() && it.modelId.isNotBlank() && it.benchmarkId.isNotBlank() && it.score.isFinite())
            require(it.cost == null || it.cost.isFinite())
            BenchmarkScoreEntity(it.id, it.modelId, it.benchmarkId, it.score, it.metric, it.unit,
                it.higherIsBetter, it.subset, it.harness, it.reasoningEffort, it.toolsEnabled,
                it.sourceUrl, it.sourceType, it.evaluatedAt, it.cost)
        }
    }
}

@Serializable
data class BenchmarkScore(
    val id: String, val modelId: String, val benchmarkId: String, val score: Double,
    val metric: String? = null, val unit: String? = null, val higherIsBetter: Boolean? = null,
    val subset: String? = null, val harness: String? = null, val reasoningEffort: String? = null,
    val toolsEnabled: Boolean? = null, val sourceUrl: String? = null, val sourceType: String? = null,
    val evaluatedAt: String? = null,
    val cost: Double? = null,
)

@Serializable
data class BenchmarkManifest(val schemaVersion: Int, val version: Long, val url: String, val sha256: String) {
    fun validate() {
        require(schemaVersion == 1 && version > 0)
        require(sha256.matches(Regex("[0-9a-fA-F]{64}")))
    }
}

val BenchmarkJson = Json { ignoreUnknownKeys = true }
