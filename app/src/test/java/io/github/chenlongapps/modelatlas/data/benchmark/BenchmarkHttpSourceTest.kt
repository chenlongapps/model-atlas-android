package io.github.chenlongapps.modelatlas.data.benchmark

import java.io.ByteArrayInputStream
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class BenchmarkHttpSourceTest {
    @Test fun httpsDownloadAndStreamingLimitsWorkWithKnownAndUnknownLength() {
        fun source(body: ResponseBody) = BenchmarkHttpSource(OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
        }.build())
        assertArrayEquals("1234".toByteArray(), source("1234".toResponseBody()).download("https://example.org/data", 4))
        try { source("12345".toResponseBody()).download("https://example.org/data", 4); fail() } catch (_: IllegalArgumentException) { }
        val chunked = object : ResponseBody() {
            override fun contentType() = "application/json".toMediaType()
            override fun contentLength() = -1L
            override fun source() = Buffer().writeUtf8("12345")
        }
        try { source(chunked).download("https://example.org/data", 4); fail() } catch (_: IllegalArgumentException) { }
        try { ByteArrayInputStream(ByteArray(100)).readBenchmarkBytes(4); fail() } catch (_: IllegalArgumentException) { }
    }

    @Test fun cleartextCredentialsAndFragmentsAreRejectedBeforeNetwork() {
        var requests = 0
        val source = BenchmarkHttpSource(OkHttpClient.Builder().addInterceptor { requests++; throw IOException("unexpected request") }.build())
        listOf("http://example.org/data", "https://user:pass@example.org/data", "https://example.org/data#fragment").forEach {
            try { source.download(it, 100); fail() } catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, requests)
    }

    @Test fun httpFailuresRedirectsAndNetworkFailureDoNotReturnData() {
        listOf(302, 404, 503).forEach { code ->
            var requests = 0
            val source = BenchmarkHttpSource(OkHttpClient.Builder().addInterceptor { chain ->
                requests++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("failed")
                    .header("Location", "http://example.org/redirected").body("bad".toResponseBody()).build()
            }.build())
            try { source.download("https://example.org/data", 100); fail() } catch (_: IllegalStateException) { }
            assertEquals(1, requests)
        }
        val offline = BenchmarkHttpSource(OkHttpClient.Builder().addInterceptor { throw IOException("offline") }.build())
        try { offline.download("https://example.org/data", 100); fail() } catch (_: IOException) { }
    }
}
