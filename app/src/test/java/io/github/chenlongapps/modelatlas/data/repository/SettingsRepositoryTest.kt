package io.github.chenlongapps.modelatlas.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.chenlongapps.modelatlas.data.model.AppearanceSettings
import io.github.chenlongapps.modelatlas.data.model.ThemeColorSource
import io.github.chenlongapps.modelatlas.data.model.ThemeMode
import io.github.chenlongapps.modelatlas.data.model.ThemePreset
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun defaultsAndPreferencesSurviveStoreRecreation() = runTest {
        val file = File(folder.root, SettingsFileName)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = repository(file, scope)
            assertEquals(AppearanceSettings(), repository.observeAppearance().first())
            repository.setThemeMode(ThemeMode.Dark)
            repository.setCustomHue(137.5f)
            repository.selectPreset(ThemePreset.Sage)
            repository.useSystemColors()
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
        }
        val reopenedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = repository(file, reopenedScope)
            assertEquals(
                AppearanceSettings(ThemeMode.Dark, ThemeColorSource.System, ThemePreset.Sage, 137.5f),
                reopened.observeAppearance().first(),
            )
        } finally {
            reopenedScope.coroutineContext.job.cancelAndJoin()
        }
    }

    @Test fun concurrentFieldChangesAreAtomicAndDoNotReplaceOtherPreferences() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = repository(File(folder.root, SettingsFileName), scope)
            coroutineScope {
                launch { repository.setThemeMode(ThemeMode.Light) }
                launch { repository.setCustomHue(360f) }
            }
            assertEquals(AppearanceSettings(mode = ThemeMode.Light, colorSource = ThemeColorSource.Custom, customHue = 360f),
                repository.observeAppearance().first())
            repository.useSystemColors()
            assertEquals(360f, repository.observeAppearance().first().customHue)
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
        }
    }

    @Test fun corruptFileIsReportedAndNeverAutomaticallyReplaced() = runTest {
        val file = File(folder.root, SettingsFileName).apply { writeBytes(byteArrayOf(0, 1, 2, 3, 4)) }
        val before = file.readBytes()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = repository(file, scope)
            try {
                repository.observeAppearance().first()
                fail("Expected corruption to be surfaced")
            } catch (_: IOException) { }
            try {
                repository.setThemeMode(ThemeMode.Dark)
                fail("Must not replace unreadable settings")
            } catch (_: IOException) { }
            assertArrayEquals(before, file.readBytes())
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
        }
    }

    @Test fun malformedPreferenceValuesAreNotTreatedAsFirstInstall() {
        val invalid = listOf(
            mutablePreferencesOf(stringPreferencesKey("theme_mode") to "invalid"),
            mutablePreferencesOf(stringPreferencesKey("theme_color_source") to "Custom"),
            mutablePreferencesOf(floatPreferencesKey("custom_hue") to Float.NaN),
            mutablePreferencesOf(floatPreferencesKey("custom_hue") to -1f),
            mutablePreferencesOf(stringPreferencesKey("custom_hue") to "wrong type"),
        )
        invalid.forEach { preferences ->
            assertThrows(IOException::class.java) { decodeAppearance(preferences) }
        }
    }

    @Test fun invalidCustomHueIsRejected() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = repository(File(folder.root, SettingsFileName), scope)
            for (hue in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 361f)) {
                try {
                    repository.setCustomHue(hue)
                    fail("Invalid hue accepted")
                } catch (_: IllegalArgumentException) { }
            }
            assertEquals(AppearanceSettings(), repository.observeAppearance().first())
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
        }
    }

    private fun repository(file: File, scope: CoroutineScope) = DataStoreSettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
    )
}
