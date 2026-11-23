package com.totemoflol.tottools

import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class LicenseSession(
    val licenseId: String,
    val expiresAt: String?
)

internal class LicenseException(message: String, val code: String? = null) : Exception(message)

internal object LicenseKeyVault {
    private const val PREFS = "tot_tools_license"
    private const val KEY_CIPHER = "key_cipher"
    private const val KEY_IV = "key_iv"
    private const val KEY_ALIAS = "tot_tools_license_key_v1"

    fun store(context: Context, licenseKey: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(licenseKey.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CIPHER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun restore(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(KEY_CIPHER, null) ?: return null
        val iv = prefs.getString(KEY_IV, null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            clear(context)
            null
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}

internal object LicenseApi {
    fun activate(context: Context, licenseKey: String): LicenseSession {
        val baseUrl = BuildConfig.LICENSE_API_URL.trim().trimEnd('/')
        if (baseUrl.isBlank() || baseUrl.contains("YOUR_SUBDOMAIN")) {
            throw LicenseException("The licence server has not been configured in this build.")
        }

        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
            ?: throw LicenseException("Could not identify this device.")

        val deviceId = sha256("$androidId:${context.packageName}")
        val requestBody = JSONObject()
            .put("key", licenseKey.trim().uppercase())
            .put("deviceId", deviceId)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .toString()

        val connection = (URL("$baseUrl/api/v1/activate").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12000
            readTimeout = 12000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val payload = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val response = runCatching { JSONObject(payload) }.getOrNull()
                ?: throw LicenseException("The licence server returned an invalid response.")

            if (responseCode !in 200..299 || !response.optBoolean("ok", false)) {
                val code = response.optString("code").takeIf { it.isNotBlank() }
                val message = response.optString("message").ifBlank { "Licence verification failed." }
                throw LicenseException(message, code)
            }

            if (response.optString("plan") != "paid" || !response.optBoolean("deviceBound", false)) {
                throw LicenseException("The server did not confirm an active device licence.")
            }

            val id = response.optString("licenseId").takeIf { it.isNotBlank() }
                ?: throw LicenseException("The licence server returned incomplete information.")
            return LicenseSession(
                licenseId = id,
                expiresAt = response.optString("expiresAt").takeIf { it.isNotBlank() && it != "null" }
            )
        } catch (e: LicenseException) {
            throw e
        } catch (_: Exception) {
            throw LicenseException("Couldn't reach the licence server. Check your internet connection and try again.")
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
