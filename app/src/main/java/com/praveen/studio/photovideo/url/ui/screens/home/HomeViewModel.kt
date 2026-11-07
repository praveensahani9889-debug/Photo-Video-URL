package com.praveen.studio.photovideo.url.ui.screens.home

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.praveen.studio.photovideo.url.data.model.MediaCategory
import com.praveen.studio.photovideo.url.data.model.SelectedMediaItem
import com.praveen.studio.photovideo.url.data.model.UploadStatus
import com.praveen.studio.photovideo.url.data.model.UserDetails
import com.praveen.studio.photovideo.url.data.remote.SupabaseStorageClient
import com.praveen.studio.photovideo.url.domain.repository.UploadRepository
import com.praveen.studio.photovideo.url.domain.util.UploadLimits
import com.praveen.studio.photovideo.url.domain.util.ZipHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val uploadRepository = UploadRepository(application)

    /** True only when the user finished setting up THEIR OWN Supabase. */
    private val _isConfigured = MutableStateFlow(uploadRepository.isSupabaseConfigured())
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    val userDetails: StateFlow<UserDetails> = uploadRepository.observeUserDetails()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserDetails())

    val installationId: String = uploadRepository.installationId
    val deviceFingerprint: String get() = installationId

    private val _uploadStatus = MutableStateFlow<UploadStatus>(UploadStatus.Idle)
    val uploadStatus: StateFlow<UploadStatus> = _uploadStatus.asStateFlow()

    private val _stagedItems = MutableStateFlow<List<SelectedMediaItem>>(emptyList())
    val stagedItems: StateFlow<List<SelectedMediaItem>> = _stagedItems.asStateFlow()

    /** Files that are too big for the Supabase limit. The first one is shown in the "ZIP & Upload" dialog. */
    private val _largeFileQueue = MutableStateFlow<List<SelectedMediaItem>>(emptyList())
    val largeFileQueue: StateFlow<List<SelectedMediaItem>> = _largeFileQueue.asStateFlow()

    private var lastFailedItem: SelectedMediaItem? = null

    init {
        refreshConfigStatus()
        cleanupTempFiles()
    }

    fun refreshConfigStatus() {
        _isConfigured.value = uploadRepository.isSupabaseConfigured()
    }

    fun handleSelectedUris(uris: List<Uri>, category: MediaCategory) {
        val context = getApplication<Application>()
        val items = uris.mapNotNull { uri ->
            resolveMediaItem(context, uri, category)
        }
        stageAndUpload(items)
    }

    /** Free trial: one real photo from the gallery, uploaded to the developer's Supabase (max 3). */
    fun handleFreeTrialGalleryPhoto(uri: Uri) {
        val context = getApplication<Application>()
        val item = resolveMediaItem(context, uri, MediaCategory.IMAGES)?.copy(isTrial = true) ?: return
        _stagedItems.value = listOf(item)
        startBatchUpload(listOf(item))
    }

    fun handleExtractedApk(uri: Uri, fileName: String) {
        val context = getApplication<Application>()
        val item = resolveMediaItem(context, uri, MediaCategory.APKS, customFileName = fileName) ?: return
        stageAndUpload(listOf(item))
    }

    /** Small files upload now. Files over the Supabase limit wait for the user's choice. */
    private fun stageAndUpload(items: List<SelectedMediaItem>) {
        if (items.isEmpty()) return
        val (large, normal) = items.partition { it.isTooLarge() }
        if (large.isNotEmpty()) {
            _largeFileQueue.value = _largeFileQueue.value + large
        }
        if (normal.isNotEmpty()) {
            _stagedItems.value = normal
            startBatchUpload(normal)
        }
    }

    private fun SelectedMediaItem.isTooLarge(): Boolean =
        !isTrial && !skipSizePrompt && sizeBytes > UploadLimits.SUPABASE_FREE_MAX_BYTES

    private fun startBatchUpload(items: List<SelectedMediaItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            for ((index, item) in items.withIndex()) {
                lastFailedItem = item
                var rejectedAsTooLarge = false

                uploadRepository.uploadItem(item, itemIndex = index + 1, totalItems = items.size)
                    .collect { status ->
                        val isTooLargeError = status is UploadStatus.Failed &&
                            status.error.startsWith(SupabaseStorageClient.TOO_LARGE_PREFIX)
                        if (isTooLargeError && !item.skipSizePrompt && !item.isTrial) {
                            // Server says "too big": offer ZIP instead of a dead-end error.
                            rejectedAsTooLarge = true
                        } else {
                            _uploadStatus.value = status
                            if (status is UploadStatus.Success) deleteTempFile(item)
                        }
                    }

                if (rejectedAsTooLarge) {
                    _largeFileQueue.value = listOf(item) + _largeFileQueue.value
                    _uploadStatus.value = UploadStatus.Idle
                    break
                }

                // If failed, stop next items
                if (_uploadStatus.value is UploadStatus.Failed) {
                    break
                }
            }
        }
    }

    // ---- "File too large" dialog actions ----

    /** Make a ZIP of the first waiting file inside the app, then upload the ZIP. */
    fun zipAndUploadLarge() {
        val item = _largeFileQueue.value.firstOrNull() ?: return
        _largeFileQueue.value = _largeFileQueue.value.drop(1)
        lastFailedItem = item

        viewModelScope.launch {
            val app = getApplication<Application>()
            _uploadStatus.value = UploadStatus.Compressing(item.name, 0f)

            val result = ZipHelper.zipItem(app, item) { progress ->
                _uploadStatus.value = UploadStatus.Compressing(item.name, progress)
            }

            result.onSuccess { zip ->
                val zippedItem = SelectedMediaItem(
                    uri = Uri.fromFile(zip.file),
                    name = zip.zipName,
                    sizeBytes = zip.sizeBytes,
                    mimeType = "application/zip",
                    category = MediaCategory.FILES,
                    skipSizePrompt = true
                )
                _stagedItems.value = listOf(zippedItem)
                startBatchUpload(listOf(zippedItem))
            }.onFailure { error ->
                _uploadStatus.value = UploadStatus.Failed(
                    fileName = item.name,
                    error = "Could not create the ZIP: ${error.localizedMessage ?: "unknown error"}",
                    retryable = true
                )
            }
        }
    }

    /** Try the original file anyway (for users whose Supabase plan allows bigger files). */
    fun uploadLargeAsIs() {
        val item = _largeFileQueue.value.firstOrNull() ?: return
        _largeFileQueue.value = _largeFileQueue.value.drop(1)
        val forced = item.copy(skipSizePrompt = true)
        _stagedItems.value = listOf(forced)
        startBatchUpload(listOf(forced))
    }

    fun skipLarge() {
        _largeFileQueue.value = _largeFileQueue.value.drop(1)
    }

    fun retryLastUpload() {
        val item = lastFailedItem ?: return
        if (item.isTooLarge()) {
            // Big original file: ask again (ZIP / upload as is) instead of failing the same way
            _uploadStatus.value = UploadStatus.Idle
            _largeFileQueue.value = listOf(item) + _largeFileQueue.value
        } else {
            startBatchUpload(listOf(item))
        }
    }

    fun dismissProgress() {
        _uploadStatus.value = UploadStatus.Idle
        _stagedItems.value = emptyList()
    }

    // ---- temp files (ZIPs and extracted APKs live in the app cache) ----

    private fun cleanupTempFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val cache = getApplication<Application>().cacheDir
            File(cache, ZipHelper.ZIP_CACHE_DIR).deleteRecursively()
            File(cache, "extracted_apks").deleteRecursively()
        }
    }

    private fun deleteTempFile(item: SelectedMediaItem) {
        if (item.uri.scheme != "file") return
        val path = item.uri.path ?: return
        val cachePath = getApplication<Application>().cacheDir.absolutePath
        if (!path.startsWith(cachePath)) return
        viewModelScope.launch(Dispatchers.IO) {
            File(path).delete()
        }
    }

    private fun resolveMediaItem(
        context: Context,
        uri: Uri,
        category: MediaCategory,
        customFileName: String? = null
    ): SelectedMediaItem? {
        var name = customFileName ?: "file_${System.currentTimeMillis()}"
        var size = 0L
        val mimeType = context.contentResolver.getType(uri) ?: category.mimeTypeFilter

        if (customFileName == null) {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                        if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                    }
                }
            } catch (_: Exception) { }
        }

        if (size <= 0) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    size = pfd.statSize
                }
            } catch (_: Exception) { }
        }

        return SelectedMediaItem(
            uri = uri,
            name = name,
            sizeBytes = size,
            mimeType = mimeType,
            category = category
        )
    }
}
