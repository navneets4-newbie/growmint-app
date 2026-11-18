package com.example.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppThemeMode
import com.example.model.CurrenciesAndCountries
import com.example.model.DefaultTransactionCategories
import com.example.model.IncomeType
import com.example.model.LifeStage
import com.example.model.MerchantCategoryRule
import com.example.model.ReferralState
import com.example.model.UserProfile
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.PersistentDisclaimerBanner
import com.example.ui.components.VirtualLedgerNoticeCard
import com.example.ui.theme.FinanceGreen
import com.example.ui.theme.FinanceRed
import com.example.ui.theme.MintPrimary

private data class PendingSettingsChange(
    val title: String,
    val summaryLines: List<String>,
    val confirmLabel: String = "Confirm & Save",
    val isDestructive: Boolean = false,
    val onApply: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profile: UserProfile,
    bankConnection: com.example.model.BankConnectionState,
    auditLogs: List<com.example.model.BankAuditLogEntry>,
    onUpdateProfile: (UserProfile) -> Unit,
    onConnectBank: () -> Unit,
    onDisconnectBank: () -> Unit,
    onRefreshBank: () -> Unit,
    onExportCsv: () -> String,
    onImportCsv: (String) -> Int,
    onResetData: () -> Unit,
    merchantRules: List<MerchantCategoryRule> = emptyList(),
    onSaveMerchantRule: (merchantPattern: String, category: String, ruleId: String?) -> Unit = { _, _, _ -> },
    onDeleteMerchantRule: (String) -> Unit = {},
    onOpenHowDecides: () -> Unit = {},
    onOpenActivityHistory: () -> Unit = {},
    activityCount: Int = 0,
    errorFlagCount: Int = 0,
    referralState: ReferralState = ReferralState(),
    onToggleSharingEnabled: (Boolean) -> Unit = {},
    onDeleteReferralCodeAndCount: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showCountryDialog by remember { mutableStateOf(false) }
    var showSecondaryCurrencyDialog by remember { mutableStateOf(false) }
    var showCsvExportDialog by remember { mutableStateOf(false) }
    var showCsvImportDialog by remember { mutableStateOf(false) }
    var showAuditLogDialog by remember { mutableStateOf(false) }
    var showPrivacyNoticeDialog by remember { mutableStateOf(false) }
    var showPinConfigDialog by remember { mutableStateOf(false) }
    var showBufferTargetDialog by remember { mutableStateOf(false) }
    var newPinText by remember { mutableStateOf("") }

    var currencySearch by remember { mutableStateOf("") }
    var countrySearch by remember { mutableStateOf("") }
    var manualRateText by remember { mutableStateOf(if (profile.manualExchangeRate != null && profile.manualExchangeRate > 0) profile.manualExchangeRate.toString() else "") }
    var csvImportContent by remember { mutableStateOf("") }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var pendingChange by remember { mutableStateOf<PendingSettingsChange?>(null) }
    var showRuleEditorDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<MerchantCategoryRule?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "GrowMint Settings",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Currency, Country, Manual Conversion & Data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = {
            snackbarMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = { TextButton(onClick = { snackbarMessage = null }) { Text("OK") } }
                ) {
                    Text(msg)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                PersistentDisclaimerBanner()
            }

            // Virtual Ledger Notice
            item {
                VirtualLedgerNoticeCard()
            }

            // Section 0: Day Mode / Night Mode / Auto Theme (Change 2)
            item {
                Text(
                    text = "Display & Theme",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_theme_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Theme Mode",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = when (profile.themeMode) {
                                        com.example.model.AppThemeMode.DAY -> "Day Mode (Light)"
                                        com.example.model.AppThemeMode.NIGHT -> "Night Mode (Dark)"
                                        com.example.model.AppThemeMode.AUTO -> "Auto (Follows Device)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = when (profile.themeMode) {
                                    com.example.model.AppThemeMode.DAY -> Icons.Default.LightMode
                                    com.example.model.AppThemeMode.NIGHT -> Icons.Default.DarkMode
                                    com.example.model.AppThemeMode.AUTO -> Icons.Default.BrightnessAuto
                                },
                                contentDescription = null,
                                tint = MintPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppThemeMode.values().forEach { mode ->
                                val isSelected = profile.themeMode == mode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (!isSelected) {
                                            pendingChange = PendingSettingsChange(
                                                title = "Change App Theme?",
                                                summaryLines = listOf("Switch theme from ${profile.themeMode.label} to ${mode.label}"),
                                                onApply = {
                                                    onUpdateProfile(profile.copy(themeMode = mode))
                                                    snackbarMessage = "Theme updated to ${mode.label}"
                                                }
                                            )
                                        }
                                    },
                                    label = {
                                        Text(
                                            when (mode) {
                                                AppThemeMode.DAY -> "Day"
                                                AppThemeMode.NIGHT -> "Night"
                                                AppThemeMode.AUTO -> "Auto"
                                            }
                                        )
                                    },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Section 0.25: Transparency & Activity History
            item {
                Text(
                    text = "Transparency & Activity",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenHowDecides)
                            .testTag("settings_how_decides_card"),
                        colors = CardDefaults.cardColors(containerColor = MintPrimary.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Rule, contentDescription = null, tint = MintPrimary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "How Grow Mint decides",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "See every rule ID, plain-language formula & trigger condition",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = "Open How Grow Mint decides", tint = MintPrimary)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenActivityHistory)
                            .testTag("settings_activity_history_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.History, contentDescription = null, tint = MintPrimary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Activity History & Error Flags",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "$activityCount confirmed change(s) • $errorFlagCount flagged • Export CSV or Text",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = "Open Activity History", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Section 0.5: Privacy Mode & Bank Linking (Coming Later)
            item {
                Text(
                    text = "Privacy Mode & Bank Linking",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("local_only_mode_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Local-only mode",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (profile.localOnlyMode) {
                                    "ON (Default): Your data stays on this device. AI calls receive only minimal anonymized context—no names or exact balances."
                                } else {
                                    "Standard privacy: Educational AI prompts include general income baseline context."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = profile.localOnlyMode,
                            onCheckedChange = { nextVal ->
                                pendingChange = PendingSettingsChange(
                                    title = "Update Privacy Mode?",
                                    summaryLines = listOf(
                                        "Local-only mode: ${if (nextVal) "ON (strictly minimized AI context)" else "OFF"}"
                                    ),
                                    onApply = {
                                        onUpdateProfile(profile.copy(localOnlyMode = nextVal))
                                        snackbarMessage = "Local-only mode turned ${if (nextVal) "ON" else "OFF"}"
                                    }
                                )
                            },
                            modifier = Modifier.testTag("local_only_mode_switch")
                        )
                    }
                }
            }

            // Section 0.52: Sharing and referrals
            item {
                Text(
                    text = "Sharing and referrals",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_sharing_referrals_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Turn sharing and referrals on",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (referralState.sharingEnabled) {
                                        "ON: You can generate privacy-safe score cards and share anonymous referral links."
                                    } else {
                                        "OFF: All score-card sharing, referral links, and invite options are hidden."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = referralState.sharingEnabled,
                                onCheckedChange = { nextVal ->
                                    pendingChange = PendingSettingsChange(
                                        title = if (nextVal) "Turn Sharing On?" else "Turn All Sharing Off?",
                                        summaryLines = listOf(
                                            if (nextVal) {
                                                "Enables privacy-safe score card generation and anonymous invite links (Guided Mode <18 remains protected)."
                                            } else {
                                                "Hides all score-card sharing and referral invite controls across GrowMint."
                                            }
                                        ),
                                        onApply = {
                                            onToggleSharingEnabled(nextVal)
                                            snackbarMessage = if (nextVal) "Sharing turned on." else "All sharing turned off."
                                        }
                                    )
                                },
                                modifier = Modifier.testTag("settings_sharing_enabled_switch")
                            )
                        }

                        HorizontalDivider()

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Anonymous Referral Record",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (referralState.myReferralCode.isNullOrBlank()) {
                                    "Your referral code and count are deleted (0 stored)."
                                } else {
                                    "Current code: ${referralState.myReferralCode} • Friends who joined: ${referralState.referralCount}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = {
                                    pendingChange = PendingSettingsChange(
                                        title = "Delete Referral Code and Count?",
                                        summaryLines = listOf(
                                            "Removes your anonymous referral code and resets your referral count to 0.",
                                            "Existing referral links using your old code will no longer count."
                                        ),
                                        confirmLabel = "Delete Code & Count",
                                        isDestructive = true,
                                        onApply = {
                                            onDeleteReferralCodeAndCount()
                                            snackbarMessage = "Deleted your referral code and count."
                                        }
                                    )
                                },
                                enabled = !referralState.myReferralCode.isNullOrBlank() || referralState.referralCount > 0,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_delete_referral_btn")
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = FinanceRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete my referral code and count", color = FinanceRed, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        HorizontalDivider()

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "What your score card and link contain",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MintPrimary
                            )
                            Text(
                                text = "• Included: Only your Money Health Score (0–100), score label, optional monthly point change, optional Low/Okay/Good component levels, optional nickname, and a random referral code.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "• Never included: Balances, income, debts, net worth, transactions, goals, account names, real name, email, currency amounts, or details about friends who join.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Disabled Link Bank (coming later) Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("link_bank_coming_later_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Link bank (coming later)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Read-only bank linking will be added later through a licensed provider.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("disabled_link_bank_button")
                        ) {
                            Icon(Icons.Default.LockClock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Link bank (coming later)")
                        }
                    }
                }
            }

            // Section 0.55: Eligibility Gate, Student Mode & Safety Buffer Target
            item {
                Text(
                    text = "Eligibility, Mode & Safety Buffer",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Guided Mode (Under 18)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    text = "Learning Mode only. Investing splits and ideas stay hidden.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = profile.isUnder18,
                                onCheckedChange = { nextVal ->
                                    pendingChange = PendingSettingsChange(
                                        title = "Change Guided Mode?",
                                        summaryLines = listOf(
                                            "Under-18 Guided Mode: ${if (nextVal) "ON (Learning Mode, no investing)" else "OFF (18+ Eligible)"}"
                                        ),
                                        onApply = {
                                            onUpdateProfile(profile.copy(isUnder18 = nextVal))
                                            snackbarMessage = "Guided Mode ${if (nextVal) "enabled" else "disabled"}"
                                        }
                                    )
                                },
                                modifier = Modifier.testTag("settings_under18_switch")
                            )
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Student Mode", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    text = "Learning Mode focus: adapts examples for student schedules, exam fees & stipends.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = profile.isStudentMode,
                                onCheckedChange = { nextVal ->
                                    pendingChange = PendingSettingsChange(
                                        title = "Change Student Mode?",
                                        summaryLines = listOf("Student Mode: ${if (nextVal) "ON" else "OFF"}"),
                                        onApply = {
                                            onUpdateProfile(profile.copy(isStudentMode = nextVal))
                                            snackbarMessage = "Student Mode updated"
                                        }
                                    )
                                }
                            )
                        }

                        HorizontalDivider()

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Life Stage", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text(
                                text = "Current: ${profile.userSituation}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LifeStage.values().toList().chunked(3).forEach { chunk ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    chunk.forEach { stage ->
                                        val isSel = profile.userSituation == stage.label
                                        FilterChip(
                                            selected = isSel,
                                            onClick = {
                                                if (!isSel) {
                                                    pendingChange = PendingSettingsChange(
                                                        title = "Change Life Stage?",
                                                        summaryLines = listOf("Switch life stage from ${profile.userSituation} to ${stage.label}"),
                                                        onApply = {
                                                            onUpdateProfile(
                                                                profile.copy(
                                                                    userSituation = stage.label,
                                                                    isStudentMode = if (stage == LifeStage.STUDENT) true else profile.isStudentMode
                                                                )
                                                            )
                                                            snackbarMessage = "Life stage updated to ${stage.label}"
                                                        }
                                                    )
                                                }
                                            },
                                            label = { Text(stage.label, fontSize = 11.sp, maxLines = 1) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (chunk.size < 3) {
                                        repeat(3 - chunk.size) { Spacer(modifier = Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Income Type", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text(
                                text = "Current: ${profile.incomeType.label} (${profile.incomeType.description})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IncomeType.values().toList().chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    pair.forEach { type ->
                                        val isSel = profile.incomeType == type
                                        FilterChip(
                                            selected = isSel,
                                            onClick = {
                                                if (!isSel) {
                                                    pendingChange = PendingSettingsChange(
                                                        title = "Change Income Type?",
                                                        summaryLines = listOf(
                                                            "Switch income type from ${profile.incomeType.label} to ${type.label}",
                                                            if (type == IncomeType.IRREGULAR) "Enables Pay-Yourself-a-Salary Holding Pot tools." else "Coach advice adapts to ${type.label} income."
                                                        ),
                                                        onApply = {
                                                            onUpdateProfile(
                                                                profile.copy(
                                                                    incomeType = type,
                                                                    isIrregularIncome = (type == IncomeType.IRREGULAR)
                                                                )
                                                            )
                                                            snackbarMessage = "Income type set to ${type.label}"
                                                        }
                                                    )
                                                }
                                            },
                                            label = { Text(type.label, fontSize = 12.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Emergency Safety Buffer Target", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                val customText = profile.customBufferTargetAmount?.let {
                                    CurrenciesAndCountries.format(it, profile.currencyCode)
                                } ?: "${String.format("%.1f", profile.emergencyBufferMonthsTarget)} month(s) of typical spending"
                                Text(
                                    text = "Current target: $customText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { showBufferTargetDialog = true }) {
                                Text("Edit Target")
                            }
                        }
                    }
                }
            }

            // Section 0.6: App Lock (PIN Security)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App Lock (4-Digit PIN)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (profile.appLockEnabled) "App requires PIN before showing balances" else "Protect balance viewing with a quick PIN lock",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = profile.appLockEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    showPinConfigDialog = true
                                } else {
                                    pendingChange = PendingSettingsChange(
                                        title = "Disable App PIN Lock?",
                                        summaryLines = listOf("Your dashboard will open without asking for a 4-digit PIN."),
                                        onApply = {
                                            onUpdateProfile(profile.copy(appLockEnabled = false))
                                            snackbarMessage = "App Lock disabled"
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // Section 1: Currency & Country
            item {
                Text(
                    text = "Currency & Jurisdiction",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCurrencyDialog = true }
                        .testTag("settings_currency_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Primary Currency (ISO 4217)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val c = CurrenciesAndCountries.ALL_CURRENCIES.find { it.code == profile.currencyCode }
                            Text(text = "${c?.code ?: profile.currencyCode} - ${c?.name ?: ""} (${c?.symbol ?: ""})", fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCountryDialog = true }
                        .testTag("settings_country_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Country (Used for legal schemes & tax notes)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = profile.country, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }

            // Section 2: Optional Display Conversion (Requirement)
            item {
                Text(
                    text = "Optional Secondary Currency Display",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "View totals in a secondary currency via a user-defined manual exchange rate. As mandated, GrowMint NEVER calls live exchange-rate APIs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSecondaryCurrencyDialog = true },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "Secondary Display Currency", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = profile.secondaryCurrency ?: "None (Disabled)",
                                    fontWeight = FontWeight.Bold,
                                    color = if (profile.secondaryCurrency != null) MintPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            TextButton(onClick = { showSecondaryCurrencyDialog = true }) {
                                Text(if (profile.secondaryCurrency == null) "Select" else "Change")
                            }
                        }

                        if (profile.secondaryCurrency != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = manualRateText,
                                onValueChange = { manualRateText = it },
                                label = { Text("Manual Exchange Rate (1 ${profile.currencyCode} = ? ${profile.secondaryCurrency})") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manual_exchange_rate_input"),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(
                                    onClick = {
                                        val rate = manualRateText.toDoubleOrNull()
                                        if (rate != null && rate > 0) {
                                            pendingChange = PendingSettingsChange(
                                                title = "Save Exchange Rate?",
                                                summaryLines = listOf("Set 1 ${profile.currencyCode} = $rate ${profile.secondaryCurrency}"),
                                                onApply = {
                                                    onUpdateProfile(profile.copy(manualExchangeRate = rate))
                                                    snackbarMessage = "Manual exchange rate saved."
                                                }
                                            )
                                        }
                                    }
                                ) {
                                    Text("Apply Rate")
                                }
                                TextButton(
                                    onClick = {
                                        pendingChange = PendingSettingsChange(
                                            title = "Disable Secondary Currency?",
                                            summaryLines = listOf("Clears secondary currency conversion display."),
                                            onApply = {
                                                manualRateText = ""
                                                onUpdateProfile(profile.copy(secondaryCurrency = null, manualExchangeRate = null))
                                                snackbarMessage = "Secondary currency display cleared."
                                            }
                                        )
                                    }
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Disable")
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: CSV Data Import / Export (Requirement 1)
            item {
                Text(
                    text = "Virtual Ledger Data Management",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showCsvExportDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_csv_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export CSV")
                    }

                    OutlinedButton(
                        onClick = { showCsvImportDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_csv_button")
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import CSV")
                    }
                }
            }

            // Section 3.5: Smart Category Rules (#5)
            item {
                Text(
                    text = "Smart Category Rules",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_smart_rules_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Categorize Merchants (${merchantRules.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Whenever you fix a merchant's category during review or quick-add, GrowMint saves a local rule here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = {
                                    editingRule = null
                                    showRuleEditorDialog = true
                                },
                                modifier = Modifier.testTag("add_smart_rule_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Rule")
                            }
                        }

                        if (merchantRules.isEmpty()) {
                            Text(
                                text = "No rules saved yet. Edit a category during import review or quick-add to create one automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            merchantRules.forEach { rule ->
                                HorizontalDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = rule.merchantPattern,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "-> ${rule.category}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MintPrimary
                                        )
                                    }
                                    Row {
                                        IconButton(
                                            onClick = {
                                                editingRule = rule
                                                showRuleEditorDialog = true
                                            },
                                            modifier = Modifier.testTag("edit_rule_btn_${rule.id}")
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit rule ${rule.merchantPattern}")
                                        }
                                        IconButton(
                                            onClick = {
                                                pendingChange = PendingSettingsChange(
                                                    title = "Delete Category Rule?",
                                                    summaryLines = listOf("Remove rule: \"${rule.merchantPattern}\" -> ${rule.category}"),
                                                    confirmLabel = "Delete Rule",
                                                    isDestructive = true,
                                                    onApply = {
                                                        onDeleteMerchantRule(rule.id)
                                                        snackbarMessage = "Rule deleted."
                                                    }
                                                )
                                            },
                                            modifier = Modifier.testTag("delete_rule_btn_${rule.id}")
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete rule ${rule.merchantPattern}", tint = FinanceRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 4: Delete all my data
            item {
                Button(
                    onClick = {
                        pendingChange = PendingSettingsChange(
                            title = "Delete All My Data?",
                            summaryLines = listOf(
                                "Permanently erases your profile, virtual envelopes, side-hustle logs, calendar items, streaks, and transactions from this device.",
                                "You will be returned to the Setup Wizard."
                            ),
                            confirmLabel = "Delete All Data",
                            isDestructive = true,
                            onApply = { onResetData() }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reset_data_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = FinanceRed.copy(alpha = 0.9f))
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete all my data", fontWeight = FontWeight.Bold)
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Currency Picker Dialog
    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Select Currency (ISO 4217)") },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp)) {
                    OutlinedTextField(
                        value = currencySearch,
                        onValueChange = { currencySearch = it },
                        label = { Text("Search currencies") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val filtered = CurrenciesAndCountries.ALL_CURRENCIES.filter {
                        it.code.contains(currencySearch, ignoreCase = true) ||
                        it.name.contains(currencySearch, ignoreCase = true)
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(filtered) { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showCurrencyDialog = false
                                        pendingChange = PendingSettingsChange(
                                            title = "Change Primary Currency?",
                                            summaryLines = listOf("Change currency from ${profile.currencyCode} to ${c.code} (${c.name})"),
                                            onApply = {
                                                onUpdateProfile(profile.copy(currencyCode = c.code))
                                                snackbarMessage = "Currency set to ${c.code}"
                                            }
                                        )
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "${c.code} (${c.symbol})", fontWeight = FontWeight.Bold)
                                    Text(text = c.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (c.code == profile.currencyCode) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MintPrimary)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyDialog = false }) { Text("Close") }
            }
        )
    }

    // Secondary Currency Dialog
    if (showSecondaryCurrencyDialog) {
        var secSearch by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSecondaryCurrencyDialog = false },
            title = { Text("Select Secondary Currency") },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp)) {
                    OutlinedTextField(
                        value = secSearch,
                        onValueChange = { secSearch = it },
                        label = { Text("Search currency") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val filtered = CurrenciesAndCountries.ALL_CURRENCIES.filter {
                        it.code.contains(secSearch, ignoreCase = true) ||
                        it.name.contains(secSearch, ignoreCase = true)
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(filtered) { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showSecondaryCurrencyDialog = false
                                        pendingChange = PendingSettingsChange(
                                            title = "Enable Secondary Currency?",
                                            summaryLines = listOf("Display secondary currency in ${c.code} (${c.name})"),
                                            onApply = {
                                                onUpdateProfile(profile.copy(secondaryCurrency = c.code, manualExchangeRate = 1.0))
                                                manualRateText = "1.0"
                                                snackbarMessage = "Secondary currency set to ${c.code}. Enter manual rate."
                                            }
                                        )
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "${c.code} - ${c.name}")
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSecondaryCurrencyDialog = false }) { Text("Close") }
            }
        )
    }

    // Country Dialog
    if (showCountryDialog) {
        AlertDialog(
            onDismissRequest = { showCountryDialog = false },
            title = { Text("Select Country") },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp)) {
                    OutlinedTextField(
                        value = countrySearch,
                        onValueChange = { countrySearch = it },
                        label = { Text("Search country") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val filtered = CurrenciesAndCountries.ALL_COUNTRIES.filter {
                        it.name.contains(countrySearch, ignoreCase = true)
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(filtered) { country ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showCountryDialog = false
                                        pendingChange = PendingSettingsChange(
                                            title = "Change Country?",
                                            summaryLines = listOf("Update country from ${profile.country} to ${country.name}"),
                                            onApply = {
                                                onUpdateProfile(profile.copy(country = country.name))
                                                snackbarMessage = "Country set to ${country.name}"
                                            }
                                        )
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = country.name, fontWeight = FontWeight.Medium)
                                if (country.name == profile.country) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MintPrimary)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCountryDialog = false }) { Text("Close") }
            }
        )
    }

    // CSV Export Dialog
    if (showCsvExportDialog) {
        val csv = onExportCsv()
        AlertDialog(
            onDismissRequest = { showCsvExportDialog = false },
            title = { Text("Exported Ledger CSV") },
            text = {
                Column {
                    Text(text = "Copy your virtual ledger records below:", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = csv,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showCsvExportDialog = false }) { Text("Done") }
            }
        )
    }

    // CSV Import Dialog
    if (showCsvImportDialog) {
        AlertDialog(
            onDismissRequest = { showCsvImportDialog = false },
            title = { Text("Import Ledger CSV") },
            text = {
                Column {
                    Text(
                        text = "Paste CSV formatted lines (Timestamp,Type,Category,Amount,Notes):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = csvImportContent,
                        onValueChange = { csvImportContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        placeholder = { Text("Timestamp,Type,Category,Amount,Notes\n1700000000000,\"INCOME\",\"Salary\",3000,\"Deposit\"") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCsvImportDialog = false
                        val toImport = csvImportContent
                        pendingChange = PendingSettingsChange(
                            title = "Import CSV Transactions?",
                            summaryLines = listOf("Parse and add pasted CSV rows into your local virtual ledger."),
                            confirmLabel = "Import Rows",
                            onApply = {
                                val count = onImportCsv(toImport)
                                snackbarMessage = "Imported $count ledger transactions."
                            }
                        )
                    }
                ) {
                    Text("Review Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCsvImportDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Audit Log Dialog
    if (showAuditLogDialog) {
        val sdf = remember { java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", java.util.Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { showAuditLogDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MintPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connection Audit Log")
                }
            },
            text = {
                Column(modifier = Modifier.heightIn(max = 420.dp)) {
                    Text(
                        text = "Real-time, immutable security log of all bank connection, sync, and revocation events:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (auditLogs.isEmpty()) {
                        Text(
                            text = "No connection events logged yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(auditLogs.reversed()) { entry ->
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = entry.action,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = when (entry.action) {
                                                "LINKED" -> FinanceGreen
                                                "REVOKED_DELETED" -> FinanceRed
                                                "APP_UNLOCKED" -> MintPrimary
                                                else -> MaterialTheme.colorScheme.primary
                                            }
                                        )
                                        Text(
                                            text = sdf.format(java.util.Date(entry.timestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = entry.description,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAuditLogDialog = false }) { Text("Close") }
            }
        )
    }

    // Privacy Notice Dialog
    if (showPrivacyNoticeDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyNoticeDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MintPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Bank Privacy Shield")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    Text(
                        text = "How GrowMint Protects Your Financial Data:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Strict Read-Only Scopes\nGrowMint only requests balances and transactions. It is mathematically and architecturally impossible for GrowMint to move money, initiate payments, or touch bank credentials.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "2. AI Prompt Privacy Guarantee\nThe AI Money Coach receives ONLY high-level categorical summaries (e.g. Total Dining: $240). Account numbers, bank names, and merchant lists are NEVER transmitted to AI models.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "3. Encrypted Backend at Rest\nBank tokens and provider secrets reside solely on the backend, encrypted using AES-256-GCM. Client code only stores masked account numbers (•••• 1234).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "4. Disconnect & Delete Anytime\nTapping 'Disconnect & Delete' instantly revokes the access token at the provider and purges all bank balances and transactions from your device.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyNoticeDialog = false }) { Text("Understood") }
            }
        )
    }

    // PIN Configuration Dialog
    if (showPinConfigDialog) {
        AlertDialog(
            onDismissRequest = { showPinConfigDialog = false },
            title = { Text("Set 4-Digit App PIN") },
            text = {
                Column {
                    Text(
                        text = "Enter a 4-digit PIN to lock the app when launched:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPinText,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                newPinText = it
                            }
                        },
                        label = { Text("4-Digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPinText.length == 4) {
                            val chosenPin = newPinText
                            showPinConfigDialog = false
                            newPinText = ""
                            pendingChange = PendingSettingsChange(
                                title = "Enable 4-Digit App PIN?",
                                summaryLines = listOf("Locks access to balances behind your new 4-digit PIN."),
                                onApply = {
                                    onUpdateProfile(profile.copy(appLockEnabled = true, appLockPin = chosenPin))
                                    snackbarMessage = "App Lock PIN enabled!"
                                }
                            )
                        }
                    },
                    enabled = newPinText.length == 4
                ) {
                    Text("Review PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPinConfigDialog = false
                    newPinText = ""
                }) { Text("Cancel") }
            }
        )
    }

    if (showBufferTargetDialog) {
        var monthsText by remember { mutableStateOf(profile.emergencyBufferMonthsTarget.toString()) }
        var customAmtText by remember { mutableStateOf(profile.customBufferTargetAmount?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { showBufferTargetDialog = false },
            title = { Text("Edit Emergency Buffer Target") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Default is 1.0 month of typical spending. You can adjust months or enter a specific custom amount.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = monthsText,
                        onValueChange = { monthsText = it },
                        label = { Text("Buffer Months (e.g. 1.0, 2.0, 3.0)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = customAmtText,
                        onValueChange = { customAmtText = it },
                        label = { Text("Optional Fixed Target (${profile.currencyCode})") },
                        supportingText = { Text("Leave blank to compute automatically from spending x months.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val m = (monthsText.toDoubleOrNull() ?: 1.0).coerceAtLeast(0.5)
                        val custom = customAmtText.toDoubleOrNull()?.takeIf { it > 0.0 }
                        showBufferTargetDialog = false
                        pendingChange = PendingSettingsChange(
                            title = "Update Emergency Buffer Target?",
                            summaryLines = listOf(
                                "Months Multiplier: ${String.format("%.1f", m)} mo",
                                if (custom != null) "Custom Fixed Target: ${CurrenciesAndCountries.format(custom, profile.currencyCode)}" else "Auto-calculated from monthly spending"
                            ),
                            onApply = {
                                onUpdateProfile(profile.copy(emergencyBufferMonthsTarget = m, customBufferTargetAmount = custom))
                                snackbarMessage = "Emergency buffer target updated!"
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                ) {
                    Text("Review")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBufferTargetDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRuleEditorDialog) {
        var patternText by remember(editingRule) { mutableStateOf(editingRule?.merchantPattern ?: "") }
        var categoryText by remember(editingRule) { mutableStateOf(editingRule?.category ?: DefaultTransactionCategories.ALL.first()) }
        var catExpanded by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showRuleEditorDialog = false },
            title = { Text(if (editingRule == null) "Add Smart Category Rule" else "Edit Smart Category Rule") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = patternText,
                        onValueChange = { patternText = it },
                        label = { Text("Merchant / Description match") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rule_pattern_input")
                    )
                    ExposedDropdownMenuBox(
                        expanded = catExpanded,
                        onExpandedChange = { catExpanded = !catExpanded }
                    ) {
                        OutlinedTextField(
                            value = categoryText,
                            onValueChange = { categoryText = it },
                            label = { Text("Assigned Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryEditable)
                                .testTag("rule_category_input")
                        )
                        ExposedDropdownMenu(
                            expanded = catExpanded,
                            onDismissRequest = { catExpanded = false }
                        ) {
                            DefaultTransactionCategories.ALL.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        categoryText = cat
                                        catExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (patternText.isNotBlank() && categoryText.isNotBlank()) {
                            onSaveMerchantRule(patternText.trim(), categoryText.trim(), editingRule?.id)
                            snackbarMessage = "Smart rule saved."
                            showRuleEditorDialog = false
                            editingRule = null
                        }
                    },
                    enabled = patternText.isNotBlank() && categoryText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary),
                    modifier = Modifier.testTag("save_smart_rule_btn")
                ) {
                    Text("Save Rule")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRuleEditorDialog = false
                    editingRule = null
                }) { Text("Cancel") }
            }
        )
    }

    pendingChange?.let { change ->
        ConfirmActionDialog(
            title = change.title,
            summaryLines = change.summaryLines,
            confirmLabel = change.confirmLabel,
            isDestructive = change.isDestructive,
            activityCategory = "Setting Change",
            onConfirm = {
                change.onApply()
                pendingChange = null
            },
            onDismiss = { pendingChange = null }
        )
    }
}

