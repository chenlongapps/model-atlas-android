package io.github.chenlongapps.modelatlas.data.local.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** 用户数据不关联目录外键，目录全量替换或下架不能删除收藏。 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val modelId: String,
    val name: String,
    val lab: String?,
    val favoritedAtEpochMillis: Long,
)
