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
                assertEquals(source.version, board.version)
                assertEquals(source.score, entry.score)
                assertEquals(source.scoreText, entry.scoreText)
            }
        }
        assertEquals(0, requests)
        assertTrue(snapshot.hasSameContentAs(dao.readCatalog()))
    }
}
