package io.github.chenlongapps.modelatlas.data.local

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkScoreEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ModelEntity
import io.github.chenlongapps.modelatlas.data.mapper.CatalogMapper
import io.github.chenlongapps.modelatlas.data.mapper.mapAtlas
import io.github.chenlongapps.modelatlas.data.mapper.toModelDetail
import io.github.chenlongapps.modelatlas.data.remote.atlas.ATLAS_BASE_URL
import io.github.chenlongapps.modelatlas.data.remote.atlas.HttpAtlasDataSource
import io.github.chenlongapps.modelatlas.data.repository.DefaultModelRepository
import io.github.chenlongapps.modelatlas.data.repository.RoomFavoriteRepository
import io.github.chenlongapps.modelatlas.di.DataModule
import java.io.IOException
import java.time.Clock
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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AtlasCatalogIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val modelId = "deepseek/deepseek-r1"
    private fun database() = Room.inMemoryDatabaseBuilder<CatalogDatabase>(context).setDriver(AndroidSQLiteDriver()).build()
    private suspend fun bundled() = HttpAtlasDataSource(context, OkHttpClient(), Dispatchers.IO).bundled()
    private fun repository(db: CatalogDatabase, client: OkHttpClient) = DefaultModelRepository(
        DataModule.api(client), RoomCatalogStore(db.catalogDao(), db.benchmarkScoreDao()),
        CatalogMapper(), Clock.systemUTC(), Dispatchers.IO, HttpAtlasDataSource(context, client, Dispatchers.IO),
    )

    @Test fun cachedMimeFormatsProjectConsistentlyWithoutNetworkOrDatabaseRewrite() = runBlocking {
        val db = database()
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { requests++; throw IOException("network must not be used") }.build()
        try {
            val snapshot = CatalogMapper().mapAtlas(bundled(), emptyMap(), 1, 1)
            db.catalogDao().replaceCatalog(snapshot)
            val id = "google/gemini-2.0-flash"
            db.favoriteDao().add(id, 123)
            val before = db.catalogDao().readCatalog()
            val repo = repository(db, client)
            val summary = repo.observeModels().first().first { it.id == id }
            val detail = repo.observeModelDetail(id).first()!!
            val favorite = RoomFavoriteRepository(db.favoriteDao(), Clock.systemUTC(), Dispatchers.IO)
                .observeFavorites().first().single().model!!
            assertEquals(listOf("text", "image", "audio", "video", "pdf"), summary.inputModalities)
            assertEquals(summary.inputModalities, detail.metadata.inputModalities)
            assertEquals(summary.inputModalities, favorite.inputModalities)
            assertEquals(listOf("text"), summary.outputModalities)
            assertEquals(29, before.models.first { it.id == id }.inputModalities!!.size)
            assertEquals(before, db.catalogDao().readCatalog())
            assertEquals(0, requests)
        } finally { db.close() }
    }

    @Test fun offlineBundleInitializesModelsDefinitionsScoresAndChineseOnce() = runBlocking {
        val db = database()
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { requests++; throw IOException("offline") }.build()
        try {
            val repo = repository(db, client)
            assertTrue(repo.hasLocalCatalog())
            val stored = db.catalogDao().readCatalog()
            assertEquals(852, stored.models.size)
            assertEquals(11, stored.definitions!!.size)
            assertEquals(1837, stored.scores!!.size)
            assertEquals(42, stored.scores.count { it.score == null && it.scoreText != null })
            assertEquals(0, requests)
            val detail = repo.observeModelDetail(modelId).first()!!
            assertEquals(stored.models.first { it.id == modelId }.descriptionZhCn, detail.metadata.description)
            assertTrue(detail.benchmarks!!.isNotEmpty())
            assertTrue(detail.providers.isEmpty())
            db.benchmarkScoreDao().upsert(BenchmarkScoreEntity("manual", modelId, "manual", 0.0))
            repo.hasLocalCatalog()
            assertTrue(repo.observeModelDetail(modelId).first()!!.benchmarks!!.any { it.name == "manual" })
            try { repo.refresh(); fail() } catch (_: IOException) { }
            assertEquals(stored.release, db.catalogDao().readCatalog().release)
            assertTrue(repo.observeModelDetail(modelId).first()!!.benchmarks!!.any { it.name == "manual" })
        } finally { db.close() }
    }

    @Test fun publishedBytesTravelThroughProductionHttpsAndRoomWithPricesAndFavoritesIntact() = runBlocking {
        var atlasRequests = 0
        var providerRequests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.toString()
            val bytes = when {
                url.startsWith(ATLAS_BASE_URL) -> {
                    atlasRequests++
                    context.assets.open("atlas/" + url.removePrefix(ATLAS_BASE_URL)).use { it.readBytes() }
                }
                url == "https://models.dev/api.json?type=all" -> {
                    providerRequests++
                    """{"provider":{"id":"provider","name":"API","models":{"svc":{"id":"svc","name":"Service","canonical_model_id":"$modelId","cost":{"input":0,"output":0.003},"limit":{"context":100,"output":20}}}}}""".toByteArray()
                }
                else -> error("Unexpected URL: $url")
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(bytes.toResponseBody()).build()
        }.build()
        val db = database()
        try {
            val dao = db.catalogDao()
            dao.replaceCatalog(CatalogSnapshot(listOf(ModelEntity(modelId, modelId, "Old", 0, type = "llm")), emptyList(), emptyList()))
            db.favoriteDao().add(modelId, 123L)
            db.benchmarkScoreDao().upsert(BenchmarkScoreEntity("old", modelId, "old", -1.0))
            val repo = repository(db, client)
            assertTrue(repo.refresh())
            val detail = repo.observeModelDetail(modelId).first()!!
            assertFalse(detail.benchmarks!!.any { it.name == "old" })
            assertEquals(0.003, detail.providers.single().offerings.single().price!!.output)
            assertEquals(100L, detail.providers.single().offerings.single().metadata.contextLimitTokens)
            assertNotEquals(100L, detail.metadata.contextLimitTokens)
            assertEquals(123L, db.favoriteDao().observeFavorite(modelId).first()!!.favoritedAtEpochMillis)
            assertEquals(5, atlasRequests)
            assertEquals(1, providerRequests)
            assertFalse(repo.refresh())
            val stored = db.catalogDao().readCatalog()
            val matchedId = stored.scores!!.first { s -> stored.definitions!!.any { it.id == s.benchmarkId } }.modelId
            val matched = repo.observeModelDetail(matchedId).first()!!.benchmarks!!.first { it.description != null }
            assertTrue(matched.description!!.isNotBlank())
        } finally { db.close() }
    }

    @Test fun singleTransactionRollsBackCatalogScoresDefinitionsVersionAndFavoriteIdentity() = runBlocking {
        val db = database()
        try {
            val snapshot = CatalogMapper().mapAtlas(bundled(), emptyMap(), 1, 1)
            val dao = db.catalogDao()
            dao.replaceCatalog(snapshot)
            db.favoriteDao().add(modelId, 123)
            val before = dao.readCatalog()
            val duplicate = before.scores!!.first()
            try {
                dao.replaceCatalog(snapshot.copy(
                    models = snapshot.models.map { it.copy(name = "Changed") },
                    scores = listOf(duplicate, duplicate),
                    definitions = emptyList(), release = snapshot.release!!.copy(dataVersion = "bad"),
                ))
                fail("Duplicate score should fail the entire transaction")
            } catch (_: android.database.sqlite.SQLiteConstraintException) { }
            assertTrue(before.hasSameContentAs(dao.readCatalog()))
            assertNotEquals("Changed", db.favoriteDao().observeFavorite(modelId).first()!!.name)
        } finally { db.close() }
    }

    @Test fun detailFlowReadsScoresDefinitionsAndChineseFromSameCommittedRelease() = runBlocking {
        val db = database()
        try {
            val dao = db.catalogDao()
            val initial = CatalogMapper().mapAtlas(bundled(), emptyMap(), 1, 1)
            dao.replaceCatalog(initial)
            val matched = initial.scores!!.first { s -> initial.definitions!!.any { it.id == s.benchmarkId } }
            val changed = initial.copy(
                models = initial.models.map { it.copy(descriptionZhCn = "new-model") },
                definitions = initial.definitions!!.map { it.copy(descriptionZhCn = "new-definition") },
                scores = initial.scores.map { it.copy(score = 123.456, scoreText = null) },
                release = initial.release!!.copy(dataVersion = "sha256:new"),
            )
            val observed = async {
                withTimeout(10_000) {
                    dao.observeModelDetail(matched.modelId).first { it?.model?.descriptionZhCn == "new-model" }
                }
            }
            dao.replaceCatalog(changed)
            val result = observed.await()!!.toModelDetail()!!
            assertTrue(result.benchmarks!!.all { it.score == 123.456 })
            assertTrue(result.benchmarks.filter { it.description != null }.all { it.description == "new-definition" })
        } finally { db.close() }
    }

    @Test fun fileReopenRetainsNewReleaseWithoutNetworkAndEmptyReleaseDoesNotDeleteFavorites() = runBlocking {
        val name = "atlas-contract-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder<CatalogDatabase>(context, name).setDriver(AndroidSQLiteDriver()).build()
        val snapshot = CatalogMapper().mapAtlas(bundled(), emptyMap(), 1, 1)
        try {
            open().let { db -> try {
                db.catalogDao().replaceCatalog(snapshot)
                db.favoriteDao().add(modelId, 123)
            } finally { db.close() } }
            open().let { db -> try {
                assertTrue(snapshot.hasSameContentAs(db.catalogDao().readCatalog()))
                val empty = snapshot.copy(models = emptyList(), scores = emptyList(), definitions = emptyList())
                db.catalogDao().replaceCatalog(empty)
                assertTrue(db.benchmarkScoreDao().observeForModel(modelId).first().isEmpty())
                assertNotNull(db.favoriteDao().observeFavorite(modelId).first())
                assertNotNull(db.catalogDao().readCatalog().release)
            } finally { db.close() } }
        } finally { context.deleteDatabase(name) }
    }
}
