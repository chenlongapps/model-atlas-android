package io.github.chenlongapps.modelatlas.ui.modeldetail

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelBenchmark
import io.github.chenlongapps.modelatlas.data.model.ModelDetailMetadata
import io.github.chenlongapps.modelatlas.data.model.ModelDetailOffering
import io.github.chenlongapps.modelatlas.data.model.ModelDetailPrice
import io.github.chenlongapps.modelatlas.ui.modellist.formatModality
import io.github.chenlongapps.modelatlas.ui.modellist.formatTokenCount
import io.github.chenlongapps.modelatlas.ui.modellist.formatUsdPerMillionTokens
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun ModelDetailRoute(modelId: String, onBack: () -> Unit, viewModel: ModelDetailViewModel = hiltViewModel()) {
    DisposableEffect(viewModel, modelId) {
        viewModel.open(modelId)
        onDispose { viewModel.close(modelId) }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ModelDetailScreen(
        state.takeIf { it.modelId == modelId } ?: ModelDetailUiState(modelId),
        onBack = onBack, onRetry = viewModel::retry,
    )
}

@Composable
internal fun ModelDetailScreen(
    uiState: ModelDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    windowInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
) {
    key(uiState.modelId) {
        ModelDetailBody(uiState, onBack, modifier, onRetry, windowInsets)
    }
}

private enum class DetailTab(val label: Int) {
    OVERVIEW(R.string.detail_tab_overview),
    PROVIDERS(R.string.detail_tab_prices),
    CAPABILITIES(R.string.detail_tab_capabilities),
    PARAMETERS(R.string.detail_tab_parameters),
    BENCHMARKS(R.string.detail_tab_benchmarks),
}

@Composable
private fun ModelDetailBody(
    uiState: ModelDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier,
    onRetry: () -> Unit,
    windowInsets: WindowInsets,
) {
    var selectedTab by rememberSaveable { mutableStateOf(DetailTab.OVERVIEW) }
    // 惰性内容的构建／测量可能晚于点击；捕获本次组合的标签，避免旧列表先测量新标签正文而夹回滚动位置。
    val activeTab = selectedTab
    // 列表状态留在标签内容外，避免切换／恢复时惰性布局的首次测量重置锚点。
    val overviewListState = rememberLazyListState()
    val providersListState = rememberLazyListState()
    val capabilitiesListState = rememberLazyListState()
    val parametersListState = rememberLazyListState()
    val benchmarksListState = rememberLazyListState()
    val statusListState = rememberLazyListState()
    // 展开状态放在惰性条目外；即使条目离屏、切换标签或恢复时尚未测量，也不会丢失。
    var expandedSections by rememberSaveable(stateSaver = Saver<Set<String>, ArrayList<String>>(
        save = { ArrayList(it) }, restore = { it.toSet() },
    )) { mutableStateOf(emptySet()) }
    val onToggleSection: (String) -> Unit = { section ->
        expandedSections = if (section in expandedSections) expandedSections - section else expandedSections + section
    }
    val stateHolder = rememberSaveableStateHolder()
    val layoutDirection = LocalLayoutDirection.current
    val background = MaterialTheme.colorScheme.surfaceContainerLowest
    Scaffold(
        modifier = modifier.testTag("model-detail-screen").drawWithContent {
            drawContent()
            val height = windowInsets.getTop(this) * 1.2f
            if (height > 0f) drawRect(
                Brush.verticalGradient(listOf(background.copy(alpha = 0.8f), background.copy(alpha = 0.4f), Color.Transparent),
                    endY = height),
                size = Size(size.width, height),
            )
        },
        containerColor = background,
        contentWindowInsets = windowInsets,
    ) { padding ->
        val hasContent = uiState.status == ModelDetailStatus.CONTENT
        // 每个标签保留自己的布局节点，避免复用上一标签的测量／锚点缓存。
        key(if (hasContent) activeTab.name else "status") {
            stateHolder.SaveableStateProvider(if (hasContent) activeTab.name else "status") {
                LazyColumn(
                    state = if (!hasContent) statusListState else when (activeTab) {
                        DetailTab.OVERVIEW -> overviewListState
                        DetailTab.PROVIDERS -> providersListState
                        DetailTab.CAPABILITIES -> capabilitiesListState
                        DetailTab.PARAMETERS -> parametersListState
                        DetailTab.BENCHMARKS -> benchmarksListState
                    },
                    modifier = Modifier.fillMaxSize().testTag("model-detail-content").consumeWindowInsets(padding),
                    // 顶部安全区在头图内部避让，背景可延伸到状态栏；正文与末尾仍避让侧边和底部。
                    contentPadding = PaddingValues(
                        start = padding.calculateStartPadding(layoutDirection),
                        end = padding.calculateEndPadding(layoutDirection),
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "header") {
                        ModelDetailHeader(uiState.detail.takeIf { hasContent }, onBack, padding.calculateTopPadding())
                    }
                    if (hasContent) item(key = "tabs") {
                        DetailTabs(activeTab, onSelect = { selectedTab = it })
                    }
                    when (uiState.status) {
                        ModelDetailStatus.LOADING -> item(key = "loading") {
                            DetailSection(stringResource(R.string.detail_loading)) { CircularProgressIndicator() }
                        }
                        ModelDetailStatus.ERROR -> item(key = "error") {
                            DetailSection(stringResource(R.string.model_list_error_title)) {
                                Text(stringResource(R.string.detail_read_error))
                                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                            }
                        }
                        ModelDetailStatus.NOT_FOUND -> item(key = "missing") {
                            DetailSection(stringResource(R.string.detail_missing_title)) {
                                Text(stringResource(R.string.detail_missing))
                                Button(onClick = onBack) { Text(stringResource(R.string.navigate_back)) }
                            }
                        }
                        ModelDetailStatus.CONTENT -> uiState.detail?.let { model ->
                            when (activeTab) {
                                DetailTab.OVERVIEW -> overviewItems(model)
                                DetailTab.PROVIDERS -> providerItems(model, expandedSections, onToggleSection)
                                DetailTab.CAPABILITIES -> item(key = "capabilities") {
                                    DetailSection(stringResource(R.string.detail_capabilities)) { MetadataCapabilities(model.metadata) }
                                }
                                DetailTab.PARAMETERS -> item(key = "parameters") {
                                    DetailSection(stringResource(R.string.detail_parameters)) {
                                        DetailField(stringResource(R.string.detail_model_id), model.id)
                                        DetailField(stringResource(R.string.detail_family), model.metadata.family)
                                        MetadataParameters(model.metadata)
                                    }
                                }
                                DetailTab.BENCHMARKS -> benchmarkItems(model)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTabs(selected: DetailTab, onSelect: (DetailTab) -> Unit) {
    val minimumTabWidth = 64.dp * LocalDensity.current.fontScale
    val background = MaterialTheme.colorScheme.surfaceContainerLowest
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("model-detail-tabs")) {
        val scrollable = maxWidth < minimumTabWidth * DetailTab.entries.size
        val tabs: @Composable () -> Unit = {
            DetailTab.entries.forEach { tab ->
                Tab(
                    selected = selected == tab, onClick = { onSelect(tab) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("detail-tab:${tab.name}"),
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        stringResource(tab.label),
                        if (scrollable) Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
                        else Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = if (scrollable) TextAlign.Start else TextAlign.Center,
                    )
                }
            }
        }
        if (!scrollable) {
            PrimaryTabRow(selected.ordinal, containerColor = background, tabs = tabs)
        } else {
            PrimaryScrollableTabRow(selected.ordinal, containerColor = background,
                edgePadding = 0.dp, minTabWidth = minimumTabWidth, tabs = tabs)
        }
    }
}

private fun LazyListScope.overviewItems(model: ModelDetail) {
    item(key = "weights") { WeightsSection(model) }
    item(key = "links") { LinksSection(model) }
    item(key = "source") {
        DetailSection(stringResource(R.string.detail_source)) {
            DetailField(stringResource(R.string.detail_source_name), model.source)
            DetailField(stringResource(R.string.detail_source_updated), model.metadata.sourceLastUpdated)
            DetailField(stringResource(R.string.detail_fetched), formatFetchedTime(model.fetchedAtEpochMillis))
            Text(stringResource(R.string.detail_source_notice), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun LazyListScope.providerItems(model: ModelDetail, expandedSections: Set<String>, onToggleSection: (String) -> Unit) {
    item(key = "providers") {
        DetailSection(stringResource(R.string.detail_providers)) {
            Text(stringResource(R.string.detail_price_notice), style = MaterialTheme.typography.bodySmall)
            if (model.providers.isEmpty()) Text(stringResource(R.string.detail_no_providers))
        }
    }
    model.providers.forEach { provider ->
        item(key = "provider:${provider.id}") {
            DetailSection(provider.name) {
                DetailField(stringResource(R.string.detail_provider_id), provider.id)
                provider.documentationUrl?.let { DetailLink(stringResource(R.string.detail_documentation), it) }
            }
        }
        provider.offerings.forEach { offering ->
            item(key = "offering:${provider.id.length}:${provider.id}${offering.id}") {
                OfferingSection(offering, provider.id, expandedSections, onToggleSection)
            }
        }
    }
}

private fun LazyListScope.benchmarkItems(model: ModelDetail) {
    item(key = "benchmark-notice") {
        DetailSection(stringResource(R.string.detail_tab_benchmarks)) {
            Text(stringResource(R.string.detail_benchmarks_notice), style = MaterialTheme.typography.bodySmall)
            if (model.benchmarks == null) Text(stringResource(R.string.detail_benchmarks_missing))
            else if (model.benchmarks.isEmpty()) Text(stringResource(R.string.detail_benchmarks_empty))
        }
    }
    // 评测没有稳定 ID，保留来源顺序及同名、不同配置的所有记录。
    model.benchmarks?.forEachIndexed { index, benchmark ->
        item(key = "benchmark:$index") { BenchmarkSection(benchmark) }
    }
}

@Composable
private fun BenchmarkSection(benchmark: ModelBenchmark) {
    DetailSection(benchmark.name.takeIf(String::isNotBlank) ?: stringResource(R.string.no_data)) {
        DetailField(stringResource(R.string.detail_benchmark_score), benchmark.score.toString())
        DetailField(stringResource(R.string.detail_benchmark_metric), benchmark.metric)
        val source = benchmark.source?.takeIf(String::isNotBlank)
        // source 也可能是出处名称而非网址；普通文字保留原文，不伪造地址。
        if (source != null && isSafeDetailUrl(source)) {
            DetailLink(stringResource(R.string.detail_benchmark_source), source)
        } else {
            DetailField(stringResource(R.string.detail_benchmark_source), source)
        }
        DetailField(stringResource(R.string.detail_benchmark_date), benchmark.date)
        DetailField(stringResource(R.string.detail_benchmark_version), benchmark.version)
        DetailField(stringResource(R.string.detail_benchmark_variant), benchmark.variant)
        DetailField(stringResource(R.string.detail_benchmark_dataset), benchmark.dataset)
        DetailField(stringResource(R.string.detail_benchmark_harness), benchmark.harness)
    }
}

@Composable
private fun WeightsSection(model: ModelDetail) {
    DetailSection(stringResource(R.string.detail_weights)) {
        DetailField(stringResource(R.string.model_filter_weights), weightStatus(model.metadata.openWeights))
        DetailField(stringResource(R.string.detail_license), model.license)
        Text(stringResource(R.string.detail_weights_notice), style = MaterialTheme.typography.bodySmall)
        when {
            model.weights == null -> Text(stringResource(R.string.no_data))
            model.weights.isEmpty() -> Text(stringResource(R.string.model_list_no_modalities))
            else -> model.weights.forEach { weight ->
                DetailLink(weight.label?.takeIf(String::isNotBlank) ?: stringResource(R.string.detail_weight_link), weight.url)
                DetailField(stringResource(R.string.detail_format), weight.format)
                DetailField(stringResource(R.string.detail_quantization), weight.quantization)
            }
        }
    }
}

@Composable
private fun LinksSection(model: ModelDetail) {
    DetailSection(stringResource(R.string.detail_links)) {
        when {
            model.links == null -> Text(stringResource(R.string.no_data))
            model.links.isEmpty() -> Text(stringResource(R.string.model_list_no_modalities))
            else -> model.links.forEach { link ->
                DetailLink(link.label?.takeIf(String::isNotBlank) ?: link.type?.takeIf(String::isNotBlank)
                    ?: stringResource(R.string.detail_reference_link), link.url)
            }
        }
    }
}

@Composable
private fun OfferingSection(
    offering: ModelDetailOffering,
    providerId: String,
    expandedSections: Set<String>,
    onToggleSection: (String) -> Unit,
) {
    val sectionKey = "offering:${providerId.length}:$providerId${offering.id.length}:${offering.id}"
    val expanded = "$sectionKey:parameters" in expandedSections
    DetailSection(offering.name) {
        DetailField(stringResource(R.string.detail_service_id), offering.id)
        DetailField(stringResource(R.string.detail_service_status), offering.status)
        Text(stringResource(R.string.detail_base_price), style = MaterialTheme.typography.titleSmall)
        DetailPrice(offering.price, expanded = "$sectionKey:base" in expandedSections,
            onToggle = { onToggleSection("$sectionKey:base") })
        offering.modes?.toSortedMap()?.forEach { (mode, price) ->
            key(mode) {
                HorizontalDivider()
                Text(stringResource(R.string.detail_mode, mode), style = MaterialTheme.typography.titleSmall)
                DetailPrice(price, expanded = "$sectionKey:mode:$mode" in expandedSections,
                    onToggle = { onToggleSection("$sectionKey:mode:$mode") })
            }
        }
        val state = stringResource(if (expanded) R.string.model_list_price_details_expanded else R.string.model_list_price_details_collapsed)
        TextButton(
            onClick = { onToggleSection("$sectionKey:parameters") },
            modifier = Modifier.testTag("service-details:$providerId:${offering.id}").semantics { stateDescription = state },
        ) { Text(stringResource(if (expanded) R.string.detail_service_collapse else R.string.detail_service_expand)) }
        if (expanded) {
            Text(stringResource(R.string.detail_service_notice), style = MaterialTheme.typography.bodySmall)
            DetailField(stringResource(R.string.detail_description), offering.metadata.description)
            DetailField(stringResource(R.string.detail_family), offering.metadata.family)
            MetadataParameters(offering.metadata)
            MetadataCapabilities(offering.metadata)
            DetailField(stringResource(R.string.model_filter_weights), weightStatus(offering.metadata.openWeights))
            DetailField(stringResource(R.string.detail_interleaved), when (offering.interleaved?.supported) {
                true -> stringResource(R.string.detail_supported)
                false -> stringResource(R.string.detail_unsupported)
                null -> offering.interleaved?.field
            })
            Text(stringResource(R.string.detail_reasoning_options), style = MaterialTheme.typography.titleSmall)
            when {
                offering.reasoningOptions == null -> Text(stringResource(R.string.no_data))
                offering.reasoningOptions.isEmpty() -> Text(stringResource(R.string.detail_no_reasoning_control))
                else -> offering.reasoningOptions.forEach { option ->
                    DetailField(stringResource(R.string.detail_option_type), option.type)
                    DetailField(stringResource(R.string.detail_option_min), option.min?.let(::formatTokenCount))
                    DetailField(stringResource(R.string.detail_option_max), option.max?.let(::formatTokenCount))
                    DetailField(stringResource(R.string.detail_option_values), option.values?.let {
                        if (it.isEmpty()) stringResource(R.string.model_list_no_modalities)
                        else it.joinToString(" · ") { value -> value ?: "null" }
                    })
                }
            }
            DetailField(stringResource(R.string.detail_source_name), offering.source)
            DetailField(stringResource(R.string.detail_source_updated), offering.metadata.sourceLastUpdated)
            DetailField(stringResource(R.string.detail_fetched), formatFetchedTime(offering.fetchedAtEpochMillis))
        }
    }
}

@Composable
private fun DetailPrice(price: ModelDetailPrice?, showTiers: Boolean = true, expanded: Boolean = false, onToggle: () -> Unit = {}) {
    Text(stringResource(R.string.model_list_price_unit), style = MaterialTheme.typography.labelSmall)
    if (price == null) {
        Text(stringResource(R.string.model_list_price_missing))
        return
    }
    val metrics = listOf(
        R.string.model_list_input_label to price.input,
        R.string.model_list_output_label to price.output,
        R.string.model_list_price_cache_read_label to price.cacheRead,
        R.string.detail_cache_write to price.cacheWrite,
        R.string.detail_reasoning to price.reasoning,
        R.string.detail_input_audio to price.inputAudio,
        R.string.detail_output_audio to price.outputAudio,
    ).filterIndexed { index, (_, value) -> index < 2 || value != null }
    val minimumWidth = 112.dp * LocalDensity.current.fontScale
    BoxWithConstraints {
        val columns = (maxWidth / minimumWidth).toInt().coerceIn(1, 3)
        FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = columns,
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            metrics.forEach { (label, value) ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(label), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value?.let(::formatUsdPerMillionTokens) ?: stringResource(R.string.no_data),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    if (showTiers && !price.tiers.isNullOrEmpty()) {
        val state = stringResource(if (expanded) R.string.model_list_price_details_expanded else R.string.model_list_price_details_collapsed)
        TextButton(onClick = onToggle, modifier = Modifier.semantics { stateDescription = state }) {
            Text(stringResource(if (expanded) R.string.model_list_price_details_collapse else R.string.model_list_price_details_expand))
        }
        if (expanded) price.tiers.forEach { tier ->
            HorizontalDivider()
            Text(
                stringResource(if (tier.type == "context") R.string.detail_tier else R.string.detail_unknown_tier,
                    if (tier.type == "context") stringResource(R.string.model_list_context_label) else tier.type,
                    formatTokenCount(tier.size)),
                style = MaterialTheme.typography.titleSmall,
            )
            DetailPrice(tier.price, showTiers = false)
        }
    }
}

@Composable
private fun MetadataParameters(metadata: ModelDetailMetadata) {
    DetailField(stringResource(R.string.model_list_release_date_label), metadata.releaseDate)
    DetailField(stringResource(R.string.detail_knowledge_cutoff), metadata.knowledgeCutoff)
    DetailField(stringResource(R.string.model_list_context_label), tokens(metadata.contextLimitTokens))
    DetailField(stringResource(R.string.detail_input_limit), tokens(metadata.inputLimitTokens))
    DetailField(stringResource(R.string.model_list_output_limit_label), tokens(metadata.outputLimitTokens))
}

@Composable
private fun MetadataCapabilities(metadata: ModelDetailMetadata) {
    DetailField(stringResource(R.string.model_filter_input), modalities(metadata.inputModalities))
    DetailField(stringResource(R.string.model_filter_output), modalities(metadata.outputModalities))
    listOf(
        R.string.detail_attachment to metadata.attachment,
        R.string.detail_reasoning to metadata.reasoning,
        R.string.detail_tool_call to metadata.toolCall,
        R.string.detail_structured_output to metadata.structuredOutput,
        R.string.detail_temperature to metadata.temperature,
    ).forEach { (label, value) -> DetailField(stringResource(label), when (value) {
        true -> stringResource(R.string.detail_supported)
        false -> stringResource(R.string.detail_unsupported)
        null -> null
    }) }
}

@Composable
private fun modalities(values: List<String>?): String? = values?.let {
    if (it.isEmpty()) stringResource(R.string.model_list_no_modalities) else it.map { formatModality(it) }.joinToString(" · ")
}

private fun tokens(value: Long?) = value?.let { "${formatTokenCount(it)} tokens" }

@Composable
private fun weightStatus(value: Boolean?): String? = when (value) {
    true -> stringResource(R.string.model_list_open_weights_yes)
    false -> stringResource(R.string.model_list_open_weights_no)
    null -> null
}

internal fun formatFetchedTime(epochMillis: Long): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
    .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis))

@Composable
private fun DetailField(label: String, value: String?) {
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.takeIf(String::isNotBlank) ?: stringResource(R.string.no_data), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Preview(name = "Model detail · Light", showBackground = true, widthDp = 400)
@Preview(name = "Model detail · Dark", showBackground = true, widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Model detail · Narrow", showBackground = true, widthDp = 320)
@Preview(name = "Model detail · Large font", showBackground = true, widthDp = 320, fontScale = 2f)
@Preview(name = "Model detail · Dark large font", showBackground = true, widthDp = 320, fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ModelDetailPreview() {
    ModelAtlasTheme(dynamicColor = false) {
        ModelDetailScreen(
            ModelDetailUiState("deepseek/example", ModelDetailStatus.CONTENT, ModelDetail(
                id = "deepseek/example", name = "DeepSeek V4.1 Flash 1017", fetchedAtEpochMillis = 0,
                metadata = ModelDetailMetadata(
                    description = "用于预览的模型描述，展示文本与图片输入、长模型名称及完整参数的排版。",
                    releaseDate = "2026-10-17", contextLimitTokens = 1_000_000, outputLimitTokens = 384_000,
                    inputModalities = listOf("text", "image"), outputModalities = listOf("text"),
                    openWeights = true, reasoning = true, toolCall = true,
                ),
                license = "MIT",
            )), onBack = {},
        )
    }
}
