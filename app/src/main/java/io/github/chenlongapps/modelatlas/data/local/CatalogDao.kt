package io.github.chenlongapps.modelatlas.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import io.github.chenlongapps.modelatlas.data.local.entity.ModelEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ModelOfferingEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ProviderEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CatalogDao {
    @Query(
        "SELECT EXISTS(SELECT 1 FROM models) OR EXISTS(SELECT 1 FROM providers) " +
            "OR EXISTS(SELECT 1 FROM model_offerings)",
    )
    abstract suspend fun hasCatalog(): Boolean

    @Query("SELECT * FROM models ORDER BY id")
    protected abstract suspend fun readModels(): List<ModelEntity>

    @Query("SELECT * FROM providers ORDER BY id")
    protected abstract suspend fun readProviders(): List<ProviderEntity>

    @Query("SELECT * FROM model_offerings ORDER BY providerId, id")
    protected abstract suspend fun readOfferings(): List<ModelOfferingEntity>

    /** 同一事务内读取完整目录，不能仅用筛选后的 LLM 数量判断有无本地数据。 */
    @Transaction
    open suspend fun readCatalog(): CatalogSnapshot =
        CatalogSnapshot(readModels(), readProviders(), readOfferings())

    @Query(
        """
        SELECT DISTINCT m.id, m.name, m.description, m.type,
            m.inputModalities, m.outputModalities,
            m.releaseDate, m.contextLimitTokens, m.outputLimitTokens,
            m.openWeights, m.license,
            p.id AS providerId, p.name AS providerName
        FROM models AS m
        LEFT JOIN model_offerings AS o ON o.canonicalModelId = m.id
        LEFT JOIN providers AS p ON p.id = o.providerId
        ORDER BY m.name COLLATE NOCASE, m.id, p.name COLLATE NOCASE, p.id
        """,
    )
    abstract fun observeModelRows(): Flow<List<ModelListRow>>

    /**
     * 报价投影：只取显式关联到规范模型且确有报价的模型服务，不重复规范模型元数据。
     *
     * 同一服务商对同一规范模型可有多个模型服务，因此按 (canonicalModelId, providerId, id) 全量返回，
     * 由映射层挑选展示报价；`pricing IS NULL` 的服务在这里直接排除，界面统一显示“暂无数据”。
     */
    @Query(
        """
        SELECT o.canonicalModelId AS canonicalModelId,
            o.providerId AS providerId, p.name AS providerName,
            o.id AS offeringId, o.pricing AS pricing
        FROM model_offerings AS o
        LEFT JOIN providers AS p ON p.id = o.providerId
        WHERE o.canonicalModelId IS NOT NULL AND o.pricing IS NOT NULL
        ORDER BY o.canonicalModelId, o.providerId, o.id
        """,
    )
    abstract fun observeOfferingPriceRows(): Flow<List<ModelOfferingPriceRow>>

    @Insert
    protected abstract suspend fun insertModels(models: List<ModelEntity>)

    @Upsert
    protected abstract suspend fun upsertProviders(providers: List<ProviderEntity>)

    @Insert
    protected abstract suspend fun insertOfferings(offerings: List<ModelOfferingEntity>)

    @Query("DELETE FROM model_offerings")
    protected abstract suspend fun deleteOfferings()

    @Query("DELETE FROM models")
    protected abstract suspend fun deleteModels()

    @Query("SELECT id FROM providers")
    protected abstract suspend fun providerIds(): List<String>

    @Query("DELETE FROM providers WHERE id = :id")
    protected abstract suspend fun deleteProvider(id: String)

    /** 全量快照事务；先移除旧服务，再写入新服务，不用 REPLACE 意外触发级联。 */
    @Transaction
    open suspend fun replaceCatalog(snapshot: CatalogSnapshot) {
        deleteOfferings()
        deleteModels()
        upsertProviders(snapshot.providers)
        insertModels(snapshot.models)
        insertOfferings(snapshot.offerings)
        val retainedIds = snapshot.providers.mapTo(hashSetOf()) { it.id }
        providerIds().filterNot { it in retainedIds }.forEach { deleteProvider(it) }
    }
}
