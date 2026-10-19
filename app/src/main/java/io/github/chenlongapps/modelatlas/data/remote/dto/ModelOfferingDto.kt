package io.github.chenlongapps.modelatlas.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** api.json 中某服务商下的模型服务；不能仅凭 id 与规范模型关联。 */
@Serializable
data class ModelOfferingDto(
    val id: String,
    val name: String,
    @SerialName("canonical_model_id") val canonicalModelId: String? = null,
    val description: String? = null,
    val family: String? = null,
    val type: String? = null,
    val attachment: Boolean? = null,
    val reasoning: Boolean? = null,
    @SerialName("reasoning_options") val reasoningOptions: List<ReasoningOptionDto>? = null,
    @SerialName("tool_call") val toolCall: Boolean? = null,
    val interleaved: InterleavedDto? = null,
    @SerialName("structured_output") val structuredOutput: Boolean? = null,
    val temperature: Boolean? = null,
    val knowledge: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("last_updated") val lastUpdated: String? = null,
    val modalities: ModalitiesDto? = null,
    @SerialName("open_weights") val openWeights: Boolean? = null,
    val limit: ModelLimitDto? = null,
    val status: String? = null,
    val cost: CostDto? = null,
    val provider: ProviderOverrideDto? = null,
    val experimental: ExperimentalDto? = null,
)
