package io.github.chenlongapps.modelatlas.ui.modeldetail

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.ModelDetailMetadata
import io.github.chenlongapps.modelatlas.data.model.ModelDetailOffering
import io.github.chenlongapps.modelatlas.data.model.ModelDetailPrice
import io.github.chenlongapps.modelatlas.ui.modellist.formatTokenCount
import io.github.chenlongapps.modelatlas.ui.modellist.formatUsdPerMillionTokens
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme

/** 单个显式关联服务；紧凑排版不改变服务限制、基础报价或精度。 */
@Composable
internal fun ModelServiceCard(
    offering: ModelDetailOffering,
    providerId: String,
    providerName: String,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ProviderLogo(providerId)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(providerName, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    val serviceIdLabel = stringResource(R.string.detail_offering_id)
                    Text(offering.id, Modifier.semantics { contentDescription = "$serviceIdLabel：${offering.id}" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ServiceMetrics(offering)
        }
    }
}

private data class ServiceMetric(val label: String, val value: String, val isPrice: Boolean)

/** 四列放不下完整标签或原值时统一退到两列／单列，数值不缩写或截断。 */
@Composable
private fun ServiceMetrics(offering: ModelDetailOffering) {
    val noData = stringResource(R.string.no_data)
    val metrics = listOf(
        ServiceMetric(stringResource(R.string.detail_offering_context),
            offering.metadata.contextLimitTokens?.let(::formatTokenCount) ?: noData,
            isPrice = false),
        ServiceMetric(stringResource(R.string.detail_offering_output),
            offering.metadata.outputLimitTokens?.let(::formatTokenCount) ?: noData,
            isPrice = false),
        ServiceMetric(stringResource(R.string.model_list_input_label),
            offering.price?.input?.let(::formatUsdPerMillionTokens) ?: noData, isPrice = true),
        ServiceMetric(stringResource(R.string.model_list_output_label),
            offering.price?.output?.let(::formatUsdPerMillionTokens) ?: noData, isPrice = true),
    )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelSmall
    val valueStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    val requiredWidth = metrics.maxOf { metric ->
        maxOf(
            measurer.measure(metric.label, labelStyle, softWrap = false).size.width,
            measurer.measure(metric.value, valueStyle, softWrap = false).size.width,
        )
    }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("detail-service-metrics")) {
        val gapPx = with(density) { 12.dp.roundToPx() }
        val columns = listOf(4, 2, 1).first { count ->
            count == 1 || (constraints.maxWidth - gapPx * (count - 1)) / count >= requiredWidth
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            metrics.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { metric ->
                        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {},
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(metric.label, style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(metric.value, style = valueStyle,
                                color = if (metric.isPrice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Service card · Light", showBackground = true, widthDp = 400)
@Preview(name = "Service card · Dark", showBackground = true, widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Service card · Narrow", showBackground = true, widthDp = 320)
@Preview(name = "Service card · Large font", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun ModelServiceCardPreview() {
    ModelAtlasTheme(dynamicColor = false) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.model_list_price_unit), Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.labelSmall)
            ModelServiceCard(ModelDetailOffering(
                id = "example-chat-v1", name = "Preview service",
                metadata = ModelDetailMetadata(contextLimitTokens = 1_000_000, outputLimitTokens = 384_000),
                status = null, price = ModelDetailPrice(input = 0.15, output = 0.30), modes = null,
                source = "models.dev", fetchedAtEpochMillis = 0, reasoningOptions = null, interleaved = null,
            ), "google", "Google Cloud")
        }
    }
}
