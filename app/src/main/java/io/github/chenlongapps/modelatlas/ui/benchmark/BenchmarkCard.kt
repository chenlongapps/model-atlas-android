package io.github.chenlongapps.modelatlas.ui.benchmark

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.BenchmarkBoard
import io.github.chenlongapps.modelatlas.data.model.BenchmarkBoardEntry
import io.github.chenlongapps.modelatlas.ui.modeldetail.BenchmarkLogo
import io.github.chenlongapps.modelatlas.ui.modeldetail.benchmarkLogoResource
import io.github.chenlongapps.modelatlas.ui.modeldetail.benchmarkLogoWidth
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
internal fun BenchmarkCard(board: BenchmarkBoard, onClick: () -> Unit, modifier: Modifier = Modifier, showVersion: Boolean = false) {
    val top = remember(board.rankings) { board.rankings.take(3) }
    val range = remember(top, board.minScore, board.maxScore) {
        benchmarkChartRange(top.mapNotNull { it.score }, board.minScore, board.maxScore)
    }
    val metadata = "${stringResource(R.string.benchmark_version, board.version)}，${stringResource(R.string.benchmark_metric, board.metric)}"
    Card(
        modifier = modifier.fillMaxWidth().benchmarkCardContainer(board.id)
            .testTag("benchmark-card:${board.id}").semantics { stateDescription = metadata }
            .clickable(interactionSource = null, indication = null,
                onClickLabel = stringResource(R.string.benchmark_open), role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BenchmarkCatalogLogo(board.benchmarkId, Modifier.benchmarkSharedElement(board.id, BenchmarkIdentityRole.LOGO))
                Text(if (showVersion) stringResource(R.string.benchmark_name_version, board.name, board.version) else board.name,
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).wrapContentWidth(Alignment.Start)
                        .benchmarkSharedElement(board.id, BenchmarkIdentityRole.NAME).semantics { heading() })
            }
            if (top.isEmpty()) {
                Text(stringResource(R.string.benchmark_no_numeric), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                BenchmarkCompactResults(board.id, top, board.unit, range)
            }
        }
    }
}

