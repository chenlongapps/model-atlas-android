package io.github.chenlongapps.modelatlas.data.repository

import io.github.chenlongapps.modelatlas.data.model.AiModel
import javax.inject.Inject

/** Local sample data until the Models.dev API is connected. */
class ModelRepository @Inject constructor() {
    fun getModels(): List<AiModel> = listOf(
        AiModel(
            id = "openai/gpt-4.1",
            name = "GPT-4.1",
            provider = "OpenAI",
            description = "通用模型，适合文本生成与编程任务。",
        ),
        AiModel(
            id = "anthropic/claude-sonnet-4",
            name = "Claude Sonnet 4",
            provider = "Anthropic",
            description = "面向文本理解、内容创作和代码辅助。",
        ),
        AiModel(
            id = "google/gemini-2.5-pro",
            name = "Gemini 2.5 Pro",
            provider = "Google",
            description = "面向推理与多模态理解。",
        ),
    )
}
