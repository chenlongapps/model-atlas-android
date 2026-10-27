package io.github.chenlongapps.modelatlas.ui.modeldetail

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelDetailMetadata
import io.github.chenlongapps.modelatlas.ui.model.ModelInfoBadge
import io.github.chenlongapps.modelatlas.ui.modellist.formatModality
import io.github.chenlongapps.modelatlas.ui.modellist.formatTokenCount
import io.github.chenlongapps.modelatlas.ui.modellist.modalityIcon

/** 概览只读取规范模型字段；不影响公共头部、服务报价和评测的布局。 */
internal fun LazyListScope.overviewItems(model: ModelDetail) {
    item(key = "parameters") {
        OverviewSection(stringResource(R.string.detail_parameters), R.drawable.ic_nav_models_outline, "parameters") {
            OverviewMetrics(model.metadata)
            OverviewDivider()
            OverviewDates(model.metadata)
        }
    }
    item(key = "modalities") {
        OverviewSection(stringResource(R.string.detail_modalities), R.drawable.ic_modality, "modalities") {
            OverviewModalityRows(model.metadata)
        }
    }
    item(key = "weights") {
        OverviewSection(stringResource(R.string.detail_weights), R.drawable.ic_shield, "weights") {
            val status = when (model.metadata.openWeights) {
                true -> stringResource(R.string.model_list_open_weights_yes)
                false -> stringResource(R.string.model_list_open_weights_no)
                null -> stringResource(R.string.no_data)
            }
            val label = stringResource(R.string.model_filter_weights)
            Column(Modifier.testTag("overview-weights-status").semantics(mergeDescendants = true) {
                contentDescription = "$label：$status"
            }) {
                ModelInfoBadge(
                    text = if (model.metadata.openWeights == null) {
                        stringResource(R.string.model_list_open_weights, status)
                    } else status,
                    highlighted = model.metadata.openWeights == true,
                )
            }
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.detail_license), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(model.license?.takeIf(String::isNotBlank) ?: stringResource(R.string.no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (model.license.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun OverviewSection(title: String, @DrawableRes icon: Int, tag: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("overview-section:$tag"), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

private data class OverviewField(
    val tag: String,
    val label: String,
    val value: String,
    val hasTokens: Boolean = false,
    val missing: Boolean = false,
)

@Composable
private fun OverviewMetrics(metadata: ModelDetailMetadata) {
    val noData = stringResource(R.string.no_data)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        OverviewFields(listOf(
            OverviewField("context", stringResource(R.string.model_list_context_label),
                metadata.contextLimitTokens?.let(::formatTokenCount) ?: noData,
                hasTokens = metadata.contextLimitTokens != null, missing = metadata.contextLimitTokens == null),
            OverviewField("output", stringResource(R.string.model_list_output_limit_label),
                metadata.outputLimitTokens?.let(::formatTokenCount) ?: noData,
                hasTokens = metadata.outputLimitTokens != null, missing = metadata.outputLimitTokens == null),
        ), prominent = true, modifier = Modifier.testTag("overview-metrics"))
        OverviewFields(listOf(
            OverviewField("input", stringResource(R.string.detail_input_limit),
                metadata.inputLimitTokens?.let(::formatTokenCount) ?: noData,
                hasTokens = metadata.inputLimitTokens != null, missing = metadata.inputLimitTokens == null),
            // 当前来源没有参数量，不从模型名称推断或新增数据来源。
            OverviewField("parameters", stringResource(R.string.detail_parameter_count), noData, missing = true),
        ))
    }
}

/** 只有两个核心参数使用大数字；其余字段轻量排版，不绘制内层卡片或指标图标。 */
@Composable
private fun OverviewFields(fields: List<OverviewField>, modifier: Modifier = Modifier, prominent: Boolean = false) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val unit = stringResource(R.string.model_list_token_unit)
    val labelStyle = MaterialTheme.typography.labelMedium
    val valueStyle = if (prominent) MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
        else MaterialTheme.typography.bodyMedium
    val missingStyle = MaterialTheme.typography.bodyMedium
    fun OverviewField.displayValue() = if (hasTokens && !prominent) "$value $unit" else value
    val requiredWidth = fields.maxOf { field ->
        maxOf(
            measurer.measure(field.label, labelStyle, softWrap = false).size.width,
            measurer.measure(field.displayValue(), if (field.missing) missingStyle else valueStyle, softWrap = false).size.width,
        )
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columnWidth = (constraints.maxWidth - with(density) { 24.dp.roundToPx() }) / 2
        val columns = if (maxWidth >= 240.dp * density.fontScale && columnWidth >= requiredWidth) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            fields.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { field ->
                        Column(Modifier.weight(1f).testTag("overview-metric:${field.tag}").semantics(mergeDescendants = true) {
                            contentDescription = "${field.label}：${field.value}${if (field.hasTokens) " $unit" else ""}"
                        }, verticalArrangement = Arrangement.spacedBy(if (prominent) 6.dp else 4.dp)) {
                            Text(field.label, style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(field.displayValue(), style = if (field.missing) missingStyle else valueStyle,
                                color = when {
                                    field.missing -> MaterialTheme.colorScheme.onSurfaceVariant
                                    prominent && field.tag == "context" -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                })
                            if (prominent && field.hasTokens) Text(unit, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewModalityRows(metadata: ModelDetailMetadata) {
    val input = stringResource(R.string.model_list_input_label)
    val output = stringResource(R.string.model_list_output_label)
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyMedium
    val labelWidth = with(LocalDensity.current) {
        maxOf(measurer.measure(input, style, softWrap = false).size.width,
            measurer.measure(output, style, softWrap = false).size.width).toDp()
    }.coerceAtLeast(40.dp)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        OverviewModalities(input, stringResource(R.string.model_filter_input), metadata.inputModalities, labelWidth,
            Modifier.testTag("overview-input-modalities"))
        OverviewModalities(output, stringResource(R.string.model_filter_output), metadata.outputModalities, labelWidth,
            Modifier.testTag("overview-output-modalities"))
    }
}

@Composable
private fun OverviewModalities(label: String, semanticLabel: String, modalities: List<String>?, labelWidth: Dp,
    modifier: Modifier) {
    val text = when {
        modalities == null -> stringResource(R.string.no_data)
        modalities.isEmpty() -> stringResource(R.string.model_list_no_modalities)
        else -> modalities.map { formatModality(it) }.joinToString("、")
    }
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$semanticLabel：$text" },
        horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.width(labelWidth), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (modalities.isNullOrEmpty()) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                modalities.forEach { modality ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(modalityIcon(modality)), null, Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatModality(modality), Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewDates(metadata: ModelDetailMetadata) {
    val noData = stringResource(R.string.no_data)
    OverviewFields(listOf(
        OverviewField("release", stringResource(R.string.model_list_release_date_label),
            metadata.releaseDate?.takeIf(String::isNotBlank) ?: noData, missing = metadata.releaseDate.isNullOrBlank()),
        OverviewField("knowledge", stringResource(R.string.detail_knowledge_cutoff),
            metadata.knowledgeCutoff?.takeIf(String::isNotBlank) ?: noData, missing = metadata.knowledgeCutoff.isNullOrBlank()),
    ))
}

@Composable
private fun OverviewDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
