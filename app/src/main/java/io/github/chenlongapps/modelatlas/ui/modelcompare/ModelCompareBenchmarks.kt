package io.github.chenlongapps.modelatlas.ui.modelcompare

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.ModelBenchmark
import io.github.chenlongapps.modelatlas.ui.modeldetail.formatBenchmarkScore

internal fun LazyListScope.compareBenchmarkItems(
    shared: List<CompareBenchmarkGroup>, singleSide: List<CompareBenchmarkGroup>,
    names: List<String>, layout: CompareLayout,
    expandedDetails: Set<String>, onToggleDetail: (String) -> Unit,
    isRecordExpanded: (String) -> Boolean, onToggleRecord: (String) -> Unit,
) {
    item("benchmark-heading", contentType = "title") {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CompareHeading(R.string.compare_benchmarks)
            if (shared.isEmpty()) {
                CompareNote(stringResource(if (singleSide.isEmpty()) R.string.compare_benchmarks_empty
                    else R.string.compare_benchmarks_no_shared))
            }
        }
    }
    fun addGroup(group: CompareBenchmarkGroup) {
        val key = group.key.itemKey
        val expanded = key in expandedDetails
        item(key, contentType = "benchmark-summary") {
            BenchmarkSummary(group, names, layout, expanded, onToggleDetail)
        }
        if (expanded) group.configurations.forEach { configuration ->
            val configKey = "$key/config:${configuration.key}"
            item(configKey, contentType = "benchmark-configuration") {
                BenchmarkConfiguration(group, configuration, names, layout, configKey, isRecordExpanded(configKey),
                    onToggleRecord, Modifier.animateItem(fadeInSpec = tween(160), placementSpec = null, fadeOutSpec = null))
            }
        }
    }
    shared.forEach(::addGroup)
    if (singleSide.isNotEmpty()) {
        val expanded = "single-benchmarks" in expandedDetails
        item("benchmark-single-side", contentType = "toggle") {
            CompareToggleCard("single-benchmarks", R.string.compare_benchmarks_single, expanded, onToggleDetail)
        }
        if (expanded) singleSide.forEach(::addGroup)
    }
}

@Composable
private fun BenchmarkSummary(
    group: CompareBenchmarkGroup, names: List<String>, layout: CompareLayout,
    expanded: Boolean, onToggle: (String) -> Unit,
) {
    val key = group.key.itemKey
    CompareSection(Modifier.testTag(key)) {
        CompareToggle(key, group.name, expanded, onToggle, isHeading = true)
        BenchmarkIdentity(group.key)
        if (group.key.test) CompareNote(stringResource(R.string.compare_benchmarks_test))

        val pair = group.representative
        if (pair != null) {
            BenchmarkPairScores(group.name, pair, names, layout)
        } else if (group.shared) {
            val comparableCount = group.comparableCount
            CompareNote(if (comparableCount > 1) {
                pluralStringResource(R.plurals.compare_comparable_configurations, comparableCount, comparableCount)
            } else stringResource(R.string.compare_no_comparable_scores))
        }
        if (!group.shared) {
            val slot = if (group.first.isNotEmpty()) 0 else 1
            val count = group.first.size + group.second.size
            CompareNote("${stringResource(R.string.compare_slot_label, slot + 1)} · ${recordCount(count)}")
        } else if (group.first.size > 1 || group.second.size > 1) {
            CompareRow(stringResource(R.string.compare_record_count_label), CompareValue(recordCount(group.first.size)),
                CompareValue(recordCount(group.second.size)), names, layout, divider = false)
        }
    }
}

@Composable
private fun recordCount(count: Int): String = pluralStringResource(R.plurals.compare_benchmark_records, count, count)

@Composable
private fun BenchmarkIdentity(key: CompareBenchmarkKey) {
    val unknown = stringResource(R.string.compare_benchmarks_version_missing)
    val version = key.version?.takeIf { it.isNotBlank() && !it.equals("unspecified", ignoreCase = true) } ?: unknown
    val metric = key.metric?.takeIf(String::isNotBlank) ?: stringResource(R.string.compare_benchmark_metric_missing)
    val unit = key.unit?.takeIf(String::isNotBlank)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CompareNote(stringResource(R.string.compare_benchmark_summary_version, version))
        CompareNote(if (unit == null) metric else "$metric ($unit)")
    }
}

