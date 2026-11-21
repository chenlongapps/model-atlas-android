package io.github.chenlongapps.modelatlas.ui.modelcompare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.ModelBenchmark
import io.github.chenlongapps.modelatlas.ui.modeldetail.formatBenchmarkScore

internal fun LazyListScope.compareBenchmarkItems(
    groups: List<CompareBenchmarkGroup>, modelNames: List<String>,
    expandedDetails: List<String>, onToggleDetail: (String) -> Unit,
) {
    val (shared, singleSide) = groups.partition { it.shared }
    item("benchmark-heading") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.compare_benchmarks), Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge)
            val note = when {
                groups.isEmpty() -> R.string.compare_benchmarks_empty
                shared.isEmpty() -> R.string.compare_benchmarks_no_shared
                else -> null
            }
            if (note != null) {
                Text(stringResource(note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    shared.forEach { group ->
        item(group.key.itemKey) { BenchmarkPair(group, modelNames) }
    }
    if (singleSide.isNotEmpty()) {
        item("benchmark-single-side") {
            CompareToggleCard("single-benchmarks", R.string.compare_benchmarks_single, expandedDetails, onToggleDetail)
        }
        if ("single-benchmarks" in expandedDetails) singleSide.forEach { group ->
            item(group.key.itemKey) { BenchmarkPair(group, modelNames) }
        }
    }
}

@Composable
private fun BenchmarkPair(group: CompareBenchmarkGroup, modelNames: List<String>) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().testTag(group.key.itemKey)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(group.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            val version = group.key.version?.takeIf(String::isNotBlank)
                ?: stringResource(R.string.compare_benchmarks_version_missing)
            val versionLabel = stringResource(R.string.detail_benchmark_version)
            val metricLabel = stringResource(R.string.detail_benchmark_metric)
            val identity = listOfNotNull(
                "$versionLabel：$version",
                group.key.metric?.takeIf(String::isNotBlank)?.let { "$metricLabel：$it" },
            ).joinToString(" · ")
            Text(identity, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (group.key.test) {
                Text(stringResource(R.string.compare_benchmarks_test), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 240.dp * LocalDensity.current.fontScale) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 表头共用一行，模型名换行时两侧首条成绩仍从同一高度开始。
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            BenchmarkModelName(modelNames[0], Modifier.weight(1f))
                            BenchmarkModelName(modelNames[1], Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            BenchmarkScores(group.name, modelNames[0], group.first, Modifier.weight(1f))
                            BenchmarkScores(group.name, modelNames[1], group.second, Modifier.weight(1f))
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(group.first, group.second).forEachIndexed { slot, records ->
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                BenchmarkModelName(modelNames[slot])
                                BenchmarkScores(group.name, modelNames[slot], records, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BenchmarkModelName(name: String, modifier: Modifier = Modifier) {
    Text(name, modifier, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BenchmarkScores(benchmarkName: String, modelName: String, records: List<ModelBenchmark>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (records.isEmpty()) {
            val missing = stringResource(R.string.compare_benchmarks_no_score)
            Text(missing, Modifier.semantics { contentDescription = "$modelName，$benchmarkName：$missing" },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        records.forEachIndexed { index, record ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            BenchmarkScore(benchmarkName, modelName, record)
        }
    }
}

@Composable
private fun BenchmarkScore(benchmarkName: String, modelName: String, record: ModelBenchmark) {
    val score = listOfNotNull(formatBenchmarkScore(record), record.unit?.takeIf(String::isNotBlank)).joinToString(" ")
    val conditions = listOfNotNull(
        record.reasoningEffort?.takeIf(String::isNotBlank)?.let { stringResource(R.string.detail_benchmark_reasoning) to it },
        record.variant?.takeIf(String::isNotBlank)?.let { stringResource(R.string.detail_benchmark_variant) to it },
        record.toolsEnabled?.let { stringResource(R.string.detail_benchmark_tools) to
            stringResource(if (it) R.string.detail_benchmark_tools_enabled else R.string.detail_benchmark_tools_disabled) },
        record.dataset?.takeIf(String::isNotBlank)?.let { stringResource(R.string.detail_benchmark_dataset) to it },
        record.harness?.takeIf(String::isNotBlank)?.let { stringResource(R.string.detail_benchmark_harness) to it },
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val scoreLabel = stringResource(R.string.detail_benchmark_score)
        Text(score, Modifier.semantics { contentDescription = "$modelName，$benchmarkName，$scoreLabel：$score" },
            style = MaterialTheme.typography.titleMedium)
        conditions.forEach { (label, value) ->
            Text("$label：$value", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
