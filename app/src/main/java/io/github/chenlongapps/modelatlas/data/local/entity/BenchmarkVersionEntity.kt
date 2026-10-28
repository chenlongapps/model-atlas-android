package io.github.chenlongapps.modelatlas.data.local.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "benchmark_version")
data class BenchmarkVersionEntity(@PrimaryKey val id: Int = 1, val version: Long)
