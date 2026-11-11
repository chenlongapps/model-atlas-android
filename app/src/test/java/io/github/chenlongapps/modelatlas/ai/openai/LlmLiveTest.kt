package io.github.chenlongapps.modelatlas.ai.openai

import io.github.chenlongapps.modelatlas.ai.LlmMessage
import io.github.chenlongapps.modelatlas.ai.LlmRequest
import io.github.chenlongapps.modelatlas.ai.LlmRole
import io.github.chenlongapps.modelatlas.di.DataModule
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** 显式 opt-in 的付费线上冒烟；默认跳过，不输出配置、提示词或响应。 */
class LlmLiveTest {
    @Test fun configuredEndpointSupportsNormalAndStreamingMultiTurnRequests() = runBlocking {
        assumeTrue("Enable paid live tests with -PllmLiveTest=true", java.lang.Boolean.getBoolean("modelatlas.llm.liveTest.enabled"))
        val config = requireNotNull(readLlmLiveTestConfig(
            System.getProperty("modelatlas.llm.localProperties")?.let(::File),
        )) { "Configure LLM_TEST_BASE_URL, LLM_TEST_API_KEY and LLM_TEST_MODEL_ID in local.properties or environment" }
        val client = OpenAiCompatibleClient(DataModule.httpClient(), Dispatchers.IO)
        val messages = listOf(
            LlmMessage(LlmRole.SYSTEM, "You are a connectivity test. Answer briefly, without reasoning."),
            LlmMessage(LlmRole.USER, "Remember the word atlas. Reply with that word only."),
        )
        val response = client.complete(config, LlmRequest(messages))
        assertTrue("Normal response should have text", response.content.isNotBlank())
        val chunks = client.stream(config, LlmRequest(messages + listOf(
            LlmMessage(LlmRole.ASSISTANT, response.content),
            LlmMessage(LlmRole.USER, "What word did I ask you to remember? Reply with the word only."),
        ))).toList()
        assertTrue("Streaming response should preserve context", chunks.joinToString("") { it.content }.contains("atlas", ignoreCase = true))
        assertTrue("Streaming response should report a finish reason", chunks.any { it.finishReason != null })
    }
}
