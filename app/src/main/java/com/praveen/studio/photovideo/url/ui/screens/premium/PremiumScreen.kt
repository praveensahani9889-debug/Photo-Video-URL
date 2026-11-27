package com.praveen.studio.photovideo.url.ui.screens.premium

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.praveen.studio.photovideo.url.data.local.AccountStore
import com.praveen.studio.photovideo.url.data.local.InstallationIdProvider
import com.praveen.studio.photovideo.url.data.local.UsageTracker
import com.praveen.studio.photovideo.url.data.model.AccountInfo
import com.praveen.studio.photovideo.url.data.model.AccountType
import com.praveen.studio.photovideo.url.data.model.MediaCategory
import com.praveen.studio.photovideo.url.domain.util.AppLinks
import com.praveen.studio.photovideo.url.ui.components.AccountBadge
import com.praveen.studio.photovideo.url.ui.components.badgeStyleFor

private val TickColor = Color(0xFF0E9F6E)
private val CrossColor = Color(0xFFD64545)

/** One line of the comparison table. "Y" = included (tick), "N" = not included (cross), anything else is shown as text. */
private data class CompareRow(val feature: String, val free: String, val silver: String, val gold: String)

private val COMPARE_ROWS = listOf(
    CompareRow("Photos per day", "3", "10", "Unlimited"),
    CompareRow("Videos per day", "2", "5", "Unlimited"),
    CompareRow("Documents per day", "1", "3", "Unlimited"),
    CompareRow("APKs per day", "1", "2", "Unlimited"),
    CompareRow("Files per day", "1", "2", "Unlimited"),
    CompareRow("Text posts per day", "5", "10", "Unlimited"),
    CompareRow("Publish a website", "Every 36 h", "Every 12 h", "Every 6 h"),
    CompareRow("Advanced options (password, view limit, expiry, custom link name)", "N", "Y", "Y"),
    CompareRow("Website address endings (.com .in .ai .co ...)", "N", "Y", "Y"),
    CompareRow("Add your own address ending", "N", "N", "Y"),
    CompareRow("Supabase storage projects", "1", "2", "5"),
    CompareRow("Ads", "Shown", "Shown", "No ads"),
    CompareRow("Premium customer support", "N", "N", "Y"),
    CompareRow("Early access to new features", "N", "N", "Y"),
    CompareRow("Profile badge", "N", "Silver", "Gold")
)

