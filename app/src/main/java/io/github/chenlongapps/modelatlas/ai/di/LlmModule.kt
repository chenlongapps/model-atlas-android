package io.github.chenlongapps.modelatlas.ai.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.chenlongapps.modelatlas.ai.LlmClient
import io.github.chenlongapps.modelatlas.ai.openai.OpenAiCompatibleClient

@Module
@InstallIn(SingletonComponent::class)
abstract class LlmModule {
    @Binds
    abstract fun llmClient(client: OpenAiCompatibleClient): LlmClient
}
