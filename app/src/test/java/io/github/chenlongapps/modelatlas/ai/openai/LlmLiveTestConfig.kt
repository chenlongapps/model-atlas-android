package io.github.chenlongapps.modelatlas.ai.openai

import io.github.chenlongapps.modelatlas.ai.LlmConfig
import java.io.File
import java.util.Properties

/** 仅测试使用；环境变量按字段优先，本地配置不经过 Gradle、不输出配置内容。 */
internal fun readLlmLiveTestConfig(
    localPropertiesFile: File?,
    environment: (String) -> String? = System::getenv,
): LlmConfig? {
    val keys = listOf("LLM_TEST_BASE_URL", "LLM_TEST_API_KEY", "LLM_TEST_MODEL_ID")
    val values = keys.map { environment(it)?.takeIf(String::isNotBlank) }
    val properties = Properties()
    if (values.any { it == null } && localPropertiesFile?.isFile == true) {
        localPropertiesFile.bufferedReader(Charsets.UTF_8).use { properties.load(it) }
    }
    val resolved = keys.mapIndexed { index, key ->
        values[index] ?: properties.getProperty(key)?.takeIf(String::isNotBlank) ?: return null
    }
    return LlmConfig(baseUrl = resolved[0], apiKey = resolved[1], modelId = resolved[2])
}
