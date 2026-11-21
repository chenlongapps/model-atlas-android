package io.github.chenlongapps.modelatlas.ui.modelcompare

import io.github.chenlongapps.modelatlas.data.model.ModelBenchmark
import java.net.URI

internal data class CompareBenchmarkGroup(
    val key: CompareBenchmarkKey,
    val first: ModelBenchmark,
    val second: ModelBenchmark,
    val referenceOnly: Boolean = false,
) {
    val name: String get() = first.name
}

internal data class CompareBenchmarkKey(
    val id: String,
    val version: String,
    val metric: String,
    val unit: String,
    val test: Boolean,
) {
    val itemKey: String = listOf(id, version, metric, unit, test.toString())
        .joinToString("|", prefix = "compare-benchmark:") { "${it.length}:$it" }
}

private fun comparableBenchmarkGroups(
    first: List<ModelBenchmark>?,
    second: List<ModelBenchmark>?,
): List<CompareBenchmarkGroup> {
    val left = first.byIdentity()
    val right = second.byIdentity()
    return left.mapNotNull { (key, entries) ->
        val others = right[key] ?: return@mapNotNull null
        val firstByProtocol = entries.highestEffort().byProtocol()
        val secondByProtocol = others.highestEffort().byProtocol()
        // 可以按明确的运行条件消歧，但不能在多对匹配、同条件多条或低档成绩中任选。
        val protocol = firstByProtocol.keys.intersect(secondByProtocol.keys).singleOrNull() ?: return@mapNotNull null
        val a = firstByProtocol.getValue(protocol).singleOrNull() ?: return@mapNotNull null
        val b = secondByProtocol.getValue(protocol).singleOrNull() ?: return@mapNotNull null
        CompareBenchmarkGroup(key, a, b)
    }.sortedWith(benchmarkOrder)
}

internal fun compareBenchmarkGroups(
    first: List<ModelBenchmark>?,
    second: List<ModelBenchmark>?,
): List<CompareBenchmarkGroup> {
    val comparable = comparableBenchmarkGroups(first, second)
    val shown = comparable.mapTo(hashSetOf()) { it.first.benchmarkKey(useDefinitionVersion = true) }
    val left = first.byIdentity(useDefinitionVersion = true)
    val right = second.byIdentity(useDefinitionVersion = true)
    val references = left.mapNotNull { (key, entries) ->
        if (key in shown) return@mapNotNull null
        val others = right[key] ?: return@mapNotNull null
        val (a, b) = sharedSourcePair(entries.highestEffort(), others.highestEffort()) ?: return@mapNotNull null
        CompareBenchmarkGroup(key, a, b, referenceOnly = true)
    }.sortedWith(benchmarkOrder)
    return comparable + references
}

private val benchmarkOrder = compareBy<CompareBenchmarkGroup, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
    .thenBy { it.key.version }.thenBy { it.key.metric }.thenBy { it.key.unit }.thenBy { it.key.itemKey }

private fun List<ModelBenchmark>?.byIdentity(useDefinitionVersion: Boolean = false) = orEmpty()
    .mapNotNull { entry ->
        if (entry.scoreText.isNullOrBlank() && entry.score?.isFinite() != true) return@mapNotNull null
        entry.benchmarkKey(useDefinitionVersion)?.let { it to entry }
    }.groupBy({ it.first }, { it.second })

private fun ModelBenchmark.benchmarkKey(useDefinitionVersion: Boolean = false): CompareBenchmarkKey? {
    val id = benchmarkId?.takeIf { it.isNotBlank() && !it.startsWith("unmapped:") } ?: return null
    val version = version.specified() ?: return null
    val metric = metric.specified() ?: return null
    val unit = unit.specified() ?: return null
    val groupedVersion = if (useDefinitionVersion) benchmarkDefinitionVersion.specified() ?: version else version
    return CompareBenchmarkKey(id, groupedVersion, metric, unit, sourceType == "test")
}

private data class SourcedBenchmark(val record: ModelBenchmark, val url: String, val host: String)

