package io.github.chenlongapps.modelatlas.data.remote.atlas

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.chenlongapps.modelatlas.data.benchmark.readBenchmarkBytes
import io.github.chenlongapps.modelatlas.di.IoDispatcher
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException

interface AtlasDataSource {
    suspend fun fetch(): AtlasRelease
    suspend fun bundled(): AtlasRelease? = null
}

@Singleton
class HttpAtlasDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    client: OkHttpClient,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : AtlasDataSource {
    private val http = AtlasHttpSource(client)

    override suspend fun fetch(): AtlasRelease = withContext(dispatcher) {
        AtlasReleaseLoader { path, limit -> http.download(ATLAS_BASE_URL + path, limit) }.load()
    }

    override suspend fun bundled(): AtlasRelease = withContext(dispatcher) {
        AtlasReleaseLoader { path, limit ->
            context.assets.open("atlas/$path").use { it.readBenchmarkBytes(limit) }
        }.load()
    }
}

/** 固定调用方提供的 HTTPS 站点；无重定向、流式限长，取消会中止 OkHttp Call。 */
internal class AtlasHttpSource(client: OkHttpClient) {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    suspend fun download(url: String, limit: Int): ByteArray = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).build()
        require(request.url.isHttps && request.url.username.isEmpty() && request.url.password.isEmpty() && request.url.fragment == null)
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (!it.isSuccessful) throw HttpException(retrofit2.Response.error<Any>(
                            ByteArray(0).toResponseBody(), it,
                        ))
                        val body = requireNotNull(it.body)
                        require(body.contentLength() <= limit)
                        body.byteStream().use { stream -> stream.readBenchmarkBytes(limit) }
                    }
                }
                result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }
}