/** The "Premium plans" page: your plan, the three plans, the comparison table and the support address. */
@Composable
fun PremiumScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val account by AccountStore.state.collectAsState()
    BackHandler(onBack = onClose)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("premium_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Premium plans",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                YourPlanCard(account)

                PlanCard(
                    title = "Free",
                    price = "Free",
                    type = AccountType.FREE,
                    current = account.effectiveType == AccountType.FREE,
                    lines = listOf(
                        "3 photos, 2 videos, 1 document, 1 APK, 1 file and 5 text posts every day",
                        "1 website every 36 hours",
                        "1 Supabase storage project",
                        "Ads are shown"
                    ),
                    showBuy = false
                )
                PlanCard(
                    title = "Premium 49",
                    price = "\u20B949 / month",
                    type = AccountType.PREMIUM_49,
                    current = account.effectiveType == AccountType.PREMIUM_49,
                    lines = listOf(
                        "10 photos, 5 videos, 3 documents, 2 APKs, 2 files and 10 text posts every day",
                        "1 website every 12 hours",
                        "Advanced options: password, view limit, expiry, custom link name",
                        "Website addresses with endings: .com .in .ai .co and more",
                        "2 Supabase storage projects",
                        "Silver badge"
                    ),
                    showBuy = true
                )
                PlanCard(
                    title = "Premium 399",
                    price = "\u20B9399 / month",
                    type = AccountType.PREMIUM_399,
                    current = account.effectiveType == AccountType.PREMIUM_399,
                    lines = listOf(
                        "Unlimited uploads",
                        "1 website every 6 hours",
                        "No ads",
                        "Advanced options and all website address endings",
                        "Add an address ending of your own",
                        "5 Supabase storage projects",
                        "Premium customer support",
                        "Early access to new features",
                        "Gold badge"
                    ),
                    showBuy = true
                )

                Text(
                    text = "Online payment is coming soon.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                CompareTable()
                SupportCard(context)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun YourPlanCard(account: AccountInfo) {
    val context = LocalContext.current
    val type = account.effectiveType
    val plan = account.limits
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Your plan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(type.label, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                AccountBadge(type)
            }
            if (type.isPaid && account.purchaseDate.isNotBlank()) {
                Text("Purchased: ${account.purchaseDate}", style = MaterialTheme.typography.bodySmall)
            }
            if (type.isTeam) {
                Text(
                    "Team account: everything of Premium 399, with unlimited storage projects and no waiting time for websites.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // today's numbers (only the types that have a daily limit)
            val limited = listOf(
                MediaCategory.IMAGES, MediaCategory.VIDEOS, MediaCategory.DOCUMENTS,
                MediaCategory.APKS, MediaCategory.FILES, MediaCategory.TEXT
            ).filter { plan.dailyLimit(it) >= 0 }
            if (limited.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Today", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                limited.forEach { category ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            UsageTracker.plural(category).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "${UsageTracker.usedToday(context, category)} / ${plan.dailyLimit(category)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
            val wait = UsageTracker.websiteWaitMillis(context, plan)
            if (wait > 0L) {
                Text(
                    "Next website: in ${UsageTracker.formatWait(wait)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // the account ID: the owner needs it to give a plan to this account
            Spacer(Modifier.height(6.dp))
            val accountId = remember(context) { InstallationIdProvider(context).getInstallationId() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Account ID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(accountId, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Account ID", accountId))
                    Toast.makeText(context, "Account ID copied", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Account ID", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    type: AccountType,
    current: Boolean,
    lines: List<String>,
    showBuy: Boolean
) {
    val accent = badgeStyleFor(type)?.color ?: MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (current) 2.dp else 1.dp, if (current) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    AccountBadge(type)
                }
                Text(price, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = accent)
            }
            lines.forEach { line ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = TickColor, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Text(line, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (current) {
                Text(
                    "This is your plan",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = accent
                )
            } else if (showBuy) {
                Spacer(Modifier.height(4.dp))
                // The payment is not connected yet: the button does nothing for now.
                Button(
                    onClick = { },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("btn_buy_${type.key}")
                ) { Text("Buy") }
            }
        }
    }
}

@Composable
private fun CompareTable() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Compare plans",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1.7f))
                listOf("Free", "49", "399").forEach {
                    Text(
                        it,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
            COMPARE_ROWS.forEach { row ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                )
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(row.feature, modifier = Modifier.weight(1.7f).padding(end = 6.dp), style = MaterialTheme.typography.bodySmall)
                    CompareCell(row.free, Modifier.weight(1f))
                    CompareCell(row.silver, Modifier.weight(1f))
                    CompareCell(row.gold, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CompareCell(value: String, modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (value) {
            "Y" -> Icon(Icons.Filled.Check, contentDescription = "Included", tint = TickColor, modifier = Modifier.size(20.dp))
            "N" -> Icon(Icons.Filled.Close, contentDescription = "Not included", tint = CrossColor, modifier = Modifier.size(20.dp))
            else -> Text(
                value,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

@Composable
private fun SupportCard(context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Questions?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text(
                "Premium 399 members get premium customer support. Write to ${AppLinks.PREMIUM_EMAIL} and include your Account ID.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${AppLinks.PREMIUM_EMAIL}"))
                                    .putExtra(Intent.EXTRA_SUBJECT, "Photo Video URL Premium")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) {
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("Email us", maxLines = 1) }
                OutlinedButton(
                    onClick = { AppLinks.open(context, AppLinks.PREMIUM) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("More information", maxLines = 1) }
            }
        }
    }
}
