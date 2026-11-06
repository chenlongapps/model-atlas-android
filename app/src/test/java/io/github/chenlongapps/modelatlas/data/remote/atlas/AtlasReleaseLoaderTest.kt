package io.github.chenlongapps.modelatlas.data.remote.atlas

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.*
import org.junit.Test

class AtlasReleaseLoaderTest {
    @Test fun actualPublishedBytesParseWithPythonManifestAndNoInnerModelIds() = runTest {
        val root = File("src/main/assets/atlas")
        val release = AtlasReleaseLoader { path, limit -> root.resolve(path).readBytes().also { assertTrue(it.size <= limit) } }.load()
        assertEquals("27479e48258332fe988c2d06233c86d96bef4c83", release.manifest.gitCommit)
        assertEquals(855, release.models.size)
        assertEquals(11, release.benchmarks.size)
        assertEquals(1887, release.models.values.sumOf { it.benchmarks.orEmpty().size })
        assertEquals(31, release.models.values.sumOf { m -> m.benchmarks.orEmpty().count { it.score.isString } })
        assertEquals(852, release.translations.model.descriptions.size)
        assertTrue(release.models.values.all { it.schema == 2 })
    }

    @Test fun nestedCapabilitiesLongLimitsEffortAndBothTranslationShapesAreRetained() = runTest {
        val release = atlasRelease()
        val model = release.models.getValue("lab/model")
        assertEquals(false, model.capabilities!!.reasoning!!.supported)
        assertEquals(true, model.capabilities.tools!!.supported)
        assertEquals(false, model.capabilities.temperature)
        assertEquals(5_000_000_000L, model.limit!!.context)
        assertEquals("Adaptive/Max", model.benchmarks!!.single().effort)
        assertEquals("中文模型说明", release.translations.model.descriptions["lab/model"])
        assertEquals("中文评测说明", release.translations.benchmark.description["bench-v1"])
    }

    @Test fun rejectsByteTamperingSizeChangesAndWrongReleaseHash() = runTest {
        val original = atlasFiles()
        val header = original.getValue("manifest.json").decodeToString()
        val variants = listOf(
            original + ("models.json" to (original.getValue("models.json") + 32.toByte())),
            original + ("models.json" to original.getValue("models.json").copyOf().also { it[0] = 32 }),
            original + ("manifest.json" to header.replace("sha256:", "sha255:").toByteArray()),
            original + ("manifest.json" to header.replace("\"schema_version\":1", "\"schema_version\":2").toByteArray()),
        )
        variants.forEach { files -> rejected { atlasRelease(files) } }
    }

    @Test fun rejectsWrongSchemaKnownTypesUnknownTranslationIdsAndNonFiniteScores() = runTest {
        val valid = atlasFiles().getValue("models.json").decodeToString()
        listOf(
            valid.replace("\"schema\":2", "\"schema\":1"),
            valid.replace("\"schema\":2,", ""),
            valid.replace("\"schema\":2", "\"schema\":\"2\""),
            valid.replace("\"supported\":false", "\"supported\":\"false\""),
            valid.replace("42.125", "1e999"),
            valid.replace("42.125", "null"),
            valid.replace("42.125", "true"),
            "[]",
        ).forEach { models -> rejected { atlasRelease(atlasFiles(models = models)) } }
        rejected { atlasRelease(atlasFiles(translations = """{"model":{"descriptions":{"missing/id":"wrong"}}}""")) }
        rejected { atlasRelease(atlasFiles(translations = """{"benchmark":{"description":[]}}""")) }
    }

    @Test fun missingOptionalFieldsAndExplicitEmptyArraysStayDistinct() = runTest {
        val release = atlasRelease(atlasFiles(
            models = """{"lab/model":{"schema":2,"name":"M","modalities":{"input":[],"output":["text"]},"future":true}}""",
            definitions = "{}", translations = "{}",
        ))
        val model = release.models.values.single()
        assertNull(model.capabilities)
        assertNull(model.benchmarks)
        assertEquals(emptyList<String>(), model.modalities!!.input)
        assertEquals(listOf("text"), model.modalities.output)
        assertTrue(release.translations.model.descriptions.isEmpty())
    }

    @Test fun cancellationDoesNotBecomeAnInvalidDataError() = runTest {
        try {
            AtlasReleaseLoader { _, _ -> throw CancellationException("cancel") }.load()
            fail()
        } catch (expected: CancellationException) { assertEquals("cancel", expected.message) }
    }

    @Test fun oversizedManifestAndInvalidUtf8AreRejected() = runTest {
        rejected { AtlasReleaseLoader { _, _ -> ByteArray(ATLAS_MANIFEST_LIMIT + 1) }.load() }
        rejected { AtlasReleaseLoader { _, _ -> byteArrayOf(0xc3.toByte(), 0x28) }.load() }
    }

    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("Expected invalid release") } catch (_: SerializationException) { }
    }
}
