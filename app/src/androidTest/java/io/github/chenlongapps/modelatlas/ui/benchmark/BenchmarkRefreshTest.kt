package io.github.chenlongapps.modelatlas.ui.benchmark

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListError
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListMessage
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BenchmarkRefreshTest {
    @get:Rule val compose = createComposeRule()
    private val ready = BenchmarkUiState(isLoading = false, boards = listOf(benchmarkPreviewBoard()))

    @Test fun pullRefreshWorksForContentEmptyAndReadErrorWithoutReplacingContentWithLoading() {
        val state = mutableStateOf(ready)
        var refreshes = 0
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            BenchmarkScreen(state.value, {}, onRefresh = {
                refreshes++
                state.value = state.value.copy(isRefreshing = true)
            })
        } }
        for (initial in listOf(ready, ready.copy(boards = emptyList()), ready.copy(hasError = true, boards = emptyList()))) {
            compose.runOnIdle { state.value = initial }
            compose.onNodeWithTag("benchmark-list").performTouchInput {
                swipeDown(startY = height * 0.2f, endY = height * 0.9f, durationMillis = 600)
            }
            compose.runOnIdle { assertTrue(state.value.isRefreshing) }
            compose.onNodeWithText("正在读取评测…").assertDoesNotExist()
            if (initial.boards.isNotEmpty()) compose.onNodeWithText("Terminal-Bench").assertIsDisplayed()
        }
        compose.runOnIdle { assertEquals(3, refreshes) }
    }

    @Test fun accessibleRefreshIsNamedAndRejectsLoadingOrRepeatedRefresh() {
        val state = mutableStateOf(BenchmarkUiState())
        var refreshes = 0
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            BenchmarkScreen(state.value, {}, onRefresh = {
                refreshes++
                state.value = state.value.copy(isRefreshing = true)
            })
        } }
        fun invokeRefresh(): Boolean {
            val action = compose.onNodeWithTag("benchmark-list").fetchSemanticsNode()
                .config[SemanticsActions.CustomActions].single { it.label == "刷新评测" }
            var accepted = false
            compose.runOnIdle { accepted = action.action() }
            return accepted
        }
        assertFalse(invokeRefresh())
        compose.onNodeWithTag("benchmark-list").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(0, refreshes); state.value = ready }
        assertTrue(invokeRefresh())
        assertFalse(invokeRefresh())
        compose.runOnIdle { assertEquals(1, refreshes) }
        compose.onNodeWithTag("benchmark-list").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test fun lightFeedbackKeepsContentSupportsRetryAndDismissAndTimesOutSuccess() = assertFeedback(false)

    @Test fun darkFeedbackKeepsContentSupportsRetryAndDismissAndTimesOutSuccess() = assertFeedback(true)

    private fun assertFeedback(dark: Boolean) {
        val state = mutableStateOf(ready)
        var retries = 0
        compose.setContent { ModelAtlasTheme(darkTheme = dark, dynamicColor = false) {
            BenchmarkScreen(state.value, {}, onRefresh = {
                retries++
                state.value = state.value.copy(isRefreshing = true, refreshError = null)
            }, onDismissNotice = { state.value = state.value.copy(message = null, refreshError = null) })
        } }
        compose.runOnIdle { state.value = ready.copy(refreshError = ModelListError.NETWORK) }
        compose.mainClock.advanceTimeBy(60_000)
        compose.onNodeWithText("无法连接模型目录，请检查网络后重试。").assertIsDisplayed()
        compose.onNodeWithText("Terminal-Bench").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithTag("catalog-notice").assertDoesNotExist()
        compose.onNodeWithText("正在读取评测…").assertDoesNotExist()
        compose.runOnIdle { state.value = ready.copy(refreshError = ModelListError.STORAGE) }
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("catalog-notice").assertDoesNotExist()
        for ((message, text) in listOf(
            ModelListMessage.UPDATED to "评测数据已更新",
            ModelListMessage.UP_TO_DATE to "已是最新数据",
        )) {
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { state.value = ready.copy(message = message) }
            compose.mainClock.advanceTimeBy(250)
            compose.onNodeWithText(text).assertIsDisplayed()
            compose.onNodeWithText("模型目录已更新").assertDoesNotExist()
            compose.onNodeWithText("关闭").assertDoesNotExist()
            compose.mainClock.advanceTimeBy(2_500)
            compose.onNodeWithText(text).assertIsDisplayed()
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithTag("catalog-notice").assertDoesNotExist()
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun refreshAndFeedbackDoNotResetReadingPositionOrLeaderboardNavigation() {
        val boards = List(12) { benchmarkPreviewBoard().copy(id = "board-$it", name = "评测 $it") }
        val initial = ready.copy(boards = boards)
        val state = mutableStateOf(initial)
        var opened: String? = null
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            BenchmarkScreen(state.value, { opened = it }, onDismissNotice = {
                state.value = state.value.copy(message = null, refreshError = null)
            })
        } }
        compose.onNodeWithTag("benchmark-list").performScrollToIndex(6)
        val card = compose.onNodeWithTag("benchmark-card:board-5")
        val before = card.fetchSemanticsNode().boundsInRoot.top
        for (next in listOf(initial.copy(isRefreshing = true), initial.copy(message = ModelListMessage.UPDATED),
            initial.copy(refreshError = ModelListError.NETWORK), initial)) {
            compose.runOnIdle { state.value = next }
            compose.waitForIdle()
            card.assertIsDisplayed()
            assertEquals(before, card.fetchSemanticsNode().boundsInRoot.top, 1f)
        }
        card.performClick()
        compose.runOnIdle { assertEquals("board-5", opened) }
    }

    @Test fun successUsesAccessibilityTimeoutAndOldTimeoutCannotDismissNewError() {
        val state = mutableStateOf(ready)
        var dismissals = 0
        val accessibility = object : AccessibilityManager {
            override fun calculateRecommendedTimeoutMillis(originalTimeoutMillis: Long, containsIcons: Boolean,
                containsText: Boolean, containsControls: Boolean): Long {
                assertEquals(3_000L, originalTimeoutMillis)
                assertTrue(containsIcons && containsText && !containsControls)
                return 12_000L
            }
        }
        compose.setContent { CompositionLocalProvider(LocalAccessibilityManager provides accessibility) {
            ModelAtlasTheme(dynamicColor = false) {
                BenchmarkScreen(state.value, {}, onDismissNotice = {
                    dismissals++
                    state.value = state.value.copy(message = null, refreshError = null)
                })
            }
        } }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.value = ready.copy(message = ModelListMessage.UPDATED) }
        compose.mainClock.advanceTimeBy(9_000)
        compose.onNodeWithText("评测数据已更新").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithTag("catalog-notice").assertDoesNotExist()
        compose.runOnIdle { state.value = ready.copy(message = ModelListMessage.UPDATED) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { state.value = ready.copy(refreshError = ModelListError.NETWORK) }
        compose.mainClock.advanceTimeBy(60_000)
        compose.onNodeWithText("无法连接模型目录，请检查网络后重试。").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, dismissals) }
    }
}
