package io.github.chenlongapps.modelatlas.ui.modelcompare

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.ui.modellist.LabLogo
import io.github.chenlongapps.modelatlas.ui.modellist.labDisplayName

private val SlotGap = 12.dp
private val StackedGap = 8.dp
private val SlotPadding = 8.dp
private val LogoGap = 8.dp
private val ArrowSize = 20.dp
private val ExpandedLogo = 56.dp
private val CompactLogo = 36.dp
private val ReplaceMinHeight = 32.dp
private val ReplaceHorizontalPadding = 12.dp
private val SmallGap = 4.dp

internal data class CompareSelectionSlot(
    val state: CompareModelState,
    val name: String?,
    val title: String,
    val lab: String?,
    val labName: String,
)

@Composable
internal fun compareSelectionSlots(state: ModelCompareUiState): List<CompareSelectionSlot> =
    listOf(state.first, state.second).mapIndexed { index, slot ->
        val summary = slot.modelId?.let { id -> state.models.firstOrNull { it.id == id } }
        val name = slot.detail?.name ?: summary?.name
            ?: slot.modelId?.let { stringResource(R.string.compare_unknown_model, index + 1) }
        val lab = slot.detail?.lab ?: summary?.lab
        CompareSelectionSlot(slot, name, name ?: stringResource(R.string.compare_select, index + 1),
            lab, lab?.let(::labDisplayName) ?: stringResource(R.string.no_data))
    }

internal data class CompareSelectionLayout(
    val stacked: Boolean,
    val nameHeight: Int,
    val labHeight: Int,
    val slotExpandedHeight: Int,
    val slotCompactHeight: Int,
    val compactHeight: Int,
)

@Composable
internal fun compareSelectionLayout(slots: List<CompareSelectionSlot>, width: Dp): CompareSelectionLayout {
    val density = LocalDensity.current
    val stacked = width < 280.dp * density.fontScale
    val measurer = rememberTextMeasurer()
    val nameStyle = MaterialTheme.typography.titleMedium
    val labStyle = MaterialTheme.typography.bodySmall
    val replaceStyle = MaterialTheme.typography.labelLarge
    val emptyTitles = List(slots.size) { stringResource(R.string.compare_select, it + 1) }
    val emptyLab = stringResource(R.string.no_data)
    val replaceText = stringResource(R.string.compare_replace_button)
    return with(density) {
        val slotWidth = if (stacked) width.roundToPx() else (width.roundToPx() - SlotGap.roundToPx()) / 2
        val innerWidth = slotWidth - 2 * SlotPadding.roundToPx()
        val compactNameWidth = innerWidth - CompactLogo.roundToPx() - ArrowSize.roundToPx() - 2 * LogoGap.roundToPx()
        fun height(text: String, style: TextStyle, maxWidth: Int): Int =
            measurer.measure(text, style, constraints = Constraints(maxWidth = maxWidth.coerceAtLeast(1))).size.height

        // 中英文的字体回退行高不同，空槽消失后也保留其基准行高。
        val nameHeight = maxOf(slots.maxOf { height(it.title, nameStyle, innerWidth) },
            emptyTitles.maxOf { height(it, nameStyle, innerWidth) })
        val labHeight = maxOf(slots.maxOf { height(it.labName, labStyle, innerWidth) }, height(emptyLab, labStyle, innerWidth))
        val replaceHeight = maxOf(ReplaceMinHeight.roundToPx(),
            height(replaceText, replaceStyle, innerWidth - 2 * ReplaceHorizontalPadding.roundToPx()) + 2 * SmallGap.roundToPx())
        val slotExpandedHeight = 2 * SlotPadding.roundToPx() + ExpandedLogo.roundToPx() + 2 * LogoGap.roundToPx() +
            nameHeight + SmallGap.roundToPx() + labHeight + replaceHeight
        val slotCompactHeight = maxOf(48.dp.roundToPx(),
            slots.maxOf { height(it.title, nameStyle, compactNameWidth) } + 2 * SmallGap.roundToPx())
        CompareSelectionLayout(stacked, nameHeight, labHeight, slotExpandedHeight, slotCompactHeight,
            if (stacked) slots.size * slotCompactHeight + StackedGap.roundToPx() else slotCompactHeight)
    }
}

