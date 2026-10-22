package io.github.chenlongapps.modelatlas.data.local

import androidx.room3.Embedded
import androidx.room3.Relation
import io.github.chenlongapps.modelatlas.data.local.entity.ModelEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ModelOfferingEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ProviderEntity

/** 由 DAO 的事务查询读取，避免全量替换时拼接不同快照的模型与报价。 */
data class ModelDetailRecord(
    @Embedded val model: ModelEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["canonicalModelId"], entity = ModelOfferingEntity::class)
    val offerings: List<ModelDetailOfferingRecord>,
)

data class ModelDetailOfferingRecord(
    @Embedded val offering: ModelOfferingEntity,
    @Relation(parentColumns = ["providerId"], entityColumns = ["id"])
    val provider: ProviderEntity,
)
