@file:kotlinx.serialization.UseSerializers(io.github.chenlongapps.modelatlas.data.serialization.ParameterCountSerializer::class)

package io.github.chenlongapps.modelatlas.data.remote.atlas

import io.github.chenlongapps.modelatlas.data.model.validateParameterRecord
import java.math.BigDecimal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AtlasMetadataDto(val modelatlas: AtlasModelAtlasMetadataDto? = null)

@Serializable
data class AtlasModelAtlasMetadataDto(val parameters: AtlasParametersDto? = null)

@Serializable
data class AtlasParametersDto(
    @SerialName("total_billion") val totalBillion: BigDecimal? = null,
    @SerialName("active_billion") val activeBillion: BigDecimal? = null,
    val sources: List<String>? = null,
    val notes: String? = null,
    val components: List<AtlasParameterComponentDto>? = null,
) {
    init { validateParameterRecord(totalBillion, activeBillion, sources, notes, components?.map { it.name }) }
}

@Serializable
data class AtlasParameterComponentDto(
    val name: String,
    @SerialName("total_billion") val totalBillion: BigDecimal? = null,
    @SerialName("active_billion") val activeBillion: BigDecimal? = null,
    val sources: List<String>,
    val notes: String? = null,
) {
    init {
        require(name.isNotBlank())
        validateParameterRecord(totalBillion, activeBillion, sources, notes)
    }
}
