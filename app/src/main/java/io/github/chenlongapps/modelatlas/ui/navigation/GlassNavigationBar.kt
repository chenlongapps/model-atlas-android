package io.github.chenlongapps.modelatlas.ui.navigation

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import io.github.chenlongapps.modelatlas.R

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

/** AI 按钮打开独立路由；胶囊条目与主 Pager 一一对应。 */
@Composable
internal fun GlassNavigationBar(
    pagerState: PagerState,
    backdrop: Backdrop,
    onNavigate: (Int) -> Unit,
    onOpenChat: () -> Unit,
    onModelsDoubleClick: () -> Unit,
    items: List<GlassNavigationItem>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    require(items.isNotEmpty())
    val spacing = if (items.size >= 5) 2.dp else 4.dp
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val modelsDoubleClick by rememberUpdatedState(onModelsDoubleClick)
    val colors = MaterialTheme.colorScheme
    val glassEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !LocalInspectionMode.current
    val haptics = LocalHapticFeedback.current
    val aiLabel = stringResource(R.string.chat_title)
    BoxWithConstraints(modifier.testTag("glass-navigation")) {
        val gap = 8.dp
        val capsuleMinWidth = 48.dp * items.size + 8.dp + spacing * (items.size - 1)
        val aiSize = (maxWidth - gap - capsuleMinWidth).coerceIn(48.dp, 64.dp)
        Row(
            modifier = Modifier
                .pointerInput(layoutDirection, items.size, spacing, aiSize, enabled) {
                    if (!enabled) return@pointerInput
                    detectModelsDoubleClicks({ width ->
                        splitNavigationModelsRange(width, aiSize.toPx(), gap.toPx(), 4.dp.toPx(), spacing.toPx(), items.size, isRtl)
                    }) { modelsDoubleClick() }
                },
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(aiSize)
                    .navigationGlassSurface(backdrop, glassEnabled, colors.surfaceContainer, colors.background.copy(alpha = 0.5f))
                    .testTag("nav-chat")
                    .semantics { contentDescription = aiLabel }
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenChat()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.nav_ai),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            GlassNavigationCapsule(
                pagerState, backdrop, onNavigate, onModelsDoubleClick, items,
                modifier = Modifier.weight(1f), enabled = enabled,
            )
        }
    }
}

private fun Modifier.navigationGlassSurface(
    backdrop: Backdrop,
    glassEnabled: Boolean,
    fallback: Color,
    tint: Color,
): Modifier = clip(CircleShape).then(
    if (glassEnabled) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                vibrancy()
                blur(4.dp.toPx())
                lens(16.dp.toPx(), 32.dp.toPx(), chromaticAberration = true)
            },
            onDrawSurface = { drawRect(tint) },
        )
    } else Modifier.background(fallback),
)

@Composable
private fun GlassNavigationCapsule(
    pagerState: PagerState,
    backdrop: Backdrop,
    onNavigate: (Int) -> Unit,
    onModelsDoubleClick: () -> Unit,
    items: List<GlassNavigationItem>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val glassEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !LocalInspectionMode.current
    val colors = MaterialTheme.colorScheme
    val surface = colors.surface
    val tint = colors.background.copy(alpha = 0.5f)
    val indicatorTint = if (surface.luminance() < 0.5f) {
        lerp(surface, Color.White, 0.16f)
    } else {
        lerp(surface, Color.Black, 0.08f)
    }
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val haptics = LocalHapticFeedback.current
    val navigate by rememberUpdatedState(onNavigate)
    val scrollToTopLabel = stringResource(R.string.model_list_scroll_to_top)
    val spacing = if (items.size >= 5) 2.dp else 4.dp

    Box(
        modifier = modifier.testTag("glass-navigation-capsule")
            .navigationGlassSurface(backdrop, glassEnabled, colors.surfaceContainer, tint),
    ) {
        Box(Modifier.padding(4.dp)) {
            BoxWithConstraints(Modifier.matchParentSize()) {
                val itemWidth = (maxWidth - spacing * (items.size - 1)) / items.size
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(itemWidth)
                        .graphicsLayer {
                            // 在绘制阶段读取偏移，页面拖动时不必逐帧重组整个导航栏。
                            val offset = pagerState.currentPage + pagerState.currentPageOffsetFraction
                            translationX = offset * (itemWidth + spacing).toPx() * if (isRtl) -1f else 1f
                        }
                        .testTag("nav-indicator")
                        .clip(CircleShape)
                        .then(
                            if (glassEnabled) {
                                Modifier.drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { CircleShape },
                                    effects = {
                                        blur(4.dp.toPx())
                                        lens(10.dp.toPx(), 14.dp.toPx(), chromaticAberration = true)
                                    },
                                    onDrawSurface = {
                                        drawRect(indicatorTint, blendMode = BlendMode.Hue)
                                        drawRect(indicatorTint.copy(alpha = 0.75f))
                                    },
                                )
                            } else {
                                Modifier.background(colors.secondaryContainer)
                            },
                        ),
                )
            }
            Row(
                modifier = Modifier.selectableGroup()
                    .pointerInput(pagerState, layoutDirection, items, enabled) {
                        if (!enabled) return@pointerInput
                        var lastTarget = pagerState.currentPage
                        detectHorizontalDragGestures(
                            onDragStart = { lastTarget = pagerState.currentPage },
                            onHorizontalDrag = { change, _ ->
                                val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                val logicalFraction = if (isRtl) 1f - fraction else fraction
                                val target = (logicalFraction * items.size).toInt().coerceIn(items.indices)
                                if (target != lastTarget) {
                                    lastTarget = target
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    navigate(target)
                                }
                            },
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    val selected = pagerState.currentPage == index
                    val contentColor by animateColorAsState(
                        if (selected) {
                            if (glassEnabled) colors.primary else colors.onSecondaryContainer
                        } else colors.onSurfaceVariant,
                        label = "Navigation content color",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .testTag(item.tag)
                            .semantics {
                                if (enabled && item == GlassNavigationItem.Models) {
                                    customActions = listOf(CustomAccessibilityAction(scrollToTopLabel) {
                                        onModelsDoubleClick()
                                        true
                                    })
                                }
                            }
                            .selectable(
                                enabled = enabled,
                                selected = selected,
                                role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (!selected || pagerState.isScrollInProgress) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        navigate(index)
                                    }
                                },
                            )
                            .heightIn(min = 56.dp)
                            .padding(horizontal = when { items.size >= 5 -> 2.dp; items.size >= 4 -> 8.dp; else -> 12.dp }, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        Crossfade(targetState = selected, label = "Navigation icon") { isSelected ->
                            Icon(
                                painterResource(if (isSelected) item.selectedIcon else item.icon),
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
    }
}

/** 只观察触摸，不消费事件或等待双击后才执行单击，保留 selectable 与父级拖动的响应。 */
private suspend fun PointerInputScope.detectModelsDoubleClicks(
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
