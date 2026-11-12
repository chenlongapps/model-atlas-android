package io.github.chenlongapps.modelatlas.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chenlongapps.modelatlas.ai.LlmClient
import io.github.chenlongapps.modelatlas.ai.LlmConfig
import io.github.chenlongapps.modelatlas.ai.LlmException
import io.github.chenlongapps.modelatlas.ai.LlmMessage
import io.github.chenlongapps.modelatlas.ai.LlmRequest
import io.github.chenlongapps.modelatlas.ai.LlmRole
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
internal class ChatViewModel @Inject constructor(private val client: LlmClient) : ViewModel() {
    private val state = MutableStateFlow(ChatUiState())
    val uiState = state.asStateFlow()
    private var config: LlmConfig? = null
    private var generation: Job? = null
    private var nextId = 0L

    fun setInput(text: String) { state.update { it.copy(input = text) } }

    fun openConfig() {
        if (state.value.isGenerating) return
        state.update { it.copy(configDraft = ChatConfigDraft(config?.baseUrl.orEmpty(), config?.apiKey.orEmpty(), config?.modelId.orEmpty())) }
    }

    fun editConfig(baseUrl: String, apiKey: String, modelId: String) {
        if (state.value.configDraft == null) return
        state.update { it.copy(configDraft = ChatConfigDraft(baseUrl, apiKey, modelId)) }
    }

    fun dismissConfig() { state.update { it.copy(configDraft = null) } }

    fun applyConfig() {
        if (state.value.isGenerating) return
        val draft = state.value.configDraft ?: return
        val candidate = try {
            LlmConfig(draft.baseUrl.trim(), draft.apiKey, draft.modelId.trim()).also { it.validate() }
        } catch (_: IllegalArgumentException) {
            state.update { it.copy(configDraft = ChatConfigDraft(draft.baseUrl, draft.apiKey, draft.modelId, invalid = true)) }
            return
        }
        val changed = config?.let { it.baseUrl != candidate.baseUrl || it.apiKey != candidate.apiKey || it.modelId != candidate.modelId } ?: true
        config = candidate
        state.update { it.copy(modelId = candidate.modelId, configDraft = null, turns = if (changed) emptyList() else it.turns) }
    }

    fun send() {
        if (!state.value.canSend || state.value.configDraft != null) return
        val turn = ChatTurn(++nextId, state.value.input.trim())
        state.update { it.copy(turns = it.turns + turn, input = "") }
        generate(turn)
    }

    /** 仅末轮可重试；替换该轮失败内容，不再添加 user、不自动续接部分结果。 */
    fun retry() {
        if (state.value.isGenerating || state.value.configDraft != null) return
        val turn = state.value.turns.lastOrNull() ?: return
        if (turn.reply.value.status !in setOf(ChatReplyStatus.FAILED, ChatReplyStatus.STOPPED)) return
        generate(turn)
    }

    fun stop() { generation?.cancel() }

    private fun generate(turn: ChatTurn) {
        val currentConfig = config ?: return
        // 完整轮次成对进入上下文；失败／停止轮的 user 和 assistant 都排除。
        val context = state.value.turns.takeWhile { it !== turn }.flatMap { previous ->
            val reply = previous.reply.value
            if (reply.status == ChatReplyStatus.COMPLETE) listOf(
                LlmMessage(LlmRole.USER, previous.user), LlmMessage(LlmRole.ASSISTANT, reply.text),
            ) else emptyList()
        } + LlmMessage(LlmRole.USER, turn.user)
        turn.update(ChatReply())
        state.update { it.copy(isGenerating = true) }
        // 先保存 Job 再启动；同帧重复发送／停止也有确定的防重入和取消对象。
        generation = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val text = StringBuilder()
            var finishReason: String? = null
            try {
                client.stream(currentConfig, LlmRequest(context)).collect { chunk ->
                    if (chunk.content.isNotEmpty()) text.append(chunk.content)
                    if (chunk.finishReason != null) finishReason = chunk.finishReason
                    if (chunk.content.isNotEmpty()) turn.update(ChatReply(text.toString(), ChatReplyStatus.STREAMING))
                }
                ensureActive()
                // finishReason 本身不算结束，必须等待冷流正常收集完毕（客户端验证 [DONE]）。
                turn.update(ChatReply(text.toString(), ChatReplyStatus.COMPLETE, finishReason = finishReason))
            } catch (cancelled: CancellationException) {
                turn.update(ChatReply(text.toString(), ChatReplyStatus.STOPPED))
                throw cancelled
            } catch (error: Exception) {
                val kind = when (error) {
                    is LlmException.Network -> ChatError.NETWORK
                    is LlmException.Timeout -> ChatError.TIMEOUT
                    is LlmException.Http -> ChatError.HTTP
                    is LlmException.Protocol -> ChatError.PROTOCOL
                    is LlmException.Remote -> ChatError.REMOTE
                    else -> ChatError.UNKNOWN
                }
                // 不把任意 Throwable.message / cause、配置或响应带入 UI 或日志。
                turn.update(ChatReply(text.toString(), ChatReplyStatus.FAILED, kind, (error as? LlmException.Http)?.statusCode))
            }
        }.also { job ->
            job.invokeOnCompletion { cause ->
                // 停止可能发生在协程首次获得执行机会之前，此时 body 的 catch 不会运行。
                if (cause is CancellationException && turn.reply.value.status in setOf(ChatReplyStatus.WAITING, ChatReplyStatus.STREAMING)) {
                    turn.update(turn.reply.value.copy(status = ChatReplyStatus.STOPPED))
                }
                state.update { it.copy(isGenerating = false) }
            }
            job.start()
        }
    }
}
