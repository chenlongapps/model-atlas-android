package io.github.chenlongapps.modelatlas.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkScoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BenchmarkScoreDao {
    @Query("SELECT * FROM benchmark_scores WHERE modelId = :modelId ORDER BY id")
    abstract fun observeForModel(modelId: String): Flow<List<BenchmarkScoreEntity>>

    @Query("SELECT * FROM benchmark_scores WHERE benchmarkId = :benchmarkId ORDER BY id")
    abstract fun observeForBenchmark(benchmarkId: String): Flow<List<BenchmarkScoreEntity>>

    @Upsert
    abstract suspend fun upsert(score: BenchmarkScoreEntity)

    @Query("DELETE FROM benchmark_scores WHERE id = :id")
    abstract suspend fun delete(id: String)

}
