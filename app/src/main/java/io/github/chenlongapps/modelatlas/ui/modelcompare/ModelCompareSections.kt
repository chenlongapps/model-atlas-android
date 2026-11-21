package io.github.chenlongapps.modelatlas.ui.modelcompare

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.data.model.ModelPriceTierSummary
import io.github.chenlongapps.modelatlas.ui.model.formatBillionParameters
import io.github.chenlongapps.modelatlas.ui.modellist.formatCompactTokenCount
import io.github.chenlongapps.modelatlas.ui.modellist.formatModality
import io.github.chenlongapps.modelatlas.ui.modellist.formatTokenCount
import io.github.chenlongapps.modelatlas.ui.modellist.formatUsdPerMillionTokens

internal enum class CompareLayout { Table, Pair, Stacked }
internal val CompareCardPadding = 12.dp

@Composable
internal fun CompareKeySpecs(
    specs: CompareSpecs, names: List<String>, layout: CompareLayout,
    onlyDifferences: Boolean, onOnlyDifferences: (Boolean) -> Unit,
) {
    CompareSection(Modifier.testTag("compare-specs")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CompareHeading(R.string.compare_specs, Modifier.weight(1f))
            if (specs.fields.any { it.isVisible(false) }) {
                Row(Modifier.weight(1f).heightIn(min = 48.dp).testTag("compare-differences")
                    .toggleable(onlyDifferences, role = Role.Switch, onValueChange = onOnlyDifferences),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.compare_only_differences), Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.labelMedium)
                    Switch(checked = onlyDifferences, onCheckedChange = null)
                }
            }
        }
        if (specs.fields.none { it.isVisible(onlyDifferences) }) {
            CompareNote(stringResource(if (specs.fields.any { it.isVisible(false) }) R.string.compare_no_differences
                else R.string.compare_no_specs))
        }
        TokenRow(R.string.model_list_context_label, specs.context, names, layout, onlyDifferences, prominent = true)
        TokenRow(R.string.model_list_output_limit_label, specs.output, names, layout, onlyDifferences, prominent = true)
        TokenRow(R.string.detail_input_limit, specs.input, names, layout, onlyDifferences)
        SpecRow(R.string.model_filter_input, specs.inputModalities, names, layout, onlyDifferences) { ModalityValue(it) }
        SpecRow(R.string.model_filter_output, specs.outputModalities, names, layout, onlyDifferences) { ModalityValue(it) }
        SpecRow(R.string.compare_weight_status, specs.weights, names, layout, onlyDifferences) {
            CompareValue(stringResource(if (it) R.string.model_list_open_weights_yes else R.string.model_list_open_weights_no))
        }
    }
}

/** 报价仅取列表已选定的同一条服务报价，不拼接其他服务或模式。 */
@Composable
internal fun ComparePrices(
    prices: List<ModelPriceSummary?>, names: List<String>, layout: CompareLayout,
    expanded: Boolean, onToggle: (String) -> Unit,
) {
    CompareSection {
        CompareHeading(R.string.compare_api_prices)
        if (prices.any { it != null }) {
            CompareNote(stringResource(R.string.compare_price_unit))
            TextRow(R.string.compare_price_provider, prices.map { it?.providerName }, names, layout)
            PriceRow(R.string.model_list_input_label, prices.map { it?.input }, names, layout, prominent = true)
            PriceRow(R.string.model_list_output_label, prices.map { it?.output }, names, layout, prominent = true)
        }
        if (prices.none { it?.input != null || it?.output != null }) {
            CompareNote(stringResource(R.string.compare_no_base_price))
        }
        if (prices.all { it != null }) CompareNote(stringResource(R.string.compare_price_conditions))
        val extras = listOf(
            R.string.model_list_price_cache_read_label to prices.map { it?.cacheRead },
            R.string.detail_cache_write to prices.map { it?.cacheWrite },
            R.string.detail_reasoning to prices.map { it?.reasoning },
            R.string.detail_input_audio to prices.map { it?.inputAudio },
            R.string.detail_output_audio to prices.map { it?.outputAudio },
        ).filter { (_, values) -> values.any { it != null } }
        if (extras.isNotEmpty()) {
            CompareDisclosure("price-details", R.string.compare_price_details, expanded, onToggle) {
                extras.forEach { (label, values) -> PriceRow(label, values, names, layout) }
            }
        }
    }
}

