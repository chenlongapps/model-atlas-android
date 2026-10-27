package io.github.chenlongapps.modelatlas.ui.modelcompare

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.data.model.ModelPriceTierSummary
import io.github.chenlongapps.modelatlas.ui.model.ModelInfoBadge
import io.github.chenlongapps.modelatlas.ui.modellist.formatCompactTokenCount
import io.github.chenlongapps.modelatlas.ui.modellist.formatModality
import io.github.chenlongapps.modelatlas.ui.modellist.formatTokenCount
import io.github.chenlongapps.modelatlas.ui.modellist.formatUsdPerMillionTokens
import io.github.chenlongapps.modelatlas.ui.modellist.labDisplayName
import io.github.chenlongapps.modelatlas.ui.modellist.modalityIcon

/** 参数仅取规范模型；报价仅取列表已选定的同一条服务报价，不拼接其他服务或模式。 */
internal fun LazyListScope.comparisonItems(
    state: ModelCompareUiState, tiersExpanded: Boolean, onToggleTiers: () -> Unit,
    expandedDetails: List<String>, onToggleDetail: (String) -> Unit,
) {
    val first = requireNotNull(state.first.detail)
    val second = requireNotNull(state.second.detail)
    val models = listOf(first, second)
    val prices = models.map { model -> state.models.firstOrNull { it.id == model.id }?.price }
    item("basic") {
        CompareSection(stringResource(R.string.compare_basic), R.drawable.ic_document, models) {
            TextRow(R.string.model_filter_labs, models.map { it.lab?.let(::labDisplayName) }, models, accent = true)
            TextRow(R.string.model_list_release_date_label, models.map { it.metadata.releaseDate }, models)
            TextRow(R.string.detail_family, models.map { it.metadata.family }, models)
            TextRow(R.string.detail_knowledge_cutoff, models.map { it.metadata.knowledgeCutoff }, models)
        }
    }
    item("io") {
        CompareSection(stringResource(R.string.compare_io), R.drawable.ic_compare, models) {
            TokenRow(R.string.model_list_context_label, models.map { it.metadata.contextLimitTokens }, models)
            TokenRow(R.string.detail_input_limit, models.map { it.metadata.inputLimitTokens }, models)
            TokenRow(R.string.model_list_output_limit_label, models.map { it.metadata.outputLimitTokens }, models)
            ModalityRow(R.string.model_filter_input, models.map { it.metadata.inputModalities }, models)
            ModalityRow(R.string.model_filter_output, models.map { it.metadata.outputModalities }, models)
        }
    }
    item("prices") {
        CompareSection(stringResource(R.string.model_list_price_title), R.drawable.ic_calendar, models) {
            Text(stringResource(R.string.model_list_price_unit), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextRow(R.string.compare_price_provider, prices.map { it?.providerName }, models, accent = true)
            PriceRows(prices, models)
            CompareDisclosure("service-id", stringResource(R.string.compare_price_service), expandedDetails, onToggleDetail) {
                TextRow(R.string.compare_price_service, prices.map { it?.offeringId }, models)
            }
            Text(stringResource(R.string.compare_price_notice), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    // 展开状态留在页面保存域，Lazy 条目回收或异步恢复不会丢失。
    if (prices.any { it?.tiers?.isNotEmpty() == true }) {
        item("tiers") { CompareTierSection(prices, models, tiersExpanded, onToggleTiers) }
    }
    item("capabilities") {
        CompareSection(stringResource(R.string.compare_capabilities), R.drawable.ic_modality, models) {
            BooleanRow(R.string.detail_attachment, models.map { it.metadata.attachment }, models)
            BooleanRow(R.string.detail_tool_call, models.map { it.metadata.toolCall }, models)
            BooleanRow(R.string.detail_reasoning, models.map { it.metadata.reasoning }, models)
            BooleanRow(R.string.detail_structured_output, models.map { it.metadata.structuredOutput }, models)
            BooleanRow(R.string.detail_temperature, models.map { it.metadata.temperature }, models)
            Text(stringResource(R.string.compare_capability_notice), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    item("weights") {
        CompareSection(stringResource(R.string.detail_weights), R.drawable.ic_database, models) {
            TextRow(R.string.compare_weight_status, models.map {
                when (it.metadata.openWeights) {
                    true -> stringResource(R.string.model_list_open_weights_yes)
                    false -> stringResource(R.string.model_list_open_weights_no)
                    null -> null
                }
            }, models)
            CompareDisclosure("license", stringResource(R.string.compare_full_license), expandedDetails, onToggleDetail) {
                TextRow(R.string.detail_license, models.map { it.license }, models)
            }
        }
    }
}

@Composable
private fun CompareSection(title: String, @DrawableRes icon: Int, models: List<ModelDetail>, content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(painterResource(icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            }
            CompareColumnHeaders(models)
            content()
        }
    }
}

// 与正文共享列宽；空间不足以并排时由每个值重复模型名，不再额外堆叠表头。
@Composable
private fun CompareColumnHeaders(models: List<ModelDetail>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val table = maxWidth >= 600.dp * fontScale
        if (maxWidth >= 240.dp * fontScale) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (table) Spacer(Modifier.weight(0.9f))
                models.forEachIndexed { index, model ->
                    Text(model.name, Modifier.weight(1.2f).testTag("compare-column-$index"),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CompareDisclosure(
    key: String, title: String, expandedDetails: List<String>, onToggleDetail: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val expanded = key in expandedDetails
    val action = stringResource(if (expanded) R.string.compare_collapse_detail else R.string.compare_expand_detail, title)
    val state = stringResource(if (expanded) R.string.compare_detail_expanded else R.string.compare_detail_collapsed)
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("compare-$key-toggle")
            .semantics { stateDescription = state }
            .clickable(role = Role.Button, onClickLabel = action) { onToggleDetail(key) },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(action, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Icon(painterResource(R.drawable.ic_expand_more), null, Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
                tint = MaterialTheme.colorScheme.primary)
        }
        // 不依赖惰性条目的局部状态，离屏回收和异步恢复仍保留展开。
        if (expanded) content()
    }
}

private data class CompareValue(
    val text: String,
    val spoken: String = text,
    val accent: Boolean = false,
    val modalities: List<String>? = null,
)

@Composable
private fun TextRow(label: Int, values: List<String?>, models: List<ModelDetail>, accent: Boolean = false) {
    CompareRow(stringResource(label), values.map { CompareValue(it?.takeIf(String::isNotBlank) ?: stringResource(R.string.no_data), accent = accent) }, models)
}

@Composable
private fun TokenRow(label: Int, values: List<Long?>, models: List<ModelDetail>) {
    CompareRow(stringResource(label), values.map {
        CompareValue(it?.let { "${formatCompactTokenCount(it)} tokens" } ?: stringResource(R.string.no_data),
            it?.let { "${formatTokenCount(it)} tokens" } ?: stringResource(R.string.no_data))
    }, models)
}

@Composable
private fun ModalityRow(label: Int, values: List<List<String>?>, models: List<ModelDetail>) {
    CompareRow(stringResource(label), values.map { modalities ->
        val text = when {
            modalities == null -> stringResource(R.string.no_data)
            modalities.isEmpty() -> stringResource(R.string.model_list_no_modalities)
            else -> modalities.map { formatModality(it) }.joinToString("、")
        }
        CompareValue(text, modalities = modalities?.takeIf { it.isNotEmpty() })
    }, models)
}

@Composable
private fun BooleanRow(label: Int, values: List<Boolean?>, models: List<ModelDetail>) {
    CompareRow(stringResource(label), values.map {
        CompareValue(stringResource(when (it) {
            true -> R.string.detail_supported
            false -> R.string.detail_unsupported
            null -> R.string.no_data
        }), accent = it == true)
    }, models)
}

@Composable
private fun CompareRow(label: String, values: List<CompareValue>, models: List<ModelDetail>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        // 手机优先给两侧数值完整的半屏宽度，只有宽屏才额外放入字段列。
        val table = maxWidth >= 600.dp * fontScale
        val dual = maxWidth >= 240.dp * fontScale
        val value: @Composable (Int, Modifier, Boolean) -> Unit = { index, modifier, showName ->
            CompareCell(label, models[index].name, values[index], modifier, showName)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            if (table) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(label, Modifier.weight(0.9f),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    value(0, Modifier.weight(1.2f), false)
                    value(1, Modifier.weight(1.2f), false)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (dual) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        value(0, Modifier.weight(1f), false)
                        value(1, Modifier.weight(1f), false)
                    } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        value(0, Modifier.fillMaxWidth(), true)
                        value(1, Modifier.fillMaxWidth(), true)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareCell(label: String, modelName: String, value: CompareValue, modifier: Modifier, showName: Boolean) {
    Column(modifier.clearAndSetSemantics {
        contentDescription = "$modelName，$label：${value.spoken}"
    }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showName) Text(modelName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (value.modalities != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                value.modalities.forEach { ModelInfoBadge(formatModality(it), icon = modalityIcon(it), showText = false) }
            }
        } else Text(value.text, style = MaterialTheme.typography.bodyMedium,
            color = if (value.accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PriceRows(prices: List<ModelPriceSummary?>, models: List<ModelDetail>) {
    PriceRow(R.string.model_list_input_label, prices.map { it?.input }, models)
    PriceRow(R.string.model_list_output_label, prices.map { it?.output }, models)
    PriceRow(R.string.model_list_price_cache_read_label, prices.map { it?.cacheRead }, models)
    val extras = listOf(
        R.string.detail_cache_write to prices.map { it?.cacheWrite },
        R.string.detail_reasoning to prices.map { it?.reasoning },
        R.string.detail_input_audio to prices.map { it?.inputAudio },
        R.string.detail_output_audio to prices.map { it?.outputAudio },
    )
    extras.filter { it.second.any { it != null } }.forEach { (label, values) -> PriceRow(label, values, models) }
}

@Composable
private fun PriceRow(label: Int, values: List<Double?>, models: List<ModelDetail>) {
    TextRow(label, values.map { it?.let(::formatUsdPerMillionTokens) }, models, accent = true)
}

@Composable
private fun CompareTierSection(prices: List<ModelPriceSummary?>, models: List<ModelDetail>, expanded: Boolean, onToggle: () -> Unit) {
    if (prices.none { it?.tiers?.isNotEmpty() == true }) return
    val stateLabel = stringResource(if (expanded) R.string.model_list_price_details_expanded else R.string.model_list_price_details_collapsed)
    val action = stringResource(if (expanded) R.string.model_list_price_details_collapse else R.string.model_list_price_details_expand)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().testTag("compare-tiers-toggle").semantics { stateDescription = stateLabel }
                .clickable(onClickLabel = action, onClick = onToggle).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.compare_tiers), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (expanded) R.string.model_filter_less else R.string.model_filter_more),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    prices.forEachIndexed { index, price ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(models[index].name, style = MaterialTheme.typography.titleMedium)
                            Text(price?.providerName ?: stringResource(R.string.no_data), style = MaterialTheme.typography.bodySmall)
                            if (price?.tiers.isNullOrEmpty()) Text(stringResource(R.string.compare_no_tiers))
                            price?.tiers?.forEach { tier -> CompareTier(tier) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareTier(tier: ModelPriceTierSummary) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (tier.conditionType == "context") stringResource(R.string.compare_context_tier, formatTokenCount(tier.conditionSizeTokens))
            else stringResource(R.string.compare_other_tier, tier.conditionType, formatTokenCount(tier.conditionSizeTokens)),
            style = MaterialTheme.typography.bodyMedium)
        listOf(
            R.string.model_list_input_label to tier.input, R.string.model_list_output_label to tier.output,
            R.string.model_list_price_cache_read_label to tier.cacheRead, R.string.detail_cache_write to tier.cacheWrite,
            R.string.detail_reasoning to tier.reasoning, R.string.detail_input_audio to tier.inputAudio,
            R.string.detail_output_audio to tier.outputAudio,
        ).forEach { (label, price) ->
            if (price != null) Text("${stringResource(label)}：${formatUsdPerMillionTokens(price)}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
