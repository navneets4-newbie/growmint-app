package com.example.ui.split

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import com.example.model.*
import com.example.data.FinancialCalculator
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.FormattedAmountDisplay
import com.example.ui.components.PersistentDisclaimerBanner
import com.example.ui.components.SafetyBufferGateCard
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartSplitScreen(
    profile: UserProfile,
    envelopes: List<VirtualEnvelope>,
    transferHistory: List<TransferRecord>,
    presets: List<SplitPreset>,
    bufferStatus: BufferGateStatus = FinancialCalculator.calculateBufferGateStatus(profile, envelopes),
    onSaveRules: (List<SplitEnvelopeRule>) -> Unit,
    onExecuteSplit: (Double, String) -> Unit,
    onUndoLastTransfer: () -> Boolean,
    onReleaseSalary: ((Double) -> Boolean)? = null,
    prefilledIncomeAmount: Double? = null,
    prefilledIncomeSource: String? = null,
    modifier: Modifier = Modifier
) {
    val initialSafeRules = remember(profile.splitRules, profile.isUnder18) {
        if (profile.isUnder18) {
            val investPct = profile.splitRules.filter { it.envelopeType == EnvelopeType.INVESTMENTS }.sumOf { it.percentage }
            val filtered = profile.splitRules.filter { it.envelopeType != EnvelopeType.INVESTMENTS }.toMutableList()
            if (investPct > 0) {
                val savingsIdx = filtered.indexOfFirst { it.envelopeType == EnvelopeType.SAVINGS }
                if (savingsIdx >= 0) {
                    filtered[savingsIdx] = filtered[savingsIdx].copy(percentage = filtered[savingsIdx].percentage + investPct)
                } else {
                    filtered.add(SplitEnvelopeRule(EnvelopeType.SAVINGS, investPct))
                }
            }
            filtered
        } else {
            profile.splitRules
        }
    }
    var rulesState by remember(initialSafeRules) { mutableStateOf(initialSafeRules) }
    val visibleEnvelopes = remember(envelopes, profile.isUnder18) {
        if (profile.isUnder18) envelopes.filter { it.type != EnvelopeType.INVESTMENTS } else envelopes
    }
    var incomeInput by remember {
        mutableStateOf((prefilledIncomeAmount ?: profile.monthlyIncome).toString())
    }
    var sourceInput by remember {
        mutableStateOf(prefilledIncomeSource?.takeIf { it.isNotBlank() } ?: "Monthly Salary")
    }
    LaunchedEffect(prefilledIncomeAmount, prefilledIncomeSource) {
        if (prefilledIncomeAmount != null && prefilledIncomeAmount > 0.0) {
            incomeInput = prefilledIncomeAmount.toString()
        }
        if (!prefilledIncomeSource.isNullOrBlank()) {
            sourceInput = prefilledIncomeSource
        }
    }
    var showBankGuide by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    // Confirmation dialog states
    var pendingPreset by remember { mutableStateOf<SplitPreset?>(null) }
    var showConfirmSaveRules by remember { mutableStateOf(false) }
    var pendingSplit by remember { mutableStateOf<Pair<Double, String>?>(null) }
    var showConfirmUndo by remember { mutableStateOf(false) }
    var showConfirmReleaseSalary by remember { mutableStateOf(false) }

    val totalPercentage = rulesState.sumOf { it.percentage }
    val isValid100Percent = totalPercentage == 100

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Paycheck Auto-Divider",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Sort your income into clear buckets for bills, savings, and fun",
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
                    action = {
                        TextButton(onClick = { snackbarMessage = null }) { Text("Dismiss") }
                    }
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

            // Safety-First Buffer Gate / Guided Mode Status
            item {
                SafetyBufferGateCard(
                    bufferStatus = bufferStatus,
                    currencyCode = profile.currencyCode
                )
            }

            // Irregular Income Holding Pot quick release
            if (profile.isIrregularIncome && onReleaseSalary != null) {
                val runwayMonths = FinancialCalculator.calculateHoldingPotRunwayMonths(
                    profile.holdingPotBalance,
                    profile.monthlySalaryTarget
                )
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MintPrimary.copy(alpha = 0.14f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Holding Pot: ${CurrenciesAndCountries.format(profile.holdingPotBalance, profile.currencyCode)}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MintPrimary
                                )
                                Text(
                                    text = "Runway: ${String.format("%.1f", runwayMonths)} mos at ${CurrenciesAndCountries.format(profile.monthlySalaryTarget, profile.currencyCode)}/mo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { showConfirmReleaseSalary = true },
                                enabled = profile.holdingPotBalance >= profile.monthlySalaryTarget && profile.monthlySalaryTarget > 0.0,
                                colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                            ) {
                                Text("Release Salary", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Real Bank Auto-Transfer Guide Card (Requirement C)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bank_setup_guide_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showBankGuide = !showBankGuide },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MintAccentGold.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalance,
                                        contentDescription = null,
                                        tint = MintAccentGold,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "How to copy this in your real bank",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "3 simple steps to automate your real-world money",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (showBankGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle Guide"
                            )
                        }

                        AnimatedVisibility(visible = showBankGuide) {
                            Column(modifier = Modifier.padding(top = 14.dp)) {
                                Text(
                                    text = "You don't need any app to touch your bank. You can set this up yourself in 5 minutes:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                GuideStep(
                                    stepNum = "1",
                                    title = "Open your real bank app or website",
                                    desc = "Look for 'Scheduled Transfers', 'Standing Orders', or 'Recurring Auto-Pay'."
                                )
                                GuideStep(
                                    stepNum = "2",
                                    title = "Pick the day after your paycheck",
                                    desc = "Schedule recurring transfers for 1 day after your salary arrives so the money moves automatically before you are tempted to spend it."
                                )
                                GuideStep(
                                    stepNum = "3",
                                    title = "Send each share to the right account",
                                    desc = if (profile.isUnder18) {
                                        "Keep your Essentials (${rulesState.find { it.envelopeType == EnvelopeType.NEEDS }?.percentage ?: 50}%) in your main account, and move Savings (${rulesState.find { it.envelopeType == EnvelopeType.SAVINGS }?.percentage ?: 20}%) to a dedicated savings pocket."
                                    } else {
                                        "Keep your Essentials (${rulesState.find { it.envelopeType == EnvelopeType.NEEDS }?.percentage ?: 50}%) in your main checking account, send Savings (${rulesState.find { it.envelopeType == EnvelopeType.SAVINGS }?.percentage ?: 20}%) to a high-yield savings account, and move your Investments (${rulesState.find { it.envelopeType == EnvelopeType.INVESTMENTS }?.percentage ?: 10}%) to your investment account."
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Presets Selector
            item {
                Text(
                    text = "Quick Starter Presets",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        OutlinedButton(
                            onClick = { pendingPreset = preset },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = preset.name.substringBefore(" "),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Allocation Rules (100% Validation Rule)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rules_config_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Allocation Rules",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Surface(
                                color = if (isValid100Percent) FinanceGreen.copy(alpha = 0.2f) else FinanceRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Total: $totalPercentage% ${if (isValid100Percent) "✓ (Valid)" else "⚠ (Must be 100%)"}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isValid100Percent) FinanceGreen else FinanceRed,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (!isValid100Percent) {
                            Text(
                                text = "Allocation must equal exactly 100% to save or trigger splits (currently off by ${100 - totalPercentage}%).",
                                style = MaterialTheme.typography.bodySmall,
                                color = FinanceRed,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        rulesState.forEachIndexed { idx, rule ->
                            val isLockedInvestRule = rule.envelopeType == EnvelopeType.INVESTMENTS && !bufferStatus.isInvestingUnlocked
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color(rule.envelopeType.defaultColorHex))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isLockedInvestRule) {
                                                "${rule.envelopeType.displayName} (Locked -> Buffer)"
                                            } else {
                                                rule.envelopeType.displayName
                                            },
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                    Text(
                                        text = "${rule.percentage}%",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Slider(
                                    value = rule.percentage.toFloat(),
                                    onValueChange = { newVal ->
                                        val updated = rulesState.toMutableList()
                                        updated[idx] = rule.copy(percentage = newVal.toInt())
                                        rulesState = updated
                                    },
                                    enabled = !isLockedInvestRule,
                                    valueRange = 0f..100f,
                                    steps = 19,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(rule.envelopeType.defaultColorHex),
                                        activeTrackColor = Color(rule.envelopeType.defaultColorHex)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (isValid100Percent) {
                                    showConfirmSaveRules = true
                                }
                            },
                            enabled = isValid100Percent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_rules_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save 100% Rules")
                        }
                    }
                }
            }

            // Income Deposit & Live Split Preview
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("execute_split_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Log Deposit & Auto-Split",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (profile.isIrregularIncome) "Irregular Mode: Split variable payouts or released salary into buckets" else "Regular Mode: Preview & auto-route income into virtual envelopes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = incomeInput,
                                onValueChange = { incomeInput = it },
                                label = { Text("Amount (${profile.currencyCode})") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("deposit_amount_input"),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = sourceInput,
                                onValueChange = { sourceInput = it },
                                label = { Text("Note / Source") },
                                modifier = Modifier.weight(1.3f),
                                singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val amt = incomeInput.toDoubleOrNull() ?: 0.0

                        Text(
                            text = "SPLIT PREVIEW (${if (isValid100Percent) "100% Rules" else "Rules Invalid"})",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        rulesState.forEach { r ->
                            val splitValue = (amt * r.percentage) / 100.0
                            val effectiveLabel = if (r.envelopeType == EnvelopeType.INVESTMENTS && !bufferStatus.isInvestingUnlocked) {
                                if (profile.isUnder18) "${EnvelopeType.SAVINGS.displayName} (Guided Mode)"
                                else "${EnvelopeType.EMERGENCY_FUND.displayName} (Buffer Gate)"
                            } else {
                                r.envelopeType.displayName
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "$effectiveLabel (${r.percentage}%)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                FormattedAmountDisplay(
                                    amount = splitValue,
                                    currencyCode = profile.currencyCode,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (isValid100Percent && amt > 0) {
                                    pendingSplit = amt to sourceInput
                                }
                            },
                            enabled = isValid100Percent && amt > 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("confirm_split_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Review & Move Into Envelopes", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Virtual Envelopes Status & Goal Progress
            item {
                Text(
                    text = "Virtual Envelopes & Goal Progress",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(visibleEnvelopes) { env ->
                val connectedGoal = profile.goals.find { it.envelopeType == env.type }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(env.type.defaultColorHex))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = env.name, fontWeight = FontWeight.Bold)
                            }
                            FormattedAmountDisplay(
                                amount = env.balance,
                                currencyCode = profile.currencyCode,
                                secondaryCurrency = profile.secondaryCurrency,
                                manualExchangeRate = profile.manualExchangeRate,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        if (connectedGoal != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val progress = if (connectedGoal.targetAmount > 0) minOf(1.0, env.balance / connectedGoal.targetAmount).toFloat() else 0f
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Goal: ${connectedGoal.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${String.format("%.0f", progress * 100)}% of ${CurrenciesAndCountries.format(connectedGoal.targetAmount, profile.currencyCode)} (${connectedGoal.targetDate})",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(env.type.defaultColorHex),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }

            // Transfer History & Undo Button (Requirement C)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transfer History (${transferHistory.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (transferHistory.any { it.isUndoable }) {
                        OutlinedButton(
                            onClick = { showConfirmUndo = true },
                            modifier = Modifier.testTag("undo_transfer_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Undo Last")
                        }
                    }
                }
            }

            if (transferHistory.isEmpty()) {
                item {
                    Text(
                        text = "No transfers recorded yet. Log an income split above to start tracking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(transferHistory.reversed().take(10)) { t ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = t.description, fontWeight = FontWeight.Bold)
                                val breakdownStr = t.envelopeAllocations.entries.joinToString(" • ") { (k, v) ->
                                    "$k: ${CurrenciesAndCountries.format(v, profile.currencyCode)}"
                                }
                                Text(
                                    text = breakdownStr,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FormattedAmountDisplay(
                                amount = t.totalAmount,
                                currencyCode = profile.currencyCode,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = FinanceGreen
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    pendingPreset?.let { preset ->
        val adjustedRules = if (!bufferStatus.isInvestingUnlocked) {
            preset.rules.map { r ->
                if (r.envelopeType == EnvelopeType.INVESTMENTS) {
                    r.copy(envelopeType = if (profile.isUnder18) EnvelopeType.SAVINGS else EnvelopeType.EMERGENCY_FUND)
                } else r
            }
        } else {
            preset.rules
        }
        ConfirmActionDialog(
            title = "Apply '${preset.name}'?",
            summaryLines = adjustedRules.map { "${it.envelopeType.displayName}: ${it.percentage}%" } +
                (if (!bufferStatus.isInvestingUnlocked) listOf("Investing share automatically redirected until buffer is met.") else emptyList()),
            confirmLabel = "Apply Preset",
            whyText = if (!bufferStatus.isInvestingUnlocked) "Safety-first rule redirects investing splits until your emergency buffer target is met." else "Updates your Auto-Split allocation percentages.",
            backingNumbers = adjustedRules.joinToString(", ") { "${it.envelopeType.displayName} ${it.percentage}%" },
            ruleId = if (!bufferStatus.isInvestingUnlocked) com.example.data.RuleRegistry.RULE_BUFFER_FIRST else null,
            activityCategory = "Allocation",
            onConfirm = {
                rulesState = adjustedRules
                onSaveRules(adjustedRules)
                snackbarMessage = "Applied preset: ${preset.name}"
                pendingPreset = null
            },
            onDismiss = { pendingPreset = null }
        )
    }

    if (showConfirmSaveRules) {
        ConfirmActionDialog(
            title = "Save Split Rules?",
            summaryLines = rulesState.map { "${it.envelopeType.displayName}: ${it.percentage}%" },
            confirmLabel = "Save Rules",
            whyText = "Saves your custom 100% envelope split rules for future income.",
            backingNumbers = rulesState.joinToString(", ") { "${it.envelopeType.displayName} ${it.percentage}%" },
            activityCategory = "Allocation",
            onConfirm = {
                onSaveRules(rulesState)
                snackbarMessage = "Rules saved successfully!"
                showConfirmSaveRules = false
            },
            onDismiss = { showConfirmSaveRules = false }
        )
    }

    pendingSplit?.let { (amt, src) ->
        ConfirmActionDialog(
            title = "Split Virtual Deposit?",
            summaryLines = listOf(
                "Total Deposit: ${CurrenciesAndCountries.format(amt, profile.currencyCode)} ($src)"
            ) + rulesState.map { r ->
                val valAmt = (amt * r.percentage) / 100.0
                val label = if (r.envelopeType == EnvelopeType.INVESTMENTS && !bufferStatus.isInvestingUnlocked) {
                    if (profile.isUnder18) EnvelopeType.SAVINGS.displayName else "${EnvelopeType.EMERGENCY_FUND.displayName} (Buffer Gate)"
                } else r.envelopeType.displayName
                "$label (${r.percentage}%): ${CurrenciesAndCountries.format(valAmt, profile.currencyCode)}"
            },
            confirmLabel = "Split Now",
            whyText = if (!bufferStatus.isInvestingUnlocked) "Divides income by your rules; safety gate protects your emergency buffer first." else "Divides income across your virtual envelopes by your saved percentages.",
            backingNumbers = "Deposit: ${CurrenciesAndCountries.format(amt, profile.currencyCode)} ($src)",
            ruleId = if (!bufferStatus.isInvestingUnlocked) com.example.data.RuleRegistry.RULE_BUFFER_FIRST else null,
            activityCategory = "Allocation",
            onConfirm = {
                onExecuteSplit(amt, src)
                snackbarMessage = "Split ${CurrenciesAndCountries.format(amt, profile.currencyCode)} into envelopes!"
                pendingSplit = null
            },
            onDismiss = { pendingSplit = null }
        )
    }

    if (showConfirmUndo) {
        ConfirmActionDialog(
            title = "Undo Last Virtual Transfer?",
            summaryLines = listOf("Reverts the most recent split from your virtual envelopes and balance."),
            confirmLabel = "Undo Transfer",
            isDestructive = true,
            whyText = "Reverses the latest virtual envelope transfer.",
            activityCategory = "Allocation",
            onConfirm = {
                val undone = onUndoLastTransfer()
                if (undone) {
                    snackbarMessage = "Last transfer undone! Envelopes refunded."
                }
                showConfirmUndo = false
            },
            onDismiss = { showConfirmUndo = false }
        )
    }

    if (showConfirmReleaseSalary && onReleaseSalary != null) {
        ConfirmActionDialog(
            title = "Release Monthly Salary?",
            summaryLines = listOf(
                "Release ${CurrenciesAndCountries.format(profile.monthlySalaryTarget, profile.currencyCode)} from Holding Pot",
                "Remaining Pot: ${CurrenciesAndCountries.format(maxOf(0.0, profile.holdingPotBalance - profile.monthlySalaryTarget), profile.currencyCode)}",
                "Applies your Auto-Split buckets immediately."
            ),
            confirmLabel = "Release Salary",
            whyText = "Pay-yourself-a-salary releases a steady monthly payout from your irregular-income Holding Pot.",
            backingNumbers = "Pot: ${CurrenciesAndCountries.format(profile.holdingPotBalance, profile.currencyCode)} -> Salary: ${CurrenciesAndCountries.format(profile.monthlySalaryTarget, profile.currencyCode)}",
            activityCategory = "Allocation",
            onConfirm = {
                val ok = onReleaseSalary(profile.monthlySalaryTarget)
                snackbarMessage = if (ok) "Salary released and split!" else "Insufficient Holding Pot balance."
                showConfirmReleaseSalary = false
            },
            onDismiss = { showConfirmReleaseSalary = false }
        )
    }
}

@Composable
private fun GuideStep(stepNum: String, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = MintPrimary.copy(alpha = 0.2f),
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = stepNum, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MintPrimary)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
