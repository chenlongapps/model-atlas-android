package io.github.chenlongapps.modelatlas.ui.modeldetail

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelDetailMetadata
import io.github.chenlongapps.modelatlas.ui.model.ModelIdentity
import io.github.chenlongapps.modelatlas.ui.model.ModelInfoBadge
import io.github.chenlongapps.modelatlas.ui.model.ModelModalities
import io.github.chenlongapps.modelatlas.ui.model.ModelIdentitySnapshot
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme

/** 居中身份、描述、模态与已支持能力；精确参数仅放在概览。 */
@Composable
internal fun ModelDetailHeader(
    model: ModelDetail?,
    collapseProgress: () -> Float,
    onNameBoundsChanged: (Rect) -> Unit,
    initialIdentity: ModelIdentitySnapshot? = null,
    sharedElementsEnabled: Boolean = true,
) {
    val identity = model?.let { ModelIdentitySnapshot(it.id, it.name, it.lab, it.metadata.openWeights, it.license) }
        ?: initialIdentity ?: return
    var headerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().testTag("model-detail-summary").onPlaced { headerCoordinates = it }) {
        Column(
            Modifier.fillMaxWidth().testTag("model-detail-hero").padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ModelIdentity(
                identity.name, identity.lab, identity.openWeights, identity.license,
                modifier = Modifier.fillMaxWidth(), nameStyle = MaterialTheme.typography.headlineMedium, centered = true,
                modelId = identity.id, sharedElementsEnabled = sharedElementsEnabled,
                nameModifier = Modifier.onGloballyPositioned { coordinates ->
                    headerCoordinates?.takeIf { it.isAttached }?.let { header ->
                        // 只回报标题在首条内的相对位置，滚动不改变测量值；长名称／字号变化会重新测量。
                        onNameBoundsChanged(header.localBoundingBoxOf(coordinates, clipBounds = false))
                    }
                }.graphicsLayer { alpha = 1f - collapseProgress() }.semantics {
                    if (collapseProgress() >= 0.5f) hideFromAccessibility()
                },
            )
            if (model != null) {
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
        }
    }
}

@Composable
internal fun DetailHeaderBar(
    onBack: () -> Unit,
    topInset: Dp,
    modelName: String?,
    collapseProgress: () -> Float,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    val title = stringResource(R.string.detail_title)
    Row(
        modifier.fillMaxWidth().padding(top = topInset).testTag("model-detail-header")
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
        }
        // 两层文字始终参与测量，滚动仅改变绘制与语义，不让返回栏高度或正文位置跳动。
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Text(stringResource(R.string.app_name), Modifier.fillMaxWidth().graphicsLayer {
                val progress = collapseProgress()
                alpha = 1f - progress
                translationY = -8.dp.toPx() * progress
            }.clearAndSetSemantics {
                if (collapseProgress() < 0.5f) {
                    contentDescription = title
                    heading()
                }
            }, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (modelName != null) Text(modelName,
                Modifier.fillMaxWidth().testTag("model-detail-collapsed-name").graphicsLayer {
                    val progress = collapseProgress()
                    alpha = progress
                    translationY = 8.dp.toPx() * (1f - progress)
                }.clearAndSetSemantics {
                    if (collapseProgress() >= 0.5f) {
                        contentDescription = modelName
                        heading()
                    }
                },
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        action()
    }
}

@Preview(name = "Detail header · Light", showBackground = true, widthDp = 400)
@Preview(name = "Detail header · Dark", showBackground = true, widthDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Detail header · Narrow", showBackground = true, widthDp = 320)
@Preview(name = "Detail header · Large font", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun ModelDetailHeaderPreview() {
    val model = ModelDetail(
        id = "deepseek/example",
        name = "DeepSeek V4.1 Flash",
        fetchedAtEpochMillis = 0,
        metadata = ModelDetailMetadata(
            description = "用于预览的模型描述，展示文本与图片输入、长上下文及能力标签的排版。",
            releaseDate = "2025-06-17",
            contextLimitTokens = 1_000_000,
            outputLimitTokens = 384_000,
            inputModalities = listOf("text", "image"),
            outputModalities = listOf("text"),
            openWeights = true,
            reasoning = true,
            toolCall = true,
            structuredOutput = true,
            attachment = true,
            temperature = true,
        ),
        license = "MIT",
    )
    ModelAtlasTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ModelDetailHeader(model = model, collapseProgress = { 0f }, onNameBoundsChanged = {})
        }
    }
}