private fun ModelBenchmark.sourced(): SourcedBenchmark? {
    val url = source.specified() ?: return null
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    if (uri.rawUserInfo != null || (!uri.scheme.equals("https", true) && !uri.scheme.equals("http", true))) return null
    return SourcedBenchmark(this, url, host)
}

/** 来源只用来选出参考记录，不代表同一运行条件；有歧义时不按源顺序或分数任选。 */
private fun sharedSourcePair(
    first: List<ModelBenchmark>,
    second: List<ModelBenchmark>,
): Pair<ModelBenchmark, ModelBenchmark>? {
    val left = first.mapNotNull { it.sourced() }
    val right = second.mapNotNull { it.sourced() }
    fun sharedBy(key: (SourcedBenchmark) -> String): List<Pair<List<SourcedBenchmark>, List<SourcedBenchmark>>> {
        val a = left.groupBy(key)
        val b = right.groupBy(key)
        return a.keys.intersect(b.keys).map { a.getValue(it) to b.getValue(it) }
    }
    val (a, b) = sharedBy(SourcedBenchmark::url).ifEmpty { sharedBy(SourcedBenchmark::host) }.singleOrNull()
        ?: return null
    val leftRecord = a.singleOrNull()?.record ?: return null
    val rightRecord = b.singleOrNull()?.record ?: return null
    if (leftRecord.reasoningEffort.isNullOrBlank() != rightRecord.reasoningEffort.isNullOrBlank()) return null
    return leftRecord to rightRecord
}

private enum class Effort { Off, Minimal, Low, Medium, High, ExtraHigh, Max }

private val effortSeparators = Regex("""[\s_-]+""")
private val unspecifiedValues = setOf("unspecified", "unknown", "n/a")

private fun String?.effortLevel(): Effort? {
    val value = this?.trim()?.lowercase()?.replace(effortSeparators, " ") ?: return null
    if (value in setOf("non reasoning", "no reasoning", "no thinking")) return Effort.Off
    val tier = value.removeSuffix(" reasoning effort").removeSuffix(" reasoning")
        .removeSuffix(" thinking effort").removeSuffix(" thinking")
    return when (tier) {
        "off", "none", "disabled" -> Effort.Off
        "minimal", "min" -> Effort.Minimal
        "low" -> Effort.Low
        "medium", "med", "mid" -> Effort.Medium
        "high" -> Effort.High
        "xhigh", "x high", "extrahigh", "extra high", "veryhigh", "very high" -> Effort.ExtraHigh
        "max", "maximum" -> Effort.Max
        else -> null
    }
}

private fun List<ModelBenchmark>.highestEffort(): List<ModelBenchmark> {
    val ranked = mapNotNull { record -> record.reasoningEffort.effortLevel()?.let { it to record } }
    val highest = ranked.maxOfOrNull { it.first }
    if (highest != null) return ranked.filter { it.first == highest }.map { it.second }
    // 未报告的档位不参与排序；只有整侧未报告时才尝试按同一出处匹配。
    return if (all { it.reasoningEffort.isNullOrBlank() }) this else emptyList()
}

private data class Protocol(
    val dataset: String,
    val harness: String,
    val effortReported: Boolean,
    val toolsEnabled: Boolean?,
    val variant: String?,
    val source: String?,
    val sourceType: String?,
    val date: String?,
    val higherIsBetter: Boolean?,
)

private fun String?.specified(): String? = this?.takeIf {
    it.isNotBlank() && it.trim().lowercase() !in unspecifiedValues
}

private fun List<ModelBenchmark>.byProtocol() =
    mapNotNull { entry -> entry.protocol()?.let { it to entry } }.groupBy({ it.first }, { it.second })

private fun ModelBenchmark.protocol(): Protocol? {
    val dataset = dataset.specified() ?: return null
    val harness = harness.specified() ?: return null
    val effortReported = !reasoningEffort.isNullOrBlank()
    // 工具、变体或思考程度缺失时，只有同一明确出处可作为配对依据；不补造实际取值。
    if (source.specified() == null && (toolsEnabled == null || variant.specified() == null || !effortReported)) return null
    return Protocol(dataset, harness, effortReported, toolsEnabled, variant, source, sourceType, date, higherIsBetter)
}
