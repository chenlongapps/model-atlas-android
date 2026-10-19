package io.github.chenlongapps.modelatlas.data.remote.dto

import kotlinx.serialization.Serializable

/** api.json 中的 API 服务商，models 的键只在当前服务商内标识模型服务。 */
@Serializable
data class ProviderDto(
    val id: String,
    val name: String,
    val models: Map<String, ModelOfferingDto>,
    val env: List<String>? = null,
    val npm: String? = null,
    val api: String? = null,
    val doc: String? = null,
)
