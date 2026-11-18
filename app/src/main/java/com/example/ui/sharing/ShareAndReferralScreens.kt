package com.example.ui.sharing

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FinancialCalculator
import com.example.model.MonthlyImprovementReport
import com.example.model.ReferralState
import com.example.model.ScoreCardThemeStyle
import com.example.model.UserProfile
import com.example.ui.theme.FinanceGreen
import com.example.ui.theme.MintAccentGold
import com.example.ui.theme.MintPrimary
import com.example.util.ScoreCardRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScoreCardScreen(
    profile: UserProfile,
    monthlyReport: MonthlyImprovementReport,
    referralState: ReferralState,
    onOpenInviteFriend: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    // Default to most private settings: toggles OFF and nickname empty
    var showMonthlyChange by remember { mutableStateOf(false) }
    var showComponentBars by remember { mutableStateOf(false) }
    var showNickname by remember { mutableStateOf(false) }
    var nicknameInput by remember { mutableStateOf("") }
    var cardStyle by remember { mutableStateOf(ScoreCardThemeStyle.DAY) }

    var pendingSafBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val safPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        val bmp = pendingSafBitmap
        pendingSafBitmap = null
        if (uri != null && bmp != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    ScoreCardRenderer.writeBitmapToUri(context, bmp, uri)
                }
                snackbarMessage = if (ok) "Saved 1080×1080 PNG score card!" else "Could not save score card image."
            }
        }
    }

    val cardData = remember(
        monthlyReport,
        profile.isUnder18,
        showMonthlyChange,
        showComponentBars,
        showNickname,
        nicknameInput,
        cardStyle
    ) {
        FinancialCalculator.buildPrivacySafeScoreCardData(
            report = monthlyReport,
            isUnder18 = profile.isUnder18,
            requestedShowMonthlyChange = showMonthlyChange,
            requestedShowComponentBars = showComponentBars,
            requestedShowNickname = showNickname,
            requestedNickname = nicknameInput,
            style = cardStyle
        )
    }

    val previewBitmap = remember(cardData) {
        ScoreCardRenderer.renderScoreCardBitmap(cardData)
    }

    val linkToCopy = remember(referralState.myReferralCode) {
        FinancialCalculator.buildReferralLink(referralState.myReferralCode)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Share My Progress", fontWeight = FontWeight.Bold)
                        Text(
                            text = if (profile.isUnder18) "Guided Mode: Private save only"
                            else "Privacy-safe score card (1080×1080)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("share_card_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                ) { Text(msg) }
            }
        }
    ) { innerPadding ->
        if (!referralState.sharingEnabled) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sharing_disabled_banner"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = MintPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sharing is turned off", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        Text(
                            text = "You turned sharing and referrals off in Settings. Turn sharing back on under Settings > Sharing and referrals whenever you want to create or share a score card.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onBack,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Go Back")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Privacy guarantee banner
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MintPrimary.copy(alpha = 0.12f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MintPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Privacy-safe: zero balances, income, debts, goals, transactions, or real names ever appear on the card or in shared text.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (profile.isUnder18) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("guided_mode_share_notice"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MintAccentGold.copy(alpha = 0.16f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = MintAccentGold)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Guided Mode (<18): You can download your private score card image for yourself. External sharing, nicknames, bars, and invite links stay hidden.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                        }
                    }
                }

                // Live 1:1 1080x1080 PNG Preview
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                            .testTag("score_card_live_preview"),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "Score Card Preview: ${cardData.score} out of 100, ${cardData.scoreLabel}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Customization Controls (locked/hidden in Guided Mode <18)
                if (!profile.isUnder18) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("score_card_customize_card"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Customize Card",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MintPrimary
                                )

                                // Day or Night card style
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Card style:",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        modifier = Modifier.weight(1f)
                                    )
                                    ScoreCardThemeStyle.values().forEach { styleOpt ->
                                        FilterChip(
                                            selected = cardStyle == styleOpt,
                                            onClick = { cardStyle = styleOpt },
                                            label = { Text(styleOpt.label) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (styleOpt == ScoreCardThemeStyle.DAY) Icons.Default.LightMode else Icons.Default.DarkMode,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            modifier = Modifier.testTag("card_style_${styleOpt.name.lowercase()}")
                                        )
                                    }
                                }

                                HorizontalDivider()

                                // Monthly change toggle
                                val hasPrevious = monthlyReport.previousScore != null
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Show monthly change", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                        Text(
                                            text = if (hasPrevious) {
                                                "Shows ${FinancialCalculator.formatMonthDeltaForCard(monthlyReport.scoreDelta)}"
                                            } else {
                                                "Available once a previous month score exists"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = showMonthlyChange && hasPrevious,
                                        enabled = hasPrevious,
                                        onCheckedChange = { showMonthlyChange = it },
                                        modifier = Modifier.testTag("toggle_show_monthly_change")
                                    )
                                }

                                // Component bars toggle (level only: Low / Okay / Good)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Show 4 component bars", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                        Text(
                                            text = "Level only (Low / Okay / Good) — zero numbers",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = showComponentBars,
                                        onCheckedChange = { showComponentBars = it },
                                        modifier = Modifier.testTag("toggle_show_component_bars")
                                    )
                                }

                                // Optional nickname toggle & field (OFF & empty by default)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Show nickname (optional)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                        Text(
                                            text = "Off and empty by default",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = showNickname,
                                        onCheckedChange = { showNickname = it },
                                        modifier = Modifier.testTag("toggle_show_nickname")
                                    )
                                }

                                if (showNickname) {
                                    OutlinedTextField(
                                        value = nicknameInput,
                                        onValueChange = { next ->
                                            nicknameInput = FinancialCalculator.sanitizeNicknameForShareCard(next)
                                        },
                                        label = { Text("Nickname (letters only, max 20)") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("share_card_nickname_input")
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Buttons (User-triggered only)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        ScoreCardRenderer.saveBitmapToGalleryZeroPermission(context, previewBitmap)
                                    }
                                    result.onSuccess { savedUri ->
                                        if (savedUri == null) {
                                            pendingSafBitmap = previewBitmap
                                            safPngLauncher.launch("growmint_score_${cardData.score}.png")
                                        } else {
                                            snackbarMessage = "Saved 1080×1080 PNG to Pictures/GrowMint!"
                                        }
                                    }.onFailure {
                                        pendingSafBitmap = previewBitmap
                                        safPngLauncher.launch("growmint_score_${cardData.score}.png")
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("share_card_download_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download image (1080×1080 PNG)", fontWeight = FontWeight.Bold)
                        }

                        if (!profile.isUnder18) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        scope.launch {
                                            val shareUri = withContext(Dispatchers.IO) {
                                                ScoreCardRenderer.writeBitmapToShareCache(context, previewBitmap)
                                            }
                                            val shareText = FinancialCalculator.buildPrivacySafeShareText(
                                                card = cardData,
                                                includeReferralLink = !referralState.myReferralCode.isNullOrBlank(),
                                                referralCode = referralState.myReferralCode
                                            )
                                            val chooser = if (shareUri != null) {
                                                ScoreCardRenderer.buildScoreCardShareChooser(context, shareUri, shareText)
                                            } else {
                                                null
                                            }
                                            if (chooser != null) {
                                                context.startActivity(chooser)
                                            } else {
                                                // Fallback to download + copy link when share sheet is unavailable
                                                withContext(Dispatchers.IO) {
                                                    ScoreCardRenderer.saveBitmapToGalleryZeroPermission(context, previewBitmap)
                                                }
                                                clipboard.setText(AnnotatedString(linkToCopy))
                                                snackbarMessage = "No share handler found — saved image & copied link instead."
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("share_card_share_btn")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(linkToCopy))
                                        snackbarMessage = "Privacy-safe link copied!"
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("share_card_copy_link_btn")
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy link", fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = onOpenInviteFriend,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("share_card_open_invite_btn")
                            ) {
                                Icon(Icons.Default.PersonAddAlt1, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Invite a friend & view Milestone Badges (${referralState.referralCount} joined)")
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteFriendScreen(
    profile: UserProfile,
    referralState: ReferralState,
    monthlyReport: MonthlyImprovementReport,
    monthlySnapshotsCount: Int,
    onUpdateCustomMessage: (String) -> Unit,
    onRegenerateCode: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var messageDraft by remember(referralState.customInviteMessage) {
        mutableStateOf(referralState.customInviteMessage)
    }

    val badges = remember(
        referralState.referralCount,
        monthlyReport.currentScore,
        monthlySnapshotsCount
    ) {
        FinancialCalculator.calculateMilestoneBadges(
            referralCount = referralState.referralCount,
            currentScore = monthlyReport.currentScore,
            monthlySnapshotsCount = maxOf(1, monthlySnapshotsCount)
        )
    }

    val referralLink = remember(referralState.myReferralCode) {
        FinancialCalculator.buildReferralLink(referralState.myReferralCode)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Invite a Friend", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Anonymous count only • No PII stored",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("invite_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                ) { Text(msg) }
            }
        }
    ) { innerPadding ->
        if (profile.isUnder18 || !referralState.sharingEnabled) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("invite_unavailable_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (profile.isUnder18) "Unavailable in Guided Mode (<18)" else "Sharing & referrals are turned off",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (profile.isUnder18) {
                                "For under-18 privacy, referral invites are disabled in Guided Mode."
                            } else {
                                "Turn sharing back on under Settings > Sharing and referrals to use referral invites."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Go Back")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hero Counter & Anonymous Link Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MintPrimary.copy(alpha = 0.14f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Friends who joined: ${referralState.referralCount}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MintPrimary,
                                    modifier = Modifier.testTag("invite_friends_joined_count")
                                )
                                Surface(
                                    color = MintPrimary.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Counts only",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MintPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Your invite uses a random anonymous code. GrowMint counts completed setups without storing who you invited or any money numbers.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (referralState.myReferralCode.isNullOrBlank()) {
                                Text(
                                    text = "Your referral code and count were deleted.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Button(
                                    onClick = onRegenerateCode,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("invite_regenerate_code_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Generate anonymous referral code", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Code: ${referralState.myReferralCode}",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MintPrimary
                                        )
                                        Text(
                                            text = referralLink,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.testTag("invite_referral_link_text")
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(referralLink))
                                            snackbarMessage = "Invite link copied!"
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("invite_copy_link_btn"),
                                        colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Copy link", fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val safeMsg = FinancialCalculator.sanitizeInviteMessage(messageDraft)
                                            onUpdateCustomMessage(safeMsg)
                                            val fullInviteText = "$safeMsg $referralLink\n${FinancialCalculator.SCORE_CARD_DISCLAIMER}"
                                            val chooser = ScoreCardRenderer.buildTextInviteShareChooser(context, fullInviteText)
                                            if (chooser != null) {
                                                context.startActivity(chooser)
                                            } else {
                                                clipboard.setText(AnnotatedString(fullInviteText))
                                                snackbarMessage = "No share handler available — copied message & link instead."
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("invite_share_btn")
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Share", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Editable pre-written message (never contains amounts/PII)
                if (!referralState.myReferralCode.isNullOrBlank()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Editable Invite Message",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    TextButton(
                                        onClick = {
                                            messageDraft = ReferralState.DEFAULT_INVITE_MESSAGE
                                            onUpdateCustomMessage(ReferralState.DEFAULT_INVITE_MESSAGE)
                                        }
                                    ) {
                                        Text("Reset")
                                    }
                                }
                                OutlinedTextField(
                                    value = messageDraft,
                                    onValueChange = { next ->
                                        messageDraft = next
                                    },
                                    label = { Text("Message (edited before sharing)") },
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("invite_message_input")
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Money amounts and email addresses are stripped automatically.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = {
                                            val safe = FinancialCalculator.sanitizeInviteMessage(messageDraft)
                                            messageDraft = safe
                                            onUpdateCustomMessage(safe)
                                            snackbarMessage = "Invite message saved."
                                        }
                                    ) {
                                        Text("Save text")
                                    }
                                }
                            }
                        }
                    }
                }

                // Milestone Badges (In-App Only, no money rewards, no leaderboards)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("milestone_badges_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MintAccentGold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Milestone Badges (In-App Only)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            badges.forEach { badge ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (badge.isUnlocked) FinanceGreen.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("badge_${badge.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (badge.isUnlocked) FinanceGreen.copy(alpha = 0.22f)
                                                        else MaterialTheme.colorScheme.surfaceVariant
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (badge.isUnlocked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                    contentDescription = if (badge.isUnlocked) "Unlocked" else "Locked",
                                                    tint = if (badge.isUnlocked) FinanceGreen else Color.Gray,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = badge.title,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                                )
                                                Text(
                                                    text = badge.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (badge.isUnlocked) "Unlocked" else badge.progressText,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (badge.isUnlocked) FinanceGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Text(
                                text = FinancialCalculator.BADGE_REWARDS_NOTE,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("milestone_rewards_note")
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}
