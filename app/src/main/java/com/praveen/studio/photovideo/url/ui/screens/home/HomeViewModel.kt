package com.praveen.studio.photovideo.url.ui.screens.home

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.praveen.studio.photovideo.url.data.model.MediaCategory
import com.praveen.studio.photovideo.url.data.model.SelectedMediaItem
import com.praveen.studio.photovideo.url.data.model.TextPostData
import com.praveen.studio.photovideo.url.domain.util.WebsiteBuilder
import com.praveen.studio.photovideo.url.domain.util.WebsiteDraft
import com.praveen.studio.photovideo.url.data.model.UploadRecord
import com.praveen.studio.photovideo.url.data.model.UploadStatus
import com.praveen.studio.photovideo.url.data.model.UserDetails
import com.praveen.studio.photovideo.url.data.remote.SupabaseStorageClient
import com.praveen.studio.photovideo.url.domain.repository.UploadRepository
import com.praveen.studio.photovideo.url.domain.util.LinkRules
import com.praveen.studio.photovideo.url.domain.util.UploadLimits
import com.praveen.studio.photovideo.url.domain.util.ZipHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

    /** True when the free demo photo that runs / just finished is the very FIRST one: no ad and the options page opens. */
    private val _firstDemoUpload = MutableStateFlow(false)
    val firstDemoUpload: StateFlow<Boolean> = _firstDemoUpload.asStateFlow()

    private val _stagedItems = MutableStateFlow<List<SelectedMediaItem>>(emptyList())
    val stagedItems: StateFlow<List<SelectedMediaItem>> = _stagedItems.asStateFlow()

    /** Files that are too big for the Supabase limit. The first one is shown in the "ZIP & Upload" dialog. */
    private val _largeFileQueue = MutableStateFlow<List<SelectedMediaItem>>(emptyList())
    val largeFileQueue: StateFlow<List<SelectedMediaItem>> = _largeFileQueue.asStateFlow()

    private var lastFailedItem: SelectedMediaItem? = null

    /** Files that were still waiting when an earlier file of the same batch failed (continued on Retry). */
    private var itemsAfterFailure: List<SelectedMediaItem> = emptyList()

    /** The text post that waits for the ad, the Advanced options and Publish (nothing is saved before Publish). */
    private val _textDraft = MutableStateFlow<UploadRecord?>(null)
    val textDraft: StateFlow<UploadRecord?> = _textDraft.asStateFlow()

    // ---- Website publish ----
    /** A website that was read (zip / html / terminal) and waits for the name + Publish. */
    private val _websiteDraft = MutableStateFlow<WebsiteDraft?>(null)
    val websiteDraft: StateFlow<WebsiteDraft?> = _websiteDraft.asStateFlow()

    /** True while a ZIP is opened in the background. */
    private val _websiteBusy = MutableStateFlow(false)
    val websiteBusy: StateFlow<Boolean> = _websiteBusy.asStateFlow()

    private val _websiteError = MutableStateFlow<String?>(null)
    val websiteError: StateFlow<String?> = _websiteError.asStateFlow()

    /** The website of a failed upload: "Retry" uploads it again. */
    private var websiteToRetry: WebsiteDraft? = null

    /** The text post of a failed Publish: "Retry" publishes it again. */
    private var textToRetry: UploadRecord? = null

    /** Only one batch uploads at a time, so two batches never fight over the progress dialog. */
    private val uploadMutex = Mutex()

    init {
        refreshConfigStatus()
        cleanupTempFiles()
        // The counters are saved on the phone; Firebase is only asked when they are stale (no live listener)
        viewModelScope.launch { uploadRepository.refreshUserDetails(false) }
        // Links that ran out of time (Advanced options > Expiry) are deleted from Supabase and Firebase.
        // Uses the history saved on the phone, so nothing is requested when nothing has expired.
        viewModelScope.launch(Dispatchers.IO) {
            com.praveen.studio.photovideo.url.domain.repository.HistoryRepository(getApplication()).purgeExpired()
        }
    }

    fun refreshConfigStatus() {
        _isConfigured.value = uploadRepository.isSupabaseConfigured()
    }

    fun handleSelectedUris(uris: List<Uri>, category: MediaCategory) {
        resolveAndStage(uris, category)
    }

    /**
     * Files received from another app's "Share" menu. A share can mix photos, videos and files,
     * so every file gets its own category (a video must not end up as an "image_" link).
     */
    fun handleSharedUris(uris: List<Uri>) {
        resolveAndStage(uris, null)
    }

    private fun resolveAndStage(
        uris: List<Uri>,
        fixedCategory: MediaCategory?,
        customFileName: String? = null,
        mimeOverride: String? = null
    ) {
        if (uris.isEmpty()) return
        val context = getApplication<Application>()
        viewModelScope.launch {
            // Name / size lookups can block on cloud-backed providers: keep them off the main thread.
            val items = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> resolveMediaItem(context, uri, fixedCategory, customFileName, mimeOverride) }
            }
            stageAndUpload(items)
        }
    }

    /** Free trial: one real photo from the gallery, uploaded to the developer's Supabase (max 3). */
    fun handleFreeTrialGalleryPhoto(uri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val item = withContext(Dispatchers.IO) {
                resolveMediaItem(context, uri, MediaCategory.IMAGES, null, null)
            }?.copy(isTrial = true) ?: return@launch
            // counted before the upload: from the second demo photo on, the ad shows as before
            _firstDemoUpload.value = userDetails.value.freeTierUploadsUsed <= 0L
            _stagedItems.value = listOf(item)
            startBatchUpload(listOf(item))
        }
    }

    fun handleExtractedApk(uri: Uri, fileName: String) {
        // Apps made of split APKs are exported as ".apks" (one zip with all parts). The website treats that as a
        // normal file download, a single ".apk" stays an APK link.
        val bundle = fileName.endsWith(".apks", ignoreCase = true)
        resolveAndStage(
            uris = listOf(uri),
            fixedCategory = if (bundle) MediaCategory.FILES else MediaCategory.APKS,
            customFileName = fileName,
            mimeOverride = if (bundle) "application/zip" else null
        )
    }

    /** Small files upload now. Files over the Supabase limit wait for the user's choice. */
    private fun stageAndUpload(items: List<SelectedMediaItem>) {
        if (items.isEmpty()) return
        _firstDemoUpload.value = false
        val (large, normal) = items.partition { it.isTooLarge() }
        if (large.isNotEmpty()) {
            _largeFileQueue.update { it + large }
        }
        if (normal.isNotEmpty()) {
            _stagedItems.value = normal
            startBatchUpload(normal)
        }
    }

    private fun SelectedMediaItem.isTooLarge(): Boolean =
        !isTrial && !skipSizePrompt && sizeBytes > UploadLimits.SUPABASE_FREE_MAX_BYTES

    // ---- Website publish: file (index.html / zip) or Terminal -> review -> upload -> ad -> Custom hosting ----

    private fun displayNameOf(uri: Uri): String {
        val app = getApplication<Application>()
        return try {
            app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        } catch (_: Throwable) {
            null
        } ?: (uri.lastPathSegment ?: "website")
    }

    /** The user picked index.html or a ZIP: it is read in the background. */
    fun loadWebsiteFile(uri: Uri) {
        val app = getApplication<Application>()
        _websiteBusy.value = true
        _websiteError.value = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                WebsiteBuilder.fromUri(app, uri, displayNameOf(uri), app.contentResolver.getType(uri).orEmpty())
            }
            _websiteBusy.value = false
            result.fold(
                onSuccess = { _websiteDraft.value = it },
                onFailure = { _websiteError.value = it.message ?: "The website could not be read." }
            )
        }
    }

    /** The files written in the Terminal. */
    fun loadWebsiteTexts(texts: Map<String, String>) {
        val app = getApplication<Application>()
        _websiteBusy.value = true
        _websiteError.value = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { WebsiteBuilder.fromTexts(app, "My website", texts) }
            _websiteBusy.value = false
            result.fold(
                onSuccess = { _websiteDraft.value = it },
                onFailure = { _websiteError.value = it.message ?: "The website could not be prepared." }
            )
        }
    }

    fun clearWebsiteError() {
        _websiteError.value = null
    }

    /** Cancel on the review page: the prepared files are removed. */
    fun discardWebsite() {
        _websiteDraft.value = null
        WebsiteBuilder.clean(getApplication<Application>())
    }

    /** "Publish" on the review page: the files go to the user's Supabase, then the link is saved. */
    fun confirmWebsite(name: String) {
        val draft = _websiteDraft.value ?: return
        _websiteDraft.value = null
        startWebsiteUpload(draft.copy(name = name))
    }

    private fun startWebsiteUpload(draft: WebsiteDraft) {
        textToRetry = null
        websiteToRetry = draft
        viewModelScope.launch {
            uploadMutex.withLock {
                uploadRepository.uploadWebsite(draft).collect { status ->
                    _uploadStatus.value = status
                    if (status is UploadStatus.Success) websiteToRetry = null
                }
            }
        }
    }

    // ---- Text post (database only) ----

    /** The editor is finished: the post gets its address; the ad and the Advanced options follow. */
    fun startTextPost(post: TextPostData) {
        textToRetry = null
        _uploadStatus.value = UploadStatus.Preparing(post.topic.ifBlank { "Text post" })
        viewModelScope.launch {
            _textDraft.value = withContext(Dispatchers.IO) { uploadRepository.createTextDraft(post) }
        }
    }

    /** Back from the Advanced options page: nothing was published, the editor opens again. */
    fun cancelTextPost() {
        _textDraft.value = null
        textToRetry = null
        _uploadStatus.value = UploadStatus.Idle
    }

    /** "Publish": the post is saved in the database and the result page opens. */
    fun publishTextPost(record: UploadRecord) {
        _textDraft.value = null
        textToRetry = record
        viewModelScope.launch {
            uploadMutex.withLock {
                uploadRepository.publishTextPost(record).collect { status ->
                    _uploadStatus.value = status
                    if (status is UploadStatus.Success) textToRetry = null
                }
            }
        }
    }

    private fun startBatchUpload(items: List<SelectedMediaItem>) {
        if (items.isEmpty()) return
        textToRetry = null
        websiteToRetry = null
        viewModelScope.launch {
            uploadMutex.withLock { runBatch(items) }
        }
    }

    private suspend fun runBatch(items: List<SelectedMediaItem>) {
        itemsAfterFailure = emptyList()
        var anyRejectedAsTooLarge = false

        for ((index, item) in items.withIndex()) {
            lastFailedItem = item
            var rejectedAsTooLarge = false
            var failed = false

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
                        if (status is UploadStatus.Failed) failed = true
                    }
                }

            if (rejectedAsTooLarge) {
                _largeFileQueue.update { listOf(item) + it }
                anyRejectedAsTooLarge = true
                continue // the other files of the batch still upload
            }

            if (failed) {
                // Remember the rest of the batch: Retry continues with it instead of silently dropping it.
                itemsAfterFailure = items.drop(index + 1)
                return
            }
        }

        if (anyRejectedAsTooLarge && _uploadStatus.value !is UploadStatus.Success) {
            _uploadStatus.value = UploadStatus.Idle
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
        websiteToRetry?.let { pending ->
            startWebsiteUpload(pending)
            return
        }
        textToRetry?.let { pending ->
            publishTextPost(pending)
            return
        }
        val item = lastFailedItem ?: return
        val rest = itemsAfterFailure
        itemsAfterFailure = emptyList()
        if (item.isTooLarge()) {
            // Big original file: ask again (ZIP / upload as is) instead of failing the same way
            _uploadStatus.value = UploadStatus.Idle
            _largeFileQueue.update { listOf(item) + it }
            if (rest.isNotEmpty()) startBatchUpload(rest)
        } else {
            startBatchUpload(listOf(item) + rest)
        }
    }

    /** The Advanced options page changed the link (new address / options): the result page shows the new link. */
    fun replaceSuccessRecord(record: com.praveen.studio.photovideo.url.data.model.UploadRecord) {
        val current = _uploadStatus.value
        if (current is UploadStatus.Success) {
            _uploadStatus.value = current.copy(record = record)
        }
    }

    fun dismissProgress() {
        if (_uploadStatus.value is UploadStatus.Failed) {
            itemsAfterFailure = emptyList()
        }
        textToRetry = null
        if (websiteToRetry != null) {
            websiteToRetry = null
            WebsiteBuilder.clean(getApplication<Application>())
        }
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
        fixedCategory: MediaCategory?,
        customFileName: String?,
        mimeOverride: String?
    ): SelectedMediaItem? {
        var name = customFileName ?: "file_${System.currentTimeMillis()}"
        var size = 0L
        val providerMime: String? = mimeOverride ?: try {
            context.contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }

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

        var category = fixedCategory ?: MediaCategory.fromMimeAndExtension(providerMime, name)
        // .xapk / .apks are archives, not installable .apk files (the website would add ".apk" to their name)
        if (category == MediaCategory.APKS &&
            name.substringAfterLast('.', "").lowercase() in listOf("xapk", "apks")
        ) {
            category = MediaCategory.FILES
        }

        val mimeType = (providerMime?.takeIf { it.isNotBlank() }
            ?: LinkRules.mimeFromExtension(name).ifEmpty { category.mimeTypeFilter })
            .let { if (it.contains('*')) "application/octet-stream" else it }

        return SelectedMediaItem(
            uri = uri,
            name = name,
            sizeBytes = size,
            mimeType = mimeType,
            category = category
        )
    }
}
