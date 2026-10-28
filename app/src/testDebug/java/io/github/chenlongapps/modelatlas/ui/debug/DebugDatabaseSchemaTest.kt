package io.github.chenlongapps.modelatlas.ui.debug

import io.github.chenlongapps.modelatlas.R
import org.junit.Assert.*
import org.junit.Test

class DebugDatabaseSchemaTest {
    private fun values(table: DatabaseTable): Map<String, String?> = table.columns.associate { column ->
        column.name to when {
            !column.required -> null
            column.type == DatabaseFieldType.INTEGER -> "123"
            column.type == DatabaseFieldType.REAL -> "1.25"
            else -> "required"
        }
    }

    @Test fun allFiveTablesAcceptMinimumRecordsAndPreserveNullAndEmpty() {
        assertEquals(setOf("models", "providers", "model_offerings", "favorites", "benchmark_scores"), DatabaseTable.entries.map { it.tableName }.toSet())
        for (table in DatabaseTable.entries) assertNull(validateDatabaseRow(table, values(table)))
        val model = values(DatabaseTable.MODELS) + mapOf("description" to "", "attachment" to "0", "inputModalities" to "[]")
        assertNull(validateDatabaseRow(DatabaseTable.MODELS, model))
        assertNull(validateDatabaseRow(DatabaseTable.MODELS, model + ("attachment" to "1")))
        assertNull(validateDatabaseRow(DatabaseTable.MODELS, model + ("attachment" to null)))
    }

    @Test fun validatesRequiredKeysLongRangeAndBooleanWithoutCoercion() {
        val table = DatabaseTable.MODELS
        val base = values(table)
        assertEquals(R.string.debug_database_invalid_primary_empty, validateDatabaseRow(table, base + ("id" to " "))?.messageResource)
        assertEquals(R.string.debug_database_invalid_null, validateDatabaseRow(table, base + ("name" to null))?.messageResource)
        assertEquals(R.string.debug_database_invalid_integer, validateDatabaseRow(table, base + ("fetchedAtEpochMillis" to "9223372036854775808"))?.messageResource)
        assertEquals(R.string.debug_database_invalid_boolean, validateDatabaseRow(table, base + ("reasoning" to "false"))?.messageResource)
        assertEquals(R.string.debug_database_invalid_schema, validateDatabaseRow(table, base - "name")?.messageResource)
        val original = DatabaseRow(table, base)
        assertEquals(R.string.debug_database_invalid_primary_change, validateDatabaseRow(table, base + ("id" to "different"), original)?.messageResource)
    }

    @Test fun jsonUsesLocalConvertersAndLeavesUnmodifiedRawValuesUntouched() {
        val model = values(DatabaseTable.MODELS)
        assertNull(validateDatabaseRow(DatabaseTable.MODELS, model + ("weights" to """[{"url":"https://example.test","future":1}]""")))
        assertEquals("weights", validateDatabaseRow(DatabaseTable.MODELS, model + ("weights" to "[{}]"))?.field)
        assertEquals("inputModalities", validateDatabaseRow(DatabaseTable.MODELS, model + ("inputModalities" to "null"))?.field)
        val offerings = values(DatabaseTable.OFFERINGS)
        assertNull(validateDatabaseRow(DatabaseTable.OFFERINGS, offerings + mapOf(
            "pricing" to """{"input":0,"future":"retained"}""",
            "interleaved" to """{"type":"toggle","enabled":false}""",
            "providerOverride" to """{"headers":{"x-test":"value"}}""",
            "experimentalModes" to "{}",
        )))
        assertEquals("interleaved", validateDatabaseRow(DatabaseTable.OFFERINGS, offerings + ("interleaved" to "true"))?.field)
        assertEquals("pricing", validateDatabaseRow(DatabaseTable.OFFERINGS, offerings + ("pricing" to """{"input":"wrong"}"""))?.field)
        // 已损坏的值仍可查看／修复，修改其他列不会强制覆盖原始 JSON。
        val broken = DatabaseRow(DatabaseTable.OFFERINGS, offerings + ("pricing" to "bad"))
        assertNull(validateDatabaseRow(DatabaseTable.OFFERINGS, broken.values + ("name" to "Changed"), broken))
        assertNotNull(validateDatabaseRow(DatabaseTable.OFFERINGS, broken.values + ("pricing" to "still bad"), broken))
    }

    @Test fun scoresValidateFiniteRealAndNullableBooleans() {
        val table = DatabaseTable.BENCHMARK_SCORES
        val score = values(table)
        assertNull(validateDatabaseRow(table, score + mapOf("score" to "-0.003", "toolsEnabled" to "0", "higherIsBetter" to "1")))
        assertNull(validateDatabaseRow(table, score + ("cost" to "0")))
        for (invalid in listOf("NaN", "Infinity", "1e999", "text")) {
            assertEquals(R.string.debug_database_invalid_real, validateDatabaseRow(table, score + ("score" to invalid))?.messageResource)
            assertEquals(R.string.debug_database_invalid_real, validateDatabaseRow(table, score + ("cost" to invalid))?.messageResource)
        }
    }
}
