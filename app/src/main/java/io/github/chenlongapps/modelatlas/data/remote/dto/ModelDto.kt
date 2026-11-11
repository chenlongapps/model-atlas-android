package io.github.chenlongapps.modelatlas.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Atlas 映射复用的规范字段载体；不作为独立远程端点解析，也不包含服务商价格。 */
@Serializable
data class ModelDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val family: String? = null,
    val type: String? = null,
    val attachment: Boolean? = null,
    val reasoning: Boolean? = null,
    @SerialName("tool_call") val toolCall: Boolean? = null,
    @SerialName("structured_output") val structuredOutput: Boolean? = null,
    val temperature: Boolean? = null,
    val knowledge: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("last_updated") val lastUpdated: String? = null,
    val modalities: ModalitiesDto? = null,
    @SerialName("open_weights") val openWeights: Boolean? = null,
    val limit: ModelLimitDto? = null,
    val license: String? = null,
    val weights: List<ModelWeightDto>? = null,
    val links: List<ModelLinkDto>? = null,
)
