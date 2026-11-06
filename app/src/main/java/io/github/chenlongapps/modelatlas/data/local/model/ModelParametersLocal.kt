@file:kotlinx.serialization.UseSerializers(io.github.chenlongapps.modelatlas.data.serialization.ParameterCountSerializer::class)

package io.github.chenlongapps.modelatlas.data.local.model

import io.github.chenlongapps.modelatlas.data.model.validateParameterRecord
import java.math.BigDecimal
import kotlinx.serialization.Serializable

/** 本地 JSON 使用驼峰字段；原值、来源、组件顺序及缺失状态独立保存。 */
@Serializable
data class ModelParametersLocal(
    val totalBillion: BigDecimal? = null,
    val activeBillion: BigDecimal? = null,
    val sources: List<String>? = null,
    val notes: String? = null,
    val components: List<ModelParameterComponentLocal>? = null,
) {
    init { validateParameterRecord(totalBillion, activeBillion, sources, notes, components?.map { it.name }) }
}

@Serializable
data class ModelParameterComponentLocal(
    val name: String,
    val totalBillion: BigDecimal? = null,
    val activeBillion: BigDecimal? = null,
    val sources: List<String>,
    val notes: String? = null,
) {
    init {
        require(name.isNotBlank())
        validateParameterRecord(totalBillion, activeBillion, sources, notes)
    }
}
