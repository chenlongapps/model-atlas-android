package io.github.chenlongapps.modelatlas.data.repository

import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import kotlinx.coroutines.flow.Flow

interface ModelRepository {
    fun observeModels(): Flow<List<ModelSummary>>
    fun observeModelDetail(id: String): Flow<ModelDetail?>

    suspend fun hasLocalCatalog(): Boolean

    /** 主动获取最新目录并在内容变化时原子提交；返回是否更新，失败不写入部分数据。 */
    suspend fun refresh(): Boolean
}
