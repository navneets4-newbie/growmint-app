package com.example.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiCoachManager
import com.example.data.FinancialCalculator
import com.example.model.*
import com.example.ui.components.PersistentDisclaimerBanner
import com.example.ui.components.SafetyBufferGateCard
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatIfSimulatorScreen(
    profile: UserProfile,
    bufferStatus: BufferGateStatus,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var monthlyText by remember { mutableStateOf("200") }
    var initialText by remember { mutableStateOf("500") }
    var yearsFloat by remember { mutableFloatStateOf(10f) }
    var selectedMix by remember { mutableStateOf("Equity Index / SIP") }

    val assetMixes = listOf("Equity Index / SIP", "Balanced Mix", "Gold / Conservative")

    val monthlyAmt = monthlyText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val initialAmt = initialText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val years = yearsFloat.toInt().coerceIn(1, 30)

    val report = remember(monthlyAmt, initialAmt, years, selectedMix) {
        FinancialCalculator.calculateSipRealityCheck(
            monthlyContribution = monthlyAmt,
            initialAmount = initialAmt,
            years = years,
            assetMix = selectedMix
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SIP & What-If Reality Check",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Compare poor, typical, and good outcomes side-by-side",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("whatif_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { PersistentDisclaimerBanner() }

            if (!bufferStatus.isInvestingUnlocked) {
                item {
                    SafetyBufferGateCard(
                        bufferStatus = bufferStatus,
                        currencyCode = profile.currencyCode
                    )
                }
            }

            if (profile.isUnder18) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("guided_mode_whatif_hidden_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Learning Mode (Under 18)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MintPrimary
                            )
                            Text(
                                text = "Investing simulations stay hidden in Guided Mode so you can focus on budgeting, saving habits, and scam defense first.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (!bufferStatus.isInvestingUnlocked) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("buffer_locked_whatif_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MintAccentGold.copy(alpha = 0.14f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MintAccentGold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Build your buffer first",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MintAccentGold
                                )
                            }
                            Text(
                                text = "Your SIP & investing simulator unlocks once your emergency buffer reaches ${CurrenciesAndCountries.format(bufferStatus.targetAmount, profile.currencyCode)}. Keep ${CurrenciesAndCountries.format(bufferStatus.remainingToTarget, profile.currencyCode)} more in your Emergency Safety Net first.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                // Mandatory Reality Disclaimer Banner
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sip_illustrative_disclaimer"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MintAccentGold.copy(alpha = 0.16f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MintAccentGold, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = report.disclaimer,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Controls Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Your Scenario Inputs",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = monthlyText,
                                    onValueChange = { monthlyText = it },
                                    label = { Text("Monthly SIP (${profile.currencyCode})") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("sip_monthly_input")
                                )
                                OutlinedTextField(
                                    value = initialText,
                                    onValueChange = { initialText = it },
                                    label = { Text("Initial Amount (${profile.currencyCode})") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("sip_initial_input")
                                )
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Time Horizon", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Text(text = "$years years", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MintPrimary)
                                }
                                Slider(
                                    value = yearsFloat,
                                    onValueChange = { yearsFloat = it },
                                    valueRange = 1f..30f,
                                    steps = 28,
                                    colors = SliderDefaults.colors(thumbColor = MintPrimary, activeTrackColor = MintPrimary),
                                    modifier = Modifier.testTag("sip_years_slider")
                                )
                            }

                            Text(text = "General Asset Type", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(assetMixes) { mix ->
                                    FilterChip(
                                        selected = selectedMix == mix,
                                        onClick = { selectedMix = mix },
                                        label = { Text(mix, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Three Side-by-Side Scenarios
                item {
                    Text(
                        text = "Three Outcomes Over $years Years",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Total money put in: ${CurrenciesAndCountries.format(report.totalInvested, profile.currencyCode)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sip_three_cases_row"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SipOutcomeMiniCard(
                            scenario = report.poorCase,
                            currencyCode = profile.currencyCode,
                            accent = FinanceRed,
                            modifier = Modifier.weight(1f)
                        )
                        SipOutcomeMiniCard(
                            scenario = report.typicalCase,
                            currencyCode = profile.currencyCode,
                            accent = MintPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        SipOutcomeMiniCard(
                            scenario = report.goodCase,
                            currencyCode = profile.currencyCode,
                            accent = FinanceGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Worst-Year Loss Example Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("worst_year_loss_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = FinanceRed.copy(alpha = 0.12f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TrendingDown, contentDescription = null, tint = FinanceRed)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Worst-Year Loss Example (-${report.worstYearLossPercent.toInt()}%)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = FinanceRed
                                )
                            }
                            Text(
                                text = "Even in long-term investing, bad years happen. If a -${report.worstYearLossPercent.toInt()}% drop hits around your Typical Case value (${CurrenciesAndCountries.format(report.typicalCase.finalValue, profile.currencyCode)}), your account could temporarily fall by about ${CurrenciesAndCountries.format(report.worstYearLossAmountOnTypical, profile.currencyCode)} in a single rough year.",
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp
                            )
                            Text(
                                text = "Risk note: Only use money you will not need for 5+ years after your emergency buffer is funded.",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = report.disclaimer,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SipOutcomeMiniCard(
    scenario: SipScenarioResult,
    currencyCode: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = scenario.label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = accent
            )
            Text(
                text = "${scenario.annualRatePercent}% / yr*",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = CurrenciesAndCountries.format(scenario.finalValue, currencyCode),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Gain: +${CurrenciesAndCountries.format(scenario.netGain, currencyCode)}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = accent
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScamCheckerScreen(
    profile: UserProfile,
    coachManager: AiCoachManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var messageInput by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ScamCheckResult?>(null) }

    val isInr = profile.currencyCode.equals("INR", ignoreCase = true) ||
        profile.country.equals("India", ignoreCase = true)

    val exampleChips = remember(isInr) {
        if (isInr) {
            listOf(
                "Fake UPI Collect" to "Congratulations! You won ₹15,000 cashback. Approve this UPI collect request and enter your UPI PIN to receive money into your account.",
                "KYC Expiry Call" to "URGENT: Your bank KYC has expired today. Account will be blocked in 2 hours! Click bit.ly/kyc-update-now or call 98000-12345.",
                "Pay to Receive" to "Your ₹8,500 government refund is approved. Please pay a ₹499 processing fee first to unlock payout.",
                "Fake Job Fee" to "Part-time work from home! Earn ₹3,000 daily liking videos. Pay ₹1,999 refundable security & training fee to start.",
                "Fake VIP Stock Group" to "Join our VIP WhatsApp & Telegram trading group! Guaranteed 40% weekly returns on options with zero loss risk."
            )
        } else {
            listOf(
                "Bank Lock SMS" to "URGENT: Your bank account has been suspended due to unusual activity. Verify within 2 hours at http://bit.ly/verify-acct or share your OTP.",
                "Pay to Receive" to "Congratulations! You won a $5,000 grant. Pay a $95 processing fee today to receive your money.",
                "Fake Job Offer" to "Remote job offer: $400/day easy tasks! Pay a $150 refundable registration & equipment fee via gift card or wire transfer.",
                "Guaranteed Crypto Group" to "Join our VIP Telegram crypto group! Guaranteed 35% monthly returns, zero risk, double your money in 14 days."
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Scam & Fraud Checker",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Spot red flags in messages, calls, or offers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("scam_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                // Mandatory Safety Notice
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scam_never_share_banner"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = FinanceRed.copy(alpha = 0.14f))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GppMaybe, contentDescription = null, tint = FinanceRed, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Never share OTP, PIN, or passwords.",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = FinanceRed
                            )
                            Text(
                                text = "Not even bank staff, police, or support need your PIN/OTP to send you money. Checks are educational and never 100% certain.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = if (isInr) "Common India / INR Scam Examples (Tap to test)" else "Common Scam Examples (Tap to test)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(exampleChips) { (label, sample) ->
                        SuggestionChip(
                            onClick = {
                                messageInput = sample
                                isChecking = true
                                scope.launch {
                                    result = coachManager.analyzeScamMessage(sample, profile)
                                    isChecking = false
                                }
                            },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    label = { Text("Paste SMS, WhatsApp/Telegram message, or describe the situation") },
                    placeholder = { Text("Example: Someone sent a UPI collect request saying I'll receive ₹5,000 after entering my PIN...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .testTag("scam_checker_input"),
                    maxLines = 6
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (messageInput.isNotBlank() && !isChecking) {
                            isChecking = true
                            scope.launch {
                                result = coachManager.analyzeScamMessage(messageInput, profile)
                                isChecking = false
                            }
                        }
                    },
                    enabled = messageInput.isNotBlank() && !isChecking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("check_scam_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking Warning Signs...")
                    } else {
                        Icon(Icons.Default.Security, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check Message for Red Flags", fontWeight = FontWeight.Bold)
                    }
                }
            }

            result?.let { res ->
                item {
                    val verdictColor = when (res.verdict) {
                        ScamVerdict.LIKELY_SCAM -> FinanceRed
                        ScamVerdict.SUSPICIOUS -> MintAccentGold
                        ScamVerdict.LOOKS_FINE -> FinanceGreen
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scam_result_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = verdictColor.copy(alpha = 0.12f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Verdict: ${res.verdict.label}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = verdictColor
                                )
                                Surface(
                                    color = verdictColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Educational Assessment",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                        color = verdictColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            HorizontalDivider()

                            Text(
                                text = "Red Flags Spotted",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            res.redFlags.forEach { flag ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = verdictColor, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = flag, style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            HorizontalDivider()

                            Text(
                                text = "Calm Next Steps",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            res.nextSteps.forEachIndexed { idx, step ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(
                                        text = "${idx + 1}.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MintPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = step, style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = res.safetyNote,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
