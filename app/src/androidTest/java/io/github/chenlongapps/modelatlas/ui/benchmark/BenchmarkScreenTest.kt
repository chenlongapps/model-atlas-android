package io.github.chenlongapps.modelatlas.ui.benchmark

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import io.github.chenlongapps.modelatlas.ui.ModelAtlasScaffold
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListScreen
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListUiState
import io.github.chenlongapps.modelatlas.ui.modellist.ModelCard
import io.github.chenlongapps.modelatlas.ui.modellist.LabLogoTag
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.data.local.BenchmarkRankingRecord
import io.github.chenlongapps.modelatlas.data.local.entity.BenchmarkDefinitionEntity
import io.github.chenlongapps.modelatlas.data.mapper.toBenchmarkBoards
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import io.github.chenlongapps.modelatlas.testing.saveRegressionScreenshot
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BenchmarkScreenTest {
    @get:Rule val compose = createComposeRule()
    private val board = benchmarkPreviewBoard()
    private val ready = BenchmarkUiState(isLoading = false, boards = listOf(board))

    @Test fun overviewUsesPopularityOrderRatherThanAlphabeticalOrderAndOpensCorrectBoard() {
        val boards = listOf(
            BenchmarkDefinitionEntity("alpha", "Alpha", "1", "accuracy", popularity = 0.6),
            BenchmarkDefinitionEntity("zulu", "Zulu", "1", "accuracy", popularity = 0.98),
        ).map { BenchmarkRankingRecord(it, emptyList()) }.toBenchmarkBoards()
        var opened: String? = null
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            BenchmarkScreen(BenchmarkUiState(isLoading = false, boards = boards), onOpen = { opened = it })
        } }
        val popular = compose.onNodeWithTag("benchmark-card:${boards[0].id}")
        val other = compose.onNodeWithTag("benchmark-card:${boards[1].id}")
        assertEquals("zulu", boards.first().benchmarkId)
        assertTrue(popular.fetchSemanticsNode().boundsInRoot.top < other.fetchSemanticsNode().boundsInRoot.top)
        popular.performClick()
        compose.runOnIdle { assertEquals(boards.first().id, opened) }
    }

    @Test fun matchesModelCardSurfaceLogoInsetsAndPriceTypographyInBothThemes() {
        val dark = mutableStateOf(false)
        var pixelsPerDp = 0f
        val model = ModelSummary("deepseek/example", "模型卡", price = ModelPriceSummary(
            providerId = "example", providerName = "Provider", isOfficial = true, input = 1.0, output = 2.0,
        ))
        val wide = board.copy(id = "wide", benchmarkId = "automationbench-v1.0.6", name = "AutomationBench", rankings = emptyList())
        compose.setContent { ModelAtlasTheme(darkTheme = dark.value, dynamicColor = false) {
            pixelsPerDp = LocalDensity.current.density
            Column(Modifier.width(400.dp).background(MaterialTheme.colorScheme.surfaceContainerLowest),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ModelCard(model, false, {}, onOpenDetail = {})
                BenchmarkCard(board, {})
                BenchmarkCard(wide, {})
            }
        } }
        for (isDark in listOf(false, true)) {
            compose.runOnIdle { dark.value = isDark }
            val modelImage = compose.onNodeWithTag("model:${model.id}").captureToImage().toPixelMap()
            val benchmarkImage = compose.onNodeWithTag("benchmark-card:${board.id}").captureToImage().toPixelMap()
            // 顶边中点为同色 surface，无描边；圆角外侧与背景一致，无独立阴影。
            assertEquals(modelImage[modelImage.width / 2, 1], benchmarkImage[benchmarkImage.width / 2, 1])
            assertEquals(modelImage[1, 1], benchmarkImage[1, 1])
            val modelLogo = compose.onNodeWithTag(LabLogoTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val card = compose.onNodeWithTag("benchmark-card:${board.id}").fetchSemanticsNode().boundsInRoot
            val logo = compose.onNodeWithTag("benchmark-catalog-logo:${board.benchmarkId}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val logoImage = compose.onNodeWithTag("benchmark-catalog-logo-image:${board.benchmarkId}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            // 模型图形 44dp、总览底座 56dp，统一 6dp 内留白和 16dp 卡片内边距。
            // 使用实际密度，不能用已取整的 44dp 图形高度反推，否则会放大为 88dp 的 1px 误差。
            val dp = pixelsPerDp
            assertEquals(56f * dp, logo.height, 1f)
            assertEquals(56f * dp, logo.width, 1f)
            assertEquals(modelLogo.width, logoImage.width, 1f)
            assertEquals(modelLogo.height, logoImage.height, 1f)
            assertEquals(16f * dp, logo.left - card.left, 1f)
            assertEquals(16f * dp, logo.top - card.top, 1f)
            val first = compose.onNodeWithTag("benchmark-result:a", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(12f * dp, first.top - logo.bottom, 1f)
            val wordmark = compose.onNodeWithTag("benchmark-catalog-logo:${wide.benchmarkId}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(56f * dp, wordmark.height, 1f)
            assertEquals(88f * dp, wordmark.width, 1f)
            val priceLayouts = mutableListOf<TextLayoutResult>()
            val scoreLayouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("$1.00", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(priceLayouts) }
            compose.onNodeWithTag("benchmark-score:a", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(scoreLayouts) }
            val priceStyle = priceLayouts.single().layoutInput.style
            val scoreStyle = scoreLayouts.single().layoutInput.style
            assertEquals(priceStyle.fontSize, scoreStyle.fontSize)
            assertEquals(priceStyle.fontWeight, scoreStyle.fontWeight)
            assertEquals(priceStyle.color, scoreStyle.color)
        }
    }

    @Test fun cardHasNamedButtonActionAndNoRippleWhilePressed() {
        var opens = 0
        compose.setContent { ModelAtlasTheme(dynamicColor = false) { BenchmarkCard(board, { opens++ }) } }
        val card = compose.onNodeWithTag("benchmark-card:${board.id}")
        card.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertEquals("查看完整排行榜", card.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        val before = card.captureToImage().toPixelMap()
        compose.mainClock.autoAdvance = false
        card.performTouchInput { down(center) }
        try {
            compose.mainClock.advanceTimeBy(120)
            val pressed = card.captureToImage().toPixelMap()
            for (y in 0 until before.height step 4) for (x in 0 until before.width step 4) {
                assertEquals("Press must not tint card at $x,$y", before[x, y], pressed[x, y])
            }
            assertEquals(0, opens)
        } finally {
            card.performTouchInput { up() }
            compose.mainClock.autoAdvance = true
        }
        compose.runOnIdle { assertEquals(1, opens) }
    }

    @Test fun topThreeShowReferenceRangeAndInstitutionColorsInsteadOfRankColors() {
        compose.setContent { ModelAtlasTheme(dynamicColor = false) { BenchmarkScreen(ready, {}) } }
        compose.onNodeWithText("Terminal-Bench").assertIsDisplayed()
        compose.onNodeWithContentDescription("第 1 名，模型 A，分数 72.4%", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("图表范围 55% 至 75%；柱长按局部刻度绘制，不代表绝对比例", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("各模型最高成绩 · 配置可能不同 · 柱条使用局部刻度").assertIsDisplayed()
        for (text in listOf("Top 3", "查看完整排行榜", "版本：4.0.0", "编程", board.metric, "55%", "75%", "1", "2", "3")) {
            compose.onNodeWithText(text, useUnmergedTree = true).assertDoesNotExist()
        }
        // 名称与分数同一行，下方柱条占满内容宽度；颜色由机构而非名次决定。
        val bars = board.rankings.map { entry ->
            val name = compose.onNodeWithTag("benchmark-name:${entry.scoreId}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val score = compose.onNodeWithTag("benchmark-score:${entry.scoreId}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val bar = compose.onNodeWithTag("benchmark-bar:${entry.modelId}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(name.right < score.left)
            assertEquals(name.center.y, score.center.y, 1f)
            assertTrue(name.bottom < bar.top)
            assertTrue(score.bottom < bar.top)
            assertEquals(name.left, bar.left, 1f)
            assertEquals(score.right, bar.right, 1f)
            bar
        }
        bars.drop(1).forEach { assertEquals(bars.first().left, it.left, 1f); assertEquals(bars.first().right, it.right, 1f) }
        val colors = board.rankings.map { entry ->
            val image = compose.onNodeWithTag("benchmark-bar:${entry.modelId}", useUnmergedTree = true).captureToImage().toPixelMap()
            image[image.width / 10, image.height / 2]
        }
        assertNotEquals(colors[0], colors[1])
        assertNotEquals(colors[1], colors[2])
    }

    @Test fun referenceModelNamesStayOnOneLineAndPointsAreCompactWithoutChangingSpokenUnit() {
        val names = listOf("Gemini 4 Carbon", "Claude Opus 5.5", "GPT-6 Astra (Fast)")
        val reference = board.copy(name = "Artificial Analysis Intelligence Index", unit = "points",
            rankings = board.rankings.mapIndexed { index, entry -> entry.copy(modelName = names[index], score = listOf(60.0, 59.1, 57.0)[index]) })
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            Box(Modifier.width(400.dp)) { BenchmarkCard(reference, {}) }
        } }
        reference.rankings.forEach { entry ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("benchmark-name:${entry.scoreId}", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            assertFalse("${entry.modelName}: size=${layouts.single().size}, paragraphWidth=${layouts.single().multiParagraph.width}, constraints=${layouts.single().layoutInput.constraints}",
                layouts.single().didOverflowWidth)
            compose.onNodeWithText("${entry.score} pts", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("第 ${entry.rank} 名，${entry.modelName}，分数 ${entry.score} points", useUnmergedTree = true).assertIsDisplayed()
            val name = compose.onNodeWithTag("benchmark-name:${entry.scoreId}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val score = compose.onNodeWithTag("benchmark-score:${entry.scoreId}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val bar = compose.onNodeWithTag("benchmark-bar:${entry.modelId}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(name.center.y, score.center.y, 1f)
            assertEquals(name.left, bar.left, 1f)
            assertEquals(score.right, bar.right, 1f)
            assertTrue(name.bottom < bar.top)
        }
    }

    @Test fun sameNamedVersionsUseCompactTitleSuffixWhileUniqueNamesStayPlain() {
        val other = board.copy(id = "other-version", version = "4.0")
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            BenchmarkScreen(ready.copy(boards = listOf(board, other)), {})
        } }
        compose.onNodeWithText("Terminal-Bench · 4.0.0").assertIsDisplayed()
        compose.onNodeWithText("Terminal-Bench · 4.0").assertIsDisplayed()
        compose.onNodeWithText("Terminal-Bench").assertDoesNotExist()
    }

    @Test fun noNumericScoresKeepTheWholeCardClickableWithoutInventingBars() {
        var opens = 0
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            BenchmarkCard(board.copy(rankings = emptyList()), onClick = { opens++ })
        } }
        compose.onNodeWithText("暂无可排名的数值成绩").assertIsDisplayed()
        compose.onNodeWithTag("benchmark-bar:deepseek/a", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("benchmark-card:${board.id}").performClick()
        assertEquals(1, opens)
    }

    @Test fun overviewAppendsEffortAndOmitsBlankEffortAndConditions() {
        val changed = board.copy(rankings = board.rankings.mapIndexed { index, entry -> entry.copy(
            reasoningEffort = listOf("Max", null, "  ")[index], variant = "thinking", harness = "Agent X",
            dataset = "public", toolsEnabled = true,
        ) })
        compose.setContent { ModelAtlasTheme(dynamicColor = false) { BenchmarkCard(changed, {}) } }
        compose.onNodeWithText("模型 A · Max", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("模型 B", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("模型 C", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("第 1 名，模型 A，分数 72.4%，推理档位：Max", useUnmergedTree = true).assertIsDisplayed()
        for (text in listOf("推理档位：Max", "thinking", "Agent X", "public", "工具：启用", "来源未提供更多执行条件")) {
            compose.onNodeWithText(text, substring = true, useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test fun leaderboardOmitsMetricNoticesAndScaleWhileKeepingScores() {
        val state = mutableStateOf(ready)
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            BenchmarkLeaderboardScreen(state.value, board.id, {})
        } }
        for (higherIsBetter in listOf(true, false, null)) {
            compose.runOnIdle { state.value = ready.copy(boards = listOf(board.copy(higherIsBetter = higherIsBetter))) }
            compose.onNodeWithText("Terminal-Bench").assertIsDisplayed()
            compose.onNodeWithText("版本：4.0.0").assertIsDisplayed()
            compose.onNodeWithTag("benchmark-scale", useUnmergedTree = true).assertDoesNotExist()
            for (text in listOf("指标：${board.metric}", "各模型最高成绩 · 配置可能不同", "仅浏览成绩，不生成最高分排名", "55%", "75%", "局部刻度 · 非零起点")) {
                compose.onNodeWithText(text, useUnmergedTree = true).assertDoesNotExist()
            }
            compose.onNodeWithTag("benchmark-bar:deepseek/a", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("72.4%", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test fun leaderboardIntroductionIs64DpAndDescriptionOnlyOpensOnDemandInBothThemes() {
        val dark = mutableStateOf(false)
        val description = "这是一段仅在信息弹窗展示的完整评测说明。"
        val described = board.copy(description = description)
        var pixelsPerDp = 0f
        compose.setContent { ModelAtlasTheme(darkTheme = dark.value, dynamicColor = false) {
            pixelsPerDp = LocalDensity.current.density
            Box(Modifier.width(400.dp)) {
                BenchmarkLeaderboardScreen(ready.copy(boards = listOf(described)), described.id, {})
            }
        } }
        for (isDark in listOf(false, true)) {
            compose.runOnIdle { dark.value = isDark }
            compose.onNodeWithText(description).assertDoesNotExist()
            compose.onNodeWithText(board.name).assertIsDisplayed()
            compose.onNodeWithText("版本：4.0.0").assertIsDisplayed()
            compose.onNodeWithText("编程").assertIsDisplayed()
            val intro = compose.onNodeWithTag("benchmark-introduction").fetchSemanticsNode().boundsInRoot
            assertEquals(64f * pixelsPerDp, intro.height, 1f)
            val info = compose.onNodeWithContentDescription("查看评测说明")
            val bounds = info.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width >= 48f * pixelsPerDp - 1f)
            assertTrue(bounds.height >= 48f * pixelsPerDp - 1f)
            saveRegressionScreenshot("leaderboard-intro-${if (isDark) "dark" else "light"}")
            info.performClick()
            compose.onNodeWithTag("benchmark-description").assertIsDisplayed()
            compose.onNodeWithText(description).assertIsDisplayed()
            saveRegressionScreenshot("leaderboard-dialog-${if (isDark) "dark" else "light"}")
            compose.onNodeWithText("关闭").performClick()
            compose.onNodeWithText(description).assertDoesNotExist()
            compose.onNodeWithTag("benchmark-bar:deepseek/a", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test fun descriptionDialogRestoresAndSystemBackDismissesWithoutLeavingLeaderboard() {
        val described = board.copy(description = "完整说明。".repeat(200) + "说明末尾唯一标记")
        val restore = StateRestorationTester(compose)
        var backCount = 0
        restore.setContent { ModelAtlasTheme(dynamicColor = false) {
            BenchmarkLeaderboardScreen(ready.copy(boards = listOf(described)), described.id, { backCount++ })
        } }
        compose.onNodeWithTag("benchmark-leaderboard-list").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 24f) }
        val before = compose.onNodeWithTag("benchmark-result:a").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("查看评测说明").performClick()
        compose.onNodeWithTag("benchmark-description").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, Float.MAX_VALUE) }
        val range = compose.onNodeWithTag("benchmark-description").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("长说明必须实际滚到末尾", range.maxValue() > 0f && range.value() == range.maxValue())
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("benchmark-description").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(described.description!!.length, layouts.single().getLineEnd(layouts.single().lineCount - 1))
        saveRegressionScreenshot("leaderboard-long-description-end")
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("benchmark-description").assertIsDisplayed()
        compose.onNodeWithTag("benchmark-description").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))
        compose.onNodeWithText("关闭").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("benchmark-description").assertDoesNotExist()
        compose.onNodeWithTag("benchmark-leaderboard").assertIsDisplayed()
        assertEquals(0, backCount)
        assertEquals(before, compose.onNodeWithTag("benchmark-result:a").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, backCount)
    }

    @Test fun descriptionDoesNotLeakAcrossBoardsOrIntoLoadingErrorRemovedAndBlankStates() {
        val first = board.copy(description = "只属于榜单 A 的说明")
        val second = board.copy(id = "second", name = "榜单 B", description = "只属于榜单 B 的说明")
        val state = mutableStateOf(ready.copy(boards = listOf(first, second)))
        val selected = mutableStateOf(first.id)
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            BenchmarkLeaderboardScreen(state.value, selected.value, {})
        } }
        compose.onNodeWithContentDescription("查看评测说明").performClick()
        compose.onNodeWithText(first.description!!).assertIsDisplayed()
        compose.runOnIdle { selected.value = second.id }
        compose.onNodeWithTag("benchmark-description").assertDoesNotExist()
        compose.onNodeWithContentDescription("查看评测说明").performClick()
        compose.onNodeWithText(second.description!!).assertIsDisplayed()
        compose.onNodeWithText(first.description).assertDoesNotExist()
        for (unavailable in listOf(
            BenchmarkUiState(), BenchmarkUiState(isLoading = false, hasError = true),
            BenchmarkUiState(isLoading = false),
            ready.copy(boards = listOf(second.copy(description = " \n "))),
        )) {
            compose.runOnIdle { state.value = unavailable }
            compose.onNodeWithTag("benchmark-description").assertDoesNotExist()
            compose.onNodeWithText(second.description).assertDoesNotExist()
        }
    }

    @Test fun tappingOutsideDescriptionDismissesOnlyTheDialog() {
        val described = board.copy(description = "遮罩关闭不退出排行榜")
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            BenchmarkLeaderboardScreen(ready.copy(boards = listOf(described)), described.id, { fail("不能返回总览") })
        } }
        compose.onNodeWithContentDescription("查看评测说明").performClick()
        // Compose 的 root 触摸直达 View，不经过 Dialog.onTouchEvent；遮罩需系统级事件。
        // 语义树已出现不等于窗口已绘制，先同步独立窗口，避免事件落到背景信息按钮。
        compose.onNode(isDialog()).captureToImage()
        saveRegressionScreenshot("leaderboard-mask-before")
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val metrics = instrumentation.targetContext.resources.displayMetrics
        val time = android.os.SystemClock.uptimeMillis()
        for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            val event = android.view.MotionEvent.obtain(time, time, action,
                metrics.widthPixels / 2f, 96f * metrics.density, 0).apply { source = android.view.InputDevice.SOURCE_TOUCHSCREEN }
            try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("benchmark-description").fetchSemanticsNodes().isEmpty() }
        saveRegressionScreenshot("leaderboard-mask-after")
        compose.onNodeWithTag("benchmark-description").assertDoesNotExist()
        compose.onNodeWithTag("benchmark-leaderboard").assertIsDisplayed()
    }

    @Test fun missingOrBlankDescriptionDoesNotOfferAnEmptyInfoAction() {
        val state = mutableStateOf(ready)
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            BenchmarkLeaderboardScreen(state.value, board.id, {})
        } }
        for (description in listOf(null, "", "  \n ")) {
            compose.runOnIdle { state.value = ready.copy(boards = listOf(board.copy(description = description))) }
            compose.onNodeWithContentDescription("查看评测说明").assertDoesNotExist()
            compose.onNodeWithTag("benchmark-description").assertDoesNotExist()
            compose.onNodeWithText("版本：4.0.0").assertIsDisplayed()
        }
    }

    @Test fun narrowDoubleFontRtlIntroductionAndLongDescriptionRemainComplete() {
        val long = board.copy(benchmarkId = "automationbench-v1.0.6", name = "A benchmark with a complete long name",
            version = "2026.11.10-preview", description = "完整说明支持窄屏、大字号与滚动。".repeat(100))
        var pixelsPerDp = 0f
        compose.setContent {
            val density = LocalDensity.current
            pixelsPerDp = density.density
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModelAtlasTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.width(320.dp)) {
                        BenchmarkLeaderboardScreen(ready.copy(boards = listOf(long)), long.id, {})
                    }
                }
            }
        }
        val intro = compose.onNodeWithTag("benchmark-introduction").fetchSemanticsNode().boundsInRoot
        assertTrue(intro.height > 64f * pixelsPerDp)
        saveRegressionScreenshot("leaderboard-narrow-double-font-rtl")
        for (text in listOf(long.name, "版本：${long.version}", "编程")) {
            compose.onNodeWithText(text).assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse(layout.didOverflowHeight)
            assertFalse("$text 横向溢出，size=${layout.size}, lines=${layout.lineCount}", layout.didOverflowWidth)
            assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
        }
        compose.onNodeWithText(long.description!!).assertDoesNotExist()
        compose.onNodeWithContentDescription("查看评测说明").performClick()
        compose.onNodeWithTag("benchmark-description").assertIsDisplayed()
        compose.onNodeWithTag("benchmark-description").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("benchmark-description").assertDoesNotExist()
    }

    @Test fun loadingErrorEmptyAndRemovedHaveDistinctRecoverableStates() {
        val state = mutableStateOf(BenchmarkUiState())
        var retries = 0
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            BenchmarkLeaderboardScreen(state.value, board.id, {}, onRetry = { retries++ })
        } }
        compose.onNodeWithText("正在读取评测…").assertIsDisplayed()
        compose.runOnIdle { state.value = BenchmarkUiState(isLoading = false, hasError = true) }
        compose.onNodeWithText("无法读取本地评测").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        assertEquals(1, retries)
        compose.runOnIdle { state.value = BenchmarkUiState(isLoading = false) }
        compose.onNodeWithText("该评测或版本已不在当前目录中。").assertIsDisplayed()
    }

    @Test fun narrowDarkRtlDoubleFontPreservesLongNamesAndScores() {
        val long = board.copy(name = "A benchmark with a complete long name", rankings = listOf(board.rankings.first().copy(
            modelName = "一个需要完整换行且不可省略的模型名称", reasoningEffort = "Max")))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModelAtlasTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.width(320.dp)) { BenchmarkCard(long, {}) }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(long.name, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse(layout.didOverflowHeight)
        assertEquals(long.name.length, layout.getLineEnd(layout.lineCount - 1))
        assertFalse(layout.isLineEllipsized(layout.lineCount - 1))
        compose.onNodeWithContentDescription("第 1 名，一个需要完整换行且不可省略的模型名称，分数 72.4%，推理档位：Max", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("一个需要完整换行且不可省略的模型名称 · Max", useUnmergedTree = true).assertIsDisplayed()
        for (tag in listOf("benchmark-name:a", "benchmark-score:a")) {
            val values = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(values) }
            val result = values.single()
            assertFalse(result.didOverflowHeight)
            repeat(result.lineCount) { line ->
                assertFalse(result.isLineEllipsized(line))
                assertTrue(result.getLineRight(line) <= result.size.width + 1f)
                assertTrue(result.getLineLeft(line) >= -1f)
            }
        }
        val name = compose.onNodeWithTag("benchmark-name:a", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val score = compose.onNodeWithTag("benchmark-score:a", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bar = compose.onNodeWithTag("benchmark-bar:deepseek/a", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(name.bottom < score.top)
        assertTrue(score.bottom < bar.top)
    }

    @Test fun narrowLeaderboardDoubleFontRtlPreservesCompleteNameEffortAndScore() {
        val entry = board.rankings.first().copy(modelName = "一个需要完整换行且不可省略的模型名称", reasoningEffort = "Max")
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModelAtlasTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.width(320.dp)) {
                        BenchmarkResultRow(entry, board.unit, benchmarkChartRange(listOf(entry.score!!)))
                    }
                }
            }
        }
        compose.onNodeWithText("${entry.modelName} · Max", useUnmergedTree = true).assertIsDisplayed()
        for (tag in listOf("benchmark-name:a", "benchmark-score:a")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse(layout.didOverflowHeight)
            assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1))
            repeat(layout.lineCount) { line ->
                assertFalse(layout.isLineEllipsized(line))
                assertTrue(layout.getLineLeft(line) >= -1f)
                assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
            }
        }
        val name = compose.onNodeWithTag("benchmark-name:a", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val score = compose.onNodeWithTag("benchmark-score:a", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bar = compose.onNodeWithTag("benchmark-bar:${entry.modelId}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(name.bottom < score.top)
        assertTrue(score.bottom < bar.top)
    }

    @Test fun narrowLargeFontRtlNavigationLabelsRemainCompleteAndClickable() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModelAtlasTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.width(320.dp)) {
                        ModelAtlasScaffold { padding, listState, _ ->
                            ModelListScreen(ModelListUiState(isLoading = false), bottomOverlayPadding = padding, listState = listState)
                        }
                    }
                }
            }
        }
        for ((tag, label) in listOf("nav-models" to "模型", "nav-benchmarks" to "评测", "nav-compare" to "对比", "nav-settings" to "设置", "nav-debug" to "调试")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse(layout.didOverflowHeight)
            repeat(layout.lineCount) { line ->
                assertTrue(layout.getLineLeft(line) >= -1f)
                assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
                assertFalse(layout.isLineEllipsized(line))
            }
            assertEquals(label.length, layout.getLineEnd(layout.lineCount - 1))
            compose.onNodeWithTag(tag).performClick().assertIsSelected()
        }
    }

    @Test fun fullListPositionSurvivesLoadingErrorAndStateRestoration() {
        val long = board.copy(rankings = List(18) { index -> board.rankings.first().copy(
            scoreId = "score-$index", modelId = "lab/$index", modelName = "模型 $index", score = 90.0 - index, rank = index + 1,
        ) })
        val state = mutableStateOf(ready.copy(boards = listOf(long)))
        val restore = StateRestorationTester(compose)
        restore.setContent { ModelAtlasTheme(dynamicColor = false) { BenchmarkLeaderboardScreen(state.value, long.id, {}) } }
        val target = "benchmark-result:score-10"
        compose.onNodeWithTag("benchmark-leaderboard-list").performScrollToNode(hasTestTag(target))
        val before = compose.onNodeWithTag(target).fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { state.value = BenchmarkUiState() }
        restore.emulateSavedInstanceStateRestore()
        compose.runOnIdle { state.value = ready.copy(boards = listOf(long)) }
        assertEquals(before, compose.onNodeWithTag(target).fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.runOnIdle { state.value = BenchmarkUiState(isLoading = false, hasError = true) }
        compose.runOnIdle { state.value = ready.copy(boards = listOf(long)) }
        assertEquals(before, compose.onNodeWithTag(target).fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun fullRankingButtonAndSystemBackRestoreOverviewAndSavedDestination() {
        val restore = StateRestorationTester(compose)
        restore.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold(
                    benchmarkContent = { padding, _, onOpen -> BenchmarkScreen(ready, onOpen, bottomOverlayPadding = padding) },
                    leaderboardContent = { id, onBack -> BenchmarkLeaderboardScreen(ready, id, onBack) },
                ) { padding, listState, _ -> ModelListScreen(ModelListUiState(isLoading = false), bottomOverlayPadding = padding, listState = listState) }
            }
        }
        compose.onNodeWithTag("nav-benchmarks").performClick().assertIsSelected()
        compose.onNodeWithTag("benchmark-card:${board.id}").performClick()
        compose.onNodeWithTag("benchmark-leaderboard").assertIsDisplayed()
        compose.onNodeWithTag("main-pager").assertIsNotDisplayed()
        compose.onNodeWithTag("glass-navigation").assertIsNotDisplayed()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("benchmark-leaderboard").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("nav-benchmarks").assertIsSelected()
        compose.onNodeWithTag("benchmark-card:${board.id}").performClick()
        pressBack()
        compose.onNodeWithTag("benchmark-screen").assertIsDisplayed()
        compose.onNodeWithTag("nav-benchmarks").assertIsSelected()
    }

    @Test fun overviewScrollRestoresAfterLeaderboardAndTabSwitch() {
        val boards = List(12) { board.copy(id = "board-$it", name = "评测 $it") }
        val state = ready.copy(boards = boards)
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            ModelAtlasScaffold(touchExplorationEnabled = true,
                benchmarkContent = { padding, _, onOpen -> BenchmarkScreen(state, onOpen, bottomOverlayPadding = padding) },
                leaderboardContent = { id, onBack -> BenchmarkLeaderboardScreen(state, id, onBack) },
            ) { padding, listState, _ -> ModelListScreen(ModelListUiState(isLoading = false), bottomOverlayPadding = padding, listState = listState) }
        } }
        compose.onNodeWithTag("nav-benchmarks").performClick()
        compose.onNodeWithTag("benchmark-list").performScrollToIndex(6)
        val tag = "benchmark-card:board-5"
        val before = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag(tag).performClick()
        pressBack()
        assertEquals(before, compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("nav-benchmarks").performClick()
        assertEquals(before, compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top, 1f)
    }
}
