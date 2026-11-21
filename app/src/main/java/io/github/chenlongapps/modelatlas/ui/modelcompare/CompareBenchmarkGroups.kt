package io.github.chenlongapps.modelatlas.ui.modelcompare

import io.github.chenlongapps.modelatlas.data.model.ModelBenchmark

internal data class CompareBenchmarkGroup(
    val key: CompareBenchmarkKey,
    val name: String,
    val first: List<ModelBenchmark>,
    val second: List<ModelBenchmark>,
) {
    val shared: Boolean get() = first.isNotEmpty() && second.isNotEmpty()
    val configurations: List<CompareBenchmarkConfiguration> = compareConfigurations(key, first, second)
    // 只能展示唯一的一对明确匹配；多对匹配时留给用户选择配置，不挑分数。
    private val comparableConfigurations = configurations.filter { it.comparable }
    val comparableCount: Int get() = comparableConfigurations.size
    val representative: CompareBenchmarkConfiguration? = comparableConfigurations.singleOrNull()
}

internal data class CompareBenchmarkConfiguration(
    val key: String,
    val first: ModelBenchmark?,
    val second: ModelBenchmark?,
    val comparable: Boolean = false,
)

internal data class CompareBenchmarkKey(
    val id: String?,
    val version: String?,
    val metric: String?,
    val unit: String?,
    val test: Boolean,
    // 未关联定义的成绩只在各自模型侧展示，不用显示名称猜测共同评测。
    val unmatchedSlot: Int?,
    val unmatchedName: String?,
) {
    val itemKey: String = listOf(id, version, metric, unit, test.toString(), unmatchedSlot?.toString(), unmatchedName)
        .joinToString("|", prefix = "compare-benchmark:") { if (it == null) "-1:" else "${it.length}:$it" }
}

/** 保持既有评测身份边界；缺分的记录也保留供展开追溯。 */
internal fun compareBenchmarkGroups(
    first: List<ModelBenchmark>?,
    second: List<ModelBenchmark>?,
): List<CompareBenchmarkGroup> {
    fun byKey(slot: Int, entries: List<ModelBenchmark>?) = entries.orEmpty()
        .groupBy { entry ->
            val id = entry.benchmarkId?.takeIf(String::isNotBlank)
            val matched = id != null && !id.startsWith("unmapped:")
            CompareBenchmarkKey(
                id, entry.version, entry.metric, entry.unit, entry.sourceType == "test",
                unmatchedSlot = if (matched) null else slot,
                unmatchedName = if (matched) null else entry.name,
            )
        }

    val left = byKey(0, first)
    val right = byKey(1, second)
    return (left.keys + right.keys).map { key ->
        val firstEntries = left[key].orEmpty()
        val secondEntries = right[key].orEmpty()
        val name = (firstEntries.firstOrNull() ?: secondEntries.first()).name
        CompareBenchmarkGroup(key, name, firstEntries, secondEntries)
    }.sortedWith(compareBy<CompareBenchmarkGroup, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        .thenBy { it.key.version }.thenBy { it.key.metric }.thenBy { it.key.unit }.thenBy { it.key.itemKey })
}

private data class ConfigurationIdentity(
    val dataset: String,
    val harness: String,
    val effort: String?,
    val toolsEnabled: Boolean?,
    val variant: String?,
    val source: String?,
    val sourceType: String?,
    val date: String?,
    val higherIsBetter: Boolean?,
)

private fun String?.isSpecified(): Boolean {
    val value = this?.trim()
    return !value.isNullOrEmpty() && !value.equals("unspecified", ignoreCase = true) &&
        !value.equals("unknown", ignoreCase = true) && !value.equals("n/a", ignoreCase = true)
}

private fun ModelBenchmark.configurationIdentity(): ConfigurationIdentity? {
    if (!dataset.isSpecified() || !harness.isSpecified()) return null
    // 省略的工具／推理／变体不等于关闭或默认。只有同一份出处明确给出数据集和
    // harness 时，才接受两侧同样省略的可选字段；各字段（含来源和日期）仍须精确相等。
    if (!source.isSpecified() && (toolsEnabled == null || !reasoningEffort.isSpecified() || !variant.isSpecified())) return null
    return ConfigurationIdentity(
        requireNotNull(dataset), requireNotNull(harness), reasoningEffort, toolsEnabled,
        variant, source, sourceType, date, higherIsBetter,
    )
}

private fun compareConfigurations(
    group: CompareBenchmarkKey,
    first: List<ModelBenchmark>,
    second: List<ModelBenchmark>,
): List<CompareBenchmarkConfiguration> {
    fun unpaired(slot: Int, index: Int, record: ModelBenchmark) =
        CompareBenchmarkConfiguration("$slot:$index", record.takeIf { slot == 0 }, record.takeIf { slot == 1 })

    if (first.isEmpty() || second.isEmpty() || group.unmatchedSlot != null ||
        !group.version.isSpecified() || !group.metric.isSpecified() || !group.unit.isSpecified()
    ) return first.mapIndexed { index, record -> unpaired(0, index, record) } +
        second.mapIndexed { index, record -> unpaired(1, index, record) }

    val leftKeys = first.map(ModelBenchmark::configurationIdentity)
    val rightByKey = second.indices.groupBy { second[it].configurationIdentity() }
    val leftCounts = leftKeys.groupingBy { it }.eachCount()
    val paired = mutableSetOf<Int>()
    return buildList {
        first.forEachIndexed { index, record ->
            val key = leftKeys[index]
            val counterpart = key?.takeIf { leftCounts[it] == 1 }?.let { rightByKey[it]?.singleOrNull() }
            if (counterpart == null) {
                add(unpaired(0, index, record))
            } else {
                val other = second[counterpart]
                paired += counterpart
                add(CompareBenchmarkConfiguration("0:$index/1:$counterpart", record, other,
                    comparable = record.hasScore() && other.hasScore()))
            }
        }
        second.forEachIndexed { index, record ->
            if (index !in paired) add(unpaired(1, index, record))
        }
    }
}

private fun ModelBenchmark.hasScore(): Boolean = !scoreText.isNullOrBlank() || score?.isFinite() == true
