package io.github.chenlongapps.modelatlas.ai.config

import io.github.chenlongapps.modelatlas.ai.LlmConfig

/** 只保存已提交配置；实现不得记录凭据或将原始异常传到界面。 */
internal interface LlmConfigRepository {
    suspend fun load(): LlmConfig?
    suspend fun save(config: LlmConfig)
    suspend fun clear()
}

internal enum class ConfigFailure { STORAGE, KEY_UNAVAILABLE }

internal class ConfigStorageException(val failure: ConfigFailure) : Exception("AI configuration unavailable")
