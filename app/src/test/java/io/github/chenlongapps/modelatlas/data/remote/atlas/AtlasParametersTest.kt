package io.github.chenlongapps.modelatlas.data.remote.atlas

import io.github.chenlongapps.modelatlas.data.local.ModelDetailRecord
import io.github.chenlongapps.modelatlas.data.mapper.CatalogMapper
import io.github.chenlongapps.modelatlas.data.mapper.mapAtlas
import io.github.chenlongapps.modelatlas.data.mapper.toModelDetail
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class AtlasParametersTest {
    private val source = "\"sources\":[\"https://example.org/model-card\"]"

    private suspend fun release(parameters: String) = atlasRelease(atlasFiles(
        models = """{"lab/model":{"schema":2,"name":"Not a guessed 99B","type":"llm","metadata":{"unrelated":[false,1,null],"modelatlas":{"future":true,"parameters":$parameters}}}}""",
        definitions = "{}", translations = "{}",
    ))

    @Test fun preservesDecimalPrecisionComponentsNotesAndSourcesThroughBothMappers() = runTest {
        val release = release("""{"total_billion":70.553706496123456789,"active_billion":3.3,$source,"notes":"Decode only","components":[{"name":"Vision encoder","total_billion":0.41184256,$source,"notes":"Component only"}]}""")
        val dto = release.models.values.single().metadata!!.modelatlas!!.parameters!!
        assertEquals("70.553706496123456789".toBigDecimal(), dto.totalBillion)
        val encoded = AtlasJson.encodeToString(release.models)
        val roundTrip = atlasRelease(atlasFiles(models = encoded, definitions = "{}", translations = "{}"))
        assertEquals(dto, roundTrip.models.values.single().metadata!!.modelatlas!!.parameters)
        val stored = CatalogMapper().mapAtlas(release, emptyMap(), 1, 1).models.single()
        val detail = ModelDetailRecord(stored, emptyList()).toModelDetail()!!
        assertEquals(dto.totalBillion, stored.parameters!!.totalBillion)
        assertEquals(dto.totalBillion, detail.parameters!!.totalBillion)
        assertEquals("Decode only", detail.parameters.notes)
        val component = detail.parameters.components!!.single()
        assertEquals("Vision encoder", component.name)
        assertEquals("0.41184256".toBigDecimal(), component.totalBillion)
        assertEquals(listOf("https://example.org/model-card"), component.sources)
        assertEquals("Component only", component.notes)
    }

    @Test fun totalOnlyActiveOnlyAndComponentsOnlyDoNotInventOtherCounts() = runTest {
        for (json in listOf("""{"total_billion":8.2,$source}""", """{"active_billion":16,$source}""",
            """{"notes":"Complete total unknown","components":[{"name":"Decoder","total_billion":12,$source},{"name":"Encoder","total_billion":0.4,$source}]}""")) {
            val result = release(json)
            val dto = result.models.values.single().metadata!!.modelatlas!!.parameters!!
            val detail = ModelDetailRecord(CatalogMapper().mapAtlas(result, emptyMap(), 1, 1).models.single(), emptyList()).toModelDetail()!!
            assertEquals(dto.totalBillion, detail.parameters!!.totalBillion)
            assertEquals(dto.activeBillion, detail.parameters.activeBillion)
            if (dto.components != null) {
                assertNull(detail.parameters.totalBillion)
                assertNull(detail.parameters.activeBillion)
                assertNull(detail.parameters.sources)
                assertEquals(listOf("Decoder", "Encoder"), detail.parameters.components!!.map { it.name })
            }
        }
    }

    @Test fun missingMetadataOrParametersAndExplicitNullStayUnknown() = runTest {
        for (metadata in listOf("", ",\"metadata\":null", ",\"metadata\":{}",
            ",\"metadata\":{\"other\":true}", ",\"metadata\":{\"modelatlas\":{\"parameters\":null}}")) {
            val release = atlasRelease(atlasFiles(models = """{"lab/model":{"schema":2,"name":"99B","type":"llm"$metadata}}""",
                definitions = "{}", translations = "{}"))
            val model = CatalogMapper().mapAtlas(release, emptyMap(), 1, 1).models.single()
            assertNull(model.parameters)
            assertNull(ModelDetailRecord(model, emptyList()).toModelDetail()!!.parameters)
        }
    }

    @Test fun rejectsCoercedNonPositiveNonFiniteAndInconsistentCounts() = runTest {
        val counts = listOf("\"7\"", "true", "0", "-1", "1e999", "1e-999")
        for (count in counts) for (field in listOf("total_billion", "active_billion")) {
            rejected("""{"$field":$count,$source}""")
            rejected("""{"components":[{"name":"Decoder","$field":$count,$source}]}""")
        }
        rejected("""{"total_billion":1,"active_billion":1.000000000000000000001,$source}""")
        rejected("""{"components":[{"name":"Decoder","total_billion":1,"active_billion":2,$source}]}""")
    }

    @Test fun rejectsMissingEvidenceWrongKnownTypesAndInvalidComponents() = runTest {
        for (json in listOf("{}", "[]", "true", """{"notes":"Only notes"}""",
            """{"components":[]}""", """{"total_billion":7}""",
            """{"total_billion":7,"sources":[]}""", """{"total_billion":7,"sources":[1]}""",
            """{"total_billion":7,$source,"notes":1}""", """{"total_billion":7,$source,"notes":" "}""",
            """{"total_billion":7,"sources":["file:///private/model"]}""",
            """{"total_billion":7,"sources":["https://example.org","https://example.org"]}""",
            """{"total_billion":7,"sources":["https://user@example.org"]}""",
            """{"components":[{"name":"Decoder","total_billion":12}]}""",
            """{"components":[{"name":" ","total_billion":12,$source}]}""",
            """{"components":[{"name":"Decoder","total_billion":12,$source},{"name":"Decoder","total_billion":12,$source}]}""")) {
            rejected(json)
        }
    }

    @Test fun realPublishedCountsCoverDenseMoeActiveOnlyAndComponentsOnlyWithoutInference() = runTest {
        val release = AtlasReleaseLoader { path, _ -> File("src/main/assets/atlas/$path").readBytes() }.load()
        val models = CatalogMapper().mapAtlas(release, emptyMap(), 1, 1).models.associateBy { it.id }
        assertEquals(373, models.values.count { it.parameters != null })
        fun detail(id: String) = ModelDetailRecord(models.getValue(id), emptyList()).toModelDetail()!!
        assertEquals("671".toBigDecimal(), detail("deepseek/deepseek-r1").parameters!!.totalBillion)
        assertEquals("37".toBigDecimal(), detail("deepseek/deepseek-r1").parameters!!.activeBillion)
        assertEquals("8.2".toBigDecimal(), detail("alibaba/qwen3-8b").parameters!!.totalBillion)
        assertNull(detail("alibaba/qwen3-8b").parameters!!.activeBillion)
        assertNull(detail("deepseek/deepseek-v4.1-flash").parameters!!.totalBillion)
        assertEquals("16".toBigDecimal(), detail("deepseek/deepseek-v4.1-flash").parameters!!.activeBillion)
        assertNull(detail("mistral/pixtral-12b").parameters!!.totalBillion)
        assertEquals(2, detail("mistral/pixtral-12b").parameters!!.components!!.size)
        assertNull(detail("openai/gpt-4o").parameters)
    }

    private suspend fun rejected(json: String) {
        try { release(json); fail("Accepted invalid parameters: $json") }
        catch (_: SerializationException) { }
    }
}
