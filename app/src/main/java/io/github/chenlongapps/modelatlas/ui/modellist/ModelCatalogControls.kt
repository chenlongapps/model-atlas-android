package io.github.chenlongapps.modelatlas.ui.modellist

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import kotlinx.serialization.json.Json

@Composable
internal fun ModelCatalogControls(
    query: ModelListQuery,
    models: List<ModelSummary>,
    onQueryChange: (ModelListQuery) -> Unit,
    onShowFilters: () -> Unit,
    skeletonProgress: State<Float>? = null,
) {
    val enabled = skeletonProgress == null
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val searchLabel = stringResource(R.string.model_search_label)
    val labModelCounts = remember(models) { countModelsByLab(models) }
    Column(modifier = Modifier.testTag("catalog-query"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(
            value = query.search,
            enabled = enabled,
            onValueChange = { onQueryChange(query.copy(search = it)) },
            placeholder = { Text(stringResource(R.string.model_search_hint)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            trailingIcon = if (query.search.isNotEmpty()) {
                {
                    IconButton(enabled = enabled, onClick = { onQueryChange(query.copy(search = "")) }) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.model_search_clear))
                    }
                }
            } else null,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainerLow,
                unfocusedContainerColor = colors.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("model-search")
                .modelSkeletonPlaceholder(skeletonProgress, CircleShape)
                .semantics { contentDescription = searchLabel },
        )
        FilledTonalButton(
            enabled = enabled,
            modifier = Modifier.testTag("model-filter")
                .modelSkeletonPlaceholder(skeletonProgress, CircleShape),
            onClick = {
                focusManager.clearFocus()
                onShowFilters()
            },
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (query.filterCount > 0) colors.secondaryContainer else colors.surfaceContainerLow,
                contentColor = if (query.filterCount > 0) colors.onSecondaryContainer else colors.onSurface,
            ),
        ) {
            Icon(
                painterResource(R.drawable.ic_filter), contentDescription = null,
                modifier = Modifier.padding(end = 8.dp).size(20.dp),
            )
            Text(stringResource(R.string.model_filter_action, query.filterCount))
            Icon(
                painterResource(R.drawable.ic_expand_more), contentDescription = null,
                modifier = Modifier.padding(start = 8.dp).size(18.dp),
            )
        }
        if (query.filterCount > 0) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                activeFilters(query, labModelCounts).forEach { filter ->
                    InputChip(
                        selected = true,
                        enabled = enabled,
                        onClick = { onQueryChange(filter.withoutFilter) },
                        label = { Text(filter.label) },
                        trailingIcon = {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        shape = CircleShape,
                        colors = InputChipDefaults.inputChipColors(
                            selectedContainerColor = colors.secondaryContainer,
                            selectedLabelColor = colors.onSecondaryContainer,
                            selectedTrailingIconColor = colors.onSecondaryContainer,
                        ),
                        border = BorderStroke(0.dp, Color.Transparent),
                        modifier = Modifier.testTag("active-filter:${filter.key}")
                            .modelSkeletonPlaceholder(skeletonProgress, CircleShape)
                            .semantics { contentDescription = filter.removeLabel },
                    )
                }
                TextButton(
                    enabled = enabled,
                    onClick = { onQueryChange(query.resetFilters()) },
                    modifier = Modifier.testTag("reset-filters")
                        .modelSkeletonPlaceholder(skeletonProgress, CircleShape),
                ) {
                    Text(stringResource(R.string.model_filter_reset))
                }
            }
        }
    }
}

private data class ActiveFilter(
    val key: String,
    val label: String,
    val removeLabel: String,
    val withoutFilter: ModelListQuery,
)

