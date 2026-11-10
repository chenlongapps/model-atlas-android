package io.github.chenlongapps.modelatlas.ui.benchmark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.test.espresso.Espresso.pressBack
import io.github.chenlongapps.modelatlas.ui.ModelAtlasScaffold
import io.github.chenlongapps.modelatlas.ui.modeldetail.ModelBackTestInput
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListScreen
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListUiState
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import io.github.chenlongapps.modelatlas.testing.saveRegressionScreenshot
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BenchmarkSharedTransitionTest {
    @get:Rule val compose = createComposeRule()
    private val board = benchmarkPreviewBoard().copy(description = "评测说明", rankings = List(24) { index ->
        benchmarkPreviewBoard().rankings[index % 3].copy(scoreId = "score-$index", modelId = "model-$index", rank = index + 1)
    })
    private val state = mutableStateOf(BenchmarkUiState(isLoading = false, boards = listOf(board)))
    private val input = ModelBackTestInput()
    private lateinit var transition: BenchmarkSharedTransition
    private var overviewDisposals = 0
    private var leaderboardDisposals = 0
    private var trackColor = Color.Unspecified
    private val resultColors = mutableMapOf<String, Color>()

    @Test fun darkCardExpandsAndContractsWithoutFadingTheOverview() = assertContainerFrames(dark = true)
    @Test fun lightCardExpandsAndContractsWithoutFadingTheOverview() = assertContainerFrames(dark = false)

    @Test fun topThreeResultsShareBothDirectionsAcrossDifferentChartRangesInDark() = assertResultFrames(dark = true)
    @Test fun topThreeResultsShareBothDirectionsAcrossDifferentChartRangesInLight() = assertResultFrames(dark = false)

    private fun assertResultFrames(dark: Boolean) {
        val changed = configureDifferentRange()
        show(dark = dark)
        val sourceImage = pixels()
        val source = changed.rankings.take(3).map { entry -> resultGeometry(entry.modelId, sourceImage, "benchmark-screen") }
        changed.rankings.take(3).forEach { entry -> assertResultTextFits(entry.modelId, "benchmark-screen") }
        val dp = source.first().track.height / 6f
        source.forEach { assertEquals(6f * dp, it.track.height, 1f) }
        // 实际填充占比取各页自己的轴，不为共享动画统一刻度。
        val topRange = benchmarkChartRange(changed.rankings.take(3).mapNotNull { it.score }, 0.0, 100.0)
        val fullRange = benchmarkChartRange(changed.rankings.mapNotNull { it.score }, 0.0, 100.0)
        assertNotEquals(topRange, fullRange)
        compose.mainClock.autoAdvance = false
        open()
        compose.mainClock.advanceTimeBy(176)
        val middle = pixels()
        val expansion = transition.expansionProgress()
        saveRegressionScreenshot("benchmark-results-${if (dark) "dark" else "light"}-opening")
        compose.mainClock.advanceTimeBy(650)
        val targetImage = pixels()
        val target = changed.rankings.take(3).map { entry -> resultGeometry(entry.modelId, targetImage, "benchmark-leaderboard") }
        changed.rankings.take(3).forEach { entry -> assertResultTextFits(entry.modelId, "benchmark-leaderboard") }
        target.forEach { assertEquals(8f * dp, it.track.height, 1f) }
        changed.rankings.take(3).forEachIndexed { index, entry ->
            assertEquals(topRange.fraction(entry.score!!), source[index].fill.width / source[index].track.width, .01f)
            assertEquals(fullRange.fraction(entry.score), target[index].fill.width / target[index].track.width, .01f)
            assertResultNear(source[index], target[index], middle, expansion, entry.modelId)
            resultNode(entry.modelId, BenchmarkResultRole.SCORE).assertTextEquals(formatBoardScore(entry, changed.unit))
        }
        compose.onNodeWithTag("benchmark-shared-result:${board.id}:model-3:NAME", true).assertDoesNotExist()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.mainClock.advanceTimeBy(176)
        val returning = pixels()
        saveRegressionScreenshot("benchmark-results-${if (dark) "dark" else "light"}-returning")
        changed.rankings.take(3).forEachIndexed { index, entry ->
            assertResultNear(source[index], target[index], returning, transition.expansionProgress(), entry.modelId)
        }
        compose.mainClock.advanceTimeBy(650)
        compose.mainClock.autoAdvance = true
        assertReturned()
        val restored = pixels()
        changed.rankings.take(3).forEachIndexed { index, entry ->
            assertRectNear(source[index].fill, coloredBounds(restored, resultColors.getValue(entry.modelId)))
        }
    }

    @Test fun sharedResultsStayVisibleWhilePredictiveBackIsPausedAndCancelled() {
        val changed = configureDifferentRange()
        show()
        val before = pixels()
        val source = changed.rankings.take(3).map { resultGeometry(it.modelId, before, "benchmark-screen") }
        open()
        compose.onNodeWithTag("benchmark-leaderboard").assertIsDisplayed()
        val after = pixels()
        val target = changed.rankings.take(3).map { resultGeometry(it.modelId, after, "benchmark-leaderboard") }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { input.start() }
        compose.mainClock.advanceTimeBy(32)
        for (fraction in listOf(.25f, .5f, .9f, 1f)) {
            compose.runOnIdle { input.progress(fraction) }
            compose.mainClock.advanceTimeBy(64)
            val image = pixels()
            changed.rankings.take(3).forEachIndexed { index, entry ->
                assertResultNear(source[index], target[index], image, transition.expansionProgress(), entry.modelId)
            }
            val paused = transition.expansionProgress()
            compose.mainClock.advanceTimeBy(64)
            compose.runOnIdle { assertEquals(paused, transition.expansionProgress(), .001f) }
        }
        compose.runOnIdle { input.cancel() }
        compose.mainClock.advanceTimeBy(700)
        compose.mainClock.autoAdvance = true
        val restored = pixels()
        changed.rankings.take(3).forEachIndexed { index, entry ->
            assertRectNear(target[index].fill, coloredBounds(restored, resultColors.getValue(entry.modelId)))
        }
        compose.runOnIdle { assertEquals(0, leaderboardDisposals) }
        pressBack()
        assertReturned()
    }

    @Test fun resultKeysIgnoreNamesAndTiedRanksAndScoreFormattingKeepsEndpointUnits() {
        val changed = configureDifferentRange().copy(unit = "points", rankings = board.rankings.take(3).mapIndexed { index, entry ->
            entry.copy(modelName = "同名模型", rank = if (index < 2) 1 else 3)
        })
        state.value = state.value.copy(boards = listOf(changed))
        show()
        changed.rankings.forEach { entry ->
            resultNode(entry.modelId, BenchmarkResultRole.SCORE, "benchmark-screen")
                .assertTextEquals(formatCompactBoardScore(entry, "points"))
        }
        open()
        changed.rankings.forEach { entry ->
            resultNode(entry.modelId, BenchmarkResultRole.NAME).assertTextEquals("同名模型")
            resultNode(entry.modelId, BenchmarkResultRole.SCORE).assertTextEquals(formatBoardScore(entry, "points"))
        }
        pressBack()
        assertReturned()
    }

    @Test fun wrappedLargeFontRtlResultsKeepTheirOwnKeysAndDoNotForceAScrolledReturnToTop() {
        val changed = configureDifferentRange().let { value -> value.copy(rankings = value.rankings.mapIndexed { index, entry ->
            if (index == 0) entry.copy(modelName = "用于检查完整换行的较长模型名称", reasoningEffort = "Max") else entry
        }) }
        state.value = state.value.copy(boards = listOf(changed))
        show(largeRtl = true)
        resultNode("model-0", BenchmarkResultRole.SCORE, "benchmark-screen").assertTextEquals("72.4%")
        assertResultTextFits("model-0", "benchmark-screen")
        open()
        resultNode("model-0", BenchmarkResultRole.NAME).assertTextEquals("用于检查完整换行的较长模型名称 · Max")
        resultNode("model-0", BenchmarkResultRole.SCORE).assertTextEquals("72.4%")
        assertResultTextFits("model-0", "benchmark-leaderboard")
        compose.onNodeWithTag("benchmark-leaderboard-list").performScrollToIndex(12)
        compose.onNodeWithTag("benchmark-shared-result:${board.id}:model-0:NAME", true).assertDoesNotExist()
        pressBack()
        assertReturned()
    }

    @Test fun zeroLengthSharedFillsCanOpenAndReturnWithoutInventingAValue() {
        state.value = state.value.copy(boards = listOf(board.copy(minScore = 0.0, maxScore = 100.0,
            rankings = board.rankings.take(3).map { it.copy(score = 0.0) })))
        show()
        open()
        resultNode("model-0", BenchmarkResultRole.SCORE).assertTextEquals("0.0%")
        pressBack()
        assertReturned()
    }

    private fun assertContainerFrames(dark: Boolean) {
        show(dark = dark)
        val source = compose.onNodeWithTag(cardTag).fetchSemanticsNode().boundsInRoot
        val sourceName = compose.onNode(hasTestTag("benchmark-shared:${board.id}:NAME") and
            hasAnyAncestor(hasTestTag("benchmark-screen")), true).fetchSemanticsNode().boundsInRoot
        val before = pixels()
        saveRegressionScreenshot("benchmark-transition-${if (dark) "dark" else "light"}-source")
        compose.mainClock.autoAdvance = false
        open()
        compose.mainClock.advanceTimeBy(160)
        val middlePixels = pixels()
        saveRegressionScreenshot("benchmark-transition-${if (dark) "dark" else "light"}-opening")
        val middle = checkNotNull(changedBounds(before, middlePixels))
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("整卡连续展开，而非直接铺满页面：$source → $middle → $root", middle.width > source.width && middle.width < root.width)
        assertTrue(middle.height > source.height && middle.height < root.height)
        assertTrue(middle.top < source.top)
        // 动画容器之外的总览不淡出，背景标题及其他卡片不参与缩放。
        for (y in 0 until (middle.top / 2).toInt()) for (x in 0 until before.width) {
            assertEquals(before[x, y].toArgb(), middlePixels[x, y].toArgb())
        }
        compose.mainClock.advanceTimeBy(650)
        compose.onNodeWithTag("benchmark-leaderboard").assertIsDisplayed()
        compose.onNodeWithTag("main-pager").assertIsNotDisplayed()
        saveRegressionScreenshot("benchmark-transition-${if (dark) "dark" else "light"}-leaderboard")
        sharedNode(BenchmarkIdentityRole.LOGO).assertIsDisplayed()
        sharedNode(BenchmarkIdentityRole.NAME).assertIsDisplayed()
        val targetName = sharedNode(BenchmarkIdentityRole.NAME).fetchSemanticsNode().boundsInRoot
        assertEquals("相同字体／名称的共享边界仅包裹文字，不能包含总览 weight 列留白", sourceName.width, targetName.width, 1f)
        assertEquals(sourceName.height, targetName.height, 1f)
        compose.onNodeWithContentDescription("返回").performClick()
        compose.mainClock.advanceTimeBy(160)
        val returning = checkNotNull(changedBounds(before, pixels()))
        saveRegressionScreenshot("benchmark-transition-${if (dark) "dark" else "light"}-returning")
        assertTrue("整卡连续收缩：$root → $returning → $source", returning.width > source.width && returning.width < root.width)
        assertTrue("整卡连续收缩：$root → $returning → $source", returning.height > source.height && returning.height < root.height)
        compose.runOnIdle { assertEquals(0, leaderboardDisposals) }
        compose.mainClock.advanceTimeBy(650)
        compose.mainClock.autoAdvance = true
        assertReturned()
        assertEquals(source, compose.onNodeWithTag(cardTag).fetchSemanticsNode().boundsInRoot)
        assertNull("共享层交接后恢复总览像素", changedBounds(before, pixels(), tolerance = 4))
    }

    @Test fun predictiveBackPausesCancelsAndCommitsFromEitherEdge() {
        show()
        open()
        compose.onNodeWithTag("benchmark-leaderboard").assertIsDisplayed()
        for (edge in listOf(NavigationEvent.EDGE_LEFT, NavigationEvent.EDGE_RIGHT)) {
            input.edge = edge
            compose.mainClock.autoAdvance = false
            startBack(.55f)
            val expansion = transition.expansionProgress()
            assertTrue("返回进度应处于中间帧，实际 $expansion", expansion > 0f && expansion < 1f)
            compose.mainClock.advanceTimeBy(160)
            compose.runOnIdle { assertEquals(expansion, transition.expansionProgress(), .001f) }
            compose.onNodeWithTag("benchmark-screen").assertIsNotDisplayed()
            compose.runOnIdle { input.cancel() }
            compose.mainClock.advanceTimeBy(700)
            compose.runOnIdle { assertEquals(1f, transition.expansionProgress(), .001f) }
            compose.mainClock.autoAdvance = true
            compose.onNodeWithTag("benchmark-leaderboard").assertIsDisplayed()
        }
        compose.mainClock.autoAdvance = false
        startBack(1f)
        compose.runOnIdle { assertEquals("100% 手势未确认也不能销毁页面", 0, leaderboardDisposals) }
        compose.runOnIdle { input.complete() }
        compose.mainClock.advanceTimeBy(700)
        compose.mainClock.autoAdvance = true
        assertReturned()
        compose.runOnIdle { assertFalse(input.hasEnabledHandlers) }
    }

    @Test fun returningDuringEntryCanBeCancelledThenImmediatelyCommitted() {
        show()
        compose.mainClock.autoAdvance = false
        open()
        compose.mainClock.advanceTimeBy(96)
        startBack(.5f)
        compose.runOnIdle { input.cancel() }
        compose.mainClock.advanceTimeBy(48)
        startBack(.75f)
        compose.runOnIdle { input.complete() }
        compose.mainClock.advanceTimeBy(900)
        compose.mainClock.autoAdvance = true
        assertReturned()
        open()
        compose.onNodeWithTag("benchmark-leaderboard").assertIsDisplayed()
    }

    @Test fun predictiveCancellationKeepsTheLogoDrawnAndDoesNotFlashOutsideTheContainer() {
        show()
        val source = compose.onNodeWithTag(cardTag).fetchSemanticsNode().boundsInRoot
        val sourceLogo = compose.onNode(hasTestTag("benchmark-shared:${board.id}:LOGO") and
            hasAnyAncestor(hasTestTag("benchmark-screen")), true).fetchSemanticsNode().boundsInRoot
        val before = pixels()
        open()
        val targetLogo = sharedNode(BenchmarkIdentityRole.LOGO).fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        startBack(.7f)
        compose.runOnIdle { input.cancel() }
        repeat(26) { frame ->
            compose.mainClock.advanceTimeByFrame()
            val image = pixels()
            val progress = transition.expansionProgress()
            val allowed = lerp(source, root, progress).inflate(3f)
            val changed = changedBounds(before, image, tolerance = 4)
            if (changed != null) assertTrue("取消第 $frame 帧不能在共享容器外闪出榜单：$changed / $allowed",
                changed.left >= allowed.left && changed.top >= allowed.top && changed.right <= allowed.right && changed.bottom <= allowed.bottom)
            val logo = lerp(sourceLogo, targetLogo, progress)
            var visibleLogoPixels = 0
            for (y in logo.top.toInt().coerceAtLeast(0) until logo.bottom.toInt().coerceAtMost(image.height)) {
                for (x in logo.left.toInt().coerceAtLeast(0) until logo.right.toInt().coerceAtMost(image.width)) {
                    val c = image[x, y]
                    if (c.red > .65f && c.green > .65f && c.blue > .65f) visibleLogoPixels++
                }
            }
            assertTrue("取消第 $frame 帧 Logo 不能因共享层交接消失", visibleLogoPixels > 100)
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("benchmark-leaderboard").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1f, transition.expansionProgress(), .001f) }
        pressBack()
        assertReturned()
    }

    @Test fun buttonReturnDuringEntryDoesNotLeaveAnOrphanedLeaderboard() {
        show()
        compose.mainClock.autoAdvance = false
        open()
        // 非共享返回栏在身份接近终点才淡入；此时仍处于 450ms 入场阶段。
        compose.mainClock.advanceTimeBy(336)
        compose.onNodeWithContentDescription("返回").performClick()
        compose.mainClock.advanceTimeBy(900)
        compose.mainClock.autoAdvance = true
        assertReturned()
    }

    @Test fun scrollingDisablesIdentityMatchingAndBothReadingPositionsSurviveReentry() {
        val boards = List(10) { board.copy(id = "board-$it", name = "评测 $it") }
        state.value = state.value.copy(boards = boards)
        show()
        compose.onNodeWithTag("benchmark-list").performScrollToIndex(5)
        val tag = "benchmark-card:board-4"
        val source = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag(tag).performClick()
        compose.onNodeWithTag("benchmark-leaderboard-list").performScrollToIndex(12)
        val saved = compose.onNodeWithTag("benchmark-result:score-11", true).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("benchmark-shared:board-4:LOGO", true).assertDoesNotExist()
        pressBack()
        assertEquals(source, compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag(tag).performClick()
        assertEquals(saved, compose.onNodeWithTag("benchmark-result:score-11", true).fetchSemanticsNode().boundsInRoot)
        pressBack()
        compose.onNodeWithTag("benchmark-leaderboard").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, overviewDisposals) }
    }

    @Test fun descriptionDialogConsumesBackWithoutStartingPageReturn() {
        show()
        open()
        compose.onNodeWithContentDescription("查看评测说明").performClick()
        pressBack()
        compose.onNodeWithTag("benchmark-description").assertDoesNotExist()
        compose.onNodeWithTag("benchmark-leaderboard").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1f, transition.expansionProgress(), .001f) }
        pressBack()
        assertReturned()
    }

    @Test fun removedOrFailedBoardDoesNotKeepOldIdentityAndMissingSourceFallsBack() {
        show()
        open()
        compose.runOnIdle { state.value = state.value.copy(hasError = true, boards = emptyList()) }
        compose.onNodeWithText("无法读取本地评测").assertIsDisplayed()
        sharedNode(BenchmarkIdentityRole.LOGO).assertDoesNotExist()
        compose.onNodeWithTag("benchmark-result:score-0", true).assertDoesNotExist()
        pressBack()
        compose.onNodeWithTag("benchmark-leaderboard").assertDoesNotExist()
        compose.onNodeWithTag("nav-benchmarks").assertIsDisplayed()
    }

    @Test fun wideWordmarkSameNamedVersionsAndLargeFontRtlUseBoardSpecificKeys() {
        val wide = board.copy(benchmarkId = "automationbench-v1.0.6", name = "同名评测", rankings = emptyList())
        state.value = state.value.copy(boards = listOf(wide, wide.copy(id = "another-version", version = "2026.11")))
        show(largeRtl = true)
        open()
        sharedNode(BenchmarkIdentityRole.LOGO).assertIsDisplayed()
        sharedNode(BenchmarkIdentityRole.NAME).assertIsDisplayed()
        compose.onNodeWithText("版本：4.0.0").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("benchmark-list").performScrollToIndex(2)
        compose.onNodeWithTag("benchmark-card:another-version").performClick()
        compose.onNode(hasTestTag("benchmark-shared:another-version:LOGO") and
            hasAnyAncestor(hasTestTag("benchmark-leaderboard")), true).assertIsDisplayed()
        compose.onNodeWithText("版本：2026.11").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("benchmark-leaderboard").assertDoesNotExist()
    }

    @Test fun recreationRestoresAStableLeaderboardAndReturnTarget() {
        val restoration = StateRestorationTester(compose)
        show(restoration = restoration)
        open()
        compose.onNodeWithTag("benchmark-leaderboard-list").performScrollToIndex(8)
        val saved = compose.onNodeWithTag("benchmark-result:score-7", true).fetchSemanticsNode().boundsInRoot
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("main-pager").assertIsNotDisplayed()
        compose.runOnIdle { assertEquals(1f, transition.expansionProgress(), .001f) }
        assertEquals(saved, compose.onNodeWithTag("benchmark-result:score-7", true).fetchSemanticsNode().boundsInRoot)
        pressBack()
        compose.onNodeWithTag(cardTag).assertIsDisplayed()
    }

    private val cardTag get() = "benchmark-card:${board.id}"
    private fun open() = compose.onNodeWithTag(cardTag).performClick()
    private fun pixels() = compose.onRoot().captureToImage().toPixelMap()
    private fun sharedNode(role: BenchmarkIdentityRole) = compose.onNode(
        hasTestTag("benchmark-shared:${board.id}:$role") and hasAnyAncestor(hasTestTag("benchmark-leaderboard")), true,
    )
    private fun assertReturned() {
        compose.onNodeWithTag("benchmark-leaderboard").assertDoesNotExist()
        compose.onNodeWithTag(cardTag).assertIsDisplayed()
        compose.onNodeWithTag("nav-benchmarks").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, overviewDisposals)
            assertEquals(1, leaderboardDisposals)
        }
    }
    private fun startBack(progress: Float) {
        compose.runOnIdle { input.start() }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { input.progress(progress) }
        compose.mainClock.advanceTimeBy(64)
    }
    private fun show(dark: Boolean = true, largeRtl: Boolean = false, restoration: StateRestorationTester? = null) {
        val content: @Composable () -> Unit = {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeRtl) 2f else 1f),
                LocalLayoutDirection provides if (largeRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                ModelAtlasTheme(darkTheme = dark, dynamicColor = false) {
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    state.value.boards.firstOrNull()?.rankings?.take(3)?.forEach {
                        resultColors[it.modelId] = labChartColor(it.lab, trackColor)
                    }
                    val dispatcher = LocalNavigationEventDispatcherOwner.current!!.navigationEventDispatcher
                    DisposableEffect(dispatcher) {
                        dispatcher.addInput(input)
                        onDispose { dispatcher.removeInput(input) }
                    }
                    ModelAtlasScaffold(
                        modifier = if (largeRtl) Modifier.width(320.dp) else Modifier,
                        benchmarkContent = { padding, _, onOpen ->
                            DisposableEffect(Unit) { onDispose { overviewDisposals++ } }
                            BenchmarkScreen(state.value, onOpen, bottomOverlayPadding = padding)
                        },
                        leaderboardContent = { id, back ->
                            transition = checkNotNull(LocalBenchmarkSharedTransition.current)
                            DisposableEffect(id) { onDispose { leaderboardDisposals++ } }
                            BenchmarkLeaderboardScreen(state.value, id, back)
                        },
                    ) { padding, listState, _ ->
                        ModelListScreen(ModelListUiState(isLoading = false), bottomOverlayPadding = padding, listState = listState)
                    }
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        compose.onNodeWithTag("nav-benchmarks").performClick()
        // 先让顶层 Pager 停稳，再由用例冻结时钟；不能在横移期间点击来源卡片。
        compose.onNodeWithTag("benchmark-screen").assertIsDisplayed()
    }

    private fun changedBounds(before: PixelMap, after: PixelMap, tolerance: Int = 0): Rect? {
        var left = before.width
        var right = 0
        var top = before.height
        var bottom = 0
        for (y in 0 until before.height) for (x in 0 until before.width) {
            val a = before[x, y].toArgb()
            val b = after[x, y].toArgb()
            if ((0..24 step 8).any { shift -> kotlin.math.abs(((a ushr shift) and 255) - ((b ushr shift) and 255)) > tolerance }) {
                left = minOf(left, x); right = maxOf(right, x + 1)
                top = minOf(top, y); bottom = maxOf(bottom, y + 1)
            }
        }
        return if (right > left && bottom > top) Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()) else null
    }

    private fun configureDifferentRange() = board.copy(minScore = 0.0, maxScore = 100.0,
        rankings = board.rankings.mapIndexed { index, entry ->
            if (index < 3) entry else entry.copy(score = 1.0 / (index - 2), lab = "xai")
        }).also { state.value = state.value.copy(boards = listOf(it)) }

    private fun resultNode(modelId: String, role: BenchmarkResultRole, page: String = "benchmark-leaderboard") = compose.onNode(
        hasTestTag("benchmark-shared-result:${board.id}:$modelId:$role") and hasAnyAncestor(hasTestTag(page)), true,
    )
    private data class ResultGeometry(val fill: Rect, val track: Rect)
    private fun assertResultTextFits(modelId: String, page: String) {
        for (role in listOf(BenchmarkResultRole.NAME, BenchmarkResultRole.SCORE)) {
            val results = mutableListOf<TextLayoutResult>()
            resultNode(modelId, role, page).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            assertFalse("$page / $modelId / $role 的真实段落不能超过文字布局宽度", results.single().didOverflowWidth)
            assertFalse(results.single().didOverflowHeight)
        }
    }
    private fun resultGeometry(modelId: String, image: PixelMap, page: String) = ResultGeometry(
        coloredBounds(image, resultColors.getValue(modelId)),
        resultNode(modelId, BenchmarkResultRole.TRACK, page).fetchSemanticsNode().boundsInRoot,
    )
    private fun assertResultNear(source: ResultGeometry, target: ResultGeometry, image: PixelMap, progress: Float, modelId: String) {
        assertRectNear(lerp(source.fill, target.fill, progress), coloredBounds(image, resultColors.getValue(modelId)))
        val track = lerp(source.track, target.track, progress)
        val pixel = image[(track.right - 5).toInt(), track.center.y.toInt()]
        assertTrue("轨道也应连续移动／变高，不能跟随正文先消失：$track / $progress", colorClose(pixel, trackColor))
    }
    private fun assertRectNear(expected: Rect, actual: Rect) {
        assertEquals("$expected ≈ $actual", expected.left, actual.left, 4f)
        assertEquals("$expected ≈ $actual", expected.top, actual.top, 4f)
        assertEquals("$expected ≈ $actual", expected.right, actual.right, 4f)
        assertEquals("$expected ≈ $actual", expected.bottom, actual.bottom, 4f)
    }
    private fun colorClose(a: Color, b: Color): Boolean = kotlin.math.abs(a.red - b.red) < .012f &&
        kotlin.math.abs(a.green - b.green) < .012f && kotlin.math.abs(a.blue - b.blue) < .012f
    private fun coloredBounds(image: PixelMap, color: Color): Rect {
        var left = image.width
        var right = 0
        var top = image.height
        var bottom = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if (colorClose(image[x, y], color)) {
                left = minOf(left, x); right = maxOf(right, x + 1)
                top = minOf(top, y); bottom = maxOf(bottom, y + 1)
            }
        }
        assertTrue("共享成绩填充在中间帧也必须可见：$color", right > left && bottom > top)
        return Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }
}

class BenchmarkSharedTransitionReducedMotionTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale { override val scaleFactor = 0f })

    @Test fun disabledAnimationsSettleAndPredictiveCancellationDoesNotLoseThePage() {
        val input = ModelBackTestInput()
        val board = benchmarkPreviewBoard()
        val state = BenchmarkUiState(isLoading = false, boards = listOf(board))
        compose.setContent {
            val dispatcher = LocalNavigationEventDispatcherOwner.current!!.navigationEventDispatcher
            DisposableEffect(dispatcher) {
                dispatcher.addInput(input)
                onDispose { dispatcher.removeInput(input) }
            }
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold(
                    benchmarkContent = { padding, _, open -> BenchmarkScreen(state, open, bottomOverlayPadding = padding) },
                    leaderboardContent = { id, back -> BenchmarkLeaderboardScreen(state, id, back) },
                ) { _, _, _ -> }
            }
        }
        compose.onNodeWithTag("nav-benchmarks").performClick()
        compose.onNodeWithTag("benchmark-screen").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("benchmark-card:${board.id}").performClick()
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithTag("main-pager").assertIsNotDisplayed()
        compose.runOnIdle { input.start() }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { input.progress(.5f) }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { input.cancel() }
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithTag("benchmark-leaderboard").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithTag("benchmark-leaderboard").assertDoesNotExist()
        compose.onNodeWithTag("benchmark-card:${board.id}").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }
}
