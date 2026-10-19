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

@Serializable
data class ModelBenchmarkLocal(
    val name: String,
    val score: Double,
    val metric: String? = null,
    val source: String? = null,
    val date: String? = null,
    val version: String? = null,
    val variant: String? = null,
    val dataset: String? = null,
    val harness: String? = null,
)
