package io.github.chenlongapps.modelatlas.ai.config

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.chenlongapps.modelatlas.ai.LlmConfig
import java.io.File
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LlmConfigRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val scopes = mutableListOf<CoroutineScope>()
    private val cipher = TestCipher()
    private val file get() = File(folder.root, LlmConfigFileName)

    @After fun close() = runBlocking { scopes.forEach { it.coroutineContext.job.cancelAndJoin() } }

    @Test fun completeConfigSurvivesFileReopenAndNeverWritesPlaintextKey() = runTest {
        val (store, repo) = repository()
        assertNull(repo.load())
        repo.save(config())
        val preferences = store.dataStore.data.first()
        assertEquals("https://example.test/custom/api", preferences[stringPreferencesKey("base_url")])
        assertEquals("model", preferences[stringPreferencesKey("model_id")])
        assertFalse(preferences.asMap().toString().contains("private-api-key"))
        assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("private-api-key"))
        scopes.last().coroutineContext.job.cancelAndJoin()
        val restored = repository().second.load()!!
        assertEquals(config().baseUrl, restored.baseUrl)
        assertEquals(config().modelId, restored.modelId)
        assertEquals(config().apiKey, restored.apiKey)
        assertFalse(restored.toString().contains("private-api-key"))
    }

    @Test fun corruptProtobufIsNotReplacedByLoadOrSaveButExplicitClearRecovers() = runTest {
        file.writeBytes(byteArrayOf(0, 1, 2, 3, 4))
        val original = file.readBytes()
        val (_, repo) = repository()
        expectFailure(ConfigFailure.STORAGE) { repo.load() }
        expectFailure(ConfigFailure.STORAGE) { repo.save(config()) }
        assertArrayEquals(original, file.readBytes())
        repo.clear()
        assertNull(repo.load())
        repo.save(config())
        assertEquals("model", repo.load()!!.modelId)
    }

    @Test fun malformedAndUnsupportedRecordsFailClosedWithoutBeingOverwrittenByReads() = runTest {
        val (store, repo) = repository()
        store.dataStore.edit { it[intPreferencesKey("version")] = 1 }
        expectFailure(ConfigFailure.STORAGE) { repo.load() }
        assertEquals(1, store.dataStore.data.first().asMap().size)
        repo.clear(); repo.save(config())
        store.dataStore.edit { it[intPreferencesKey("version")] = 2 }
        expectFailure(ConfigFailure.STORAGE) { repo.load() }
    }

    @Test fun baseUrlAndModelIdTamperingCannotRedirectSavedApiKey() = runTest {
        val (store, repo) = repository()
        for ((field, value) in listOf("base_url" to "https://attacker.test", "model_id" to "tampered-model")) {
            repo.save(config())
            store.dataStore.edit { it[stringPreferencesKey(field)] = value }
            val before = file.readBytes()
            expectFailure(ConfigFailure.KEY_UNAVAILABLE) { repo.load() }
            assertArrayEquals(before, file.readBytes())
        }
    }

    @Test fun ciphertextAndIvTamperingFailAuthentication() = runTest {
        val (store, repo) = repository()
        for (field in listOf("api_key_ciphertext", "api_key_iv")) {
            repo.save(config())
            store.dataStore.edit { preferences ->
                val key = stringPreferencesKey(field)
                val bytes = java.util.Base64.getDecoder().decode(preferences[key]!!)
                bytes[0] = (bytes[0].toInt() xor 1).toByte()
                preferences[key] = java.util.Base64.getEncoder().encodeToString(bytes)
            }
            expectFailure(ConfigFailure.KEY_UNAVAILABLE) { repo.load() }
        }
    }

    @Test fun failedEncryptionDoesNotPublishPartialConfigurationOrLeakCause() = runTest {
        val (_, repo) = repository()
        repo.save(config())
        val before = file.readBytes()
        cipher.encryptError = IllegalStateException("private-api-key")
        expectFailure(ConfigFailure.KEY_UNAVAILABLE) { repo.save(config("new")) }
        assertArrayEquals(before, file.readBytes())
        assertEquals("model", repo.load()!!.modelId)
    }

    @Test fun missingKeyDoesNotGenerateReplacementUntilExplicitReset() = runTest {
        val (_, repo) = repository()
        repo.save(config())
        cipher.key = null
        val before = file.readBytes()
        expectFailure(ConfigFailure.KEY_UNAVAILABLE) { repo.load() }
        assertNull(cipher.key)
        assertArrayEquals(before, file.readBytes())
        repo.clear(); repo.save(config("new"))
        assertEquals("new", repo.load()!!.modelId)
    }

    @Test fun concurrentSavesCommitWholeRecordsAndClearRemovesKeyAndAllPreferences() = runTest {
        val (store, repo) = repository()
        (1..10).map { id -> async { repo.save(config("model-$id", "key-$id")) } }.awaitAll()
        val saved = repo.load()!!
        assertEquals(saved.modelId.replace("model-", "key-"), saved.apiKey)
        repo.clear()
        assertNull(cipher.key)
        assertTrue(store.dataStore.data.first().asMap().isEmpty())
        assertNull(repo.load())
    }

    @Test fun coroutineCancellationIsNotWrappedAsStorageFailure() = runTest {
        val (_, repo) = repository()
        cipher.encryptError = CancellationException("cancelled")
        try { repo.save(config()); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertNull(repo.load())
    }

    @Test fun diskWriteFailureKeepsPreviouslyCommittedRecordIntact() = runTest {
        val live = folder.newFolder("live")
        val parked = File(folder.root, "parked")
        val path = File(live, LlmConfigFileName)
        val (_, repo) = repository(path)
        repo.save(config())
        assertTrue(live.renameTo(parked))
        // 只在测试自建目录中制造不可写路径，不依赖宿主 chmod 或特权行为。
        live.writeText("blocks directory creation")
        try {
            expectFailure(ConfigFailure.STORAGE) { repo.save(config("new")) }
            assertEquals("model", repo.load()!!.modelId)
        } finally {
            scopes.last().coroutineContext.job.cancelAndJoin()
            assertTrue(live.delete())
            assertTrue(parked.renameTo(live))
        }
        assertEquals("model", repository(path).second.load()!!.modelId)
    }

    @Test fun keyCleanupFailureAfterDiskClearDoesNotRestoreOldCredentials() = runTest {
        val (_, repo) = repository()
        repo.save(config())
        cipher.deleteError = IllegalStateException("provider failure")
        expectFailure(ConfigFailure.KEY_UNAVAILABLE) { repo.clear() }
        assertNull(repo.load())
        cipher.deleteError = null
        repo.clear()
        assertNull(cipher.key)
    }

    private fun repository(path: File = file): Pair<LlmPreferencesStore, DataStoreLlmConfigRepository> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        val store = LlmPreferencesStore({ path }, scope)
        return store to DataStoreLlmConfigRepository(store, cipher, Dispatchers.IO)
    }

    private fun config(model: String = "model", key: String = "private-api-key") =
        LlmConfig("https://example.test/custom/api", key, model)

    private suspend fun expectFailure(failure: ConfigFailure, block: suspend () -> Any?) {
        try { block(); fail("Expected safe failure") } catch (error: ConfigStorageException) {
            assertEquals(failure, error.failure)
            assertNull(error.cause)
            assertFalse(error.toString().contains("private-api-key"))
        }
    }

    /** JVM 边界使用真实 JCE AES-GCM；设备测试单独验证 AndroidKeyStore Provider。 */
    private class TestCipher : ApiKeyCipher {
        var key: SecretKey? = null
        var encryptError: Exception? = null
        var deleteError: Exception? = null
        override fun encrypt(plaintext: String, associatedData: ByteArray): EncryptedApiKey {
            encryptError?.let { throw it }
            val secret = key ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secret)
            cipher.updateAAD(associatedData)
            return EncryptedApiKey(cipher.iv, cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)))
        }
        override fun decrypt(encrypted: EncryptedApiKey, associatedData: ByteArray): String {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, checkNotNull(key), GCMParameterSpec(128, encrypted.iv))
            cipher.updateAAD(associatedData)
            return cipher.doFinal(encrypted.ciphertext).toString(Charsets.UTF_8)
        }
        override fun deleteKey() { deleteError?.let { throw it }; key = null }
    }
}
