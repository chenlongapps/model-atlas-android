package io.github.chenlongapps.modelatlas.ui.modellist

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.data.model.ModelPriceTierSummary
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import java.math.BigDecimal

@Composable
fun ModelListRoute(
    modifier: Modifier = Modifier,
    viewModel: ModelListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ModelListScreen(uiState = uiState, onRetry = viewModel::retry, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelListScreen(
    uiState: ModelListUiState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    val layoutDirection = LocalLayoutDirection.current
    var showLicense by rememberSaveable { mutableStateOf(false) }
    // 展开状态提到列表层，避免滚动回收卡片后丢失，同时便于状态恢复。
    var expandedPriceModelId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = { showLicense = true }) {
                        Text(stringResource(R.string.data_source_action))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                top = innerPadding.calculateTopPadding() + 16.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                uiState.isLoading -> item(key = "loading") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.model_list_loading))
                    }
                }
                uiState.error != null -> item(key = "error") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val message = when (uiState.error) {
                            ModelListError.NETWORK -> R.string.model_list_network_error
                            ModelListError.SERVER -> R.string.model_list_server_error
                            ModelListError.INVALID_DATA -> R.string.model_list_invalid_data
                            ModelListError.RATE_LIMITED -> R.string.model_list_rate_limited
                            ModelListError.STORAGE -> R.string.model_list_storage_error
                        }
                        Text(stringResource(message))
                        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    }
                }
                uiState.models.isEmpty() -> item(key = "empty") {
                    Text(stringResource(R.string.model_list_empty))
                }
            }
            items(items = uiState.models, key = { "model:${it.id}" }) { model ->
                ModelCard(
                    model = model,
                    priceDetailsExpanded = expandedPriceModelId == model.id,
                    onTogglePriceDetails = {
                        expandedPriceModelId =
                            if (expandedPriceModelId == model.id) null else model.id
                    },
                )
            }
            if (uiState.models.isNotEmpty()) {
                item(key = "price-notice") {
                    Text(
                        text = stringResource(R.string.model_list_price_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (showLicense) {
        val resources = LocalResources.current
        val license = remember(resources) {
            resources.openRawResource(R.raw.models_dev_license).bufferedReader().use { it.readText() }
        }
        AlertDialog(
            onDismissRequest = { showLicense = false },
            title = { Text(stringResource(R.string.data_source_action)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.model_list_source_notice))
                    Text(stringResource(R.string.model_list_price_notice))
                    Text(license, modifier = Modifier.padding(top = 16.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicense = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
private fun ModelCard(
    model: ModelSummary,
    priceDetailsExpanded: Boolean,
    onTogglePriceDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // LAB logo 与模型名并列；没有随包素材时显示兜底图。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LabLogo(lab = model.lab)
                Text(text = model.name, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = stringResource(
                    R.string.model_list_release_date,
                    model.releaseDate ?: stringResource(R.string.no_data),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.model_list_context_limit, formatTokenLimit(model.contextLimitTokens)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.model_list_output_limit, formatTokenLimit(model.outputLimitTokens)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.model_list_input_modalities, formatModalities(model.inputModalities)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.model_list_output_modalities, formatModalities(model.outputModalities)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.model_list_open_weights, formatOpenWeights(model.openWeights)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 仅在来源报告权重公开时附带许可，便于进一步核对；权重未公开或缺失时不拼造许可信息。
            val license = model.license
            if (model.openWeights == true && license != null) {
                Text(
                    text = stringResource(R.string.model_list_license, license),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val price = model.price
            if (price == null) {
                Text(
                    text = stringResource(R.string.model_list_price_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                PriceLine(R.string.model_list_price_input, price.input)
                PriceLine(R.string.model_list_price_output, price.output)
                // 缓存、推理与音频单价只在来源提供时展示，缺失不补造零价。
                price.cacheRead?.let { PriceLine(R.string.model_list_price_cache_read, it) }
                price.cacheWrite?.let { PriceLine(R.string.model_list_price_cache_write, it) }
                price.reasoning?.let { PriceLine(R.string.model_list_price_reasoning, it) }
                price.inputAudio?.let { PriceLine(R.string.model_list_price_input_audio, it) }
                price.outputAudio?.let { PriceLine(R.string.model_list_price_output_audio, it) }
                if (price.tiers.isNotEmpty()) {
                    TextButton(onClick = onTogglePriceDetails) {
                        Text(
                            stringResource(
                                if (priceDetailsExpanded) {
                                    R.string.model_list_price_details_collapse
                                } else {
                                    R.string.model_list_price_details_expand
                                },
                            ),
                        )
                    }
                    if (priceDetailsExpanded) {
                        PriceTiers(price.tiers)
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceLine(labelRes: Int, value: Double?, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(labelRes, formatPrice(value)),
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 上下文档位按来源原值展示，不选择档位、不解释门槛等号归属或累进计费规则。 */
@Composable
private fun PriceTiers(
    tiers: List<ModelPriceTierSummary>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tiers.forEach { tier ->
            Text(
                text = stringResource(
                    R.string.model_list_price_tier,
                    tier.conditionSizeTokens.toString(),
                    formatPrice(tier.input),
                    formatPrice(tier.output),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun formatOpenWeights(openWeights: Boolean?): String = when (openWeights) {
    true -> stringResource(R.string.model_list_open_weights_yes)
    false -> stringResource(R.string.model_list_open_weights_no)
    null -> stringResource(R.string.no_data)
}

@Composable
private fun formatTokenLimit(tokens: Long?): String = tokens?.let {
    stringResource(R.string.model_list_tokens, it.toString())
} ?: stringResource(R.string.no_data)

@Composable
private fun formatPrice(price: Double?): String = price?.let {
    formatUsdPerMillionTokens(it)
} ?: stringResource(R.string.no_data)

/**
 * 单价口径固定为 USD / 百万 tokens；只做显示格式化，不换算币种或数量单位。
 *
 * 至少保留两位小数（`0.6` → `$0.60`），来源本身更精确时保留更多位（`0.003` → `$0.003`），
 * 因此不会把高精度报价抹成 `$0.00`。
 */
private fun formatUsdPerMillionTokens(value: Double): String {
    if (value == 0.0) return "$0.00"
    val plain = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    val dot = plain.indexOf('.')
    if (dot < 0) return "$" + plain + ".00"
    val decimals = plain.length - dot - 1
    return if (decimals >= 2) "$$plain" else "$" + plain + "0".repeat(2 - decimals)
}

@Composable
private fun formatModalities(modalities: List<String>?): String = when {
    modalities == null -> stringResource(R.string.no_data)
    modalities.isEmpty() -> stringResource(R.string.model_list_no_modalities)
    else -> modalities.map { modality ->
        when (modality) {
            "text" -> stringResource(R.string.modality_text)
            "image" -> stringResource(R.string.modality_image)
            "audio" -> stringResource(R.string.modality_audio)
            "video" -> stringResource(R.string.modality_video)
            "pdf" -> "PDF"
            else -> modality
        }
    }.joinToString("、")
}

@Preview(name = "Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ModelListScreenPreview() {
    ModelAtlasTheme {
        ModelListScreen(
            uiState = ModelListUiState(
                models = listOf(
                    ModelSummary(
                        id = "deepseek/deepseek-v4.1-flash-1017",
                        name = "DeepSeek V4.1 Flash 1017",
                        releaseDate = "2026-10-17",
                        contextLimitTokens = 1_000_000,
                        outputLimitTokens = 384_000,
                        inputModalities = listOf("text", "image"),
                        outputModalities = listOf("text"),
                        openWeights = true,
                        license = "MIT",
                        price = ModelPriceSummary(
                            providerId = "deepseek",
                            providerName = "DeepSeek",
                            isOfficial = true,
                            input = 0.60,
                            output = 0.15,
                            cacheRead = 0.003,
                            tiers = listOf(
                                ModelPriceTierSummary(
                                    conditionType = "context",
                                    conditionSizeTokens = 272_000,
                                    input = 0.3,
                                    output = 1.2,
                                ),
                            ),
                        ),
                    ),
                    ModelSummary(
                        id = "meta/muse-spark-open",
                        name = "Muse Spark Open",
                        releaseDate = "2026-10-15",
                        contextLimitTokens = 1_048_576,
                        outputLimitTokens = 131_072,
                        inputModalities = listOf("text", "image", "video", "audio"),
                        outputModalities = listOf("text"),
                        openWeights = true,
                        license = "Apache-2.0",
                        price = ModelPriceSummary(
                            providerId = "openrouter",
                            providerName = "OpenRouter",
                            isOfficial = false,
                            input = 0.1,
                            output = 0.2,
                        ),
                    ),
                    ModelSummary(
                        id = "nvidia/nemotron-3.5-super-vl-120b-a12b",
                        name = "Nemotron 3.5 Super VL 120B A12B",
                        releaseDate = "2026-10-15",
                        contextLimitTokens = 1_048_576,
                        outputLimitTokens = 8_192,
                        inputModalities = listOf("text", "image", "video"),
                        outputModalities = listOf("text"),
                        openWeights = false,
                    ),
                    // 没有随包 logo 素材的 LAB 显示兜底图。
                    ModelSummary(
                        id = "acme/atlas-mini",
                        name = "Atlas Mini",
                        releaseDate = "2026-10-01",
                        contextLimitTokens = 128_000,
                        outputLimitTokens = 4_096,
                        inputModalities = listOf("text"),
                        outputModalities = listOf("text"),
                        openWeights = true,
                        license = "Apache-2.0",
                    ),
                ),
                isLoading = false,
            ),
        )
    }
}
