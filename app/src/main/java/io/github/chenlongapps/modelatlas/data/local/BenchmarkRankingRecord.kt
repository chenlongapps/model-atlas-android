package io.github.chenlongapps.modelatlas.data.local

import androidx.room3.ColumnTypeConverters
import androidx.room3.Embedded
import androidx.room3.Relation
import io.github.chenlongapps.modelatlas.data.local.converter.CatalogTypeConverters
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkDefinitionEntity
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkScoreEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ModelEntity

/** 定义、成绩与当前模型身份在同一事务读取；不逐模型订阅详情。 */
data class BenchmarkRankingRecord(
    @Embedded val definition: BenchmarkDefinitionEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["benchmarkId"], entity = BenchmarkScoreEntity::class)
    val scores: List<BenchmarkRankingScoreRecord>,
)

data class BenchmarkRankingScoreRecord(
    @Embedded val score: BenchmarkScoreEntity,
    @Relation(parentColumns = ["modelId"], entityColumns = ["id"], entity = ModelEntity::class,
        projection = ["id", "name", "type", "inputModalities", "outputModalities"])
    val model: BenchmarkRankingModel?,
)

@ColumnTypeConverters(CatalogTypeConverters::class)
data class BenchmarkRankingModel(
    val id: String,
    val name: String?,
    val type: String?,
    val inputModalities: List<String>?,
    val outputModalities: List<String>?,
)
