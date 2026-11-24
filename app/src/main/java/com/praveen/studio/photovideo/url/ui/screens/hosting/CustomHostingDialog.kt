package com.praveen.studio.photovideo.url.ui.screens.hosting

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.praveen.studio.photovideo.url.data.model.AppControlStore
import com.praveen.studio.photovideo.url.data.model.UploadRecord
import com.praveen.studio.photovideo.url.domain.repository.LinkOptionsRepository
import com.praveen.studio.photovideo.url.domain.repository.NameCheck
import com.praveen.studio.photovideo.url.domain.util.HostingRules
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ExpiryChoice(val key: String, val label: String, val days: Int)

/**
 * "Custom hosting": shown right after a website was uploaded (instead of the Advanced options).
 *  1. The address: ivu.gt.tc/ (fixed) + the user's free name + one of the fixed endings (.com .in .org ...).
 *  2. How long the site stays online (the developer's default is pre-selected; the user can choose another time).
 * Both are chosen ONCE: after "Save" (or "Skip") they cannot be changed again.
 */
@Composable
fun CustomHostingDialog(
    record: UploadRecord,
    onFinished: (UploadRecord?) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { LinkOptionsRepository(context.applicationContext as Application) }
    val scope = rememberCoroutineScope()
    val control = AppControlStore.current

    var name by rememberSaveable { mutableStateOf("") }
    var suffix by rememberSaveable { mutableStateOf(HostingRules.SUFFIXES.first()) }
    var check by remember { mutableStateOf<NameCheck?>(null) }
    var checking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // ---- expiry choices (inside the limit the developer set on the server) ----
    val serverDefault = control.websiteExpiryDays.coerceAtLeast(0)
    val maxDays = control.websiteMaxExpiryDays.coerceAtLeast(0)
    val choices = remember(serverDefault, maxDays) {
        val list = ArrayList<ExpiryChoice>()
        list += ExpiryChoice(
            key = "default",
            label = if (serverDefault > 0) "Default ($serverDefault days)" else "Default (never expires)",
            days = serverDefault
        )
        listOf(1 to "1 day", 7 to "7 days", 30 to "30 days", 90 to "90 days", 365 to "1 year").forEach { (d, l) ->
            if ((maxDays == 0 || d <= maxDays) && d != serverDefault) list += ExpiryChoice("d$d", l, d)
        }
        list += ExpiryChoice("custom", "Custom", -1)
        list
    }
    var choiceKey by rememberSaveable { mutableStateOf("default") }
    var customDays by rememberSaveable { mutableStateOf("") }

    val selected = choices.firstOrNull { it.key == choiceKey } ?: choices.first()
    val days: Int? = if (selected.key == "custom") {
        customDays.toIntOrNull()?.takeIf { it >= 1 && (maxDays == 0 || it <= maxDays) }
    } else {
        selected.days
    }

    // availability of the address (a short pause after typing, so Firebase is not asked for every letter)
    LaunchedEffect(name, suffix) {
        check = null
        if (name.isBlank()) return@LaunchedEffect
        val local = HostingRules.problem(name, suffix, control.blockedWords)
        if (local != null) {
            check = NameCheck.Invalid(local)
            return@LaunchedEffect
        }
        delay(600)
        checking = true
        check = repository.checkHosting(name, suffix)
        checking = false
    }

    fun save(useName: Boolean) {
        if (saving) return
        val chosenDays = if (useName) days else serverDefault
        if (chosenDays == null) return
        saving = true
        error = null
        scope.launch {
            repository.applyHosting(record, if (useName) name else "", suffix, chosenDays).fold(
                onSuccess = { updated ->
                    saving = false
                    onFinished(updated)
                },
                onFailure = {
                    saving = false
                    error = it.message ?: "Could not save the hosting."
                }
            )
        }
    }

    val canSave = !saving && days != null && (name.isBlank() || check is NameCheck.Free)

    Dialog(
        onDismissRequest = { if (!saving) save(useName = false) },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("custom_hosting"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Custom hosting", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = "Your website is online. Choose its own address and how long it stays online. " +
                            "You can do this only once: it cannot be changed later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HostingCard(title = "Website address") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${HostingRules.SITE}/",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = name,
                                onValueChange = { v ->
                                    name = v.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '-' }
                                        .take(HostingRules.MAX_NAME)
                                },
                                label = { Text("your-name") },
                                singleLine = true,
                                enabled = !saving,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 6.dp)
                                    .testTag("hosting_name")
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Ending", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HostingRules.SUFFIXES.forEach { s ->
                                ChoiceChip(label = s, selected = suffix == s, enabled = !saving) { suffix = s }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (name.isNotBlank()) {
                            Text(
                                text = HostingRules.url(name, suffix),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (checking) {
                                    CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.padding(start = 8.dp))
                                }
                                val (text, color) = when (val c = check) {
                                    NameCheck.Free -> "Available" to MaterialTheme.colorScheme.primary
                                    NameCheck.Taken -> "Already taken. Try another name or ending." to MaterialTheme.colorScheme.error
                                    is NameCheck.Invalid -> c.message to MaterialTheme.colorScheme.error
                                    is NameCheck.Error -> c.message to MaterialTheme.colorScheme.error
                                    null -> (if (checking) "Checking..." else "") to MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodySmall, color = color)
                            }
                        } else {
                            Text(
                                text = "Leave the name empty to keep the automatic address ${HostingRules.SITE}/${record.shortId}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HostingCard(title = "Online for") {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            choices.forEach { c ->
                                ChoiceChip(label = c.label, selected = choiceKey == c.key, enabled = !saving) { choiceKey = c.key }
                            }
                        }
                        if (selected.key == "custom") {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = customDays,
                                onValueChange = { v -> customDays = v.filter { it.isDigit() }.take(5) },
                                label = { Text(if (maxDays > 0) "Days (1 to $maxDays)" else "Days") },
                                singleLine = true,
                                isError = customDays.isNotEmpty() && days == null,
                                enabled = !saving,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "After this time the website goes offline and its files are removed from your storage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { save(useName = false) },
                        enabled = !saving,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_hosting_skip")
                    ) { Text("Skip") }
                    Button(
                        onClick = { save(useName = true) },
                        enabled = canSave,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_hosting_save")
                    ) {
                        if (saving) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Save & continue", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostingCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
