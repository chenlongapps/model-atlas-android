package io.github.chenlongapps.modelatlas.ui.modelcompare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.ModelBenchmark
import io.github.chenlongapps.modelatlas.ui.benchmark.labChartColor
import io.github.chenlongapps.modelatlas.ui.modeldetail.BenchmarkLogo
import io.github.chenlongapps.modelatlas.ui.modeldetail.formatBenchmarkScore

internal fun LazyListScope.compareBenchmarkItems(
    groups: List<CompareBenchmarkGroup>, names: List<String>, labs: List<String?>, onInfo: () -> Unit,
) {
    item("benchmark-heading", contentType = "title") {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CompareInfoHeading(R.string.compare_benchmarks, R.string.benchmark_show_description,
                onInfo.takeIf { groups.any { it.referenceOnly } })
            if (groups.isEmpty()) CompareNote(stringResource(R.string.compare_no_comparable_scores))
        }
    }
    val nameCounts = groups.groupingBy { it.name.lowercase() }.eachCount()
    groups.forEach { group ->
        val key = group.key
        item(key.itemKey, contentType = "benchmark-summary") {
            val title = buildList {
                add(group.name)
                if (nameCounts.getValue(group.name.lowercase()) > 1) add(key.metric)
                if (key.test) add(stringResource(R.string.compare_benchmarks_test))
            }.joinToString(" · ")
            val chart = compareBenchmarkChart(group)
            CompareSection(Modifier.testTag(key.itemKey)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BenchmarkLogo(key.id)
                    Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BenchmarkScore(group.first, title, key.unit, names[0], labs[0], chart, 0)
                    BenchmarkScore(group.second, title, key.unit, names[1], labs[1], chart, 1)
                }
            }
        }
    }
}

@Composable
internal fun CompareBenchmarkInfo(groups: List<CompareBenchmarkGroup>, names: List<String>, onDismiss: () -> Unit) {
    val references = groups.filter { it.referenceOnly }
    val nameCounts = references.groupingBy { it.name.lowercase() }.eachCount()
    CompareInfoDialog(R.string.compare_benchmark_info, onDismiss) {
        Text(stringResource(R.string.compare_benchmark_reference_notice))
        references.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val title = if (nameCounts.getValue(group.name.lowercase()) > 1) {
                    "${group.name} · ${group.key.metric}"
                } else group.name
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (group.first.variant != group.second.variant) {
                    listOf(group.first, group.second).forEachIndexed { index, record ->
                        record.variant?.takeIf(String::isNotBlank)?.let { variant ->
                            Text("${names[index]}：$variant",
                                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.ContentOrLtr))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BenchmarkScore(
    record: ModelBenchmark, title: String, unit: String, name: String, lab: String?,
    chart: CompareBenchmarkChart?, slot: Int,
) {
    val score = "${requireNotNull(formatBenchmarkScore(record))} $unit"
    val scoreLabel = stringResource(R.string.detail_benchmark_score)
    val nameStyle = MaterialTheme.typography.bodyMedium
    val scoreStyle = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.ContentOrLtr)
    val measurer = rememberTextMeasurer()
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$name，$title，$scoreLabel：$score" },
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val textWidth = measurer.measure(name, nameStyle).size.width + measurer.measure(score, scoreStyle).size.width
            if (textWidth + with(LocalDensity.current) { 12.dp.roundToPx() } <= constraints.maxWidth) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = nameStyle)
                    Text(score, style = scoreStyle)
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, style = nameStyle)
                    Text(score, Modifier.align(Alignment.End), style = scoreStyle)
                }
            }
        }
        if (chart != null) {
            val end = chart.range.fraction(requireNotNull(record.chartScore()))
            BenchmarkScoreBar(chart.zero, end, lab, slot)
        }
    }
}

@Composable
private fun BenchmarkScoreBar(zero: Float, end: Float, lab: String?, slot: Int) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val indicator = labChartColor(lab, track)
    BoxWithConstraints(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(track)
        .testTag("compare-benchmark-track-$slot")) {
        if (end != zero) {
            Box(Modifier.align(Alignment.CenterStart).offset(x = maxWidth * minOf(zero, end))
                .width(maxWidth * kotlin.math.abs(end - zero)).fillMaxHeight().clip(CircleShape).background(indicator)
                .testTag("compare-benchmark-fill-$slot"))
        }
    }
}
