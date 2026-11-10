package com.praveen.studio.photovideo.url.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.praveen.studio.photovideo.url.data.local.DeviceFile
import com.praveen.studio.photovideo.url.data.local.DeviceMediaStore
import com.praveen.studio.photovideo.url.data.local.DeviceThumbnails
import com.praveen.studio.photovideo.url.data.local.StorageAccess
import com.praveen.studio.photovideo.url.data.model.MediaCategory
import com.praveen.studio.photovideo.url.ui.theme.CyanPrimary
import java.text.DateFormat
import java.util.Date

/**
 * In-app gallery / file browser. It asks for the storage permission once and then lists the photos,
 * videos, documents, files or APKs of the device, so the user picks them inside the app instead of
 * going through the system file picker.
 */
@Composable
fun StoragePickerDialog(
    category: MediaCategory,
    maxSelection: Int,
    onDismiss: () -> Unit,
    onConfirm: (List<Uri>) -> Unit,
    onUseSystemPicker: () -> Unit
) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(StorageAccess.hasAccess(context, category)) }
    var denied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasAccess = StorageAccess.hasAccess(context, category)
        denied = !hasAccess
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasAccess = StorageAccess.hasAccess(context, category)
        denied = !hasAccess
    }

    fun requestAccess() {
        try {
            if (StorageAccess.needsAllFilesAccess(category)) {
                settingsLauncher.launch(StorageAccess.allFilesAccessIntent(context))
            } else {
                val permissions = StorageAccess.runtimePermissions(category)
                if (permissions.isEmpty()) hasAccess = true else permissionLauncher.launch(permissions)
            }
        } catch (e: Exception) {
            try {
                settingsLauncher.launch(StorageAccess.appDetailsIntent(context))
            } catch (_: Exception) {
                Toast.makeText(context, "Cannot open the permission screen: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Normal runtime permissions are asked right away. "All files access" first explains why it is needed.
    LaunchedEffect(Unit) {
        if (!hasAccess && !StorageAccess.needsAllFilesAccess(category)) requestAccess()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("storage_picker"),
            color = MaterialTheme.colorScheme.background
        ) {
            if (hasAccess) {
                PickerContent(
                    category = category,
                    maxSelection = maxSelection,
                    onDismiss = onDismiss,
                    onConfirm = onConfirm,
                    onUseSystemPicker = onUseSystemPicker
                )
            } else {
                PermissionContent(
                    category = category,
                    denied = denied,
                    onAllow = { requestAccess() },
                    onOpenSettings = {
                        try {
                            settingsLauncher.launch(StorageAccess.appDetailsIntent(context))
                        } catch (_: Exception) {
                        }
                    },
                    onUseSystemPicker = onUseSystemPicker,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

private fun categoryLabel(category: MediaCategory): String = when (category) {
    MediaCategory.IMAGES -> "photos"
    MediaCategory.VIDEOS -> "videos"
    MediaCategory.DOCUMENTS -> "documents"
    MediaCategory.FILES -> "files"
    MediaCategory.APKS -> "APK files"
}

@Composable
private fun PermissionContent(
    category: MediaCategory,
    denied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onUseSystemPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    val allFiles = StorageAccess.needsAllFilesAccess(category)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = category.getIcon(),
            contentDescription = null,
            tint = CyanPrimary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Allow access to your ${categoryLabel(category)}",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (allFiles) {
                "To list documents, archives and APK files, Android asks you to switch on " +
                    "\"Allow access to manage all files\" for this app. Files are only read when you select them."
            } else {
                "Photo Video URL needs permission to read ${categoryLabel(category)} on this device. " +
                    "Files are only read when you select them to upload."
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (denied) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Permission is still off. Allow it in the app settings, or pick files with the system picker.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.height(22.dp))
        Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) {
            Text(if (allFiles) "Open permission settings" else "Allow access")
        }
        if (denied && !allFiles) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Open app settings")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onUseSystemPicker, modifier = Modifier.fillMaxWidth()) {
            Text("Use system file picker instead")
        }
        TextButton(onClick = onDismiss) { Text("Cancel") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerContent(
    category: MediaCategory,
    maxSelection: Int,
    onDismiss: () -> Unit,
    onConfirm: (List<Uri>) -> Unit,
    onUseSystemPicker: () -> Unit
) {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<DeviceFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showAllTypes by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    LaunchedEffect(category, showAllTypes) {
        loading = true
        files = DeviceMediaStore.load(context, category, showAllTypes)
        loading = false
    }

    val isMedia = category == MediaCategory.IMAGES || category == MediaCategory.VIDEOS
    val visible = remember(files, query) {
        val q = query.trim()
        if (q.isEmpty()) files else files.filter { it.name.contains(q, ignoreCase = true) }
    }
    val byId = remember(files) { files.associateBy { it.id } }

    fun toggle(file: DeviceFile) {
        selectedIds = when {
            file.id in selectedIds -> selectedIds - file.id
            maxSelection == 1 -> listOf(file.id)
            selectedIds.size >= maxSelection -> {
                Toast.makeText(context, "You can select up to $maxSelection files at once", Toast.LENGTH_SHORT).show()
                selectedIds
            }
            else -> selectedIds + file.id
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Select ${categoryLabel(category)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (maxSelection > 1) "${selectedIds.size} of $maxSelection selected" else "Tap one to select",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onUseSystemPicker) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Other folders")
            }
        }

        if (!isMedia) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search by name") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )
            if (category == MediaCategory.FILES) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    FilterChip(
                        selected = showAllTypes,
                        onClick = { showAllTypes = !showAllTypes },
                        label = { Text("Show all file types") }
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                visible.isEmpty() -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No ${categoryLabel(category)} found here.",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Use \"Other folders\" to browse with the system picker.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                isMedia -> {
                    val isVideo = category == MediaCategory.VIDEOS
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 104.dp),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        gridItems(visible, key = { it.id }) { file ->
                            MediaGridCell(
                                file = file,
                                isVideo = isVideo,
                                selectedNumber = selectedIds.indexOf(file.id) + 1,
                                onClick = { toggle(file) }
                            )
                        }
                    }
                }
                else -> {
                    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(visible, key = { it.id }) { file ->
                            FileRow(
                                file = file,
                                category = category,
                                dateText = dateFormat.format(Date(file.dateSeconds * 1000L)),
                                selected = file.id in selectedIds,
                                onClick = { toggle(file) }
                            )
                        }
                    }
                }
            }
        }

        // Bottom bar
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedIds.isEmpty()) "Nothing selected" else "${selectedIds.size} selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onConfirm(selectedIds.mapNotNull { byId[it]?.uri }) },
                    enabled = selectedIds.isNotEmpty(),
                    modifier = Modifier.testTag("storage_picker_confirm")
                ) {
                    Text(if (selectedIds.size > 1) "Upload ${selectedIds.size} files" else "Upload")
                }
            }
        }
    }
}

@Composable
private fun MediaGridCell(
    file: DeviceFile,
    isVideo: Boolean,
    selectedNumber: Int,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, file.id) {
        value = DeviceThumbnails.load(context, file, isVideo, 256)
    }
    val selected = selectedNumber > 0

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        bitmap?.let { bmp ->
            val image = remember(bmp) { bmp.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (isVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = formatDuration(file.durationMs),
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CyanPrimary.copy(alpha = 0.28f))
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) CyanPrimary else Color.Black.copy(alpha = 0.25f))
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun FileRow(
    file: DeviceFile,
    category: MediaCategory,
    dateText: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) CyanPrimary.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CyanPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = category.getIcon(),
                contentDescription = null,
                tint = CyanPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatSize(file.sizeBytes)}  \u2022  $dateText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Checkbox(checked = selected, onCheckedChange = null)
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.0f KB", kb)
        else -> "$bytes B"
    }
}
