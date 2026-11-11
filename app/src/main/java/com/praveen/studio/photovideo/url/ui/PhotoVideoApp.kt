package com.praveen.studio.photovideo.url.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import android.content.Intent
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import com.praveen.studio.photovideo.url.LinkSearchActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.praveen.studio.photovideo.url.data.local.PreferencesManager
import com.praveen.studio.photovideo.url.data.model.ThemePreference
import com.praveen.studio.photovideo.url.data.remote.IdentityManager
import com.praveen.studio.photovideo.url.data.remote.IncomingIntentManager
import com.praveen.studio.photovideo.url.ui.components.MediaViewerScreen
import com.praveen.studio.photovideo.url.ui.components.SplashScreen
import com.praveen.studio.photovideo.url.ui.navigation.Screen
import com.praveen.studio.photovideo.url.ui.screens.history.HistoryScreen
import com.praveen.studio.photovideo.url.ui.screens.home.HomeScreen
import com.praveen.studio.photovideo.url.ui.screens.settings.SettingsScreen
import com.praveen.studio.photovideo.url.ui.theme.CyanPrimary
import com.praveen.studio.photovideo.url.ui.theme.PhotoVideoUrlTheme
import kotlinx.coroutines.delay

@Composable
fun PhotoVideoApp() {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val themePreference by preferencesManager.themeFlow.collectAsState(initial = ThemePreference.SYSTEM)

    var currentScreen by rememberSaveable { mutableStateOf(Screen.HOME) }

    // Flash screen: shown once when the app opens (the app is already loading behind it)
    var showSplash by rememberSaveable { mutableStateOf(true) }

    // The phone's permanent profile ID (same after re-install / clear data) must be known before any screen
    // reads its history or counters, so the screens start once it is resolved (a moment, or a few seconds offline).
    var identityReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val startedAt = System.currentTimeMillis()
        try {
            IdentityManager.prepare(context)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
        }
        identityReady = true
        if (showSplash) {
            val elapsed = System.currentTimeMillis() - startedAt
            delay((1400L - elapsed).coerceAtLeast(0L))
            showSplash = false
        }
    }

    // A tapped https://ivu.gt.tc/<id> link: shown here (not inside the Home screen) so it opens on top of
    // whichever tab is currently visible.
    val deepLinkShortId by IncomingIntentManager.deepLinkShortId.collectAsState()

    // BackHandler: Return to Home screen if user presses system back on History or Settings
    BackHandler(enabled = currentScreen != Screen.HOME) {
        currentScreen = Screen.HOME
    }

    PhotoVideoUrlTheme(themePreference = themePreference) {
        Box(modifier = Modifier.fillMaxSize()) {
        if (identityReady) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("app_bottom_nav_bar"),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Screen.entries.forEach { screen ->
                        // "Search" opens its own screen (Activity); it never stays selected in this bar
                        if (screen == Screen.SETTINGS) {
                            SearchNavItem(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(context, LinkSearchActivity::class.java))
                                    } catch (_: Throwable) {
                                    }
                                }
                            )
                        }
                        val isSelected = currentScreen == screen
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentScreen = screen },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = CyanPrimary.copy(alpha = 0.18f),
                                selectedIconColor = CyanPrimary,
                                selectedTextColor = CyanPrimary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_item_${screen.name.lowercase()}")
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                    when (screen) {
                        Screen.HOME -> HomeScreen(
                            onNavigateToHistory = { currentScreen = Screen.HISTORY },
                            onNavigateToSettings = { currentScreen = Screen.SETTINGS }
                        )
                        Screen.HISTORY -> HistoryScreen(
                            onNavigateBack = { currentScreen = Screen.HOME }
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            onNavigateBack = { currentScreen = Screen.HOME }
                        )
                    }
                }
            }
        }
        }

        deepLinkShortId?.let { shortId ->
            MediaViewerScreen(
                shortId = shortId,
                onClose = { IncomingIntentManager.clearDeepLink() }
            )
        }

        AnimatedVisibility(
            visible = showSplash,
            enter = EnterTransition.None,
            exit = fadeOut(animationSpec = tween(durationMillis = 450))
        ) {
            SplashScreen()
        }
        }
    }
}

@Composable
private fun RowScope.SearchNavItem(onClick: () -> Unit) {
    NavigationBarItem(
        selected = false,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search"
            )
        },
        label = {
            Text(
                text = "Search",
                style = MaterialTheme.typography.labelSmall
            )
        },
        colors = NavigationBarItemDefaults.colors(
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.testTag("nav_item_search")
    )
}
