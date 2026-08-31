package com.monday8am.edgelab.core.storage

import android.content.Context
import androidx.datastore.core.Serializer
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.io.InputStream
import java.io.OutputStream

/** A DataStore Serializer that uses Google Tink (AEAD) to encrypt/decrypt auth tokens. */
class AuthTokenSerializer(private val aead: Aead) : Serializer<String?> {
    override val defaultValue: String? = null

    override suspend fun readFrom(input: InputStream): String? {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return null

        return try {
            val decryptedBytes = aead.decrypt(bytes, null)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun writeTo(t: String?, output: OutputStream) {
        if (t == null) {
            output.write(byteArrayOf())
            return
        }

        val encryptedBytes = aead.encrypt(t.toByteArray(Charsets.UTF_8), null)
        output.write(encryptedBytes)
    }

    companion object {
        private const val KEYSET_NAME = "hf_token_keyset"
        private const val PREFERENCE_FILE = "hf_token_key_prefs"
        private const val MASTER_KEY_URI = "android-keystore://hf_token_master_key"

        fun factory(context: Context): AuthTokenSerializer {
            AeadConfig.register()

            val keysetHandle =
                AndroidKeysetManager.Builder()
                    .withSharedPref(context, KEYSET_NAME, PREFERENCE_FILE)
                    .withKeyTemplate(
                        com.google.crypto.tink.aead.AesGcmKeyManager.aes256GcmTemplate()
                    )
                    .withMasterKeyUri(MASTER_KEY_URI)
                    .build()
                    .keysetHandle

            val aead = keysetHandle.getPrimitive(Aead::class.java)
            return AuthTokenSerializer(aead)
        }
    }
}
