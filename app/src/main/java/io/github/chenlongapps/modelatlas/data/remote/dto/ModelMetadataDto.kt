package io.github.chenlongapps.modelatlas.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ModalitiesDto(
    val input: List<String>? = null,
    val output: List<String>? = null,
)

@Serializable
data class ModelLimitDto(
    val context: Long? = null,
    val input: Long? = null,
    val output: Long? = null,
)

@Serializable
data class ModelWeightDto(
    val url: String,
    val label: String? = null,
    val quantization: String? = null,
    val format: String? = null,
)

@Serializable
data class ModelLinkDto(
    val url: String,
    val label: String? = null,
    val type: String? = null,
)

@Serializable
data class ModelBenchmarkDto(
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
