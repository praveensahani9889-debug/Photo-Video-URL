package com.praveen.studio.photovideo.url.ui.screens.textpost

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.praveen.studio.photovideo.url.data.model.TextPostData
import com.praveen.studio.photovideo.url.data.model.TextPostRules
import com.praveen.studio.photovideo.url.ui.components.TextPostCard
import com.praveen.studio.photovideo.url.ui.components.parseHexColor

/** Colour of the page behind the post in the viewer (the same as the viewer's default background). */
private val PreviewPageColor = Color(0xFF1E1E1E)

private val TextSwatches = listOf(
    "#FFFFFF", "#111827", "#F59E0B", "#22C55E", "#0EA5E9", "#6366F1",
    "#A855F7", "#EC4899", "#EF4444", "#F97316", "#14B8A6", "#64748B"
)

private val BackgroundSwatches = listOf(
    "#111827", "#1E293B", "#FFFFFF", "#FEF3C7", "#DCFCE7", "#E0F2FE",
    "#312E81", "#14532D", "#7C2D12", "#831843", "#0F766E", "#000000"
)

/** One-tap combinations of background + text colour; the user can still change every value afterwards. */
private data class PostTheme(val name: String, val bg: String, val text: String)

private val PostThemes = listOf(
    PostTheme("Night", "#111827", "#F9FAFB"),
    PostTheme("Paper", "#FFFFFF", "#111827"),
    PostTheme("Ocean", "#0B3B5E", "#E0F2FE"),
    PostTheme("Forest", "#14532D", "#DCFCE7"),
    PostTheme("Sunset", "#7C2D12", "#FFEDD5"),
    PostTheme("Royal", "#312E81", "#E0E7FF"),
    PostTheme("Gold", "#1F2937", "#FBBF24")
)

private val PostSaver = listSaver<TextPostData, Any>(
    save = {
        listOf(it.topic, it.body, it.textColor, it.bgColor, it.font, it.sizeSp, it.bold, it.italic, it.underline, it.align)
    },
    restore = {
        TextPostData(
            topic = it[0] as String,
            body = it[1] as String,
            textColor = it[2] as String,
            bgColor = it[3] as String,
            font = it[4] as String,
            sizeSp = it[5] as Int,
            bold = it[6] as Boolean,
            italic = it[7] as Boolean,
            underline = it[8] as Boolean,
            align = it[9] as String
        )
    }
)

/**
 * "Text post": the user writes a topic and a text and designs it (font, size, bold / italic / underline, alignment,
 * text colour, background colour, or a ready-made theme) with a live preview. [onNext] gets the finished post;
 * the ad, the Advanced options and Publish follow.
 */
