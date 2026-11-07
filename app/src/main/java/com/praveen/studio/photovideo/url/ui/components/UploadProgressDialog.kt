package com.praveen.studio.photovideo.url.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.praveen.studio.photovideo.url.data.model.UploadStatus
import com.praveen.studio.photovideo.url.ui.theme.AccentEmerald
import com.praveen.studio.photovideo.url.ui.theme.AccentRose
import com.praveen.studio.photovideo.url.ui.theme.CyanPrimary

@Composable
fun UploadProgressDialog(
    status: UploadStatus,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (status is UploadStatus.Idle) return

    val context = LocalContext.current

    Dialog(
        onDismissRequest = {
            if (status is UploadStatus.Success || status is UploadStatus.Failed) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = status is UploadStatus.Success || status is UploadStatus.Failed,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .testTag("upload_progress_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (status) {
                    is UploadStatus.Preparing -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(CyanPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = CyanPrimary,
                                strokeWidth = 3.dp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Preparing Upload",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = status.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    is UploadStatus.Compressing -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(CyanPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = CyanPrimary,
                                strokeWidth = 3.dp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Creating ZIP",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = status.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { status.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = CyanPrimary,
                            trackColor = CyanPrimary.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${(status.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is UploadStatus.Uploading -> {
                        val progressPercent = (status.progress * 100).toInt()
                        val uploadedMb = String.format("%.2f", status.bytesUploaded / (1024.0 * 1024.0))
                        val totalMb = String.format("%.2f", status.totalBytes / (1024.0 * 1024.0))
                        val speedStr = if (status.speedKBps >= 1024.0) {
                            String.format("%.2f MB/s", status.speedKBps / 1024.0)
                        } else {
                            String.format("%.0f KB/s", status.speedKBps)
                        }

                        // Header with percentage
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (status.totalItems > 1) {
                                        "Uploading (${status.currentItemIndex}/${status.totalItems})"
                                    } else {
                                        "Uploading to Supabase"
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = status.fileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = "$progressPercent%",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = CyanPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Progress Bar
                        LinearProgressIndicator(
                            progress = { status.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .testTag("upload_linear_progress"),
                            color = CyanPrimary,
                            trackColor = CyanPrimary.copy(alpha = 0.2f)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Metric grid
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Data Uploaded",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "$uploadedMb / $totalMb MB",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Transfer Speed",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = speedStr,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = CyanPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Elapsed Time",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = String.format("%02d:%02ds", status.elapsedSeconds / 60, status.elapsedSeconds % 60),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    is UploadStatus.RegisteringMetadata -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(CyanPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Registering Link",
                                tint = CyanPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Registering Short URL",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Saving metadata to Firebase Realtime Database...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ID: ${status.shortId}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = CyanPrimary
                            )
                        )
                    }

                    is UploadStatus.Success -> {
                        val record = status.record

                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(AccentEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Upload Succeeded",
                                tint = AccentEmerald,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (record.isDemo) "Demo Simulation Complete" else "Upload Successful!",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (record.isDemo) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Notice: In Demo Mode, files are simulated locally. To host public files on the web, configure your Supabase in Settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Generated Public Short URL Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "PUBLIC SHORT URL (ivu.gt.tc)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = record.publicUrl,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${record.fileName} • ${record.formattedSize}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Buttons: Copy, Share, Open
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Short URL", record.publicUrl)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Short URL copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dialog_copy_url_btn")
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy")
                            }

                            Button(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Shared File: ${record.fileName}")
                                        putExtra(Intent.EXTRA_TEXT, record.publicUrl)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Short URL"))
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dialog_share_url_btn")
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_done_btn")
                        ) {
                            Text("Done")
                        }
                    }

                    is UploadStatus.Failed -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(AccentRose.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Upload Failed",
                                tint = AccentRose,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Upload Failed",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = status.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (status.retryable) {
                                Button(
                                    onClick = onRetry,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Retry")
                                }
                            } else {
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onNavigateToSettings()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Open Settings")
                                }
                            }

                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Close")
                            }
                        }
                    }
                    UploadStatus.Idle -> Unit
                }
            }
        }
    }
}
