package io.github.chenlongapps.modelatlas.ui.model

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.ui.modellist.LabLogo
import io.github.chenlongapps.modelatlas.ui.modellist.formatCompactTokenCount
import io.github.chenlongapps.modelatlas.ui.modellist.formatModality
import io.github.chenlongapps.modelatlas.ui.modellist.formatTokenCount
import io.github.chenlongapps.modelatlas.ui.modellist.labDisplayName
import io.github.chenlongapps.modelatlas.ui.modellist.modalityIcon
import io.github.chenlongapps.modelatlas.ui.modellist.shortLicenseLabel
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme

/** 列表与详情共用身份结构；身份共享过渡与外层整卡展开同时进行。 */
@Composable
internal fun ModelIdentity(
    name: String,
    lab: String?,
    openWeights: Boolean?,
    license: String?,
    modifier: Modifier = Modifier,
    nameStyle: TextStyle = MaterialTheme.typography.titleMedium,
    logoModifier: Modifier = Modifier,
    nameModifier: Modifier = Modifier,
    labModifier: Modifier = Modifier,
    weightsModifier: Modifier = Modifier,
    centered: Boolean = false,
    modelId: String? = null,
    sharedElementsEnabled: Boolean = true,
) {
    val logo = logoModifier.then(modelSharedModifier(modelId, ModelIdentityRole.LOGO, sharedElementsEnabled))
    val nameElement = modelSharedModifier(modelId, ModelIdentityRole.NAME, sharedElementsEnabled)
    val labElement = labModifier.then(modelSharedModifier(modelId, ModelIdentityRole.LAB, sharedElementsEnabled))
    val weights = weightsModifier.then(modelSharedModifier(modelId, ModelIdentityRole.WEIGHTS, sharedElementsEnabled))
    if (centered) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LabLogo(lab, logo, size = 88.dp)
            Text(name, Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
                .then(nameModifier).then(nameElement).testTag("model-name").semantics { heading() },
                style = nameStyle, textAlign = TextAlign.Center)
            ModelLabAndWeights(lab, openWeights, license, labModifier = labElement, weightsModifier = weights, centered = true)
        }
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        LabLogo(lab, logo)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, nameModifier.then(nameElement).testTag("model-name").semantics { heading() }, style = nameStyle)
            ModelLabAndWeights(lab, openWeights, license, labModifier = labElement, weightsModifier = weights)
        }
    }
}

