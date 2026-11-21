package com.praveen.studio.photovideo.url.data.local

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.praveen.studio.photovideo.url.data.model.UploadRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** One upload in the safe copy. Only what the readable list needs: name, link and date. */
data class VaultEntry(
    val id: String,
    val name: String,
    val type: String,
    val url: String,
    val date: String,
    val timestamp: Long
)

/** The account that was saved on the phone (see [RecoveryStore.readIdentity]). */
data class SavedIdentity(val installationId: String, val deviceKey: String)

/**
 * Safe copies on the phone, in the folder  /storage/emulated/0/Photo video URL/  (inside it every folder and file
 * starts with a dot, so the gallery and the file apps do not show them):
 *
 *  1. The ACCOUNT FILE: the user ID of this phone. When the app is installed again and would start with a brand new
 *     account, the app reads this file, asks Firebase and continues with the old account (see IdentityManager).
 *  2. The UPLOAD LIST: every upload (name, link, date) as a ZIP that is encrypted. In Settings the user can turn it
 *     into a readable text file in the Downloads folder.
 *
 * Both files are encrypted with AES-256-GCM. The key is made from a secret inside the app and the hardware of the
 * phone (maker, model, board...), so another phone model cannot read a copied file, and for the app itself the file
 * is unreadable text. All functions are best effort: when the storage is not allowed, they quietly do nothing and the
 * app works exactly as before.
 */
object RecoveryStore {
    private const val ROOT_NAME = "Photo video URL"
    private val IDENTITY_PATH = listOf(".pvu_core", ".sys", ".id")
    private val VAULT_PATH = listOf(".pvu_core", ".vault", ".v1")
    private const val IDENTITY_FILE = ".pvu_identity.dat"
    private const val VAULT_FILE = ".pvu_uploads.dat"
    private const val APP_SECRET = "pvu-safe-copy-v1|praveen-studio|7f3a91c2e5d84b06"
    private val MAGIC = byteArrayOf('P'.code.toByte(), 'V'.code.toByte(), 'U'.code.toByte(), 1)
    private const val IV_SIZE = 12

    // ---------------------------------------------------------------------------------------------
    // Encryption
    // ---------------------------------------------------------------------------------------------

    /** Maker, model, board... of the phone. They stay the same after a re-install, an update and a new app signature. */
    private fun hardwareId(): String =
        listOf(
            Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.DEVICE, Build.BOARD, Build.HARDWARE,
            Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        ).joinToString("|") { it.orEmpty().trim().lowercase(Locale.ROOT) }

