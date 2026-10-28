package io.github.chenlongapps.modelatlas.data.local.model

import kotlinx.serialization.Serializable

/** 以下值对象只定义本地 JSON 存储结构，不依赖远程 DTO。 */
@Serializable
data class ModelWeightLocal(
    val url: String,
    val label: String? = null,
    val quantization: String? = null,
    val format: String? = null,
)

@Serializable
data class ModelLinkLocal(
    val url: String,
    val label: String? = null,
    val type: String? = null,
)
