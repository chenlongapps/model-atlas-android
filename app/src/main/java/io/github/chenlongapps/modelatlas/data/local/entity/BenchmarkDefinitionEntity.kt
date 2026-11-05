package io.github.chenlongapps.modelatlas.data.local.entity

import androidx.room3.ColumnTypeConverters
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import io.github.chenlongapps.modelatlas.data.local.converter.CatalogTypeConverters

@Entity(tableName = "benchmark_definitions")
@ColumnTypeConverters(CatalogTypeConverters::class)
data class BenchmarkDefinitionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val metric: String,
    val description: String? = null,
    val descriptionZhCn: String? = null,
    val compatibleVersions: List<String>? = null,
    val category: String? = null,
    val organization: String? = null,
    val url: String? = null,
    val methodology: String? = null,
    val unit: String? = null,
    val higherIsBetter: Boolean? = null,
    val minScore: Double? = null,
    val maxScore: Double? = null,
)
