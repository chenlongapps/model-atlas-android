package io.github.chenlongapps.modelatlas.ui.debug

import io.github.chenlongapps.modelatlas.data.local.converter.CatalogTypeConverters
import io.github.chenlongapps.modelatlas.R

/** 仅允许操作已知业务表；列顺序、主键和类型与 Room Schema v1 一致。 */
internal enum class DatabaseFieldType {
    TEXT, INTEGER, BOOLEAN, STRING_LIST, WEIGHTS, LINKS, BENCHMARKS,
    REASONING_OPTIONS, INTERLEAVED, PRICING, PROVIDER_OVERRIDE, EXPERIMENTAL_MODES,
}

internal data class DatabaseColumn(
    val name: String,
    val type: DatabaseFieldType = DatabaseFieldType.TEXT,
    val required: Boolean = false,
    val primary: Boolean = false,
    val sensitive: Boolean = false,
) {
    val affinity: String get() = when (type) {
        DatabaseFieldType.INTEGER, DatabaseFieldType.BOOLEAN -> "INTEGER"
        else -> "TEXT"
    }
    val isJson: Boolean get() = type !in setOf(DatabaseFieldType.TEXT, DatabaseFieldType.INTEGER, DatabaseFieldType.BOOLEAN)
}

private fun text(name: String, required: Boolean = false, primary: Boolean = false) =
    DatabaseColumn(name, required = required, primary = primary)
private fun number(name: String, required: Boolean = false) = DatabaseColumn(name, DatabaseFieldType.INTEGER, required)
private fun bool(name: String) = DatabaseColumn(name, DatabaseFieldType.BOOLEAN)
private fun json(name: String, type: DatabaseFieldType, sensitive: Boolean = false) =
    DatabaseColumn(name, type, sensitive = sensitive)

private fun metadataColumns() = listOf(
    text("description"), text("family"), text("type"), text("knowledgeCutoff"), text("releaseDate"),
    text("sourceLastUpdated"), bool("attachment"), bool("reasoning"), bool("toolCall"),
    bool("structuredOutput"), bool("temperature"), bool("openWeights"), number("contextLimitTokens"),
    number("inputLimitTokens"), number("outputLimitTokens"),
    json("inputModalities", DatabaseFieldType.STRING_LIST), json("outputModalities", DatabaseFieldType.STRING_LIST),
)

internal enum class DatabaseTable(val tableName: String, val titleResource: Int, val columns: List<DatabaseColumn>) {
    MODELS("models", R.string.debug_database_table_models, listOf(
        text("id", true, true), text("remoteId", true), text("name", true), number("fetchedAtEpochMillis", true),
        text("source", true),
    ) + metadataColumns() + listOf(
        text("license"), json("weights", DatabaseFieldType.WEIGHTS), json("links", DatabaseFieldType.LINKS),
        json("benchmarks", DatabaseFieldType.BENCHMARKS),
    )),
    PROVIDERS("providers", R.string.debug_database_table_providers, listOf(
        text("id", true, true), text("remoteId", true), text("name", true), number("fetchedAtEpochMillis", true),
        text("source", true), text("apiUrl"), text("documentationUrl"), text("npmPackage"),
        json("environmentVariables", DatabaseFieldType.STRING_LIST),
    )),
    OFFERINGS("model_offerings", R.string.debug_database_table_offerings, listOf(
        text("providerId", true, true), text("id", true, true), text("remoteId", true), text("name", true),
        number("fetchedAtEpochMillis", true), text("source", true), text("canonicalModelId"),
    ) + metadataColumns() + listOf(
        text("status"), json("reasoningOptions", DatabaseFieldType.REASONING_OPTIONS),
        json("interleaved", DatabaseFieldType.INTERLEAVED), json("pricing", DatabaseFieldType.PRICING),
        json("providerOverride", DatabaseFieldType.PROVIDER_OVERRIDE, sensitive = true),
        json("experimentalModes", DatabaseFieldType.EXPERIMENTAL_MODES, sensitive = true),
    )),
    FAVORITES("favorites", R.string.debug_database_table_favorites, listOf(
        text("modelId", true, true), text("name", true), text("lab"), number("favoritedAtEpochMillis", true),
    ));

    val primaryColumns: List<DatabaseColumn> get() = columns.filter { it.primary }
}

internal data class DatabaseRow(val table: DatabaseTable, val values: Map<String, String?>) {
    val key: Map<String, String> get() = table.primaryColumns.associate { it.name to requireNotNull(values[it.name]) }
}

internal data class DatabaseValidationError(val field: String, val messageResource: Int)

/** 未修改的原始 JSON 不重新编码，避免丢失未知字段或格式。 */
internal fun validateDatabaseRow(
    table: DatabaseTable,
    values: Map<String, String?>,
    original: DatabaseRow? = null,
): DatabaseValidationError? {
    if (values.keys != table.columns.map { it.name }.toSet()) return DatabaseValidationError("", R.string.debug_database_invalid_schema)
    if (original != null && (original.table != table || table.primaryColumns.any { values[it.name] != original.values[it.name] })) {
        return DatabaseValidationError("", R.string.debug_database_invalid_primary_change)
    }
    val converters = CatalogTypeConverters()
    for (column in table.columns) {
        val value = values[column.name]
        if (value == null) {
            if (column.required) return DatabaseValidationError(column.name, R.string.debug_database_invalid_null)
            continue
        }
        if (column.primary && value.isBlank()) return DatabaseValidationError(column.name, R.string.debug_database_invalid_primary_empty)
        if (column.type == DatabaseFieldType.INTEGER && value.toLongOrNull() == null) {
            return DatabaseValidationError(column.name, R.string.debug_database_invalid_integer)
        }
        if (column.type == DatabaseFieldType.BOOLEAN && value !in listOf("0", "1")) {
            return DatabaseValidationError(column.name, R.string.debug_database_invalid_boolean)
        }
        if (column.isJson && (original == null || original.values[column.name] != value)) {
            val valid = runCatching {
                when (column.type) {
                    DatabaseFieldType.STRING_LIST -> converters.jsonToStringList(value)
                    DatabaseFieldType.WEIGHTS -> converters.jsonToWeights(value)
                    DatabaseFieldType.LINKS -> converters.jsonToLinks(value)
                    DatabaseFieldType.BENCHMARKS -> converters.jsonToBenchmarks(value)
                    DatabaseFieldType.REASONING_OPTIONS -> converters.jsonToReasoningOptions(value)
                    DatabaseFieldType.INTERLEAVED -> converters.jsonToInterleaved(value)
                    DatabaseFieldType.PRICING -> converters.jsonToPricing(value)
                    DatabaseFieldType.PROVIDER_OVERRIDE -> converters.jsonToProviderOverride(value)
                    DatabaseFieldType.EXPERIMENTAL_MODES -> converters.jsonToExperimentalModes(value)
                    else -> error("Not a JSON column")
                } != null
            }.getOrDefault(false)
            if (!valid) return DatabaseValidationError(column.name, R.string.debug_database_invalid_json)
        }
    }
    return null
}
