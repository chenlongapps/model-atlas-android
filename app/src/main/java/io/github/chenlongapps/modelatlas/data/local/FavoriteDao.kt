package io.github.chenlongapps.modelatlas.data.local

import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Relation
import androidx.room3.Transaction
import io.github.chenlongapps.modelatlas.data.local.entity.FavoriteEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ModelEntity
import io.github.chenlongapps.modelatlas.data.model.LlmInclusionPolicy
import kotlinx.coroutines.flow.Flow

data class FavoriteRecord(
    @Embedded val favorite: FavoriteEntity,
    @Relation(parentColumns = ["modelId"], entityColumns = ["id"], entity = ModelEntity::class)
    val model: ModelDetailRecord?,
)

@Dao
abstract class FavoriteDao {
    /** 身份、可用性和服务报价来自同一个目录事务，不拼接不同快照的 Flow。 */
    @Transaction
    @Query("SELECT * FROM favorites ORDER BY favoritedAtEpochMillis DESC, modelId")
    abstract fun observeFavorites(): Flow<List<FavoriteRecord>>

    @Query("SELECT * FROM favorites WHERE modelId = :id")
    abstract fun observeFavorite(id: String): Flow<FavoriteEntity?>

    @Query("SELECT * FROM models WHERE id = :id")
    protected abstract suspend fun readModel(id: String): ModelEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertFavorite(favorite: FavoriteEntity)

    /** 在写入事务内校验最新目录，避免收藏刷新前的过期详情。重复收藏不重排时间。 */
    @Transaction
    open suspend fun add(id: String, now: Long) {
        val model = checkNotNull(readModel(id)) { "Model is unavailable" }
        check(LlmInclusionPolicy.includes(model.type, model.inputModalities, model.outputModalities))
        insertFavorite(FavoriteEntity(id, model.name.takeIf(String::isNotBlank) ?: id,
            id.substringBefore('/', "").takeIf(String::isNotBlank), now))
    }

    @Query("DELETE FROM favorites WHERE modelId = :id")
    abstract suspend fun remove(id: String)
}
