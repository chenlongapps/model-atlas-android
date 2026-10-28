package io.github.chenlongapps.modelatlas.data.benchmark

import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BenchmarkUpdateTest {
    private val snapshot = """{"schemaVersion":1,"version":2,"scores":[{"id":"a","modelId":"lab/model","benchmarkId":"test","score":0,"sourceType":"test","cost":0.003}]}""".toByteArray()
    private fun manifest(bytes: ByteArray = snapshot, version: Long = 2): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return """{"schemaVersion":1,"version":$version,"url":"https://example.org/data.json","sha256":"$hash"}""".toByteArray()
    }

    @Test fun newerSnapshotCommitsValidatedValues() = runBlocking {
        var committed = false
        assertTrue(updateBenchmarkSnapshot(1, manifest(), { snapshot }) { version, scores ->
            assertEquals(2L, version)
            assertEquals(0.0, scores.single().score, 0.0)
            assertEquals("test", scores.single().sourceType)
            assertEquals(0.003, scores.single().cost!!, 0.0)
            committed = true
            true
        })
        assertTrue(committed)
    }

    @Test fun oldOrSameVersionDoesNotDownloadOrCommit() = runBlocking {
        assertFalse(updateBenchmarkSnapshot(2, manifest(), { fail("download"); snapshot }) { _, _ -> fail("commit"); true })
    }

    @Test fun invalidHashVersionDuplicatesAndNetworkFailureNeverCommit() = runBlocking {
        val duplicate = """{"schemaVersion":1,"version":2,"scores":[{"id":"a","modelId":"x","benchmarkId":"b","score":1},{"id":"a","modelId":"x","benchmarkId":"b","score":2}]}""".toByteArray()
        val cases = listOf(
            manifest() to { "bad".toByteArray() },
            manifest(snapshot, 3) to { snapshot },
            manifest(duplicate) to { duplicate },
            manifest() to { throw java.io.IOException("offline") },
        )
        cases.forEach { (header, fetch) ->
            var commits = 0
            try {
                updateBenchmarkSnapshot(1, header, { fetch() }) { _, _ -> commits++; true }
                fail("expected failure")
            } catch (_: Exception) {
                assertEquals(0, commits)
            }
        }
    }

    @Test fun emptySnapshotIsValidAndInvalidRequiredValuesAreRejected() {
        assertTrue(BenchmarkJson.decodeFromString<BenchmarkSnapshot>("""{"schemaVersion":1,"version":1,"scores":[]}""").validated().isEmpty())
        val invalid = BenchmarkSnapshot(1, 1, listOf(BenchmarkScore("", "m", "b", 1.0)))
        try { invalid.validated(); fail("expected failure") } catch (_: IllegalArgumentException) { }
    }

    @Test fun costPreservesMissingNullZeroAndRejectsNonFiniteValues() {
        val prefix = """{"schemaVersion":1,"version":1,"scores":[{"id":"x","modelId":"m","benchmarkId":"b","score":1"""
        fun parse(suffix: String) = BenchmarkJson.decodeFromString<BenchmarkSnapshot>(prefix + suffix + "}]}").validated().single().cost
        assertEquals(null, parse(""))
        assertEquals(null, parse(",\"cost\":null"))
        assertEquals(0.0, parse(",\"cost\":0")!!, 0.0)
        try {
            BenchmarkSnapshot(1, 1, listOf(BenchmarkScore("x", "m", "b", 1.0, cost = Double.POSITIVE_INFINITY))).validated()
            fail("expected failure")
        } catch (_: IllegalArgumentException) { }
    }
}
