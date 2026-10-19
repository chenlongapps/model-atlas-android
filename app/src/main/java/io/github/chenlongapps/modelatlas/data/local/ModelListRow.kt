package io.github.chenlongapps.modelatlas.data.local

import androidx.room3.ColumnTypeConverters
import io.github.chenlongapps.modelatlas.data.local.converter.CatalogTypeConverters

/**
 * 左连接投影；同一个规范模型可对应多个服务商，也可以没有服务商。
 */
@ColumnTypeConverters(CatalogTypeConverters::class)
data class ModelListRow(
    val id: String,
    val name: String,
    val description: String?,
    val type: String?,
    val inputModalities: List<String>?,
    val outputModalities: List<String>?,
    val providerId: String?,
    val providerName: String?,
    val releaseDate: String? = null,
    val contextLimitTokens: Long? = null,
    val outputLimitTokens: Long? = null,
    val openWeights: Boolean? = null,
    val license: String? = null,
)
