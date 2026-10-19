package io.github.chenlongapps.modelatlas.data.model

/** 收录规则，不是模型能力认证；未知类型不根据名称或 ID 猜测。 */
object LlmInclusionPolicy {
    fun includes(type: String?, input: List<String>?, output: List<String>?): Boolean =
        when (type) {
            "llm" -> true
            null -> input?.contains("text") == true && output?.contains("text") == true
            else -> false
        }
}
