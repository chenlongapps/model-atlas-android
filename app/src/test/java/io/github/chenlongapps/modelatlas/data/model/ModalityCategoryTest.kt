package io.github.chenlongapps.modelatlas.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModalityCategoryTest {
    @Test fun mimeFamiliesCollapseToExistingCategoriesInFirstOccurrenceOrder() {
        val raw = listOf("text", "image/png", "image/jpeg", "image/webp", "video/mp4", "video/mov",
            "audio/wav", "audio/mp3", "audio/mulaw", "application/pdf", "text/plain", "image", "audio", "pdf")
        assertEquals(listOf("text", "image", "video", "audio", "pdf"), raw.toModalityCategories())
        assertEquals("image/png", raw[1])
    }

    @Test fun legacyCategoriesAreUnchangedAndProjectionIsIdempotent() {
        val values = listOf("text", "image", "audio", "video", "pdf", "future")
        assertEquals(values, values.toModalityCategories())
        assertEquals(values.toModalityCategories(), values.toModalityCategories().toModalityCategories())
    }

    @Test fun missingEmptyUnknownAndDifferentDirectionsRemainDistinct() {
        val missing: List<String>? = null
        assertNull(missing.toModalityCategories())
        assertEquals(emptyList<String>(), emptyList<String>().toModalityCategories())
        val unknown = listOf("application/json", "future", "image/", "embedding")
        assertEquals(unknown, unknown.toModalityCategories())
        assertEquals(listOf("image"), listOf("image/jpeg").toModalityCategories())
        assertEquals(listOf("audio"), listOf("audio/mp3").toModalityCategories())
    }

    @Test fun explicitMimeTypesAreCaseInsensitiveAndMayHaveParameters() {
        assertEquals(listOf("image", "audio", "video", "text", "pdf"), listOf(
            "IMAGE/PNG", "audio/ogg; codecs=opus", " video/webm ", "text/plain; charset=UTF-8", "Application/PDF",
        ).toModalityCategories())
        assertEquals("Application/Unknown", "Application/Unknown".toModalityCategory())
    }
}
