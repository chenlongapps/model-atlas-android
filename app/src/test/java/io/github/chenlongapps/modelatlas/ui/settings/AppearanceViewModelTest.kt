package io.github.chenlongapps.modelatlas.ui.settings

import androidx.lifecycle.ViewModelStore
import io.github.chenlongapps.modelatlas.data.model.AppearanceSettings
import io.github.chenlongapps.modelatlas.data.model.ThemeColorSource
import io.github.chenlongapps.modelatlas.data.model.ThemeMode
import io.github.chenlongapps.modelatlas.data.model.ThemePreset
import io.github.chenlongapps.modelatlas.data.repository.SettingsRepository
import io.github.chenlongapps.modelatlas.testing.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val store = ViewModelStore()
    private val repository = FakeSettings()
    private fun viewModel() = AppearanceViewModel(repository).also { store.put("appearance", it) }
    @After fun close() = store.clear()

    @Test fun initialReadRestoresBeforeStartupCompletes() = runTest {
        repository.settings.value = AppearanceSettings(ThemeMode.Dark, ThemeColorSource.Preset, ThemePreset.Teal, 123f)
        val vm = viewModel()
        assertFalse(vm.uiState.value.startupComplete)
        assertFalse(vm.uiState.value.canEdit)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.startupComplete)
        assertTrue(vm.uiState.value.canEdit)
        assertEquals(repository.settings.value, vm.uiState.value.appearance)
        assertEquals(0, repository.writes)
    }

    @Test fun readFailureUsesDefaultsAndBlocksAllWritesUntilRetrySucceeds() = runTest {
        repository.readFailure = true
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasReadError)
        assertTrue(vm.uiState.value.startupComplete)
        assertFalse(vm.uiState.value.canEdit)
        vm.setMode(ThemeMode.Dark)
        vm.setHue(20f)
        vm.selectPreset(ThemePreset.Rose)
        vm.flushPending()
        advanceUntilIdle()
        assertEquals(0, repository.writes)
        assertEquals(AppearanceSettings(), vm.uiState.value.appearance)
        repository.readFailure = false
        repository.settings.value = AppearanceSettings(mode = ThemeMode.Dark)
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canEdit)
        assertEquals(ThemeMode.Dark, vm.uiState.value.appearance.mode)
    }

    @Test fun hungInitialReadReleasesStartupAt1500msAndRequiresExplicitRetry() = runTest {
        repository.hangRead = true
        val vm = viewModel()
        runCurrent()
        advanceTimeBy(1_499)
        assertFalse(vm.uiState.value.startupComplete)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(vm.uiState.value.startupComplete)
        assertTrue(vm.uiState.value.hasReadError)
        repository.hangRead = false
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canEdit)
    }

    @Test fun huePreviewsImmediatelyButOnlyLatestPauseIsSaved() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.setHue(40f)
        assertEquals(40f, vm.uiState.value.appearance.customHue)
        advanceTimeBy(200)
        vm.setHue(80f)
        advanceTimeBy(299)
        runCurrent()
        assertEquals(0, repository.writes)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, repository.writes)
        assertEquals(80f, repository.settings.value.customHue)
    }

    @Test fun releaseFlushesAndModeChangeDoesNotLosePendingHue() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.setHue(130f)
        vm.setMode(ThemeMode.Light)
        vm.flushPending()
        runCurrent()
        assertEquals(ThemeMode.Light, repository.settings.value.mode)
        assertEquals(130f, repository.settings.value.customHue)
        assertEquals(1, repository.writes)
    }

    @Test fun switchingSystemOrPresetRetainsCustomHistoryAndMode() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.restoreCustomColor()
        assertEquals(ThemeColorSource.System, vm.uiState.value.appearance.colorSource)
        vm.setHue(333f)
        vm.setMode(ThemeMode.Dark)
        advanceUntilIdle()
        vm.selectPreset(ThemePreset.Sand)
        advanceUntilIdle()
        vm.useSystemColors()
        advanceUntilIdle()
        assertEquals(333f, repository.settings.value.customHue)
        vm.restoreCustomColor()
        advanceUntilIdle()
        assertEquals(ThemeColorSource.Custom, repository.settings.value.colorSource)
        assertEquals(ThemeMode.Dark, repository.settings.value.mode)
        assertEquals(333f, repository.settings.value.customHue)
    }

    @Test fun saveFailureRollsBackToLastCommitAndCanBeRetried() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.setMode(ThemeMode.Dark)
        advanceUntilIdle()
        repository.writeFailure = true
        vm.setHue(80f)
        assertEquals(80f, vm.uiState.value.appearance.customHue)
        vm.flushPending()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasWriteError)
        assertEquals(AppearanceSettings(mode = ThemeMode.Dark), vm.uiState.value.appearance)
        repository.writeFailure = false
        vm.setHue(90f)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasWriteError)
        assertEquals(90f, repository.settings.value.customHue)
    }

    @Test fun newPreviewDoesNotCancelInFlightWriteOrGetReplacedByItsResult() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        vm.setHue(10f)
        vm.flushPending()
        runCurrent()
        vm.setHue(20f)
        assertEquals(20f, vm.uiState.value.appearance.customHue)
        repository.gate = null
        gate.complete(Unit)
        runCurrent()
        assertEquals(20f, vm.uiState.value.appearance.customHue)
        advanceUntilIdle()
        assertEquals(20f, repository.settings.value.customHue)
        assertEquals(2, repository.writes)
    }

    @Test fun externalChangesAreObservedAndPendingFieldChangesPreserveThem() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        repository.settings.value = AppearanceSettings(mode = ThemeMode.Dark)
        runCurrent()
        assertEquals(ThemeMode.Dark, vm.uiState.value.appearance.mode)
        vm.setHue(42f)
        repository.settings.value = repository.settings.value.copy(mode = ThemeMode.Light)
        advanceUntilIdle()
        assertEquals(ThemeMode.Light, repository.settings.value.mode)
        assertEquals(ThemeMode.Light, vm.uiState.value.appearance.mode)
        assertEquals(42f, repository.settings.value.customHue)
    }

    @Test fun failedInFlightWriteDiscardsNewerUncommittedPreviewAndQueuedSignals() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.setMode(ThemeMode.Dark)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        repository.writeFailure = true
        vm.setHue(10f)
        vm.flushPending()
        runCurrent()
        vm.setHue(20f)
        vm.flushPending()
        repository.gate = null
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(AppearanceSettings(mode = ThemeMode.Dark), vm.uiState.value.appearance)
        assertTrue(vm.uiState.value.hasWriteError)
        assertEquals(2, repository.writes)
        repository.writeFailure = false
        vm.selectPreset(ThemePreset.Blue)
        advanceUntilIdle()
        assertNull(repository.settings.value.customHue)
        assertEquals(ThemeColorSource.Preset, repository.settings.value.colorSource)
    }

    @Test fun continuousReadFailureAlsoDisablesEditingAndUsesDefaults() = runTest {
        // 替换订阅的上游可在首次成功后失败。
        val values = MutableStateFlow(false)
        val failing = object : SettingsRepository {
            override fun observeAppearance() = flow {
                values.collect { fail ->
                    if (fail) throw IOException("read")
                    emit(AppearanceSettings(mode = ThemeMode.Dark))
                }
            }
            override suspend fun updateAppearance(transform: (AppearanceSettings) -> AppearanceSettings) = error("No writes expected")
        }
        val vm = AppearanceViewModel(failing).also { store.put("appearance", it) }
        advanceUntilIdle()
        values.value = true
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasReadError)
        assertFalse(vm.uiState.value.canEdit)
        assertEquals(AppearanceSettings(), vm.uiState.value.appearance)
    }

    private class FakeSettings : SettingsRepository {
        val settings = MutableStateFlow(AppearanceSettings())
        var readFailure = false
        var hangRead = false
        var writeFailure = false
        var gate: CompletableDeferred<Unit>? = null
        var writes = 0
        override fun observeAppearance() = flow {
            if (readFailure) throw IOException("read")
            if (hangRead) awaitCancellation()
            emitAll(settings)
        }
        override suspend fun updateAppearance(transform: (AppearanceSettings) -> AppearanceSettings): AppearanceSettings {
            writes++
            gate?.await()
            if (writeFailure) throw IOException("write")
            settings.value = transform(settings.value)
            return settings.value
        }
    }
}
