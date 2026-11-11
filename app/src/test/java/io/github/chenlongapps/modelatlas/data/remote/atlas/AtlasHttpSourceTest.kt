package io.github.chenlongapps.modelatlas.data.remote.atlas

import java.io.ByteArrayInputStream
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
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
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

    @Test fun rejectsRedirectsOversizedFilesAndPreservesRetryAfter() = runBlocking {
        for (status in listOf(302, 429, 503, 200)) {
            var requests = 0
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                requests++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("test")
                    .header("Location", "https://other.test/").header("Retry-After", "120")
                    .body(ByteArray(100).toResponseBody()).build()
            }.build()
            try {
                AtlasHttpSource(client).download(ATLAS_BASE_URL + "models.json", 50)
                fail()
            } catch (e: HttpException) {
                assertEquals(status, e.code())
                assertEquals("120", e.response()!!.headers()["Retry-After"])
            } catch (_: IllegalArgumentException) { assertEquals(200, status) }
            assertEquals(1, requests)
        }
    }

    @Test fun streamingLimitsCoverExactEmptyAndUnknownLengthBodies() = runBlocking {
        for (size in listOf(0, 4, 8193)) {
            val bytes = ByteArray(size) { it.toByte() }
            assertArrayEquals(bytes, ByteArrayInputStream(bytes).readAtlasBytes(size))
            assertThrows(IllegalArgumentException::class.java) {
                ByteArrayInputStream(bytes + 1.toByte()).readAtlasBytes(size)
            }
        }
        assertThrows(IllegalArgumentException::class.java) { ByteArrayInputStream(byteArrayOf()).readAtlasBytes(-1) }
        for (size in listOf(4, 5)) {
            val body = object : ResponseBody() {
                private val buffer = Buffer().write(ByteArray(size))
                override fun contentType() = null
                override fun contentLength() = -1L
                override fun source() = buffer
            }
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(body).build()
            }.build()
            try {
                assertArrayEquals(ByteArray(4), AtlasHttpSource(client).download(ATLAS_BASE_URL + "models.json", 4))
                assertEquals(4, size)
            } catch (_: IllegalArgumentException) { assertEquals(5, size) }
        }
    }

    @Test fun unsafeUrlsAreRejectedBeforeNetworkAndOfflineErrorsPropagate() = runBlocking {
        var requests = 0
        val source = AtlasHttpSource(OkHttpClient.Builder().addInterceptor {
            requests++
            throw IOException("offline")
        }.build())
        for (url in listOf("http://example.org/data", "https://user:pass@example.org/data", "https://example.org/data#fragment")) {
            try { source.download(url, 100); fail("Expected unsafe URL rejection") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, requests)
        try { source.download(ATLAS_BASE_URL + "models.json", 100); fail("Expected network failure") }
        catch (error: IOException) { assertEquals("offline", error.message) }
        assertEquals(1, requests)
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
