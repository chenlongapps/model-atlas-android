package io.github.chenlongapps.modelatlas.ui.modellist

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.data.model.ModelPriceTierSummary
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun ModelCard(
    model: ModelSummary,
    priceDetailsExpanded: Boolean,
    onTogglePriceDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth().testTag("model:${model.id}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                LabLogo(lab = model.lab)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = model.lab?.let(::labDisplayName) ?: stringResource(R.string.no_data),
                            modifier = Modifier.testTag("model-lab"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.primary,
                        )
                        val weights = formatOpenWeights(model.openWeights)
                        val license = shortLicenseLabel(model.license)
                        InfoBadge(
                            text = if (model.openWeights == true && license != null) {
                                stringResource(R.string.model_list_weights_with_license, weights, license)
                            } else {
                                weights
                            },
                            highlighted = model.openWeights == true,
                        )
                    }

                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactMetric(
                    icon = R.drawable.ic_calendar,
                    label = stringResource(R.string.model_list_release_date_label),
                    value = model.releaseDate ?: stringResource(R.string.no_data),
                    modifier = Modifier.testTag("release-date"),
                )
                CompactMetric(
                    icon = R.drawable.ic_context_window,
                    label = stringResource(R.string.model_list_context_label),
                    value = model.contextLimitTokens?.let(::formatCompactTokenCount) ?: stringResource(R.string.no_data),
                    unit = model.contextLimitTokens?.let { stringResource(R.string.model_list_token_unit) },
                    accessibilityValue = model.contextLimitTokens?.let(::formatTokenCount),
                    modifier = Modifier.testTag("context-limit"),
                )
                CompactMetric(
                    icon = R.drawable.ic_output_limit,
                    label = stringResource(R.string.model_list_output_limit_label),
                    value = model.outputLimitTokens?.let(::formatCompactTokenCount) ?: stringResource(R.string.no_data),
                    unit = model.outputLimitTokens?.let { stringResource(R.string.model_list_token_unit) },
                    accessibilityValue = model.outputLimitTokens?.let(::formatTokenCount),
                    modifier = Modifier.testTag("output-limit"),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                ModalitiesRow(
                    label = stringResource(R.string.model_list_input_label),
                    modalities = model.inputModalities,
                    modifier = Modifier.testTag("input-modalities"),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_forward),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).testTag("modalities-direction"),
                        tint = colors.onSurfaceVariant,
                    )
                    ModalitiesRow(
                        label = stringResource(R.string.model_list_output_label),
                        modalities = model.outputModalities,
                        modifier = Modifier.weight(1f, fill = false).testTag("output-modalities"),
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.testTag("model-price-divider"),
                color = colors.outlineVariant.copy(alpha = 0.5f),
            )
            val price = model.price
            if (price == null) {
                Text(
                    text = stringResource(R.string.model_list_price_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            } else {
                PriceSection(price, priceDetailsExpanded, onTogglePriceDetails)
            }
        }
    }
}