@Composable
fun TextPostEditorDialog(
    initial: TextPostData?,
    onDismiss: () -> Unit,
    onNext: (TextPostData) -> Unit
) {
    var post by rememberSaveable(stateSaver = PostSaver) {
        mutableStateOf(initial ?: TextPostData(textColor = "#F9FAFB", bgColor = "#111827"))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("text_post_editor"),
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
                        .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Text post",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Write it, design it, then publish",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ---- live preview ----
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(PreviewPageColor)
                            .padding(10.dp)
                    ) {
                        if (post.body.isBlank() && post.topic.isBlank()) {
                            TextPostCard(
                                post = post.copy(topic = "Your topic", body = "Your text appears here, exactly like this."),
                                pageColor = PreviewPageColor,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            TextPostCard(post = post, pageColor = PreviewPageColor, modifier = Modifier.fillMaxWidth())
                        }
                    }

                    // ---- topic + text ----
                    OutlinedTextField(
                        value = post.topic,
                        onValueChange = { v -> post = post.copy(topic = v.take(TextPostRules.MAX_TOPIC)) },
                        label = { Text("Topic") },
                        singleLine = true,
                        supportingText = { Text("${post.topic.length}/${TextPostRules.MAX_TOPIC}") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("text_post_topic")
                    )
                    OutlinedTextField(
                        value = post.body,
                        onValueChange = { v -> post = post.copy(body = v.take(TextPostRules.MAX_BODY)) },
                        label = { Text("Text") },
                        minLines = 6,
                        maxLines = 12,
                        supportingText = { Text("${post.body.length}/${TextPostRules.MAX_BODY}") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("text_post_body")
                    )

                    // ---- ready-made themes ----
                    EditorSection(title = "Quick themes") {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PostThemes.forEach { theme ->
                                val bg = parseHexColor(theme.bg) ?: Color.Black
                                val fg = parseHexColor(theme.text) ?: Color.White
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(bg)
                                        .border(
                                            width = if (post.bgColor.equals(theme.bg, true) && post.textColor.equals(theme.text, true)) 3.dp else 1.dp,
                                            color = if (post.bgColor.equals(theme.bg, true) && post.textColor.equals(theme.text, true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { post = post.copy(bgColor = theme.bg, textColor = theme.text) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(theme.name, color = fg, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // ---- text style ----
                    EditorSection(title = "Text style") {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextPostRules.FONTS.forEach { (id, label) ->
                                StyleChip(label = label, selected = post.font == id) { post = post.copy(font = id) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextPostRules.SIZES.forEach { (size, label) ->
                                StyleChip(label = label, selected = post.sizeSp == size) { post = post.copy(sizeSp = size) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconChip(Icons.Default.FormatBold, "Bold", post.bold) { post = post.copy(bold = !post.bold) }
                            IconChip(Icons.Default.FormatItalic, "Italic", post.italic) { post = post.copy(italic = !post.italic) }
                            IconChip(Icons.Default.FormatUnderlined, "Underline", post.underline) { post = post.copy(underline = !post.underline) }
                            Spacer(Modifier.width(8.dp))
                            IconChip(Icons.Default.FormatAlignLeft, "Align left", post.align == TextPostRules.ALIGN_LEFT) {
                                post = post.copy(align = TextPostRules.ALIGN_LEFT)
                            }
                            IconChip(Icons.Default.FormatAlignCenter, "Align center", post.align == TextPostRules.ALIGN_CENTER) {
                                post = post.copy(align = TextPostRules.ALIGN_CENTER)
                            }
                            IconChip(Icons.Default.FormatAlignRight, "Align right", post.align == TextPostRules.ALIGN_RIGHT) {
                                post = post.copy(align = TextPostRules.ALIGN_RIGHT)
                            }
                        }
                    }

                    // ---- colours ----
                    EditorSection(title = "Text colour") {
                        ColorChooser(
                            swatches = TextSwatches,
                            value = post.textColor,
                            noneLabel = "Auto",
                            onChange = { hex -> post = post.copy(textColor = hex) }
                        )
                    }
                    EditorSection(title = "Background colour") {
                        ColorChooser(
                            swatches = BackgroundSwatches,
                            value = post.bgColor,
                            noneLabel = "None",
                            onChange = { hex -> post = post.copy(bgColor = hex) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = { onNext(post.copy(topic = post.topic.trim(), body = post.body.trim())) },
                        enabled = post.isReady,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_text_post_next")
                    ) {
                        Text("Next", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun StyleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun IconChip(icon: ImageVector, description: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .size(22.dp)
        )
    }
}

/** Swatches, an "Auto / None" option and a field for any own colour (#RRGGBB). */
@Composable
private fun ColorChooser(
    swatches: List<String>,
    value: String,
    noneLabel: String,
    onChange: (String) -> Unit
) {
    var typed by remember(value) { mutableStateOf(value) }
    val bad = typed.isNotBlank() && TextPostRules.normalizeHex(typed) == null

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StyleChip(label = noneLabel, selected = value.isBlank()) { onChange("") }
        swatches.forEach { hex ->
            val selected = value.equals(hex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(hex) ?: Color.Gray)
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .clickable { onChange(hex) }
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = typed,
        onValueChange = { v ->
            typed = v.take(7)
            TextPostRules.normalizeHex(typed)?.let { onChange(it) }
        },
        label = { Text("Own colour  #RRGGBB") },
        singleLine = true,
        isError = bad,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}
