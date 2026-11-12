package io.github.chenlongapps.modelatlas.ai.config

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

internal const val LlmConfigFileName = "llm_config.preferences_pb"

/** 与外观 DataStore 隔离。只有用户确认清除时，才允许替换损坏的 protobuf。 */
internal class LlmPreferencesStore(
    produceFile: () -> File,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val resetRequested = AtomicBoolean(false)
    val dataStore = PreferenceDataStoreFactory.create(
        scope = scope,
        corruptionHandler = ReplaceFileCorruptionHandler { error ->
            if (resetRequested.get()) emptyPreferences() else throw error
        },
        produceFile = produceFile,
    )

    // 调用方持有仓库 Mutex；已确认的删除完成后再释放恢复权限，取消不泄漏权限。
    suspend fun reset() = withContext(NonCancellable) {
        resetRequested.set(true)
        try { dataStore.edit { it.clear() }; Unit } finally { resetRequested.set(false) }
    }
}
