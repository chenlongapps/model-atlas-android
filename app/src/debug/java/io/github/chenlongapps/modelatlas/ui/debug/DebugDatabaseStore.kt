package io.github.chenlongapps.modelatlas.ui.debug

import android.content.Context
import androidx.room3.PooledConnection
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteStatement
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.chenlongapps.modelatlas.data.local.CatalogDatabase
import io.github.chenlongapps.modelatlas.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal data class DatabaseOverview(
    val version: Long,
    val counts: Map<DatabaseTable, Long>,
    val databaseBytes: Long?,
    val walBytes: Long?,
    val shmBytes: Long?,
)

internal data class DatabaseRowSummary(val key: Map<String, String>, val name: String)
internal data class DatabasePage(val rows: List<DatabaseRowSummary>, val total: Long, val offset: Int)
internal data class DatabaseDeleteImpact(val original: DatabaseRow, val cascadeCount: Long)

internal sealed interface DatabaseWriteResult {
    data object Success : DatabaseWriteResult
    data object Conflict : DatabaseWriteResult
    data object Duplicate : DatabaseWriteResult
    data object CascadeChanged : DatabaseWriteResult
    data class Invalid(val error: DatabaseValidationError) : DatabaseWriteResult
}

internal class DatabaseSchemaMismatch : IllegalStateException()

internal interface DatabaseAccess {
    suspend fun overview(): DatabaseOverview
    suspend fun page(table: DatabaseTable, search: String, requestedOffset: Int): DatabasePage
    suspend fun read(table: DatabaseTable, key: Map<String, String>): DatabaseRow?
    suspend fun deleteImpact(original: DatabaseRow): DatabaseDeleteImpact?
    suspend fun save(table: DatabaseTable, values: Map<String, String?>, original: DatabaseRow?): DatabaseWriteResult
    suspend fun delete(impact: DatabaseDeleteImpact): DatabaseWriteResult
}

