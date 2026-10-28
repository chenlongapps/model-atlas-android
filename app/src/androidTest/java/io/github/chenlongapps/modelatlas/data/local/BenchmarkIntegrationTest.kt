package io.github.chenlongapps.modelatlas.data.local

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.chenlongapps.modelatlas.data.benchmark.BenchmarkJson
import io.github.chenlongapps.modelatlas.data.benchmark.BenchmarkHttpSource
import io.github.chenlongapps.modelatlas.data.benchmark.BenchmarkManifest
import io.github.chenlongapps.modelatlas.data.benchmark.BenchmarkSyncRunner
import io.github.chenlongapps.modelatlas.data.benchmark.RoomBenchmarkSnapshotStore
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkScoreEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ModelEntity
import io.github.chenlongapps.modelatlas.data.mapper.CatalogMapper
import io.github.chenlongapps.modelatlas.data.repository.DefaultModelRepository
import io.github.chenlongapps.modelatlas.di.DataModule
import java.time.Clock
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BenchmarkIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun database() = Room.inMemoryDatabaseBuilder<CatalogDatabase>(context).setDriver(AndroidSQLiteDriver()).build()
    private fun bundled() = context.assets.open("benchmarks.json").use { it.readBytes() }

    @Test fun bundledImportAndSubsequentLocalUpdateReachDetail() = runBlocking {
        val db = database()
        try {
            val id = "deepseek/deepseek-r1"
            db.catalogDao().replaceCatalog(CatalogSnapshot(
                listOf(ModelEntity(id, id, "Example", 0, type = "llm")), emptyList(), emptyList()))
            val client = OkHttpClient()
            val repository = DefaultModelRepository(DataModule.api(client), RoomCatalogStore(db.catalogDao(), db.benchmarkScoreDao()),
                CatalogMapper(), Clock.systemUTC(), Dispatchers.IO)
            assertTrue(repository.observeModelDetail(id).first()!!.benchmarks!!.isEmpty())
            val dao = db.benchmarkScoreDao()
            val runner = BenchmarkSyncRunner(RoomBenchmarkSnapshotStore(dao), ::bundled) { _, _ -> throw IOException("offline") }
            try { runner.sync("https://example.org/manifest.json"); fail() } catch (_: IOException) { }
            val seeded = repository.observeModelDetail(id).first()!!.benchmarks!!
            assertEquals(2, seeded.size)
            assertEquals(2L, dao.version())
            assertTrue(seeded.all { it.sourceType == "model_developer" })
            assertEquals(setOf(84.0, 97.3), seeded.map { it.score }.toSet())
            // A second offline open does not import over local data or require a download.
            val reopened = BenchmarkSyncRunner(RoomBenchmarkSnapshotStore(dao), { error("must not reimport") }) { _, _ -> throw IOException("offline") }
            try { reopened.sync("https://example.org/manifest.json"); fail() } catch (_: IOException) { }
            assertEquals(seeded, repository.observeModelDetail(id).first()!!.benchmarks)
            val observed = async { withTimeout(5_000) { repository.observeModelDetail(id).first { it?.benchmarks?.singleOrNull()?.score == 4.0 } } }
            dao.replaceIfNewer(3, listOf(BenchmarkScoreEntity("updated", id, "bench", 4.0)))
            assertEquals("bench", observed.await()!!.benchmarks!!.single().name)
        } finally { db.close() }
    }

    @Test fun actualPythonManifestAndSnapshotUpdateOlderRoomSeedWithoutTouchingFavoritesOrCatalog() = runBlocking {
        val db = database()
        try {
            val id = "deepseek/deepseek-r1"
            db.catalogDao().replaceCatalog(CatalogSnapshot(listOf(ModelEntity(id, id, "R1", 0, type = "llm")), emptyList(), emptyList()))
            db.favoriteDao().add(id, 123L)
            db.benchmarkScoreDao().initialize(1, listOf(BenchmarkScoreEntity("old-test", id, "test", 0.0)))
            val manifest = context.assets.open("benchmark-manifest.json").use { it.readBytes() }
            val scores = bundled()
            val runner = BenchmarkSyncRunner(RoomBenchmarkSnapshotStore(db.benchmarkScoreDao()), { error("existing version") }) { url, _ ->
                if (url.endsWith("manifest.json")) manifest else scores
            }
            assertTrue(runner.sync("https://example.org/manifest.json"))
            assertEquals(2L, db.benchmarkScoreDao().version())
            assertEquals(setOf(84.0, 97.3), db.benchmarkScoreDao().observeForModel(id).first().map { it.score }.toSet())
            assertNotNull(db.catalogDao().observeModelDetail(id).first())
            assertEquals(123L, db.favoriteDao().observeFavorite(id).first()!!.favoritedAtEpochMillis)
            assertFalse(runner.sync("https://example.org/manifest.json"))
            db.catalogDao().replaceCatalog(CatalogSnapshot(emptyList(), emptyList(), emptyList()))
            assertEquals(2, db.benchmarkScoreDao().observeForModel(id).first().size)
            assertNotNull(db.favoriteDao().observeFavorite(id).first())
        } finally { db.close() }
    }

    @Test fun failedSqlTransactionRollsBackScoresAndVersionAndRejectsDowngrade() = runBlocking {
        val db = database()
        try {
            val dao = db.benchmarkScoreDao()
            val snapshot = BenchmarkJson.decodeFromString<io.github.chenlongapps.modelatlas.data.benchmark.BenchmarkSnapshot>(bundled().decodeToString())
            val scores = snapshot.validated()
            dao.initialize(snapshot.version, scores)
            val duplicate = scores.first()
            try { dao.replaceIfNewer(3, listOf(duplicate, duplicate)); fail("duplicate insert should fail") } catch (_: Exception) { }
            assertEquals(2L, dao.version())
            assertEquals(scores.filter { it.modelId == duplicate.modelId }, dao.observeForModel(duplicate.modelId).first())
            assertFalse(dao.replaceIfNewer(1, emptyList()))
            assertFalse(dao.replaceIfNewer(2, emptyList()))
            assertEquals(2L, dao.version())
        } finally { db.close() }
    }

    @Test fun invalidHashAndIncompatibleSchemaNeverChangeRoom() = runBlocking {
        val db = database()
        try {
            val dao = db.benchmarkScoreDao()
            val seed = BenchmarkJson.decodeFromString<io.github.chenlongapps.modelatlas.data.benchmark.BenchmarkSnapshot>(bundled().decodeToString())
            dao.initialize(seed.version, seed.validated())
            val invalid = """{"schemaVersion":99,"version":3,"scores":[]}""".toByteArray()
            val hash = MessageDigest.getInstance("SHA-256").digest(invalid).joinToString("") { "%02x".format(it) }
            for (digest in listOf("0".repeat(64), hash)) {
                val header = """{"schemaVersion":1,"version":3,"url":"https://example.org/scores.json","sha256":"$digest"}""".toByteArray()
                val runner = BenchmarkSyncRunner(RoomBenchmarkSnapshotStore(dao), { error("must not reimport") }) { url, _ ->
                    if (url.endsWith("manifest.json")) header else invalid
                }
                try { runner.sync("https://example.org/manifest.json"); fail() } catch (_: IllegalArgumentException) { }
                assertEquals(2L, dao.version())
                assertEquals(2, dao.observeForModel("deepseek/deepseek-r1").first().size)
            }
        } finally { db.close() }
    }

    @Test fun reopeningFileDatabaseReadsCommittedScoresAndVersionWithoutImportOrNetwork() = runBlocking {
        val name = "benchmark-integration-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder<CatalogDatabase>(context, name).setDriver(AndroidSQLiteDriver()).build()
        try {
            val first = open()
            try {
                val runner = BenchmarkSyncRunner(RoomBenchmarkSnapshotStore(first.benchmarkScoreDao()), ::bundled) { _, _ -> error("offline import") }
                assertFalse(runner.sync(""))
            } finally { first.close() }
            val reopened = open()
            try {
                val dao = reopened.benchmarkScoreDao()
                assertEquals(2L, dao.version())
                assertEquals(setOf(84.0, 97.3), dao.observeForModel("deepseek/deepseek-r1").first().map { it.score }.toSet())
                val runner = BenchmarkSyncRunner(RoomBenchmarkSnapshotStore(dao), { error("must not reimport") }) { _, _ -> error("network disabled") }
                assertFalse(runner.sync(""))
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun pythonGeneratedV3TravelsThroughHttpsSourceAndReplacesBundledV2InObservedDetail() = runBlocking {
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val header = testAssets.open("benchmark-contract-v3/benchmark-manifest.json").use { it.readBytes() }
        val bytes = testAssets.open("benchmark-contract-v3/benchmarks.json").use { it.readBytes() }
        val manifest = BenchmarkJson.decodeFromString<BenchmarkManifest>(header.decodeToString())
        val manifestUrl = context.getString(io.github.chenlongapps.modelatlas.R.string.benchmark_manifest_url)
        var snapshotDownloads = 0
        // Real production HTTPS parsing, status and size checks; only HTTP responses are injected.
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val body = when (chain.request().url.toString()) {
                manifestUrl -> header
                manifest.url -> { snapshotDownloads++; bytes }
                else -> throw IOException("unexpected URL")
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
        val db = database()
        try {
            val id = "deepseek/deepseek-r1"
            val dao = db.benchmarkScoreDao()
            val runner = BenchmarkSyncRunner(RoomBenchmarkSnapshotStore(dao), ::bundled, BenchmarkHttpSource(client)::download)
            assertFalse(runner.sync(""))
            assertEquals(2L, dao.version())
            db.catalogDao().replaceCatalog(CatalogSnapshot(listOf(ModelEntity(id, id, "R1", 0, type = "llm")), emptyList(), emptyList()))
            dao.upsert(BenchmarkScoreEntity("local-manual-test", id, "synthetic", 0.0, sourceType = "test"))
            val repository = DefaultModelRepository(DataModule.api(client), RoomCatalogStore(db.catalogDao(), dao), CatalogMapper(), Clock.systemUTC(), Dispatchers.IO)
            assertEquals(3, repository.observeModelDetail(id).first()!!.benchmarks!!.size)
            val observed = async {
                withTimeout(5_000) { repository.observeModelDetail(id).first { it?.benchmarks?.size == 2 } }
            }
            assertTrue(runner.sync(manifestUrl))
            assertEquals(3L, dao.version())
            assertEquals(setOf(84.0, 97.3), observed.await()!!.benchmarks!!.map { it.score }.toSet())
            assertTrue(dao.observeForModel(id).first().none { it.id == "local-manual-test" })
            assertFalse(runner.sync(manifestUrl))
            assertEquals(1, snapshotDownloads)
        } finally { db.close() }
    }
}
