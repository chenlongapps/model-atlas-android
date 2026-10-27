package io.github.chenlongapps.modelatlas.ui.debug

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.chenlongapps.modelatlas.data.local.CatalogDatabase
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.ui.ModelAtlasScaffold
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugDatabaseScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database = Room.inMemoryDatabaseBuilder<CatalogDatabase>(context).setDriver(AndroidSQLiteDriver()).build()
    private val vm = DebugDatabaseViewModel(DebugDatabaseStore(database, context, Dispatchers.IO))
    private val viewModels = ViewModelStore().apply { put("database", vm) }
    private val info = DebugDeviceInfo("1-debug", 1, "debug", true, "Google", "Test", "17", 37)
    @After fun close() { viewModels.clear(); database.close() }

    @Test fun fullPageCrudAndBothBackPaths() {
        show()
        openDatabase()
        compose.onNodeWithTag("main-pager").assertIsNotDisplayed()
        compose.onNodeWithTag("glass-navigation").assertIsNotDisplayed()
        compose.onNodeWithTag("database-table-favorites").performClick()
        await { it.destination == DatabaseDestination.TABLE && !it.loading }
        compose.onNodeWithTag("database-add").performClick()
        field("modelId", "lab/example")
        field("name", "Example")
        field("favoritedAtEpochMillis", "123")
        compose.onNodeWithTag("debug-database-content").performScrollToNode(androidx.compose.ui.test.hasTestTag("database-save"))
        compose.onNodeWithTag("database-save").performClick()
        await { it.destination == DatabaseDestination.DETAIL && it.row != null && !it.loading }
        assertEquals("Example", vm.state.value.row!!.values["name"])
        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithTag("debug-database-content").performScrollToNode(androidx.compose.ui.test.hasTestTag("database-field-name"))
        compose.onNodeWithTag("database-field-name").performTextClearance()
        compose.onNodeWithTag("database-field-name").performTextInput("Changed")
        compose.onNodeWithTag("debug-database-content").performScrollToNode(androidx.compose.ui.test.hasTestTag("database-save"))
        compose.onNodeWithTag("database-save").performClick()
        await { it.destination == DatabaseDestination.DETAIL && it.row?.values?.get("name") == "Changed" && !it.loading }
        compose.onNodeWithTag("debug-database-content").performScrollToNode(hasText("删除"))
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithText("删除收藏是本地用户数据操作，无法撤销。").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        assertNotNull(vm.state.value.row)
        compose.onNodeWithText("删除").performClick()
        // 对话框和背景各有一个删除按钮，使用对话框中的最后一个节点。
        compose.onAllNodesWithText("删除")[1].performClick()
        await { it.destination == DatabaseDestination.TABLE && !it.loading }
        assertEquals(0L, vm.state.value.page!!.total)
        pressBack()
        await { it.destination == DatabaseDestination.OVERVIEW && !it.loading }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("debug-screen").assertIsDisplayed()
        compose.onNodeWithTag("glass-navigation").assertIsDisplayed()
    }

    @Test fun dirtyEditorPromptsBeforeSystemBack() {
        show()
        openDatabase()
        compose.onNodeWithTag("database-table-favorites").performClick()
        await { it.destination == DatabaseDestination.TABLE && !it.loading }
        compose.onNodeWithTag("database-add").performClick()
        field("modelId", "new")
        pressBack()
        compose.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("database-field-modelId").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("放弃修改").performClick()
        await { it.destination == DatabaseDestination.TABLE && !it.loading }
    }

    @Test fun invalidValueShowsFieldErrorWithoutCreatingRecord() {
        show()
        openDatabase()
        compose.onNodeWithTag("database-table-favorites").performClick()
        await { it.destination == DatabaseDestination.TABLE && !it.loading }
        compose.onNodeWithTag("database-add").performClick()
        field("modelId", "invalid-time")
        compose.onNodeWithTag("debug-database-content").performScrollToNode(androidx.compose.ui.test.hasTestTag("database-save"))
        compose.onNodeWithTag("database-save").performClick()
        await { it.fieldError != null && !it.saving }
        assertEquals("favoritedAtEpochMillis", vm.state.value.fieldError?.field)
        compose.onNodeWithText("favoritedAtEpochMillis: 请输入有效的 64 位整数").assertIsDisplayed()
        assertEquals(DatabaseDestination.EDIT, vm.state.value.destination)
    }

    @Test fun databaseSubpageAndDraftSurviveStateRestoration() {
        val restoration = StateRestorationTester(compose)
        show(restoration)
        openDatabase()
        compose.onNodeWithTag("database-table-favorites").performClick()
        await { it.destination == DatabaseDestination.TABLE && !it.loading }
        compose.onNodeWithTag("database-add").performClick()
        field("modelId", "draft-id")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("debug-database-screen").assertIsDisplayed()
        compose.onNodeWithTag("main-pager").assertIsNotDisplayed()
        assertEquals("draft-id", vm.state.value.draft["modelId"])
        pressBack()
        compose.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
    }

    @Test fun sensitiveFieldsAreHiddenUntilExplicitlyRevealed() {
        val table = DatabaseTable.OFFERINGS
        val values = table.columns.associate { column -> column.name to if (column.required) "test" else null } +
            ("providerOverride" to """{"headers":{"token":"private-test-value"}}""")
        val state = DatabaseUiState(destination = DatabaseDestination.DETAIL, table = table, row = DatabaseRow(table, values))
        compose.setContent { ModelAtlasTheme(dynamicColor = false) { DebugDatabaseScreen(state, noActions()) } }
        compose.onNodeWithTag("debug-database-content").performScrollToNode(hasText("providerOverride"))
        compose.onNodeWithText("private-test-value", substring = true).assertDoesNotExist()
        compose.onNodeWithText("显示敏感内容").performClick()
        compose.onNodeWithText("private-test-value", substring = true).assertIsDisplayed()
        compose.onNodeWithText("隐藏敏感内容").performClick()
        compose.onNodeWithText("private-test-value", substring = true).assertDoesNotExist()
    }

    @Test fun overviewSupportsDarkNarrowLargeFontAndRtl() {
        val state = DatabaseUiState(overview = DatabaseOverview(1, DatabaseTable.entries.associateWith { 1L }, 100, 20, 30))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModelAtlasTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.width(320.dp)) { DebugDatabaseScreen(state, noActions()) }
                }
            }
        }
        compose.onNodeWithTag("debug-database-content").performScrollToNode(hasText("favorites", substring = true))
        compose.onNodeWithText("favorites", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").assertIsDisplayed()
    }

    @Test fun saveFailureRemainsVisibleNextToSaveAction() {
        val values = mapOf("modelId" to "id", "name" to "name", "lab" to null, "favoritedAtEpochMillis" to "100")
        val state = DatabaseUiState(destination = DatabaseDestination.EDIT, table = DatabaseTable.FAVORITES,
            draft = values, initialDraft = values, creating = true, error = R.string.debug_database_duplicate)
        compose.setContent { ModelAtlasTheme(dynamicColor = false) { DebugDatabaseScreen(state, noActions()) } }
        compose.onNodeWithTag("debug-database-content").performScrollToNode(androidx.compose.ui.test.hasTestTag("database-save"))
        compose.onNodeWithText("主键已存在，请使用其他主键。").assertIsDisplayed()
    }

    @Test fun movingToNextPageStartsAtItsFirstRow() {
        fun page(offset: Int) = DatabasePage(
            rows = (offset until offset + 50).map { DatabaseRowSummary(mapOf("modelId" to "lab/$it"), "记录 $it") },
            total = 100, offset = offset,
        )
        val state = mutableStateOf(DatabaseUiState(destination = DatabaseDestination.TABLE, table = DatabaseTable.FAVORITES, page = page(0)))
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                DebugDatabaseScreen(state.value, noActions().copy(page = { state.value = state.value.copy(page = page(it)) }))
            }
        }
        compose.onNodeWithTag("debug-database-content").performScrollToNode(hasText("下一页"))
        compose.onNodeWithText("下一页").performClick()
        compose.onNodeWithText("记录 50").assertIsDisplayed()
    }

    private fun show(restoration: StateRestorationTester? = null) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold(
                    debugContent = { padding, _, open -> DebugScreen(DebugUiState(), info, {}, {}, onOpenDatabase = open, bottomOverlayPadding = padding) },
                    databaseContent = { close ->
                        val state by vm.state.collectAsStateWithLifecycle()
                        LaunchedEffect(vm) { if (vm.state.value.overview == null) vm.loadOverview() }
                        val back = { if (vm.back()) close() }
                        NavigationBackHandler(rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
                            isBackEnabled = true, onBackCompleted = back)
                        DebugDatabaseScreen(state, DatabaseActions(
                            back, vm::openTable, vm::search, { vm.loadPage(it) }, vm::openRow, vm::create,
                            vm::edit, vm::change, { vm.save {} }, vm::requestDelete, vm::dismissDelete,
                            { vm.delete {} }, vm::dismissDiscard, vm::discard, vm::retry,
                        ))
                    },
                ) { _, _, _ -> }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    }

    private fun openDatabase() {
        compose.onNodeWithTag("nav-debug").performClick()
        compose.onNodeWithTag("debug-content").performScrollToNode(androidx.compose.ui.test.hasTestTag("debug-database-entry"))
        compose.onNodeWithTag("debug-database-entry").performClick()
        await { it.overview != null && !it.loading }
    }

    private fun field(name: String, value: String) {
        compose.onNodeWithTag("debug-database-content").performScrollToNode(androidx.compose.ui.test.hasTestTag("database-field-$name"))
        compose.onNodeWithTag("database-field-$name").performTextInput(value)
    }

    private fun await(predicate: (DatabaseUiState) -> Boolean) {
        compose.waitUntil(timeoutMillis = 10_000) { predicate(vm.state.value) }
    }

    private fun noActions() = DatabaseActions({}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {})
}
