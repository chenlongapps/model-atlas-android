package io.github.chenlongapps.modelatlas.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import androidx.room3.Insert
import androidx.room3.Transaction
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkScoreEntity
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkVersionEntity
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

    @Query("SELECT version FROM benchmark_version WHERE id = 1")
    abstract suspend fun version(): Long?

    @Query("SELECT COUNT(*) FROM benchmark_scores")
    protected abstract suspend fun count(): Int

    @Query("DELETE FROM benchmark_scores")
    protected abstract suspend fun deleteAll()

    @Insert
    protected abstract suspend fun insertAll(scores: List<BenchmarkScoreEntity>)

    @Upsert
    protected abstract suspend fun saveVersion(version: BenchmarkVersionEntity)

    /** 旧版本的手动成绩不被首次导入覆盖；版本与成绩始终在同一事务提交。 */
    @Transaction
    open suspend fun initialize(version: Long, scores: List<BenchmarkScoreEntity>) {
        if (version() != null) return
        if (count() == 0) {
            insertAll(scores)
            saveVersion(BenchmarkVersionEntity(version = version))
        } else {
            saveVersion(BenchmarkVersionEntity(version = 0))
        }
    }

    @Transaction
    open suspend fun replaceIfNewer(version: Long, scores: List<BenchmarkScoreEntity>): Boolean {
        val current = version() ?: return false
        if (version <= current) return false
        deleteAll()
        insertAll(scores)
        saveVersion(BenchmarkVersionEntity(version = version))
        return true
    }
}
