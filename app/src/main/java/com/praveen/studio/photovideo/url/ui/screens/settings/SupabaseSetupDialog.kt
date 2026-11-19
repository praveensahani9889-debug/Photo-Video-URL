package com.praveen.studio.photovideo.url.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.praveen.studio.photovideo.url.data.remote.CheckState
import com.praveen.studio.photovideo.url.data.remote.SetupCheckItem
import com.praveen.studio.photovideo.url.ui.theme.AccentAmber
import com.praveen.studio.photovideo.url.ui.theme.AccentEmerald
import com.praveen.studio.photovideo.url.ui.theme.AccentRose
import com.praveen.studio.photovideo.url.ui.theme.CyanPrimary

/** SQL that creates the public bucket and the three permissions the app needs (run once in Supabase SQL Editor). */
fun supabaseSetupSql(bucket: String): String {
    val id = bucket.trim().ifEmpty { "media" }.replace("'", "''")
    val tag = bucket.trim().ifEmpty { "media" }.replace("\"", "")
    return buildString {
        appendLine("-- Photo Video URL: bucket + permissions for '$id'")
        appendLine("insert into storage.buckets (id, name, public)")
        appendLine("values ('$id', '$id', true)")
        appendLine("on conflict (id) do update set public = true;")
        appendLine()
        appendLine("create policy \"ivu read $tag\" on storage.objects")
        appendLine("  for select to anon using (bucket_id = '$id');")
        appendLine("create policy \"ivu upload $tag\" on storage.objects")
        appendLine("  for insert to anon with check (bucket_id = '$id');")
        appendLine("create policy \"ivu delete $tag\" on storage.objects")
        append("  for delete to anon using (bucket_id = '$id');")
    }
}

/**
 * One place for the whole Supabase setup: project URL, key, bucket, plan size, the SQL that creates the bucket and
 * permissions, and a full check. Used for adding a project and for editing one.
 */
@Composable
fun SupabaseSetupDialog(
    viewModel: SettingsViewModel,
    onSaved: (String) -> Unit
) {
    val draft by viewModel.draft.collectAsState()
    val error by viewModel.draftError.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val check by viewModel.draftCheck.collectAsState()
    val context = LocalContext.current
    var keyVisible by remember { mutableStateOf(false) }
    val current = draft ?: return
    val editing = current.id != null

    Dialog(
        onDismissRequest = { viewModel.closeDialog() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("setup_supabase_screen"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
            ) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.closeDialog() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                    Text(
                        text = if (editing) "Edit Supabase" else "Set up Supabase",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Step 1: project
                    StepCard(number = "1", title = "Your Supabase project") {
                        Text(
                            text = "Supabase > Project Settings > API: copy the Project URL and the anon public key.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = current.label,
                            onValueChange = { v -> viewModel.updateDraft { it.copy(label = v) } },
                            label = { Text("Name (optional)") },
                            placeholder = { Text("e.g. Supabase 1") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = current.url,
                            onValueChange = { v -> viewModel.updateDraft { it.copy(url = v) } },
                            label = { Text("Project URL") },
                            placeholder = { Text("https://xyzcompany.supabase.co") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_project_url_input")
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = current.key,
                            onValueChange = { v -> viewModel.updateDraft { it.copy(key = v) } },
                            label = { Text("Public anon / publishable key") },
                            placeholder = { Text("eyJhbGciOiJIUzI1NiIsInR5cCI6...") },
                            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { keyVisible = !keyVisible }) {
                                    Icon(
                                        imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Show or hide the key"
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_anon_key_input")
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = current.bucket,
                            onValueChange = { v -> viewModel.updateDraft { it.copy(bucket = v) } },
                            label = { Text("Storage bucket name") },
                            placeholder = { Text("media") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_bucket_input")
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = current.capacityGb,
                            onValueChange = { v -> viewModel.updateDraft { it.copy(capacityGb = v) } },
                            label = { Text("Storage size of your plan (GB)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = current.capacityGb.trim() == "1",
                                onClick = { viewModel.updateDraft { it.copy(capacityGb = "1") } },
                                label = { Text("1 GB (Free)") }
                            )
                            FilterChip(
                                selected = current.capacityGb.trim() == "100",
                                onClick = { viewModel.updateDraft { it.copy(capacityGb = "100") } },
                                label = { Text("100 GB (Pro)") }
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "The key cannot tell the app your plan size, so it is used to show how full the project is " +
                                "and to switch to the next project in time. Only the public anon key goes here, never service_role.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Step 2: SQL
                    StepCard(number = "2", title = "Create the bucket and permissions (one time)") {
                        Text(
                            text = "Supabase > SQL Editor > New query. Paste this, press Run. It creates the PUBLIC bucket " +
                                "and allows upload, read and delete for the app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        val sql = supabaseSetupSql(current.bucket)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = sql,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Supabase setup SQL", sql))
                                Toast.makeText(context, "SQL copied", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy SQL")
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "If Supabase says a policy already exists, that part is already done.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Step 3: check
                    StepCard(number = "3", title = "Check the setup") {
                        OutlinedButton(
                            onClick = { viewModel.runDraftCheck() },
                            enabled = check !is SetupCheckState.Running && current.url.isNotBlank() && current.key.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_test_connection")
                        ) {
                            if (check is SetupCheckState.Running) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(if (check is SetupCheckState.Running) "Checking..." else "Run full check")
                        }
                        (check as? SetupCheckState.Done)?.let { done ->
                            Spacer(Modifier.height(8.dp))
                            SetupCheckList(done.items)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "The check uploads one tiny test file and deletes it again.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    error?.let {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(AccentRose.copy(alpha = 0.12f))
                                .padding(10.dp)
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = AccentRose, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = AccentRose)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Save bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.closeDialog() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancel") }
                    Button(
                        onClick = { viewModel.saveDraft(onSaved) },
                        enabled = !saving && current.url.isNotBlank() && current.key.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("btn_save_config")
                    ) {
                        if (saving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (editing) "Save changes" else "Save & connect")
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(number: String, title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyanPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = number,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** The lines of a setup check: green tick / red cross / grey question mark with the explanation. */
@Composable
fun SetupCheckList(items: List<SetupCheckItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            val color = when (item.state) {
                CheckState.OK -> AccentEmerald
                CheckState.FAIL -> AccentRose
                CheckState.UNKNOWN -> AccentAmber
            }
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = when (item.state) {
                        CheckState.OK -> Icons.Default.CheckCircle
                        CheckState.FAIL -> Icons.Default.Error
                        CheckState.UNKNOWN -> Icons.Default.HelpOutline
                    },
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
