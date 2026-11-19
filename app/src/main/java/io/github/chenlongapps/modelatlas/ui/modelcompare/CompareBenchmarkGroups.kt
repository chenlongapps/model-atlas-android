package io.github.chenlongapps.modelatlas.ui.modelcompare

import io.github.chenlongapps.modelatlas.data.model.ModelBenchmark

internal data class CompareBenchmarkGroup(
    val key: CompareBenchmarkKey,
    val name: String,
    val first: List<ModelBenchmark>,
    val second: List<ModelBenchmark>,
) {
    val shared: Boolean get() = first.isNotEmpty() && second.isNotEmpty()
}

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
    val itemKey: String get() = listOf(id, version, metric, unit, test.toString(), unmatchedSlot?.toString(), unmatchedName)
        .joinToString("|", prefix = "compare-benchmark:") { if (it == null) "-1:" else "${it.length}:$it" }
}

/** 对齐评测口径，不对齐不同配置的成绩，也不挑选最高分；各侧保留详情中的记录顺序。 */
internal fun compareBenchmarkGroups(
    first: List<ModelBenchmark>?,
    second: List<ModelBenchmark>?,
): List<CompareBenchmarkGroup> {
    fun byKey(slot: Int, entries: List<ModelBenchmark>?) = entries.orEmpty()
        .filter { !it.scoreText.isNullOrBlank() || it.score?.isFinite() == true }
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
