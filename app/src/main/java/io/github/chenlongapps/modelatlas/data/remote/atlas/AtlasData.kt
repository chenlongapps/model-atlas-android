package io.github.chenlongapps.modelatlas.data.remote.atlas

import io.github.chenlongapps.modelatlas.data.remote.dto.ModelLimitDto
import io.github.chenlongapps.modelatlas.data.remote.dto.ModelLinkDto
import io.github.chenlongapps.modelatlas.data.remote.dto.ModelWeightDto
import io.github.chenlongapps.modelatlas.data.remote.dto.ModalitiesDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

internal val AtlasJson = Json { ignoreUnknownKeys = true }

@Serializable
data class AtlasModelDto(
    val schema: Int,
    val name: String,
    val description: String? = null,
    val family: String? = null,
    val type: String? = null,
    val knowledge: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("last_updated") val lastUpdated: String? = null,
    @SerialName("open_weights") val openWeights: Boolean? = null,
    val license: String? = null,
    val modalities: ModalitiesDto? = null,
    val limit: ModelLimitDto? = null,
    val capabilities: AtlasCapabilitiesDto? = null,
    val weights: List<ModelWeightDto>? = null,
    val links: List<ModelLinkDto>? = null,
    val benchmarks: List<AtlasBenchmarkScoreDto>? = null,
    val metadata: AtlasMetadataDto? = null,
)

/** 来源允许置信区间、百分号等文本；不解析其中的数字或丢弃限定条件。 */
@Serializable
data class AtlasBenchmarkScoreDto(
    val name: String,
    val score: JsonPrimitive,
    val metric: String? = null,
    val source: String? = null,
    val date: String? = null,
    val version: String? = null,
    val variant: String? = null,
    val dataset: String? = null,
    val harness: String? = null,
    val effort: String? = null,
)

@Serializable
data class AtlasCapabilitiesDto(
    val reasoning: AtlasSupportDto? = null,
    val tools: AtlasSupportDto? = null,
    val temperature: Boolean? = null,
)

@Serializable
data class AtlasSupportDto(val supported: Boolean? = null)

@Serializable
data class AtlasBenchmarkDto(
    val name: String,
    val version: String,
    val metric: String,
    val description: String? = null,
    val category: String? = null,
    val organization: String? = null,
    val url: String? = null,
    val methodology: String? = null,
    val unit: String? = null,
    @SerialName("higher_is_better") val higherIsBetter: Boolean? = null,
    @SerialName("min_score") val minScore: Double? = null,
    @SerialName("max_score") val maxScore: Double? = null,
    @SerialName("compatible_versions") val compatibleVersions: List<String>? = null,
)

@Serializable
data class AtlasTranslationsDto(
    val model: AtlasModelTranslationsDto = AtlasModelTranslationsDto(),
    val benchmark: AtlasBenchmarkTranslationsDto = AtlasBenchmarkTranslationsDto(),
)

@Serializable
data class AtlasModelTranslationsDto(val descriptions: Map<String, String> = emptyMap())

@Serializable
data class AtlasBenchmarkTranslationsDto(val description: Map<String, String> = emptyMap())

@Serializable
data class AtlasManifest(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("data_version") val dataVersion: String,
    @SerialName("git_commit") val gitCommit: String,
    @SerialName("published_at") val publishedAt: String,
    val files: Map<String, AtlasFile>,
)

@Serializable
data class AtlasFile(val sha256: String, val size: Long)

data class AtlasRelease(
    val manifest: AtlasManifest,
    val models: Map<String, AtlasModelDto>,
    val benchmarks: Map<String, AtlasBenchmarkDto>,
    val translations: AtlasTranslationsDto,
)
