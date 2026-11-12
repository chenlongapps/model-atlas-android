package io.github.chenlongapps.modelatlas.ai.config

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

internal class EncryptedApiKey(val iv: ByteArray, val ciphertext: ByteArray) {
    override fun toString() = "EncryptedApiKey([redacted])"
}

internal interface ApiKeyCipher {
    fun encrypt(plaintext: String, associatedData: ByteArray): EncryptedApiKey
    fun decrypt(encrypted: EncryptedApiKey, associatedData: ByteArray): String
    fun deleteKey()
}

/** 每次操作使用独立 Cipher；IV 由 AndroidKeyStore 随机生成，不导出 AES 密钥。 */
@Singleton
internal class KeystoreApiKeyCipher internal constructor(private val alias: String) : ApiKeyCipher {
    @Inject constructor() : this("modelatlas.ai.api-key.v1")
    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized
    private fun key(create: Boolean): SecretKey {
        val store = keyStore()
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        // 读取密文时绝不生成替代密钥，避免掩盖密钥丢失。
        check(create) { "Encryption key unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build())
        }.generateKey()
    }

    override fun encrypt(plaintext: String, associatedData: ByteArray): EncryptedApiKey {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
        cipher.updateAAD(associatedData)
        val bytes = plaintext.toByteArray(Charsets.UTF_8)
        return try { EncryptedApiKey(cipher.iv, cipher.doFinal(bytes)) } finally { bytes.fill(0) }
    }

    override fun decrypt(encrypted: EncryptedApiKey, associatedData: ByteArray): String {
        require(encrypted.iv.size == 12 && encrypted.ciphertext.size >= 16)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, encrypted.iv))
        cipher.updateAAD(associatedData)
        val bytes = cipher.doFinal(encrypted.ciphertext)
        return try { bytes.toString(Charsets.UTF_8) } finally { bytes.fill(0) }
    }

    @Synchronized
    override fun deleteKey() { keyStore().deleteEntry(alias) }
}