    private fun key(): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$APP_SECRET|${hardwareId()}".toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return MAGIC + iv + cipher.doFinal(plain)
    }

    /** The plain bytes, or null when the data is damaged or was made on another phone model. */
    private fun decrypt(data: ByteArray): ByteArray? {
        return try {
            if (data.size < MAGIC.size + IV_SIZE + 16) return null
            if (!data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return null
            val iv = data.copyOfRange(MAGIC.size, MAGIC.size + IV_SIZE)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            cipher.doFinal(data, MAGIC.size + IV_SIZE, data.size - MAGIC.size - IV_SIZE)
        } catch (_: Throwable) {
            null
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Files
    // ---------------------------------------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun rootDir(): File = File(Environment.getExternalStorageDirectory(), ROOT_NAME)

    private fun dir(path: List<String>, create: Boolean): File? {
        return try {
            var folder = rootDir()
            if (create && !folder.exists()) folder.mkdirs()
            path.forEach { folder = File(folder, it) }
            if (create) {
                if (!folder.exists() && !folder.mkdirs()) return null
                // keeps the gallery away from this folder
                val marker = File(File(rootDir(), path.first()), ".nomedia")
                if (!marker.exists()) marker.createNewFile()
            }
            folder.takeIf { it.isDirectory }
        } catch (_: Throwable) {
            null
        }
    }

    private fun readBytes(path: List<String>, name: String): ByteArray? {
        return try {
            val file = File(dir(path, create = false) ?: return null, name)
            if (file.isFile && file.canRead()) file.readBytes() else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun writeBytes(path: List<String>, name: String, data: ByteArray): Boolean {
        return try {
            val folder = dir(path, create = true) ?: return false
            val target = File(folder, name)
            val temp = File(folder, "$name.tmp")
            temp.writeBytes(data)
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                target.writeBytes(data)
                temp.delete()
            }
            target.isFile
        } catch (_: Throwable) {
            false
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Storage access
    // ---------------------------------------------------------------------------------------------

    /** True when the folder can be written right now (the permission was given and the write really works). */
    fun isReady(context: Context): Boolean {
        return try {
            val allowed = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> true // tested below
                else -> ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
            }
            if (!allowed) return false
            val folder = dir(IDENTITY_PATH, create = true) ?: return false
            folder.canWrite()
        } catch (_: Throwable) {
            false
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 1. The account file
    // ---------------------------------------------------------------------------------------------

    fun readIdentity(): SavedIdentity? {
        return try {
            val plain = decrypt(readBytes(IDENTITY_PATH, IDENTITY_FILE) ?: return null) ?: return null
            val json = JSONObject(String(plain, Charsets.UTF_8))
            val id = json.optString("id").trim()
            val deviceKey = json.optString("dk").trim()
            if (id.isEmpty() || deviceKey.isEmpty()) null else SavedIdentity(id, deviceKey)
        } catch (_: Throwable) {
            null
        }
    }

    /** Saves the account of this phone. Nothing is written when the file already has the same content. */
    fun saveIdentity(installationId: String, deviceKey: String): Boolean {
        return try {
            val current = readIdentity()
            if (current != null && current.installationId == installationId && current.deviceKey == deviceKey) return true
            val json = JSONObject()
                .put("v", 1)
                .put("id", installationId)
                .put("dk", deviceKey)
                .put("t", System.currentTimeMillis())
            writeBytes(IDENTITY_PATH, IDENTITY_FILE, encrypt(json.toString().toByteArray(Charsets.UTF_8)))
        } catch (_: Throwable) {
            false
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 2. The upload list (encrypted ZIP)
    // ---------------------------------------------------------------------------------------------

    /** null = a file exists but cannot be read (other phone model / damaged): it is never overwritten. */
    private fun loadVault(): MutableList<VaultEntry>? {
        val bytes = readBytes(VAULT_PATH, VAULT_FILE) ?: return mutableListOf()
        val zipBytes = decrypt(bytes) ?: return null
        return try {
            val list = mutableListOf<VaultEntry>()
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "uploads.json") {
                        val array = JSONArray(String(zip.readBytes(), Charsets.UTF_8))
                        for (i in 0 until array.length()) {
                            val o = array.optJSONObject(i) ?: continue
                            val id = o.optString("id")
                            if (id.isEmpty()) continue
                            list += VaultEntry(
                                id = id,
                                name = o.optString("name"),
                                type = o.optString("type"),
                                url = o.optString("url"),
                                date = o.optString("date"),
                                timestamp = o.optLong("ts")
                            )
                        }
                    }
                    entry = zip.nextEntry
                }
            }
            list
        } catch (_: Throwable) {
            null
        }
    }

    private fun storeVault(list: List<VaultEntry>): Boolean {
        return try {
            val array = JSONArray()
            list.forEach { e ->
                array.put(
                    JSONObject()
                        .put("id", e.id).put("name", e.name).put("type", e.type)
                        .put("url", e.url).put("date", e.date).put("ts", e.timestamp)
                )
            }
            val buffer = ByteArrayOutputStream()
            ZipOutputStream(buffer).use { zip ->
                zip.putNextEntry(ZipEntry("uploads.json"))
                zip.write(array.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            writeBytes(VAULT_PATH, VAULT_FILE, encrypt(buffer.toByteArray()))
        } catch (_: Throwable) {
            false
        }
    }

    private fun entryOf(record: UploadRecord): VaultEntry {
        val link = record.effectiveShortUrl.ifBlank { "https://ivu.gt.tc/${record.shortId}" }
        val stamp = record.createdAt.takeIf { it > 0 } ?: record.timestamp
        val date = "${record.uploadDate} ${record.uploadTime}".trim().ifEmpty {
            if (stamp > 0) SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(stamp)) else ""
        }
        return VaultEntry(
            id = record.shortId,
            name = record.fileName,
            type = record.effectiveType,
            url = link,
            date = date,
            timestamp = stamp
        )
    }

    /** A new upload (also used for a changed link name: the entry with the same ID is replaced). */
    @Synchronized
    fun addUpload(record: UploadRecord): Boolean = mergeUploads(listOf(record))

    /** Adds the uploads that are not in the safe copy yet (older uploads) and refreshes the others. */
    @Synchronized
    fun mergeUploads(records: List<UploadRecord>): Boolean {
        return try {
            val list = loadVault() ?: return false
            var changed = false
            records.filter { !it.isDemo && it.shortId.isNotBlank() }.forEach { record ->
                val entry = entryOf(record)
                val index = list.indexOfFirst { it.id == entry.id }
                if (index < 0) {
                    list += entry
                    changed = true
                } else if (list[index] != entry) {
                    list[index] = entry
                    changed = true
                }
            }
            if (!changed) true else storeVault(list)
        } catch (_: Throwable) {
            false
        }
    }

    /** The old name of a link was replaced by a new one. */
    @Synchronized
    fun renameUpload(oldId: String, record: UploadRecord): Boolean {
        return try {
            val list = loadVault() ?: return false
            list.removeAll { it.id == oldId }
            val entry = entryOf(record)
            list.removeAll { it.id == entry.id }
            list += entry
            storeVault(list)
        } catch (_: Throwable) {
            false
        }
    }

    @Synchronized
    fun removeUploads(ids: Collection<String>): Boolean {
        return try {
            if (ids.isEmpty()) return true
            val list = loadVault() ?: return false
            if (!list.removeAll { it.id in ids }) return true
            storeVault(list)
        } catch (_: Throwable) {
            false
        }
    }

    @Synchronized
    fun readUploads(): List<VaultEntry> = (loadVault() ?: emptyList()).sortedByDescending { it.timestamp }

    // ---------------------------------------------------------------------------------------------
    // Readable list for the user (Downloads folder)
    // ---------------------------------------------------------------------------------------------

    fun readableText(entries: List<VaultEntry>): String {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val text = StringBuilder()
        text.append("Photo Video URL - my uploads\n")
        text.append("Saved on: ").append(now).append('\n')
        text.append("Total: ").append(entries.size).append("\n\n")
        entries.forEachIndexed { index, e ->
            text.append(index + 1).append(". Name: ").append(e.name).append('\n')
            text.append("   URL: ").append(e.url).append('\n')
            text.append("   Date: ").append(e.date.ifEmpty { "-" }).append("\n\n")
        }
        return text.toString()
    }

    /**
     * Saves the readable list as a text file in the Downloads folder. Android 10+ needs no permission; on Android 9
     * the storage permission is needed (the caller asks for it first). Returns the file name.
     */
    fun exportReadable(context: Context, entries: List<VaultEntry>): Result<String> {
        return try {
            val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
            val fileName = "Photo-Video-URL-uploads-$stamp.txt"
            val bytes = readableText(entries).toByteArray(Charsets.UTF_8)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return Result.failure(IllegalStateException("The Downloads folder is not available."))
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: return Result.failure(IllegalStateException("The file could not be written."))
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloads.exists()) downloads.mkdirs()
                File(downloads, fileName).writeBytes(bytes)
            }
            Result.success(fileName)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
