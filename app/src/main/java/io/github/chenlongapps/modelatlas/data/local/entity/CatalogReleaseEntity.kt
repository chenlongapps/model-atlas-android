package io.github.chenlongapps.modelatlas.data.local.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** 内容哈希只判断是否相同，不作为可排序的版本号；允许发布方显式回滚。 */
@Entity(tableName = "catalog_release")
data class CatalogReleaseEntity(
    @PrimaryKey val id: Int = 1,
    val dataVersion: String,
    val gitCommit: String,
    val publishedAt: String,
)
