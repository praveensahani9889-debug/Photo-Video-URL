package com.praveen.studio.photovideo.url.data.local

import android.util.Base64
import com.praveen.studio.photovideo.url.data.model.SupabaseAccount
import com.praveen.studio.photovideo.url.data.model.SupabaseConfig
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts the user's Supabase settings before they are backed up in Firebase, so nobody who can read the
 * database sees the URL / key. The key is derived from the phone's Android ID, which never leaves the phone, so only
 * this phone can open the backup again (after a re-install or "clear data").
 */
object ConfigBackupCodec {
    private const val PREFIX = "v1:"
    private const val PREFIX_ACCOUNTS = "v2:"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun encrypt(androidId: String, config: SupabaseConfig): String {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyFrom(androidId), "AES"), GCMParameterSpec(TAG_BITS, iv))
        val json = JSONObject()
            .put("u", config.projectUrl)
            .put("k", config.anonKey)
            .put("b", config.bucketName)
            .toString()
        val encrypted = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    /** Returns null when the backup is damaged or was made on another phone. */
    fun decrypt(androidId: String, blob: String): SupabaseConfig? {
        return try {
            if (!blob.startsWith(PREFIX)) return null
            val raw = Base64.decode(blob.removePrefix(PREFIX), Base64.NO_WRAP)
            if (raw.size <= IV_BYTES) return null
            val iv = raw.copyOfRange(0, IV_BYTES)
            val data = raw.copyOfRange(IV_BYTES, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyFrom(androidId), "AES"), GCMParameterSpec(TAG_BITS, iv))
            val json = JSONObject(String(cipher.doFinal(data), Charsets.UTF_8))
            SupabaseConfig(
                projectUrl = json.optString("u"),
                anonKey = json.optString("k"),
                bucketName = json.optString("b").ifBlank { "media" }
            )
        } catch (_: Throwable) {
            null
        }
    }

    /** Backup of all Supabase projects (up to 5) and which one is active. */
    fun encryptAccounts(androidId: String, accounts: List<SupabaseAccount>, activeId: String): String {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyFrom(androidId), "AES"), GCMParameterSpec(TAG_BITS, iv))
        val array = JSONArray()
        accounts.forEach { array.put(it.toBackupJson()) }
        val json = JSONObject().put("a", array).put("x", activeId).toString()
        val encrypted = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        return PREFIX_ACCOUNTS + Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    /**
     * Reads a backup of any version: "v2:" (all projects) or the older "v1:" (one project, which becomes the first
     * account). Returns null when the backup is damaged or was made on another phone.
     */
    fun decryptAccounts(androidId: String, blob: String): Pair<List<SupabaseAccount>, String>? {
        if (blob.startsWith(PREFIX)) {
            val single = decrypt(androidId, blob) ?: return null
            val account = SupabaseAccount(
                id = SupabaseAccount.newId(),
                projectUrl = single.projectUrl,
                anonKey = single.anonKey,
                bucketName = single.bucketName,
                createdAt = System.currentTimeMillis()
            )
            return listOf(account) to account.id
        }
        return try {
            if (!blob.startsWith(PREFIX_ACCOUNTS)) return null
            val raw = Base64.decode(blob.removePrefix(PREFIX_ACCOUNTS), Base64.NO_WRAP)
            if (raw.size <= IV_BYTES) return null
            val iv = raw.copyOfRange(0, IV_BYTES)
            val data = raw.copyOfRange(IV_BYTES, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyFrom(androidId), "AES"), GCMParameterSpec(TAG_BITS, iv))
            val json = JSONObject(String(cipher.doFinal(data), Charsets.UTF_8))
            val array = json.optJSONArray("a") ?: JSONArray()
            val accounts = (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { SupabaseAccount.fromJson(it) }
            }
            accounts to json.optString("x")
        } catch (_: Throwable) {
            null
        }
    }

    private fun keyFrom(androidId: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest("ivu-cfg-v1|$androidId".toByteArray(Charsets.UTF_8))
}
