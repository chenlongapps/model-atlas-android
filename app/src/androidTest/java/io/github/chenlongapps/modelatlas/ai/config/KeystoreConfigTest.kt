package io.github.chenlongapps.modelatlas.ai.config

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.platform.app.InstrumentationRegistry
import io.github.chenlongapps.modelatlas.ai.LlmConfig
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class KeystoreConfigTest {
    private val id = UUID.randomUUID().toString()
    private val alias = "modelatlas.test.api-key.$id"
    private val cipher = KeystoreApiKeyCipher(alias)
    private val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "$id.preferences_pb")
    private val scopes = mutableListOf<CoroutineScope>()

    @After fun close() = runBlocking {
        scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        cipher.deleteKey()
        file.delete()
        Unit
    }

    @Test fun keystoreKeyIsNonExportableAndEveryEncryptionUsesAFreshIv() {
        val aad = associatedData("https://example.test/v1", "model")
        val first = cipher.encrypt("device-test-secret", aad)
        val second = cipher.encrypt("device-test-secret", aad)
        assertEquals(12, first.iv.size)
        assertFalse(first.iv.contentEquals(second.iv))
        assertFalse(first.ciphertext.contentEquals(second.ciphertext))
        assertEquals("device-test-secret", cipher.decrypt(first, aad))
        val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null)
        assertNull(key.encoded)
        assertThrows(Exception::class.java) { cipher.decrypt(first, associatedData("https://attacker.test", "model")) }
        val modified = first.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { cipher.decrypt(EncryptedApiKey(first.iv, modified), aad) }
    }

    @Test fun productionRepositoryAndKeystoreSurviveStoreAndCipherRecreationWithoutPlaintextOnDisk() = runBlocking {
        val (_, repo) = repository()
        repo.save(LlmConfig("https://example.test/custom/api", "device-test-secret", "device-model"))
        assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("device-test-secret"))
        scopes.last().coroutineContext.job.cancelAndJoin()
        val restored = repository(KeystoreApiKeyCipher(alias)).second.load()!!
        assertEquals("https://example.test/custom/api", restored.baseUrl)
        assertEquals("device-model", restored.modelId)
        assertEquals("device-test-secret", restored.apiKey)
    }

    @Test fun missingKeystoreKeyIsReportedWithoutReplacementAndExplicitClearAllowsReconfiguration() = runBlocking {
        val (_, repo) = repository()
        repo.save(LlmConfig("https://example.test/api", "device-test-secret", "model"))
        cipher.deleteKey()
        expectFailure(ConfigFailure.KEY_UNAVAILABLE) { repo.load() }
        assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias))
        repo.clear()
        assertNull(repo.load())
        repo.save(LlmConfig("https://example.test/api", "replacement-secret", "new-model"))
        assertEquals("replacement-secret", repo.load()!!.apiKey)
    }

    @Test fun changedEndpointFailsClosedAndDoesNotModifySavedRecord() = runBlocking {
        val (store, repo) = repository()
        repo.save(LlmConfig("https://example.test/api", "device-test-secret", "model"))
        store.dataStore.edit { it[stringPreferencesKey("base_url")] = "https://attacker.test/api" }
        val before = file.readBytes()
        expectFailure(ConfigFailure.KEY_UNAVAILABLE) { repo.load() }
        assertArrayEquals(before, file.readBytes())
        repo.clear()
        assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias))
    }

    @Test fun corruptFileIsRetainedUntilExplicitClear() = runBlocking {
        file.writeBytes(byteArrayOf(0, 1, 2, 3, 4))
        val (_, repo) = repository()
        val before = file.readBytes()
        expectFailure(ConfigFailure.STORAGE) { repo.load() }
        assertArrayEquals(before, file.readBytes())
        repo.clear()
        assertNull(repo.load())
    }

    private fun repository(cipher: ApiKeyCipher = this.cipher): Pair<LlmPreferencesStore, LlmConfigRepository> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        val store = LlmPreferencesStore({ file }, scope)
        return store to DataStoreLlmConfigRepository(store, cipher, Dispatchers.IO)
    }

    private suspend fun expectFailure(failure: ConfigFailure, block: suspend () -> Any?) {
        try { block(); fail("Expected safe failure") } catch (error: ConfigStorageException) {
            assertEquals(failure, error.failure)
            assertNull(error.cause)
        }
    }
}
