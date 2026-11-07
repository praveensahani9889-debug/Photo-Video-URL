package com.praveen.studio.photovideo.url.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.praveen.studio.photovideo.url.data.model.MediaCategory

/**
 * Shown when the user taps a dull category card before connecting their own Supabase.
 */
@Composable
fun SetupSupabaseDialog(
    category: MediaCategory,
    onSetup: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("setup_supabase_dialog"),
        icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = null) },
        title = {
            Text(
                text = "Set up your Supabase",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column {
                Text(
                    text = "To upload ${category.title.lowercase()}, connect your own Supabase storage. " +
                        "Your files are stored in your project, and you get a short link for each one."
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Add your Project URL and anon key in Settings. It takes about 2 minutes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSetup,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("btn_setup_supabase_now")
            ) {
                Text("Set up Supabase")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now")
            }
        }
    )
}