@Composable
private fun BenchmarkConfiguration(
    group: CompareBenchmarkGroup, configuration: CompareBenchmarkConfiguration, names: List<String>,
    layout: CompareLayout, key: String, expanded: Boolean, onToggle: (String) -> Unit, modifier: Modifier = Modifier,
) {
    val first = configuration.first
    val second = configuration.second
    val record = first ?: requireNotNull(second)
    CompareSection(modifier) {
        Text(group.name, style = MaterialTheme.typography.titleSmall)
        BenchmarkIdentity(group.key)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val conditions = benchmarkConditions(record, includeMissing = false)
            if (conditions.isEmpty()) CompareNote(stringResource(R.string.compare_conditions_missing))
            conditions.forEach { (label, value) -> CompareNote("${stringResource(label)}：$value") }
        }
        if (first != null && second != null) {
            BenchmarkPairScores(group.name, configuration, names, layout)
        } else {
            if (group.shared) CompareNote(stringResource(R.string.compare_configuration_unpaired))
            val slot = if (first != null) 0 else 1
            CompareCell(benchmarkScoreLabel(group.name), names[slot], benchmarkValue(record),
                Modifier.fillMaxWidth(), slot = slot)
        }
        CompareDisclosure(key, R.string.compare_raw_record, expanded, onToggle) {
            first?.let { RawBenchmarkRecord(it, 0, names[0]) }
            second?.let {
                if (first != null) CompareDivider()
                RawBenchmarkRecord(it, 1, names[1])
            }
        }
    }
}

@Composable
private fun benchmarkScoreLabel(benchmark: String): String = "$benchmark，${stringResource(R.string.detail_benchmark_score)}"

@Composable
private fun BenchmarkPairScores(
    benchmark: String, configuration: CompareBenchmarkConfiguration, names: List<String>, layout: CompareLayout,
) {
    CompareRow(stringResource(R.string.detail_benchmark_score), benchmarkValue(requireNotNull(configuration.first)),
        benchmarkValue(requireNotNull(configuration.second)), names, layout, divider = false,
        spokenLabel = benchmarkScoreLabel(benchmark))
}

@Composable
private fun benchmarkValue(record: ModelBenchmark): CompareValue {
    val score = formatBenchmarkScore(record) ?: return MissingCompareValue(R.string.compare_benchmarks_no_score)
    val unit = record.unit?.takeIf(String::isNotBlank)
    return CompareValue(score, if (unit == null) score else "$score $unit", prominent = true)
}

@Composable
private fun benchmarkConditions(record: ModelBenchmark, includeMissing: Boolean): List<Pair<Int, String>> {
    val noData = stringResource(R.string.no_data)
    return listOf(
        R.string.detail_benchmark_reasoning to record.reasoningEffort,
        R.string.detail_benchmark_tools to record.toolsEnabled?.let {
            stringResource(if (it) R.string.detail_benchmark_tools_enabled else R.string.detail_benchmark_tools_disabled)
        },
        R.string.detail_benchmark_dataset to record.dataset,
        R.string.detail_benchmark_harness to record.harness,
        R.string.detail_benchmark_variant to record.variant,
    ).mapNotNull { (label, value) -> (value?.takeIf(String::isNotBlank) ?: noData.takeIf { includeMissing })?.let { label to it } }
}

@Composable
private fun RawBenchmarkRecord(record: ModelBenchmark, slot: Int, name: String) {
    val noData = stringResource(R.string.no_data)
    SelectionContainer {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${stringResource(R.string.compare_slot_label, slot + 1)} · $name", style = MaterialTheme.typography.titleSmall)
            val extraScoreText = record.scoreText?.takeIf { record.score != null && it.isNotBlank() }
            val fields = listOf(R.string.compare_raw_score to (record.score?.toString() ?: record.scoreText)) +
                listOfNotNull(extraScoreText?.let { R.string.compare_raw_score_text to it }) + listOf(
                R.string.detail_benchmark_version to record.version,
                R.string.detail_benchmark_metric to record.metric,
                R.string.detail_benchmark_unit to record.unit,
                R.string.detail_benchmark_direction to record.higherIsBetter?.let {
                    stringResource(if (it) R.string.detail_benchmark_higher else R.string.detail_benchmark_lower)
                },
            ) + benchmarkConditions(record, includeMissing = true) + listOf(
                R.string.detail_benchmark_source to record.source,
                R.string.detail_benchmark_date to record.date,
                R.string.compare_benchmark_source_type to record.sourceType,
                R.string.compare_benchmark_id to record.benchmarkId,
                R.string.compare_benchmark_name to record.name,
            )
            fields.forEach { (label, value) ->
                Text("${stringResource(label)}：${value?.takeIf(String::isNotBlank) ?: noData}",
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr))
            }
            record.description?.takeIf(String::isNotBlank)?.let { CompareNote(it) }
        }
    }
}
