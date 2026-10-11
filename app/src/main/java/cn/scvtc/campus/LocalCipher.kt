package cn.scvtc.campus

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps legacy ciphertext readable. A failed key is retired only for new writes,
 * and is never deleted with the user's cached data. */
class LocalCipher(context: Context, private val legacyAlias: String) {
    class Unavailable(cause: Throwable? = null) :
        IllegalStateException("本机加密记录暂时无法读取，请重新连接学校；已有课程保留", cause)

    private val prefs = context.applicationContext.getSharedPreferences("scvtc-key-versions", Context.MODE_PRIVATE)

    companion object {
        private val LOCK = Any()
        private const val PREFIX = "keystore:v2:"
    }

    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun obtain(alias: String, create: Boolean): SecretKey {
        val keystore = store()
        (keystore.getKey(alias, null) as? SecretKey)?.let { return it }
        if (!create) throw Unavailable()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build())
            generateKey()
        }
    }

    private fun invalidated(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.take(8).any {
            it is KeyPermanentlyInvalidatedException || it is UnrecoverableKeyException
        }

    fun seal(text: String, scope: String? = null): String = synchronized(LOCK) {
        var alias = prefs.getString(legacyAlias, legacyAlias) ?: legacyAlias
        fun encrypt(): String {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, obtain(alias, create = true))
            scope?.let { cipher.updateAAD(it.toByteArray(Charsets.UTF_8)) }
            val bytes = cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
            return PREFIX + alias + ":" + Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
        try {
            encrypt()
        } catch (error: Exception) {
            if (!invalidated(error)) throw error
            // One recovery, with a different alias: old ciphertext/keys stay intact.
            alias = legacyAlias + ".generation." + UUID.randomUUID()
            val value = encrypt()
            check(prefs.edit().putString(legacyAlias, alias).commit()) { "未能保存本机加密配置，请重试" }
            value
        }
    }

    fun open(text: String, scope: String? = null): String = synchronized(LOCK) {
        val alias: String
        val encoded: String
        if (text.startsWith(PREFIX)) {
            val envelope = text.removePrefix(PREFIX)
            alias = envelope.substringBefore(':')
            require(alias == legacyAlias || alias.startsWith(legacyAlias + ".generation.")) { "加密记录不属于当前存储" }
            encoded = envelope.substringAfter(':', "")
        } else {
            alias = legacyAlias
            encoded = text
        }
        try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size > 28) { "加密记录不完整" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, obtain(alias, create = false), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            scope?.let { cipher.updateAAD(it.toByteArray(Charsets.UTF_8)) }
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (error: Exception) {
            if (invalidated(error) || error is AEADBadTagException) throw Unavailable(error)
            throw error
        }
    }
}

