package com.praveen.studio.photovideo.url.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.praveen.studio.photovideo.url.data.local.AccountStore
import com.praveen.studio.photovideo.url.data.local.InstallationIdProvider
import com.praveen.studio.photovideo.url.data.local.RecoveryStore
import com.praveen.studio.photovideo.url.ui.screens.premium.PremiumNav
import com.praveen.studio.photovideo.url.ui.screens.premium.PremiumScreen
import com.praveen.studio.photovideo.url.ui.components.AppBottomBar
import com.praveen.studio.photovideo.url.ui.components.NavTab
import com.praveen.studio.photovideo.url.ui.navigation.TabRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.praveen.studio.photovideo.url.data.local.PreferencesManager
import com.praveen.studio.photovideo.url.data.local.SupabaseAccountManager
import com.praveen.studio.photovideo.url.data.model.ThemePreference
import com.praveen.studio.photovideo.url.BuildConfig
import com.praveen.studio.photovideo.url.data.model.AppControlStore
import com.praveen.studio.photovideo.url.data.remote.AppControlManager
import com.praveen.studio.photovideo.url.data.remote.IdentityManager
import com.praveen.studio.photovideo.url.domain.util.VersionUtils
import com.praveen.studio.photovideo.url.ui.components.MaintenanceScreen
import com.praveen.studio.photovideo.url.ui.components.UpdateAvailableDialog
import com.praveen.studio.photovideo.url.data.remote.IncomingIntentManager
import com.praveen.studio.photovideo.url.ui.components.MediaViewerScreen
import com.praveen.studio.photovideo.url.ui.components.SplashScreen
import com.praveen.studio.photovideo.url.ui.navigation.Screen
import com.praveen.studio.photovideo.url.ui.screens.history.HistoryScreen
import com.praveen.studio.photovideo.url.ui.screens.search.LinkSearchScreen
import com.praveen.studio.photovideo.url.ui.screens.home.HomeScreen
import com.praveen.studio.photovideo.url.ui.screens.settings.SettingsScreen
import com.praveen.studio.photovideo.url.ui.theme.CyanPrimary
import com.praveen.studio.photovideo.url.ui.theme.PhotoVideoUrlTheme
import com.praveen.studio.photovideo.url.ui.theme.appBackdrop
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val appControl by AppControlStore.state.collectAsState()
    val controlScope = rememberCoroutineScope()

    // A Supabase project got full and the app switched to the next one (a notification is shown too)
    LaunchedEffect(Unit) {
        SupabaseAccountManager.events.collect { event ->
            Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
        }
    }

    // Android 13+: ask once for the permission to show messages (notifications) from Firebase
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(identityReady, showSplash) {
        if (identityReady && !showSplash && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val flags = context.getSharedPreferences("app_flags", Context.MODE_PRIVATE)
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted && !flags.getBoolean("notification_permission_asked", false)) {
                flags.edit().putBoolean("notification_permission_asked", true).apply()
                try {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } catch (_: Throwable) {
                }
            }
        }
    }
    // Something (for example the Quick Settings tile) asks for a tab of this screen
    val tabRequest by TabRequests.pending.collectAsState()
    val scannerRequest by TabRequests.openScanner.collectAsState()
    LaunchedEffect(tabRequest) {
        val name = tabRequest ?: return@LaunchedEffect
        Screen.entries.firstOrNull { it.name == name }?.let { currentScreen = it }
        TabRequests.pending.value = null
    }

    // Safe copy of the account (encrypted file in the folder "Photo video URL"): the storage is asked for ONCE, by itself
    var showAllFilesDialog by rememberSaveable { mutableStateOf(false) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) controlScope.launch { IdentityManager.refreshSafeCopy(context) }
    }
    LaunchedEffect(identityReady, showSplash) {
        if (identityReady && !showSplash) {
            val flags = context.getSharedPreferences("app_flags", Context.MODE_PRIVATE)
            if (!flags.getBoolean("safe_copy_asked", false)) {
                delay(2500L)
                val ready = withContext(Dispatchers.IO) { RecoveryStore.isReady(context) }
                if (!ready) {
                    flags.edit().putBoolean("safe_copy_asked", true).apply()
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> showAllFilesDialog = true
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> Unit
                        else -> try {
                            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
        }
    }
    // coming back from the system page "All files access": the safe copy is written at once
    val appLifecycle = LocalLifecycleOwner.current
    val identityReadyNow by rememberUpdatedState(identityReady)
    DisposableEffect(appLifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && identityReadyNow) {
                controlScope.launch { IdentityManager.refreshSafeCopy(context) }
                controlScope.launch { AccountStore.refresh(context, InstallationIdProvider(context).getInstallationId()) }
            }
        }
        appLifecycle.lifecycle.addObserver(observer)
        onDispose { appLifecycle.lifecycle.removeObserver(observer) }
    }

    var updateDialogDismissed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val startedAt = System.currentTimeMillis()
        try {
            coroutineScope {
                // remote switches (maintenance, update, demo) are read in parallel with the phone profile
                val control = async { AppControlManager.refresh() }
                IdentityManager.prepare(context)
                control.await()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
        }
        identityReady = true
        // the account type (Free / Premium / team) is read once the phone's profile ID is known
        controlScope.launch { AccountStore.refresh(context, InstallationIdProvider(context).getInstallationId(), force = true) }
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
        // The soft gradient + glow is drawn ONCE here; every screen above it is transparent.
        Box(modifier = Modifier.fillMaxSize().appBackdrop()) {
        if (identityReady && !appControl.maintenance) {
        Scaffold(
            containerColor = Color.Transparent,
            // Only the left/right edges here. The bottom bar takes care of the bottom edge, and the top bar of
            // each tab takes care of the status bar. (Before, the top and bottom edges were padded here AND again
            // inside every tab, so Upload / History / Settings had extra empty space that the Search screen
            // did not have — that is the "different feeling" when you opened Search.)
            contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
            bottomBar = {
                AppBottomBar(
                    selected = when (currentScreen) {
                        Screen.HOME -> NavTab.HOME
                        Screen.HISTORY -> NavTab.HISTORY
                        Screen.SEARCH -> NavTab.SEARCH
                        Screen.SETTINGS -> NavTab.SETTINGS
                    },
                    onSelect = { tab ->
                        currentScreen = when (tab) {
                            NavTab.HOME -> Screen.HOME
                            NavTab.HISTORY -> Screen.HISTORY
                            NavTab.SEARCH -> Screen.SEARCH
                            NavTab.SETTINGS -> Screen.SETTINGS
                        }
                    }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // tell the screens inside that the bar's space is already used, so they do not pad it again
                    .consumeWindowInsets(innerPadding)
                    // the keyboard pushes the content up (the bar stays under the keyboard, nothing jumps)
                    .imePadding()
            ) {
                // ONE calm transition for all four tabs: the new tab fades in, the old tab is removed at once.
                // (The old Crossfade kept BOTH screens on the screen for a moment, so heavy tabs like Search and
                // Settings stuttered and looked different from Upload / History.)
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = { fadeIn(tween(durationMillis = 180)) togetherWith ExitTransition.None },
                    label = "ScreenTransition"
                ) { screen ->
                    when (screen) {
                        Screen.HOME -> HomeScreen(
                            onNavigateToHistory = { currentScreen = Screen.HISTORY },
                            onNavigateToSettings = { currentScreen = Screen.SETTINGS }
                        )
                        Screen.HISTORY -> HistoryScreen(
                            onNavigateBack = { currentScreen = Screen.HOME }
                        )
                        Screen.SEARCH -> LinkSearchScreen(
                            onClose = { currentScreen = Screen.HOME },
                            scannerRequest = scannerRequest && !showSplash,
                            onScannerRequestHandled = { TabRequests.openScanner.value = false }
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

        // The Premium plans page (opened from Settings and from locked features)
        val premiumOpen by PremiumNav.open.collectAsState()
        if (premiumOpen && identityReady) {
            PremiumScreen(onClose = { PremiumNav.close() })
        }

        // "New update available" (only when the version in app/backend is higher than this app)
        val updateAvailable = identityReady && !appControl.maintenance && appControl.newUpdate &&
            VersionUtils.isNewer(appControl.latestVersion, BuildConfig.VERSION_NAME)
        if (updateAvailable && (!updateDialogDismissed || appControl.forceUpdate)) {
            UpdateAvailableDialog(control = appControl, onLater = { updateDialogDismissed = true })
        }

        // Maintenance: the app does not open
        if (identityReady && appControl.maintenance) {
            MaintenanceScreen(
                control = appControl,
                onRetry = { controlScope.launch { AppControlManager.refresh() } }
            )
        }

        if (showAllFilesDialog) {
            AlertDialog(
                onDismissRequest = { showAllFilesDialog = false },
                title = { Text("Keep your account safe") },
                text = {
                    Text(
                        "Allow access to all files, so the app can keep an encrypted safe copy of your account in the folder " +
                            "\"Photo video URL\". After a re-install your account comes back by itself."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showAllFilesDialog = false
                        try {
                            context.startActivity(
                                Intent(
                                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                            )
                        } catch (_: Throwable) {
                            try {
                                context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            } catch (_: Throwable) {
                            }
                        }
                    }) { Text("Allow") }
                },
                dismissButton = { TextButton(onClick = { showAllFilesDialog = false }) { Text("Later") } }
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
