package com.example.feedlite.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 基于 AndroidKeyStore 的加密偏好存储（API 23+，minSdk 26 满足）。
 *
 * 用于翻译 API Key：AES-256/GCM 密钥保存在系统 Keystore（不可导出），
 * 密文以 JSON（iv + ct）落盘。相比明文 SharedPreferences 是实质性提升，
 * 且零第三方依赖。注意：密文只能在「当前设备、当前用户」下解密，
 * 换机/刷机后需重新输入。
 */
class SecurePrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("feedlite_secure", Context.MODE_PRIVATE)

    fun putString(key: String, value: String) {
        if (value.isEmpty()) {
            prefs.edit().remove(key).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = JSONObject()
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("ct", Base64.encodeToString(ct, Base64.NO_WRAP))
            .toString()
        prefs.edit().putString(key, payload).apply()
    }

    fun getString(key: String): String? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            val o = JSONObject(raw)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(
                GCM_TAG_BITS,
                Base64.decode(o.getString("iv"), Base64.NO_WRAP),
            )
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
            cipher.doFinal(Base64.decode(o.getString("ct"), Base64.NO_WRAP))
                .toString(Charsets.UTF_8)
        } catch (e: Exception) {
            // 密钥失效（刷机/换机）或数据损坏 → 视为未配置，清掉重来
            prefs.edit().remove(key).apply()
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: run {
            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore",
            )
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generator.generateKey()
        }
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    companion object {
        private const val KEY_ALIAS = "feedlite_translation_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
