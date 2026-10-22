package dev.arkava.vpn.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.arkava.vpn.core.ConnectionProfile
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small encrypted profile store using an Android Keystore AES key.
 *
 * Profile data and credentials are encrypted before they reach SharedPreferences.
 * This is intentionally synchronous for a small profile collection; call from IO
 * dispatchers if integrating it into a larger UI flow.
 */
class SecureProfileStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun loadAll(): List<ConnectionProfile> {
        val encoded = preferences.getString(PROFILES_KEY, null) ?: return emptyList()
        val plaintext = decrypt(decode(encoded))
        val array = JSONArray(plaintext)
        require(array.length() <= MAX_PROFILES) { "Stored profile count exceeds limit" }
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    ConnectionProfile(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        engineId = item.getString("engineId"),
                        configJson = item.getString("configJson")
                    )
                )
            }
        }
    }

    @Synchronized
    fun save(profile: ConnectionProfile) {
        validate(profile)
        val profiles = loadAll().filterNot { it.id == profile.id } + profile
        require(profiles.size <= MAX_PROFILES) { "Too many profiles" }
        persist(profiles)
    }

    @Synchronized
    fun delete(profileId: String) {
        require(profileId.isNotBlank()) { "Profile ID is required" }
        persist(loadAll().filterNot { it.id == profileId })
    }

    @Synchronized
    fun replaceAll(profiles: List<ConnectionProfile>) {
        require(profiles.size <= MAX_PROFILES) { "Too many profiles" }
        require(profiles.map { it.id }.distinct().size == profiles.size) { "Duplicate profile IDs" }
        profiles.forEach(::validate)
        persist(profiles)
    }

    private fun persist(profiles: List<ConnectionProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("engineId", profile.engineId)
                    .put("configJson", profile.configJson)
            )
        }
        val encrypted = encrypt(array.toString())
        check(preferences.edit().putString(PROFILES_KEY, encode(encrypted)).commit()) {
            "Could not persist encrypted profiles"
        }
    }

    private fun validate(profile: ConnectionProfile) {
        require(profile.id.isNotBlank() && profile.id.length <= 128) { "Invalid profile ID" }
        require(profile.name.isNotBlank() && profile.name.length <= 160) { "Invalid profile name" }
        require(profile.engineId.isNotBlank() && profile.engineId.length <= 80) { "Invalid engine ID" }
        require(profile.configJson.isNotBlank() && profile.configJson.toByteArray(Charsets.UTF_8).size <= MAX_CONFIG_BYTES) {
            "Profile configuration is empty or too large"
        }
    }

    private fun encrypt(plaintext: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return byteArrayOf(iv.size.toByte()) + iv + encrypted
    }

    private fun decrypt(payload: ByteArray): String {
        require(payload.isNotEmpty()) { "Encrypted profile data is empty" }
        val ivSize = payload[0].toInt() and 0xff
        require(ivSize == GCM_IV_BYTES && payload.size > 1 + ivSize) { "Encrypted profile data is malformed" }
        val iv = payload.copyOfRange(1, 1 + ivSize)
        val ciphertext = payload.copyOfRange(1 + ivSize, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "dev.arkava.vpn.profiles.v1"
        private const val PREFERENCES_NAME = "arkava_encrypted_profiles"
        private const val PROFILES_KEY = "encrypted_profiles_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val MAX_PROFILES = 500
        private const val MAX_CONFIG_BYTES = 1_000_000
    }
}
