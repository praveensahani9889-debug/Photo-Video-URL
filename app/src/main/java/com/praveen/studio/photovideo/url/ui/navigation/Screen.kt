package com.praveen.studio.photovideo.url.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Upload", Icons.Filled.CloudUpload, Icons.Outlined.CloudUpload),
    HISTORY("History", Icons.Filled.History, Icons.Outlined.History),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}
