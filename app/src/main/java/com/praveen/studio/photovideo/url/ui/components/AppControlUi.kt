package com.praveen.studio.photovideo.url.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.praveen.studio.photovideo.url.data.model.AppControl
import com.praveen.studio.photovideo.url.ui.theme.CyanPrimary

/** Full screen shown instead of the app while `app/backend/maintenance` is true. */
@Composable
fun MaintenanceScreen(control: AppControl, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("maintenance_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = null,
                tint = CyanPrimary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "App is under maintenance",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = control.maintenanceMessage.ifBlank { "We are making improvements. Please try again a little later." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry, modifier = Modifier.testTag("maintenance_retry")) {
                Text("Try again")
            }
        }
    }
}

/**
 * "New update available" dialog. The Update button opens `app/backend/updateLink`.
 * With forceUpdate = true it cannot be dismissed.
 */
@Composable
fun UpdateAvailableDialog(control: AppControl, onLater: () -> Unit) {
    val context = LocalContext.current
    val canDismiss = !control.forceUpdate
    AlertDialog(
        onDismissRequest = { if (canDismiss) onLater() },
        icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = CyanPrimary) },
        title = { Text("New update available") },
        text = {
            Text(
                control.updateMessage.ifBlank {
                    "Version ${control.latestVersion} is available. Please update the app to get the latest features and fixes."
                }
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (control.updateLink.isBlank()) {
                        Toast.makeText(context, "The update link is not set yet", Toast.LENGTH_SHORT).show()
                    } else {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(control.updateLink)))
                        } catch (_: Exception) {
                            Toast.makeText(context, "No app found to open the update link", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.testTag("update_btn")
            ) { Text("Update") }
        },
        dismissButton = if (canDismiss) {
            { TextButton(onClick = onLater) { Text("Later") } }
        } else {
            null
        }
    )
}
