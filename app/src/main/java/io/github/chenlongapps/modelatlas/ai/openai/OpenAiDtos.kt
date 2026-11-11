package io.github.chenlongapps.modelatlas.ai.openai

import io.github.chenlongapps.modelatlas.ai.LlmMessage
import io.github.chenlongapps.modelatlas.ai.LlmRequest
import io.github.chenlongapps.modelatlas.ai.LlmRole
import io.github.chenlongapps.modelatlas.ai.LlmUsage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal val LlmJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class ChatRequestDto(
    val model: String,
    val messages: List<MessageDto>,
    val stream: Boolean,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
) {
    companion object {
        fun from(model: String, request: LlmRequest, stream: Boolean) = ChatRequestDto(
            model = model,
            messages = request.messages.map(MessageDto::from),
            stream = stream,
            temperature = request.temperature,
            maxTokens = request.maxOutputTokens,
        )
    }
}

@Serializable
internal data class MessageDto(val role: String? = null, val content: String? = null) {
    companion object {
        fun from(message: LlmMessage) = MessageDto(
            role = when (message.role) {
                LlmRole.SYSTEM -> "system"
                LlmRole.USER -> "user"
                LlmRole.ASSISTANT -> "assistant"
            },
            content = message.content,
        )
    }
}

@Serializable
internal data class ChatResponseDto(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChoiceDto> = emptyList(),
    val usage: UsageDto? = null,
    val error: JsonElement? = null,
)

@Serializable
internal data class ChoiceDto(
    val index: Int,
    val message: MessageDto? = null,
    val delta: MessageDto? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class UsageDto(
    @SerialName("prompt_tokens") val inputTokens: Long? = null,
    @SerialName("completion_tokens") val outputTokens: Long? = null,
    @SerialName("total_tokens") val totalTokens: Long? = null,
) {
    fun toModel() = LlmUsage(inputTokens, outputTokens, totalTokens)
}
