package io.github.chenlongapps.modelatlas.ui.modeldetail

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.ui.model.LocalModelSharedTransition
import io.github.chenlongapps.modelatlas.ui.model.ModelDetailContainer
import io.github.chenlongapps.modelatlas.ui.model.ModelIdentityRole
import io.github.chenlongapps.modelatlas.ui.model.ModelSharedTransition
import io.github.chenlongapps.modelatlas.ui.model.ModelTransitionDurationMillis
import io.github.chenlongapps.modelatlas.ui.model.modelCardContainerModifier
import io.github.chenlongapps.modelatlas.ui.model.modelSharedModifier
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 先独立验证公开 Seekable API 能驱动现有 sharedBounds/sharedElement，不涉及应用导航。 */
class ModelSeekableTransitionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pairedVisibilityTransitionsSeekTogetherAndCanReverseWithoutChangingTargets() {
        val mainState = SeekableTransitionState(EnterExitState.PostExit)
        val state = SeekableTransitionState(EnterExitState.Visible)
        lateinit var scope: CoroutineScope
        var expansion = 1f
        var cardColor = Color.Unspecified
        var pageColor = Color.Unspecified
        var sourceWidth = 0f
        var sourceHeight = 0f
        compose.setContent {
            scope = rememberCoroutineScope()
            ModelAtlasTheme(darkTheme = true, dynamicColor = false) {
                cardColor = MaterialTheme.colorScheme.surface
                pageColor = MaterialTheme.colorScheme.surfaceContainerLowest
                sourceWidth = with(LocalDensity.current) { 160.dp.toPx() }
                sourceHeight = with(LocalDensity.current) { 240.dp.toPx() }
                val main = rememberTransition(mainState)
                val detail = rememberTransition(state)
                val progress by detail.animateFloat(transitionSpec = { tween(ModelTransitionDurationMillis) }) {
                    if (it == EnterExitState.Visible) 1f else 0f
                }
                expansion = progress
                val progressReader = remember { { progress } }
                val mainScope = remember(main) { object : AnimatedVisibilityScope { override val transition = main } }
                val detailScope = remember(detail) { object : AnimatedVisibilityScope { override val transition = detail } }
                SharedTransitionLayout(Modifier.fillMaxSize().background(Color.Black)) {
                    CompositionLocalProvider(LocalModelSharedTransition provides ModelSharedTransition(
                        this, mainScope, true, progressReader,
                    )) {
                        Box(Modifier.size(160.dp, 240.dp).then(modelCardContainerModifier("test"))) {
                            // 与真实卡片一致保留内边距；面积断言测量 Logo，不混入父级圆角剪裁。
                            Box(Modifier.padding(16.dp)) {
                                Box(Modifier.size(40.dp).then(modelSharedModifier("test", ModelIdentityRole.LOGO, true)).background(Color.Red))
                            }
                        }
                    }
                    CompositionLocalProvider(LocalModelSharedTransition provides ModelSharedTransition(
                        this, detailScope, true, progressReader,
                    )) {
                        ModelDetailContainer("test") {
                            Box(Modifier.padding(16.dp)) {
                                Box(Modifier.size(80.dp).then(modelSharedModifier("test", ModelIdentityRole.LOGO, true)).background(Color.Red))
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        fun seek(fraction: Float): Int {
            compose.runOnIdle { scope.launch {
                coroutineScope {
                    launch { mainState.seekTo(fraction, EnterExitState.Visible) }
                    launch { state.seekTo(fraction, EnterExitState.PostExit) }
                }
            } }
            compose.mainClock.advanceTimeBy(64)
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            val width = sourceWidth + (pixels.width - sourceWidth) * expansion
            val height = sourceHeight + (pixels.height - sourceHeight) * expansion
            assertEquals("详情表面必须按共享扩展进度精确插值底色",
                androidx.compose.ui.graphics.lerp(cardColor, pageColor, expansion).toArgb(),
                pixels[(width / 2).toInt(), height.toInt() - 5].toArgb())
            var redPixels = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val c = pixels[x, y]
                if (c.red > .95f && c.green < .05f && c.blue < .05f) redPixels++
            }
            return redPixels
        }
        val areas = listOf(0f, .25f, .5f, .75f, 1f).map { seek(it) }
        areas.zipWithNext().forEach { (a, b) -> assertTrue("Logo 应随可寻址共享容器收缩：$areas", a > b) }
        assertEquals(0f, expansion, .001f)
        val half = seek(.5f)
        assertEquals(areas[2], half)
        seek(0f)
        assertEquals(1f, expansion, .001f)
        compose.runOnIdle { scope.launch {
            mainState.snapTo(EnterExitState.PostExit)
            state.snapTo(EnterExitState.Visible)
        } }
        compose.mainClock.advanceTimeBy(64)
        seek(.75f)
        compose.runOnIdle { scope.launch { coroutineScope {
            launch { mainState.animateTo(EnterExitState.Visible) }
            launch { state.animateTo(EnterExitState.PostExit) }
        } } }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle {
            assertEquals(EnterExitState.PostExit, state.currentState)
            assertEquals(EnterExitState.Visible, mainState.currentState)
        }
        compose.mainClock.autoAdvance = true
    }
}
