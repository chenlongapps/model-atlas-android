package io.github.chenlongapps.modelatlas.data.local

import androidx.room3.ColumnTypeConverters
import io.github.chenlongapps.modelatlas.data.local.converter.CatalogTypeConverters
import io.github.chenlongapps.modelatlas.data.local.model.OfferingPricingLocal

/**
 * 报价投影行；只保存挑选展示报价所需的信息，不重复规范模型元数据。
 *
 * [canonicalModelId] 来自上游显式关联，[offeringId] 是该服务商 models 字典的键，只在该服务商内唯一，
 * 因此同一规范模型可有多个报价行。[providerName] 缺失时该行不可用于展示，由映射层丢弃。
 */
@ColumnTypeConverters(CatalogTypeConverters::class)
data class ModelOfferingPriceRow(
    val canonicalModelId: String,
    val providerId: String,
    val providerName: String?,
    val offeringId: String,
    val pricing: OfferingPricingLocal,
)
