package io.github.chenlongapps.modelatlas.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.chenlongapps.modelatlas.data.model.AppearanceSettings
import io.github.chenlongapps.modelatlas.data.model.ThemeColorSource
import io.github.chenlongapps.modelatlas.data.model.ThemeMode
import io.github.chenlongapps.modelatlas.data.model.ThemePreset
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override fun observeAppearance() = dataStore.data.map(::decodeAppearance).distinctUntilChanged()

    override suspend fun updateAppearance(
        transform: (AppearanceSettings) -> AppearanceSettings,
    ): AppearanceSettings = decodeAppearance(dataStore.edit { preferences ->
        val next = transform(decodeAppearance(preferences))
        preferences[Mode] = next.mode.name
        preferences[Source] = next.colorSource.name
        preferences[Preset] = next.preset.name
        next.customHue?.let { preferences[Hue] = it } ?: preferences.remove(Hue)
    })
}

// 文件名与两套备份排除规则保持一致；不安装自动覆盖损坏文件的 corruptionHandler。
internal const val SettingsFileName = "appearance.preferences_pb"
private val Mode = stringPreferencesKey("theme_mode")
private val Source = stringPreferencesKey("theme_color_source")
private val Preset = stringPreferencesKey("theme_preset")
private val Hue = floatPreferencesKey("custom_hue")

internal fun decodeAppearance(preferences: Preferences): AppearanceSettings = try {
    AppearanceSettings(
        mode = preferences[Mode]?.let(ThemeMode::valueOf) ?: ThemeMode.System,
        colorSource = preferences[Source]?.let(ThemeColorSource::valueOf) ?: ThemeColorSource.System,
        preset = preferences[Preset]?.let(ThemePreset::valueOf) ?: ThemePreset.Blue,
        customHue = preferences[Hue],
    )
} catch (error: IllegalArgumentException) {
    throw IOException("Invalid appearance preferences", error)
} catch (error: ClassCastException) {
    throw IOException("Invalid appearance preference type", error)
}