internal fun LazyListScope.compareMoreItems(
    models: List<ModelDetail>, prices: List<ModelPriceSummary?>, names: List<String>, layout: CompareLayout,
    expandedDetails: Set<String>, onToggleDetail: (String) -> Unit,
) {
    val metadata = models.map(ModelDetail::metadata)
    val hasBasic = metadata.any { !it.releaseDate.isNullOrBlank() || !it.family.isNullOrBlank() || !it.knowledgeCutoff.isNullOrBlank() }
    val hasParameters = models.any { it.parameters?.let { p -> p.totalBillion != null || p.activeBillion != null } == true }
    val hasCapabilities = metadata.any {
        it.attachment != null || it.toolCall != null || it.reasoning != null ||
            it.structuredOutput != null || it.temperature != null
    }
    val hasLicense = models.any { !it.license.isNullOrBlank() }
    val hasServiceId = prices.any { !it?.offeringId.isNullOrBlank() }
    val hasTiers = prices.any { it?.tiers?.isNotEmpty() == true }
    if (!(hasBasic || hasParameters || hasCapabilities || hasLicense || hasServiceId || hasTiers)) return
    item("more", contentType = "toggle") {
        CompareToggleCard("more", R.string.compare_more_information, "more" in expandedDetails, onToggleDetail)
    }
    if ("more" !in expandedDetails) return
    if (hasBasic || hasParameters) item("basic", contentType = "section") {
        CompareSection {
            CompareHeading(R.string.compare_basic)
            TextRow(R.string.model_list_release_date_label, metadata.map { it.releaseDate }, names, layout)
            TextRow(R.string.detail_family, metadata.map { it.family }, names, layout)
            TextRow(R.string.detail_knowledge_cutoff, metadata.map { it.knowledgeCutoff }, names, layout)
            TextRow(R.string.parameter_total, models.map { it.parameters?.totalBillion?.let(::formatBillionParameters) }, names, layout)
            TextRow(R.string.parameter_active, models.map { it.parameters?.activeBillion?.let(::formatBillionParameters) }, names, layout)
        }
    }
    if (hasCapabilities) item("capabilities", contentType = "section") {
        CompareSection {
            CompareHeading(R.string.compare_capabilities)
            BooleanRow(R.string.detail_attachment, metadata.map { it.attachment }, names, layout)
            BooleanRow(R.string.detail_tool_call, metadata.map { it.toolCall }, names, layout)
            BooleanRow(R.string.detail_reasoning, metadata.map { it.reasoning }, names, layout)
            BooleanRow(R.string.detail_structured_output, metadata.map { it.structuredOutput }, names, layout)
            BooleanRow(R.string.detail_temperature, metadata.map { it.temperature }, names, layout)
        }
    }
    if (hasServiceId) item("service-id", contentType = "section") {
        CompareSection {
            CompareDisclosure("service-id", R.string.compare_price_service, "service-id" in expandedDetails, onToggleDetail) {
                TextRow(R.string.compare_price_service, prices.map { it?.offeringId }, names, layout)
            }
        }
    }
    if (hasTiers) item("tiers", contentType = "section") {
        CompareSection {
            CompareDisclosure("tiers", R.string.compare_tiers, "tiers" in expandedDetails, onToggleDetail,
                titleAsAction = false) {
                CompareNote(stringResource(R.string.compare_price_unit))
                // 档位条件未必相同，逐侧展示而非逐条配对。
                prices.forEachIndexed { index, price ->
                    CompareDivider()
                    Column(Modifier.semantics { contentDescription = names[index] },
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val slot = stringResource(R.string.compare_slot_label, index + 1)
                        Text(listOfNotNull(slot, price?.providerName).joinToString(" · "), style = MaterialTheme.typography.titleSmall)
                        if (price?.tiers.isNullOrEmpty()) CompareNote(stringResource(R.string.compare_no_tiers))
                        price?.tiers?.forEach { CompareTier(it) }
                    }
                }
            }
        }
    }
    if (hasLicense) item("weights", contentType = "section") {
        CompareSection {
            CompareHeading(R.string.detail_weights)
            CompareDisclosure("license", R.string.compare_full_license, "license" in expandedDetails, onToggleDetail) {
                TextRow(R.string.detail_license, models.map { it.license }, names, layout)
            }
        }
    }
}

