package io.github.chenlongapps.modelatlas.ui.benchmark

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import io.github.chenlongapps.modelatlas.data.model.BenchmarkBoard
import io.github.chenlongapps.modelatlas.data.repository.BenchmarkRepository
import io.github.chenlongapps.modelatlas.ui.ModelAtlasScaffold
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListScreen
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListUiState
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 使用真实 Route / ViewModel，刻意暂停每次订阅的首个结果，以检查恢复期间而非仅最终画面。 */
class BenchmarkReentryTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private val reads = AtomicInteger()
    private val active = AtomicInteger()
    private val pending = ConcurrentLinkedQueue<CompletableDeferred<List<BenchmarkBoard>>>()
    private val boards = List(12) { benchmarkPreviewBoard().copy(id = "board-$it", name = "评测 $it") }
    private lateinit var lifecycle: LifecycleRegistry
    private lateinit var viewModel: BenchmarkViewModel

    @After fun close() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
    }

    @Test fun swipingBackFromBothSidesKeepsContentAndPositionDuringLocalRead() {
        show()
        compose.onNodeWithTag("main-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("nav-benchmarks").assertIsSelected()
        compose.onNodeWithText("正在读取评测…").assertIsDisplayed()
        nextRead().complete(boards)
        compose.waitForIdle()
        val before = scrollToSavedCard()

        compose.onNodeWithTag("main-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("nav-compare").assertIsSelected()
        assertStopped()
        compose.onNodeWithTag("main-pager").performTouchInput { swipeRight() }
        val fromRight = nextRead()
        assertSavedCard(before)
        fromRight.complete(boards)
        compose.waitForIdle()

        compose.onNodeWithTag("main-pager").performTouchInput { swipeRight() }
        compose.onNodeWithTag("nav-models").assertIsSelected()
        assertStopped()
        compose.onNodeWithTag("main-pager").performTouchInput { swipeLeft() }
        val fromLeft = nextRead()
        assertSavedCard(before)
        fromLeft.complete(boards)
        compose.waitForIdle()
        assertSavedCard(before)
        assertEquals(3, reads.get())
    }

    @Test fun bottomNavigationAndRapidReentryKeepContentAndCancelPendingReads() {
        enterReady()
        val before = scrollToSavedCard()
        repeat(3) {
            compose.onNodeWithTag("nav-settings").performClick()
            assertStopped()
            compose.onNodeWithTag("nav-benchmarks").performClick()
            val read = nextRead()
            assertSavedCard(before)
            assertEquals(1, active.get())
            // 前两次不交付结果就离开；取消的读取不能清空已成功显示的榜单。
            if (it == 2) read.complete(boards)
        }
        compose.waitForIdle()
        assertSavedCard(before)
        assertEquals(4, reads.get())
    }

    @Test fun lifecycleResumeKeepsContentWhileReadingAndAppliesNewLocalResults() {
        enterReady()
        compose.runOnIdle { lifecycle.currentState = Lifecycle.State.CREATED }
        assertStopped()
        compose.runOnIdle { lifecycle.currentState = Lifecycle.State.RESUMED }
        val read = nextRead()
        compose.onNodeWithText("评测 0").assertIsDisplayed()
        compose.onNodeWithText("正在读取评测…").assertDoesNotExist()
        read.complete(boards.mapIndexed { index, board -> if (index == 0) board.copy(name = "更新后的评测") else board })
        compose.waitForIdle()
        compose.onNodeWithText("更新后的评测").assertIsDisplayed()
        compose.onNodeWithText("评测 0").assertDoesNotExist()
        assertEquals(2, reads.get())
    }

    @Test fun leaderboardButtonAndSystemBackKeepTheSameSubscriptionAndOverviewPosition() {
        enterReady()
        val before = scrollToSavedCard()
        repeat(2) { index ->
            compose.onNodeWithTag(savedCard).performClick()
            compose.onNodeWithTag("benchmark-leaderboard").assertIsDisplayed()
            assertEquals(1, active.get())
            assertEquals(1, reads.get())
            if (index == 0) compose.onNodeWithContentDescription("返回").performClick() else pressBack()
            assertSavedCard(before)
            assertEquals(1, reads.get())
        }
    }

    private fun show() {
        lateinit var owner: LifecycleOwner
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            owner = object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry(this)
            }
            lifecycle = owner.lifecycle
            lifecycle.currentState = Lifecycle.State.RESUMED
            viewModel = BenchmarkViewModel(object : BenchmarkRepository {
                override fun observeBoards() = flow {
                    val read = CompletableDeferred<List<BenchmarkBoard>>()
                    reads.incrementAndGet()
                    active.incrementAndGet()
                    pending.add(read)
                    try {
                        emit(read.await())
                        awaitCancellation()
                    } finally { active.decrementAndGet() }
                }
            })
            store.put("benchmark", viewModel)
        }
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold(
                    touchExplorationEnabled = true,
                    benchmarkContent = { padding, visible, onOpen ->
                        CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                            BenchmarkRoute(padding, visible, onOpen, viewModel)
                        }
                    },
                    leaderboardContent = { id, onBack -> BenchmarkLeaderboardRoute(id, onBack, viewModel) },
                ) { padding, listState, _ ->
                    ModelListScreen(ModelListUiState(isLoading = false), bottomOverlayPadding = padding, listState = listState)
                }
            }
        }
        compose.runOnIdle { assertEquals(0, reads.get()) }
    }

    private fun enterReady() {
        show()
        compose.onNodeWithTag("nav-benchmarks").performClick()
        nextRead().complete(boards)
        compose.waitForIdle()
    }

    private fun nextRead(): CompletableDeferred<List<BenchmarkBoard>> {
        compose.waitUntil(5_000) { pending.isNotEmpty() }
        return pending.remove()
    }

    private fun assertStopped() {
        compose.waitUntil(5_000) { active.get() == 0 }
    }

    private val savedCard = "benchmark-card:board-5"

    private fun scrollToSavedCard(): Float {
        compose.onNodeWithTag("benchmark-list").performScrollToIndex(6)
        return compose.onNodeWithTag(savedCard).fetchSemanticsNode().boundsInRoot.top
    }

    private fun assertSavedCard(top: Float) {
        compose.onNodeWithTag("nav-benchmarks").assertIsSelected()
        compose.onNodeWithText("正在读取评测…").assertDoesNotExist()
        compose.onNodeWithTag(savedCard).assertIsDisplayed()
        assertEquals(top, compose.onNodeWithTag(savedCard).fetchSemanticsNode().boundsInRoot.top, 1f)
    }
}
