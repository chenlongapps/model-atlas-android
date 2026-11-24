package io.github.chenlongapps.modelatlas.ai

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal enum class LlmConfigField { BASE_URL, API_KEY, MODEL_ID }

internal fun invalidLlmConfigFields(baseUrl: String, apiKey: String, modelId: String): Set<LlmConfigField> = buildSet {
    if (baseUrl.validApiBaseUrlOrNull() == null) add(LlmConfigField.BASE_URL)
    if (!apiKey.isValidLlmApiKey()) add(LlmConfigField.API_KEY)
    if (!modelId.isValidLlmModelId()) add(LlmConfigField.MODEL_ID)
}

internal fun String.isValidLlmApiKey() = isNotBlank() && all { it.code in 0x21..0x7e }
internal fun String.isValidLlmModelId() = isNotBlank()

/** 配置表单与客户端共用同一 URL 安全规则；不补充 /v1，也不发起探测请求。 */
internal fun LlmConfig.apiBaseUrl(): HttpUrl =
    requireNotNull(baseUrl.validApiBaseUrlOrNull()) { "Invalid LLM API base URL" }

private fun String.validApiBaseUrlOrNull(): HttpUrl? = toHttpUrlOrNull()?.takeIf { base ->
    base.username.isEmpty() && base.password.isEmpty() &&
        base.query == null && base.fragment == null &&
        (base.isHttps || base.host in setOf("localhost", "127.0.0.1", "::1"))
}
