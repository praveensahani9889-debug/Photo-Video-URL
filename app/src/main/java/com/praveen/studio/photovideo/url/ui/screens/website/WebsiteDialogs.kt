package com.praveen.studio.photovideo.url.ui.screens.website

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.praveen.studio.photovideo.url.domain.util.SizeFormat
import com.praveen.studio.photovideo.url.domain.util.WebsiteBuilder
import com.praveen.studio.photovideo.url.domain.util.WebsiteDraft

/** "Website Publish": the two ways to bring a website into the app. */
@Composable
fun WebsiteChooserDialog(
    onUploadFile: () -> Unit,
    onTerminal: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.testTag("website_chooser")
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Publish a website", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    "Like uploading to a hosting: choose how you want to add your website.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OptionCard(
                    icon = Icons.Default.CloudUpload,
                    title = "Upload file",
                    text = "One index.html file, or a ZIP with all your website files (pages, css, js, images).",
                    tag = "website_option_file",
                    onClick = onUploadFile
                )
                OptionCard(
                    icon = Icons.Default.Code,
                    title = "Terminal",
                    text = "Write or paste your code, run it to check it, then upload from there.",
                    tag = "website_option_terminal",
                    onClick = onTerminal
                )
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun OptionCard(icon: ImageVector, title: String, text: String, tag: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The website is ready (files found): its name, what will be uploaded, and the Publish button. */
@Composable
fun WebsiteReviewDialog(
    draft: WebsiteDraft,
    onPublish: (String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember(draft) { mutableStateOf(draft.name.take(60)) }
    Dialog(onDismissRequest = onCancel) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.testTag("website_review")
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ready to publish", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Website name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("website_name")
                )
                Text(
                    text = "${draft.files.size} file${if (draft.files.size == 1) "" else "s"}  \u2022  ${SizeFormat.format(draft.totalBytes)}  \u2022  main page: ${draft.entry}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (draft.skipped.isNotEmpty()) {
                    val shown = draft.skipped.take(4).joinToString(", ")
                    val more = if (draft.skipped.size > 4) " and ${draft.skipped.size - 4} more" else ""
                    Text(
                        text = "${draft.skipped.size} file${if (draft.skipped.size == 1) " was" else "s were"} left out " +
                            "(not a website file type, or the name has spaces / symbols): $shown$more. " +
                            "Use only letters, numbers, . _ - in file names.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = "Only static websites work (html, css, js, images, fonts, audio, video). " +
                        "Up to ${WebsiteBuilder.MAX_FILES} files and ${WebsiteBuilder.MAX_TOTAL_BYTES / (1024 * 1024)} MB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = { onPublish(name.trim().ifEmpty { "My website" }) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_website_publish")
                    ) { Text("Publish", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