@Composable
private fun activeFilters(query: ModelListQuery, labModelCounts: Map<String, Int>): List<ActiveFilter> {
    val result = mutableListOf<ActiveFilter>()
    @Composable fun add(key: String, label: String, withoutFilter: ModelListQuery, readableLabel: String = label) {
        result += ActiveFilter(key, label, stringResource(R.string.model_filter_remove, readableLabel), withoutFilter)
    }
    sortFilterLabs(query.labs, labModelCounts).forEach { lab ->
        add("lab:$lab", stringResource(R.string.model_filter_active_lab, labDisplayName(lab)), query.copy(labs = query.labs - lab))
    }
    if (query.weights != WeightFilter.ALL) {
        add("weights", stringResource(R.string.model_filter_active_weights, weightLabel(query.weights)), query.copy(weights = WeightFilter.ALL))
    }
    query.inputModalities.sorted().forEach { modality ->
        add("input:$modality", stringResource(R.string.model_filter_active_input, formatModality(modality)), query.copy(inputModalities = query.inputModalities - modality))
    }
    query.outputModalities.sorted().forEach { modality ->
        add("output:$modality", stringResource(R.string.model_filter_active_output, formatModality(modality)), query.copy(outputModalities = query.outputModalities - modality))
    }
    query.minContextTokens?.let { tokens ->
        add(
            "context", stringResource(R.string.model_filter_active_context, formatFilterTokenCount(tokens)),
            query.copy(minContextTokens = null),
            stringResource(R.string.model_filter_active_context, "${formatTokenCount(tokens)} tokens"),
        )
    }
    query.minOutputTokens?.let { tokens ->
        add(
            "output-limit", stringResource(R.string.model_filter_active_output_limit, formatFilterTokenCount(tokens)),
            query.copy(minOutputTokens = null),
            stringResource(R.string.model_filter_active_output_limit, "${formatTokenCount(tokens)} tokens"),
        )
    }
    query.hasPrice?.let { hasPrice ->
        add("price", stringResource(if (hasPrice) R.string.model_filter_price_yes else R.string.model_filter_price_no), query.copy(hasPrice = null))
    }
    return result
}