@Composable
internal fun CompareSelection(
    slots: List<CompareSelectionSlot>,
    sizes: CompareSelectionLayout,
    fraction: () -> Float,
    onChoose: (Int) -> Unit,
    onRetry: (Int) -> Unit,
) {
    Column {
        CompareSelectionGroup(sizes.stacked) { index, modifier ->
            CompareSlot(slots[index], index, sizes, fraction, { onChoose(index) }, modifier)
        }
        // 反馈放在整组选模槽位下方，避免加载和重试入口把另一侧（尤其是纵排时）挤走。
        if (slots.any { it.state.modelId != null && it.state.status != CompareReadStatus.CONTENT }) {
            CompareSelectionGroup(sizes.stacked) { index, modifier ->
                Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                    val slot = slots[index]
                    if (slot.state.modelId != null && slot.state.status != CompareReadStatus.CONTENT) {
                        CompareSlotStatus(slot.state.status, slotIndex = index.takeIf { sizes.stacked }) { onRetry(index) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareSlot(
    slot: CompareSelectionSlot,
    index: Int,
    sizes: CompareSelectionLayout,
    fraction: () -> Float,
    onChoose: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val selected = slot.state.modelId != null
    val action = stringResource(if (selected) R.string.compare_replace_action else R.string.compare_choose_action)
    fun auxiliaryAlpha() = (1f - 3f * fraction()).coerceAtLeast(0f)
    Layout(
        modifier = modifier.testTag("compare-slot-$index").clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClickLabel = action, onClick = onChoose),
        content = {
            Box(Modifier.testTag(if (selected) "compare-logo-$index" else "compare-add-$index"),
                contentAlignment = Alignment.Center) {
                if (selected) {
                    LabLogo(slot.lab)
                } else {
                    Box(
                        Modifier.fillMaxSize().clip(RoundedCornerShape(25))
                            .background(colors.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_add), null, tint = colors.primary)
                    }
                }
            }
            Text(slot.title, Modifier.testTag("compare-name-$index"), style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface, textAlign = if (selected) TextAlign.Start else TextAlign.Center)
            if (selected) {
                Text(slot.labName, Modifier.testTag("compare-lab-$index").graphicsLayer { alpha = auxiliaryAlpha() }
                    .semantics { if (auxiliaryAlpha() == 0f) hideFromAccessibility() },
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                Box(
                    Modifier.graphicsLayer { alpha = auxiliaryAlpha() }.semantics {
                        if (auxiliaryAlpha() == 0f) hideFromAccessibility()
                    }.border(1.dp, colors.outlineVariant, MaterialTheme.shapes.extraLarge)
                        .heightIn(min = ReplaceMinHeight).padding(horizontal = ReplaceHorizontalPadding, vertical = SmallGap),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.compare_replace_button), color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                }
                Icon(painterResource(R.drawable.ic_expand_more), null,
                    Modifier.testTag("compare-expand-$index").size(ArrowSize), tint = colors.onSurfaceVariant)
            }
        },
    ) { children, constraints ->
        // 只在测量与绘制阶段读滚动进度；同一个名称、Logo 和点击节点从展开移动到吸顶。
        val progress = if (selected) fraction() else 0f
        val width = constraints.maxWidth
        val padding = SlotPadding.roundToPx()
        val gap = LogoGap.roundToPx()
        val arrowSize = ArrowSize.roundToPx()
        val logoSize = lerp(ExpandedLogo.roundToPx(), CompactLogo.roundToPx(), progress).coerceAtMost(width)
        val compactLogoSize = CompactLogo.roundToPx()
        val innerWidth = (width - 2 * padding).coerceAtLeast(1)
        val compactNameWidth = (innerWidth - compactLogoSize - arrowSize - 2 * gap).coerceAtLeast(1)
        val logo = children[0].measure(Constraints.fixed(logoSize, logoSize))
        val name = children[1].measure(Constraints(minWidth = if (selected) 0 else innerWidth,
            minHeight = lerp(sizes.nameHeight, 0, progress),
            maxWidth = lerp(innerWidth, compactNameWidth, progress)))
        // 居中文字的字形按段落宽度定位，让段落与布局节点使用同一个宽度。
        val lab = children.getOrNull(2)?.measure(Constraints(minWidth = innerWidth, maxWidth = innerWidth, minHeight = sizes.labHeight))
        val replaceLabel = children.getOrNull(3)?.measure(Constraints(maxWidth = innerWidth))
        val arrow = children.getOrNull(4)?.measure(Constraints(maxWidth = innerWidth))
        val compactHeight = sizes.slotCompactHeight
        val nameTop = padding + ExpandedLogo.roundToPx() + gap
        val labTop = nameTop + sizes.nameHeight + SmallGap.roundToPx()
        val replaceTop = labTop + sizes.labHeight + gap
        val expandedHeight = sizes.slotExpandedHeight
        val height = lerp(expandedHeight, compactHeight, progress)
        layout(width, height) {
            // Logo 先向起始侧移动，给上移的名称让出路径。
            val logoX = lerp((width - logo.width) / 2, padding, (progress * 2f).coerceAtMost(1f))
            val logoY = lerp(padding, (compactHeight - logo.height) / 2, progress)
            logo.placeRelative(logoX, logoY)
            val nameX = lerp((width - name.width) / 2, padding + compactLogoSize + gap, progress)
            val nameY = lerp(nameTop, (compactHeight - name.height) / 2, progress)
            val logoClearance = nameX - logoX - logo.width
            val separation = (1f - logoClearance.toFloat() / gap).coerceIn(0f, 1f)
            val clearNameY = lerp(nameY, maxOf(nameY, logoY + logo.height), separation)
            name.placeRelative(nameX, clearNameY)
            if (lab != null && replaceLabel != null && auxiliaryAlpha() > 0f) {
                val lift = expandedHeight - height
                val labY = maxOf(labTop - lift, clearNameY + name.height + SmallGap.roundToPx())
                lab.placeRelative((width - lab.width) / 2, labY)
                replaceLabel.placeRelative((width - replaceLabel.width) / 2, maxOf(replaceTop - lift, labY + lab.height + gap))
            }
            if (arrow != null) {
                val arrowX = width - padding - arrow.width
                val arrowClearance = (arrowX - nameX - name.width).toFloat() / gap
                val arrowAlpha = minOf((progress - 0.5f) * 2f, arrowClearance).coerceIn(0f, 1f)
                if (arrowAlpha > 0f) {
                    arrow.placeRelativeWithLayer(arrowX, (height - arrow.height) / 2) { alpha = arrowAlpha }
                }
            }
        }
    }
}

@Composable
private fun CompareSlotStatus(status: CompareReadStatus, slotIndex: Int?, onRetry: () -> Unit) {
    val message = when (status) {
        CompareReadStatus.LOADING -> stringResource(R.string.compare_loading)
        CompareReadStatus.NOT_FOUND -> stringResource(R.string.compare_missing)
        CompareReadStatus.ERROR -> stringResource(R.string.detail_read_error)
        CompareReadStatus.CONTENT -> return
    }
    Text(
        if (slotIndex == null) message else stringResource(R.string.compare_stacked_slot_status, slotIndex + 1, message),
        Modifier.padding(horizontal = SlotPadding),
        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
        textAlign = TextAlign.Center,
        color = if (status == CompareReadStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (status == CompareReadStatus.ERROR) {
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun CompareSelectionGroup(
    stacked: Boolean,
    slot: @Composable (Int, Modifier) -> Unit,
) {
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(StackedGap)) {
            repeat(2) { slot(it, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(SlotGap)) {
            repeat(2) { slot(it, Modifier.weight(1f)) }
        }
    }
}
