package io.github.chenlongapps.modelatlas.data.local.entity

import androidx.room3.ColumnTypeConverters
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import io.github.chenlongapps.modelatlas.data.local.converter.CatalogTypeConverters
import io.github.chenlongapps.modelatlas.data.local.model.ExperimentalModeLocal
import io.github.chenlongapps.modelatlas.data.local.model.InterleavedLocal
import io.github.chenlongapps.modelatlas.data.local.model.OfferingPricingLocal
import io.github.chenlongapps.modelatlas.data.local.model.ProviderOverrideLocal
import io.github.chenlongapps.modelatlas.data.local.model.ReasoningOptionLocal

/**
 * 某个 API 服务商的模型服务，能力、限制和报价均独立于规范模型。
 *
 * [id] 保存服务商 models 字典的键，与 [providerId] 共同构成主键；[remoteId] 保存条目内的 id。
 * [canonicalModelId] 只保存上游显式关联，不猜测匹配，也不设外键，以兼容两个端点不同步。
 * 查询时允许找不到规范模型；服务商删除时，其模型服务级联删除。
 */
@Entity(
    tableName = "model_offerings",
    primaryKeys = ["providerId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["canonicalModelId"])],
)
@ColumnTypeConverters(CatalogTypeConverters::class)
data class ModelOfferingEntity(
    val providerId: String,
    val id: String,
    val remoteId: String,
    val name: String,
    val fetchedAtEpochMillis: Long,
    val source: String = "models.dev",
    val canonicalModelId: String? = null,
    val description: String? = null,
    val family: String? = null,
    val type: String? = null,
    val knowledgeCutoff: String? = null,
    val releaseDate: String? = null,
    val sourceLastUpdated: String? = null,
    val attachment: Boolean? = null,
    val reasoning: Boolean? = null,
    val toolCall: Boolean? = null,
    val structuredOutput: Boolean? = null,
    val temperature: Boolean? = null,
    val openWeights: Boolean? = null,
    val contextLimitTokens: Long? = null,
    val inputLimitTokens: Long? = null,
    val outputLimitTokens: Long? = null,
    val inputModalities: List<String>? = null,
    val outputModalities: List<String>? = null,
    val status: String? = null,
    val reasoningOptions: List<ReasoningOptionLocal>? = null,
    val interleaved: InterleavedLocal? = null,
    val pricing: OfferingPricingLocal? = null,
    val providerOverride: ProviderOverrideLocal? = null,
    val experimentalModes: Map<String, ExperimentalModeLocal>? = null,
)
