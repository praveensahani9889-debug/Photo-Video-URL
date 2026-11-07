package com.praveen.studio.photovideo.url.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.praveen.studio.photovideo.url.ui.theme.CyanPrimary

/**
 * Shown ONLY when Supabase is NOT configured and user still has free trial uploads remaining (< 3).
 * When freeUploadsRemaining <= 0, this banner disappears completely ("Option gayab").
 */
@Composable
fun SetupRequiredBanner(
    freeUploadsRemaining: Int,
    onSelectFreePhotos: () -> Unit,
    onConfigureSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (freeUploadsRemaining <= 0) {
        return
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = CyanPrimary.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("setup_required_banner"),
        colors = CardDefaults.cardColors(
            containerColor = CyanPrimary.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyanPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = "Demo Mode",
                        tint = CyanPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Free Demo Photos ($freeUploadsRemaining/3 left)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Pick up to 3 photos from your gallery and get a real ivu.gt.tc link, no setup needed. Set up your own Supabase to unlock photos, videos, documents, files and APKs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSelectFreePhotos,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("btn_select_free_photos")
                ) {
                    Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = "Select Photo", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Gallery Photo ($freeUploadsRemaining left)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                OutlinedButton(
                    onClick = onConfigureSettings,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(0.9f)
                        .testTag("btn_configure_supabase_settings")
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Set Up", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
