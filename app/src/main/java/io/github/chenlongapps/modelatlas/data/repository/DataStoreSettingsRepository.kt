package io.github.chenlongapps.modelatlas.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.chenlongapps.modelatlas.data.model.AppearanceSettings
import io.github.chenlongapps.modelatlas.data.model.CompareModelSelection
import io.github.chenlongapps.modelatlas.data.model.ThemeColorSource
import io.github.chenlongapps.modelatlas.data.model.ThemeMode
import io.github.chenlongapps.modelatlas.data.model.ThemePreset
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    private val compareSelectionLock = Mutex()

    override fun observeAppearance() = dataStore.data.map(::decodeAppearance).distinctUntilChanged()

    override suspend fun readCompareSelection(): CompareModelSelection = compareSelectionLock.withLock {
        val preferences = dataStore.data.first()
        try {
            CompareModelSelection(preferences[CompareFirst], preferences[CompareSecond]).normalized()
        } catch (error: ClassCastException) {
            throw IOException("Invalid compare selection preference type", error)
        }
    }

    override suspend fun saveCompareSelection(selection: CompareModelSelection) {
        compareSelectionLock.withLock {
            val (first, second) = selection.normalized()
            dataStore.edit { preferences ->
                if (first == null) preferences.remove(CompareFirst) else preferences[CompareFirst] = first
                if (second == null) preferences.remove(CompareSecond) else preferences[CompareSecond] = second
            }
        }
    }

    override suspend fun updateAppearance(
        transform: (AppearanceSettings) -> AppearanceSettings,
    ): AppearanceSettings = decodeAppearance(dataStore.edit { preferences ->
        val next = transform(decodeAppearance(preferences))
        preferences[Mode] = next.mode.name
        preferences[Source] = next.colorSource.name
        preferences[Preset] = next.preset.name
        next.customHue?.let { preferences[Hue] = it } ?: preferences.remove(Hue)
        preferences[NavigationAutoHide] = next.navigationAutoHideEnabled
    })
}

// 文件名与两套备份排除规则保持一致；不安装自动覆盖损坏文件的 corruptionHandler。
internal const val SettingsFileName = "appearance.preferences_pb"
private val Mode = stringPreferencesKey("theme_mode")
private val Source = stringPreferencesKey("theme_color_source")
private val Preset = stringPreferencesKey("theme_preset")
private val Hue = floatPreferencesKey("custom_hue")
private val NavigationAutoHide = booleanPreferencesKey("navigation_auto_hide")
private val CompareFirst = stringPreferencesKey("compare_first_model_id")
private val CompareSecond = stringPreferencesKey("compare_second_model_id")

internal fun decodeAppearance(preferences: Preferences): AppearanceSettings = try {
    AppearanceSettings(
        mode = preferences[Mode]?.let(ThemeMode::valueOf) ?: ThemeMode.System,
        colorSource = preferences[Source]?.let(ThemeColorSource::valueOf) ?: ThemeColorSource.System,
        preset = preferences[Preset]?.let(ThemePreset::valueOf) ?: ThemePreset.Blue,
        customHue = preferences[Hue],
        navigationAutoHideEnabled = preferences[NavigationAutoHide] ?: false,
    )
} catch (error: IllegalArgumentException) {
    throw IOException("Invalid appearance preferences", error)
} catch (error: ClassCastException) {
    throw IOException("Invalid appearance preference type", error)
}
