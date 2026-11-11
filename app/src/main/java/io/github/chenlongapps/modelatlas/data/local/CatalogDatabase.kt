package io.github.chenlongapps.modelatlas.data.local

import androidx.room3.Database
import androidx.room3.RoomDatabase
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkScoreEntity
import io.github.chenlongapps.modelatlas.data.local.entity.FavoriteEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ModelEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ModelOfferingEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ProviderEntity
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkDefinitionEntity
import io.github.chenlongapps.modelatlas.data.local.entity.CatalogReleaseEntity

@Database(
    entities = [ModelEntity::class, ProviderEntity::class, ModelOfferingEntity::class, FavoriteEntity::class, BenchmarkScoreEntity::class, BenchmarkDefinitionEntity::class, CatalogReleaseEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun benchmarkScoreDao(): BenchmarkScoreDao
}
