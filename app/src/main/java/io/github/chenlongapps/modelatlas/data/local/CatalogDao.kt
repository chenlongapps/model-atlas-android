package io.github.chenlongapps.modelatlas.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import androidx.room3.Update
import io.github.chenlongapps.modelatlas.data.local.entity.ModelEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ModelOfferingEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ProviderEntity
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkDefinitionEntity
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkScoreEntity
import io.github.chenlongapps.modelatlas.data.local.entity.CatalogReleaseEntity
import io.github.chenlongapps.modelatlas.data.model.LlmInclusionPolicy
import io.github.chenlongapps.modelatlas.data.mapper.withAtlasBenchmarkDefinition
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

    @Query("SELECT * FROM benchmark_scores ORDER BY id")
    protected abstract suspend fun readScores(): List<BenchmarkScoreEntity>

    @Query("SELECT * FROM benchmark_definitions ORDER BY id")
    protected abstract suspend fun readDefinitions(): List<BenchmarkDefinitionEntity>

    @Query("SELECT * FROM catalog_release WHERE id = 1")
    protected abstract suspend fun readRelease(): CatalogReleaseEntity?

    /** 同一事务内读取完整目录，不能仅用筛选后的 LLM 数量判断有无本地数据。 */
    @Transaction
    open suspend fun readCatalog(): CatalogSnapshot {
        val release = readRelease()
        return CatalogSnapshot(readModels(), readProviders(), readOfferings(),
            if (release != null) readScores() else null, if (release != null) readDefinitions() else null, release)
    }

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

    @Transaction
    @Query("SELECT * FROM models WHERE id = :id")
    abstract fun observeModelDetail(id: String): Flow<ModelDetailRecord?>

    @Transaction
    @Query("SELECT * FROM benchmark_definitions ORDER BY name COLLATE NOCASE, id")
    abstract fun observeBenchmarkRankings(): Flow<List<BenchmarkRankingRecord>>

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

    @Query("UPDATE favorites SET name = :name, lab = :lab WHERE modelId = :id")
    protected abstract suspend fun updateFavoriteIdentity(id: String, name: String, lab: String?)

    @Query("SELECT modelId FROM favorites")
    protected abstract suspend fun favoriteIds(): List<String>

    @Query("DELETE FROM benchmark_scores")
    protected abstract suspend fun deleteScores()

    @Query("DELETE FROM benchmark_definitions")
    protected abstract suspend fun deleteDefinitions()

    @Insert
    protected abstract suspend fun insertScores(scores: List<BenchmarkScoreEntity>)

    @Update
    protected abstract suspend fun updateScores(scores: List<BenchmarkScoreEntity>)

    /** 离线修复旧 Atlas 缓存的派生关联，不换发布、不重写原始版本、不触碰手工成绩。 */
    @Transaction
    open suspend fun reconcileUnspecifiedBenchmarkScores() {
        if (readRelease() == null) return
        val definitions = readDefinitions()
        val relaxedKeys = definitions.filter { it.version == "unspecified" }.map { it.name to it.metric }.toSet()
        if (relaxedKeys.isEmpty()) return
        val changed = readScores().filter { it.id.startsWith("atlas:") && (it.name to it.metric) in relaxedKeys }
            .mapNotNull { score -> score.withAtlasBenchmarkDefinition(definitions).takeIf { it != score } }
        if (changed.isNotEmpty()) updateScores(changed)
    }

    @Insert
    protected abstract suspend fun insertDefinitions(definitions: List<BenchmarkDefinitionEntity>)

    @Upsert
    protected abstract suspend fun saveRelease(release: CatalogReleaseEntity)

    /** 全量快照事务；先移除旧服务，再写入新服务，不用 REPLACE 意外触发级联。 */
    @Transaction
    open suspend fun replaceCatalog(snapshot: CatalogSnapshot) {
        deleteOfferings()
        deleteModels()
        upsertProviders(snapshot.providers)
        insertModels(snapshot.models)
        insertOfferings(snapshot.offerings)
        // 仅同步最后可用身份；不收录、下架、空目录都不覆盖历史身份或收藏时间。
        val favorites = favoriteIds().toHashSet()
        snapshot.models.filter { it.id in favorites && LlmInclusionPolicy.includes(it.type, it.inputModalities, it.outputModalities) }
            .forEach { model ->
                updateFavoriteIdentity(model.id, model.name.takeIf(String::isNotBlank) ?: model.id,
                    model.id.substringBefore('/', "").takeIf(String::isNotBlank))
            }
        val retainedIds = snapshot.providers.mapTo(hashSetOf()) { it.id }
        providerIds().filterNot { it in retainedIds }.forEach { deleteProvider(it) }
        if (snapshot.release != null) {
            requireNotNull(snapshot.scores)
            requireNotNull(snapshot.definitions)
            deleteScores()
            deleteDefinitions()
            insertDefinitions(snapshot.definitions)
            insertScores(snapshot.scores)
            saveRelease(snapshot.release)
        }
    }
}
