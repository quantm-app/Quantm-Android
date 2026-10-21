package app.quantm.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Auth(val token: String, val clientId: String)

/**
 * The SoundCloud login. The token is encrypted with a key that never leaves the Android
 * Keystore, so it can't be read from a backup or another app.
 */
class AuthStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val _auth = MutableStateFlow(load())
    val auth: StateFlow<Auth?> = _auth.asStateFlow()

    val current: Auth? get() = _auth.value

    fun save(token: String, clientId: String) {
        prefs.edit()
            .putString("token", encrypt(token))
            .putString("clientId", clientId)
            .apply()
        _auth.value = Auth(token, clientId)
    }

    fun updateClientId(clientId: String) {
        val auth = _auth.value ?: return
        prefs.edit().putString("clientId", clientId).apply()
        _auth.value = auth.copy(clientId = clientId)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _auth.value = null
    }

    private fun load(): Auth? {
        val stored = prefs.getString("token", null) ?: return null
        val clientId = prefs.getString("clientId", null) ?: return null
        val token = runCatching { decrypt(stored) }.getOrNull() ?: return null
        return Auth(token, clientId)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.iv + cipher.doFinal(value.toByteArray())
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val data = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, 12))
        return String(cipher.doFinal(data, 12, data.size - 12))
    }

    private companion object {
        const val KEY_ALIAS = "quantm-auth"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
