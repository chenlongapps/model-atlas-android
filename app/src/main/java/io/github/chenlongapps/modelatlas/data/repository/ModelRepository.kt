package io.github.chenlongapps.modelatlas.data.repository

import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import kotlinx.coroutines.flow.Flow

interface ModelRepository {
    fun observeModels(): Flow<List<ModelSummary>>

    suspend fun hasLocalCatalog(): Boolean

    /** 只检查并暂存完整快照，不修改数据库。返回是否有待应用更新。 */
    suspend fun checkForUpdates(): Boolean

    /** 应用已获取的快照，不再次请求；没有待更新内容时返回 false。 */
    suspend fun applyPendingUpdate(): Boolean

    /** 主动获取最新目录并在内容变化时原子提交；返回是否更新，失败不写入部分数据。 */
    suspend fun refresh(): Boolean
}
