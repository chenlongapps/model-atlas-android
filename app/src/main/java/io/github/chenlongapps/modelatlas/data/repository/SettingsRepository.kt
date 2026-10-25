package io.github.chenlongapps.modelatlas.data.repository

import io.github.chenlongapps.modelatlas.data.model.AppearanceSettings
import io.github.chenlongapps.modelatlas.data.model.ThemeColorSource
import io.github.chenlongapps.modelatlas.data.model.ThemeMode
import io.github.chenlongapps.modelatlas.data.model.ThemePreset
import kotlinx.coroutines.flow.Flow

/** 持久化状态，不包含 UI 拖动预览；读取或写入失败向调用方抛出，不以默认值掩盖失败。 */
interface SettingsRepository {
    fun observeAppearance(): Flow<AppearanceSettings>

    /** 原子读改写；transform 应无副作用。仅在持久化成功后返回最新状态。 */
    suspend fun updateAppearance(transform: (AppearanceSettings) -> AppearanceSettings): AppearanceSettings

    suspend fun setThemeMode(mode: ThemeMode) = updateAppearance { it.copy(mode = mode) }
    suspend fun useSystemColors() = updateAppearance { it.copy(colorSource = ThemeColorSource.System) }
    suspend fun selectPreset(preset: ThemePreset) = updateAppearance {
        it.copy(colorSource = ThemeColorSource.Preset, preset = preset)
    }
    suspend fun setCustomHue(hue: Float) = updateAppearance {
        it.copy(colorSource = ThemeColorSource.Custom, customHue = hue)
    }
}
