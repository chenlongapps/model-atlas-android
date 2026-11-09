package io.github.chenlongapps.modelatlas.ui.modeldetail

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.ModelBenchmark
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme

internal fun LazyListScope.benchmarkItems(model: ModelDetail) {
    val benchmarks = model.benchmarks
    item(key = "benchmark-notice") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (benchmarks.isNullOrEmpty()) {
                Text(stringResource(if (benchmarks == null) R.string.detail_benchmarks_missing else R.string.detail_benchmarks_empty),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(stringResource(R.string.detail_benchmarks_notice), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (benchmarks?.any { it.sourceType == "test" } == true) {
                Text(stringResource(R.string.detail_benchmark_test_notice), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
    // 定义 ID 不是成绩 ID；名称 A–Z、同名数值降序，平分稳定，条目 key 仍使用源下标。
    benchmarks?.let(::alphabeticalBenchmarkEntries)?.forEach { (index, benchmark) ->
        item(key = "benchmark:$index") { ModelBenchmarkCard(benchmark, Modifier.testTag("model-benchmark:$index")) }
    }
}

@Composable
internal fun ModelBenchmarkCard(benchmark: ModelBenchmark, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BenchmarkHeader(benchmark)
            BenchmarkConditions(benchmark)
            benchmark.description?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 按完整分数、名称与字号测量；空间不够时分数下移，不缩小字号或截断原值。 */
@Composable
private fun BenchmarkHeader(benchmark: ModelBenchmark) {
    val name = benchmark.name.takeIf(String::isNotBlank) ?: stringResource(R.string.no_data)
    val score = formatBenchmarkScore(benchmark) ?: stringResource(R.string.no_data)
    val unit = benchmark.unit?.takeIf(String::isNotBlank)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val nameStyle = MaterialTheme.typography.titleMedium
    val scoreStyle = if (benchmark.score == null && benchmark.scoreText.isNullOrBlank()) MaterialTheme.typography.bodyMedium
        else MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
    val scoreWidth = with(density) {
        maxOf(measurer.measure(score, scoreStyle, softWrap = false).size.width,
            unit?.let { measurer.measure(it, MaterialTheme.typography.labelSmall, softWrap = false).size.width } ?: 0).toDp()
    }
    val nameWidth = with(density) { measurer.measure(name, nameStyle, softWrap = false).size.width.toDp() }
        .coerceAtMost(120.dp * density.fontScale)
    val identity: @Composable (Modifier) -> Unit = { modifier ->
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BenchmarkLogo(benchmark.benchmarkId)
            Text(name, Modifier.weight(1f).semantics { heading() }, style = nameStyle)
        }
    }
    val value: @Composable (Modifier) -> Unit = { modifier ->
        val label = stringResource(R.string.detail_benchmark_score)
        Column(modifier.semantics(mergeDescendants = true) { contentDescription = "$name，$label：$score${unit?.let { "，$it" }.orEmpty()}" },
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(score, style = scoreStyle, color = if (benchmark.score == null && benchmark.scoreText.isNullOrBlank())
                MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
            if (unit != null) Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= benchmarkLogoWidth(benchmark.benchmarkId) + 12.dp + nameWidth + 16.dp + scoreWidth) {
            Row(Modifier.fillMaxWidth().testTag("benchmark-header:inline"), horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                identity(Modifier.weight(1f))
                value(Modifier.width(scoreWidth))
            }
        } else {
            Column(Modifier.fillMaxWidth().testTag("benchmark-header:stacked"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                identity(Modifier.fillMaxWidth())
                value(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun BenchmarkConditions(benchmark: ModelBenchmark) {
    val conditions = listOfNotNull(
        benchmark.version?.takeIf(String::isNotBlank)?.let { stringResource(R.string.detail_benchmark_version) to it },
        benchmark.reasoningEffort?.takeIf(String::isNotBlank)?.let { stringResource(R.string.detail_benchmark_reasoning) to it },
        benchmark.variant?.takeIf(String::isNotBlank)?.let { stringResource(R.string.detail_benchmark_variant) to it },
        benchmark.toolsEnabled?.let { stringResource(R.string.detail_benchmark_tools) to
            stringResource(if (it) R.string.detail_benchmark_tools_enabled else R.string.detail_benchmark_tools_disabled) },
    )
    if (conditions.isNotEmpty()) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            conditions.forEach { (label, value) ->
                Text(value, Modifier.semantics { contentDescription = "$label：$value" },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Preview(name = "Benchmark · Light", showBackground = true, widthDp = 400)
@Preview(name = "Benchmark · Dark", showBackground = true, widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Benchmark · Narrow", showBackground = true, widthDp = 320)
@Preview(name = "Benchmark · Large font", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun ModelBenchmarkPreview() {
    ModelAtlasTheme(dynamicColor = false) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ModelBenchmarkCard(ModelBenchmark("GDPval-AA", null, version = "2.1", reasoningEffort = "Adaptive/Max",
                variant = "without tools", description = "虚构预览：衡量专业知识工作中的任务表现。", scoreText = "899 ± 18",
                benchmarkId = "gdpval-aa-v2.1", unit = "Elo points"))
            ModelBenchmarkCard(ModelBenchmark("SWE-bench Pro", 0.0, benchmarkId = "swe-bench-pro-v2"))
        }
    }
}
