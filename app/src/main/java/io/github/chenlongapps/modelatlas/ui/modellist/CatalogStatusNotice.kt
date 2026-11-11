package io.github.chenlongapps.modelatlas.ui.modellist

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme

internal fun ModelListUiState.hasCatalogNotice(): Boolean =
    !isLoading && !isRefreshing && error == null &&
        (message != null || refreshError != null)

/** 只保留提醒的展示字段，避免目录内容变化触发动画或保留整份模型列表。 */
internal data class CatalogNotice(
    val refreshError: ModelListError? = null,
    val message: ModelListMessage? = null,
)

internal fun ModelListUiState.catalogNotice(): CatalogNotice? =
    if (hasCatalogNotice()) CatalogNotice(refreshError, message) else null

@Composable
internal fun AnimatedCatalogStatusNotice(
    notice: CatalogNotice?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    updatedMessage: String = stringResource(R.string.model_list_updated),
) {
    AnimatedContent(
        targetState = notice,
        modifier = Modifier.fillMaxWidth().testTag("catalog-notice-transition"),
        contentAlignment = Alignment.TopStart,
        transitionSpec = {
            (fadeIn(tween(200)) togetherWith fadeOut(tween(180))).using(
                SizeTransform { _, _ ->
                    tween(if (targetState == null) 180 else 200, easing = FastOutSlowInEasing)
                },
            )
        },
        label = "Catalog notice",
    ) { displayedNotice ->
        if (displayedNotice == null) {
            Box(Modifier.fillMaxWidth())
        } else {
            // 间距也随高度一起收起；退出期间保留旧内容，但立即禁用旧操作和读屏。
            val active = displayedNotice == notice
            Box(Modifier.padding(top = 16.dp)) {
                CatalogStatusNotice(
                    notice = displayedNotice,
                    onRefresh = onRefresh,
                    onDismiss = onDismiss,
                    enabled = active,
                    updatedMessage = updatedMessage,
                    modifier = if (active) Modifier else Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

/** 列表内的非阻断提醒；成功结果由页面自动清除，只有错误提供手动关闭。 */
@Composable
internal fun CatalogStatusNotice(
    notice: CatalogNotice,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    updatedMessage: String = stringResource(R.string.model_list_updated),
) {
    val failed = notice.refreshError != null
    val colors = MaterialTheme.colorScheme
    val accent = if (failed) colors.error else colors.primary
    val message = when {
        notice.refreshError != null -> stringResource(notice.refreshError.messageResource())
        notice.message == ModelListMessage.UPDATED -> updatedMessage
        else -> stringResource(R.string.model_list_up_to_date)
    }

    Surface(
        modifier = modifier.fillMaxWidth().testTag("catalog-notice"),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow,
        contentColor = colors.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(
                        when {
                            failed -> R.drawable.ic_info
                            else -> R.drawable.ic_check_circle
                        },
                    ),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = message,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (failed) {
                // 操作放在独立的可换行区域，窄屏与大字号时不挤压提示正文。
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = onDismiss, enabled = enabled) { Text(stringResource(R.string.close)) }
                    FilledTonalButton(onClick = onRefresh, enabled = enabled) { Text(stringResource(R.string.retry)) }
                }
            }
        }
    }
}

@Preview(name = "Catalog notice · Success", showBackground = true)
@Composable
private fun CatalogSuccessNoticePreview() {
    ModelAtlasTheme(dynamicColor = false) {
        CatalogStatusNotice(
            notice = CatalogNotice(message = ModelListMessage.UP_TO_DATE),
            onRefresh = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Catalog notice · Light", showBackground = true)
@Preview(name = "Catalog notice · Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Catalog notice · Large font", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun CatalogStatusNoticePreview() {
    ModelAtlasTheme(dynamicColor = false) {
        CatalogStatusNotice(
            notice = CatalogNotice(message = ModelListMessage.UPDATED),
            onRefresh = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Catalog notice · Failure", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun CatalogFailureNoticePreview() {
    ModelAtlasTheme(dynamicColor = false) {
        CatalogStatusNotice(
            notice = CatalogNotice(refreshError = ModelListError.NETWORK),
            onRefresh = {},
            onDismiss = {},
        )
    }
}
