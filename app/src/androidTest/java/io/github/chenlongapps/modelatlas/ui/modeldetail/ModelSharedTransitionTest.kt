package io.github.chenlongapps.modelatlas.ui.modeldetail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.espresso.Espresso.pressBack
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelDetailMetadata
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.ModelAtlasScaffold
import io.github.chenlongapps.modelatlas.ui.model.ModelIdentityRole
import io.github.chenlongapps.modelatlas.ui.model.ModelTransitionDurationMillis
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListScreen
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListMessage
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListUiState
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ModelSharedTransitionTest {
    @get:Rule val compose = createComposeRule()
    private val status = mutableStateOf(ModelDetailStatus.CONTENT)
    private var detailsDisposed = 0
    private var listCompositions = 0
    private var listDisposals = 0
    private var noticesDismissed = 0
    private var foregroundColor = 0
    private val catalog = mutableStateOf(listOf(
        ModelSummary("openai/one", "First model", openWeights = true, license = "MIT"),
        ModelSummary("openai/two", "Second model", openWeights = true, license = "MIT"),
    ))

    @Test fun wholeCardAndSharedIdentityAnimateTogetherInBothDirectionsWithoutFadingTheCatalog() {
        show(dark = true)
        assertSharedIdentity("model-list")
        val source = compose.onNodeWithTag("model:openai/one").fetchSemanticsNode().boundsInRoot
        val sourceLogo = drawnLogoBounds()
        val before = compose.onRoot().captureToImage().toPixelMap()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("model:openai/one").performClick()
        compose.mainClock.advanceTimeBy(160)
        val middlePixels = compose.onRoot().captureToImage().toPixelMap()
        val middle = changedBounds(before, middlePixels)
        val middleLogo = drawnLogoBounds(container = middle)
        assertEquals("共享 Logo 不能再叠加容器的非等比拉伸", middleLogo.width, middleLogo.height, 2f)
        compose.mainClock.advanceTimeBy(600)
        val target = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val targetLogo = drawnLogoBounds()
        assertSharedIdentity("model-detail-screen")
        assertTrue("Logo 应独立从卡片飞到居中身份区", middleLogo.center.x > sourceLogo.center.x + 1f &&
            middleLogo.center.x < targetLogo.center.x - 1f)
        assertTrue("Logo 应连续从 56dp 放大到 88dp，而非直接跳到详情尺寸",
            middleLogo.width > sourceLogo.width + 1f && middleLogo.width < targetLogo.width - 1f)
        val openingProgress = (middle.height - source.height) / (target.height - source.height)
        assertRectNear(lerp(sourceLogo, targetLogo, openingProgress), middleLogo)
        val frames = "$source → $middle → $target"
        assertTrue("整卡必须连续变宽，而非只移动 Logo: $frames", middle.width > source.width + 1f)
        assertTrue("中间帧不能已经铺满全屏: $frames", middle.width < target.width - 1f)
        assertTrue("整卡必须连续变高: $frames", middle.height > source.height + 1f)
        assertTrue("中间帧高度不能直接跳至全屏: $frames", middle.height < target.height - 1f)
        assertTrue("容器必须从原卡片向上展开: $frames", middle.top < source.top - 1f)
        val cornerX = middle.left.toInt() + 1
        val cornerY = middle.top.toInt() + 1
        assertEquals("中间帧仍应裁剪圆角，而非立即变直角", before[cornerX, cornerY].toArgb(),
            middlePixels[cornerX, cornerY].toArgb())
        // 容器之外的目录保持原样，不跟着详情做全屏淡化。
        val y = (middle.top / 2).toInt()
        for (x in 0 until before.width) assertEquals(before[x, y].toArgb(), middlePixels[x, y].toArgb())
        compose.onNodeWithTag("model-list").assertIsNotDisplayed()
        compose.onNodeWithTag("glass-navigation").assertIsNotDisplayed()

        compose.onNodeWithContentDescription("返回").performClick()
        compose.mainClock.advanceTimeBy(160)
        val returning = changedBounds(before, compose.onRoot().captureToImage().toPixelMap())
        val returningLogo = drawnLogoBounds(container = returning)
        val returningProgress = (returning.height - source.height) / (target.height - source.height)
        assertRectNear(lerp(sourceLogo, targetLogo, returningProgress), returningLogo)
        assertTrue("返回也应收缩整卡: $returning", returning.width > source.width + 1f && returning.width < target.width - 1f)
        assertTrue(returning.height > source.height + 1f && returning.height < target.height - 1f)
        compose.runOnIdle { assertEquals("详情退出动画结束前不能销毁", 0, detailsDisposed) }
        compose.mainClock.advanceTimeBy(600)
        compose.mainClock.autoAdvance = true
        assertRectEquals(source, compose.onNodeWithTag("model:openai/one").fetchSemanticsNode().boundsInRoot)
        assertRectEquals(sourceLogo, drawnLogoBounds())
        compose.onNodeWithTag("model-detail-screen").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, detailsDisposed)
            assertEquals(1, listCompositions)
            assertEquals(0, listDisposals)
        }
    }

    @Test fun loadingIdentityMatchesWithoutInventingDetailAndErrorsDiscardTheSnapshot() {
        status.value = ModelDetailStatus.LOADING
        show()
        compose.onNodeWithTag("model:openai/one").performClick()
        compose.onNodeWithTag("model-detail-hero").assertIsDisplayed()
        compose.onNodeWithTag("model-detail-metrics").assertDoesNotExist()
        compose.onNodeWithTag("model-description").assertDoesNotExist()
        val loadingName = nameBounds()
        compose.runOnIdle { status.value = ModelDetailStatus.CONTENT }
        assertRectEquals(loadingName, nameBounds())
        compose.onNodeWithTag("model-detail-metrics").assertIsDisplayed()
        compose.runOnIdle { status.value = ModelDetailStatus.ERROR }
        compose.onNodeWithTag("model-detail-hero").assertDoesNotExist()
        sharedIdentityNode("model-detail-screen", ModelIdentityRole.LOGO).assertDoesNotExist()
        compose.onNodeWithText("重试").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("model:openai/one").assertIsDisplayed()
    }

    @Test fun hostedDarkDetailKeepsReadableThemeForegroundWithATransparentScaffold() = assertDetailForeground(dark = true)

    @Test fun hostedLightDetailKeepsThemeForegroundWithATransparentScaffold() = assertDetailForeground(dark = false)

    @Test fun lightReturnDoesNotFlashOutsideTheContractingCard() = assertReturnFrameContinuity(dark = false)

    @Test fun darkReturnDoesNotFlashOutsideTheContractingCard() = assertReturnFrameContinuity(dark = true)

    @Test fun scrolledSystemReturnDoesNotFlashOutsideTheContractingCard() =
        assertReturnFrameContinuity(dark = true, scrolled = true)

    /** 不只检查中间帧和最终状态：覆盖共享层恢复匹配、退出与详情清理的逐帧交接。 */
    private fun assertReturnFrameContinuity(dark: Boolean, scrolled: Boolean = false) {
        show(dark = dark)
        val source = compose.onNodeWithTag("model:openai/one").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val catalogPixels = compose.onRoot().captureToImage().toPixelMap()
        compose.onNodeWithTag("model:openai/one").performClick()
        compose.onNodeWithTag("model-detail-screen").assertIsDisplayed()
        if (scrolled) compose.onNodeWithTag("model-detail-scroll").performScrollToIndex(2)
        compose.mainClock.autoAdvance = false
        if (scrolled) pressBack() else compose.onNodeWithContentDescription("返回").performClick()
        val startedAt = compose.mainClock.currentTime
        val frames = mutableListOf<Pair<Long, Rect?>>()
        repeat(36) {
            compose.mainClock.advanceTimeByFrame()
            frames += (compose.mainClock.currentTime - startedAt) to changedBoundsOrNull(
                catalogPixels, compose.onRoot().captureToImage().toPixelMap(), channelTolerance = 4,
            )
        }
        compose.mainClock.autoAdvance = true
        val sequence = frames.joinToString("\n") { (time, bounds) -> "${time}ms=$bounds" }
        frames.forEach { (time, changed) ->
            // 为匹配恢复、测量及动画起钟留 64ms 余量；只容忍向卡片收缩，不能重新铺满页面。
            // 背景与详情底色可能相同，不能用变化像素外框严格推断初始容器大小。
            val progress = FastOutSlowInEasing.transform(
                ((time - 64).coerceAtLeast(0).toFloat() / ModelTransitionDurationMillis).coerceIn(0f, 1f),
            )
            val allowed = lerp(root, source, progress).inflate(3f)
            if (changed != null) assertTrue("收缩及退出交接不能在容器外闪出详情：\n$sequence",
                changed.left >= allowed.left && changed.top >= allowed.top &&
                    changed.right <= allowed.right && changed.bottom <= allowed.bottom)
        }
        assertEquals("交接结束后应恢复原列表像素：\n$sequence", null, frames.last().second)
        compose.onNodeWithTag("model-detail-screen").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, detailsDisposed)
            assertEquals(1, listCompositions)
            assertEquals(0, listDisposals)
        }
    }

    private fun assertDetailForeground(dark: Boolean) {
        show(dark = dark)
        compose.onNodeWithTag("model:openai/one").performClick()
        val bounds = nameBounds()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        var foregroundPixels = 0
        for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
            for (x in bounds.left.toInt() until bounds.right.toInt()) {
                if (pixels[x, y].toArgb() == foregroundColor) foregroundPixels++
            }
        }
        assertTrue("宿主中的标题应沿用主题 onSurface，而非透明 Scaffold 的默认黑色", foregroundPixels > 100)
    }

    @Test fun returningDuringEntrySettlesWithoutAnOrphanedDetail() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("model:openai/one").performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithContentDescription("返回").performClick()
        compose.mainClock.advanceTimeBy(900)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("model-detail-screen").assertDoesNotExist()
        compose.onNodeWithTag("model:openai/one").assertIsDisplayed()
        compose.onNodeWithTag("model:openai/one").performClick()
        compose.onNodeWithTag("model-detail-hero").assertIsDisplayed()
    }

    @Test fun reversingEntryKeepsDrawingTheContainerUntilItsExitCompletes() {
        show(dark = true)
        val source = compose.onNodeWithTag("model:openai/one").fetchSemanticsNode().boundsInRoot
        val catalog = compose.onRoot().captureToImage().toPixelMap()
        // 原卡片正上方的留白：入场容器已经覆盖这里，但底层目录没有内容。
        val x = source.center.x.toInt()
        val y = source.top.toInt() - 8
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("model:openai/one").performClick()
        compose.mainClock.advanceTimeBy(160)
        assertNotEquals(catalog[x, y].toArgb(), compose.onRoot().captureToImage().toPixelMap()[x, y].toArgb())
        compose.onNodeWithContentDescription("返回").performClick()
        compose.mainClock.advanceTimeByFrame()
        assertNotEquals("反向动画尚在运行，不能因起止状态同为 PostExit 而提前隐藏容器",
            catalog[x, y].toArgb(), compose.onRoot().captureToImage().toPixelMap()[x, y].toArgb())
        compose.mainClock.advanceTimeBy(900)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("model-detail-screen").assertDoesNotExist()
    }

    @Test fun localDetailArrivingDuringEntryDoesNotRestartTheContainerExpansion() {
        status.value = ModelDetailStatus.LOADING
        show(dark = true)
        val before = compose.onRoot().captureToImage().toPixelMap()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("model:openai/one").performClick()
        compose.mainClock.advanceTimeBy(96)
        val loadingFrame = changedBounds(before, compose.onRoot().captureToImage().toPixelMap())
        val loadingLogo = drawnLogoBounds()
        compose.runOnIdle { status.value = ModelDetailStatus.CONTENT }
        compose.mainClock.advanceTimeBy(96)
        val contentFrame = changedBounds(before, compose.onRoot().captureToImage().toPixelMap())
        val contentLogo = drawnLogoBounds()
        assertTrue("真实详情接管时不能把容器移回卡片", contentFrame.top <= loadingFrame.top + 1f)
        assertTrue("真实详情接管时不能重新缩小容器", contentFrame.height >= loadingFrame.height - 1f)
        assertTrue("真实详情接管时不能把共享 Logo 移回卡片", contentLogo.center.x >= loadingLogo.center.x - 1f)
        assertTrue("真实详情接管时不能重新缩小共享 Logo", contentLogo.width >= loadingLogo.width - 1f)
        compose.mainClock.advanceTimeBy(600)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("model-detail-metrics").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("model:openai/one").assertIsDisplayed()
    }

    @Test fun scrolledDetailStillContractsToItsCardWithoutMovingEitherListAndSameLabModelsDoNotMatch() {
        show(dark = true)
        val source = compose.onNodeWithTag("model:openai/one").fetchSemanticsNode().boundsInRoot
        val before = compose.onRoot().captureToImage().toPixelMap()
        compose.onNodeWithTag("model:openai/one").performClick()
        compose.onNodeWithTag("model-detail-scroll").performScrollToIndex(2)
        sharedIdentityNode("model-detail-screen", ModelIdentityRole.LOGO).assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        pressBack()
        compose.mainClock.advanceTimeBy(160)
        val returning = changedBounds(before, compose.onRoot().captureToImage().toPixelMap())
        assertTrue(returning.height > source.height + 1f && returning.height < before.height - 1f)
        compose.onNodeWithTag("model-detail-hero").assertIsNotDisplayed()
        compose.mainClock.advanceTimeBy(600)
        compose.mainClock.autoAdvance = true
        assertRectEquals(source, compose.onNodeWithTag("model:openai/one").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("model:openai/two").performClick()
        compose.onNodeWithText("Second model").assertIsDisplayed()
        compose.onNodeWithTag("model-container:openai/one:detail").assertDoesNotExist()
        ModelIdentityRole.entries.forEach { role ->
            sharedIdentityNode("model-detail-screen", role).assertDoesNotExist()
            sharedIdentityNode("model-detail-screen", role, modelId = "openai/two").assertIsDisplayed()
        }
    }

    @Test fun restoredDetailHasNoSavedSnapshotAndPreservesItsScrollInDarkLargeRtlLayout() {
        val restoration = StateRestorationTester(compose)
        show(restoration, dark = true, fontScale = 2f, rtl = true)
        compose.onNodeWithTag("model:openai/one").performClick()
        compose.onNodeWithTag("model-detail-scroll").performScrollToIndex(2)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("model-detail-screen").assertIsDisplayed()
        compose.onNodeWithTag("model-detail-hero").assertIsNotDisplayed()
        compose.onNodeWithTag("main-pager").assertIsNotDisplayed()
        pressBack()
        compose.onNodeWithTag("model:openai/one").assertIsDisplayed()
    }

    @Test fun removedModelAndMissingSourceCardReturnToTheCurrentCatalogWithoutRecreatingIt() {
        show()
        compose.onNodeWithTag("model:openai/one").performClick()
        compose.runOnIdle {
            catalog.value = emptyList()
            status.value = ModelDetailStatus.NOT_FOUND
        }
        compose.onNodeWithTag("model-detail-hero").assertDoesNotExist()
        compose.onNodeWithText("模型已不在当前目录中").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("model-detail-screen").assertDoesNotExist()
        compose.onNodeWithTag("model-list").assertIsDisplayed()
        compose.onNodeWithTag("model:openai/one").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, listCompositions) }
    }

    @Test fun noticeTimerContinuesWhileTheCatalogIsHiddenByDetail() {
        show(notice = ModelListMessage.UP_TO_DATE)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("model:openai/one").performClick()
        compose.mainClock.advanceTimeBy(3_500)
        compose.runOnIdle { assertEquals(1, noticesDismissed) }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.mainClock.autoAdvance = true
        compose.runOnIdle {
            assertEquals(1, noticesDismissed)
            assertEquals(1, listCompositions)
        }
    }

    private fun nameBounds(): Rect = compose.onNode(
        (hasTestTag("model-name") or hasTestTag("model-shared:openai/one:NAME")) and
            hasAnyAncestor(hasTestTag("model-detail-screen")), useUnmergedTree = true,
    ).fetchSemanticsNode().boundsInRoot

    private fun sharedIdentityNode(page: String, role: ModelIdentityRole, modelId: String = "openai/one") = compose.onNode(
        hasTestTag("model-shared:$modelId:$role") and hasAnyAncestor(hasTestTag(page)), useUnmergedTree = true,
    )

    private fun assertSharedIdentity(page: String) {
        ModelIdentityRole.entries.forEach { sharedIdentityNode(page, it).assertIsDisplayed() }
    }

    /** 容器与身份使用同一边界进度；容忍截图抗锯齿及像素取整，不容忍父级二次缩放。 */
    private fun assertRectNear(expected: Rect, actual: Rect) {
        val frames = "$expected ≈ $actual"
        assertEquals(frames, expected.left, actual.left, 4f)
        assertEquals(frames, expected.top, actual.top, 4f)
        assertEquals(frames, expected.width, actual.width, 4f)
        assertEquals(frames, expected.height, actual.height, 4f)
    }

    /** 容器绘制在根覆盖层，语义坐标只是全屏占位；按实际变化像素验证展开边界。 */
    private fun changedBounds(before: PixelMap, after: PixelMap): Rect {
        return checkNotNull(changedBoundsOrNull(before, after)) { "动画应实际改变画面" }
    }

    private fun changedBoundsOrNull(before: PixelMap, after: PixelMap, channelTolerance: Int = 0): Rect? {
        var left = before.width
        var right = 0
        var top = before.height
        var bottom = 0
        for (y in 0 until before.height) for (x in 0 until before.width) {
            val oldColor = before[x, y].toArgb()
            val newColor = after[x, y].toArgb()
            // 共享层切换时文字抗锯齿会有少量舍入差异，不把它当作整页回闪。
            if (oldColor != newColor && (channelTolerance == 0 || (0..24 step 8).any { shift ->
                    kotlin.math.abs(((oldColor ushr shift) and 255) - ((newColor ushr shift) and 255)) > channelTolerance
                })) {
                left = minOf(left, x)
                right = maxOf(right, x + 1)
                top = minOf(top, y)
                bottom = maxOf(bottom, y + 1)
            }
        }
        if (right <= left || bottom <= top) return null
        return Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }

    /** 最终 Logo 仍保持比例，并返回原卡片。 */
    private fun drawnLogoBounds(container: Rect? = null): Rect {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val width = pixels.width
        val height = pixels.height
        val white = BooleanArray(width * height) { index ->
            val color = pixels[index % width, index / width].toArgb()
            ((color shr 16) and 255) >= 253 && ((color shr 8) and 255) >= 253 && (color and 255) >= 253
        }
        val queue = IntArray(white.size)
        var largestArea = 0
        var largestBounds = Rect.Zero
        var logosInContainer = 0
        for (start in white.indices) {
            if (!white[start]) continue
            var head = 0
            var tail = 1
            queue[0] = start
            white[start] = false
            var left = width
            var right = 0
            var top = height
            var bottom = 0
            while (head < tail) {
                val point = queue[head++]
                val x = point % width
                val y = point / width
                left = minOf(left, x)
                right = maxOf(right, x)
                top = minOf(top, y)
                bottom = maxOf(bottom, y)
                fun visit(next: Int) {
                    if (white[next]) {
                        white[next] = false
                        queue[tail++] = next
                    }
                }
                if (x > 0) visit(point - 1)
                if (x < width - 1) visit(point + 1)
                if (y > 0) visit(point - width)
                if (y < height - 1) visit(point + width)
            }
            val bounds = Rect(left.toFloat(), top.toFloat(), (right + 1).toFloat(), (bottom + 1).toFloat())
            if (tail > 1_000 && container?.contains(bounds.center) == true) logosInContainer++
            if (tail > largestArea) {
                largestArea = tail
                largestBounds = bounds
            }
        }
        assertTrue("截图中必须找到固定白底的品牌 logo", largestArea > 1_000)
        if (container != null) assertEquals("展开／收回的容器中只能绘制一份 Logo", 1, logosInContainer)
        return largestBounds
    }

    private fun assertRectEquals(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 1f)
        assertEquals(expected.top, actual.top, 1f)
        assertEquals(expected.width, actual.width, 1f)
        assertEquals(expected.height, actual.height, 1f)
    }

    private fun show(
        restoration: StateRestorationTester? = null,
        dark: Boolean = false,
        fontScale: Float = 1f,
        rtl: Boolean = false,
        notice: ModelListMessage? = null,
    ) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                ModelAtlasTheme(darkTheme = dark, dynamicColor = false) {
                    foregroundColor = MaterialTheme.colorScheme.onSurface.toArgb()
                    ModelAtlasScaffold(detailContent = { id, back ->
                        DisposableEffect(id) { onDispose { detailsDisposed++ } }
                        val name = if (id == "openai/one") "First model" else "Second model"
                        ModelDetailScreen(ModelDetailUiState(id, status.value, if (status.value == ModelDetailStatus.CONTENT)
                            ModelDetail(id, name, metadata = ModelDetailMetadata(openWeights = true), license = "MIT",
                                fetchedAtEpochMillis = 100) else null), onBack = back)
                    }) { padding, state, open ->
                        DisposableEffect(Unit) {
                            listCompositions++
                            onDispose { listDisposals++ }
                        }
                        ModelListScreen(ModelListUiState(models = catalog.value, isLoading = false, message = notice),
                            listState = state, bottomOverlayPadding = padding, onModelClick = open,
                            onDismissNotice = { noticesDismissed++ })
                    }
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    }
}

class ModelSharedTransitionReducedMotionTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test fun disabledAnimationsSettleBothDirectionsAndDisposeTheDetail() {
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold(detailContent = { id, back ->
                    ModelDetailScreen(ModelDetailUiState(id, ModelDetailStatus.CONTENT,
                        ModelDetail(id, "Model", fetchedAtEpochMillis = 0)), onBack = back)
                }) { padding, state, open ->
                    ModelListScreen(ModelListUiState(models = listOf(ModelSummary("openai/one", "Model")), isLoading = false),
                        bottomOverlayPadding = padding, listState = state, onModelClick = open)
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("model:openai/one").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("model-detail-hero").assertIsDisplayed()
        compose.onNodeWithTag("main-pager").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("model-detail-screen").assertDoesNotExist()
        compose.onNodeWithTag("model:openai/one").assertIsDisplayed()
    }
}
