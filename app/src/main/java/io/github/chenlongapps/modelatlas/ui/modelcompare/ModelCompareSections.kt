package io.github.chenlongapps.modelatlas.ui.modelcompare

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.ui.model.formatBillionParameters
import io.github.chenlongapps.modelatlas.ui.modellist.formatCompactTokenCount
import io.github.chenlongapps.modelatlas.ui.modellist.formatModality
import io.github.chenlongapps.modelatlas.ui.modellist.formatTokenCount
import io.github.chenlongapps.modelatlas.ui.modellist.formatUsdPerMillionTokens

internal enum class CompareLayout { Table, Pair, Stacked }

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
    prices: List<ModelPriceSummary?>, names: List<String>, layout: CompareLayout, onInfo: () -> Unit,
) {
    CompareSection {
        val hasQuote = prices.any { it != null }
        CompareInfoHeading(R.string.compare_api_prices, R.string.compare_show_price_info, onInfo.takeIf { hasQuote })
        if (hasQuote) {
            CompareNote(stringResource(R.string.compare_price_unit))
            TextRow(R.string.compare_price_provider, prices.map { it?.providerName }, names, layout)
            PriceRow(R.string.model_list_input_label, prices.map { it?.input }, names, layout, prominent = true)
            PriceRow(R.string.model_list_output_label, prices.map { it?.output }, names, layout, prominent = true)
        }
        if (prices.none { it?.input != null || it?.output != null }) {
            CompareNote(stringResource(R.string.compare_no_base_price))
        }
        PriceRow(R.string.model_list_price_cache_read_label, prices.map { it?.cacheRead }, names, layout)
        PriceRow(R.string.detail_cache_write, prices.map { it?.cacheWrite }, names, layout)
        PriceRow(R.string.compare_price_reasoning, prices.map { it?.reasoning }, names, layout)
        PriceRow(R.string.detail_input_audio, prices.map { it?.inputAudio }, names, layout)
        PriceRow(R.string.detail_output_audio, prices.map { it?.outputAudio }, names, layout)
    }
}

internal fun LazyListScope.compareModelInfoItems(
    models: List<ModelDetail>, names: List<String>, layout: CompareLayout,
) {
    val metadata = models.map(ModelDetail::metadata)
    val hasBasic = metadata.any { !it.releaseDate.isNullOrBlank() || !it.family.isNullOrBlank() || !it.knowledgeCutoff.isNullOrBlank() }
    val hasParameters = models.any { it.parameters?.let { p -> p.totalBillion != null || p.activeBillion != null } == true }
    val hasCapabilities = metadata.any {
        it.attachment != null || it.toolCall != null || it.reasoning != null ||
            it.structuredOutput != null || it.temperature != null
    }
    val hasLicense = models.any { !it.license.isNullOrBlank() }
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
    if (hasLicense) item("weights", contentType = "section") {
        CompareSection {
            CompareHeading(R.string.detail_weights)
            TextRow(R.string.detail_license, models.map { it.license }, names, layout)
        }
    }
}

@Composable
internal fun CompareSection(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
internal fun CompareHeading(@StringRes title: Int, modifier: Modifier = Modifier) {
    Text(stringResource(title), modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
}

@Composable
internal fun CompareNote(text: String, textAlign: TextAlign = TextAlign.Start) {
    Text(text, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr),
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = textAlign)
}

internal data class CompareValue(
    val text: String, val spoken: String = text, val supportingText: String? = null,
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
    modifier: Modifier = Modifier, different: Boolean = false, spokenLabel: String = label, isHeading: Boolean = false,
) {
    val differentLabel = if (different) stringResource(R.string.compare_field_differs) else null
    val field: @Composable (Modifier) -> Unit = { modifier ->
        Text(label, modifier.semantics {
            if (isHeading) heading()
            differentLabel?.let { stateDescription = it }
        }, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isHeading || different) FontWeight.SemiBold else null,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (layout != CompareLayout.Table) field(Modifier)
        if (layout == CompareLayout.Stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CompareCell(spokenLabel, names[0], first, Modifier.fillMaxWidth(), slot = 0, different = different)
                CompareCell(spokenLabel, names[1], second, Modifier.fillMaxWidth(), slot = 1, different = different)
            }
        } else {
            // 先按各列宽度计算换行高度，再让两个值框占满同一行。
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (layout == CompareLayout.Table) field(Modifier.weight(0.75f))
                CompareCell(spokenLabel, names[0], first, Modifier.weight(1f).fillMaxHeight(), different = different)
                CompareCell(spokenLabel, names[1], second, Modifier.weight(1f).fillMaxHeight(), different = different)
            }
        }
    }
}

@Composable
private fun CompareCell(label: String, name: String, value: CompareValue, modifier: Modifier, slot: Int? = null,
    different: Boolean = false) {
    Column(
        modifier.heightIn(min = 48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
            .clearAndSetSemantics { contentDescription = "$name，$label：${value.spoken}" }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (slot != null) CompareNote(stringResource(R.string.compare_slot_label, slot + 1), TextAlign.Center)
        val style = if (value.prominent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
        // 只镜像列顺序，不让 RTL 反转由数字／中性符号组成的成绩（如 899 ± 18）。
        Text(value.text, style = style.copy(textDirection = TextDirection.ContentOrLtr), textAlign = TextAlign.Center,
            fontWeight = if (different) FontWeight.Bold else null,
            color = if (value.missing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        value.supportingText?.let { CompareNote(it, TextAlign.Center) }
    }
}
