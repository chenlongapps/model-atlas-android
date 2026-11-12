package io.github.chenlongapps.modelatlas.ai.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.chenlongapps.modelatlas.ai.config.ApiKeyCipher
import io.github.chenlongapps.modelatlas.ai.config.DataStoreLlmConfigRepository
import io.github.chenlongapps.modelatlas.ai.config.KeystoreApiKeyCipher
import io.github.chenlongapps.modelatlas.ai.config.LlmConfigFileName
import io.github.chenlongapps.modelatlas.ai.config.LlmConfigRepository
import io.github.chenlongapps.modelatlas.ai.config.LlmPreferencesStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LlmConfigBindings {
    @Binds abstract fun config(repository: DataStoreLlmConfigRepository): LlmConfigRepository
    @Binds abstract fun cipher(cipher: KeystoreApiKeyCipher): ApiKeyCipher
}

@Module
@InstallIn(SingletonComponent::class)
internal object LlmConfigModule {
    @Provides
    @Singleton
    fun store(@ApplicationContext context: Context): LlmPreferencesStore = LlmPreferencesStore(
        produceFile = { context.preferencesDataStoreFile(LlmConfigFileName.removeSuffix(".preferences_pb")) },
    )
}
