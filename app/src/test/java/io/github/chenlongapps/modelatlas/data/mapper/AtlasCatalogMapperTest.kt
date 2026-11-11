package io.github.chenlongapps.modelatlas.data.mapper

import io.github.chenlongapps.modelatlas.data.local.ModelBenchmarkRecord
import io.github.chenlongapps.modelatlas.data.local.ModelDetailRecord
import io.github.chenlongapps.modelatlas.data.local.ModelListRow
import io.github.chenlongapps.modelatlas.data.remote.atlas.AtlasReleaseLoader
import java.io.File
import io.github.chenlongapps.modelatlas.data.remote.atlas.atlasFiles
import io.github.chenlongapps.modelatlas.data.remote.atlas.atlasRelease
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class AtlasCatalogMapperTest {
    @Test fun popularityIsMappedAndParticipatesInCatalogContentComparison() = runTest {
        val base = atlasRelease()
        val original = CatalogMapper().mapAtlas(base, emptyMap(), 1, 1)
        assertNull(original.definitions!!.single().popularity)
        val release = base.copy(benchmarks = base.benchmarks.mapValues { (_, b) -> b.copy(popularity = 0.98) })
        val updated = CatalogMapper().mapAtlas(release, emptyMap(), 1, 1)
        assertEquals(0.98, updated.definitions!!.single().popularity!!, 0.0)
        assertFalse(original.hasSameContentAs(updated))
        assertFalse(updated.hasSameContentAs(original))
        assertEquals(original.scores, updated.scores)
    }

    @Test fun actualGeminiMimeListProjectsToFiveCategoriesButStoredFormatsAreIntact() = runTest {
        val release = AtlasReleaseLoader { path, _ -> File("src/main/assets/atlas/$path").readBytes() }.load()
        val snapshot = CatalogMapper().mapAtlas(release, emptyMap(), 1, 1)
        val model = snapshot.models.first { it.id == "google/gemini-2.0-flash" }
        assertEquals(29, model.inputModalities!!.size)
        assertEquals(release.models.getValue(model.id).modalities!!.input, model.inputModalities)
        val detail = ModelDetailRecord(model, emptyList()).toModelDetail()!!
        val summary = listOf(ModelListRow(model.id, model.name, model.description, model.type,
            model.inputModalities, model.outputModalities, null, null)).toModelSummaries().single()
        assertEquals(listOf("text", "image", "audio", "video", "pdf"), detail.metadata.inputModalities)
        assertEquals(detail.metadata.inputModalities, summary.inputModalities)
        assertEquals(listOf("text"), summary.outputModalities)
        assertEquals(29, model.inputModalities.size)
    }

    @Test fun mapsCanonicalPathsCapabilitiesAndChineseWithoutOverwritingOriginals() = runTest {
        val snapshot = CatalogMapper().mapAtlas(atlasRelease(), emptyMap(), 100, 200)
        val model = snapshot.models.single()
        assertEquals("lab/model", model.id)
        assertEquals(model.id, model.remoteId)
        assertEquals("model-atlas-data", model.source)
        assertEquals("Original", model.description)
        assertEquals("中文模型说明", model.descriptionZhCn)
        assertEquals(false, model.reasoning)
        assertEquals(true, model.toolCall)
        assertNull(model.attachment)
        assertNull(model.structuredOutput)
        assertEquals(100L, model.fetchedAtEpochMillis)
        val definition = snapshot.definitions!!.single()
        val score = snapshot.scores!!.single()
        assertEquals("bench-v1", score.benchmarkId)
        assertEquals("1.0", score.version)
        assertEquals("no tools", score.variant)
        assertEquals("Adaptive/Max", score.reasoningEffort)
        assertNull(score.toolsEnabled)
        assertNull(score.sourceType)
        val record = ModelDetailRecord(model, emptyList(), listOf(ModelBenchmarkRecord(score, definition)))
        val detail = record.toModelDetail()!!
        assertEquals("中文模型说明", detail.metadata.description)
        assertEquals("中文评测说明", detail.benchmarks!!.single().description)
        val fallback = record.copy(model = model.copy(descriptionZhCn = " "), scores = listOf(
            ModelBenchmarkRecord(score, definition.copy(descriptionZhCn = null)),
        )).toModelDetail()!!
        assertEquals("Original", fallback.metadata.description)
        assertEquals("Benchmark original", fallback.benchmarks!!.single().description)
    }

    @Test fun missingVersionUnknownMetricAndAmbiguousDefinitionsDoNotBorrowMetadata() = runTest {
        val base = atlasRelease()
        val original = base.models.getValue("lab/model")
        val score = original.benchmarks!!.single()
        val b = base.benchmarks.values.single()
        val release = base.copy(
            models = mapOf("lab/model" to original.copy(benchmarks = listOf(
                score.copy(version = null), score.copy(metric = "other"), score,
            ))),
            benchmarks = base.benchmarks + ("duplicate" to b),
        )
        val snapshot = CatalogMapper().mapAtlas(release, emptyMap(), 1, 1)
        assertEquals(3, snapshot.scores!!.size)
        snapshot.scores.forEach {
            assertEquals("Bench", it.name)
            assertTrue(it.benchmarkId.startsWith("unmapped:"))
            assertNull(it.unit)
            assertNull(it.higherIsBetter)
        }
    }

    @Test fun preservesEveryEffortAndVariantInSourceOrderWithoutSelectingHighestScore() = runTest {
        val base = atlasRelease()
        val m = base.models.values.single()
        val first = m.benchmarks!!.single()
        val records = listOf(first, first.copy(effort = "high", score = JsonPrimitive(1.0)), first.copy(effort = null, variant = null, score = JsonPrimitive(0.0)))
        val snapshot = CatalogMapper().mapAtlas(base.copy(models = mapOf("lab/model" to m.copy(benchmarks = records))), emptyMap(), 1, 1)
        assertEquals(listOf(42.125, 1.0, 0.0), snapshot.scores!!.sortedBy { it.id }.map { it.score })
        assertEquals(listOf("Adaptive/Max", "high", null), snapshot.scores.map { it.reasoningEffort })
        assertEquals(3, snapshot.scores.map { it.id }.toSet().size)
        assertFalse(snapshot.hasSameContentAs(snapshot.copy(scores = snapshot.scores.dropLast(1))))
        assertFalse(snapshot.hasSameContentAs(snapshot.copy(definitions = emptyList())))
        assertTrue(snapshot.hasSameContentAs(snapshot.copy(scores = snapshot.scores.reversed())))
    }

    @Test fun emptyPublicationIsAnExplicitReplacementNotMissingData() = runTest {
        val release = atlasRelease(atlasFiles(models = "{}", definitions = "{}", translations = "{}"))
        val snapshot = CatalogMapper().mapAtlas(release, emptyMap(), 1, 1)
        assertTrue(snapshot.isEmpty)
        assertEquals(emptyList<Any>(), snapshot.scores)
        assertNotNull(snapshot.release)
    }

    @Test fun compositeScoresAreStoredAndDisplayedVerbatimWithoutANumericPlaceholder() = runTest {
        val base = atlasRelease()
        val m = base.models.values.single()
        val snapshot = CatalogMapper().mapAtlas(base.copy(models = mapOf("lab/model" to m.copy(
            benchmarks = listOf(m.benchmarks!!.single().copy(score = JsonPrimitive("899 ± 18"))),
        ))), emptyMap(), 1, 1)
        val s = snapshot.scores!!.single()
        assertNull(s.score)
        assertEquals("899 ± 18", s.scoreText)
        val detail = ModelDetailRecord(snapshot.models.single(), emptyList(), listOf(ModelBenchmarkRecord(s, null))).toModelDetail()!!
        assertNull(detail.benchmarks!!.single().score)
        assertEquals("899 ± 18", detail.benchmarks.single().scoreText)
    }
}
