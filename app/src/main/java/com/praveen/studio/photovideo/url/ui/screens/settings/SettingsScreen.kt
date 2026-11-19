package com.praveen.studio.photovideo.url.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.praveen.studio.photovideo.url.BuildConfig
import com.praveen.studio.photovideo.url.ads.AdsManager
import com.praveen.studio.photovideo.url.data.model.SupabaseAccount
import com.praveen.studio.photovideo.url.data.model.ThemePreference
import com.praveen.studio.photovideo.url.domain.util.AppLinks
import com.praveen.studio.photovideo.url.domain.util.CacheCleaner
import com.praveen.studio.photovideo.url.domain.util.SizeFormat
import com.praveen.studio.photovideo.url.ui.theme.AccentAmber
import com.praveen.studio.photovideo.url.ui.theme.AccentEmerald
import com.praveen.studio.photovideo.url.ui.theme.AccentRose
import com.praveen.studio.photovideo.url.ui.theme.CyanPrimary

/** The "Check Firebase Connection" tools are kept but hidden for now. */
private const val SHOW_FIREBASE_CHECK = false

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accounts by viewModel.accounts.collectAsState()
    val activeId by viewModel.activeAccountId.collectAsState()
    val themePreference by viewModel.themePreference.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val savedName by viewModel.savedName.collectAsState()
    val nameChanged by viewModel.nameChanged.collectAsState()
    val nameSaving by viewModel.nameSaving.collectAsState()
    val userDetails by viewModel.userDetails.collectAsState()
    val firebaseCheck by viewModel.firebaseCheck.collectAsState()
    val cacheBytes by viewModel.cacheBytes.collectAsState()

    var isGuideExpanded by remember { mutableStateOf(false) }
    var menuAccountId by remember { mutableStateOf<String?>(null) }
    var deleteAccountId by remember { mutableStateOf<String?>(null) }
    var showAnalyze by remember { mutableStateOf(false) }

    val isSaved = accounts.isNotEmpty()

    // Sent here by a "Set up Supabase" button of the Home screen: open the setup dialog at once
    val setupRequested by SetupRequest.pending.collectAsState()
    LaunchedEffect(setupRequested) {
        if (setupRequested) {
            SetupRequest.consume()
            if (viewModel.accounts.value.size < viewModel.maxAccounts) viewModel.openAddDialog()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("settings_screen_content"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---------------------------------------------------------------------------------
            // Profile
            // ---------------------------------------------------------------------------------
            item {
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(CyanPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (savedName.trim().firstOrNull() ?: 'U').uppercaseChar().toString(),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = CyanPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Profile",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = savedName.ifBlank { "Add your name" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = userName,
                        onValueChange = { viewModel.setUserName(it) },
                        label = { Text("Your name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_username_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (nameChanged) "Not saved yet" else if (savedName.isBlank()) "Type your name, then tap Save" else "Saved",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (nameChanged) AccentAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                viewModel.saveUserName {
                                    Toast.makeText(context, "Name saved", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = nameChanged && userName.isNotBlank() && !nameSaving,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("btn_save_name")
                        ) {
                            if (nameSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (savedName.isBlank()) "Save" else "Update")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Installation ID
                    Text(
                        text = "Installation ID",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Installation ID", viewModel.installationId))
                                Toast.makeText(context, "Installation ID copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                            .padding(12.dp),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = viewModel.installationId,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Installation ID", modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Cloud upload statistics
                    Text(
                        text = "Cloud Upload Statistics",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            StatRow("Photos Uploaded:", "${userDetails.totalPhotosUpload}")
                            StatRow("Videos Uploaded:", "${userDetails.totalVideosUpload}")
                            StatRow("Documents Uploaded:", "${userDetails.totalDocumentsUpload}")
                            StatRow("Files Uploaded:", "${userDetails.totalFilesUpload}")
                            StatRow("APKs Uploaded:", "${userDetails.totalApksUpload}")
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Hosting Plan:", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (isSaved) "Unlimited (Custom Supabase)" else "${userDetails.freeTierUploadsUsed} / ${userDetails.freeTierMax} Free Gallery Uploads",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSaved) AccentEmerald else CyanPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------------------------
            // Supabase
            // ---------------------------------------------------------------------------------
            item {
                SettingsCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CyanPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = "Supabase",
                                tint = CyanPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Supabase Storage",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Your own cloud storage. Up to ${viewModel.maxAccounts} projects: when one is full, uploads switch to the next.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (accounts.isEmpty()) {
                        StatusPill(connected = false, text = "Supabase not connected")
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.openAddDialog() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_setup_supabase")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Set up Supabase")
                        }
                    } else {
                        StatusPill(
                            connected = true,
                            text = if (accounts.size == 1) "Supabase Connected" else "Supabase Connected (${accounts.size} projects)"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        accounts.forEach { account ->
                            AccountRow(
                                account = account,
                                isActive = account.id == activeId,
                                onClick = { menuAccountId = account.id }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = "Tap a project for Edit, Delete and Analyze.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showAnalyze = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_analyze_supabase")
                            ) {
                                Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Analyze", maxLines = 1)
                            }
                            if (accounts.size < viewModel.maxAccounts) {
                                OutlinedButton(
                                    onClick = { viewModel.openAddDialog() },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_add_supabase")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add another", maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------------------------
            // Setup guide (kept)
            // ---------------------------------------------------------------------------------
            item {
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isGuideExpanded = !isGuideExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AccentAmber.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = "Guide",
                                    tint = AccentAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Supabase Setup Guide",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "How to create your project and configure storage",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = if (isGuideExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle Guide",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = isGuideExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GuideStepItem(
                                stepNumber = "1",
                                title = "Create a Supabase Project",
                                description = "Sign in at https://supabase.com and click 'New Project'. Give your project a name and choose a database region."
                            )
                            GuideStepItem(
                                stepNumber = "2",
                                title = "Copy Project URL & Anon Key",
                                description = "In your Supabase dashboard, go to Project Settings > API. Copy the 'Project URL' and the 'anon' public key. Paste them in the 'Set up Supabase' screen."
                            )
                            GuideStepItem(
                                stepNumber = "3",
                                title = "Create the bucket and permissions",
                                description = "The 'Set up Supabase' screen shows one SQL script. Paste it in Supabase > SQL Editor and press Run: it creates the public bucket and the upload, read and delete permissions."
                            )
                            GuideStepItem(
                                stepNumber = "4",
                                title = "Check and Save",
                                description = "Tap 'Run full check'. When every step is green, tap 'Save & connect'. You are ready to upload! Add up to ${viewModel.maxAccounts} projects: uploads switch to the next one when one is full."
                            )
                        }
                    }
                }
            }

            // ---------------------------------------------------------------------------------
            // Preferences & storage
            // ---------------------------------------------------------------------------------
            item {
                SettingsCard {
                    Text(
                        text = "Preferences & Storage",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Theme Preference",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemePreference.entries.forEach { pref ->
                            FilterChip(
                                selected = themePreference == pref,
                                onClick = { viewModel.setTheme(pref) },
                                label = { Text(pref.title) },
                                modifier = Modifier.testTag("theme_chip_${pref.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "App cache: ${CacheCleaner.format(cacheBytes)} (temporary files and image previews)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // Ads status (tap to refresh): shows why an ad does not appear (SDK / no fill / test or real ads)
                    var adsStatus by remember { mutableStateOf(AdsManager.statusSummary()) }
                    Text(
                        text = adsStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable {
                            AdsManager.preloadAds()
                            adsStatus = AdsManager.statusSummary()
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.clearAllCache { freed ->
                                Toast.makeText(
                                    context,
                                    "Cache cleared (${CacheCleaner.format(freed)} freed)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear Cache", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Cache & Local History")
                    }

                    // Hidden for now (set SHOW_FIREBASE_CHECK to true to show it again, e.g. in advanced features)
                    if (SHOW_FIREBASE_CHECK) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { viewModel.checkFirebase() },
                            enabled = firebaseCheck !is FirebaseCheckState.Checking,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = "Check Firebase", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (firebaseCheck is FirebaseCheckState.Checking) "Checking Firebase..." else "Check Firebase Connection")
                        }
                        (firebaseCheck as? FirebaseCheckState.Done)?.let { done ->
                            Spacer(modifier = Modifier.height(8.dp))
                            done.lines.forEach { line ->
                                Text(
                                    text = (if (line.ok) "\u2713 " else "\u2717 ") + line.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (line.ok) AccentEmerald else AccentRose
                                )
                            }
                            if (done.lines.any { !it.ok }) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "If a step says BLOCKED, publish the Firebase rules from README.md " +
                                        "(Firebase Console > Realtime Database > Rules).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------------------------
            // About, contact, terms, privacy (pages of ivu.gt.tc)
            // ---------------------------------------------------------------------------------
            item {
                SettingsCard {
                    Text(
                        text = "About & Legal",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinkRow(Icons.Default.Info, "About Photo Video URL", "ivu.gt.tc/about") { AppLinks.open(context, AppLinks.ABOUT) }
                    LinkRow(Icons.Default.Mail, "Contact support", "ivu.gt.tc/contact") { AppLinks.open(context, AppLinks.CONTACT) }
                    LinkRow(Icons.Default.Description, "Terms & Conditions", "ivu.gt.tc/terms") { AppLinks.open(context, AppLinks.TERMS) }
                    LinkRow(Icons.Default.PrivacyTip, "Privacy Policy", "ivu.gt.tc/privacy") { AppLinks.open(context, AppLinks.PRIVACY) }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Photo Video URL v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // ---- dialogs -------------------------------------------------------------------------------

    SupabaseSetupDialog(
        viewModel = viewModel,
        onSaved = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    )

    if (showAnalyze) {
        SupabaseAnalyzeDialog(
            viewModel = viewModel,
            onClose = { showAnalyze = false },
            onEdit = { id -> viewModel.openEditDialog(id) }
        )
    }

    // Tap on a project: Edit / Analyze / Use / Delete
    val menuAccount = accounts.firstOrNull { it.id == menuAccountId }
    if (menuAccount != null) {
        AlertDialog(
            onDismissRequest = { menuAccountId = null },
            title = {
                Text(menuAccount.displayName, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            },
            text = {
                Column {
                    Text(
                        text = "${menuAccount.host}  /  ${menuAccount.bucketName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MenuRow(Icons.Default.Edit, "Edit") {
                        menuAccountId = null
                        viewModel.openEditDialog(menuAccount.id)
                    }
                    MenuRow(Icons.Default.Analytics, "Analyze") {
                        menuAccountId = null
                        showAnalyze = true
                    }
                    if (menuAccount.id != activeId) {
                        MenuRow(Icons.Default.SwapHoriz, "Use for uploads") {
                            menuAccountId = null
                            viewModel.setActiveAccount(menuAccount.id)
                            Toast.makeText(context, "${menuAccount.displayName} is active now", Toast.LENGTH_SHORT).show()
                        }
                    }
                    MenuRow(Icons.Default.DeleteOutline, "Delete", tint = AccentRose) {
                        menuAccountId = null
                        deleteAccountId = menuAccount.id
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuAccountId = null }) { Text("Close") }
            }
        )
    }

    val deleteAccount = accounts.firstOrNull { it.id == deleteAccountId }
    if (deleteAccount != null) {
        AlertDialog(
            onDismissRequest = { deleteAccountId = null },
            title = { Text("Delete ${deleteAccount.displayName}?") },
            text = {
                Text(
                    "The project is removed from this app. The files stay in your Supabase and their links keep working, " +
                        "but deleting them from History will not remove them from that project any more " +
                        "(until you add it again)."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = deleteAccount.id
                        deleteAccountId = null
                        viewModel.deleteAccount(id) {
                            Toast.makeText(context, "Supabase deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteAccountId = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun StatusPill(connected: Boolean, text: String) {
    val color = if (connected) AccentEmerald else AccentRose
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(10.dp)
    ) {
        Icon(
            imageVector = if (connected) Icons.Default.CheckCircle else Icons.Default.Storage,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            modifier = Modifier.testTag("supabase_status_text")
        )
    }
}

@Composable
private fun AccountRow(account: SupabaseAccount, isActive: Boolean, onClick: () -> Unit) {
    val full = account.full || account.usedBytes >= account.capacityBytes
    val badge = when {
        full -> "FULL" to AccentRose
        isActive -> "ACTIVE" to AccentEmerald
        else -> "STANDBY" to CyanPrimary
    }
    val barColor = when {
        account.usedFraction >= 0.9f -> AccentRose
        account.usedFraction >= 0.7f -> AccentAmber
        else -> AccentEmerald
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Text(
                    text = "${account.host}  /  ${account.bucketName}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = badge.first,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = badge.second,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badge.second.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { account.usedFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = barColor.copy(alpha = 0.18f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${SizeFormat.format(account.usedBytes)} of ${SizeFormat.format(account.capacityBytes)} used",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MenuRow(icon: ImageVector, text: String, tint: Color = Color.Unspecified, onClick: () -> Unit) {
    val color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp)
    ) {
        Icon(icon, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GuideStepItem(
    stepNumber: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(CyanPrimary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
