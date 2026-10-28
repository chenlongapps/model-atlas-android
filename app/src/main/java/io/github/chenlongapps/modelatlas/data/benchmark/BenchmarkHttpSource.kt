package io.github.chenlongapps.modelatlas.data.benchmark

import java.io.ByteArrayOutputStream
import java.io.InputStream
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** 固定 HTTPS、禁用重定向；长度未知时也在流式读取过程中限制内存。 */
internal class BenchmarkHttpSource(client: OkHttpClient) {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    fun download(url: String, maxBytes: Int): ByteArray {
        val parsed = url.toHttpUrl()
        require(parsed.isHttps && parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.fragment == null)
        val request = Request.Builder().url(parsed).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Benchmark HTTP ${response.code}" }
            val body = requireNotNull(response.body)
            require(body.contentLength() <= maxBytes)
            return body.byteStream().use { it.readBenchmarkBytes(maxBytes) }
        }
    }
}

internal fun InputStream.readBenchmarkBytes(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count == -1) break
        require(count <= maxBytes - output.size()) { "Benchmark data exceeds byte limit" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
