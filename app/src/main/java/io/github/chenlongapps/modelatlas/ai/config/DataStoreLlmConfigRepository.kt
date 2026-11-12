package io.github.chenlongapps.modelatlas.ai.config

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.chenlongapps.modelatlas.ai.LlmConfig
import io.github.chenlongapps.modelatlas.di.IoDispatcher
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
internal class DataStoreLlmConfigRepository @Inject constructor(
    private val store: LlmPreferencesStore,
    private val cipher: ApiKeyCipher,
    @IoDispatcher private val io: CoroutineDispatcher,
) : LlmConfigRepository {
    private val mutex = Mutex()

    override suspend fun load(): LlmConfig? = operation {
        val preferences = storage { store.dataStore.data.first() }
        if (preferences.asMap().isEmpty()) return@operation null
        val (baseUrl, modelId, encrypted) = storage {
            require(preferences[Version] == 1)
            Triple(
                requireNotNull(preferences[BaseUrl]), requireNotNull(preferences[ModelId]),
                EncryptedApiKey(Base64.getDecoder().decode(requireNotNull(preferences[Iv])),
                    Base64.getDecoder().decode(requireNotNull(preferences[Ciphertext]))),
            )
        }
        val apiKey = cryptography { cipher.decrypt(encrypted, associatedData(baseUrl, modelId)) }
        storage { LlmConfig(baseUrl, apiKey, modelId).also { it.validate() } }
    }

    override suspend fun save(config: LlmConfig) = operation {
        config.validate()
        // 加密成功后才进入一次原子提交；绝不将原始 API Key 写入 Preferences。
        val encrypted = cryptography { cipher.encrypt(config.apiKey, associatedData(config.baseUrl, config.modelId)) }
        storage {
            store.dataStore.edit {
                it.clear()
                it[Version] = 1
                it[BaseUrl] = config.baseUrl
                it[ModelId] = config.modelId
                it[Iv] = Base64.getEncoder().encodeToString(encrypted.iv)
                it[Ciphertext] = Base64.getEncoder().encodeToString(encrypted.ciphertext)
            }
            Unit
        }
    }

    override suspend fun clear() = operation {
        storage { store.reset() }
        cryptography { cipher.deleteKey() }
    }

    private suspend fun <T> operation(block: suspend () -> T): T = withContext(io) { mutex.withLock { block() } }

    private suspend fun <T> storage(block: suspend () -> T): T = safe(ConfigFailure.STORAGE, block)
    private suspend fun <T> cryptography(block: suspend () -> T): T = safe(ConfigFailure.KEY_UNAVAILABLE, block)
    private suspend fun <T> safe(failure: ConfigFailure, block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // 不保留原始 cause / message，避免磁盘或 Provider 异常带出敏感内容。
        throw ConfigStorageException(failure)
    }

    private companion object {
        val Version = intPreferencesKey("version")
        val BaseUrl = stringPreferencesKey("base_url")
        val ModelId = stringPreferencesKey("model_id")
        val Iv = stringPreferencesKey("api_key_iv")
        val Ciphertext = stringPreferencesKey("api_key_ciphertext")
    }
}

/** 长度前缀避免字段边界歧义；格式版本也纳入认证，URL 被改动不能继续解密密钥。 */
internal fun associatedData(baseUrl: String, modelId: String): ByteArray = ByteArrayOutputStream().apply {
    DataOutputStream(this).use { output ->
        output.writeInt(1)
        for (value in listOf(baseUrl, modelId)) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            output.writeInt(bytes.size)
            output.write(bytes)
        }
    }
}.toByteArray()
