package io.github.chenlongapps.modelatlas.ui.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class ChatReplyStatus { WAITING, STREAMING, COMPLETE, FAILED, STOPPED }
internal enum class ChatError { NETWORK, TIMEOUT, HTTP, PROTOCOL, REMOTE, UNKNOWN }

internal data class ChatReply(
    val text: String = "",
    val status: ChatReplyStatus = ChatReplyStatus.WAITING,
    val error: ChatError? = null,
    val httpStatus: Int? = null,
    val finishReason: String? = null,
)

/** 稳定条目；增量仅更新当前回复，避免逐 token 复制整个历史列表。 */
internal class ChatTurn(val id: Long, val user: String) {
    private val state = MutableStateFlow(ChatReply())
    val reply: StateFlow<ChatReply> = state.asStateFlow()
    internal fun update(reply: ChatReply) { state.value = reply }
}

/** 只在 ViewModel 内存中使用，禁止 Saver / SavedState / 持久化或自动打印字段。 */
internal class ChatConfigDraft(
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelId: String = "",
    val invalid: Boolean = false,
) {
    override fun toString() = "ChatConfigDraft([redacted])"
}

internal data class ChatUiState(
    val turns: List<ChatTurn> = emptyList(),
    val input: String = "",
    val modelId: String? = null,
    val isGenerating: Boolean = false,
    val configDraft: ChatConfigDraft? = null,
) {
    val canSend: Boolean get() = modelId != null && input.isNotBlank() && !isGenerating
}
