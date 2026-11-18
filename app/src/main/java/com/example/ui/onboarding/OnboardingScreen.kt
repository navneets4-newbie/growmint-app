package com.example.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.example.ui.components.PersistentDisclaimerBanner
import com.example.ui.components.VirtualLedgerNoticeCard
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    initialProfile: UserProfile,
    onComplete: (UserProfile, Boolean) -> Unit,
    pendingReferralCode: String? = null,
    onInviteCodeChange: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(0) }
    val totalSteps = 6
    var linkBankRequested by remember { mutableStateOf(false) }

    // State holders
    var selectedCurrency by remember { mutableStateOf(initialProfile.currencyCode) }
    var selectedCountry by remember { mutableStateOf(initialProfile.country) }
    var monthlyIncomeText by remember { mutableStateOf(initialProfile.monthlyIncome.toString()) }
    var isIrregularIncome by remember { mutableStateOf(initialProfile.isIrregularIncome) }
    var startingBalanceText by remember { mutableStateOf(initialProfile.currentBalance.toString()) }

    // Expenses
    var housingExp by remember { mutableStateOf("1200") }
    var foodExp by remember { mutableStateOf("500") }
    var transportExp by remember { mutableStateOf("300") }
    var utilitiesExp by remember { mutableStateOf("200") }
    var healthExp by remember { mutableStateOf("150") }
    var entertainmentExp by remember { mutableStateOf("250") }
    var otherExp by remember { mutableStateOf("150") }

    // Debts
    val debtsList = remember {
        mutableStateListOf(
            DebtItem(name = "Credit Card Balance", totalAmount = 2400.0, interestRate = 19.9, minimumPayment = 90.0)
        )
    }

    // Goals
    val goalsList = remember {
        mutableStateListOf(
            GoalItem(name = "Emergency Buffer (3 mos)", targetAmount = 7500.0, targetDate = "Dec 2026", envelopeType = EnvelopeType.EMERGENCY_FUND),
            GoalItem(name = "Index Fund Seed", targetAmount = 5000.0, targetDate = "Aug 2027", envelopeType = EnvelopeType.INVESTMENTS)
        )
    }

    var riskComfort by remember { mutableStateOf(initialProfile.riskComfort) }
    var sideHours by remember { mutableFloatStateOf(initialProfile.sideIncomeHoursPerWeek.toFloat()) }

    // Eligibility Gate & Modes
    var isUnder18 by remember { mutableStateOf(initialProfile.isUnder18) }
    var isStudentMode by remember { mutableStateOf(initialProfile.isStudentMode) }
    var userSituation by remember {
        val validLabels = LifeStage.values().map { it.label }
        mutableStateOf(if (initialProfile.userSituation in validLabels) initialProfile.userSituation else LifeStage.EARLY_CAREER.label)
    }
    var selectedIncomeType by remember { mutableStateOf(initialProfile.incomeType) }
    var monthlySalaryTargetText by remember { mutableStateOf(initialProfile.monthlySalaryTarget.toString()) }
    var holdingPotText by remember { mutableStateOf(initialProfile.holdingPotBalance.toString()) }
    var localOnlyMode by remember { mutableStateOf(initialProfile.localOnlyMode) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "GrowMint Setup",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Step ${step + 1} of $totalSteps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    if (step > 0) {
                        IconButton(
                            onClick = { step-- },
                            modifier = Modifier.testTag("onboarding_back_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    PersistentDisclaimerBanner(modifier = Modifier.padding(bottom = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (step > 0) {
                            OutlinedButton(
                                onClick = { step-- },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("onboarding_prev_button")
                            ) {
                                Text("Previous")
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Button(
                            onClick = {
                                if (step < totalSteps - 1) {
                                    step++
                                } else {
                                    // Assemble profile
                                    val expenses = listOf(
                                        ExpenseItem(category = "Housing", amount = housingExp.toDoubleOrNull() ?: 0.0),
                                        ExpenseItem(category = "Food & Groceries", amount = foodExp.toDoubleOrNull() ?: 0.0),
                                        ExpenseItem(category = "Transportation", amount = transportExp.toDoubleOrNull() ?: 0.0),
                                        ExpenseItem(category = "Utilities", amount = utilitiesExp.toDoubleOrNull() ?: 0.0),
                                        ExpenseItem(category = "Healthcare", amount = healthExp.toDoubleOrNull() ?: 0.0),
                                        ExpenseItem(category = "Entertainment", amount = entertainmentExp.toDoubleOrNull() ?: 0.0),
                                        ExpenseItem(category = "Other Essentials", amount = otherExp.toDoubleOrNull() ?: 0.0)
                                    )

                                    val effectiveRules = if (isUnder18) {
                                        listOf(
                                            SplitEnvelopeRule(EnvelopeType.NEEDS, 50),
                                            SplitEnvelopeRule(EnvelopeType.FUN, 20),
                                            SplitEnvelopeRule(EnvelopeType.EMERGENCY_FUND, 15),
                                            SplitEnvelopeRule(EnvelopeType.SAVINGS, 15)
                                        )
                                    } else {
                                        initialProfile.splitRules
                                    }

                                    val parsedIncome = monthlyIncomeText.toDoubleOrNull()
                                        ?: if (selectedIncomeType == IncomeType.NONE) 0.0 else 3000.0
                                    val finalProfile = UserProfile(
                                        currencyCode = selectedCurrency,
                                        country = selectedCountry,
                                        monthlyIncome = parsedIncome,
                                        isIrregularIncome = isIrregularIncome || selectedIncomeType == IncomeType.IRREGULAR,
                                        currentBalance = startingBalanceText.toDoubleOrNull() ?: 2500.0,
                                        expenses = expenses,
                                        debts = debtsList.toList(),
                                        goals = goalsList.toList(),
                                        riskComfort = riskComfort,
                                        sideIncomeHoursPerWeek = sideHours.toInt(),
                                        isOnboarded = true,
                                        isUnder18 = isUnder18,
                                        isStudentMode = isStudentMode,
                                        userSituation = userSituation,
                                        incomeType = selectedIncomeType,
                                        monthlySalaryTarget = monthlySalaryTargetText.toDoubleOrNull() ?: 2500.0,
                                        holdingPotBalance = holdingPotText.toDoubleOrNull() ?: 0.0,
                                        localOnlyMode = localOnlyMode,
                                        splitRules = effectiveRules
                                    )
                                    onComplete(finalProfile, false)
                                }
                            },
                            modifier = Modifier
                                .weight(if (step > 0) 1.5f else 1f)
                                .height(48.dp)
                                .testTag("onboarding_next_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                        ) {
                            Text(
                                text = if (step == totalSteps - 1) "Enter GrowMint" else "Continue",
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = if (step == totalSteps - 1) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Step Progress Indicator
            LinearProgressIndicator(
                progress = { (step + 1) / totalSteps.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MintPrimary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (step) {
                0 -> StepWelcomeNotice(
                    isUnder18 = isUnder18,
                    onUnder18Change = {
                        isUnder18 = it
                        if (it) {
                            isStudentMode = true
                            userSituation = LifeStage.STUDENT.label
                        }
                    },
                    isStudentMode = isStudentMode,
                    onStudentModeChange = { isStudentMode = it },
                    userSituation = userSituation,
                    onSituationChange = {
                        userSituation = it
                        if (it == LifeStage.STUDENT.label) isStudentMode = true
                        if (it == LifeStage.SELF_EMPLOYED.label) {
                            selectedIncomeType = IncomeType.IRREGULAR
                            isIrregularIncome = true
                        }
                    },
                    pendingReferralCode = pendingReferralCode,
                    onInviteCodeChange = onInviteCodeChange
                )
                1 -> StepCurrencyAndCountry(
                    selectedCurrency = selectedCurrency,
                    onCurrencyChange = { selectedCurrency = it },
                    selectedCountry = selectedCountry,
                    onCountryChange = { selectedCountry = it }
                )
                2 -> StepIncomeBaseline(
                    currencyCode = selectedCurrency,
                    incomeText = monthlyIncomeText,
                    onIncomeChange = { monthlyIncomeText = it },
                    incomeType = selectedIncomeType,
                    onIncomeTypeChange = { nextType ->
                        selectedIncomeType = nextType
                        isIrregularIncome = (nextType == IncomeType.IRREGULAR)
                        if (nextType == IncomeType.NONE && (monthlyIncomeText.toDoubleOrNull() ?: 0.0) > 0.0) {
                            monthlyIncomeText = "0"
                        }
                    },
                    isIrregular = isIrregularIncome,
                    onIrregularChange = { irregular ->
                        isIrregularIncome = irregular
                        if (irregular) selectedIncomeType = IncomeType.IRREGULAR
                        else if (selectedIncomeType == IncomeType.IRREGULAR) selectedIncomeType = IncomeType.STEADY
                    },
                    balanceText = startingBalanceText,
                    onBalanceChange = { startingBalanceText = it },
                    monthlySalaryTargetText = monthlySalaryTargetText,
                    onMonthlySalaryTargetChange = { monthlySalaryTargetText = it },
                    holdingPotText = holdingPotText,
                    onHoldingPotChange = { holdingPotText = it }
                )
                3 -> StepExpensesAndDebts(
                    currencyCode = selectedCurrency,
                    housing = housingExp, onHousingChange = { housingExp = it },
                    food = foodExp, onFoodChange = { foodExp = it },
                    transport = transportExp, onTransportChange = { transportExp = it },
                    utilities = utilitiesExp, onUtilitiesChange = { utilitiesExp = it },
                    health = healthExp, onHealthChange = { healthExp = it },
                    entertainment = entertainmentExp, onEntertainmentChange = { entertainmentExp = it },
                    other = otherExp, onOtherChange = { otherExp = it },
                    debts = debtsList,
                    onAddDebt = { debtsList.add(it) },
                    onRemoveDebt = { debtsList.remove(it) }
                )
                4 -> StepGoalsAndRisk(
                    currencyCode = selectedCurrency,
                    goals = goalsList,
                    onAddGoal = { goalsList.add(it) },
                    onRemoveGoal = { goalsList.remove(it) },
                    riskComfort = riskComfort,
                    onRiskChange = { riskComfort = it },
                    sideHours = sideHours,
                    onSideHoursChange = { sideHours = it }
                )
                5 -> StepPrivacyAndLedger(
                    localOnlyMode = localOnlyMode,
                    onToggleLocalOnly = { localOnlyMode = it }
                )
            }
        }
    }
}

@Composable
private fun StepPrivacyAndLedger(
    localOnlyMode: Boolean,
    onToggleLocalOnly: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "Privacy & Ledger Setup",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Keep your numbers safe on your device and enter balances manually or by pasting transactions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (localOnlyMode) MintPrimary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Local-only mode (Recommended)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "All your financial data stays strictly on your phone. When asking the AI Coach, only minimal anonymized text is sent—never names or exact balances.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = localOnlyMode,
                        onCheckedChange = onToggleLocalOnly,
                        modifier = Modifier.testTag("onboarding_local_only_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Link bank (coming later)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Read-only bank linking will be added later through a licensed provider. Right now GrowMint uses manual entries and CSV/pasted transactions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StepWelcomeNotice(
    isUnder18: Boolean,
    onUnder18Change: (Boolean) -> Unit,
    isStudentMode: Boolean,
    onStudentModeChange: (Boolean) -> Unit,
    userSituation: String,
    onSituationChange: (String) -> Unit,
    pendingReferralCode: String? = null,
    onInviteCodeChange: (String?) -> Unit = {}
) {
    val lifeStages = LifeStage.values().toList()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MintPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                tint = MintPrimary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Welcome to GrowMint",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Your friendly guide to budgeting, saving, and growing your money safely.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Eligibility Gate & Mode Setup
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("eligibility_gate_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Eligibility & Your Situation",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MintPrimary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "I am under 18 (Guided Mode)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Learning Mode (under 18): hides investing and focuses on saving, budgeting & scam defense.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isUnder18,
                        onCheckedChange = onUnder18Change,
                        modifier = Modifier.testTag("onboarding_under18_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Student Mode",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Learning Mode focus: tailors examples for exam fees, stipends, and flexible student schedules.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isStudentMode,
                        onCheckedChange = onStudentModeChange,
                        modifier = Modifier.testTag("onboarding_student_switch")
                    )
                }

                Text(
                    text = "Which life stage fits you best right now?",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lifeStages.chunked(3).forEach { rowStages ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowStages.forEach { stage ->
                                FilterChip(
                                    selected = userSituation == stage.label,
                                    onClick = { onSituationChange(stage.label) },
                                    label = { Text(stage.label, fontSize = 11.sp, maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowStages.size < 3) {
                                repeat(3 - rowStages.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                lifeStages.find { it.label == userSituation }?.let { stage ->
                    Text(
                        text = stage.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        VirtualLedgerNoticeCard()

        Spacer(modifier = Modifier.height(12.dp))

        var manualCodeInput by remember(pendingReferralCode) { mutableStateOf(pendingReferralCode.orEmpty()) }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_referral_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Have an invite code? (Optional)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                if (!pendingReferralCode.isNullOrBlank()) {
                    Text(
                        text = "Invite code detected: $pendingReferralCode • Counts anonymously after you complete setup & eligibility check.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MintPrimary,
                        modifier = Modifier.testTag("onboarding_detected_ref_text")
                    )
                } else {
                    Text(
                        text = "Only the referral count is updated after setup. No personal details are stored, and eligibility rules still apply.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = manualCodeInput,
                    onValueChange = { raw ->
                        manualCodeInput = raw.uppercase()
                        onInviteCodeChange(raw.trim().ifBlank { null })
                    },
                    label = { Text("Friend's invite code or link (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_referral_code_input")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Here is how GrowMint helps you:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                FeatureBullet(
                    icon = Icons.Default.PieChart,
                    title = "Safety Buffer & Auto-Divider",
                    desc = "Builds your 1-month emergency buffer first before unlocking educational investing concepts."
                )
                FeatureBullet(
                    icon = Icons.Default.Psychology,
                    title = "Friendly AI Money Coach",
                    desc = "Explains habits, scam red flags, and savings concepts in plain English."
                )
                FeatureBullet(
                    icon = Icons.Default.Storefront,
                    title = "Side-Hustle & Irregular Pay Tools",
                    desc = "Smooth variable income with a Holding Pot and track net hustle profits."
                )
            }
        }
    }
}

@Composable
private fun FeatureBullet(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MintPrimary,
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepCurrencyAndCountry(
    selectedCurrency: String,
    onCurrencyChange: (String) -> Unit,
    selectedCountry: String,
    onCountryChange: (String) -> Unit
) {
    var currencySearch by remember { mutableStateOf("") }
    var countrySearch by remember { mutableStateOf("") }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showCountryDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "Your Currency & Country",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Pick the money currency you use every day, and select where you live so the AI can look up tax rules and bank accounts that actually exist in your country.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Currency Selector Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showCurrencyDialog = true }
                .testTag("currency_selector_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Your Currency",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val curr = CurrenciesAndCountries.ALL_CURRENCIES.find { it.code == selectedCurrency }
                    Text(
                        text = "${curr?.code ?: selectedCurrency} - ${curr?.name ?: ""} (${curr?.symbol ?: ""})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = "Change Currency")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Country Selector Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showCountryDialog = true }
                .testTag("country_selector_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Where You Live (Country)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selectedCountry,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = "Change Country")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MintSecondary.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MintPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Your country is only used to suggest official accounts (like 401k, ISA, or PPF). If the AI is ever unsure whether a rule applies to your country, it will write [uncertain] so you know to double-check.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    // Currency Dialog
    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Select Currency (ISO 4217)") },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp)) {
                    OutlinedTextField(
                        value = currencySearch,
                        onValueChange = { currencySearch = it },
                        label = { Text("Search code or name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("currency_search_input"),
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
                                        onCurrencyChange(c.code)
                                        showCurrencyDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "${c.code} (${c.symbol})", fontWeight = FontWeight.Bold)
                                    Text(text = c.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (c.code == selectedCurrency) {
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
                        label = { Text("Search country name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("country_search_input"),
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
                                        onCountryChange(country.name)
                                        showCountryDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = country.name, fontWeight = FontWeight.Medium)
                                if (country.name == selectedCountry) {
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
}

@Composable
private fun StepIncomeBaseline(
    currencyCode: String,
    incomeText: String,
    onIncomeChange: (String) -> Unit,
    incomeType: IncomeType,
    onIncomeTypeChange: (IncomeType) -> Unit,
    isIrregular: Boolean,
    onIrregularChange: (Boolean) -> Unit,
    balanceText: String,
    onBalanceChange: (String) -> Unit,
    monthlySalaryTargetText: String,
    onMonthlySalaryTargetChange: (String) -> Unit,
    holdingPotText: String,
    onHoldingPotChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "What Comes In (Income Type)",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Pick how your money arrives and your typical monthly amount. Your Money Coach uses both your life stage and income type.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Income Type",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MintPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))

        IncomeType.values().toList().chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { type ->
                    val selected = incomeType == type
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp)
                            .clickable { onIncomeTypeChange(type) }
                            .testTag("income_type_${type.name.lowercase()}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MintPrimary.copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = if (selected) CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(MintPrimary)
                        ) else null
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = type.label,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (selected) MintPrimary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = type.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Irregular Income Switch
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "My income changes month-to-month",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Enables Pay-Yourself-a-Salary mode: gigs land in a Holding Pot and release a steady monthly salary.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isIrregular,
                    onCheckedChange = onIrregularChange,
                    modifier = Modifier.testTag("irregular_income_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = incomeText,
            onValueChange = onIncomeChange,
            label = {
                Text(
                    when {
                        isIrregular -> "Average Monthly Earnings ($currencyCode)"
                        incomeType == IncomeType.ALLOWANCE -> "Monthly Allowance / Stipend ($currencyCode)"
                        incomeType == IncomeType.NONE -> "Monthly Income ($currencyCode, 0 if none)"
                        else -> "Monthly Take-Home Pay ($currencyCode)"
                    }
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = MintPrimary) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("monthly_income_input"),
            singleLine = true
        )

        if (isIrregular) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = monthlySalaryTargetText,
                onValueChange = onMonthlySalaryTargetChange,
                label = { Text("Steady Monthly Salary to Release ($currencyCode)") },
                supportingText = { Text("What you pay yourself from your Holding Pot each month.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MintPrimary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("monthly_salary_target_input"),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = holdingPotText,
                onValueChange = onHoldingPotChange,
                label = { Text("Starting Holding Pot Balance ($currencyCode)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                leadingIcon = { Icon(Icons.Default.Savings, contentDescription = null, tint = MintAccentGold) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("starting_holding_pot_input"),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = balanceText,
            onValueChange = onBalanceChange,
            label = { Text("Starting Practice Balance ($currencyCode)") },
            supportingText = { Text("How much virtual cash you want to start with in your practice journal.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null, tint = MintAccentGold) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("starting_balance_input"),
            singleLine = true
        )
    }
}

@Composable
private fun StepExpensesAndDebts(
    currencyCode: String,
    housing: String, onHousingChange: (String) -> Unit,
    food: String, onFoodChange: (String) -> Unit,
    transport: String, onTransportChange: (String) -> Unit,
    utilities: String, onUtilitiesChange: (String) -> Unit,
    health: String, onHealthChange: (String) -> Unit,
    entertainment: String, onEntertainmentChange: (String) -> Unit,
    other: String, onOtherChange: (String) -> Unit,
    debts: List<DebtItem>,
    onAddDebt: (DebtItem) -> Unit,
    onRemoveDebt: (DebtItem) -> Unit
) {
    var showAddDebtDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "What Goes Out (Bills & Debts)",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Tell us about your usual monthly spending and any loans. This helps us calculate your emergency safety cushion and payoff plan.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Estimated Monthly Living Costs ($currencyCode)",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = housing,
                onValueChange = onHousingChange,
                label = { Text("Housing/Rent") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = food,
                onValueChange = onFoodChange,
                label = { Text("Food & Groceries") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = transport,
                onValueChange = onTransportChange,
                label = { Text("Transportation") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = utilities,
                onValueChange = onUtilitiesChange,
                label = { Text("Utilities & Bills") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = health,
                onValueChange = onHealthChange,
                label = { Text("Healthcare") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = entertainment,
                onValueChange = onEntertainmentChange,
                label = { Text("Entertainment/Fun") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = other,
            onValueChange = onOtherChange,
            label = { Text("Other Expenses ($currencyCode)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Debts section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Outstanding Debts (${debts.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(
                onClick = { showAddDebtDialog = true },
                modifier = Modifier.testTag("add_debt_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Debt")
            }
        }

        if (debts.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = FinanceGreen.copy(alpha = 0.1f))
            ) {
                Text(
                    text = "No debts logged. This dramatically boosts your Financial Health Score!",
                    style = MaterialTheme.typography.bodySmall,
                    color = FinanceGreen,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            debts.forEach { debt ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
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
                            Text(text = debt.name, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Balance: $currencyCode ${debt.totalAmount} • Rate: ${debt.interestRate}% • Min: $currencyCode ${debt.minimumPayment}/mo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onRemoveDebt(debt) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = FinanceRed)
                        }
                    }
                }
            }
        }
    }

    if (showAddDebtDialog) {
        var debtName by remember { mutableStateOf("") }
        var debtAmount by remember { mutableStateOf("") }
        var debtRate by remember { mutableStateOf("") }
        var debtMin by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDebtDialog = false },
            title = { Text("Add Debt Item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = debtName,
                        onValueChange = { debtName = it },
                        label = { Text("Debt Name (e.g. Student Loan, Auto)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = debtAmount,
                        onValueChange = { debtAmount = it },
                        label = { Text("Total Balance ($currencyCode)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = debtRate,
                        onValueChange = { debtRate = it },
                        label = { Text("Annual Interest Rate (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = debtMin,
                        onValueChange = { debtMin = it },
                        label = { Text("Minimum Monthly Payment ($currencyCode)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = debtAmount.toDoubleOrNull() ?: 0.0
                        if (debtName.isNotBlank() && amount > 0) {
                            onAddDebt(
                                DebtItem(
                                    name = debtName,
                                    totalAmount = amount,
                                    interestRate = debtRate.toDoubleOrNull() ?: 0.0,
                                    minimumPayment = debtMin.toDoubleOrNull() ?: (amount * 0.02)
                                )
                            )
                            showAddDebtDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDebtDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StepGoalsAndRisk(
    currencyCode: String,
    goals: List<GoalItem>,
    onAddGoal: (GoalItem) -> Unit,
    onRemoveGoal: (GoalItem) -> Unit,
    riskComfort: RiskComfort,
    onRiskChange: (RiskComfort) -> Unit,
    sideHours: Float,
    onSideHoursChange: (Float) -> Unit
) {
    var showAddGoalDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "Goals, Risk & Hustle",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Finalize your profile so the AI Money Coach can calculate your growth plan and income split priorities.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Goals list
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Target Goals (${goals.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(
                onClick = { showAddGoalDialog = true },
                modifier = Modifier.testTag("add_goal_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Goal")
            }
        }

        goals.forEach { g ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
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
                        Text(text = g.name, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Target: $currencyCode ${g.targetAmount} by ${g.targetDate} (${g.envelopeType.displayName})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onRemoveGoal(g) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = FinanceRed)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Risk Comfort
        Text(
            text = "Risk Comfort Level",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        RiskComfort.values().forEach { r ->
            val isSelected = riskComfort == r
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onRiskChange(r) }
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MintPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                color = if (isSelected) MintSecondary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onRiskChange(r) },
                        colors = RadioButtonDefaults.colors(selectedColor = MintPrimary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = r.label, fontWeight = FontWeight.Bold)
                        Text(text = r.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Side income hours
        Text(
            text = "Hours/Week for Side Income: ${sideHours.toInt()} hrs",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Used by the AI Coach to recommend realistic earning channels (e.g. digital goods, freelancing, micro-business).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = sideHours,
            onValueChange = onSideHoursChange,
            valueRange = 0f..40f,
            steps = 7,
            colors = SliderDefaults.colors(thumbColor = MintPrimary, activeTrackColor = MintPrimary),
            modifier = Modifier.testTag("side_hours_slider")
        )
    }

    if (showAddGoalDialog) {
        var goalName by remember { mutableStateOf("") }
        var goalTarget by remember { mutableStateOf("") }
        var goalDate by remember { mutableStateOf("Dec 2027") }
        var goalCategory by remember { mutableStateOf("Custom") }
        val categories = listOf("House", "Car", "Trip", "Retirement", "Custom")

        AlertDialog(
            onDismissRequest = { showAddGoalDialog = false },
            title = { Text("Add Financial Goal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Category", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        categories.take(3).forEach { cat ->
                            FilterChip(
                                selected = goalCategory == cat,
                                onClick = { goalCategory = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        categories.drop(3).forEach { cat ->
                            FilterChip(
                                selected = goalCategory == cat,
                                onClick = { goalCategory = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = goalName,
                        onValueChange = { goalName = it },
                        label = { Text("Goal Name (e.g. House Downpayment)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = goalTarget,
                        onValueChange = { goalTarget = it },
                        label = { Text("Target Amount ($currencyCode)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = goalDate,
                        onValueChange = { goalDate = it },
                        label = { Text("Target Date (e.g. Nov 2027)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = goalTarget.toDoubleOrNull() ?: 0.0
                        if (goalName.isNotBlank() && amt > 0) {
                            onAddGoal(
                                GoalItem(
                                    name = goalName,
                                    targetAmount = amt,
                                    targetDate = goalDate,
                                    category = goalCategory
                                )
                            )
                            showAddGoalDialog = false
                        }
                    }
                ) {
                    Text("Add Goal")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddGoalDialog = false }) { Text("Cancel") }
            }
        )
    }
}