@Composable
internal fun CompareSection(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(CompareCardPadding), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
internal fun CompareHeading(@StringRes title: Int, modifier: Modifier = Modifier) {
    Text(stringResource(title), modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
}

@Composable
internal fun CompareNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr),
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun CompareDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
internal fun CompareToggleCard(key: String, @StringRes title: Int, expanded: Boolean, onToggle: (String) -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        CompareToggle(key, stringResource(title), expanded, onToggle, Modifier.padding(horizontal = CompareCardPadding),
            isHeading = true)
    }
}

@Composable
internal fun CompareDisclosure(
    key: String, @StringRes title: Int, expanded: Boolean, onToggle: (String) -> Unit,
    titleAsAction: Boolean = true, content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        CompareToggle(key, stringResource(title), expanded, onToggle, titleAsAction = titleAsAction)
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
        }
    }
}

@Composable
internal fun CompareToggle(
    key: String, title: String, expanded: Boolean, onToggle: (String) -> Unit,
    modifier: Modifier = Modifier, isHeading: Boolean = false, titleAsAction: Boolean = false,
) {
    val action = stringResource(if (expanded) R.string.compare_collapse_detail else R.string.compare_expand_detail, title)
    val state = stringResource(if (expanded) R.string.compare_detail_expanded else R.string.compare_detail_collapsed)
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "compare-chevron")
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("compare-$key-toggle")
        .semantics { stateDescription = state; if (isHeading) heading() }
        .clickable(role = Role.Button, onClickLabel = action) { onToggle(key) },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (titleAsAction) action else title, Modifier.weight(1f), style = if (isHeading)
            MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            color = if (isHeading) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
        Icon(painterResource(R.drawable.ic_expand_more), null, Modifier.size(20.dp).graphicsLayer { rotationZ = rotation },
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal data class CompareValue(
    val text: String, val spoken: String = text, val unit: String? = null,
    val prominent: Boolean = false, val missing: Boolean = false,
)

@Composable
internal fun MissingCompareValue(@StringRes label: Int = R.string.no_data): CompareValue =
    CompareValue(stringResource(label), missing = true)

@Composable
private fun <T : Any> SpecRow(
    @StringRes label: Int, spec: CompareSpec<T>, names: List<String>, layout: CompareLayout,
    onlyDifferences: Boolean, value: @Composable (T) -> CompareValue,
) {
    if (!spec.isVisible(onlyDifferences)) return
    CompareRow(stringResource(label), spec.first?.let { value(it) } ?: MissingCompareValue(),
        spec.second?.let { value(it) } ?: MissingCompareValue(), names, layout,
        different = spec.difference == SpecDifference.DIFFERENT)
}

@Composable
private fun TokenRow(label: Int, spec: CompareSpec<Long>, names: List<String>, layout: CompareLayout,
    onlyDifferences: Boolean, prominent: Boolean = false) {
    val showExact = spec.difference == SpecDifference.DIFFERENT &&
        formatCompactTokenCount(requireNotNull(spec.first)) == formatCompactTokenCount(requireNotNull(spec.second))
    SpecRow(label, spec, names, layout, onlyDifferences) {
        val unit = stringResource(R.string.model_list_token_unit)
        val exact = formatTokenCount(it)
        CompareValue(if (showExact) exact else formatCompactTokenCount(it), "$exact $unit", unit, prominent)
    }
}

@Composable
private fun ModalityValue(modalities: Set<String>): CompareValue =
    CompareValue(if (modalities.isEmpty()) stringResource(R.string.model_list_no_modalities)
        else modalities.map { formatModality(it) }.joinToString("、"))

@Composable
private fun TextRow(label: Int, values: List<String?>, names: List<String>, layout: CompareLayout) {
    if (values.all { it.isNullOrBlank() }) return
    val cells = values.map { it?.takeIf(String::isNotBlank)?.let { text -> CompareValue(text) } ?: MissingCompareValue() }
    CompareRow(stringResource(label), cells[0], cells[1], names, layout)
}

@Composable
private fun BooleanRow(label: Int, values: List<Boolean?>, names: List<String>, layout: CompareLayout) =
    TextRow(label, values.map {
        it?.let { supported -> stringResource(if (supported) R.string.detail_supported else R.string.detail_unsupported) }
    }, names, layout)

@Composable
private fun PriceRow(label: Int, values: List<Double?>, names: List<String>, layout: CompareLayout, prominent: Boolean = false) {
    if (values.all { it == null }) return
    val cells = values.map { it?.let { price -> CompareValue(formatUsdPerMillionTokens(price), prominent = prominent) }
        ?: MissingCompareValue() }
    CompareRow(stringResource(label), cells[0], cells[1], names, layout)
}

@Composable
internal fun CompareRow(
    label: String, first: CompareValue, second: CompareValue, names: List<String>, layout: CompareLayout,
    different: Boolean = false, divider: Boolean = true, spokenLabel: String = label,
) {
    val differentLabel = if (different) stringResource(R.string.compare_field_differs) else null
    val field: @Composable (Modifier) -> Unit = { modifier ->
        Text(label, modifier.semantics { differentLabel?.let { stateDescription = it } },
            style = MaterialTheme.typography.labelMedium,
            color = if (different) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (divider) CompareDivider()
        when (layout) {
            CompareLayout.Table -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                field(Modifier.weight(0.9f))
                CompareCell(spokenLabel, names[0], first, Modifier.weight(1.2f), different = different)
                CompareCell(spokenLabel, names[1], second, Modifier.weight(1.2f), different = different)
            }
            CompareLayout.Pair -> {
                field(Modifier)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CompareCell(spokenLabel, names[0], first, Modifier.weight(1f), different = different)
                    CompareCell(spokenLabel, names[1], second, Modifier.weight(1f), different = different)
                }
            }
            CompareLayout.Stacked -> {
                field(Modifier)
                CompareCell(spokenLabel, names[0], first, Modifier.fillMaxWidth(), slot = 0, different = different)
                CompareCell(spokenLabel, names[1], second, Modifier.fillMaxWidth(), slot = 1, different = different)
            }
        }
    }
}

@Composable
internal fun CompareCell(label: String, name: String, value: CompareValue, modifier: Modifier, slot: Int? = null,
    different: Boolean = false) {
    Column(modifier.clearAndSetSemantics { contentDescription = "$name，$label：${value.spoken}" }) {
        if (slot != null) CompareNote(stringResource(R.string.compare_slot_label, slot + 1))
        val style = if (value.prominent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
        // 只镜像列顺序，不让 RTL 反转由数字／中性符号组成的成绩（如 899 ± 18）。
        Text(value.text, style = style.copy(textDirection = TextDirection.ContentOrLtr),
            fontWeight = if (different) FontWeight.SemiBold else null,
            color = when {
                value.missing -> MaterialTheme.colorScheme.onSurfaceVariant
                different -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            })
        value.unit?.let { CompareNote(it) }
    }
}

@Composable
private fun CompareTier(tier: ModelPriceTierSummary) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}
