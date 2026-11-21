package com.praveen.studio.photovideo.url.ui.screens.advanced

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.praveen.studio.photovideo.url.data.model.AppControlStore
import com.praveen.studio.photovideo.url.data.model.UploadRecord
import com.praveen.studio.photovideo.url.domain.repository.NameCheck
import com.praveen.studio.photovideo.url.domain.util.LinkDraft
import com.praveen.studio.photovideo.url.domain.util.LinkOptionsRules
import com.praveen.studio.photovideo.url.domain.util.LinkRules
import com.praveen.studio.photovideo.url.domain.util.LinkSlugRules
import com.praveen.studio.photovideo.url.ui.theme.AccentAmber
import com.praveen.studio.photovideo.url.ui.theme.AccentEmerald
import com.praveen.studio.photovideo.url.ui.theme.AccentRose
import com.praveen.studio.photovideo.url.ui.theme.CyanPrimary
import java.text.DateFormat
import java.util.Date

private val SwatchColors = listOf(
    "#0EA5E9", "#10B981", "#F59E0B", "#F43F5E", "#8B5CF6", "#EC4899",
    "#F97316", "#14B8A6", "#1E293B", "#111827", "#64748B", "#FFFFFF"
)

private fun parseColor(hex: String): Color? = try {
    Color(android.graphics.Color.parseColor(hex.trim()))
} catch (_: Exception) {
    null
}

/**
 * "Advanced options": opens after the upload and the ad, before the result page. Everything is optional.
 *  - Custom link address (ivu.gt.tc/<name>): letters, numbers, - and _ only; blocked words are refused.
 *  - Expiry (preset or custom), password, view limit, page background, a button under the file, download on / off,
 *    page title and message.
 * [onFinished] gets the changed record, or null when the user skipped.
 * With [loadExisting] the saved options of the link are shown (changing a link from History).
 */
