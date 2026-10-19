package io.github.chenlongapps.modelatlas.data.local.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 本地 JSON 使用固定的 type 标记区分变体，不复用远程的布尔值 / 对象线格式。 */
@Serializable
sealed interface InterleavedLocal {
    @Serializable
    @SerialName("toggle")
    data class Toggle(val enabled: Boolean) : InterleavedLocal

    @Serializable
    @SerialName("field")
    data class Field(val field: String) : InterleavedLocal
}
