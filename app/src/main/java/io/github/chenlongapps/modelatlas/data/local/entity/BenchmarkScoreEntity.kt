package io.github.chenlongapps.modelatlas.data.local.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** 独立成绩记录；目录替换或模型下架不删除评测结果。 */
@Entity(tableName = "benchmark_scores", indices = [Index("modelId"), Index("benchmarkId")])
data class BenchmarkScoreEntity(
    @PrimaryKey val id: String,
    val modelId: String,
    val benchmarkId: String,
    val score: Double,
    val metric: String? = null,
    val unit: String? = null,
    val higherIsBetter: Boolean? = null,
    val subset: String? = null,
    val harness: String? = null,
    val reasoningEffort: String? = null,
    val toolsEnabled: Boolean? = null,
    val sourceUrl: String? = null,
    val sourceType: String? = null,
    val evaluatedAt: String? = null,
    val cost: Double? = null,
)
