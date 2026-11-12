package io.github.chenlongapps.modelatlas.ui.chat

import androidx.lifecycle.ViewModelStore
import io.github.chenlongapps.modelatlas.ai.LlmChunk
import io.github.chenlongapps.modelatlas.ai.LlmClient
import io.github.chenlongapps.modelatlas.ai.LlmConfig
import io.github.chenlongapps.modelatlas.ai.LlmException
import io.github.chenlongapps.modelatlas.ai.LlmMessage
import io.github.chenlongapps.modelatlas.ai.LlmRequest
import io.github.chenlongapps.modelatlas.ai.LlmResponse
import io.github.chenlongapps.modelatlas.ai.LlmRole
import io.github.chenlongapps.modelatlas.ai.LlmUsage
import io.github.chenlongapps.modelatlas.ai.config.ConfigFailure
import io.github.chenlongapps.modelatlas.ai.config.ConfigStorageException
import io.github.chenlongapps.modelatlas.ai.config.LlmConfigRepository
import io.github.chenlongapps.modelatlas.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val store = ViewModelStore()
    private val client = FakeClient()
    private val configs = FakeConfigs()
    private fun TestScope.create(configured: Boolean = true) = ChatViewModel(client, configs).also {
        store.put("chat", it)
        runCurrent()
        if (configured) configure(it)
    }
    @After fun close() = store.clear()

    @Test fun sendAndMultiTurnContextAreOrderedAndOnlyCollectedOnce() = runTest {
        val vm = create()
        vm.setInput(" 第一问\n第二行 ")
        vm.send()
        assertEquals("", vm.uiState.value.input)
        assertTrue(vm.uiState.value.isGenerating)
        runCurrent()
        assertEquals(listOf(LlmMessage(LlmRole.USER, "第一问\n第二行")), client.requests.single().messages)
        val turns = vm.uiState.value.turns
        client.delta("你"); client.delta("好\n世界")
        runCurrent()
        assertSame(turns, vm.uiState.value.turns)
        assertEquals("你好\n世界", turns.single().reply.value.text)
        assertEquals(ChatReplyStatus.STREAMING, turns.single().reply.value.status)
        client.finish()
        advanceUntilIdle()
        assertEquals(ChatReplyStatus.COMPLETE, turns.single().reply.value.status)
        vm.setInput("下一问"); vm.send(); runCurrent()
        assertEquals(listOf(
            LlmMessage(LlmRole.USER, "第一问\n第二行"),
            LlmMessage(LlmRole.ASSISTANT, "你好\n世界"),
            LlmMessage(LlmRole.USER, "下一问"),
        ), client.requests.last().messages)
        assertEquals(2, client.requests.size)
        client.finish(); advanceUntilIdle()
    }

    @Test fun emptyDeltasFinishAndUsageDoNotCompleteUntilFlowEnds() = runTest {
        val vm = create(); send(vm)
        client.channel.send(LlmChunk())
        client.channel.send(LlmChunk(finishReason = "stop"))
        client.channel.send(LlmChunk(usage = LlmUsage(1, 0, 1)))
        runCurrent()
        assertTrue(vm.uiState.value.isGenerating)
        assertEquals(ChatReplyStatus.WAITING, reply(vm).status)
        client.finish(); advanceUntilIdle()
        assertFalse(vm.uiState.value.isGenerating)
        assertEquals(ChatReplyStatus.COMPLETE, reply(vm).status)
        assertEquals("", reply(vm).text)
        assertEquals("stop", reply(vm).finishReason)
    }

    @Test fun failedPartialResponseIsRetainedAndExplicitRetryReplacesItWithoutDuplicateUser() = runTest {
        val vm = create(); send(vm)
        client.delta("部分"); runCurrent()
        client.channel.close(LlmException.Protocol()); advanceUntilIdle()
        assertEquals(ChatReplyStatus.FAILED, reply(vm).status)
        assertEquals("部分", reply(vm).text)
        assertEquals(ChatError.PROTOCOL, reply(vm).error)
        assertEquals(0, client.active)
        assertEquals(1, client.requests.size)
        val turn = vm.uiState.value.turns.single()
        vm.retry(); runCurrent()
        assertSame(turn, vm.uiState.value.turns.single())
        assertEquals("", reply(vm).text)
        assertEquals(client.requests.first(), client.requests.last())
        client.delta("新回复"); client.finish(); advanceUntilIdle()
        assertEquals("新回复", reply(vm).text)
        assertEquals(ChatReplyStatus.COMPLETE, reply(vm).status)
    }

    @Test fun cancellationRetainsPartialTextAndReleasesCollectorBeforeNextSend() = runTest {
        val vm = create(); send(vm)
        client.delta("部分"); runCurrent()
        vm.stop(); advanceUntilIdle()
        assertEquals(0, client.active)
        assertEquals(1, client.released)
        assertEquals(ChatReplyStatus.STOPPED, reply(vm).status)
        assertEquals("部分", reply(vm).text)
        assertNull(reply(vm).error)
        vm.setInput("新问题"); vm.send(); runCurrent()
        assertEquals(listOf(LlmMessage(LlmRole.USER, "新问题")), client.requests.last().messages)
        client.finish(); advanceUntilIdle()
    }

    @Test fun cancellationBeforeCoroutineStartsDoesNotLeaveGeneratingStuck() = runTest {
        val vm = create()
        vm.setInput("hello"); vm.send(); vm.stop()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isGenerating)
        assertEquals(ChatReplyStatus.STOPPED, reply(vm).status)
        assertEquals(0, client.requests.size)
        vm.retry(); runCurrent()
        assertEquals(1, client.requests.size)
        client.finish(); advanceUntilIdle()
    }

    @Test fun viewModelClearingCancelsRequestAndFreesResources() = runTest {
        val vm = create(); send(vm)
        store.clear(); advanceUntilIdle()
        assertEquals(0, client.active)
        assertEquals(1, client.released)
        assertEquals(ChatReplyStatus.STOPPED, reply(vm).status)
    }

    @Test fun duplicateSendAndRetryWhileGeneratingAreIgnoredWithoutLosingDraft() = runTest {
        val vm = create(); send(vm)
        vm.setInput("next")
        repeat(4) { vm.send(); vm.retry(); vm.openConfig() }
        runCurrent()
        assertEquals(1, client.requests.size)
        assertEquals(1, vm.uiState.value.turns.size)
        assertEquals("next", vm.uiState.value.input)
        assertNull(vm.uiState.value.configDraft)
        client.finish(); advanceUntilIdle()
        vm.retry(); runCurrent()
        assertEquals(1, client.requests.size)
    }

    @Test fun whitespaceAndMissingConfigurationNeverRequest() = runTest {
        val vm = create(false)
        vm.setInput("hello"); vm.send(); runCurrent()
        assertEquals("hello", vm.uiState.value.input)
        assertTrue(vm.uiState.value.turns.isEmpty())
        configure(vm)
        vm.setInput(" \n\t "); vm.send(); runCurrent()
        assertEquals(0, client.requests.size)
        assertTrue(vm.uiState.value.turns.isEmpty())
    }

    @Test fun invalidConfigurationIsRejectedBeforeAnyRequestWithSameClientRules() = runTest {
        val vm = create(false)
        val invalid = listOf(
            Triple("", "key", "model"), Triple("http://example.com/v1", "key", "model"),
            Triple("https://user:pass@example.com/", "key", "model"),
            Triple("https://example.com/?key=x", "key", "model"),
            Triple("https://example.com/#x", "key", "model"),
            Triple("https://example.com/", "key\nheader", "model"),
            Triple("https://example.com/", " ", "model"), Triple("https://example.com/", "key", " "),
        )
        invalid.forEach { (url, key, model) ->
            vm.openConfig(); vm.editConfig(url, key, model); vm.applyConfig()
            assertTrue(vm.uiState.value.configDraft!!.invalid)
            assertNull(vm.uiState.value.modelId)
            vm.setInput("hello"); vm.send(); runCurrent()
        }
        assertEquals(0, client.requests.size)
        assertFalse(ChatConfigDraft("url", "secret", "model").toString().contains("secret"))
    }

    @Test fun onlyAppliedConfigurationIsPersistedAndChangingItStartsNewConversation() = runTest {
        val vm = create(); send(vm); client.finish(); advanceUntilIdle()
        val turns = vm.uiState.value.turns
        vm.openConfig(); vm.applyConfig(); runCurrent()
        assertSame(turns, vm.uiState.value.turns)
        vm.openConfig(); vm.editConfig("https://other.example/api", "new-secret", "new-model"); vm.dismissConfig()
        assertEquals("model", vm.uiState.value.modelId)
        vm.openConfig(); vm.editConfig("https://other.example/api", "new-secret", "new-model"); vm.applyConfig(); runCurrent()
        assertTrue(vm.uiState.value.turns.isEmpty())
        assertEquals("new-model", vm.uiState.value.modelId)
        assertEquals(1, client.requests.size)
        val fresh = create(false)
        assertEquals("new-model", fresh.uiState.value.modelId)
        assertTrue(fresh.uiState.value.turns.isEmpty())
    }

    @Test fun failedAndStoppedTurnsAreExcludedWhileEarlierCompletedPairsRemain() = runTest {
        val vm = create(); send(vm, "ok"); client.delta("answer"); client.finish(); advanceUntilIdle()
        send(vm, "failed"); client.channel.close(LlmException.Network()); advanceUntilIdle()
        send(vm, "stopped"); vm.stop(); advanceUntilIdle()
        send(vm, "next")
        assertEquals(listOf(
            LlmMessage(LlmRole.USER, "ok"), LlmMessage(LlmRole.ASSISTANT, "answer"), LlmMessage(LlmRole.USER, "next"),
        ), client.requests.last().messages)
        client.finish(); advanceUntilIdle()
    }

    @Test fun stateCollectorsCanRestartWithoutStartingAnotherPaidRequest() = runTest {
        val vm = create(); send(vm)
        repeat(3) {
            val observer = backgroundScope.launch { vm.uiState.collect {} }
            runCurrent(); observer.cancel(); runCurrent()
        }
        assertEquals(1, client.requests.size)
        assertEquals(1, client.active)
        client.finish(); advanceUntilIdle()
    }

    @Test fun errorsAreTypedAndDoNotExposeArbitraryExceptionMessages() = runTest {
        val vm = create()
        val errors = listOf(
            LlmException.Network() to ChatError.NETWORK, LlmException.Timeout() to ChatError.TIMEOUT,
            LlmException.Http(401, null) to ChatError.HTTP, LlmException.Remote() to ChatError.REMOTE,
            IllegalStateException("sensitive-secret") to ChatError.UNKNOWN,
        )
        errors.forEach { (error, expected) ->
            send(vm); client.channel.close(error); advanceUntilIdle()
            assertEquals(expected, reply(vm).error)
            assertFalse(reply(vm).toString().contains("sensitive-secret"))
            if (expected == ChatError.HTTP) assertEquals(401, reply(vm).httpStatus)
        }
    }

    private fun TestScope.configure(vm: ChatViewModel) {
        vm.openConfig(); vm.editConfig("https://example.test/custom/root", "test-key", "model"); vm.applyConfig()
        runCurrent()
    }
    private suspend fun kotlinx.coroutines.test.TestScope.send(vm: ChatViewModel, text: String = "hello") {
        vm.setInput(text); vm.send(); runCurrent()
    }
    private fun reply(vm: ChatViewModel) = vm.uiState.value.turns.last().reply.value

    @Test fun delayedRestoreBlocksEditsAndSendingAndNeverStartsNetwork() = runTest {
        configs.saved = LlmConfig("https://example.test/api", "restored-key", "restored-model")
        configs.loadGate = CompletableDeferred()
        val vm = create(false)
        vm.setInput("hello"); vm.send(); vm.openConfig()
        assertTrue(vm.uiState.value.configLoading)
        assertFalse(vm.uiState.value.canSend)
        assertNull(vm.uiState.value.configDraft)
        configs.loadGate!!.complete(Unit); runCurrent()
        assertEquals("restored-model", vm.uiState.value.modelId)
        assertTrue(vm.uiState.value.canSend)
        vm.openConfig()
        assertEquals("restored-key", vm.uiState.value.configDraft!!.apiKey)
        assertTrue(vm.uiState.value.turns.isEmpty())
        assertTrue(client.requests.isEmpty())
    }

    @Test fun saveFailureKeepsOldConfigConversationAndDraftAndRetryCommitsOnce() = runTest {
        val vm = create(); send(vm); client.finish(); advanceUntilIdle()
        val turns = vm.uiState.value.turns
        configs.saveGate = CompletableDeferred()
        configs.saveError = ConfigStorageException(ConfigFailure.STORAGE)
        vm.openConfig(); vm.editConfig("https://other.test/api", "other-key", "other-model"); vm.applyConfig()
        repeat(3) { vm.applyConfig(); vm.dismissConfig(); vm.send(); vm.editConfig("bad", "bad", "bad") }
        runCurrent()
        assertTrue(vm.uiState.value.configSaving)
        assertEquals(2, configs.saves)
        assertEquals("other-model", vm.uiState.value.configDraft!!.modelId)
        assertSame(turns, vm.uiState.value.turns)
        configs.saveGate!!.complete(Unit); runCurrent()
        assertEquals("model", vm.uiState.value.modelId)
        assertEquals("model", configs.saved!!.modelId)
        assertEquals(ConfigFailure.STORAGE, vm.uiState.value.configFailure)
        assertSame(turns, vm.uiState.value.turns)
        configs.saveError = null
        vm.applyConfig(); runCurrent()
        assertEquals("other-model", vm.uiState.value.modelId)
        assertTrue(vm.uiState.value.turns.isEmpty())
        assertNull(vm.uiState.value.configDraft)
        assertEquals(1, client.requests.size)
    }

    @Test fun loadFailureIsNotAnEmptyConfigAndDoesNotAllowOverwritingUntilResetOrRetry() = runTest {
        configs.loadError = ConfigStorageException(ConfigFailure.KEY_UNAVAILABLE)
        val vm = create(false)
        vm.setInput("hello"); vm.openConfig(); vm.editConfig("https://test.test", "key", "model"); vm.applyConfig(); vm.send()
        assertTrue(vm.uiState.value.configLoadFailed)
        assertEquals(ConfigFailure.KEY_UNAVAILABLE, vm.uiState.value.configFailure)
        assertEquals(0, configs.saves)
        configs.loadError = null
        configs.saved = LlmConfig("https://example.test", "key", "recovered")
        vm.retryLoadConfig(); runCurrent()
        assertFalse(vm.uiState.value.configLoadFailed)
        assertEquals("recovered", vm.uiState.value.configDraft!!.modelId)
        assertTrue(client.requests.isEmpty())
    }

    @Test fun clearRequiresConfirmationAndRemovesPersistedAndMemoryStateWithoutNetwork() = runTest {
        val vm = create(); send(vm); client.finish(); advanceUntilIdle()
        vm.setInput("draft")
        vm.clearConfig(); runCurrent()
        assertNotNull(configs.saved)
        vm.openConfig(); vm.requestClearConfig(); vm.cancelClearConfig(); vm.clearConfig(); runCurrent()
        assertNotNull(configs.saved)
        vm.requestClearConfig(); vm.clearConfig(); vm.clearConfig(); runCurrent()
        assertNull(configs.saved)
        assertEquals(1, configs.clears)
        assertNull(vm.uiState.value.modelId)
        assertTrue(vm.uiState.value.turns.isEmpty())
        assertEquals("", vm.uiState.value.input)
        assertNull(create(false).uiState.value.modelId)
        assertEquals(1, client.requests.size)
    }

    @Test fun failedClearDisablesOldCredentialsAndCanBeRetriedWithoutLeakingError() = runTest {
        val vm = create()
        configs.clearError = IllegalStateException("sensitive-secret")
        vm.openConfig(); vm.requestClearConfig(); vm.clearConfig(); runCurrent()
        assertNull(vm.uiState.value.modelId)
        assertTrue(vm.uiState.value.configLoadFailed)
        assertEquals(ConfigFailure.STORAGE, vm.uiState.value.configFailure)
        assertFalse(vm.uiState.value.toString().contains("sensitive-secret"))
        configs.clearError = null
        vm.requestClearConfig(); vm.clearConfig(); runCurrent()
        assertFalse(vm.uiState.value.configLoadFailed)
        assertNull(configs.saved)
    }

    private class FakeConfigs : LlmConfigRepository {
        var saved: LlmConfig? = null
        var loadGate: CompletableDeferred<Unit>? = null
        var saveGate: CompletableDeferred<Unit>? = null
        var loadError: Exception? = null
        var saveError: Exception? = null
        var clearError: Exception? = null
        var saves = 0
        var clears = 0
        override suspend fun load(): LlmConfig? {
            loadGate?.await(); loadError?.let { throw it }; return saved
        }
        override suspend fun save(config: LlmConfig) {
            saves++; saveGate?.await(); saveError?.let { throw it }; saved = config
        }
        override suspend fun clear() { clears++; clearError?.let { throw it }; saved = null }
    }

    private class FakeClient : LlmClient {
        val requests = mutableListOf<LlmRequest>()
        lateinit var channel: Channel<LlmChunk>
        var active = 0
        var released = 0
        override suspend fun complete(config: LlmConfig, request: LlmRequest): LlmResponse = error("stream only")
        override fun stream(config: LlmConfig, request: LlmRequest) = flow {
            requests += request
            val input = Channel<LlmChunk>(Channel.UNLIMITED).also { channel = it }
            active++
            try { for (chunk in input) emit(chunk) } finally { active--; released++; input.cancel() }
        }
        suspend fun delta(text: String) { channel.send(LlmChunk(text)) }
        fun finish() { channel.close() }
    }
}
