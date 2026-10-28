package io.github.chenlongapps.modelatlas.data.benchmark

import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkScoreEntity
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BenchmarkSyncRunnerTest {
    private val manifestUrl = "https://example.org/manifest.json"
    private val seed = snapshot(1, "seed", 1.0)
    private val next = snapshot(2, "new", 0.0)

    private fun snapshot(version: Int, id: String, score: Double) =
        """{"schemaVersion":1,"version":$version,"scores":[{"id":"$id","modelId":"lab/model","benchmarkId":"synthetic","score":$score,"sourceType":"test"}]}""".toByteArray()

    private fun manifest(bytes: ByteArray, version: Int = 2): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return """{"schemaVersion":1,"version":$version,"url":"https://example.org/scores.json","sha256":"$hash"}""".toByteArray()
    }

    private class Store : BenchmarkSnapshotStore {
        var current: Long? = null
        var scores = emptyList<BenchmarkScoreEntity>()
        var initializations = 0
        var commits = 0
        var failCommit = false
        override suspend fun version() = current
        override suspend fun initialize(version: Long, scores: List<BenchmarkScoreEntity>) {
            initializations++
            if (current == null) { this.scores = scores; current = version }
        }
        override suspend fun replaceIfNewer(version: Long, scores: List<BenchmarkScoreEntity>): Boolean {
            if (version <= requireNotNull(current)) return false
            if (failCommit) throw IOException("transaction failed")
            commits++
            this.scores = scores
            current = version
            return true
        }
    }

    @Test fun firstInstallImportsBeforeNetworkAndOfflineReopenNeverReseeds() = runTest {
        val store = Store()
        var seeds = 0
        val runner = BenchmarkSyncRunner(store, { seeds++; seed }) { _, _ ->
            assertEquals(1L, store.current)
            assertEquals("seed", store.scores.single().id)
            throw IOException("offline")
        }
        repeat(2) { try { runner.sync(manifestUrl); fail() } catch (_: IOException) { } }
        assertEquals(1, seeds)
        assertEquals(1, store.initializations)
        assertEquals(0, store.commits)
        assertEquals(1L, store.current)
    }

    @Test fun delayedNetworkLeavesSeedReadableAndNewerSnapshotReplacesOnce() = runTest {
        val store = Store()
        val gate = CompletableDeferred<Unit>()
        var snapshotDownloads = 0
        val runner = BenchmarkSyncRunner(store, { seed }) { url, limit ->
            if (url == manifestUrl) {
                assertEquals(MAX_MANIFEST_BYTES, limit)
                gate.await()
                manifest(next)
            } else {
                assertEquals(MAX_SNAPSHOT_BYTES, limit)
                snapshotDownloads++
                next
            }
        }
        val first = async { runner.sync(manifestUrl) }
        val concurrent = async { runner.sync(manifestUrl) }
        runCurrent()
        assertEquals(1L, store.current)
        assertEquals("seed", store.scores.single().id)
        gate.complete(Unit)
        assertTrue(first.await())
        assertFalse(concurrent.await())
        assertEquals(2L, store.current)
        assertEquals("new", store.scores.single().id)
        assertEquals(1, snapshotDownloads)
        assertEquals(1, store.commits)
    }

    @Test fun invalidDownloadsAndCommitFailureKeepPreviousVersionAndScores() = runTest {
        val incompatible = next.decodeToString().replace("\"schemaVersion\":1", "\"schemaVersion\":99").toByteArray()
        val duplicate = """{"schemaVersion":1,"version":2,"scores":[{"id":"x","modelId":"m","benchmarkId":"b","score":1},{"id":"x","modelId":"m","benchmarkId":"b","score":2}]}""".toByteArray()
        val invalidNumber = next.decodeToString().replace("\"score\":0.0", "\"score\":1e9999").toByteArray()
        val cases = listOf(
            manifest(next) to "corrupt".toByteArray(),
            manifest(next, 3) to next,
            manifest(incompatible) to incompatible,
            manifest(duplicate) to duplicate,
            manifest(invalidNumber) to invalidNumber,
            "not json".toByteArray() to next,
        )
        cases.forEach { (header, body) ->
            val store = Store()
            val runner = BenchmarkSyncRunner(store, { seed }) { url, _ -> if (url == manifestUrl) header else body }
            try { runner.sync(manifestUrl); fail("expected rejection") } catch (_: Exception) { }
            assertEquals(1L, store.current)
            assertEquals("seed", store.scores.single().id)
            assertEquals(0, store.commits)
        }
        val store = Store().apply { failCommit = true }
        val runner = BenchmarkSyncRunner(store, { seed }) { url, _ -> if (url == manifestUrl) manifest(next) else next }
        try { runner.sync(manifestUrl); fail() } catch (_: IOException) { }
        assertEquals(1L, store.current)
        assertEquals("seed", store.scores.single().id)
    }

    @Test fun cancelledSyncPropagatesAndLeavesSeed() = runTest {
        val store = Store()
        val gate = CompletableDeferred<Unit>()
        val runner = BenchmarkSyncRunner(store, { seed }) { _, _ -> gate.await(); manifest(next) }
        val sync = async { runner.sync(manifestUrl) }
        runCurrent()
        sync.cancel()
        sync.join()
        assertTrue(sync.isCancelled)
        assertEquals(1L, store.current)
        assertEquals(0, store.commits)
    }

    @Test fun corruptBundleNeverInitializesAndOversizedManifestNeverReplacesSeed() = runTest {
        val emptyStore = Store()
        val corrupt = BenchmarkSyncRunner(emptyStore, { "invalid".toByteArray() }) { _, _ -> error("must not download") }
        try { corrupt.sync(manifestUrl); fail() } catch (_: IllegalArgumentException) { }
        assertNull(emptyStore.current)
        assertEquals(0, emptyStore.initializations)
        val store = Store()
        val oversized = BenchmarkSyncRunner(store, { seed }) { _, _ -> ByteArray(MAX_MANIFEST_BYTES + 1) }
        try { oversized.sync(manifestUrl); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(1L, store.current)
        assertEquals("seed", store.scores.single().id)
        assertEquals(0, store.commits)
    }

    @Test fun pythonGeneratedReleaseParsesWithActualKotlinContractAndUpdates() = runTest {
        val assets = File("src/main/assets")
        val bytes = File(assets, "benchmarks.json").readBytes()
        val header = File(assets, "benchmark-manifest.json").readBytes()
        val snapshot = BenchmarkJson.decodeFromString<BenchmarkSnapshot>(bytes.decodeToString())
        assertEquals(2L, snapshot.version)
        assertEquals(5, snapshot.validated().size)
        assertEquals(setOf("deepseek/deepseek-r1", "deepseek/deepseek-v3", "deepseek/deepseek-r1-distill-qwen-32b"), snapshot.scores.map { it.modelId }.toSet())
        assertTrue(snapshot.scores.all { it.sourceType == "model_developer" && it.evaluatedAt == null && it.cost == null })
        var committed = false
        assertTrue(updateBenchmarkSnapshot(1, header, { bytes }) { version, scores ->
            assertEquals(snapshot.version, version)
            assertEquals(snapshot.validated(), scores)
            committed = true
            true
        })
        assertTrue(committed)
        val attributions = BenchmarkJson.decodeFromString<BenchmarkAttributions>(File(assets, "benchmark-attributions.json").readText())
        assertEquals(snapshot.version, attributions.version)
        attributions.sources.forEach { source ->
            assertTrue(File(assets, source.license.validatedAssetPath()).readText().contains("Copyright (c) 2023 DeepSeek"))
        }
    }
}
