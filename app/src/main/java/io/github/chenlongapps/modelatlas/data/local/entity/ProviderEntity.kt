package io.github.chenlongapps.modelatlas.data.local.entity

import androidx.room3.ColumnTypeConverters
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import io.github.chenlongapps.modelatlas.data.local.converter.CatalogTypeConverters

/**
 * API 服务商的本地目录记录；模型服务单独存入 [ModelOfferingEntity]。
 *
 * [id] 保存 api.json 的字典键，[remoteId] 保存条目内的 id。
 * [environmentVariables] 只保存上游声明的环境变量名称，不保存用户密钥。
 */
@Entity(tableName = "providers")
@ColumnTypeConverters(CatalogTypeConverters::class)
data class ProviderEntity(
    @PrimaryKey val id: String,
    val remoteId: String,
    val name: String,
    val fetchedAtEpochMillis: Long,
    val source: String = "models.dev",
    val apiUrl: String? = null,
    val documentationUrl: String? = null,
    val npmPackage: String? = null,
    val environmentVariables: List<String>? = null,
)
