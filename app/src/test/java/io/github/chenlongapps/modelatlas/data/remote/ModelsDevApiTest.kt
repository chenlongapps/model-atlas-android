package io.github.chenlongapps.modelatlas.data.remote

import io.github.chenlongapps.modelatlas.di.DataModule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class ModelsDevApiTest {
    @Test fun requestsAllTypesAndDecodesBothEndpointsWithProductionJson() {
        MockWebServer().use { server ->
            server.enqueue(jsonResponse(fixture("models")))
            server.enqueue(jsonResponse(fixture("api")))
            server.start()
            val api = api(server)
            runBlocking {
                assertEquals("Example Atlas", api.getModels()["example/atlas"]!!.name)
                assertEquals(2, api.getProviders().size)
            }
            val modelsRequest = server.takeRequest().url
            val providersRequest = server.takeRequest().url
            assertEquals("/models.json", modelsRequest.encodedPath)
            assertEquals("all", modelsRequest.queryParameter("type"))
            assertEquals("/api.json", providersRequest.encodedPath)
            assertEquals("all", providersRequest.queryParameter("type"))
        }
    }

    @Test fun propagatesHttpFailureWithoutAutomaticRetry() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().code(503).addHeader("Retry-After", "120").build())
            server.start()
            val error = assertThrows(HttpException::class.java) {
                runBlocking { api(server).getModels() }
            }
            assertEquals(503, error.code())
            assertEquals("120", error.response()!!.headers()["Retry-After"])
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun invalidPayloadIsNotSilentlyConvertedToEmptyCatalog() {
        MockWebServer().use { server ->
            server.enqueue(jsonResponse("{\"model\":{\"id\":\"model\"}}"))
            server.start()
            assertThrows(SerializationException::class.java) {
                runBlocking { api(server).getModels() }
            }
        }
    }

    private fun api(server: MockWebServer): ModelsDevApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(DataModule.httpClient())
        .addConverterFactory(ModelsDevJson.asConverterFactory("application/json".toMediaType()))
        .build().create(ModelsDevApi::class.java)

    private fun jsonResponse(body: String) = MockResponse.Builder()
        .addHeader("Content-Type", "application/json").body(body).build()

    private fun fixture(name: String) = javaClass.getResource("/modelsdev/$name.json")!!.readText()
}