private val FilterDraftSaver = Saver<ModelListQuery, String>(
    save = { Json.encodeToString(it) },
    restore = { Json.decodeFromString<ModelListQuery>(it) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelFilterSheet(
    models: List<ModelSummary>,
    query: ModelListQuery,
    onQueryChange: (ModelListQuery) -> Unit,
    onDismiss: () -> Unit,
) {
    // 在页面的保存状态域中持有草稿，不依赖弹层窗口自己的保存注册表。
    // 整个 Sheet 关闭后这些状态离开组合，下次打开仍从已应用条件重新建立草稿。
    var draft by rememberSaveable(stateSaver = FilterDraftSaver) { mutableStateOf(query) }
    var labsExpanded by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ModelFilterSheetContent(
            models = models,
            query = query,
            draft = draft,
            onDraftChange = { draft = it },
            labsExpanded = labsExpanded,
            onLabsExpandedChange = { labsExpanded = it },
            onQueryChange = onQueryChange,
            onDismiss = onDismiss,
            modifier = Modifier.fillMaxHeight(if (LocalDensity.current.fontScale > 1.5f) 0.9f else 0.75f),
        )
    }
}

@Composable
private fun ModelFilterSheetContent(
    models: List<ModelSummary>,
    query: ModelListQuery,
    draft: ModelListQuery,
    onDraftChange: (ModelListQuery) -> Unit,
    labsExpanded: Boolean,
    onLabsExpandedChange: (Boolean) -> Unit,
    onQueryChange: (ModelListQuery) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewQuery = draft.copy(search = query.search)
    val labModelCounts = remember(models) { countModelsByLab(models) }
    val labs = remember(labModelCounts, draft.labs) {
        sortFilterLabs((labModelCounts.keys + draft.labs).toList(), labModelCounts)
    }
    val visibleLabs = if (labsExpanded) labs else {
        val selected = labs.filter { it in draft.labs }
        selected + labs.filterNot { it in draft.labs }.take((8 - selected.size).coerceAtLeast(0))
    }
    val inputs = remember(models, draft.inputModalities) {
        (models.flatMap { it.inputModalities.orEmpty() } + draft.inputModalities).distinct().sorted()
    }
    val outputs = remember(models, draft.outputModalities) {
        (models.flatMap { it.outputModalities.orEmpty() } + draft.outputModalities).distinct().sorted()
    }
    val matchCount = remember(models, previewQuery) { filterModels(models, previewQuery).size }
    Column(
        modifier = modifier.fillMaxWidth()
            .padding(horizontal = 20.dp).testTag("model-filter-sheet"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.model_filter_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        Text(
            stringResource(R.string.model_filter_preview_count, matchCount, models.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp).testTag("filter-result-count"),
        )
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            FilterSection(
                title = stringResource(R.string.model_filter_labs),
                action = if (labs.size > 8) {
                    {
                        TextButton(onClick = { onLabsExpandedChange(!labsExpanded) }, modifier = Modifier.testTag("filter-labs-more")) {
                            Text(stringResource(if (labsExpanded) R.string.model_filter_less else R.string.model_filter_more))
                        }
                    }
                } else null,
            ) {
                FilterOption(draft.labs.isEmpty(), stringResource(R.string.model_filter_all), { onDraftChange(draft.copy(labs = emptySet())) })
                visibleLabs.forEach { lab ->
                    FilterOption(lab in draft.labs, labDisplayName(lab), { onDraftChange(draft.copy(labs = draft.labs.toggle(lab))) }, "filter-lab:$lab")
                }
            }
            FilterSection(stringResource(R.string.model_filter_weights)) {
                WeightFilter.entries.forEach { weights ->
                    FilterOption(draft.weights == weights, weightLabel(weights), { onDraftChange(draft.copy(weights = weights)) }, "filter-weights:$weights")
                }
            }
            ModalityFilters(
                title = stringResource(R.string.model_filter_input),
                options = inputs, selected = draft.inputModalities, tag = "input",
                onChange = { onDraftChange(draft.copy(inputModalities = it)) },
            )
            ModalityFilters(
                title = stringResource(R.string.model_filter_output),
                options = outputs, selected = draft.outputModalities, tag = "output",
                onChange = { onDraftChange(draft.copy(outputModalities = it)) },
            )
            TokenFilters(
                title = stringResource(R.string.model_filter_context),
                options = listOf(32_000L, 128_000L, 200_000L, 1_000_000L),
                selected = draft.minContextTokens, tag = "context",
                onChange = { onDraftChange(draft.copy(minContextTokens = it)) },
            )
            TokenFilters(
                title = stringResource(R.string.model_filter_output_limit),
                options = listOf(4_000L, 16_000L, 32_000L, 128_000L),
                selected = draft.minOutputTokens, tag = "output-limit",
                onChange = { onDraftChange(draft.copy(minOutputTokens = it)) },
            )
            FilterSection(stringResource(R.string.model_filter_price)) {
                listOf(null, true, false).forEach { hasPrice ->
                    FilterOption(
                        selected = draft.hasPrice == hasPrice,
                        label = stringResource(when (hasPrice) {
                            null -> R.string.model_filter_all
                            true -> R.string.model_filter_price_yes
                            false -> R.string.model_filter_price_no
                        }),
                        onClick = { onDraftChange(draft.copy(hasPrice = hasPrice)) },
                        tag = "filter-price:$hasPrice",
                    )
                }
            }
            Text(
                stringResource(R.string.model_filter_rules) + "\n" + stringResource(R.string.model_filter_price_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp).height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { onDraftChange(draft.resetFilters()) },
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 56.dp).testTag("filter-reset"),
            ) { Text(stringResource(R.string.model_filter_reset)) }
            Button(
                onClick = {
                    onQueryChange(previewQuery)
                    onDismiss()
                },
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 56.dp).testTag("filter-apply"),
            ) { Text(stringResource(R.string.model_filter_apply)) }
        }
    }
}

@Composable
private fun FilterSection(title: String, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            action?.invoke()
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun FilterOption(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    tag: String? = null,
    icon: Int? = null,
    description: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = icon?.let { { Icon(painterResource(it), null, modifier = Modifier.size(18.dp)) } },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.surfaceContainerLow,
            labelColor = colors.onSurface,
            selectedContainerColor = colors.secondaryContainer.copy(alpha = 0.5f),
            selectedLabelColor = colors.onSecondaryContainer,
            selectedLeadingIconColor = colors.onSecondaryContainer,
        ),
        border = BorderStroke(1.dp, if (selected) colors.primary else Color.Transparent),
        modifier = (if (tag == null) Modifier else Modifier.testTag(tag))
            .then(if (description == null) Modifier else Modifier.semantics { contentDescription = description }),
    )
}

