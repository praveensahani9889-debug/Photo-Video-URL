package com.praveen.studio.photovideo.url.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.praveen.studio.photovideo.url.data.model.AccountType

/** Name, colour and icon of the badge of an account type. Free has no badge. */
data class BadgeStyle(val label: String, val color: Color, val icon: ImageVector)

fun badgeStyleFor(type: AccountType): BadgeStyle? = when (type) {
    AccountType.FREE -> null
    AccountType.PREMIUM_49 -> BadgeStyle("Silver", Color(0xFF8A96A3), Icons.Filled.WorkspacePremium)
    AccountType.PREMIUM_399 -> BadgeStyle("Gold", Color(0xFFD99A00), Icons.Filled.WorkspacePremium)
    AccountType.FRIEND -> BadgeStyle("Friend", Color(0xFFD6336C), Icons.Filled.Favorite)
    AccountType.MODERATOR -> BadgeStyle("Moderator", Color(0xFF0E9F6E), Icons.Filled.Shield)
    AccountType.ADMIN -> BadgeStyle("Admin", Color(0xFFD32F2F), Icons.Filled.AdminPanelSettings)
}

/** The small badge next to the name: Silver (49), Gold (399), Friend, Moderator, Admin. Nothing for Free. */
@Composable
fun AccountBadge(type: AccountType, modifier: Modifier = Modifier) {
    val style = badgeStyleFor(type) ?: return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = style.color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, style.color.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(14.dp))
            Text(
                text = style.label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = style.color
            )
        }
    }
}
