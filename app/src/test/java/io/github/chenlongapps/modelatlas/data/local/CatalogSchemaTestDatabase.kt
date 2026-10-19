package io.github.chenlongapps.modelatlas.data.local

import androidx.room3.Database
import androidx.room3.RoomDatabase
import io.github.chenlongapps.modelatlas.data.local.entity.ModelEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ModelOfferingEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ProviderEntity

/** 仅供测试编译时由 Room KSP 检查实体、外键、索引与列转换器，不创建运行时数据库。 */
@Database(
    entities = [ModelEntity::class, ProviderEntity::class, ModelOfferingEntity::class],
    version = 1,
    exportSchema = false,
)
internal abstract class CatalogSchemaTestDatabase : RoomDatabase()
