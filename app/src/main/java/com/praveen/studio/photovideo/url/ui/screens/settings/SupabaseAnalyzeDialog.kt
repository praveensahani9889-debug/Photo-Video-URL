package com.praveen.studio.photovideo.url.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.praveen.studio.photovideo.url.data.local.SupabaseAccountManager
import com.praveen.studio.photovideo.url.data.model.MediaCategory
import com.praveen.studio.photovideo.url.data.model.SupabaseAccount
import com.praveen.studio.photovideo.url.data.model.UploadRecord
import com.praveen.studio.photovideo.url.domain.util.SizeFormat
import com.praveen.studio.photovideo.url.ui.theme.AccentAmber
import com.praveen.studio.photovideo.url.ui.theme.AccentEmerald
import com.praveen.studio.photovideo.url.ui.theme.AccentRose
import com.praveen.studio.photovideo.url.ui.theme.CyanPrimary
import java.text.DateFormat
import java.util.Date

/**
 * "Analyze": how full every Supabase project is and how much is left, per file type, plus a setup check.
 * The numbers come from the files the app uploaded (updated on every upload / delete); "Scan storage" reads the
 * real size of the bucket from Supabase.
 */
@Composable
fun SupabaseAnalyzeDialog(
    viewModel: SettingsViewModel,
    onClose: () -> Unit,
    onEdit: (String) -> Unit
) {
    val accounts by viewModel.accounts.collectAsState()
    val activeId by viewModel.activeAccountId.collectAsState()
    val scans by viewModel.scanStates.collectAsState()
    val checks by viewModel.accountChecks.collectAsState()
    val records by viewModel.historyRecords.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadAnalyzeData() }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("analyze_supabase_screen"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "Supabase Analyze",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (accounts.isEmpty()) {
                        Text(
                            text = "No Supabase connected yet. Set it up in Settings first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        SummaryCard(accounts, viewModel.maxAccounts)
                        accounts.forEach { account ->
                            AccountAnalysisCard(
                                account = account,
                                isActive = account.id == activeId,
                                accounts = accounts,
                                records = records,
                                scan = scans[account.id] ?: ScanState.Idle,
                                check = checks[account.id] ?: SetupCheckState.Idle,
                                onScan = { viewModel.scanAccount(account) },
                                onCheck = { viewModel.checkAccount(account) },
                                onUse = { viewModel.setActiveAccount(account.id) },
                                onEdit = { onEdit(account.id) }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(accounts: List<SupabaseAccount>, max: Int) {
    val used = accounts.sumOf { it.usedBytes }
    val capacity = accounts.sumOf { it.capacityBytes }
    val free = (capacity - used).coerceAtLeast(0L)
    val fraction = if (capacity > 0) (used.toDouble() / capacity.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "All Supabase projects",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(8.dp))
            UsageBar(fraction)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${SizeFormat.format(used)} used of ${SizeFormat.format(capacity)}  -  ${SizeFormat.format(free)} left",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${accounts.size} of $max projects added. When the active project is full the app switches " +
                    "to the next one by itself and shows a notification.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AccountAnalysisCard(
    account: SupabaseAccount,
    isActive: Boolean,
    accounts: List<SupabaseAccount>,
    records: List<UploadRecord>,
    scan: ScanState,
    check: SetupCheckState,
    onScan: () -> Unit,
    onCheck: () -> Unit,
    onUse: () -> Unit,
    onEdit: () -> Unit
) {
    // files of this project, by type (from the uploads the app knows)
    val mine = records.filter { !it.isFreeTier && SupabaseAccountManager.accountOfRecord(accounts, it)?.id == account.id }
    val status = when {
        account.full || account.usedBytes >= account.capacityBytes -> "FULL" to AccentRose
        isActive -> "ACTIVE" to AccentEmerald
        else -> "STANDBY" to CyanPrimary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${account.host}  /  ${account.bucketName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = status.first,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = status.second,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(status.second.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            UsageBar(account.usedFraction)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${SizeFormat.format(account.usedBytes)} used of ${SizeFormat.format(account.capacityBytes)} " +
                    "(${account.usedPercent}%)",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = "${SizeFormat.format(account.freeBytes)} left",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (account.lastScanAt > 0L) {
                    "Last scan: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(account.lastScanAt))
                } else {
                    "Estimate from your uploads. Tap \"Scan storage\" for the real size."
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // by type
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Uploaded with this app",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.height(4.dp))
            MediaCategory.entries.filter { it != MediaCategory.TEXT }.forEach { category ->
                val ofType = mine.filter { it.fileType.equals(category.title, ignoreCase = true) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${category.title}:", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "${ofType.size} files  -  ${SizeFormat.format(ofType.sumOf { it.fileSize })}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // scan result
            when (scan) {
                is ScanState.Scanning -> {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Scanning... ${scan.filesFound} files found", style = MaterialTheme.typography.bodySmall)
                    }
                }
                is ScanState.Done -> {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Real size in Supabase: ${SizeFormat.format(scan.usage.totalBytes)} in ${scan.usage.fileCount} files" +
                            if (scan.usage.truncated) " (stopped at the file limit, can be more)" else "",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = AccentEmerald
                    )
                    scan.usage.bytesByFolder.forEach { (folder, bytes) ->
                        Text(
                            text = "  $folder: ${SizeFormat.format(bytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is ScanState.Failed -> {
                    Spacer(Modifier.height(10.dp))
                    Text(scan.message, style = MaterialTheme.typography.bodySmall, color = AccentRose)
                }
                ScanState.Idle -> Unit
            }

            // setup check result
            when (check) {
                SetupCheckState.Running -> {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Checking the setup...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                is SetupCheckState.Done -> {
                    val ok = check.items.count { it.state == com.praveen.studio.photovideo.url.data.remote.CheckState.OK }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Setup: $ok of ${check.items.size} steps complete",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (ok == check.items.size) AccentEmerald else AccentAmber
                    )
                    Spacer(Modifier.height(6.dp))
                    SetupCheckList(check.items)
                }
                SetupCheckState.Idle -> Unit
            }

            // actions
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onScan,
                    enabled = scan !is ScanState.Scanning,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan storage", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onCheck,
                    enabled = check !is SetupCheckState.Running,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Check setup", maxLines = 1)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isActive && !account.full) {
                    OutlinedButton(onClick = onUse, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Use this", maxLines = 1)
                    }
                }
                OutlinedButton(onClick = onEdit, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit", maxLines = 1)
                }
            }
        }
    }
}

/** Green while there is room, amber when it fills up, red when it is almost full. */
@Composable
private fun UsageBar(fraction: Float) {
    val color: Color = when {
        fraction >= 0.9f -> AccentRose
        fraction >= 0.7f -> AccentAmber
        else -> AccentEmerald
    }
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp)),
        color = color,
        trackColor = color.copy(alpha = 0.18f)
    )
}
