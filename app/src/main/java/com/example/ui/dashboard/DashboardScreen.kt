package com.example.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
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
import com.example.ToolSubScreen
import com.example.data.FinancialCalculator
import com.example.model.*
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.DataQualityBadgeRow
import com.example.ui.components.FormattedAmountDisplay
import com.example.ui.components.HealthScorePill
import com.example.ui.components.PersistentDisclaimerBanner
import com.example.ui.components.SafetyBufferGateCard
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    profile: UserProfile,
    envelopes: List<VirtualEnvelope>,
    healthReport: FinancialHealthReport,
    bankConnection: BankConnectionState,
    themeMode: AppThemeMode,
    detectedDeposits: List<DetectedDeposit>,
    subscriptionLeaks: List<SubscriptionLeak>,
    bufferStatus: BufferGateStatus,
    upcomingDeadlines: List<UpcomingDeadlineView>,
    weeklyScore: WeeklyMoneyScore,
    dailyLesson: MoneyLesson,
    noSpendStreakDays: Int,
    sideHustleReport: SideHustleReport,
    monthlyReport: MonthlyImprovementReport,
    netWorthSummary: NetWorthSummary,
    onToggleTheme: () -> Unit,
    onNavigateToSplit: () -> Unit,
    onNavigateToCoach: () -> Unit,
    onNavigateToSandbox: () -> Unit,
    onNavigateToBankLink: () -> Unit,
    onRefreshBankData: () -> Unit,
    onSimulateDeposit: (Double, String) -> Unit,
    onDismissDeposit: (String) -> Unit,
    onLogIncome: (Double, String) -> Unit,
    onLogExpense: (Double, String, String) -> Unit,
    onUpdateBufferTarget: (Double, Double?) -> Unit,
    onDepositToHoldingPot: (Double, String) -> Unit,
    onReleaseMonthlySalary: (Double) -> Boolean,
    onOpenTool: (ToolSubScreen) -> Unit,
    transactions: List<LedgerTransaction> = emptyList(),
    lastImportBatch: ImportBatchRecord? = null,
    pendingIncomeForSplit: Pair<Double, String>? = null,
    onOpenTransactions: () -> Unit = {},
    onOpenPasteImport: () -> Unit = {},
    onUndoLastImport: () -> Unit = {},
    onLaunchSplitForIncome: (Double, String) -> Unit = { _, _ -> },
    onDismissIncomePrompt: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showHealthDialog by remember { mutableStateOf(false) }
    var showQuickIncomeDialog by remember { mutableStateOf(false) }
    var showQuickExpenseDialog by remember { mutableStateOf(false) }
    var showEditBufferDialog by remember { mutableStateOf(false) }
    var showDepositPotDialog by remember { mutableStateOf(false) }

    // Confirmation dialog state for money-changing actions
    var pendingIncomeConfirm by remember { mutableStateOf<Pair<Double, String>?>(null) }
    var pendingExpenseConfirm by remember { mutableStateOf<Triple<Double, String, String>?>(null) }
    var pendingBufferConfirm by remember { mutableStateOf<Pair<Double, Double?>?>(null) }
    var pendingPotDepositConfirm by remember { mutableStateOf<Pair<Double, String>?>(null) }
    var showConfirmReleaseSalary by remember { mutableStateOf(false) }
    var dashboardNotice by remember { mutableStateOf<String?>(null) }

    val thirtyDaysAgo = remember { System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L) }
    val logged30DayExpenses = remember(transactions, thirtyDaysAgo) {
        transactions.filter { it.type.equals("EXPENSE", ignoreCase = true) && it.timestamp >= thirtyDaysAgo }
    }
    val logged30DayExpenseTotal = logged30DayExpenses.sumOf { it.amount }
    val logged30DayIncomeTotal = remember(transactions, thirtyDaysAgo) {
        transactions.filter { it.type.equals("INCOME", ignoreCase = true) && it.timestamp >= thirtyDaysAgo }.sumOf { it.amount }
    }
    val topSpendingCategories = remember(logged30DayExpenses) {
        logged30DayExpenses
            .groupBy { it.category.ifBlank { "General" } }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .entries
            .sortedByDescending { it.value }
            .take(4)
    }

    val baselineExpenses = profile.expenses.sumOf { it.amount } + profile.debts.sumOf { it.minimumPayment }
    val totalExpenses = maxOf(baselineExpenses, logged30DayExpenseTotal)
    val effectiveMonthlyIncome = maxOf(profile.monthlyIncome, logged30DayIncomeTotal)
    val effectiveExpenses = if (totalExpenses > 0) totalExpenses else 1000.0
    val emergencyEnv = envelopes.find { it.type == EnvelopeType.EMERGENCY_FUND }?.balance ?: 0.0
    val emergencyRunwayMonths = emergencyEnv / effectiveExpenses

    val totalDebts = profile.debts.sumOf { it.totalAmount }
    val totalEnvelopeAssets = envelopes.sumOf { it.balance }

    // Reconciliation logic: When bank is linked, real bank balance is source of truth!
    val realBankBalance = if (bankConnection.isLinked) {
        bankConnection.accounts.sumOf { it.currentBalance }
    } else {
        profile.currentBalance
    }
    val unallocatedCash = maxOf(0.0, realBankBalance - totalEnvelopeAssets)
    val hasReconciliationMismatch = bankConnection.isLinked && kotlin.math.abs(realBankBalance - totalEnvelopeAssets) > 1.0

    val netWorth = if (netWorthSummary.manualAssetsTotal > 0.0 || bankConnection.isLinked || netWorthSummary.ledgerCashBalanceTotal > 0.0) {
        netWorthSummary.netWorth
    } else {
        (realBankBalance + totalEnvelopeAssets) - totalDebts
    }
    val monthlySurplus = effectiveMonthlyIncome - totalExpenses
    val savingsRate = if (effectiveMonthlyIncome > 0) (monthlySurplus / effectiveMonthlyIncome) * 100.0 else 0.0

    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val lastSyncString = remember(bankConnection.lastSyncedAt) {
        bankConnection.lastSyncedAt?.let { timeFormat.format(Date(it)) } ?: "Never"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "GrowMint",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = buildString {
                                    if (profile.isUnder18) append("Guided Mode • ")
                                    else if (profile.isStudentMode) append("Student Mode • ")
                                    append("${profile.userSituation} • ${profile.country}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HealthScorePill(
                                score = monthlyReport.currentScore,
                                grade = monthlyReport.gradeLabel,
                                onClick = { onOpenTool(ToolSubScreen.SCORE_DETAIL) }
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            // Day / Night / Auto Theme Toggle Button
                            IconButton(
                                onClick = onToggleTheme,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .testTag("theme_toggle_header_btn")
                            ) {
                                val themeIcon = when (themeMode) {
                                    AppThemeMode.DAY -> Icons.Default.LightMode
                                    AppThemeMode.NIGHT -> Icons.Default.DarkMode
                                    AppThemeMode.AUTO -> Icons.Default.BrightnessAuto
                                }
                                Icon(
                                    imageVector = themeIcon,
                                    contentDescription = "Theme: ${themeMode.label}",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = {
            dashboardNotice?.let { msg ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = { TextButton(onClick = { dashboardNotice = null }) { Text("OK") } }
                ) { Text(msg) }
            }
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
                PersistentDisclaimerBanner()
            }

            // === 1. AUTO-DETECTED INCOMING DEPOSIT BANNER (Requirement 1) ===
            if (detectedDeposits.isNotEmpty()) {
                val latestDeposit = detectedDeposits.first()
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("detected_deposit_banner"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MintPrimary.copy(alpha = 0.15f)),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(MintPrimary)
                        )
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
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(MintPrimary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.AttachMoney, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Incoming Deposit Detected!",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MintPrimary
                                        )
                                        Text(
                                            text = "${CurrenciesAndCountries.format(latestDeposit.amount, profile.currencyCode)} from ${latestDeposit.sourceName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                IconButton(onClick = { onDismissDeposit(latestDeposit.transactionId) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Would you like to run your Paycheck Auto-Divider rules on this deposit now?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        onLogIncome(latestDeposit.amount, latestDeposit.sourceName)
                                        onDismissDeposit(latestDeposit.transactionId)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                                ) {
                                    Icon(Icons.Default.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Confirm & Split", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { onDismissDeposit(latestDeposit.transactionId) },
                                    modifier = Modifier.weight(0.7f)
                                ) {
                                    Text("Later")
                                }
                            }
                        }
                    }
                }
            }

            // === 1.5 INCOME RECEIVED SPLIT PROMPT & UNDO IMPORT BANNER ===
            if (pendingIncomeForSplit != null && pendingIncomeForSplit.first > 0.0) {
                val (incAmt, incSrc) = pendingIncomeForSplit
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_income_detected_banner"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MintPrimary.copy(alpha = 0.16f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Income received, ready to split it?",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MintPrimary
                                    )
                                    Text(
                                        text = "${CurrenciesAndCountries.format(incAmt, profile.currencyCode)} from $incSrc",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                IconButton(onClick = onDismissIncomePrompt) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        onDismissIncomePrompt()
                                        onLaunchSplitForIncome(incAmt, incSrc)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Split Income", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(onClick = onDismissIncomePrompt) {
                                    Text("Later")
                                }
                            }
                        }
                    }
                }
            }

            if (lastImportBatch != null && lastImportBatch.transactionIds.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_undo_import_banner"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MintAccentGold.copy(alpha = 0.14f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Imported ${lastImportBatch.transactionIds.size} tx (${lastImportBatch.sourceType})",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    onUndoLastImport()
                                    dashboardNotice = "Undid last import batch."
                                },
                                modifier = Modifier.testTag("home_undo_import_btn")
                            ) {
                                Text("Undo this import", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // === 2. DEADLINE BADGE & LOCAL LEDGER STATUS BAR ===
            val dueSoonDeadlines = upcomingDeadlines.filter { it.isDueSoon && !it.item.isPaid }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenTool(ToolSubScreen.CALENDAR) }
                        .testTag("due_soon_home_badge_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (dueSoonDeadlines.isNotEmpty()) MintAccentGold.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (dueSoonDeadlines.isNotEmpty()) FinanceRed.copy(alpha = 0.18f)
                                        else MintPrimary.copy(alpha = 0.18f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (dueSoonDeadlines.isNotEmpty()) Icons.Default.EventBusy else Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = if (dueSoonDeadlines.isNotEmpty()) FinanceRed else MintPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (dueSoonDeadlines.isNotEmpty()) {
                                            "${dueSoonDeadlines.first().item.title} (${dueSoonDeadlines.first().daysUntilDue}d left)"
                                        } else {
                                            "Next 30 Days: ${upcomingDeadlines.size} Deadline(s)"
                                        },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    if (dueSoonDeadlines.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(color = FinanceRed.copy(alpha = 0.18f), shape = RoundedCornerShape(6.dp)) {
                                            Text(
                                                text = "Due soon",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                                color = FinanceRed,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (profile.localOnlyMode) "Local-only ledger • Tap to view calendar & renewals"
                                    else "Manual ledger • Tap to manage deadlines & renewals",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "Open Calendar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // === 3. HERO BALANCE CARD (SOURCE OF TRUTH) ===
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_hero_balance_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOTAL VIRTUAL BALANCE",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                                color = MintPrimary
                            )
                            Surface(
                                color = MintPrimary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = profile.currencyCode,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MintPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        FormattedAmountDisplay(
                            amount = profile.currentBalance,
                            currencyCode = profile.currencyCode,
                            secondaryCurrency = profile.secondaryCurrency,
                            manualExchangeRate = profile.manualExchangeRate,
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showQuickIncomeDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("log_income_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                            ) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Log Pay", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { showQuickExpenseDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("log_expense_button")
                            ) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Log Spend")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onOpenPasteImport,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("home_paste_transactions_btn")
                            ) {
                                Icon(Icons.Default.ContentPasteGo, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paste transactions", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = onOpenTransactions,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("home_open_transactions_btn")
                            ) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Transactions (${transactions.size})", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // === 3.1 SAFETY-FIRST BUFFER GATE CARD ===
            item {
                SafetyBufferGateCard(
                    bufferStatus = bufferStatus,
                    currencyCode = profile.currencyCode,
                    onEditTarget = { showEditBufferDialog = true }
                )
            }

            // === 3.2 PAY-YOURSELF-A-SALARY HOLDING POT (IRREGULAR INCOME MODE) ===
            if (profile.isIrregularIncome) {
                val potRunwayMonths = FinancialCalculator.calculateHoldingPotRunwayMonths(
                    profile.holdingPotBalance,
                    profile.monthlySalaryTarget
                )
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("holding_pot_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MintPrimary.copy(alpha = 0.14f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "PAY-YOURSELF-A-SALARY POT",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                        color = MintPrimary
                                    )
                                    Text(
                                        text = CurrenciesAndCountries.format(profile.holdingPotBalance, profile.currencyCode),
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)
                                    )
                                }
                                Surface(color = MintPrimary.copy(alpha = 0.2f), shape = RoundedCornerShape(10.dp)) {
                                    Text(
                                        text = "${String.format(Locale.US, "%.1f", potRunwayMonths)} mo runway",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MintPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Monthly Salary Target: ${CurrenciesAndCountries.format(profile.monthlySalaryTarget, profile.currencyCode)}. Good months fill this pot; lean months still pay your steady salary.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showDepositPotDialog = true },
                                    modifier = Modifier.weight(1f).testTag("deposit_pot_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Payout", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { showConfirmReleaseSalary = true },
                                    enabled = profile.holdingPotBalance >= profile.monthlySalaryTarget && profile.monthlySalaryTarget > 0.0,
                                    modifier = Modifier.weight(1f).testTag("release_salary_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                                ) {
                                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Release Salary", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // === 3.3 MONTHLY REPORT, STREAKS & DAILY 2-MIN LESSON CARD ===
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenTool(ToolSubScreen.LESSONS_CHECKIN) }
                        .testTag("home_streaks_lessons_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Assessment, contentDescription = null, tint = MintAccentGold, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                val deltaLabel = if (monthlyReport.previousScore != null) {
                                    val sign = if (monthlyReport.scoreDelta >= 0) "+" else ""
                                    " ($sign${monthlyReport.scoreDelta} vs last mo)"
                                } else ""
                                Text(
                                    text = "Monthly Health ${monthlyReport.currentScore}/100$deltaLabel • $noSpendStreakDays-Day Streak",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = "Top Next Action: ${monthlyReport.top3Actions.firstOrNull()?.title ?: dailyLesson.title}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        DataQualityBadgeRow(notes = monthlyReport.dataQualityNotes)
                        Text(
                            text = "Today's 2-Min Lesson (${dailyLesson.topic}): ${dailyLesson.title}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // === 4. RECONCILIATION CARD (REAL BALANCE VS ENVELOPES) ===
            if (bankConnection.isLinked) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reconciliation_card"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (hasReconciliationMismatch) MintAccentGold.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (hasReconciliationMismatch) Icons.Default.Tune else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (hasReconciliationMismatch) MintAccentGold else FinanceGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Envelope Reconciliation",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Text(
                                    text = if (hasReconciliationMismatch) "Action Needed" else "Fully Reconciled",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (hasReconciliationMismatch) MintAccentGold else FinanceGreen
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(text = "Total In Envelopes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(text = CurrenciesAndCountries.format(totalEnvelopeAssets, profile.currencyCode), fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(text = "Unallocated Cash", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = CurrenciesAndCountries.format(unallocatedCash, profile.currencyCode),
                                        fontWeight = FontWeight.Bold,
                                        color = if (unallocatedCash > 0) MintPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            if (unallocatedCash > 0) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = onNavigateToSplit,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Allocate Unassigned Cash (${CurrenciesAndCountries.format(unallocatedCash, profile.currencyCode)})")
                                }
                            }
                        }
                    }
                }
            }

            // === 5. LEAK DETECTOR & RECURRING SUBSCRIPTIONS ===
            if (subscriptionLeaks.isNotEmpty()) {
                val totalLeakMonthly = subscriptionLeaks.sumOf { it.amount }
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("leak_detector_card"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = FinancePurple.copy(alpha = 0.12f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.WaterDrop, contentDescription = null, tint = FinancePurple, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Leak Detector (${subscriptionLeaks.size} Detected)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = FinancePurple
                                    )
                                }
                                Text(
                                    text = "${CurrenciesAndCountries.format(totalLeakMonthly, profile.currencyCode)}/mo",
                                    fontWeight = FontWeight.Bold,
                                    color = FinancePurple
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (bankConnection.isLinked) {
                                    "Found ${subscriptionLeaks.size} recurring subscriptions in your transaction & bank history. Trimming unused services frees up cash fast."
                                } else {
                                    "Found ${subscriptionLeaks.size} recurring subscriptions in your transaction history. Trimming unused services frees up cash fast."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            subscriptionLeaks.take(3).forEach { leak ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = leak.name, style = MaterialTheme.typography.bodySmall)
                                    Text(text = "${CurrenciesAndCountries.format(leak.amount, profile.currencyCode)} / mo", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                                }
                            }
                        }
                    }
                }
            }

            // === 6. KEY METRICS GRID ===
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricMiniCard(
                        title = "Net Worth",
                        amount = netWorth,
                        currencyCode = profile.currencyCode,
                        icon = Icons.Default.Timeline,
                        badge = if (totalDebts > 0) "Tap to view trend • Minus debt" else "Tap to view trend",
                        onClick = { onOpenTool(ToolSubScreen.NET_WORTH) },
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniCard(
                        title = "Income Kept",
                        customText = "${String.format("%.1f", savingsRate)}%",
                        icon = Icons.Default.PieChart,
                        badge = if (savingsRate >= 20.0) "Great! (Saving 20%+)" else "Goal: Keep 20%",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricMiniCard(
                        title = "Safety Cushion",
                        customText = "${String.format("%.1f", emergencyRunwayMonths)} months",
                        icon = Icons.Default.Shield,
                        badge = if (emergencyRunwayMonths >= 6.0) "Bills covered for 6+ mos" else "Target: 3-6 months",
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniCard(
                        title = "Money Health Score",
                        customText = "${monthlyReport.currentScore}/100",
                        icon = Icons.Default.Favorite,
                        badge = "${monthlyReport.gradeLabel} • Tap for inputs & history",
                        onClick = { onOpenTool(ToolSubScreen.SCORE_DETAIL) },
                        modifier = Modifier.weight(1f).testTag("dashboard_money_health_card")
                    )
                }
            }

            // === 7. CASHFLOW RATIO BAR ===
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                                text = "Monthly Cashflow Ratio",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (monthlySurplus >= 0) "+${profile.currencyCode} ${String.format("%.0f", monthlySurplus)} surplus" else "Deficit!",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (monthlySurplus >= 0) FinanceGreen else FinanceRed
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val income = maxOf(1.0, effectiveMonthlyIncome)
                        val expenseRatio = minOf(1.0, totalExpenses / income).toFloat()
                        val surplusRatio = maxOf(0.0, (income - totalExpenses) / income).toFloat()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(maxOf(0.01f, expenseRatio))
                                    .background(FinanceRed.copy(alpha = 0.8f))
                            )
                            if (surplusRatio > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(surplusRatio)
                                        .background(FinanceGreen)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Expenses: ${CurrenciesAndCountries.format(totalExpenses, profile.currencyCode)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Income: ${CurrenciesAndCountries.format(effectiveMonthlyIncome, profile.currencyCode)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (topSpendingCategories.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "30-Day Imported & Tracked Categories",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            topSpendingCategories.forEach { (cat, amt) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(cat, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = CurrenciesAndCountries.format(amt, profile.currencyCode),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // === 8. FEATURE NAVIGATION CARDS ===
            item {
                Text(
                    text = "GrowMint Tools",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                FeatureNavCard(
                    title = "Transactions, Quick-Add & Import",
                    subtitle = if (transactions.isEmpty()) "Quick-add in <3 taps, paste SMS/UPI text, or upload CSV statement"
                    else "${transactions.size} logged transaction(s) • Quick-add, paste SMS, or CSV",
                    icon = Icons.Default.ReceiptLong,
                    actionText = "Open",
                    onClick = onOpenTransactions
                )
            }

            item {
                FeatureNavCard(
                    title = "Side-Hustle Profit Tracker",
                    subtitle = if (sideHustleReport.monthlySummaries.isEmpty()) {
                        "Log gig income minus costs to see real profit & margins"
                    } else {
                        "Net profit: ${CurrenciesAndCountries.format(sideHustleReport.totalProfit, profile.currencyCode)} (${String.format(Locale.US, "%.1f", sideHustleReport.overallMarginPercent)}% margin)"
                    },
                    icon = Icons.Default.Storefront,
                    actionText = "Track",
                    onClick = { onOpenTool(ToolSubScreen.SIDE_HUSTLE) }
                )
            }

            item {
                FeatureNavCard(
                    title = "Deadline & Renewal Calendar",
                    subtitle = "Exam fees, rent, subscriptions & renewals for the next 30 days",
                    icon = Icons.Default.CalendarMonth,
                    actionText = "Open",
                    badge = if (upcomingDeadlines.any { it.isDueSoon && !it.item.isPaid }) "Due soon" else null,
                    onClick = { onOpenTool(ToolSubScreen.CALENDAR) }
                )
            }

            item {
                FeatureNavCard(
                    title = "Monthly Improvement Report",
                    subtitle = "Score delta vs last month, what improved/slipped, top 3 actions & weekly tab",
                    icon = Icons.Default.Assessment,
                    actionText = "Check-In",
                    badge = "${monthlyReport.currentScore}/100",
                    onClick = { onOpenTool(ToolSubScreen.LESSONS_CHECKIN) }
                )
            }

            if (!profile.isUnder18) {
                item {
                    FeatureNavCard(
                        title = "Debt Payoff Planner",
                        subtitle = if (profile.debts.isEmpty()) "Compare Snowball vs Avalanche payoff date & total interest"
                        else "${profile.debts.size} debt(s) • Side-by-side Snowball vs Avalanche",
                        icon = Icons.Default.CreditScore,
                        actionText = "Plan",
                        onClick = { onOpenTool(ToolSubScreen.DEBT_PLANNER) }
                    )
                }
            }

            item {
                FeatureNavCard(
                    title = "Net Worth & Monthly Trend",
                    subtitle = "Assets + linked accounts minus debts • Track monthly snapshots",
                    icon = Icons.Default.Timeline,
                    actionText = "View",
                    onClick = { onOpenTool(ToolSubScreen.NET_WORTH) }
                )
            }

            item {
                FeatureNavCard(
                    title = "Goal Planner",
                    subtitle = "House, Car, Trip, Retirement or Custom • Monthly savings needed & pace",
                    icon = Icons.Default.Flag,
                    actionText = "Goals",
                    badge = if (profile.goals.isNotEmpty()) "${profile.goals.size}" else null,
                    onClick = { onOpenTool(ToolSubScreen.GOAL_PLANNER) }
                )
            }

            item {
                FeatureNavCard(
                    title = "Scam & Fraud Checker",
                    subtitle = "Paste suspicious messages or UPI/job offers to spot red flags",
                    icon = Icons.Default.Security,
                    actionText = "Check",
                    onClick = { onOpenTool(ToolSubScreen.SCAM_CHECKER) }
                )
            }

            if (!profile.isUnder18) {
                item {
                    FeatureNavCard(
                        title = "SIP & What-If Reality Check",
                        subtitle = if (bufferStatus.isInvestingUnlocked) "Compare Poor, Typical & Good outcomes side-by-side + worst-year loss"
                        else "Locked until 1-month emergency buffer is met",
                        icon = Icons.Default.Calculate,
                        actionText = "Simulate",
                        badge = if (!bufferStatus.isInvestingUnlocked) "Locked" else null,
                        onClick = { onOpenTool(ToolSubScreen.WHAT_IF) }
                    )
                }
            }

            item {
                FeatureNavCard(
                    title = "Income Ideas Lab",
                    subtitle = "Practical side income paths matched to your schedule",
                    icon = Icons.Default.Lightbulb,
                    actionText = "Explore",
                    onClick = { onOpenTool(ToolSubScreen.IDEAS_LAB) }
                )
            }

            item {
                FeatureNavCard(
                    title = "Paycheck Auto-Divider",
                    subtitle = "Automatically sorts your pay into bills, savings, and guilt-free fun",
                    icon = Icons.AutoMirrored.Filled.CallSplit,
                    actionText = "Open Divider",
                    onClick = onNavigateToSplit
                )
            }

            item {
                FeatureNavCard(
                    title = "Friendly AI Money Coach",
                    subtitle = "Ask questions or get an easy educational roadmap for ${profile.country}",
                    icon = Icons.Default.Psychology,
                    actionText = "Chat with Coach",
                    onClick = onNavigateToCoach
                )
            }

            if (!profile.isUnder18) {
                item {
                    FeatureNavCard(
                        title = "Practice Trading Game",
                        subtitle = "Learn how markets work by practicing with play money — zero risk",
                        icon = Icons.Default.CandlestickChart,
                        actionText = "Play Game",
                        onClick = onNavigateToSandbox
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Health Score Breakdown Dialog
    if (showHealthDialog) {
        AlertDialog(
            onDismissRequest = { showHealthDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = MintPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Financial Health: ${healthReport.overallScore}/100")
                }
            },
            text = {
                Column(modifier = Modifier.heightIn(max = 450.dp)) {
                    Text(
                        text = healthReport.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(healthReport.breakdown.size) { idx ->
                            val item = healthReport.breakdown[idx]
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = item.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = item.impact,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.positive) FinanceGreen else FinanceRed
                                    )
                                }
                                Text(
                                    text = item.explanation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHealthDialog = false }) { Text("Got it") }
            }
        )
    }

    // Quick Income Dialog
    if (showQuickIncomeDialog) {
        var incomeAmount by remember { mutableStateOf("") }
        var incomeNote by remember { mutableStateOf("Paycheck") }

        AlertDialog(
            onDismissRequest = { showQuickIncomeDialog = false },
            title = { Text("Log Virtual Income") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = incomeAmount,
                        onValueChange = { incomeAmount = it },
                        label = { Text("Income Amount (${profile.currencyCode})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = incomeNote,
                        onValueChange = { incomeNote = it },
                        label = { Text("Source / Note (e.g. Salary, Client payout)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = incomeAmount.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            pendingIncomeConfirm = amt to incomeNote
                            showQuickIncomeDialog = false
                        }
                    }
                ) {
                    Text("Review")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickIncomeDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Quick Expense Dialog
    if (showQuickExpenseDialog) {
        var expenseAmount by remember { mutableStateOf("") }
        var expenseCategory by remember { mutableStateOf("General") }
        var expenseNote by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showQuickExpenseDialog = false },
            title = { Text("Log Virtual Expense") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = expenseAmount,
                        onValueChange = { expenseAmount = it },
                        label = { Text("Amount (${profile.currencyCode})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = expenseCategory,
                        onValueChange = { expenseCategory = it },
                        label = { Text("Category (Housing, Groceries, Fun)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = expenseNote,
                        onValueChange = { expenseNote = it },
                        label = { Text("Note / Description") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = expenseAmount.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            pendingExpenseConfirm = Triple(amt, expenseCategory, expenseNote)
                            showQuickExpenseDialog = false
                        }
                    }
                ) {
                    Text("Review")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickExpenseDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Edit Buffer Target Dialog
    if (showEditBufferDialog) {
        var monthsText by remember { mutableStateOf(profile.emergencyBufferMonthsTarget.toString()) }
        var customTargetText by remember { mutableStateOf(profile.customBufferTargetAmount?.toString() ?: "") }

        AlertDialog(
            onDismissRequest = { showEditBufferDialog = false },
            title = { Text("Edit Emergency Buffer Target") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Default is 1.0 month of typical spending (${CurrenciesAndCountries.format(bufferStatus.typicalMonthlySpending, profile.currencyCode)}).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = monthsText,
                        onValueChange = { monthsText = it },
                        label = { Text("Target Months (e.g. 1.0, 2.0)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = customTargetText,
                        onValueChange = { customTargetText = it },
                        label = { Text("Or Custom Fixed Target (${profile.currencyCode})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val months = (monthsText.toDoubleOrNull() ?: 1.0).coerceAtLeast(0.5)
                        val customAmt = customTargetText.toDoubleOrNull()?.takeIf { it > 0.0 }
                        pendingBufferConfirm = months to customAmt
                        showEditBufferDialog = false
                    }
                ) {
                    Text("Review")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditBufferDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Deposit to Holding Pot Dialog
    if (showDepositPotDialog) {
        var amtText by remember { mutableStateOf("") }
        var srcText by remember { mutableStateOf("Freelance Payout") }
        AlertDialog(
            onDismissRequest = { showDepositPotDialog = false },
            title = { Text("Add Payout to Holding Pot") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amtText,
                        onValueChange = { amtText = it },
                        label = { Text("Payout Amount (${profile.currencyCode})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = srcText,
                        onValueChange = { srcText = it },
                        label = { Text("Source / Client") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amtText.toDoubleOrNull() ?: 0.0
                        if (amt > 0.0) {
                            pendingPotDepositConfirm = amt to srcText
                            showDepositPotDialog = false
                        }
                    }
                ) { Text("Review") }
            },
            dismissButton = {
                TextButton(onClick = { showDepositPotDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ConfirmActionDialog handlers (Feature 9)
    pendingIncomeConfirm?.let { (amt, note) ->
        ConfirmActionDialog(
            title = "Confirm Income & Auto-Split?",
            summaryLines = listOf(
                "Add ${CurrenciesAndCountries.format(amt, profile.currencyCode)} ($note)",
                "Splits across your active envelopes using your current rules."
            ),
            confirmLabel = "Log & Split",
            onConfirm = {
                onLogIncome(amt, note)
                dashboardNotice = "Logged and split ${CurrenciesAndCountries.format(amt, profile.currencyCode)}!"
                pendingIncomeConfirm = null
            },
            onDismiss = { pendingIncomeConfirm = null }
        )
    }

    pendingExpenseConfirm?.let { (amt, cat, note) ->
        ConfirmActionDialog(
            title = "Record Virtual Expense?",
            summaryLines = listOf(
                "Amount: -${CurrenciesAndCountries.format(amt, profile.currencyCode)}",
                "Category: $cat${if (note.isNotBlank()) " ($note)" else ""}",
                "Updates your local balance and weekly spending tracking."
            ),
            confirmLabel = "Record Spend",
            onConfirm = {
                onLogExpense(amt, cat, note)
                dashboardNotice = "Recorded expense of ${CurrenciesAndCountries.format(amt, profile.currencyCode)}."
                pendingExpenseConfirm = null
            },
            onDismiss = { pendingExpenseConfirm = null }
        )
    }

    pendingBufferConfirm?.let { (months, customAmt) ->
        ConfirmActionDialog(
            title = "Update Buffer Target?",
            summaryLines = listOf(
                "Target Months: ${String.format(Locale.US, "%.1f", months)} mo",
                if (customAmt != null) "Custom Target Amount: ${CurrenciesAndCountries.format(customAmt, profile.currencyCode)}"
                else "Calculated from your monthly spending baseline"
            ),
            confirmLabel = "Save Target",
            onConfirm = {
                onUpdateBufferTarget(months, customAmt)
                dashboardNotice = "Emergency buffer target updated!"
                pendingBufferConfirm = null
            },
            onDismiss = { pendingBufferConfirm = null }
        )
    }

    pendingPotDepositConfirm?.let { (amt, src) ->
        ConfirmActionDialog(
            title = "Hold Payout in Salary Pot?",
            summaryLines = listOf(
                "Amount: ${CurrenciesAndCountries.format(amt, profile.currencyCode)} ($src)",
                "New Pot Balance: ${CurrenciesAndCountries.format(profile.holdingPotBalance + amt, profile.currencyCode)}"
            ),
            confirmLabel = "Add to Pot",
            onConfirm = {
                onDepositToHoldingPot(amt, src)
                dashboardNotice = "Added ${CurrenciesAndCountries.format(amt, profile.currencyCode)} to Holding Pot."
                pendingPotDepositConfirm = null
            },
            onDismiss = { pendingPotDepositConfirm = null }
        )
    }

    if (showConfirmReleaseSalary) {
        ConfirmActionDialog(
            title = "Pay Yourself Monthly Salary?",
            summaryLines = listOf(
                "Release ${CurrenciesAndCountries.format(profile.monthlySalaryTarget, profile.currencyCode)} from Holding Pot",
                "Remaining Pot: ${CurrenciesAndCountries.format(maxOf(0.0, profile.holdingPotBalance - profile.monthlySalaryTarget), profile.currencyCode)}",
                "Runs Auto-Split on your released salary."
            ),
            confirmLabel = "Release Salary",
            onConfirm = {
                val ok = onReleaseMonthlySalary(profile.monthlySalaryTarget)
                dashboardNotice = if (ok) "Monthly salary released and split!" else "Not enough in Holding Pot."
                showConfirmReleaseSalary = false
            },
            onDismiss = { showConfirmReleaseSalary = false }
        )
    }
}

@Composable
private fun MetricMiniCard(
    title: String,
    amount: Double? = null,
    customText: String? = null,
    currencyCode: String = "USD",
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MintPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (customText != null) {
                Text(
                    text = customText,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            } else if (amount != null) {
                FormattedAmountDisplay(
                    amount = amount,
                    currencyCode = currencyCode,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = badge,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun FeatureNavCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actionText: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MintPrimary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MintPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (badge != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MintAccentGold.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = MintAccentGold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
