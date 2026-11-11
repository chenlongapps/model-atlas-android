package io.github.chenlongapps.modelatlas.ai.openai

import io.github.chenlongapps.modelatlas.ai.LlmConfig
import io.github.chenlongapps.modelatlas.ai.LlmException
import io.github.chenlongapps.modelatlas.ai.LlmMessage
import io.github.chenlongapps.modelatlas.ai.LlmRequest
import io.github.chenlongapps.modelatlas.ai.LlmRole
import io.github.chenlongapps.modelatlas.ai.LlmTimeouts
import io.github.chenlongapps.modelatlas.di.DataModule
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class OpenAiCompatibleClientTest {
    private val request = LlmRequest(listOf(
        LlmMessage(LlmRole.SYSTEM, "简洁回答"),
        LlmMessage(LlmRole.USER, "你好\n\"世界\""),
        LlmMessage(LlmRole.ASSISTANT, "你好"),
        LlmMessage(LlmRole.USER, "继续"),
    ))

    @Test fun serializesOrderedContextAndDecodesNormalResponse() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(jsonResponse(SUCCESS))
            server.start()
            val client = client()
            val result = client.complete(config(server), request.copy(temperature = 0.3, maxOutputTokens = 50))
            assertEquals("你好 🌍", result.content)
            assertEquals("stop", result.finishReason)
            assertEquals("response-id", result.id)
            assertEquals("actual-model", result.modelId)
            assertEquals(10L, result.usage?.inputTokens)
            assertEquals(2L, result.usage?.outputTokens)
            assertEquals(12L, result.usage?.totalTokens)
            val sent = server.takeRequest()
            assertEquals("POST", sent.method)
            assertEquals("/gateway/v1/chat/completions", sent.url.encodedPath)
            assertEquals("Bearer test-secret", sent.headers["Authorization"])
            assertEquals("application/json", sent.headers["Accept"])
            val json = Json.parseToJsonElement(requireNotNull(sent.body).utf8()).jsonObject
            assertEquals("configured-model", json.getValue("model").jsonPrimitive.content)
            assertEquals("false", json.getValue("stream").jsonPrimitive.content)
            assertEquals("0.3", json.getValue("temperature").jsonPrimitive.content)
            assertEquals("50", json.getValue("max_tokens").jsonPrimitive.content)
            val messages = json.getValue("messages").jsonArray.map { it.jsonObject }
            assertEquals(listOf("system", "user", "assistant", "user"), messages.map { it.getValue("role").jsonPrimitive.content })
            assertEquals(request.messages.map { it.content }, messages.map { it.getValue("content").jsonPrimitive.content })
        }
    }

    @Test fun supportsDifferentEndpointsKeysModelsAndOmitsUnsetOptions() = runBlocking {
        MockWebServer().use { first ->
            MockWebServer().use { second ->
                first.enqueue(jsonResponse(SUCCESS))
                second.enqueue(jsonResponse(SUCCESS))
                first.start()
                second.start()
                val client = client()
                client.complete(config(first, "/v1"), request)
                client.complete(LlmConfig(second.url("/custom/").toString(), "other-key", "other-model"), request)
                assertEquals("/v1/chat/completions", first.takeRequest().url.encodedPath)
                val sent = second.takeRequest()
                assertEquals("/custom/chat/completions", sent.url.encodedPath)
                assertEquals("Bearer other-key", sent.headers["Authorization"])
                val json = Json.parseToJsonElement(requireNotNull(sent.body).utf8()).jsonObject
                assertEquals("other-model", json.getValue("model").jsonPrimitive.content)
                assertFalse(json.containsKey("temperature"))
                assertFalse(json.containsKey("max_tokens"))
            }
        }
    }

    @Test fun streamsFragmentedUtf8CommentsMultilineDataAndTrailingUsage() = runBlocking {
        MockWebServer().use { server ->
            val sse = "\uFEFF: heartbeat\r\n\r\n" +
                "event: message\r\nid: 1\r\ndata: {\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":null}}]}\r\n\r\n" +
                "data: {\"choices\":[{\"index\":0,\n" +
                "data: \"delta\":{\"content\":\"你好 🌍\"}}],\"unknown\":true}\n\n" +
                event("", "stop") +
                "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2,\"total_tokens\":5}}\n\n" +
                "data: [DONE]\n\n"
            server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/event-stream; charset=utf-8")
                .chunkedBody(sse, 1).build())
            server.start()
            val chunks = client().stream(config(server), request).toList()
            assertEquals(3, chunks.size)
            assertEquals("你好 🌍", chunks[0].content)
            assertEquals("stop", chunks[1].finishReason)
            assertEquals(5L, chunks[2].usage?.totalTokens)
            val sent = server.takeRequest()
            assertEquals("text/event-stream", sent.headers["Accept"])
            assertEquals("true", Json.parseToJsonElement(requireNotNull(sent.body).utf8()).jsonObject.getValue("stream").jsonPrimitive.content)
        }
    }

    @Test fun streamIsColdAndEachCollectionIsAnIndependentRequest() = runBlocking {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(sseResponse(event("hello") + "data: [DONE]\n\n")) }
            server.start()
            val stream = client().stream(config(server), request)
            assertEquals(0, server.requestCount)
            repeat(2) { assertEquals("hello", stream.toList().single().content) }
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun httpErrorsAreTypedRedactedAndNotRetried() = runBlocking {
        for (status in listOf(302, 401, 403, 404, 429, 500, 503)) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse.Builder().code(status).addHeader("Retry-After", "120")
                    .addHeader("Location", "/forwarded").body("test-secret private conversation").build())
                server.start()
                for (streaming in listOf(false, true)) {
                    if (streaming) server.enqueue(MockResponse.Builder().code(status).addHeader("Retry-After", "120").build())
                    val error = assertThrows(LlmException.Http::class.java) {
                        runBlocking {
                            if (streaming) client().stream(config(server), request).toList()
                            else client().complete(config(server), request)
                        }
                    }
                    assertEquals(status, error.statusCode)
                    assertEquals(120L, error.retryAfterSeconds)
                    assertNull(error.cause)
                    assertFalse(error.toString().contains("test-secret"))
                    assertEquals(if (streaming) 2 else 1, server.requestCount)
                }
            }
        }
    }

    @Test fun malformedNormalResponsesFailWithoutRawPayloads() = runBlocking {
        for (body in listOf("not-json test-secret", "{}", "{\"choices\":[]}",
            "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":null}}]}")) {
            MockWebServer().use { server ->
                server.enqueue(jsonResponse(body))
                server.start()
                val error = assertThrows(LlmException.Protocol::class.java) {
                    runBlocking { client().complete(config(server), request) }
                }
                assertRedacted(error)
            }
        }
    }

    @Test fun serverRetryAfterZeroCannotReplayPaidPost() = runBlocking {
        for (status in listOf(408, 503)) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse.Builder().code(status).addHeader("Retry-After", "0").build())
                server.enqueue(jsonResponse(SUCCESS))
                server.start()
                val error = assertThrows(LlmException.Http::class.java) {
                    runBlocking { client().complete(config(server), request) }
                }
                assertEquals(status, error.statusCode)
                assertEquals(0L, error.retryAfterSeconds)
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test fun rejectsMalformedOrTruncatedStreamsAndWrongContentType(): Unit = runBlocking {
        for (body in listOf("data: invalid test-secret\n\n", event("partial"),
            "data: [DONE]", "data: {}\n\n", "", event("", "stop"))) {
            MockWebServer().use { server ->
                server.enqueue(sseResponse(body))
                server.start()
                assertThrows(LlmException.Protocol::class.java) {
                    runBlocking { client().stream(config(server), request).toList() }
                }
            }
        }
        MockWebServer().use { server ->
            server.enqueue(jsonResponse(SUCCESS))
            server.start()
            assertThrows(LlmException.Protocol::class.java) {
                runBlocking { client().stream(config(server), request).toList() }
            }
        }
    }

    @Test fun handlesInBandServiceErrorsWithoutExposingMessages() = runBlocking {
        MockWebServer().use { server ->
            val errorBody = "{\"error\":{\"message\":\"test-secret private conversation\",\"code\":\"failure\"}}"
            server.enqueue(jsonResponse(errorBody))
            server.enqueue(sseResponse(event("partial") + "data: $errorBody\n\n"))
            server.start()
            for (streaming in listOf(false, true)) {
                val error = assertThrows(LlmException.Remote::class.java) {
                    runBlocking {
                        if (streaming) client().stream(config(server), request).toList()
                        else client().complete(config(server), request)
                    }
                }
                assertRedacted(error)
            }
        }
    }

    @Test fun mapsOfflineFailureWithoutRetainingSensitiveCause() {
        val http = OkHttpClient.Builder().addInterceptor { throw IOException("test-secret at https://private.test") }.build()
        val error = assertThrows(LlmException.Network::class.java) {
            runBlocking { client(http).complete(LlmConfig("https://example.test/v1/", "test-secret", "model"), request) }
        }
        assertRedacted(error)
    }

    @Test fun readAndOverallTimeoutsCoverHeadersAndStreamBody() = runBlocking {
        for (streaming in listOf(false, true)) {
            for (overall in listOf(false, true)) {
                MockWebServer().use { server ->
                    val response = if (streaming) MockResponse.Builder().addHeader("Content-Type", "text/event-stream")
                        .body(event("late") + "data: [DONE]\n\n").bodyDelay(1, TimeUnit.SECONDS).build()
                    else MockResponse.Builder().body(SUCCESS).headersDelay(1, TimeUnit.SECONDS).build()
                    server.enqueue(response)
                    server.start()
                    val timeout = LlmTimeouts(readMillis = if (overall) 5_000 else 100, callMillis = if (overall) 100 else 5_000)
                    val config = LlmConfig(server.url("/v1/").toString(), "test-secret", "model", timeout)
                    assertThrows(LlmException.Timeout::class.java) {
                        runBlocking {
                            if (streaming) client().stream(config, request).toList()
                            else client().complete(config, request)
                        }
                    }
                    assertEquals(1, server.requestCount)
                }
            }
        }
    }

    @Test fun cancellationBeforeHeadersCancelsCallPromptly() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val call = AtomicReference<Call>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            call.set(chain.call())
            started.complete(Unit)
            check(release.await(5, TimeUnit.SECONDS))
            throw IOException("cancelled")
        }.build()
        try {
            val job = async { client(http).complete(LlmConfig("https://example.test/v1/", "test-secret", "model"), request) }
            withTimeout(2_000) { started.await() }
            withTimeout(2_000) { job.cancelAndJoin() }
            assertTrue(call.get().isCanceled())
            assertTrue(job.isCancelled)
        } finally { release.countDown() }
    }

    @Test fun cancellationDuringBlockedBodyReadClosesConnectionForBothModes() = runBlocking {
        for (streaming in listOf(false, true)) {
            MockWebServer().use { server ->
                val reading = CompletableDeferred<Unit>()
                val cancelled = CompletableDeferred<Unit>()
                val http = DataModule.httpClient().newBuilder().eventListener(object : EventListener() {
                    override fun canceled(call: Call) { cancelled.complete(Unit) }
                }).addInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    val body = response.body
                    val source = object : ForwardingSource(body.source()) {
                        override fun read(sink: Buffer, byteCount: Long): Long {
                            reading.complete(Unit)
                            return super.read(sink, byteCount)
                        }
                    }.buffer()
                    response.newBuilder().body(object : ResponseBody() {
                        override fun contentType() = body.contentType()
                        override fun contentLength() = body.contentLength()
                        override fun source() = source
                    }).build()
                }.build()
                server.enqueue(MockResponse.Builder().addHeader("Content-Type", if (streaming) "text/event-stream" else "application/json")
                    .body(if (streaming) event("late") else SUCCESS).bodyDelay(3, TimeUnit.SECONDS).build())
                server.start()
                val job = async {
                    if (streaming) client(http).stream(config(server), request).toList()
                    else client(http).complete(config(server), request)
                }
                withTimeout(2_000) { reading.await() }
                withTimeout(2_000) { job.cancelAndJoin(); cancelled.await() }
                assertTrue(job.isCancelled)
            }
        }
    }

    @Test fun earlyCollectorExitAndConsumerErrorsReleaseResponseWithoutWrapping() = runBlocking {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(sseResponse(event("hello") + event("world") + "data: [DONE]\n\n")) }
            server.start()
            val cancelled = mutableListOf<Call>()
            val http = DataModule.httpClient().newBuilder().eventListener(object : EventListener() {
                override fun canceled(call: Call) { synchronized(cancelled) { cancelled += call } }
            }).build()
            assertEquals("hello", client(http).stream(config(server), request).first().content)
            val expected = IllegalStateException("consumer failure")
            val actual = assertThrows(IllegalStateException::class.java) {
                runBlocking { client(http).stream(config(server), request).collect { throw expected } }
            }
            // 协程 debug stack recovery 可能复制异常，但不能将其包装成 LlmException。
            assertEquals(expected.message, actual.message)
            assertSame(expected, generateSequence<Throwable>(actual) { it.cause }.last())
            assertEquals(2, synchronized(cancelled) { cancelled.size })
        }
    }

    @Test fun successfulAndFailedParsingCloseResponseBodies() = runBlocking {
        for (streaming in listOf(false, true)) {
            for (valid in listOf(false, true)) {
                var closed = false
                val buffer = Buffer().writeUtf8(if (!valid) "invalid" else if (streaming) event("hello") + "data: [DONE]\n\n" else SUCCESS)
                val source = object : ForwardingSource(buffer) {
                    override fun close() { closed = true; super.close() }
                }.buffer()
                val stableBody = object : ResponseBody() {
                    override fun contentType() = (if (streaming) "text/event-stream" else "application/json").toMediaType()
                    override fun contentLength() = -1L
                    override fun source() = source
                }
                val http = OkHttpClient.Builder().addInterceptor { chain ->
                    Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(stableBody).build()
                }.build()
                try {
                    if (streaming) client(http).stream(LlmConfig("https://example.test/v1/", "test-secret", "model"), request).toList()
                    else client(http).complete(LlmConfig("https://example.test/v1/", "test-secret", "model"), request)
                    assertTrue(valid)
                } catch (_: LlmException.Protocol) { assertFalse(valid) }
                assertTrue(closed)
            }
        }
    }

    @Test fun rejectsOversizedResponsesAndEvents() = runBlocking {
        for (streaming in listOf(false, true)) {
            val payload = "x".repeat(if (streaming) 1024 * 1024 + 1 else 8 * 1024 * 1024 + 1)
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body((if (streaming) "data: $payload\n\n" else payload).toResponseBody(
                        (if (streaming) "text/event-stream" else "application/json").toMediaType(),
                    )).build()
            }.build()
            assertThrows(LlmException.Protocol::class.java) {
                runBlocking {
                    if (streaming) client(http).stream(LlmConfig("https://example.test/v1/", "test-secret", "model"), request).toList()
                    else client(http).complete(LlmConfig("https://example.test/v1/", "test-secret", "model"), request)
                }
            }
        }
    }

    @Test fun unsafeConfigurationFailsBeforeNetworkAndConfigToStringIsRedacted(): Unit = runBlocking {
        var requests = 0
        val http = OkHttpClient.Builder().addInterceptor { requests++; throw IOException("unexpected") }.build()
        for (url in listOf("invalid", "http://example.com/v1/", "https://user:password@example.com/", "https://example.com/?key=secret", "https://example.com/#fragment")) {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { client(http).complete(LlmConfig(url, "test-secret", "model"), request) }
            }
        }
        assertEquals(0, requests)
        assertFalse(LlmConfig("https://example.test/v1/", "test-secret", "model").toString().contains("test-secret"))
        for (key in listOf("", " ", "key\nheader")) {
            assertThrows(IllegalArgumentException::class.java) { LlmConfig("https://example.test/", key, "model") }
        }
        assertThrows(IllegalArgumentException::class.java) { LlmRequest(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { request.copy(temperature = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { request.copy(maxOutputTokens = 0) }
        assertThrows(IllegalArgumentException::class.java) { LlmTimeouts(callMillis = 0) }
    }

    private fun client(http: OkHttpClient = DataModule.httpClient()) = OpenAiCompatibleClient(http, Dispatchers.IO)
    private fun assertRedacted(error: Throwable) {
        generateSequence(error) { it.cause }.forEach {
            assertTrue(it is LlmException)
            assertFalse(it.toString().contains("test-secret"))
            assertFalse(it.toString().contains("private conversation"))
            assertFalse(it.toString().contains("https://private.test"))
        }
    }
    private fun config(server: MockWebServer, path: String = "/gateway/v1/") =
        LlmConfig(server.url(path).toString(), "test-secret", "configured-model")
    private fun jsonResponse(body: String) = MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()
    private fun sseResponse(body: String) = MockResponse.Builder().addHeader("Content-Type", "text/event-stream").body(body).build()
    private fun event(text: String, reason: String? = null): String =
        "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"$text\"},\"finish_reason\":${reason?.let { "\"$it\"" } ?: "null"}}]}\n\n"

    private companion object {
        const val SUCCESS = """{"id":"response-id","model":"actual-model","choices":[{"index":0,"message":{"role":"assistant","content":"你好 🌍"},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":2,"total_tokens":12},"future_field":true}"""
    }
}