/** 仅总览对齐模型页 56dp 白底/14dp 圆角/6dp 留白；宽字标保留宽底座，详情 Logo 不变。 */
@Composable
private fun BenchmarkCatalogLogo(benchmarkId: String, modifier: Modifier = Modifier) {
    val width = if (benchmarkLogoWidth(benchmarkId) > 40.dp) 88.dp else 56.dp
    Box(modifier.size(width, 56.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
        .testTag("benchmark-catalog-logo:$benchmarkId")) {
        Image(painterResource(benchmarkLogoResource(benchmarkId)), contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(6.dp).testTag("benchmark-catalog-logo-image:$benchmarkId"))
    }
}

/** 每项名称/分数在上、通栏柱条在下；完整文字实测放不下时才将分数下移。 */
@Composable
private fun BenchmarkCompactResults(boardId: String, entries: List<BenchmarkBoardEntry>, unit: String?, range: BenchmarkChartRange) {
    val nameStyle = MaterialTheme.typography.bodyMedium
    val scoreStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    val scoreColor = MaterialTheme.colorScheme.primary
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scores = entries.map { formatCompactBoardScore(it, unit) }
    val scaleDescription = stringResource(R.string.benchmark_scale_accessibility,
        formatBoardUnit(formatChartTick(range.start), unit), formatBoardUnit(formatChartTick(range.end), unit))
    BoxWithConstraints(Modifier.fillMaxWidth().semantics { contentDescription = scaleDescription }) {
        val availableWidth = with(density) { maxWidth.toPx() }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            entries.forEachIndexed { index, entry ->
                val name = formatBoardModelName(entry)
                val score = scores[index]
                val spokenScore = formatBoardScore(entry, unit)
                val description = benchmarkResultDescription(entry, spokenScore)
                val requiredWidth = measurer.measure(name, nameStyle).size.width + measurer.measure(score, scoreStyle).size.width
                val inline = with(density) { requiredWidth + 16.dp.toPx() <= availableWidth }
                Column(Modifier.fillMaxWidth().testTag("benchmark-result:${entry.scoreId}").clearAndSetSemantics { contentDescription = description },
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (inline) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = nameStyle, modifier = Modifier.weight(1f).wrapContentWidth(Alignment.Start)
                                .benchmarkResultText(boardId, entry.modelId, BenchmarkResultRole.NAME).testTag("benchmark-name:${entry.scoreId}"))
                            Text(score, style = scoreStyle, color = scoreColor, modifier = Modifier
                                .benchmarkResultText(boardId, entry.modelId, BenchmarkResultRole.SCORE).testTag("benchmark-score:${entry.scoreId}"))
                        }
                    } else {
                        Text(name, style = nameStyle, modifier = Modifier
                            .benchmarkResultText(boardId, entry.modelId, BenchmarkResultRole.NAME).testTag("benchmark-name:${entry.scoreId}"))
                        Text(score, style = scoreStyle, color = scoreColor, modifier = Modifier.align(Alignment.End)
                            .benchmarkResultText(boardId, entry.modelId, BenchmarkResultRole.SCORE).testTag("benchmark-score:${entry.scoreId}"))
                    }
                    BenchmarkResultBar(entry, range, 6.dp, boardId, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** 仅总览将明示 points 简写为 pts；读屏、完整榜单及其他单位保持来源口径。 */
internal fun formatCompactBoardScore(entry: BenchmarkBoardEntry, unit: String?): String =
    formatBoardScore(entry, if (unit == "points") "pts" else unit)

/** 松开 weight 的最小宽度后，也收紧真实段落宽度，避免宽段落缓存与文字边界不一致。 */
@Composable
private fun Modifier.benchmarkResultText(boardId: String?, modelId: String, role: BenchmarkResultRole): Modifier =
    benchmarkSharedResult(boardId, modelId, role).width(IntrinsicSize.Max)

@Composable
private fun BenchmarkResultBar(entry: BenchmarkBoardEntry, range: BenchmarkChartRange, height: Dp,
    boardId: String?, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier.height(height).testTag("benchmark-bar:${entry.modelId}")) {
        Box(Modifier.fillMaxSize().benchmarkSharedResult(boardId, entry.modelId, BenchmarkResultRole.TRACK)
            .clip(RoundedCornerShape(50)).background(track).testTag("benchmark-track:${entry.modelId}"))
        Box(Modifier.fillMaxWidth(range.fraction(requireNotNull(entry.score))).fillMaxHeight()
            .benchmarkSharedResult(boardId, entry.modelId, BenchmarkResultRole.FILL)
            .clip(RoundedCornerShape(50)).background(labChartColor(entry.lab, track)).testTag("benchmark-fill:${entry.modelId}"))
    }
}

@Composable
internal fun BenchmarkIdentity(board: BenchmarkBoard, onInfo: (() -> Unit)? = null, sharedElementsEnabled: Boolean = true) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val version = stringResource(R.string.benchmark_version, board.version)
    val nameStyle = MaterialTheme.typography.titleMedium
    val metadataStyle = MaterialTheme.typography.bodySmall
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("benchmark-introduction").heightIn(min = 64.dp).padding(vertical = 8.dp)) {
        val textWidth = with(density) {
            (maxWidth - benchmarkLogoWidth(board.benchmarkId) - 12.dp - if (onInfo != null) 60.dp else 0.dp).toPx()
        }
        val longestWord = board.name.split(Regex("\\s+")).maxOfOrNull { measurer.measure(it, nameStyle).size.width } ?: 0
        val inline = maxOf(longestWord, measurer.measure(version, metadataStyle).size.width) <= textWidth
        if (inline) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BenchmarkLogo(board.benchmarkId, Modifier.benchmarkSharedElement(board.id, BenchmarkIdentityRole.LOGO, sharedElementsEnabled))
                BenchmarkIdentityText(board, Modifier.weight(1f), sharedElementsEnabled)
                BenchmarkInfoButton(onInfo)
            }
        } else {
            // 不让宽字标与 48dp 信息入口挤窄长名称／版本，文字独占整行。
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    BenchmarkLogo(board.benchmarkId, Modifier.benchmarkSharedElement(board.id, BenchmarkIdentityRole.LOGO, sharedElementsEnabled))
                    BenchmarkInfoButton(onInfo)
                }
                BenchmarkIdentityText(board, Modifier.fillMaxWidth(), sharedElementsEnabled)
            }
        }
    }
}

