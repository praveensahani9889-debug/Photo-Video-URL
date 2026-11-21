package com.praveen.studio.photovideo.url.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.praveen.studio.photovideo.url.ui.theme.BrandIndigo
import com.praveen.studio.photovideo.url.ui.theme.BrandViolet
import com.praveen.studio.photovideo.url.ui.theme.CyanPrimaryLight

/** The four places of the app. The names HOME / HISTORY / SETTINGS are the same as in the main screen. */
enum class NavTab(val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    HOME("Upload", Icons.Filled.CloudUpload, Icons.Outlined.CloudUpload),
    HISTORY("History", Icons.Filled.History, Icons.Outlined.History),
    SEARCH("Search", Icons.Filled.Search, Icons.Outlined.Search),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

/**
 * The bottom bar of the app. It lives ONCE, at the root of the app, and every tab (Upload, History, Search,
 * Settings) is shown above it, so it never changes between screens.
 *
 * NOTE: [NavigationBar] already makes room for the system navigation bar by itself. The old code added a second
 * `windowInsetsPadding(navigationBars)` around it, which left an empty strip of a different colour under the bar
 * (twice the gap on phones with gesture navigation).
 */
@Composable
fun AppBottomBar(
    selected: NavTab,
    onSelect: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    // a thin glowing line on the top edge: soft separation instead of a hard border
    val hairline = Brush.horizontalGradient(
        listOf(Color.Transparent, BrandIndigo.copy(alpha = 0.50f), BrandViolet.copy(alpha = 0.50f), Color.Transparent)
    )

    NavigationBar(
        modifier = modifier
            .testTag("app_bottom_nav_bar")
            .drawWithContent {
                drawContent()
                drawRect(brush = hairline, size = Size(size.width, 1.dp.toPx()))
            },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        NavTab.entries.forEach { tab ->
            BarItem(tab = tab, isSelected = selected == tab, onClick = { onSelect(tab) })
        }
    }
}

@Composable
private fun RowScope.BarItem(tab: NavTab, isSelected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val selectedColor = if (isDark) CyanPrimaryLight else scheme.primary
    // minor animation: the selected icon grows a little, with a soft spring
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.14f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "navIconScale"
    )

    NavigationBarItem(
        selected = isSelected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = tab.title,
                modifier = Modifier.graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
            )
        },
        label = {
            // the same font weight for every state: a bold label is wider, which made the whole bar "jump" a little
            Text(
                text = tab.title,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
            )
        },
        colors = NavigationBarItemDefaults.colors(
            indicatorColor = scheme.primary.copy(alpha = if (isDark) 0.28f else 0.14f),
            selectedIconColor = selectedColor,
            selectedTextColor = selectedColor,
            unselectedIconColor = scheme.onSurfaceVariant,
            unselectedTextColor = scheme.onSurfaceVariant
        ),
        modifier = Modifier.testTag("nav_item_${tab.name.lowercase()}")
    )
}
