package io.github.chenlongapps.modelatlas.data.remote.atlas

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException

class AtlasHttpSourceTest {
    @Test fun realHttpsSourceLoadsExactPublicationPathsAndChecksAllBytes() = runBlocking {
        val files = atlasFiles()
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.toString().removePrefix(ATLAS_BASE_URL)
            synchronized(requests) { requests += path }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(files.getValue(path).toResponseBody()).build()
        }.build()
        val source = AtlasHttpSource(client)
        val release = AtlasReleaseLoader { path, limit -> source.download(ATLAS_BASE_URL + path, limit) }.load()
        assertEquals("Model", release.models.values.single().name)
        assertEquals(listOf("manifest.json") + ATLAS_FILES, requests)
    }

    @Test fun rejectsHttpRedirectsOversizedFilesAndPreservesRetryAfter() = runBlocking {
        for (status in listOf(302, 429, 503, 200)) {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("test")
                    .header("Location", "https://other.test/").header("Retry-After", "120")
                    .body(ByteArray(100).toResponseBody()).build()
            }.build()
            assertFalse(client.newBuilder().followRedirects(false).build().followRedirects)
            try {
                AtlasHttpSource(client).download(ATLAS_BASE_URL + "models.json", 50)
                fail()
            } catch (e: HttpException) {
                assertEquals(status, e.code())
                assertEquals("120", e.response()!!.headers()["Retry-After"])
            } catch (_: IllegalArgumentException) { assertEquals(200, status) }
        }
        try { AtlasHttpSource(OkHttpClient()).download("http://example.org/file", 100); fail() }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun cancellationStopsWaitingForNetworkAndCannotReturnARelease() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            started.complete(Unit)
            check(release.await(5, TimeUnit.SECONDS))
            throw IOException("cancelled test")
        }.build()
        try {
            val request = async(Dispatchers.Default) { AtlasHttpSource(client).download(ATLAS_BASE_URL + "models.json", 100) }
            started.await()
            request.cancelAndJoin()
            assertTrue(request.isCancelled)
        } finally { release.countDown() }
    }
}
