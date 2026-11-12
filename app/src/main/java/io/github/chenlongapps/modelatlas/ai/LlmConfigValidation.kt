package io.github.chenlongapps.modelatlas.ai

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** 配置表单与客户端共用同一 URL 安全规则；不补充 /v1，也不发起探测请求。 */
internal fun LlmConfig.apiBaseUrl(): HttpUrl {
    val base = baseUrl.toHttpUrlOrNull()
    require(base != null && base.username.isEmpty() && base.password.isEmpty() &&
        base.query == null && base.fragment == null &&
        (base.isHttps || base.host in setOf("localhost", "127.0.0.1", "::1"))) { "Invalid LLM API base URL" }
    return base
}
