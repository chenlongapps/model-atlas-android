package io.github.chenlongapps.modelatlas.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chenlongapps.modelatlas.data.model.AppearanceSettings
import io.github.chenlongapps.modelatlas.data.model.ThemeColorSource
import io.github.chenlongapps.modelatlas.data.model.ThemeMode
import io.github.chenlongapps.modelatlas.data.model.ThemePreset
import io.github.chenlongapps.modelatlas.data.repository.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class AppearanceUiState(
    val appearance: AppearanceSettings = AppearanceSettings(),
    val isLoading: Boolean = true,
    val startupComplete: Boolean = false,
    val hasReadError: Boolean = false,
    val hasWriteError: Boolean = false,
) {
    val canEdit: Boolean get() = !isLoading && !hasReadError
}

/** Activity 级状态：只取消防抖计时，不取消已经开始的磁盘提交。 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AppearanceUiState())
    val uiState = mutableState.asStateFlow()
    private var saved = AppearanceSettings()
    private var readJob: Job? = null
    private var debounceJob: Job? = null
    private val pending = mutableListOf<(AppearanceSettings) -> AppearanceSettings>()
    private val writes = Channel<Unit>(Channel.CONFLATED)
    private var writing = false

    init {
        retry()
        viewModelScope.launch {
            for (signal in writes) {
                if (pending.isEmpty() || !uiState.value.canEdit) continue
                val changes = pending.toList()
                pending.clear()
                writing = true
                try {
                    saved = repository.updateAppearance { current -> changes.fold(current) { value, change -> change(value) } }
                    if (pending.isEmpty() && uiState.value.canEdit) {
                        mutableState.value = uiState.value.copy(appearance = saved)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    debounceJob?.cancel()
                    pending.clear()
                    if (uiState.value.canEdit) {
                        mutableState.value = uiState.value.copy(appearance = saved, hasWriteError = true)
                    }
                } finally {
                    writing = false
                }
            }
        }
    }

    fun retry() {
        if (readJob?.isActive == true) return
        readJob = viewModelScope.launch {
            mutableState.value = uiState.value.copy(isLoading = true, hasReadError = false, hasWriteError = false)
            try {
                val initial = withTimeoutOrNull(1_500) { repository.observeAppearance().first() }
                    ?: throw IOException("Appearance read timed out")
                saved = initial
                mutableState.value = AppearanceUiState(appearance = initial, isLoading = false, startupComplete = true)
                repository.observeAppearance().collect { value ->
                    saved = value
                    if (pending.isEmpty() && !writing) mutableState.value = uiState.value.copy(appearance = value)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                debounceJob?.cancel()
                pending.clear()
                mutableState.value = AppearanceUiState(isLoading = false, startupComplete = true, hasReadError = true)
            }
        }
    }

    fun setMode(mode: ThemeMode) = change { it.copy(mode = mode) }
    fun useSystemColors() = change { it.copy(colorSource = ThemeColorSource.System) }
    fun selectPreset(preset: ThemePreset) = change { it.copy(colorSource = ThemeColorSource.Preset, preset = preset) }
    fun restoreCustomColor() {
        if (uiState.value.appearance.customHue != null) change { it.copy(colorSource = ThemeColorSource.Custom) }
    }
    fun setHue(hue: Float) {
        if (!hue.isFinite()) return
        change(debounce = true) { it.copy(colorSource = ThemeColorSource.Custom, customHue = hue.coerceIn(0f, 360f)) }
    }

    private fun change(debounce: Boolean = false, transform: (AppearanceSettings) -> AppearanceSettings) {
        if (!uiState.value.canEdit) return
        val next = transform(uiState.value.appearance)
        if (next == uiState.value.appearance) return
        pending += transform
        mutableState.value = uiState.value.copy(appearance = next, hasWriteError = false)
        debounceJob?.cancel()
        if (debounce) debounceJob = viewModelScope.launch { delay(300); writes.trySend(Unit) }
        else writes.trySend(Unit)
    }

    fun flushPending() {
        debounceJob?.cancel()
        writes.trySend(Unit)
    }
}
