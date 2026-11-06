package io.github.chenlongapps.modelatlas.data.model

import io.github.chenlongapps.modelatlas.data.serialization.validateParameterCount
import java.math.BigDecimal
import java.net.URI

/** 只属于规范模型；组件可能重叠或不完整，不能相加或作为整模型总量的回退。 */
data class ModelParameters(
    val totalBillion: BigDecimal? = null,
    val activeBillion: BigDecimal? = null,
    val sources: List<String>? = null,
    val notes: String? = null,
    val components: List<ModelParameterComponent>? = null,
)

data class ModelParameterComponent(
    val name: String,
    val totalBillion: BigDecimal? = null,
    val activeBillion: BigDecimal? = null,
    val sources: List<String>,
    val notes: String? = null,
)

/** 远端 DTO 和本地 JSON 使用同一口径校验，但各自保持独立结构。 */
internal fun validateParameterRecord(
    total: BigDecimal?, active: BigDecimal?, sources: List<String>?, notes: String?,
    componentNames: List<String>? = null,
) {
    total?.let(::validateParameterCount)
    active?.let(::validateParameterCount)
    require(total == null || active == null || active <= total)
    require(total != null || active != null || !componentNames.isNullOrEmpty())
    if (total != null || active != null) require(!sources.isNullOrEmpty())
    sources?.let { urls ->
        require(urls.isNotEmpty() && urls.distinct().size == urls.size)
        require(urls.all { url ->
            runCatching {
                val uri = URI(url)
                (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
                    !uri.host.isNullOrBlank() && uri.rawUserInfo == null
            }.getOrDefault(false)
        })
    }
    require(notes == null || notes.isNotBlank())
    componentNames?.let { require(it.all(String::isNotBlank) && it.distinct().size == it.size) }
}
