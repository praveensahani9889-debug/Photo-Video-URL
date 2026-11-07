package com.praveen.studio.photovideo.url.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.praveen.studio.photovideo.url.data.model.MediaCategory
import com.praveen.studio.photovideo.url.ui.theme.CyanPrimary
import com.praveen.studio.photovideo.url.ui.theme.IndigoSecondary

/**
 * Upload card for one category.
 *
 * While the user has not set up their own Supabase ([needsSetup] = true) the card is shown
 * dull/faded, without any lock icon. Tapping it calls [onSetupNeeded], which opens the
 * "Set up your Supabase" prompt. Once Supabase is set up the card works normally.
 */
@Composable
fun UploadCategoryCard(
    category: MediaCategory,
    selectedCount: Int,
    onClick: () -> Unit,
    needsSetup: Boolean = false,
    onSetupNeeded: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val categoryAccentColor = when (category) {
        MediaCategory.IMAGES -> CyanPrimary
        MediaCategory.VIDEOS -> IndigoSecondary
        MediaCategory.DOCUMENTS -> Color(0xFFF59E0B)
        MediaCategory.FILES -> Color(0xFF10B981)
        MediaCategory.APKS -> Color(0xFF8B5CF6)
    }

    val actualClick = if (needsSetup) onSetupNeeded else onClick

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (needsSetup) 0.5f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = when {
                    needsSetup -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    selectedCount > 0 -> categoryAccentColor
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                },
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { actualClick() }
            .testTag("upload_card_${category.name.lowercase()}"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (selectedCount > 0 && !needsSetup) 4.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category icon with colored background
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(categoryAccentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.getIcon(),
                    contentDescription = category.title,
                    tint = categoryAccentColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Text info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (selectedCount > 0 && !needsSetup) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(categoryAccentColor)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$selectedCount ready",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action button
            Button(
                onClick = actualClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedCount > 0 && !needsSetup) categoryAccentColor
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selectedCount > 0 && !needsSetup) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier
                    .height(38.dp)
                    .testTag("action_btn_${category.name.lowercase()}")
            ) {
                Icon(
                    imageVector = if (selectedCount > 0 && !needsSetup) Icons.Default.CloudUpload
                    else Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Select or Upload",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (selectedCount > 0 && !needsSetup) "Upload" else "Select",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}
