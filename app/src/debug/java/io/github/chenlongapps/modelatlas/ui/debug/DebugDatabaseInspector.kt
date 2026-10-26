package io.github.chenlongapps.modelatlas.ui.debug

import android.content.Context
import androidx.room3.useReaderConnection
import androidx.room3.withReadTransaction
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.chenlongapps.modelatlas.data.local.CatalogDatabase
import java.io.File
import javax.inject.Inject

/** Debug-only read-only introspection. Never accept arbitrary SQL table names. */
internal val DEBUG_TABLE_NAMES = listOf("models", "providers", "model_offerings", "favorites")
private const val DATABASE_NAME = "catalog.db"

internal data class DebugTableCount(val name: String, val rows: Long)

internal data class DebugDatabaseStatus(
    val fileName: String,
    val schemaVersion: Long,
    /** Sum of main file, WAL, and SHM sizes at the time of inspection. */
    val diskBytes: Long,
    val tables: List<DebugTableCount>,
)

internal data class DebugColumnSchema(
    val name: String,
    val type: String,
    val notNull: Boolean,
    val primaryKeyPosition: Long,
    val defaultValue: String?,
)

internal interface DebugDatabaseInspector {
    suspend fun readStatus(): DebugDatabaseStatus
    suspend fun readColumns(tableName: String): List<DebugColumnSchema>
}

internal class RoomDebugDatabaseInspector @Inject constructor(
    private val database: CatalogDatabase,
    @ApplicationContext private val context: Context,
) : DebugDatabaseInspector {
    override suspend fun readStatus(): DebugDatabaseStatus {
        // Aggregates are computed by SQLite, without materializing a CatalogSnapshot.
        val (schemaVersion, tables) = database.withReadTransaction {
            val version = usePrepared("PRAGMA user_version") { statement ->
                check(statement.step()) { "No database version returned" }
                statement.getLong(0)
            }
            val counts = DEBUG_TABLE_NAMES.map { tableName ->
                val rows = usePrepared("SELECT COUNT(*) FROM \"" + tableName + "\"") { statement ->
                    check(statement.step()) { "Count query returned no rows" }
                    statement.getLong(0)
                }
                DebugTableCount(tableName, rows)
            }
            version to counts
        }
        val file = context.getDatabasePath(DATABASE_NAME)
        val bytes = listOf(file, File(file.path + "-wal"), File(file.path + "-shm"))
            .sumOf { if (it.isFile) it.length() else 0L }
        return DebugDatabaseStatus(DATABASE_NAME, schemaVersion, bytes, tables)
    }

    override suspend fun readColumns(tableName: String): List<DebugColumnSchema> {
        require(tableName in DEBUG_TABLE_NAMES) { "Unknown database table" }
        return database.useReaderConnection { connection ->
            connection.usePrepared("PRAGMA table_info(\"" + tableName + "\")") { statement ->
                buildList {
                    while (statement.step()) {
                        add(
                            DebugColumnSchema(
                                name = statement.getText(1),
                                type = statement.getText(2),
                                notNull = statement.getLong(3) != 0L,
                                defaultValue = if (statement.isNull(4)) null else statement.getText(4),
                                primaryKeyPosition = statement.getLong(5),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DebugDatabaseInspectorModule {
    @Binds
    abstract fun bindInspector(impl: RoomDebugDatabaseInspector): DebugDatabaseInspector
}
