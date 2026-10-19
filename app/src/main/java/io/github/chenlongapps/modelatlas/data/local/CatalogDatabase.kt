package io.github.chenlongapps.modelatlas.data.local

import androidx.room3.Database
import androidx.room3.RoomDatabase
import io.github.chenlongapps.modelatlas.data.local.entity.ModelEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ModelOfferingEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ProviderEntity

@Database(
    entities = [ModelEntity::class, ProviderEntity::class, ModelOfferingEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
}