@Composable
private fun CompactMetric(
    @DrawableRes icon: Int,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    accessibilityValue: String? = null,
) {
    Row(
        modifier = modifier.then(
            if (accessibilityValue != null) {
                Modifier.clearAndSetSemantics {
                    contentDescription = "$label：$accessibilityValue${unit?.let { " $it" }.orEmpty()}"
                }
            } else Modifier.semantics(mergeDescendants = true) {},
        ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            // 参数视觉可缩写，行级读屏语义保留字段、完整数值与 token 单位。
            contentDescription = if (unit == null) label else "$label ($unit)",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModalitiesRow(
    label: String,
    modalities: List<String>?,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val values = when {
            modalities == null -> listOf(null to stringResource(R.string.no_data))
            modalities.isEmpty() -> listOf(null to stringResource(R.string.model_list_no_modalities))
            else -> modalities.map { it to formatModality(it) }
        }
        values.forEach { (modality, text) ->
            InfoBadge(
                text = text,
                icon = modalityIcon(modality),
                showText = false,
            )
        }
    }
}

/** 非交互标签，不借用 FilterChip，避免让人误以为可以筛选。 */
@Composable
private fun InfoBadge(
    text: String,
    highlighted: Boolean = false,
    @DrawableRes icon: Int? = null,
    showText: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (highlighted) colors.tertiaryContainer else colors.surfaceContainerLow,
        contentColor = if (highlighted) colors.onTertiaryContainer else colors.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(painterResource(icon), contentDescription = if (showText) null else text, modifier = Modifier.size(16.dp))
            if (showText) Text(text, modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PriceSection(price: ModelPriceSummary, expanded: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("model-price-section").animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PriceHeader(hasTiers = price.tiers.isNotEmpty(), expanded = expanded, onToggle = onToggle)
        PriceMetrics(price)
        // 缺失不补零，推理价不与输出价相加；仍然展示选中报价的原有类别。
        if (price.cacheWrite != null || price.reasoning != null ||
            price.inputAudio != null || price.outputAudio != null
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                price.cacheWrite?.let { PriceLine(R.string.model_list_price_cache_write, it) }
                price.reasoning?.let { PriceLine(R.string.model_list_price_reasoning, it) }
                price.inputAudio?.let { PriceLine(R.string.model_list_price_input_audio, it) }
                price.outputAudio?.let { PriceLine(R.string.model_list_price_output_audio, it) }
            }
        }
        if (expanded && price.tiers.isNotEmpty()) PriceTiers(price.tiers)
    }
}

@Composable
private fun PriceHeader(hasTiers: Boolean, expanded: Boolean, onToggle: () -> Unit) {
    val toggleLabel = stringResource(
        if (expanded) R.string.model_list_price_details_collapse else R.string.model_list_price_details_expand,
    )
    val description = stringResource(
        if (expanded) R.string.model_list_price_details_expanded else R.string.model_list_price_details_collapsed,
    )
    Row(
        modifier = Modifier.fillMaxWidth().then(
            if (hasTiers) {
                // clickable 在布局外扩展最小触摸区域，不再用 48dp 最小高度撑大标题行。
                Modifier.clickable(onClickLabel = toggleLabel, role = Role.Button, onClick = onToggle)
                    .semantics {
                        contentDescription = toggleLabel
                        stateDescription = description
                    }
            } else Modifier,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.model_list_price_title), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.model_list_price_unit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (hasTiers) {
            val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "price-chevron")
            Icon(
                painterResource(R.drawable.ic_expand_more), contentDescription = null,
                modifier = Modifier.size(16.dp).testTag("price-chevron").rotate(rotation), tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PriceMetrics(price: ModelPriceSummary) {
    val metrics = listOfNotNull(
        Triple(R.string.model_list_input_label, price.input, "price-input"),
        Triple(R.string.model_list_output_label, price.output, "price-output"),
        if (price.cacheRead != null) {
            Triple(R.string.model_list_price_cache_read_label, price.cacheRead, "price-cache-read")
        } else null,
    )
    val minimumPriceWidth = 88.dp * LocalDensity.current.fontScale
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    // 按当前面板宽度和字号决定列数；窄屏／大字体换行，不裁切或近似价格。
    BoxWithConstraints {
        val columns = (maxWidth / minimumPriceWidth).toInt().coerceIn(1, metrics.size)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = columns,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            metrics.forEachIndexed { index, (label, value, tag) ->
                val hasDivider = index % columns != 0
                Column(
                    modifier = Modifier.weight(1f).testTag(tag)
                        .drawBehind {
                            if (hasDivider) {
                                val x = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width
                                drawLine(dividerColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                            }
                        }
                        .padding(start = if (hasDivider) 8.dp else 0.dp, end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        stringResource(label), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatPrice(value), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceLine(labelRes: Int, value: Double) {
    Text(
        text = stringResource(labelRes, formatPrice(value)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 上下文档位按来源原值展示，不选择档位、不解释门槛等号归属或累进计费规则。 */
@Composable
private fun PriceTiers(tiers: List<ModelPriceTierSummary>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiers.forEach { tier ->
            Text(
                text = stringResource(
                    R.string.model_list_price_tier,
                    formatTokenCount(tier.conditionSizeTokens),
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
    null -> stringResource(R.string.model_list_open_weights, stringResource(R.string.no_data))
}

@Composable
private fun formatPrice(price: Double?): String = price?.let {
    formatUsdPerMillionTokens(it)
} ?: stringResource(R.string.no_data)

/** 只增加千位分隔，不缩写、不改变 Long 精度，也不受设备数字分组规则影响。 */
internal fun formatTokenCount(value: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(value)

/** 列表限制采用十进制 K／M，最多一位小数；不影响原始数据与计价档位门槛。 */
internal fun formatCompactTokenCount(value: Long): String {
    if (value in -999L..999L) return value.toString()
    val count = BigDecimal.valueOf(value)
    val thousands = count.movePointLeft(3).setScale(1, RoundingMode.HALF_UP)
    val useMillions = thousands.abs() >= BigDecimal(1000)
    val scaled = if (useMillions) count.movePointLeft(6).setScale(1, RoundingMode.HALF_UP) else thousands
    return scaled.stripTrailingZeros().toPlainString() + if (useMillions) "M" else "K"
}

/** 只附带最多 16 个字符的原始许可短名；长名称不截断、不推测简称，原文留在模型数据中。 */
internal fun shortLicenseLabel(license: String?): String? =
    license?.trim()?.takeIf { it.isNotEmpty() && it.length <= 16 }

/** 至少两位小数，来源更精确时保留原精度；仅格式化 USD / 百万 tokens，不作计费计算。 */
internal fun formatUsdPerMillionTokens(value: Double): String {
    if (value == 0.0) return "$0.00"
    val plain = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    val dot = plain.indexOf('.')
    if (dot < 0) return "$" + plain + ".00"
    val decimals = plain.length - dot - 1
    return if (decimals >= 2) "$$plain" else "$" + plain + "0".repeat(2 - decimals)
}

@Composable
internal fun formatModality(modality: String): String = when (modality) {
    "text" -> stringResource(R.string.modality_text)
    "image" -> stringResource(R.string.modality_image)
    "audio" -> stringResource(R.string.modality_audio)
    "video" -> stringResource(R.string.modality_video)
    "pdf" -> "PDF"
    else -> modality
}

@DrawableRes
internal fun modalityIcon(modality: String?): Int = when (modality) {
    "image" -> R.drawable.ic_image
    "audio" -> R.drawable.ic_audio
    "video" -> R.drawable.ic_video
    "text" -> R.drawable.ic_text
    "pdf" -> R.drawable.ic_document
    else -> R.drawable.ic_modality
}

@Preview(name = "Light", showBackground = true, widthDp = 400)
@Preview(name = "Dark", showBackground = true, widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Narrow", showBackground = true, widthDp = 320)
@Preview(name = "Large font", showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
private fun ModelCardPreview() {
    ModelAtlasTheme(dynamicColor = false) {
        ModelCard(
            model = ModelSummary(
                id = "deepseek/deepseek-v4.1-flash-1017",
                name = "DeepSeek V4.1 Flash 1017",
                releaseDate = "2026-10-17",
                contextLimitTokens = 1_000_000,
                outputLimitTokens = 384_000,
                inputModalities = listOf("text", "image", "audio", "video", "pdf"),
                outputModalities = listOf("text", "image", "audio"),
                openWeights = true,
                license = "MIT",
                price = ModelPriceSummary(
                    providerId = "deepseek",
                    providerName = "DeepSeek",
                    isOfficial = true,
                    input = 0.15,
                    output = 0.60,
                    cacheRead = 0.003,
                    cacheWrite = 0.001,
                    reasoning = 0.12,
                    inputAudio = 0.80,
                    outputAudio = 1.20,
                    tiers = listOf(
                        ModelPriceTierSummary(
                            conditionType = "context",
                            conditionSizeTokens = 128_000,
                            input = 0.15,
                            output = 0.60,
                        ),
                        ModelPriceTierSummary(
                            conditionType = "context",
                            conditionSizeTokens = 1_000_000,
                            input = 0.30,
                            output = 1.20,
                        ),
                    ),
                ),
            ),
            priceDetailsExpanded = true,
            onTogglePriceDetails = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