@Composable
private fun ModalityFilters(
    title: String,
    options: List<String>,
    selected: Set<String>,
    tag: String,
    onChange: (Set<String>) -> Unit,
) {
    FilterSection(title) {
        FilterOption(selected.isEmpty(), stringResource(R.string.model_filter_all), { onChange(emptySet()) })
        options.forEach { modality ->
            FilterOption(
                modality in selected, formatModality(modality), { onChange(selected.toggle(modality)) },
                "filter-$tag:$modality", icon = modalityIcon(modality),
            )
        }
    }
}

@Composable
private fun TokenFilters(
    title: String,
    options: List<Long>,
    selected: Long?,
    tag: String,
    onChange: (Long?) -> Unit,
) {
    FilterSection(title) {
        FilterOption(selected == null, stringResource(R.string.model_filter_all), { onChange(null) })
        (options + listOfNotNull(selected)).distinct().sorted().forEach { tokens ->
            FilterOption(
                selected == tokens, "≥ ${formatFilterTokenCount(tokens)}", { onChange(tokens) }, "filter-$tag:$tokens",
                description = stringResource(R.string.model_filter_token_minimum, formatTokenCount(tokens)),
            )
        }
    }
}

@Composable
private fun weightLabel(weights: WeightFilter): String = stringResource(when (weights) {
    WeightFilter.ALL -> R.string.model_filter_all
    WeightFilter.OPEN -> R.string.model_list_open_weights_yes
    WeightFilter.CLOSED -> R.string.model_list_open_weights_no
    WeightFilter.UNKNOWN -> R.string.no_data
})

private fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value

@Preview(name = "Controls · Light", showBackground = true, widthDp = 360)
@Preview(name = "Controls · Dark", showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ModelCatalogControlsPreview() {
    ModelAtlasTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                var query by remember { mutableStateOf(ModelListQuery()) }
                ModelCatalogControls(query = query, models = emptyList(), onQueryChange = { query = it }, onShowFilters = {})
            }
        }
    }
}

@Preview(name = "Active filters · Light", showBackground = true, widthDp = 360)
@Preview(name = "Active filters · Dark", showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Active filters · Large font", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun ModelCatalogActiveFiltersPreview() {
    ModelAtlasTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                var query by remember { mutableStateOf(filterPreviewQuery()) }
                ModelCatalogControls(query = query, models = emptyList(), onQueryChange = { query = it }, onShowFilters = {})
            }
        }
    }
}

@Preview(name = "Filter sheet · Light", widthDp = 360, heightDp = 800)
@Preview(name = "Filter sheet · Dark", widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Filter sheet · Large font", widthDp = 320, heightDp = 800, fontScale = 2f)
@Composable
private fun ModelFilterSheetPreview() {
    var draft by remember { mutableStateOf(filterPreviewQuery()) }
    var labsExpanded by remember { mutableStateOf(false) }
    ModelAtlasTheme {
        Surface(shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
            ModelFilterSheetContent(
                models = listOf(
                    ModelSummary(
                        id = "example/atlas-vision", name = "Atlas Vision",
                        contextLimitTokens = 128_000, outputLimitTokens = 32_000,
                        inputModalities = listOf("text", "image"), outputModalities = listOf("text"),
                        openWeights = true,
                    ),
                    ModelSummary(
                        id = "sample/atlas-audio", name = "Atlas Audio",
                        contextLimitTokens = 32_000, outputLimitTokens = 4_000,
                        inputModalities = listOf("text", "audio"), outputModalities = listOf("text", "audio"),
                        openWeights = false,
                    ),
                ),
                query = filterPreviewQuery(),
                draft = draft,
                onDraftChange = { draft = it },
                labsExpanded = labsExpanded,
                onLabsExpandedChange = { labsExpanded = it },
                onQueryChange = {},
                onDismiss = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun filterPreviewQuery() = ModelListQuery(
    search = "Atlas",
    labs = setOf("example"),
    weights = WeightFilter.OPEN,
    inputModalities = setOf("image"),
    minContextTokens = 128_000,
)
