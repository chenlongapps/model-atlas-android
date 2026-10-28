package io.github.chenlongapps.modelatlas.data.local.entity

import androidx.room3.ColumnTypeConverters
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import io.github.chenlongapps.modelatlas.data.local.converter.CatalogTypeConverters
import io.github.chenlongapps.modelatlas.data.local.model.ModelLinkLocal
import io.github.chenlongapps.modelatlas.data.local.model.ModelWeightLocal

/**
 * 规范模型的本地目录记录，不包含特定服务商的报价或生命周期。
 *
 * [id] 保存 models.json 的字典键，[remoteId] 独立保存条目内的 id，不推断开发商。
 * [fetchedAtEpochMillis] 由获取数据的一方传入，不等同于上游的 [sourceLastUpdated]。
 * 可选能力、限制和集合均不补造默认业务值。
 */
@Entity(tableName = "models")
@ColumnTypeConverters(CatalogTypeConverters::class)
data class ModelEntity(
    @PrimaryKey val id: String,
    val remoteId: String,
    val name: String,
    val fetchedAtEpochMillis: Long,
    val source: String = "models.dev",
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
    val license: String? = null,
    val weights: List<ModelWeightLocal>? = null,
    val links: List<ModelLinkLocal>? = null,
)