@Composable
fun AdvancedOptionsDialog(
    record: UploadRecord,
    loadExisting: Boolean,
    onFinished: (UploadRecord?) -> Unit,
    viewModel: AdvancedOptionsViewModel = viewModel()
) {
    LaunchedEffect(record.createdAt, record.shortId) { viewModel.start(record, loadExisting) }

    val draft by viewModel.draft.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.error.collectAsState()
    val nameCheck by viewModel.nameCheck.collectAsState()
    val checking by viewModel.checking.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    // an existing password is kept; the field only appears when the user wants to change it
    var changePassword by remember { mutableStateOf(false) }

    // a custom name that was created once is fixed (only the automatic image_xxx / video_xxx names can be replaced)
    val customNameFixed = !LinkRules.isTypedId(record.shortId)
    // the address is chosen once, on the page that opens right after the upload; from History it is always fixed
    val slugLocked = customNameFixed || loadExisting
    // photos and videos are shown on the page: they need no "allow download" switch
    val recordKind = record.type.ifEmpty { record.fileType }.lowercase().removeSuffix("s")
    val showDownloadSwitch = recordKind != "image" && recordKind != "video"

    val blocked = AppControlStore.current.blockedWords
    // the current address of the link (editing from History) is always fine
    val slugProblem = if (LinkSlugRules.normalize(draft.slug) == record.shortId) null else LinkSlugRules.problem(draft.slug, blocked)

    Dialog(
        onDismissRequest = { if (!saving) onFinished(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("advanced_options_screen"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Advanced options",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Everything here is optional. Skip to keep the default link.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ---------------------------------------------------------------------------
                    // 1. Link address
                    // ---------------------------------------------------------------------------
                    SectionCard(title = "Link address") {
                        Text(
                            text = if (slugLocked) "Your link: ivu.gt.tc/${record.shortId}" else "Default link: ivu.gt.tc/${record.shortId}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (slugLocked) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CyanPrimary.copy(alpha = 0.10f))
                                    .padding(10.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (customNameFixed) {
                                        "Your own name is fixed. It cannot be changed after it was created."
                                    } else {
                                        "The link address is fixed. A name can only be chosen right after the upload."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                        Text(
                            text = "Leave the name empty to keep it. Or choose your own, for example ivu.gt.tc/my-trip",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = draft.slug,
                            onValueChange = { v -> viewModel.update { it.copy(slug = v.take(40)) } },
                            label = { Text("Custom name") },
                            prefix = { Text("ivu.gt.tc/") },
                            singleLine = true,
                            isError = slugProblem != null,
                            supportingText = {
                                Text(
                                    slugProblem
                                        ?: "${LinkSlugRules.MIN_LENGTH}-${LinkSlugRules.MAX_LENGTH} characters: letters, numbers, - and _"
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("advanced_slug_input")
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { viewModel.checkName() },
                                enabled = !checking && draft.slug.isNotBlank() && slugProblem == null,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (checking) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text("Check availability")
                            }
                            Spacer(Modifier.width(10.dp))
                            when (val check = nameCheck) {
                                NameCheck.Free -> StatusText("Available", AccentEmerald, true)
                                NameCheck.Taken -> StatusText("Already taken", AccentRose, false)
                                is NameCheck.Invalid -> StatusText(check.message, AccentRose, false)
                                is NameCheck.Error -> StatusText(check.message, AccentAmber, false)
                                null -> Unit
                            }
                        }
                        }
                    }

                    // ---------------------------------------------------------------------------
                    // 2. Expiry
                    // ---------------------------------------------------------------------------
                    OptionCard(
                        title = "Expiry time",
                        subtitle = "The link stops working and the file is deleted (Firebase and Supabase).",
                        checked = draft.expiryOn,
                        onCheckedChange = { on -> viewModel.update { it.copy(expiryOn = on) } }
                    ) {
                        val choices = buildList<Pair<Int, String>> {
                            if (viewModel.currentExpiresAt > 0L) add(LinkDraft.PRESET_KEEP to "Keep current")
                            LinkDraft.PRESET_LABELS.forEachIndexed { index, label -> add(index to label) }
                        }
                        choices.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { (presetId, label) ->
                                    ChoiceButton(
                                        label = label,
                                        selected = draft.expiryPreset == presetId,
                                        onClick = { viewModel.update { it.copy(expiryPreset = presetId) } },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (draft.expiryPreset == LinkDraft.PRESET_CUSTOM) {
                            Text(
                                text = "The link works for",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = draft.customAmount,
                                onValueChange = { v -> viewModel.update { it.copy(customAmount = v.filter(Char::isDigit).take(6)) } },
                                label = { Text("Amount (a number)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Minutes", "Hours", "Days").forEachIndexed { index, label ->
                                    ChoiceButton(
                                        label = label,
                                        selected = draft.customUnit == index,
                                        onClick = { viewModel.update { it.copy(customUnit = index) } },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        val preview = expiryPreview(draft, viewModel.currentExpiresAt)
                        if (preview != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = CyanPrimary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "The link stops working at that time. The file is removed from Supabase and Firebase " +
                                "the next time this app is opened after it.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // ---------------------------------------------------------------------------
                    // 3. Password
                    // ---------------------------------------------------------------------------
                    OptionCard(
                        title = "Password",
                        subtitle = "Visitors must type it to open the link.",
                        checked = draft.passwordOn,
                        onCheckedChange = { on -> viewModel.update { it.copy(passwordOn = on) } }
                    ) {
                        if (viewModel.hasExistingPassword && !changePassword) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AccentEmerald.copy(alpha = 0.12f))
                                    .padding(start = 10.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "A password is already set. It stays the same.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { changePassword = true }) { Text("Change") }
                            }
                        } else {
                            OutlinedTextField(
                                value = draft.password,
                                onValueChange = { v -> viewModel.update { it.copy(password = v.take(LinkOptionsRules.MAX_PASSWORD)) } },
                                label = { Text(if (viewModel.hasExistingPassword) "New password" else "Password") },
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Show or hide the password"
                                        )
                                    }
                                },
                                supportingText = {
                                    Text(
                                        if (viewModel.hasExistingPassword) {
                                            "Type a new password, or tap \"Keep current\"."
                                        } else {
                                            "${LinkOptionsRules.MIN_PASSWORD}-${LinkOptionsRules.MAX_PASSWORD} characters. Only a hash is saved, never the password."
                                        }
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("advanced_password_input")
                            )
                            if (viewModel.hasExistingPassword) {
                                TextButton(onClick = {
                                    changePassword = false
                                    viewModel.update { it.copy(password = "") }
                                }) { Text("Keep current password") }
                            }
                        }
                    }

                    // ---------------------------------------------------------------------------
                    // 4. View limit
                    // ---------------------------------------------------------------------------
                    OptionCard(
                        title = "View limit",
                        subtitle = "The link stops working after this many views.",
                        checked = draft.viewsOn,
                        onCheckedChange = { on -> viewModel.update { it.copy(viewsOn = on) } }
                    ) {
                        OutlinedTextField(
                            value = draft.maxViews,
                            onValueChange = { v -> viewModel.update { it.copy(maxViews = v.filter(Char::isDigit).take(7)) } },
                            label = { Text("Maximum views") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // ---------------------------------------------------------------------------
                    // 5. Background colour
                    // ---------------------------------------------------------------------------
                    OptionCard(
                        title = "Page background colour",
                        subtitle = "The colour behind your file on the web page.",
                        checked = draft.bgOn,
                        onCheckedChange = { on -> viewModel.update { it.copy(bgOn = on) } }
                    ) {
                        ColorPicker(
                            hex = draft.bgColor,
                            onChange = { hex -> viewModel.update { it.copy(bgColor = hex) } }
                        )
                    }

                    // ---------------------------------------------------------------------------
                    // 6. Button under the file
                    // ---------------------------------------------------------------------------
                    OptionCard(
                        title = "Button under the file",
                        subtitle = "Shows one button below the photo / video / document / file.",
                        checked = draft.buttonOn,
                        onCheckedChange = { on -> viewModel.update { it.copy(buttonOn = on) } }
                    ) {
                        OutlinedTextField(
                            value = draft.buttonText,
                            onValueChange = { v -> viewModel.update { it.copy(buttonText = v.take(LinkOptionsRules.MAX_BUTTON_TEXT)) } },
                            label = { Text("Button text") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = draft.buttonUrl,
                            onValueChange = { v -> viewModel.update { it.copy(buttonUrl = v.take(LinkOptionsRules.MAX_URL)) } },
                            label = { Text("Button link") },
                            placeholder = { Text("https://example.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Button colour", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                        Spacer(Modifier.height(4.dp))
                        ColorPicker(
                            hex = draft.buttonColor,
                            onChange = { hex -> viewModel.update { it.copy(buttonColor = hex) } }
                        )
                        Spacer(Modifier.height(10.dp))
                        // preview
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Button(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = parseColor(draft.buttonColor) ?: CyanPrimary,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(draft.buttonText.ifBlank { "Button preview" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    // ---------------------------------------------------------------------------
                    // 7. More
                    // ---------------------------------------------------------------------------
                    SectionCard(title = "More") {
                        if (showDownloadSwitch) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Allow download", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    "Off: the page shows the file but has no download button.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = draft.allowDownload,
                                onCheckedChange = { on -> viewModel.update { it.copy(allowDownload = on) } }
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        }
                        OutlinedTextField(
                            value = draft.pageTitle,
                            onValueChange = { v -> viewModel.update { it.copy(pageTitle = v.take(LinkOptionsRules.MAX_TITLE)) } },
                            label = { Text("Page title (optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = draft.pageMessage,
                            onValueChange = { v -> viewModel.update { it.copy(pageMessage = v.take(LinkOptionsRules.MAX_MESSAGE)) } },
                            label = { Text("Message under the title (optional)") },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onFinished(null) },
                        enabled = !saving,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_advanced_skip")
                    ) { Text(if (loadExisting) "Close" else "Skip") }
                    Button(
                        onClick = { viewModel.apply { updated -> onFinished(updated) } },
                        enabled = !saving && !loading,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("btn_advanced_apply")
                    ) {
                        if (saving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(if (loadExisting) "Save" else "Apply & continue")
                    }
                }
            }
        }
    }
}

/** "Deletes on 20 Nov 2026, 10:30" for the chosen expiry (null when it cannot be shown yet). */
private fun expiryPreview(draft: LinkDraft, currentExpiresAt: Long): String? {
    if (!draft.expiryOn) return null
    val at: Long = when (draft.expiryPreset) {
        LinkDraft.PRESET_KEEP -> currentExpiresAt
        LinkDraft.PRESET_CUSTOM -> {
            val minutes = LinkOptionsRules.customExpiryMinutes(draft.customAmount, draft.customUnit)
            val problem = LinkOptionsRules.expiryProblem(minutes)
            if (problem != null) return problem
            System.currentTimeMillis() + minutes!! * 60_000L
        }
        else -> System.currentTimeMillis() +
            LinkDraft.PRESET_MINUTES[draft.expiryPreset.coerceIn(0, LinkDraft.PRESET_MINUTES.size - 1)] * 60_000L
    }
    if (at <= 0L) return null
    return "Link ends on " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(at))
}

@Composable
private fun StatusText(text: String, color: Color, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = color)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

/** A card with a switch: the fields appear only when the option is on (and the user sets it only if wanted). */
@Composable
private fun OptionCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
            AnimatedVisibility(visible = checked) {
                Column(modifier = Modifier.padding(top = 10.dp)) { content() }
            }
        }
    }
}

/** Colour circles + a field for a #RRGGBB value, with a preview. */
@Composable
private fun ColorPicker(hex: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      SwatchColors.chunked(6).forEach { rowColors ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        rowColors.forEach { swatch ->
            val color = parseColor(swatch) ?: Color.Gray
            val selected = swatch.equals(hex.trim(), ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        BorderStroke(if (selected) 3.dp else 1.dp, if (selected) CyanPrimary else Color.Gray.copy(alpha = 0.5f)),
                        CircleShape
                    )
                    .clickable { onChange(swatch) }
            )
        }
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(parseColor(hex) ?: Color.Transparent)
                .border(BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f)), RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(10.dp))
        OutlinedTextField(
            value = hex,
            onValueChange = { v -> onChange(v.take(7)) },
            label = { Text("Colour code") },
            singleLine = true,
            isError = !LinkOptionsRules.isHexColor(hex),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        )
    }
}

/** A big, easy to tap choice (used for the expiry time): the chosen one has a coloured frame. */
@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val frame = if (selected) CyanPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    Box(
        modifier = modifier
            .heightIn(min = 46.dp)
            .clip(shape)
            .background(if (selected) CyanPrimary.copy(alpha = 0.16f) else Color.Transparent)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, frame), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            color = if (selected) CyanPrimary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
