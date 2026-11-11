package io.github.chenlongapps.modelatlas.ai

/** baseUrl 是 API 根路径（例如 https://host/v1/），客户端只追加 chat/completions。 */
class LlmConfig(
    val baseUrl: String,
    val apiKey: String,
    val modelId: String,
    val timeouts: LlmTimeouts = LlmTimeouts(),
) {
    init {
        require(apiKey.isNotBlank() && apiKey.all { it.code in 0x21..0x7e }) { "Invalid API key" }
        require(modelId.isNotBlank()) { "Model ID is required" }
    }

    // 不使用 data class，避免自动 toString/copy/componentN 意外暴露凭据。
    override fun toString(): String = "LlmConfig([redacted])"
}

/** 流式 read 是相邻网络读取的空闲超时；call 是包含整个响应体的总时限，均不可无限等待。 */
data class LlmTimeouts(
    val connectMillis: Long = 15_000,
    val readMillis: Long = 60_000,
    val writeMillis: Long = 30_000,
    val callMillis: Long = 300_000,
) {
    init {
        require(listOf(connectMillis, readMillis, writeMillis, callMillis).all { it in 1..Int.MAX_VALUE.toLong() }) {
            "Timeouts must be positive milliseconds within Int range"
        }
    }
}

enum class LlmRole { SYSTEM, USER, ASSISTANT }

data class LlmMessage(val role: LlmRole, val content: String)

data class LlmRequest(
    val messages: List<LlmMessage>,
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
) {
    init {
        require(messages.isNotEmpty()) { "Messages are required" }
        require(temperature == null || (temperature.isFinite() && temperature in 0.0..2.0)) { "Invalid temperature" }
        require(maxOutputTokens == null || maxOutputTokens > 0) { "Invalid output token limit" }
    }
}

data class LlmUsage(val inputTokens: Long?, val outputTokens: Long?, val totalTokens: Long?)

data class LlmResponse(
    val content: String,
    val finishReason: String?,
    val id: String?,
    val modelId: String?,
    val usage: LlmUsage?,
)

/** 空文本可能携带 finishReason 或 usage；finishReason 不代表 SSE 已完整结束。 */
data class LlmChunk(
    val content: String = "",
    val finishReason: String? = null,
    val usage: LlmUsage? = null,
)
