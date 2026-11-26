package io.github.chenlongapps.modelatlas.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.chenlongapps.modelatlas.R
import kotlin.math.roundToInt

internal data class GlassNavigationItem(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
    val tag: String,
) {
    companion object {
        val Models = GlassNavigationItem(R.string.nav_models, R.drawable.ic_nav_models_outline, R.drawable.ic_nav_models, "nav-models")
        val Benchmarks = GlassNavigationItem(R.string.nav_benchmarks, R.drawable.ic_nav_benchmarks_outline, R.drawable.ic_nav_benchmarks, "nav-benchmarks")
        val Favorites = GlassNavigationItem(R.string.nav_favorites, R.drawable.ic_favorite, R.drawable.ic_favorite_filled, "nav-favorites")
        val Compare = GlassNavigationItem(R.string.nav_compare, R.drawable.ic_compare, R.drawable.ic_compare_filled, "nav-compare")
        val Settings = GlassNavigationItem(R.string.nav_settings, R.drawable.ic_settings, R.drawable.ic_settings_filled, "nav-settings")
    }
}

/** The shared capsule supplies its existing material; the lens adds only foreground and optics. */
@Composable
internal fun NavigationCapsuleContent(
    pagerState: PagerState,
    motion: NavigationLensMotion,
    backdrop: Backdrop?,
    materialBackdrop: LayerBackdrop?,
    onNavigate: suspend (Int) -> Unit,
    onModelsDoubleClick: () -> Unit,
    items: List<GlassNavigationItem>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val tick by rememberUpdatedState { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    val navigate by rememberUpdatedState(onNavigate)
    val press = animateFloatAsState(if (motion.held) 1f else 0f, spring(1f, 700f), label = "Lens optics")
    val lift = animateFloatAsState(if (motion.held) 1f else 0f, spring(0.65f, 250f), label = "Lens lift")
    val spacing = if (items.size >= 5) 2.dp else 4.dp
    val spacingPx = with(density) { spacing.roundToPx().toFloat() }
    val geometry = remember(spacingPx, items.size, isRtl) {
        { width: Int -> NavigationTabGeometry(width.toFloat(), spacingPx, items.size, isRtl) }
    }
    val inkBackdrop = if (backdrop != null && materialBackdrop != null) rememberLayerBackdrop() else null
    val lensBackdrop = if (backdrop != null && materialBackdrop != null && inkBackdrop != null) {
        rememberCombinedBackdrop(backdrop, materialBackdrop, inkBackdrop)
    } else null

    BoxWithConstraints(modifier.fillMaxWidth().padding(4.dp)) {
        val tabs = geometry(constraints.maxWidth)
        val selected = pagerState.currentPage
        val indicator: @Composable () -> Unit = {
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier.align(Alignment.CenterStart)
                        .offset { IntOffset((motion.position * tabs.step).roundToInt(), 0) }
                        .fillMaxHeight()
                        .width(with(density) { tabs.itemWidth.toDp() })
                        .testTag("nav-indicator")
                        .navigationLens(lensBackdrop, motion, press, lift, colors.onSurface, colors.secondaryContainer),
                )
            }
        }
        if (lensBackdrop == null) indicator()
        else Box(Modifier.matchParentSize().navigationPressLight(tabs, motion, press))

        NavigationTabs(
            items, selected, spacing,
            modifier = Modifier.fillMaxWidth().selectableGroup().then(
                if (enabled) Modifier.dragNavigationLens(motion, geometry, { tick() }) { motion.release(navigate) }
                else Modifier,
            ),
            color = { index ->
                if (lensBackdrop == null && index == selected) colors.onSecondaryContainer else colors.onSurface
            },
            enabled = enabled,
            onModelsDoubleClick = onModelsDoubleClick,
            onSelect = { index ->
                if (motion.select(index, navigate)) tick()
            },
        )
        if (inkBackdrop != null) {
            // Record the themed ink independently: no input or accessibility nodes in this row.
            NavigationTabs(
                items, selected, spacing,
                modifier = Modifier.matchParentSize()
                    .clearAndSetSemantics {}
                    .graphicsLayer { alpha = 0f }
                    .layerBackdrop(inkBackdrop),
                color = { colors.primary },
            )
            indicator()
        }
    }
}

@Composable
private fun NavigationTabs(
    items: List<GlassNavigationItem>,
    selected: Int,
    spacing: Dp,
    modifier: Modifier,
    color: (Int) -> Color,
    enabled: Boolean = false,
    onModelsDoubleClick: () -> Unit = {},
    onSelect: ((Int) -> Unit)? = null,
) {
    val modelsAction = if (onSelect != null && enabled) stringResource(R.string.model_list_scroll_to_top) else null
    val horizontalPadding = when {
        items.size >= 5 -> 2.dp
        items.size >= 4 -> 8.dp
        else -> 12.dp
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = selected == index
            val tabModifier = if (onSelect == null) Modifier else Modifier
                .testTag(item.tag)
                .semantics {
                    if (modelsAction != null && item == GlassNavigationItem.Models) {
                        customActions = listOf(CustomAccessibilityAction(modelsAction) {
                            onModelsDoubleClick()
                            true
                        })
                    }
                }
                .selectable(
                    enabled = enabled,
                    selected = isSelected,
                    role = Role.Tab,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onSelect(index) },
                )
            val contentColor = color(index)
            Column(
                modifier = Modifier.weight(1f).then(tabModifier)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = horizontalPadding, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                Crossfade(targetState = isSelected, label = "Navigation icon") { filled ->
                    Icon(
                        painterResource(if (filled) item.selectedIcon else item.icon),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    stringResource(item.label),
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** 只观察触摸，不消费事件或等待双击后才执行单击，保留 selectable 与父级拖动的响应。 */
internal suspend fun PointerInputScope.detectModelsDoubleClicks(
    modelsRange: (Float) -> ClosedFloatingPointRange<Float>,
    onDoubleClick: () -> Unit,
) {
    var previousUpTime: Long? = null
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val modelRange = modelsRange(size.width.toFloat())
        val interval = previousUpTime?.let { down.uptimeMillis - it }
        val isDoubleClick = interval != null &&
            interval in viewConfiguration.doubleTapMinTimeMillis..viewConfiguration.doubleTapTimeoutMillis
        var validTap = down.position.x in modelRange
        var upTime = down.uptimeMillis
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                validTap = false
                break
            }
            if (event.changes.size != 1 ||
                (change.position - down.position).getDistance() > viewConfiguration.touchSlop ||
                change.position.x !in modelRange ||
                change.position.y !in 0f..size.height.toFloat()
            ) {
                validTap = false
            }
            upTime = change.uptimeMillis
        } while (change.pressed)
        validTap = validTap && upTime - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
        previousUpTime = if (validTap && !isDoubleClick) upTime else null
        if (validTap && isDoubleClick) onDoubleClick()
    }
}
