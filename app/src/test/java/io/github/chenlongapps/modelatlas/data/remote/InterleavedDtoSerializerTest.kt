package io.github.chenlongapps.modelatlas.data.remote

import io.github.chenlongapps.modelatlas.data.remote.dto.InterleavedDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class InterleavedDtoSerializerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun booleanVariantsRoundTripWithoutAnObjectWrapper() {
        listOf(true, false).forEach { enabled ->
            val value: InterleavedDto = InterleavedDto.Toggle(enabled)
            val encoded = json.encodeToString(value)

            assertEquals(enabled.toString(), encoded)
            assertEquals(value, json.decodeFromString<InterleavedDto>(encoded))
        }
    }

    @Test
    fun fieldVariantRoundTripsWithoutADiscriminator() {
        val value: InterleavedDto = InterleavedDto.Field("reasoning_content")
        val encoded = json.encodeToString(value)

        assertEquals("""{"field":"reasoning_content"}""", encoded)
        assertEquals(value, json.decodeFromString<InterleavedDto>(encoded))
    }

    @Test
    fun nullableValuesRoundTripAsJsonNull() {
        assertEquals("null", json.encodeToString<InterleavedDto?>(null))
        assertNull(json.decodeFromString<InterleavedDto?>("null"))
    }

    @Test
    fun fieldObjectsIgnoreUnknownKeys() {
        val value = json.decodeFromString<InterleavedDto>(
            """{"field":"reasoning_content","future":true}""",
        )

        assertEquals(InterleavedDto.Field("reasoning_content"), value)
    }

    @Test
    fun invalidVariantsAreRejectedRatherThanCoerced() {
        listOf("1", "[]", "\"true\"", "{}", "{\"field\":null}").forEach { source ->
            assertThrows(SerializationException::class.java) {
                json.decodeFromString<InterleavedDto>(source)
            }
        }
    }
}
