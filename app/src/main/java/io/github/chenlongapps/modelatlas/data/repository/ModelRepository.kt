package io.github.chenlongapps.modelatlas.data.repository

import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import kotlinx.coroutines.flow.Flow

enum class CatalogUpdateCheckResult { UNCHANGED, UPDATED_SILENTLY, UPDATE_AVAILABLE }

interface ModelRepository {
    fun observeModels(): Flow<List<ModelSummary>>
    fun observeModelDetail(id: String): Flow<ModelDetail?>

    suspend fun hasLocalCatalog(): Boolean

    /** 模型增删时暂存完整快照等待确认；仅内容变化时静默提交，无变化时不写库。 */
    suspend fun checkForUpdates(): CatalogUpdateCheckResult

    /** 应用已获取的快照，不再次请求；没有待更新内容时返回 false。 */
    suspend fun applyPendingUpdate(): Boolean

    /** 主动获取最新目录并在内容变化时原子提交；返回是否更新，失败不写入部分数据。 */
    suspend fun refresh(): Boolean
}
