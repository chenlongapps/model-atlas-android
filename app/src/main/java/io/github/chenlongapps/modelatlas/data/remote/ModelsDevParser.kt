package io.github.chenlongapps.modelatlas.data.remote

import io.github.chenlongapps.modelatlas.data.remote.dto.ModelDto
import io.github.chenlongapps.modelatlas.data.remote.dto.ProviderDto

/** 只解析 Models.dev 响应，不负责网络请求、持久化或应用模型映射。 */
class ModelsDevParser {
    private val json = ModelsDevJson

    /** 解析 models.json?type=all，保留顶层的规范模型 ID。 */
    fun parseModels(source: String): Map<String, ModelDto> = json.decodeFromString(source)

    /** 解析 api.json?type=all，保留服务商 ID 及其 models 中的服务模型 ID。 */
    fun parseProviders(source: String): Map<String, ProviderDto> = json.decodeFromString(source)
}