@Composable
private fun BenchmarkIdentityText(board: BenchmarkBoard, modifier: Modifier, sharedElementsEnabled: Boolean) {
    val version = stringResource(R.string.benchmark_version, board.version)
    val category = benchmarkCategory(board.category)
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(board.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier
            .benchmarkSharedElement(board.id, BenchmarkIdentityRole.NAME, sharedElementsEnabled).semantics { heading() })
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fits = measurer.measure(version, style).size.width + measurer.measure(category, style).size.width +
                with(density) { 8.dp.toPx() } <= with(density) { maxWidth.toPx() }
            if (fits) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(version, style = style, color = color)
                Text(category, style = style, color = color)
            } else Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(version, style = style, color = color, modifier = Modifier.fillMaxWidth())
                Text(category, style = style, color = color, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun BenchmarkInfoButton(onInfo: (() -> Unit)?) {
    if (onInfo != null) IconButton(onClick = onInfo, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(R.drawable.ic_info), contentDescription = stringResource(R.string.benchmark_show_description),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun BenchmarkResultRow(
    entry: BenchmarkBoardEntry,
    unit: String?,
    range: BenchmarkChartRange?,
    modifier: Modifier = Modifier,
    sharedBoardId: String? = null,
) {
    val name = formatBoardModelName(entry)
    val score = formatBoardScore(entry, unit)
    val description = benchmarkResultDescription(entry, score)
    val textMeasurer = rememberTextMeasurer()
    val nameStyle = MaterialTheme.typography.titleSmall
    val scoreStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    Column(modifier.fillMaxWidth().testTag("benchmark-result:${entry.scoreId}")
        .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val required = textMeasurer.measure(name, nameStyle).size.width + textMeasurer.measure(score, scoreStyle).size.width
            val rankWidth = entry.rank?.let { textMeasurer.measure(it.toString(), MaterialTheme.typography.bodyMedium).size.width } ?: 0
            val fits = with(LocalDensity.current) { required + rankWidth + 32.dp.toPx() <= maxWidth.toPx() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                entry.rank?.let { Text(it.toString(), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp)) }
                if (fits) {
                    Text(name, style = nameStyle, modifier = Modifier.weight(1f).wrapContentWidth(Alignment.Start)
                        .benchmarkResultText(sharedBoardId, entry.modelId, BenchmarkResultRole.NAME).testTag("benchmark-name:${entry.scoreId}"))
                    Text(score, style = scoreStyle, modifier = Modifier
                        .benchmarkResultText(sharedBoardId, entry.modelId, BenchmarkResultRole.SCORE).testTag("benchmark-score:${entry.scoreId}"))
                } else Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, style = nameStyle, modifier = Modifier
                        .benchmarkResultText(sharedBoardId, entry.modelId, BenchmarkResultRole.NAME).testTag("benchmark-name:${entry.scoreId}"))
                    Text(score, style = scoreStyle, modifier = Modifier.align(Alignment.End)
                        .benchmarkResultText(sharedBoardId, entry.modelId, BenchmarkResultRole.SCORE).testTag("benchmark-score:${entry.scoreId}"))
                }
            }
        }
        if (range != null && entry.score != null) {
            BenchmarkResultBar(entry, range, 8.dp, sharedBoardId, Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun BenchmarkScale(range: BenchmarkChartRange, unit: String?) {
    val start = formatBoardUnit(formatChartTick(range.start), unit)
    val end = formatBoardUnit(formatChartTick(range.end), unit)
    val description = stringResource(R.string.benchmark_scale_accessibility, start, end)
    Column(Modifier.fillMaxWidth().testTag("benchmark-scale").clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(start, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(end, modifier = Modifier.weight(1f), textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(if (range.start.signum() == 0) R.string.benchmark_zero_scale else R.string.benchmark_local_scale),
            modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun formatBoardScore(entry: BenchmarkBoardEntry, unit: String?): String = entry.scoreText?.takeIf(String::isNotBlank)
    ?: entry.score?.takeIf(Double::isFinite)?.let {
        formatBoardUnit(BigDecimal.valueOf(it).setScale(1, RoundingMode.HALF_UP).toPlainString(), unit)
    } ?: "暂无数据"

private fun formatBoardUnit(value: String, unit: String?): String = when (unit) {
    "percent", "%" -> "$value%"
    null, "" -> value
    else -> "$value $unit"
}

internal fun formatBoardModelName(entry: BenchmarkBoardEntry): String =
    entry.reasoningEffort?.trim()?.takeIf(String::isNotEmpty)?.let { "${entry.modelName} · $it" } ?: entry.modelName

@Composable
private fun benchmarkResultDescription(entry: BenchmarkBoardEntry, score: String): String {
    val description = if (entry.rank != null) stringResource(R.string.benchmark_rank_accessibility, entry.rank, entry.modelName, score)
        else stringResource(R.string.benchmark_score_accessibility, entry.modelName, score)
    val effort = entry.reasoningEffort?.trim()?.takeIf(String::isNotEmpty)
        ?: return description
    return "$description，${stringResource(R.string.benchmark_condition_effort, effort)}"
}

@Composable
private fun benchmarkCategory(category: String?): String = stringResource(when (category) {
    "composite" -> R.string.benchmark_category_composite
    "agent" -> R.string.benchmark_category_agent
    "coding" -> R.string.benchmark_category_coding
    "reasoning" -> R.string.benchmark_category_reasoning
    "multimodal" -> R.string.benchmark_category_multimodal
    "professional-work" -> R.string.benchmark_category_professional
    "computer-use" -> R.string.benchmark_category_computer
    "science" -> R.string.benchmark_category_science
    else -> R.string.benchmark_category_other
})

// 合成成绩复用 BenchmarkScreen.kt 的 benchmarkPreviewBoard()，仅用于设计预览。
@Preview(name = "Benchmark card · Light", showBackground = true, widthDp = 400)
@Preview(name = "Benchmark card · Dark", showBackground = true, widthDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Benchmark card · Narrow", showBackground = true, widthDp = 320)
@Preview(name = "Benchmark card · Large font", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun BenchmarkCardPreview() {
    val board = benchmarkPreviewBoard()
    ModelAtlasTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BenchmarkCard(board, onClick = {})
                BenchmarkCard(board, onClick = {}, showVersion = true)
                BenchmarkCard(board.copy(rankings = emptyList()), onClick = {})
            }
        }
    }
}

@Preview(name = "Benchmark leaderboard rows · Light", showBackground = true, widthDp = 400)
@Preview(name = "Benchmark leaderboard rows · Dark", showBackground = true, widthDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Benchmark leaderboard rows · Narrow", showBackground = true, widthDp = 320)
@Preview(name = "Benchmark leaderboard rows · Large font", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun BenchmarkLeaderboardRowsPreview() {
    val board = benchmarkPreviewBoard()
    val range = benchmarkChartRange(board.rankings.mapNotNull { it.score }, board.minScore, board.maxScore)
    ModelAtlasTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BenchmarkIdentity(board, onInfo = {})
                BenchmarkScale(range, board.unit)
                board.rankings.forEach { entry ->
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                        BenchmarkResultRow(entry, board.unit, range, Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}
