package io.github.chenlongapps.modelatlas.ai.openai

import io.github.chenlongapps.modelatlas.ai.LlmChunk
import io.github.chenlongapps.modelatlas.ai.LlmClient
import io.github.chenlongapps.modelatlas.ai.LlmConfig
import io.github.chenlongapps.modelatlas.ai.LlmException
import io.github.chenlongapps.modelatlas.ai.LlmRequest
import io.github.chenlongapps.modelatlas.ai.LlmResponse
import io.github.chenlongapps.modelatlas.di.IoDispatcher
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink

/** 复用共享连接池，但不改变目录客户端；不记录请求、响应或凭据，不自动重试付费请求。 */
@Singleton
class OpenAiCompatibleClient @Inject constructor(
    private val client: OkHttpClient,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : LlmClient {
    override suspend fun complete(config: LlmConfig, request: LlmRequest): LlmResponse = withContext(dispatcher) {
        withResponse(config, request, streaming = false) { response ->
            val dto = network {
                val source = response.body.source()
                if (source.request(MAX_RESPONSE_BYTES + 1)) throw LlmException.Protocol()
                decode(source.readUtf8())
            }
            val choice = dto.choices.singleOrNull { it.index == 0 } ?: throw LlmException.Protocol()
            val message = choice.message ?: throw LlmException.Protocol()
            if (message.role != "assistant") throw LlmException.Protocol()
            LlmResponse(
                content = message.content ?: throw LlmException.Protocol(),
                finishReason = choice.finishReason,
                id = dto.id,
                modelId = dto.model,
                usage = dto.usage?.toModel(),
            )
        }
    }

    override fun stream(config: LlmConfig, request: LlmRequest): Flow<LlmChunk> = flow {
        withResponse(config, request, streaming = true) { response ->
            if (response.body.contentType()?.let { it.type == "text" && it.subtype == "event-stream" } != true) {
                throw LlmException.Protocol()
            }
            val events = ServerSentEvents(response.body.source())
            while (true) {
                coroutineContext.ensureActive()
                val data = network { events.nextData() } ?: throw LlmException.Protocol()
                if (data.trim() == "[DONE]") break
                val dto = decode(data)
                val choice = dto.choices.singleOrNull { it.index == 0 }
                if (choice == null && (dto.choices.isNotEmpty() || dto.usage == null)) throw LlmException.Protocol()
                val chunk = LlmChunk(
                    content = choice?.delta?.content.orEmpty(),
                    finishReason = choice?.finishReason,
                    usage = dto.usage?.toModel(),
                )
                if (chunk.content.isNotEmpty() || chunk.finishReason != null || chunk.usage != null) emit(chunk)
            }
        }
    }.flowOn(dispatcher)

    private suspend fun <T> withResponse(
        config: LlmConfig,
        request: LlmRequest,
        streaming: Boolean,
        consume: suspend (Response) -> T,
    ): T = coroutineScope {
        val base = config.baseUrl.toHttpUrlOrNull()
        require(base != null && base.username.isEmpty() && base.password.isEmpty() &&
            base.query == null && base.fragment == null &&
            (base.isHttps || base.host in setOf("localhost", "127.0.0.1", "::1"))) { "Invalid LLM API base URL" }
        val url = base.newBuilder().apply {
            if (!base.encodedPath.endsWith('/')) addPathSegment("")
        }.addPathSegment("chat").addPathSegment("completions").build()
        val http = client.newBuilder()
            .connectTimeout(config.timeouts.connectMillis, TimeUnit.MILLISECONDS)
            .readTimeout(config.timeouts.readMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(config.timeouts.writeMillis, TimeUnit.MILLISECONDS)
            .callTimeout(config.timeouts.callMillis, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val body = LlmJson.encodeToString(ChatRequestDto.from(config.modelId, request, streaming))
        val jsonBody = body.toRequestBody("application/json; charset=utf-8".toMediaType())
        // retryOnConnectionFailure(false) 不阻止 503 + Retry-After: 0 等 HTTP follow-up。
        // 标记不可重放，确保一次调用最多发送一次付费 POST。
        val oneShotBody = object : RequestBody() {
            override fun contentType() = jsonBody.contentType()
            override fun contentLength() = jsonBody.contentLength()
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) = jsonBody.writeTo(sink)
        }
        val call = http.newCall(Request.Builder().url(url)
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Accept", if (streaming) "text/event-stream" else "application/json")
            .post(oneShotBody).build())
        // 持续覆盖响应头之后的阻塞 body/SSE 读取，不仅在等待 enqueue 回调时支持取消。
        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try {
            network { awaitResponse(call) }.use { response ->
                if (!response.isSuccessful) throw LlmException.Http(
                    statusCode = response.code,
                    retryAfterSeconds = response.header("Retry-After")?.toLongOrNull()?.takeIf { it >= 0 },
                )
                consume(response)
            }
        } finally {
            cancellation.cancel()
            call.cancel()
        }
    }

    private suspend fun awaitResponse(call: Call): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response, onCancellation = { _, value, _ -> value.close() })
            }
        })
    }

    private fun decode(data: String): ChatResponseDto {
        val dto = try { LlmJson.decodeFromString<ChatResponseDto>(data) }
        catch (_: SerializationException) { throw LlmException.Protocol() }
        catch (_: IllegalArgumentException) { throw LlmException.Protocol() }
        if (dto.error != null && dto.error != JsonNull) throw LlmException.Remote()
        return dto
    }

    private suspend fun <T> network(block: suspend () -> T): T = try { block() }
    catch (_: InterruptedIOException) {
        coroutineContext.ensureActive()
        throw LlmException.Timeout()
    } catch (_: IOException) {
        coroutineContext.ensureActive()
        throw LlmException.Network()
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 8L * 1024 * 1024
    }
}
