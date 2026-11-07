package com.praveen.studio.photovideo.url.ui.screens.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.praveen.studio.photovideo.url.data.model.MediaCategory
import com.praveen.studio.photovideo.url.data.model.UploadStatus
import com.praveen.studio.photovideo.url.data.remote.IncomingIntentManager
import com.praveen.studio.photovideo.url.ui.components.ApkOptionDialog
import com.praveen.studio.photovideo.url.ui.components.AppHeader
import com.praveen.studio.photovideo.url.ui.components.DeepLinkPreviewDialog
import com.praveen.studio.photovideo.url.ui.components.InstalledAppsDialog
import com.praveen.studio.photovideo.url.ui.components.LargeFileDialog
import com.praveen.studio.photovideo.url.ui.components.SetupRequiredBanner
import com.praveen.studio.photovideo.url.ui.components.SetupSupabaseDialog
import com.praveen.studio.photovideo.url.ui.components.SharedUploadDialog
import com.praveen.studio.photovideo.url.ui.components.UploadCategoryCard
import com.praveen.studio.photovideo.url.ui.components.UploadProgressDialog

@Composable
fun HomeScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val isConfigured by viewModel.isConfigured.collectAsState()
    val userDetails by viewModel.userDetails.collectAsState()
    val uploadStatus by viewModel.uploadStatus.collectAsState()
    val stagedItems by viewModel.stagedItems.collectAsState()
    val largeFileQueue by viewModel.largeFileQueue.collectAsState()

    val sharedMedia by IncomingIntentManager.sharedMedia.collectAsState()
    val deepLinkShortId by IncomingIntentManager.deepLinkShortId.collectAsState()

    var showApkOptionDialog by remember { mutableStateOf(false) }
    var showInstalledAppsDialog by remember { mutableStateOf(false) }
    // Category the user tapped while Supabase is not set up yet -> shows "Set up your Supabase"
    var setupPromptFor by remember { mutableStateOf<MediaCategory?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshConfigStatus()
    }

    // 0. Free Trial Gallery Photo Picker (up to 3 photos before setup)
    val freeTrialGalleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.handleFreeTrialGalleryPhoto(it) }
    }

    // 1. Multiple Photos Only Picker (Android Photo Picker strictly ImageOnly)
    val multipleImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.handleSelectedUris(uris, MediaCategory.IMAGES)
        }
    }

    // 2. Multiple Videos Only Picker (Android Photo Picker strictly VideoOnly)
    val multipleVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.handleSelectedUris(uris, MediaCategory.VIDEOS)
        }
    }

    // 3. Document Picker (Strictly PDF, Word, Excel, PPT, TXT)
    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.handleSelectedUris(listOf(it), MediaCategory.DOCUMENTS) }
    }

    // 4. Archive Files Picker (Folders / ZIP / 7Z / TAR / RAR files only)
    val zipFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.handleSelectedUris(listOf(it), MediaCategory.FILES) }
    }

    // 5. Direct APK File Picker from storage
    val apkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.handleSelectedUris(listOf(it), MediaCategory.APKS) }
    }

    Scaffold(
        topBar = {
            AppHeader(
                isConfigured = isConfigured,
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToSettings = onNavigateToSettings
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("home_screen_content"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Demo banner shown ONLY when Supabase is not configured AND user has free uploads left (< 3)
            // Once 3 photos are uploaded, this option disappears ("Option gayab")
            if (!isConfigured && userDetails.freeUploadsRemaining > 0) {
                item {
                    SetupRequiredBanner(
                        freeUploadsRemaining = userDetails.freeUploadsRemaining,
                        onSelectFreePhotos = {
                            freeTrialGalleryPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onConfigureSettings = onNavigateToSettings
                    )
                }
            }

            // Section Header
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Instant Media Hosting",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isConfigured) {
                            "Select files to upload directly to Supabase Storage & generate short URLs at ivu.gt.tc"
                        } else {
                            "Set up your own Supabase to upload photos, videos, documents, files and APKs."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Category Card 1: Images (Photos only)
            item {
                val imageCount = stagedItems.count { it.category == MediaCategory.IMAGES }
                UploadCategoryCard(
                    category = MediaCategory.IMAGES,
                    selectedCount = imageCount,
                    needsSetup = !isConfigured,
                    onSetupNeeded = { setupPromptFor = MediaCategory.IMAGES },
                    onClick = {
                        multipleImagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            }

            // Category Card 2: Videos (Videos only)
            item {
                val videoCount = stagedItems.count { it.category == MediaCategory.VIDEOS }
                UploadCategoryCard(
                    category = MediaCategory.VIDEOS,
                    selectedCount = videoCount,
                    needsSetup = !isConfigured,
                    onSetupNeeded = { setupPromptFor = MediaCategory.VIDEOS },
                    onClick = {
                        multipleVideoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                )
            }

            // Category Card 3: Documents (PDF, Word, Excel, PPT, TXT)
            item {
                val docCount = stagedItems.count { it.category == MediaCategory.DOCUMENTS }
                UploadCategoryCard(
                    category = MediaCategory.DOCUMENTS,
                    selectedCount = docCount,
                    needsSetup = !isConfigured,
                    onSetupNeeded = { setupPromptFor = MediaCategory.DOCUMENTS },
                    onClick = {
                        documentPicker.launch(
                            arrayOf(
                                "application/pdf",
                                "application/msword",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "text/plain",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-powerpoint",
                                "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                            )
                        )
                    }
                )
            }

            // Category Card 4: Files (Folders and ZIP archive files only)
            item {
                val fileCount = stagedItems.count { it.category == MediaCategory.FILES }
                UploadCategoryCard(
                    category = MediaCategory.FILES,
                    selectedCount = fileCount,
                    needsSetup = !isConfigured,
                    onSetupNeeded = { setupPromptFor = MediaCategory.FILES },
                    onClick = {
                        zipFilePicker.launch(
                            arrayOf(
                                "application/zip",
                                "application/x-zip-compressed",
                                "application/x-7z-compressed",
                                "application/x-tar",
                                "application/gzip",
                                "application/x-rar-compressed",
                                "application/octet-stream"
                            )
                        )
                    }
                )
            }

            // Category Card 5: APKs (2 Options: Upload device APK OR Extract installed app)
            item {
                val apkCount = stagedItems.count { it.category == MediaCategory.APKS }
                UploadCategoryCard(
                    category = MediaCategory.APKS,
                    selectedCount = apkCount,
                    needsSetup = !isConfigured,
                    onSetupNeeded = { setupPromptFor = MediaCategory.APKS },
                    onClick = {
                        showApkOptionDialog = true
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // APK Option Dialog (Upload Device APK vs Extract Installed App APK)
    if (showApkOptionDialog) {
        ApkOptionDialog(
            onDismiss = { showApkOptionDialog = false },
            onUploadStorageApk = {
                apkPicker.launch(
                    arrayOf(
                        "application/vnd.android.package-archive",
                        "application/octet-stream"
                    )
                )
            },
            onExtractInstalledApp = {
                showInstalledAppsDialog = true
            }
        )
    }

    // Installed Apps Extraction Dialog
    if (showInstalledAppsDialog) {
        InstalledAppsDialog(
            onDismiss = { showInstalledAppsDialog = false },
            onApkExtracted = { uri, fileName ->
                viewModel.handleExtractedApk(uri, fileName)
            }
        )
    }

    // "Set up your Supabase" prompt (tap on a dull card before setup)
    setupPromptFor?.let { category ->
        SetupSupabaseDialog(
            category = category,
            onSetup = {
                setupPromptFor = null
                onNavigateToSettings()
            },
            onDismiss = { setupPromptFor = null }
        )
    }

    // Live Upload Progress Dialog
    UploadProgressDialog(
        status = uploadStatus,
        onDismiss = { viewModel.dismissProgress() },
        onRetry = { viewModel.retryLastUpload() },
        onNavigateToSettings = onNavigateToSettings
    )

    // File bigger than the Supabase limit: ZIP it here, upload as is, or cancel.
    // Shown only when no other upload dialog is on screen.
    if (uploadStatus is UploadStatus.Idle) {
        largeFileQueue.firstOrNull()?.let { bigItem ->
            LargeFileDialog(
                item = bigItem,
                moreWaiting = largeFileQueue.size - 1,
                onZipAndUpload = { viewModel.zipAndUploadLarge() },
                onUploadAsIs = { viewModel.uploadLargeAsIs() },
                onCancel = { viewModel.skipLarge() }
            )
        }
    }

    // Direct Share Target Dialog (Receives photos/videos/docs/files/apks shared from other apps)
    sharedMedia?.let { incoming ->
        SharedUploadDialog(
            sharedMedia = incoming,
            isConfigured = isConfigured,
            freeUploadsRemaining = userDetails.freeUploadsRemaining,
            onStartUpload = { uris, category ->
                viewModel.handleSelectedUris(uris, category)
            },
            onStartFreeTrial = { uri ->
                viewModel.handleFreeTrialGalleryPhoto(uri)
            },
            onConfigureSettings = onNavigateToSettings,
            onDismiss = {
                IncomingIntentManager.clearSharedMedia()
            }
        )
    }

    // Deep Link Preview Dialog for ivu.gt.tc links
    deepLinkShortId?.let { shortId ->
        DeepLinkPreviewDialog(
            shortId = shortId,
            onDismiss = {
                IncomingIntentManager.clearDeepLink()
            }
        )
    }
}
