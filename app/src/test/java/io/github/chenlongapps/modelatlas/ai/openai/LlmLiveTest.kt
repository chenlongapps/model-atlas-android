package io.github.chenlongapps.modelatlas.ai.openai

import io.github.chenlongapps.modelatlas.ai.LlmConfig
import io.github.chenlongapps.modelatlas.ai.LlmMessage
import io.github.chenlongapps.modelatlas.ai.LlmRequest
import io.github.chenlongapps.modelatlas.ai.LlmRole
import io.github.chenlongapps.modelatlas.di.DataModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** 显式 opt-in 的付费线上冒烟；默认跳过，不输出配置、提示词或响应。 */
class LlmLiveTest {
    @Test fun configuredEndpointSupportsNormalAndStreamingMultiTurnRequests() = runBlocking {
        val base = System.getenv("LLM_TEST_BASE_URL")
        val key = System.getenv("LLM_TEST_API_KEY")
        val model = System.getenv("LLM_TEST_MODEL_ID")
        assumeTrue("Live LLM credentials not configured", listOf(base, key, model).all { !it.isNullOrBlank() })
        val config = LlmConfig(base!!, key!!, model!!)
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
