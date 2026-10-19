package io.github.chenlongapps.modelatlas.data.repository

import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import kotlinx.coroutines.flow.Flow

interface ModelRepository {
    fun observeModels(): Flow<List<ModelSummary>>

    /** 两个端点均成功后原子更新目录；失败不写入部分数据。 */
    suspend fun refresh()
}
