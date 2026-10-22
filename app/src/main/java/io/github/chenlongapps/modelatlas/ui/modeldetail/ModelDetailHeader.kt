package io.github.chenlongapps.modelatlas.ui.modeldetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.ui.model.ModelCoreMetrics
import io.github.chenlongapps.modelatlas.ui.model.ModelIdentity
import io.github.chenlongapps.modelatlas.ui.model.ModelInfoBadge
import io.github.chenlongapps.modelatlas.ui.model.ModelModalities

/** 居中身份区直接沿用页面背景，下面保留独立参数卡。 */
@Composable
internal fun ModelDetailHeader(model: ModelDetail?, onBack: () -> Unit, topInset: Dp) {
    if (model == null) {
        DetailHeaderBar(onBack, topInset)
        return
    }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().testTag("model-detail-summary")) {
        DetailHeaderBar(onBack, topInset)
        Column(
            Modifier.fillMaxWidth().testTag("model-detail-hero").padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ModelIdentity(
                model.name, model.lab, model.metadata.openWeights, model.license,
                modifier = Modifier.fillMaxWidth(), nameStyle = MaterialTheme.typography.headlineMedium, centered = true,
            )
            Text(
                model.metadata.description?.takeIf(String::isNotBlank) ?: stringResource(R.string.detail_description_missing),
                Modifier.fillMaxWidth().testTag("model-description"),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
            )
            ModelModalities(model.metadata.inputModalities, model.metadata.outputModalities,
                Modifier.fillMaxWidth(), showText = true, centered = true)
            val capabilities = listOf(
                R.string.detail_reasoning to model.metadata.reasoning,
                R.string.detail_tool_call to model.metadata.toolCall,
                R.string.detail_structured_output to model.metadata.structuredOutput,
                R.string.detail_attachment to model.metadata.attachment,
                R.string.detail_temperature to model.metadata.temperature,
            ).filter { it.second == true }
            if (capabilities.isNotEmpty()) {
                FlowRow(
                    Modifier.fillMaxWidth().testTag("model-detail-capability-badges"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    capabilities.forEach { (label, _) -> ModelInfoBadge(stringResource(label)) }
                }
            }
        }
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("model-detail-metrics"),
            shape = MaterialTheme.shapes.large, color = colors.surface, shadowElevation = 2.dp,
        ) {
            ModelCoreMetrics(model.metadata.releaseDate, model.metadata.contextLimitTokens, model.metadata.outputLimitTokens,
                Modifier.padding(16.dp), prominent = true)
        }
    }
}

@Composable
private fun DetailHeaderBar(onBack: () -> Unit, topInset: Dp) {
    val title = stringResource(R.string.detail_title)
    Row(
        Modifier.fillMaxWidth().padding(top = topInset).testTag("model-detail-header")
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
        }
        Text(stringResource(R.string.app_name), Modifier.weight(1f).clearAndSetSemantics {
            contentDescription = title
            heading()
        }, style = MaterialTheme.typography.labelLarge,
            letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
