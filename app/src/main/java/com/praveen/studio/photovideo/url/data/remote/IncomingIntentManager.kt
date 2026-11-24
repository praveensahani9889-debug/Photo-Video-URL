package com.praveen.studio.photovideo.url.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.praveen.studio.photovideo.url.data.model.IncomingSharedMedia
import com.praveen.studio.photovideo.url.data.model.MediaCategory
import com.praveen.studio.photovideo.url.domain.util.LinkRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object IncomingIntentManager {
    private const val TAG = "IncomingIntentManager"

    private val _sharedMedia = MutableStateFlow<IncomingSharedMedia?>(null)
    val sharedMedia: StateFlow<IncomingSharedMedia?> = _sharedMedia.asStateFlow()

    private val _deepLinkShortId = MutableStateFlow<String?>(null)
    val deepLinkShortId: StateFlow<String?> = _deepLinkShortId.asStateFlow()

    fun handleIntent(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val data = intent.data

        Log.d(TAG, "Incoming intent action: $action, data: $data, type: ${intent.type}")

        // 1. Deep link for ivu.gt.tc. Every address of the website is offered to the app:
        //    - a link (image_..., video_..., text_... or a custom address such as /praveen) opens in the viewer;
        //    - the pages of the website (/about, /privacy ...) are handed to the browser again.
        if (Intent.ACTION_VIEW == action && data != null) {
            val host = data.host.orEmpty().lowercase()
            if (host == "ivu.gt.tc" || host.endsWith(".ivu.gt.tc")) {
                val shortId = LinkRules.viewerIdOfPath(data.path)
                    ?: LinkRules.parseShortId(data.fragment)
                if (shortId != null) {
                    Log.d(TAG, "Deep link detected with shortId: $shortId")
                    _deepLinkShortId.value = shortId
                } else {
                    openInBrowser(context, data)
                }
                return
            }
        }

        // 2. Check Share Target: ACTION_SEND (Single file/media)
        if (Intent.ACTION_SEND == action) {
            val uri: Uri? = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri

            if (uri != null) {
                val mimeType = intent.type ?: context.contentResolver.getType(uri)
                val fileName = queryDisplayName(context, uri)
                val category = MediaCategory.fromMimeAndExtension(mimeType, fileName)
                val displayName = fileName ?: "Shared ${category.title.removeSuffix("s")}"

                Log.d(TAG, "Shared single item detected: $displayName, category: $category")
                _sharedMedia.value = IncomingSharedMedia(
                    uris = listOf(uri),
                    detectedCategory = category,
                    previewName = displayName,
                    totalCount = 1
                )
            }
            return
        }

        // 3. Check Share Target: ACTION_SEND_MULTIPLE (Multiple files/media)
        if (Intent.ACTION_SEND_MULTIPLE == action) {
            val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            val clipUris = mutableListOf<Uri>()

            if (!uris.isNullOrEmpty()) {
                clipUris.addAll(uris)
            } else {
                val clipData = intent.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        clipData.getItemAt(i).uri?.let { clipUris.add(it) }
                    }
                }
            }

            if (clipUris.isNotEmpty()) {
                // A share can mix photos, videos and files: the upload uses each file's own category,
                // the dialog only needs a label.
                val categories = clipUris.map { uri ->
                    val mime = try {
                        context.contentResolver.getType(uri)
                    } catch (_: Exception) {
                        null
                    } ?: intent.type
                    MediaCategory.fromMimeAndExtension(mime, uri.lastPathSegment)
                }.distinct()
                val category = categories.singleOrNull() ?: MediaCategory.FILES
                val previewTitle = if (categories.size == 1) {
                    "${clipUris.size} ${category.title} shared"
                } else {
                    "${clipUris.size} files shared"
                }

                Log.d(TAG, "Shared multiple items detected: count=${clipUris.size}, category=$category")
                _sharedMedia.value = IncomingSharedMedia(
                    uris = clipUris,
                    detectedCategory = category,
                    previewName = previewTitle,
                    totalCount = clipUris.size
                )
            }
        }
    }

    fun clearSharedMedia() {
        _sharedMedia.value = null
    }

    /** Search (a password protected link or a text post): the full page viewer opens, like the website. */
    fun openViewer(shortId: String) {
        _deepLinkShortId.value = shortId
    }

    /** Gives an address back to the browser (the system browser, never this app again). */
    private fun openInBrowser(context: Context, uri: Uri) {
        try {
            val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER).apply {
                data = uri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (t: Throwable) {
            Log.w(TAG, "No browser for $uri: ${t.message}")
        }
    }

    fun clearDeepLink() {
        _deepLinkShortId.value = null
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }
}
