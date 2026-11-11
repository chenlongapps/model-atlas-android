package io.github.chenlongapps.modelatlas.ai

import kotlinx.coroutines.flow.Flow

/** 无会话或持久化状态；调用方提供有序的完整上下文，并持有请求的协程生命周期。 */
interface LlmClient {
    suspend fun complete(config: LlmConfig, request: LlmRequest): LlmResponse

    /** 冷流，每次收集发起一次请求；增量不是累计文本，收集正常结束才代表传输完成。 */
    fun stream(config: LlmConfig, request: LlmRequest): Flow<LlmChunk>
}