/** Debug 独有的受限行操作，使用同一 Room 连接和失效通知，不打开第二个数据库。 */
internal class DebugDatabaseStore @Inject constructor(
    private val database: CatalogDatabase,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DatabaseAccess {
    override suspend fun overview(): DatabaseOverview = withContext(ioDispatcher) {
        val (version, counts) = database.withReadTransaction {
            checkSchema(this)
            val version = scalar("PRAGMA user_version")
            val counts = DatabaseTable.entries.associateWith { scalar("SELECT COUNT(*) FROM `${it.tableName}`") }
            version to counts
        }
        val file = context.getDatabasePath("catalog.db")
        fun size(suffix: String): Long? = runCatching {
            val target = if (suffix.isEmpty()) file else java.io.File(file.path + suffix)
            if (target.exists()) target.length() else 0L
        }.getOrNull()
        DatabaseOverview(version, counts, size(""), size("-wal"), size("-shm"))
    }

    override suspend fun page(table: DatabaseTable, search: String, requestedOffset: Int): DatabasePage = withContext(ioDispatcher) {
        database.withReadTransaction {
            checkSchema(this)
            val searchColumns = (table.primaryColumns.map { it.name } + "name").distinct()
            val predicate = if (search.isEmpty()) "" else searchColumns.joinToString(" OR ", prefix = " WHERE (", postfix = ")") {
                "instr(lower(`$it`), lower(?)) > 0"
            }
            val arguments = List(if (search.isEmpty()) 0 else searchColumns.size) { search }
            val total = usePrepared("SELECT COUNT(*) FROM `${table.tableName}`$predicate") { statement ->
                arguments.forEachIndexed { index, arg -> statement.bindText(index + 1, arg) }
                check(statement.step())
                statement.getLong(0)
            }
            val lastOffset = if (total == 0L) 0 else (((total - 1) / PAGE_SIZE) * PAGE_SIZE).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val offset = requestedOffset.coerceAtLeast(0).coerceAtMost(lastOffset)
            val selected = (table.primaryColumns.map { it.name } + "name").distinct()
            val order = table.primaryColumns.joinToString(", ") { "`${it.name}`" }
            val sql = "SELECT ${selected.joinToString(", ") { "`$it`" }} FROM `${table.tableName}`$predicate ORDER BY $order LIMIT ? OFFSET ?"
            val rows = usePrepared(sql) { statement ->
                arguments.forEachIndexed { index, arg -> statement.bindText(index + 1, arg) }
                statement.bindLong(arguments.size + 1, PAGE_SIZE.toLong())
                statement.bindLong(arguments.size + 2, offset.toLong())
                buildList {
                    while (statement.step()) {
                        val key = table.primaryColumns.associate { it.name to statement.getText(selected.indexOf(it.name)) }
                        add(DatabaseRowSummary(key, statement.getText(selected.indexOf("name"))))
                    }
                }
            }
            DatabasePage(rows, total, offset)
        }
    }

    override suspend fun read(table: DatabaseTable, key: Map<String, String>): DatabaseRow? = withContext(ioDispatcher) {
        database.withReadTransaction {
            checkSchema(this)
            readRow(table, key)
        }
    }

    override suspend fun deleteImpact(original: DatabaseRow): DatabaseDeleteImpact? = withContext(ioDispatcher) {
        database.withReadTransaction {
            checkSchema(this)
            if (readRow(original.table, original.key) != original) return@withReadTransaction null
            DatabaseDeleteImpact(original, cascadeCount(original))
        }
    }

    override suspend fun save(table: DatabaseTable, values: Map<String, String?>, original: DatabaseRow?): DatabaseWriteResult =
        withContext<DatabaseWriteResult>(ioDispatcher) {
            validateDatabaseRow(table, values, original)?.let { return@withContext DatabaseWriteResult.Invalid(it) }
            database.withWriteTransaction {
                checkSchema(this)
                val row = DatabaseRow(table, values)
                val current = readRow(table, row.key)
                if (original == null && current != null) return@withWriteTransaction DatabaseWriteResult.Duplicate
                if (original != null && current != original) return@withWriteTransaction DatabaseWriteResult.Conflict
                if (original == null) {
                    val columns = table.columns
                    val names = columns.joinToString(", ") { "`${it.name}`" }
                    val placeholders = columns.joinToString(", ") { "?" }
                    usePrepared("INSERT INTO `${table.tableName}` ($names) VALUES ($placeholders)") { statement ->
                        columns.forEachIndexed { index, column -> statement.bindValue(index + 1, column, values[column.name]) }
                        statement.step()
                    }
                } else {
                    val changed = table.columns.filter { !it.primary && values[it.name] != original.values[it.name] }
                    if (changed.isNotEmpty()) {
                        val assignments = changed.joinToString(", ") { "`${it.name}` = ?" }
                        usePrepared("UPDATE `${table.tableName}` SET $assignments WHERE ${table.keyPredicate()}") { statement ->
                            changed.forEachIndexed { index, column -> statement.bindValue(index + 1, column, values[column.name]) }
                            table.primaryColumns.forEachIndexed { index, column ->
                                statement.bindText(changed.size + index + 1, row.key.getValue(column.name))
                            }
                            statement.step()
                        }
                    }
                }
                DatabaseWriteResult.Success
            }
        }

    override suspend fun delete(impact: DatabaseDeleteImpact): DatabaseWriteResult = withContext(ioDispatcher) {
        database.withWriteTransaction {
            checkSchema(this)
            val row = impact.original
            if (readRow(row.table, row.key) != row) return@withWriteTransaction DatabaseWriteResult.Conflict
            if (cascadeCount(row) != impact.cascadeCount) return@withWriteTransaction DatabaseWriteResult.CascadeChanged
            usePrepared("DELETE FROM `${row.table.tableName}` WHERE ${row.table.keyPredicate()}") { statement ->
                row.table.primaryColumns.forEachIndexed { index, column -> statement.bindText(index + 1, row.key.getValue(column.name)) }
                statement.step()
            }
            DatabaseWriteResult.Success
        }
    }

    private suspend fun PooledConnection.cascadeCount(row: DatabaseRow): Long {
        if (row.table != DatabaseTable.PROVIDERS) return 0
        return usePrepared("SELECT COUNT(*) FROM `model_offerings` WHERE `providerId` = ?") {
            it.bindText(1, row.key.getValue("id"))
            check(it.step())
            it.getLong(0)
        }
    }

    private suspend fun PooledConnection.readRow(table: DatabaseTable, key: Map<String, String>): DatabaseRow? {
        require(key.keys == table.primaryColumns.map { it.name }.toSet())
        val columns = table.columns.joinToString(", ") { "`${it.name}`" }
        return usePrepared("SELECT $columns FROM `${table.tableName}` WHERE ${table.keyPredicate()}") { statement ->
            table.primaryColumns.forEachIndexed { index, column -> statement.bindText(index + 1, key.getValue(column.name)) }
            if (!statement.step()) null else DatabaseRow(table, table.columns.mapIndexed { index, column ->
                val value = if (statement.isNull(index)) null else if (column.affinity == "INTEGER") {
                    statement.getLong(index).toString()
                } else statement.getText(index)
                column.name to value
            }.toMap())
        }
    }

    private suspend fun PooledConnection.scalar(sql: String): Long = usePrepared(sql) {
        check(it.step())
        it.getLong(0)
    }

    /** 拒绝与白名单不一致的数据库，防止未来 Schema 变化时错误编辑列。 */
    private suspend fun checkSchema(connection: PooledConnection) {
        for (table in DatabaseTable.entries) {
            val actual = connection.usePrepared("PRAGMA table_info(`${table.tableName}`)") { statement ->
                buildList {
                    while (statement.step()) add(Triple(statement.getText(1), statement.getText(2).uppercase(), statement.getLong(3) != 0L) to statement.getLong(5).toInt())
                }
            }
            val expected = table.columns.map { column ->
                Triple(column.name, column.affinity, column.required) to
                    (table.primaryColumns.indexOf(column).let { if (it < 0) 0 else it + 1 })
            }
            if (actual != expected) throw DatabaseSchemaMismatch()
        }
    }

    private fun DatabaseTable.keyPredicate() = primaryColumns.joinToString(" AND ") { "`${it.name}` = ?" }

    private fun SQLiteStatement.bindValue(index: Int, column: DatabaseColumn, value: String?) {
        when {
            value == null -> bindNull(index)
            column.affinity == "INTEGER" -> bindLong(index, value.toLong())
            else -> bindText(index, value)
        }
    }

    companion object { const val PAGE_SIZE = 50 }
}
