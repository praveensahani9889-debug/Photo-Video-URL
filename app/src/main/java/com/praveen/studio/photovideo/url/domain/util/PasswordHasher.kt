package com.praveen.studio.photovideo.url.domain.util

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Password of a short link. The password itself is never saved: Firebase gets a PBKDF2-HMAC-SHA256 hash (the
 * website checks a typed password the same way):
 *   hash = PBKDF2-HMAC-SHA256(password as UTF-8 bytes, salt = bytes of the hex salt, iterations, 256 bits) as hex
 * PHP:        hash_pbkdf2('sha256', $password, hex2bin($salt), $iter, 64, false)
 * JavaScript: crypto.subtle.deriveBits({name:'PBKDF2', hash:'SHA-256', salt, iterations}, key, 256)
 */
object PasswordHasher {
    const val ITERATIONS = 10_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    data class Hashed(val salt: String, val hash: String, val iterations: Int)

    fun hash(password: String, saltHex: String? = null, iterations: Int = ITERATIONS): Hashed {
        val salt = saltHex?.let { fromHex(it) } ?: ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        return Hashed(toHex(salt), toHex(derive(password, salt, iterations)), iterations)
    }

    fun verify(password: String, saltHex: String, hashHex: String, iterations: Int): Boolean {
        if (saltHex.isBlank() || hashHex.isBlank() || iterations <= 0) return false
        return try {
            val expected = fromHex(hashHex)
            val actual = derive(password, fromHex(saltHex), iterations)
            MessageDigest.isEqual(expected, actual)
        } catch (_: Exception) {
            false
        }
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format("%02x", b.toInt() and 0xff))
        return sb.toString()
    }

    private fun fromHex(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "bad hex" }
        return ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
