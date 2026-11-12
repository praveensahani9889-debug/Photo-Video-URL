package com.praveen.studio.photovideo.url.ui.components

import android.Manifest
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.praveen.studio.photovideo.url.domain.util.QrBubbleRenderer
import com.praveen.studio.photovideo.url.domain.util.QrImageSaver
import com.praveen.studio.photovideo.url.ui.theme.CyanPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface QrUiState {
    data object Loading : QrUiState
    data class Ready(val bitmap: Bitmap) : QrUiState
    data object Error : QrUiState
}

/** Shows the QR code of a link (bubble style, app logo in the middle) with a button to save it as a picture. */
@Composable
fun QrCodeDialog(link: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val state by produceState<QrUiState>(initialValue = QrUiState.Loading, link) {
        value = withContext(Dispatchers.Default) {
            try {
                QrUiState.Ready(QrBubbleRenderer.render(context, link, 720))
            } catch (_: Throwable) {
                QrUiState.Error
            }
        }
    }
    var saving by remember { mutableStateOf(false) }

    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    // a bigger picture with the link under the code
                    val image = QrBubbleRenderer.render(
                        context, link, 1080, caption = QrBubbleRenderer.captionFor(link)
                    )
                    QrImageSaver.save(context, image, QrImageSaver.fileNameFor(link))
                } catch (t: Throwable) {
                    Result.failure(t)
                }
            }
            saving = false
            result.fold(
                onSuccess = {
                    Toast.makeText(context, "QR saved in Pictures/PhotoVideoURL", Toast.LENGTH_LONG).show()
                },
                onFailure = {
                    Toast.makeText(context, "Could not save the QR: ${it.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) save()
        else Toast.makeText(context, "Storage permission is needed to save the QR", Toast.LENGTH_LONG).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("qr_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "QR code",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Scan it with any camera to open the link",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier.size(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (val current = state) {
                        QrUiState.Loading -> CircularProgressIndicator(color = CyanPrimary)
                        is QrUiState.Ready -> {
                            val image = remember(current.bitmap) { current.bitmap.asImageBitmap() }
                            Image(
                                bitmap = image,
                                contentDescription = "QR code of the link",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        QrUiState.Error -> Text(
                            text = "The QR code could not be created.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = QrBubbleRenderer.captionFor(link),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (QrImageSaver.needsWritePermission(context)) {
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            save()
                        }
                    },
                    enabled = state is QrUiState.Ready && !saving,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("qr_download_btn")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (saving) "Saving..." else "Download QR")
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}