@Composable
private fun ModelLabAndWeights(
    lab: String?, openWeights: Boolean?, license: String?,
    modifier: Modifier = Modifier,
    labModifier: Modifier = Modifier,
    weightsModifier: Modifier = Modifier,
    centered: Boolean = false,
) {
    FlowRow(
        modifier = modifier.then(if (centered) Modifier.fillMaxWidth() else Modifier),
        horizontalArrangement = Arrangement.spacedBy(8.dp, if (centered) Alignment.CenterHorizontally else Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(lab?.let(::labDisplayName) ?: stringResource(R.string.no_data), labModifier.testTag("model-lab"),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        ModelWeightsBadge(openWeights, license, weightsModifier)
    }
}

@Composable
internal fun ModelWeightsBadge(openWeights: Boolean?, license: String?, modifier: Modifier = Modifier) {
    val status = when (openWeights) {
        true -> stringResource(R.string.model_list_open_weights_yes)
        false -> stringResource(R.string.model_list_open_weights_no)
        null -> stringResource(R.string.model_list_open_weights, stringResource(R.string.no_data))
    }
    val shortLicense = shortLicenseLabel(license)
    ModelInfoBadge(
        text = if (openWeights == true && shortLicense != null) {
            stringResource(R.string.model_list_weights_with_license, status, shortLicense)
        } else status,
        modifier = modifier.testTag("model-weights"),
        highlighted = openWeights == true,
    )
}

/** 非交互标签；详情可展示文字，但不改变标签的圆角、颜色和图标。 */
@Composable
internal fun ModelInfoBadge(
    text: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    @DrawableRes icon: Int? = null,
    showText: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (highlighted) colors.tertiaryContainer else colors.surfaceContainerLow,
        contentColor = if (highlighted) colors.onTertiaryContainer else colors.onSurfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(painterResource(icon), if (showText) null else text, Modifier.size(16.dp))
            if (showText) Text(text, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun ModelModalities(
    input: List<String>?,
    output: List<String>?,
    modifier: Modifier = Modifier,
    showText: Boolean = false,
    centered: Boolean = false,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp, if (centered) Alignment.CenterHorizontally else Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        ModalityBadges(stringResource(R.string.model_list_input_label), input, showText, Modifier.testTag("input-modalities"))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_arrow_forward), null,
                Modifier.size(18.dp).testTag("modalities-direction"), tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ModalityBadges(stringResource(R.string.model_list_output_label), output, showText,
                Modifier.weight(1f, fill = false).testTag("output-modalities"))
        }
    }
}

@Composable
private fun ModalityBadges(label: String, modalities: List<String>?, showText: Boolean, modifier: Modifier) {
    FlowRow(
        modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val values = when {
            modalities == null -> listOf(null to stringResource(R.string.no_data))
            modalities.isEmpty() -> listOf(null to stringResource(R.string.model_list_no_modalities))
            else -> modalities.map { it to formatModality(it) }
        }
        values.forEach { (modality, text) ->
            ModelInfoBadge(text, icon = modalityIcon(modality), showText = showText)
        }
    }
}

/** 只接收规范模型字段；参数卡使用同一缩写与精确读屏值，完整参数另在概览的技术参数分区展示。 */
@Composable
internal fun ModelCoreMetrics(
    releaseDate: String?,
    contextLimitTokens: Long?,
    outputLimitTokens: Long?,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
) {
    val noData = stringResource(R.string.no_data)
    val metrics = listOf(
        CoreMetric(R.drawable.ic_calendar, stringResource(R.string.model_list_release_date_label),
            releaseDate ?: noData, releaseDate ?: noData, "release-date"),
        CoreMetric(R.drawable.ic_context_window, stringResource(R.string.model_list_context_label),
            contextLimitTokens?.let(::formatCompactTokenCount) ?: noData,
            contextLimitTokens?.let { "${formatTokenCount(it)} ${stringResource(R.string.model_list_token_unit)}" } ?: noData,
            "context-limit", contextLimitTokens != null),
        CoreMetric(R.drawable.ic_output_limit, stringResource(R.string.model_list_output_limit_label),
            outputLimitTokens?.let(::formatCompactTokenCount) ?: noData,
            outputLimitTokens?.let { "${formatTokenCount(it)} ${stringResource(R.string.model_list_token_unit)}" } ?: noData,
            "output-limit", outputLimitTokens != null),
    )
    if (prominent) {
        val minimumWidth = 88.dp * LocalDensity.current.fontScale
        val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        BoxWithConstraints(modifier) {
            val columns = (maxWidth / minimumWidth).toInt().coerceIn(1, 3)
            FlowRow(
                Modifier.fillMaxWidth(), maxItemsInEachRow = columns,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                metrics.forEachIndexed { index, metric ->
                    val hasDivider = index % columns != 0
                    Row(
                        Modifier.weight(1f).testTag(metric.tag)
                            .clearAndSetSemantics { contentDescription = "${metric.label}：${metric.exactValue}" }
                            .drawBehind {
                                if (hasDivider) {
                                    val x = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width
                                    drawLine(dividerColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                                }
                            }
                            .padding(start = if (hasDivider) 8.dp else 0.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(metric.icon), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(metric.compactValue, style = if (index == 0) MaterialTheme.typography.labelMedium
                                else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(if (index == 0) metric.label else stringResource(R.string.detail_metric_tokens, metric.label),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    } else {
        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            metrics.forEach { metric -> CompactMetric(metric) }
        }
    }
}

private data class CoreMetric(
    @DrawableRes val icon: Int,
    val label: String,
    val compactValue: String,
    val exactValue: String,
    val tag: String,
    val hasTokens: Boolean = false,
)

@Composable
private fun CompactMetric(metric: CoreMetric) {
    Row(
        Modifier.testTag(metric.tag).then(
            if (metric.hasTokens) Modifier.clearAndSetSemantics { contentDescription = "${metric.label}：${metric.exactValue}" }
            else Modifier.semantics(mergeDescendants = true) {},
        ),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(metric.icon), metric.label, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(metric.compactValue, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(name = "Model presentation · List", showBackground = true, widthDp = 400)
@Preview(name = "Model presentation · List dark", showBackground = true, widthDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Model presentation · List narrow", showBackground = true, widthDp = 320)
@Preview(name = "Model presentation · List large font", showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
private fun ModelPresentationListPreview() {
    ModelAtlasTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ModelIdentity(
                    name = "DeepSeek V4.1 Flash",
                    lab = "deepseek",
                    openWeights = true,
                    license = "MIT",
                )
                ModelCoreMetrics(
                    releaseDate = "2025-06-17",
                    contextLimitTokens = 128_000,
                    outputLimitTokens = 16_384,
                )
                ModelModalities(input = listOf("text", "image"), output = listOf("text"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModelWeightsBadge(openWeights = true, license = "MIT")
                    ModelWeightsBadge(openWeights = false, license = null)
                    ModelWeightsBadge(openWeights = null, license = null)
                }
            }
        }
    }
}

@Preview(name = "Model presentation · Detail", showBackground = true, widthDp = 400)
@Preview(name = "Model presentation · Detail dark", showBackground = true, widthDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Model presentation · Detail narrow", showBackground = true, widthDp = 320)
@Preview(name = "Model presentation · Detail large font", showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
private fun ModelPresentationDetailPreview() {
    ModelAtlasTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ModelIdentity(
                    name = "DeepSeek V4.1 Flash",
                    lab = "deepseek",
                    openWeights = true,
                    license = "MIT",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    nameStyle = MaterialTheme.typography.headlineMedium,
                    centered = true,
                )
                ModelModalities(
                    input = listOf("text", "image", "audio"),
                    output = listOf("text"),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    showText = true,
                    centered = true,
                )
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModelInfoBadge("推理")
                    ModelInfoBadge("工具调用")
                    ModelInfoBadge("结构化输出")
                }
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                ) {
                    ModelCoreMetrics(
                        releaseDate = "2025-06-17",
                        contextLimitTokens = 128_000,
                        outputLimitTokens = 16_384,
                        modifier = Modifier.padding(16.dp),
                        prominent = true,
                    )
                }
            }
        }
    }
}
