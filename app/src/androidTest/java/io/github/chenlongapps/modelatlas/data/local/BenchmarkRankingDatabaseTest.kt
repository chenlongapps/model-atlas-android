package io.github.chenlongapps.modelatlas.data.local

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkDefinitionEntity
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkScoreEntity
import io.github.chenlongapps.modelatlas.data.local.entity.CatalogReleaseEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ModelEntity
import io.github.chenlongapps.modelatlas.data.mapper.CatalogMapper
import io.github.chenlongapps.modelatlas.data.mapper.mapAtlas
import io.github.chenlongapps.modelatlas.data.model.LlmInclusionPolicy
import io.github.chenlongapps.modelatlas.data.remote.atlas.HttpAtlasDataSource
import io.github.chenlongapps.modelatlas.data.repository.RoomBenchmarkRepository
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class BenchmarkRankingDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder<CatalogDatabase>(context).setDriver(AndroidSQLiteDriver()).build()
    private val dao = db.catalogDao()
    private val repo = RoomBenchmarkRepository(dao, Dispatchers.IO)
    private val definition = BenchmarkDefinitionEntity("test", "Benchmark", "1", "accuracy", unit = "percent", higherIsBetter = true)
    private fun snapshot(version: String, value: Double) = CatalogSnapshot(
        models = listOf(ModelEntity("lab/a", "a", "Model $version", 1, type = "llm", inputModalities = listOf("text"))),
        providers = emptyList(), offerings = emptyList(), definitions = listOf(definition.copy(descriptionZhCn = "说明 $version")),
        scores = listOf(BenchmarkScoreEntity("score", "lab/a", "test", value, metric = "accuracy", unit = "percent",
            higherIsBetter = true, version = "1")),
        release = CatalogReleaseEntity(dataVersion = version, gitCommit = version, publishedAt = version),
    )
    @After fun close() = db.close()

    @Test fun nestedTransactionReadsLatestIdentityScoresAndDefinitionsTogether() = runBlocking {
        dao.replaceCatalog(snapshot("first", 10.0))
        val first = repo.observeBoards().first().single()
        assertEquals("Model first", first.rankings.single().modelName)
        assertEquals("说明 first", first.description)
        assertEquals(10.0, first.rankings.single().score!!, 0.0)
        val next = async {
            withTimeout(10_000) { repo.observeBoards().first { it.single().rankings.single().score == 80.0 }.single() }
        }
        dao.replaceCatalog(snapshot("second", 80.0))
        val second = next.await()
        assertEquals("Model second", second.rankings.single().modelName)
        assertEquals("说明 second", second.description)
        assertEquals(listOf("text"), dao.observeBenchmarkRankings().first().single().scores.single().model!!.inputModalities)
    }

    @Test fun modelRemovalClearsRankingWithoutReusingHistoricalIdentity() = runBlocking {
        val initial = snapshot("first", 10.0)
        dao.replaceCatalog(initial)
        assertEquals(1, repo.observeBoards().first().single().rankings.size)
        dao.replaceCatalog(initial.copy(models = emptyList()))
        val board = repo.observeBoards().first().single()
        assertTrue(board.rankings.isEmpty())
    }

    @Test fun popularityUpdatesReorderBoardsAndMissingValuesClearPreviousPopularity() = runBlocking {
        val initial = snapshot("first", 10.0).let { s -> s.copy(
            definitions = listOf(definition.copy(name = "Zulu", popularity = 0.98),
                definition.copy(id = "other", name = "Alpha", popularity = 0.6)),
            scores = s.scores!! + s.scores.single().copy(id = "other-score", benchmarkId = "other"),
        ) }
        dao.replaceCatalog(initial)
        val first = repo.observeBoards().first()
        assertEquals(listOf("test", "other"), first.map { it.benchmarkId })
        assertTrue(initial.hasSameContentAs(dao.readCatalog()))
        val updated = initial.copy(definitions = initial.definitions!!.map {
            it.copy(popularity = if (it.id == "other") 1.0 else it.popularity)
        })
        assertFalse(initial.hasSameContentAs(updated))
        val next = async { withTimeout(10_000) { repo.observeBoards().first { it.first().benchmarkId == "other" } } }
        dao.replaceCatalog(updated)
        val second = next.await()
        assertEquals(listOf("other", "test"), second.map { it.benchmarkId })
        assertEquals(first.associate { it.id to it.rankings }, second.associate { it.id to it.rankings })
        dao.replaceCatalog(initial.copy(definitions = initial.definitions.map { it.copy(popularity = null) }))
        assertEquals(listOf("other", "test"), repo.observeBoards().first().map { it.benchmarkId })
        assertTrue(dao.readCatalog().definitions!!.all { it.popularity == null })
    }

    @Test fun fileReopenRetainsPopularityAndBoardOrderWithoutNetwork() = runBlocking {
        val name = "benchmark-popularity-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder<CatalogDatabase>(context, name).setDriver(AndroidSQLiteDriver()).build()
        val initial = snapshot("first", 10.0).copy(definitions = listOf(
            definition.copy(name = "Zulu", popularity = 0.98), definition.copy(id = "other", name = "Alpha", popularity = 0.0),
        ))
        try {
            open().let { fileDb -> try { fileDb.catalogDao().replaceCatalog(initial) } finally { fileDb.close() } }
            open().let { fileDb -> try {
                assertTrue(initial.hasSameContentAs(fileDb.catalogDao().readCatalog()))
                val boards = RoomBenchmarkRepository(fileDb.catalogDao(), Dispatchers.IO).observeBoards().first()
                assertEquals(listOf("test", "other"), boards.map { it.benchmarkId })
                assertEquals(listOf(0.98, 0.0), boards.map { it.popularity })
            } finally { fileDb.close() } }
        } finally { context.deleteDatabase(name) }
    }

    private fun oldUnspecifiedSnapshot(): CatalogSnapshot {
        val initial = snapshot("cached", 10.0)
        val original = initial.scores!!.single().copy(id = "atlas:lab/a:000000", benchmarkId = "unmapped:old",
            name = definition.name, version = null, unit = null, higherIsBetter = null,
            variant = "no tools", harness = "runner", subset = "original subset", reasoningEffort = "high",
            sourceUrl = "https://example.org/result")
        return initial.copy(
            definitions = listOf(definition.copy(version = "unspecified"),
                definition.copy(id = "strict", name = "Versioned"),
                definition.copy(id = "ambiguous-a", name = "Ambiguous", version = "unspecified"),
                definition.copy(id = "ambiguous-b", name = "Ambiguous", version = "unspecified")),
            scores = listOf(original,
                original.copy(id = "atlas:lab/a:000001", version = "2", score = 30.0),
                original.copy(id = "atlas:lab/a:000002", version = "unspecified", score = 20.0),
                original.copy(id = "manual"),
                original.copy(id = "atlas:lab/a:000003", metric = "other"),
                original.copy(id = "atlas:lab/a:000004", name = "Versioned"),
                original.copy(id = "atlas:lab/a:000005", name = "Ambiguous")),
        )
    }

    @Test fun boardObservationRepairsOldCacheOfflineAndFileReopenRetainsRawVersions() = runBlocking {
        val name = "benchmark-unspecified-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder<CatalogDatabase>(context, name).setDriver(AndroidSQLiteDriver()).build()
        val initial = oldUnspecifiedSnapshot()
        try {
            open().let { fileDb -> try {
                fileDb.catalogDao().replaceCatalog(initial)
                fileDb.favoriteDao().add("lab/a", 123)
            } finally { fileDb.close() } }
            open().let { fileDb -> try {
                val fileDao = fileDb.catalogDao()
                assertTrue(initial.hasSameContentAs(fileDao.readCatalog()))
                val board = RoomBenchmarkRepository(fileDao, Dispatchers.IO).observeBoards().first().single { it.benchmarkId == "test" }
                assertEquals("unspecified", board.version)
                assertEquals(30.0, board.rankings.single().score!!, 0.0)
                val after = fileDao.readCatalog()
                assertEquals(initial.release, after.release)
                assertEquals(initial.models, after.models)
                val beforeById = initial.scores!!.associateBy { it.id }
                after.scores!!.forEach { score ->
                    val before = beforeById.getValue(score.id)
                    if (score.id in listOf("atlas:lab/a:000000", "atlas:lab/a:000001", "atlas:lab/a:000002")) {
                        assertEquals(before.copy(benchmarkId = "test", unit = "percent", higherIsBetter = true), score)
                    } else if (before.name == "Ambiguous") {
                        assertTrue(score.benchmarkId.startsWith("unmapped:"))
                        assertEquals(before.copy(benchmarkId = score.benchmarkId), score)
                    } else assertEquals(before, score)
                }
                fileDao.reconcileUnspecifiedBenchmarkScores()
                assertEquals(after, fileDao.readCatalog())
                assertEquals(123L, fileDb.favoriteDao().observeFavorite("lab/a").first()!!.favoritedAtEpochMillis)
            } finally { fileDb.close() } }
            open().let { fileDb -> try {
                val scores = fileDb.catalogDao().readCatalog().scores!!.associateBy { it.id }
                assertNull(scores.getValue("atlas:lab/a:000000").version)
                assertEquals("2", scores.getValue("atlas:lab/a:000001").version)
                assertEquals("test", scores.getValue("atlas:lab/a:000001").benchmarkId)
            } finally { fileDb.close() } }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun detailObservationRepairsOldCacheWithoutOpeningBenchmarkPage() = runBlocking {
        dao.replaceCatalog(oldUnspecifiedSnapshot())
        val detail = RoomCatalogStore(dao).observeModelDetail("lab/a").first()!!
        val scores = detail.scores.associateBy { it.score.id }
        val missingVersion = scores.getValue("atlas:lab/a:000000")
        assertEquals("test", missingVersion.definition!!.id)
        assertNull(missingVersion.score.version)
        assertEquals("2", scores.getValue("atlas:lab/a:000001").score.version)
        assertNull(scores.getValue("manual").definition)
    }

    @Test fun actualBundledReleaseProducesOnlyMatchedLlmBoards() = runBlocking {
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { requests++; error("Must remain offline") }.build()
        val bundle = HttpAtlasDataSource(context, client, Dispatchers.IO).bundled()
        val snapshot = CatalogMapper().mapAtlas(bundle, emptyMap(), 1, 1)
        dao.replaceCatalog(snapshot)
        val boards = repo.observeBoards().first()
        assertEquals(11, boards.map { it.benchmarkId }.toSet().size)
        val models = snapshot.models.filter { LlmInclusionPolicy.includes(it.type, it.inputModalities, it.outputModalities) }.associateBy { it.id }
        val scores = snapshot.scores!!.associateBy { it.id }
        assertTrue(boards.sumOf { it.rankings.size } > 0)
        boards.forEach { board ->
            assertFalse(board.benchmarkId.startsWith("unmapped:"))
            assertEquals(board.rankings.size, board.rankings.map { it.modelId }.toSet().size)
            assertTrue(board.rankings.zipWithNext().all { (a, b) -> a.score!! >= b.score!! })
            board.rankings.forEach { entry ->
                assertTrue(entry.modelId in models)
                val source = scores.getValue(entry.scoreId)
                if (board.version != "unspecified") assertEquals(source.version, board.version)
                assertEquals(source.score, entry.score)
                assertEquals(source.scoreText, entry.scoreText)
            }
        }
        assertEquals(84, boards.single { it.benchmarkId == "humanitys-last-exam" }.rankings.size)
        assertEquals(0, requests)
        assertTrue(snapshot.hasSameContentAs(dao.readCatalog()))
    }
}
