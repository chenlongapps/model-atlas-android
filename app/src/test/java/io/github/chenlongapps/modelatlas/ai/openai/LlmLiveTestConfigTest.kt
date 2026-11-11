package io.github.chenlongapps.modelatlas.ai.openai

import java.util.Properties
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LlmLiveTestConfigTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun readsLocalPropertiesWithoutEnvironment() {
        val config = requireNotNull(readLlmLiveTestConfig(propertiesFile()) { null })
        assertEquals("https://local.example/v1/", config.baseUrl)
        assertEquals("local-test-key", config.apiKey)
        assertEquals("local-model", config.modelId)
        assertFalse(config.toString().contains("local-test-key"))
    }

    @Test fun environmentOverridesEachFieldIndependently() {
        for (key in defaults.keys) {
            val config = requireNotNull(readLlmLiveTestConfig(propertiesFile()) {
                if (it == key) overrides.getValue(it) else null
            })
            assertEquals(if (key == "LLM_TEST_BASE_URL") overrides.getValue(key) else defaults.getValue("LLM_TEST_BASE_URL"), config.baseUrl)
            assertEquals(if (key == "LLM_TEST_API_KEY") overrides.getValue(key) else defaults.getValue("LLM_TEST_API_KEY"), config.apiKey)
            assertEquals(if (key == "LLM_TEST_MODEL_ID") overrides.getValue(key) else defaults.getValue("LLM_TEST_MODEL_ID"), config.modelId)
        }
    }

    @Test fun completeEnvironmentDoesNotReadMalformedLocalFile() {
        val file = temporaryFolder.newFile().apply { writeText("invalid=\\uZZZZ") }
        val config = requireNotNull(readLlmLiveTestConfig(file, overrides::get))
        assertEquals(overrides.getValue("LLM_TEST_API_KEY"), config.apiKey)
        assertEquals(overrides.getValue("LLM_TEST_BASE_URL"), config.baseUrl)
        assertEquals(overrides.getValue("LLM_TEST_MODEL_ID"), config.modelId)
    }

    @Test fun blankEnvironmentFallsBackToLocalProperties() {
        val config = requireNotNull(readLlmLiveTestConfig(propertiesFile()) { " " })
        assertEquals("local-test-key", config.apiKey)
    }

    @Test fun missingAndBlankFieldsReturnNoConfiguration() {
        for (key in defaults.keys) {
            assertNull(readLlmLiveTestConfig(propertiesFile(defaults - key)) { null })
            assertNull(readLlmLiveTestConfig(propertiesFile(defaults + (key to " "))) { null })
        }
    }

    @Test fun missingLocalFileAllowsEnvironmentButNotIncompleteConfiguration() {
        val file = temporaryFolder.root.resolve("missing.properties")
        assertNull(readLlmLiveTestConfig(file) { null })
        assertNull(readLlmLiveTestConfig(null) { null })
        assertNotNull(readLlmLiveTestConfig(file, overrides::get))
    }

    private fun propertiesFile(values: Map<String, String> = defaults) = temporaryFolder.newFile().apply {
        bufferedWriter(Charsets.UTF_8).use { writer ->
            Properties().apply { putAll(values) }.store(writer, null)
        }
    }

    private val defaults = mapOf(
        "LLM_TEST_BASE_URL" to "https://local.example/v1/",
        "LLM_TEST_API_KEY" to "local-test-key",
        "LLM_TEST_MODEL_ID" to "local-model",
    )
    private val overrides = mapOf(
        "LLM_TEST_BASE_URL" to "https://environment.example/v1/",
        "LLM_TEST_API_KEY" to "environment-test-key",
        "LLM_TEST_MODEL_ID" to "environment-model",
    )
}
