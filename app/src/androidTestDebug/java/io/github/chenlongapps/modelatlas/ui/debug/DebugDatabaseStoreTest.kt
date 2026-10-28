package io.github.chenlongapps.modelatlas.ui.debug

import androidx.room3.Room
import androidx.room3.withWriteTransaction
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.chenlongapps.modelatlas.data.local.CatalogDatabase
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugDatabaseStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database = Room.inMemoryDatabaseBuilder<CatalogDatabase>(context).setDriver(AndroidSQLiteDriver()).build()
    private val store = DebugDatabaseStore(database, context, Dispatchers.IO)
    @After fun close() = database.close()

    private fun values(table: DatabaseTable, vararg extra: Pair<String, String?>): Map<String, String?> =
        table.columns.associate { column -> column.name to when {
            !column.required -> null
            column.type == DatabaseFieldType.INTEGER -> "100"
            column.type == DatabaseFieldType.REAL -> "1.25"
            column.name == "source" -> "models.dev"
            else -> "test"
        } } + extra.toMap()

    private suspend fun insert(table: DatabaseTable, vararg extra: Pair<String, String?>): DatabaseRow {
        val row = DatabaseRow(table, values(table, *extra))
        assertEquals(DatabaseWriteResult.Success, store.save(table, row.values, null))
        return row
    }

    @Test fun allTablesSupportCrudAndPreserveRawJsonAndIndependentFavorites() = runBlocking {
        assertEquals(1L, store.overview().version)
        val provider = insert(DatabaseTable.PROVIDERS, "id" to "p", "name" to "Provider")
        val model = insert(DatabaseTable.MODELS, "id" to "lab/model", "name" to "Model", "type" to "llm",
            "inputModalities" to "[ \"text\" ]", "attachment" to "0")
        val offering = insert(DatabaseTable.OFFERINGS, "providerId" to "p", "id" to "service", "canonicalModelId" to "lab/model",
            "pricing" to """{"input":0,"future":"preserve"}""")
        val favorite = insert(DatabaseTable.FAVORITES, "modelId" to "lab/model", "name" to "Model", "lab" to "lab")
        assertEquals(mapOf(DatabaseTable.MODELS to 1L, DatabaseTable.PROVIDERS to 1L,
            DatabaseTable.OFFERINGS to 1L, DatabaseTable.FAVORITES to 1L, DatabaseTable.BENCHMARK_SCORES to 0L), store.overview().counts)

        val edited = model.values + mapOf("name" to "Edited", "description" to "", "attachment" to "1")
        assertEquals(DatabaseWriteResult.Success, store.save(DatabaseTable.MODELS, edited, model))
        val actual = store.read(DatabaseTable.MODELS, model.key)!!
        assertEquals("", actual.values["description"])
        assertEquals("1", actual.values["attachment"])
        assertEquals("[ \"text\" ]", actual.values["inputModalities"])
        assertEquals(DatabaseWriteResult.Success, store.save(DatabaseTable.OFFERINGS, offering.values + ("name" to "Edited service"), offering))
        assertEquals("""{"input":0,"future":"preserve"}""", store.read(DatabaseTable.OFFERINGS, offering.key)!!.values["pricing"])

        assertEquals(DatabaseWriteResult.Success, store.delete(store.deleteImpact(actual)!!))
        assertNull(store.read(DatabaseTable.MODELS, model.key))
        assertNotNull(store.read(DatabaseTable.OFFERINGS, offering.key))
        assertNotNull(store.read(DatabaseTable.FAVORITES, favorite.key))
        val impact = store.deleteImpact(provider)!!
        assertEquals(1L, impact.cascadeCount)
        assertEquals(DatabaseWriteResult.Success, store.delete(impact))
        assertNull(store.read(DatabaseTable.OFFERINGS, offering.key))
        assertNotNull(store.read(DatabaseTable.FAVORITES, favorite.key))
        assertEquals(DatabaseWriteResult.Success, store.delete(store.deleteImpact(favorite)!!))
        assertNull(store.read(DatabaseTable.FAVORITES, favorite.key))
    }

    @Test fun benchmarkScoreCrudSupportsRealSearchAndNullBooleans() = runBlocking {
        val table = DatabaseTable.BENCHMARK_SCORES
        val initial = insert(table, "id" to "score", "modelId" to "lab/model", "benchmarkId" to "bench-v1",
            "score" to "0.003", "toolsEnabled" to "0")
        assertEquals("bench-v1", store.page(table, "bench", 0).rows.single().name)
        val saved = store.read(table, initial.key)!!
        assertEquals("0.003", saved.values["score"])
        assertEquals("0", saved.values["toolsEnabled"])
        assertNull(saved.values["higherIsBetter"])
        assertNull(saved.values["cost"])
        assertEquals(DatabaseWriteResult.Success, store.save(table, saved.values + mapOf("score" to "-4.5", "cost" to "0.003"), saved))
        val edited = store.read(table, saved.key)!!
        assertEquals("-4.5", edited.values["score"])
        assertEquals("0.003", edited.values["cost"])
        assertEquals(DatabaseWriteResult.Success, store.delete(store.deleteImpact(edited)!!))
        assertNull(store.read(table, saved.key))
    }

    @Test fun validatesDuplicatesForeignKeysAndConflictWithoutPartialWrites() = runBlocking {
        val provider = insert(DatabaseTable.PROVIDERS, "id" to "p")
        assertEquals(DatabaseWriteResult.Duplicate, store.save(DatabaseTable.PROVIDERS, provider.values, null))
        val missingProvider = values(DatabaseTable.OFFERINGS, "providerId" to "missing", "id" to "s")
        assertTrue(runCatching { store.save(DatabaseTable.OFFERINGS, missingProvider, null) }.isFailure)
        assertEquals(0L, store.overview().counts.getValue(DatabaseTable.OFFERINGS))
        val first = insert(DatabaseTable.OFFERINGS, "providerId" to "p", "id" to "one", "canonicalModelId" to "not-present")
        val impact = store.deleteImpact(provider)!!
        insert(DatabaseTable.OFFERINGS, "providerId" to "p", "id" to "two")
        assertEquals(DatabaseWriteResult.CascadeChanged, store.delete(impact))
        assertNotNull(store.read(DatabaseTable.OFFERINGS, first.key))
        assertEquals(DatabaseWriteResult.Success, store.save(DatabaseTable.PROVIDERS, provider.values + ("name" to "new"), provider))
        assertEquals(DatabaseWriteResult.Conflict, store.save(DatabaseTable.PROVIDERS, provider.values + ("name" to "stale"), provider))
        assertEquals(DatabaseWriteResult.Conflict, store.delete(impact))
        assertEquals("new", store.read(DatabaseTable.PROVIDERS, provider.key)!!.values["name"])
        assertTrue(store.save(DatabaseTable.OFFERINGS, first.values + ("pricing" to "broken"), first) is DatabaseWriteResult.Invalid)
        assertEquals(2L, store.overview().counts.getValue(DatabaseTable.OFFERINGS))
    }

    @Test fun pagingUsesStableCompositeKeysAndLiteralSearch() = runBlocking {
        insert(DatabaseTable.PROVIDERS, "id" to "a")
        insert(DatabaseTable.PROVIDERS, "id" to "b")
        for (i in 0 until 52) insert(DatabaseTable.OFFERINGS, "providerId" to "a", "id" to "s${i.toString().padStart(2, '0')}", "name" to "Service $i")
        insert(DatabaseTable.OFFERINGS, "providerId" to "b", "id" to "s00", "name" to "Literal %_")
        val first = store.page(DatabaseTable.OFFERINGS, "", 0)
        val second = store.page(DatabaseTable.OFFERINGS, "", 50)
        assertEquals(53L, first.total)
        assertEquals(50, first.rows.size)
        assertEquals(3, second.rows.size)
        assertEquals(53, (first.rows + second.rows).map { it.key }.toSet().size)
        assertEquals("b", store.page(DatabaseTable.OFFERINGS, "%_", 0).rows.single().key["providerId"])
        assertEquals(50, store.page(DatabaseTable.OFFERINGS, "", 500).offset)
    }

    @Test fun writesInvalidateExistingRoomFlows() = runBlocking {
        val models = Channel<List<io.github.chenlongapps.modelatlas.data.local.ModelListRow>>(Channel.UNLIMITED)
        val favorites = Channel<io.github.chenlongapps.modelatlas.data.local.entity.FavoriteEntity?>(Channel.UNLIMITED)
        val modelJob = launch { database.catalogDao().observeModelRows().collect { models.send(it) } }
        val favoriteJob = launch { database.favoriteDao().observeFavorite("lab/model").collect { favorites.send(it) } }
        withTimeout(5000) { models.receive(); favorites.receive() }
        val model = insert(DatabaseTable.MODELS, "id" to "lab/model", "name" to "Model", "type" to "llm")
        assertEquals("Model", withTimeout(5000) { models.receive() }.single().name)
        val favorite = insert(DatabaseTable.FAVORITES, "modelId" to "lab/model", "name" to "Model")
        assertEquals("lab/model", withTimeout(5000) { favorites.receive() }!!.modelId)
        assertEquals(DatabaseWriteResult.Success, store.save(DatabaseTable.MODELS, model.values + ("name" to "Changed"), model))
        assertEquals("Changed", withTimeout(5000) { models.receive() }.single().name)
        assertEquals(DatabaseWriteResult.Success, store.delete(store.deleteImpact(favorite)!!))
        assertNull(withTimeout(5000) { favorites.receive() })
        modelJob.cancel()
        favoriteJob.cancel()
    }

    @Test fun schemaMismatchPreventsReadingAndWriting() = runBlocking {
        database.withWriteTransaction { usePrepared("ALTER TABLE `favorites` ADD COLUMN `unexpected` TEXT") { it.step() } }
        assertTrue(runCatching { store.overview() }.exceptionOrNull() is DatabaseSchemaMismatch)
        assertTrue(runCatching { store.save(DatabaseTable.FAVORITES, values(DatabaseTable.FAVORITES), null) }.exceptionOrNull() is DatabaseSchemaMismatch)
    }

    @Test fun committedRowsRemainAfterFileDatabaseReopens() = runBlocking {
        val name = "debug-crud-${UUID.randomUUID()}.db"
        check(!context.getDatabasePath(name).exists())
        fun open() = Room.databaseBuilder<CatalogDatabase>(context, name).setDriver(AndroidSQLiteDriver()).build()
        try {
            val first = open()
            try {
                val access = DebugDatabaseStore(first, context, Dispatchers.IO)
                val row = values(DatabaseTable.FAVORITES, "modelId" to "lab/persisted", "name" to "Persisted")
                assertEquals(DatabaseWriteResult.Success, access.save(DatabaseTable.FAVORITES, row, null))
            } finally { first.close() }
            val second = open()
            try {
                val access = DebugDatabaseStore(second, context, Dispatchers.IO)
                assertEquals("Persisted", access.read(DatabaseTable.FAVORITES, mapOf("modelId" to "lab/persisted"))?.values?.get("name"))
            } finally { second.close() }
        } finally { context.deleteDatabase(name) }
    }
}
