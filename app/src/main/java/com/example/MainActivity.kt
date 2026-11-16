package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.AiCoachManager
import com.example.data.FinancialCalculator
import com.example.data.GrowMintRepository
import com.example.data.TransactionImportEngine
import com.example.model.ActionTargetDestination
import com.example.model.AppThemeMode
import com.example.model.CsvParsePreview
import com.example.model.ExtractionBatchResult
import com.example.model.LedgerTransaction
import com.example.ui.coach.AiCoachScreen
import com.example.ui.components.LocalActivityLogger
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.split.SmartSplitScreen
import com.example.ui.theme.GrowMintTheme
import com.example.ui.theme.MintPrimary
import com.example.ui.tools.DeadlineCalendarScreen
import com.example.ui.tools.DebtPlannerScreen
import com.example.ui.tools.GoalPlannerScreen
import com.example.ui.tools.IncomeIdeasLabScreen
import com.example.ui.tools.NetWorthScreen
import com.example.ui.tools.ScamCheckerScreen
import com.example.ui.tools.SideHustleTrackerScreen
import com.example.ui.tools.WeeklyCheckInAndLessonsScreen
import com.example.ui.tools.WhatIfSimulatorScreen
import com.example.ui.traceability.ActivityHistoryScreen
import com.example.ui.traceability.HowGrowMintDecidesScreen
import com.example.ui.traceability.ScoreDetailScreen
import com.example.ui.trading.PaperTradingScreen
import com.example.ui.transactions.CsvImportAndMappingDialog
import com.example.ui.transactions.PasteImportDialog
import com.example.ui.transactions.TransactionReviewScreen
import com.example.ui.transactions.TransactionsScreen
import com.example.util.configureAppCheck
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DASHBOARD("Home", Icons.Default.Dashboard),
    SPLIT("Auto-Split", Icons.AutoMirrored.Filled.CallSplit),
    COACH("Coach", Icons.Default.Psychology),
    SANDBOX("Practice", Icons.Default.CandlestickChart),
    SETTINGS("Settings", Icons.Default.Settings)
}

enum class ToolSubScreen {
    TRANSACTIONS,
    WHAT_IF,
    SCAM_CHECKER,
    SIDE_HUSTLE,
    CALENDAR,
    LESSONS_CHECKIN,
    IDEAS_LAB,
    DEBT_PLANNER,
    NET_WORTH,
    GOAL_PLANNER,
    SCORE_DETAIL,
    HOW_DECIDES,
    ACTIVITY_HISTORY
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory App Check initialization for Firebase AI Logic
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        val repository = GrowMintRepository(applicationContext)
        val coachManager = AiCoachManager()

        setContent {
            val scope = rememberCoroutineScope()
            val profile by repository.userProfile.collectAsStateWithLifecycle()
            val envelopes by repository.envelopes.collectAsStateWithLifecycle()
            val transferHistory by repository.transferHistory.collectAsStateWithLifecycle()
            val transactions by repository.transactions.collectAsStateWithLifecycle()
            val paperCash by repository.paperCash.collectAsStateWithLifecycle()
            val paperHoldings by repository.paperHoldings.collectAsStateWithLifecycle()
            val themeMode by repository.themeMode.collectAsStateWithLifecycle()
            val bankConnection by repository.bankConnection.collectAsStateWithLifecycle()
            val appLock by repository.appLock.collectAsStateWithLifecycle()
            val detectedDeposits by repository.detectedDeposits.collectAsStateWithLifecycle()
            val subscriptionLeaks by repository.subscriptionLeaks.collectAsStateWithLifecycle()
            val auditLogs by repository.bankAuditLog.collectAsStateWithLifecycle()

            // New upgrade state flows
            val sideHustleEntries by repository.sideHustleEntries.collectAsStateWithLifecycle()
            val calendarItems by repository.calendarItems.collectAsStateWithLifecycle()
            val noSpendStreakDays by repository.noSpendStreakDays.collectAsStateWithLifecycle()
            val lastNoSpendDate by repository.lastNoSpendDate.collectAsStateWithLifecycle()
            val lastWeeklyCheckInMillis by repository.lastWeeklyCheckInMillis.collectAsStateWithLifecycle()
            val assets by repository.assets.collectAsStateWithLifecycle()
            val netWorthSnapshots by repository.netWorthSnapshots.collectAsStateWithLifecycle()
            val monthlyHealthSnapshots by repository.monthlyHealthSnapshots.collectAsStateWithLifecycle()

            // Effortless transaction entry & import flows
            val merchantRules by repository.merchantRules.collectAsStateWithLifecycle()
            val favoriteCategories by repository.favoriteCategories.collectAsStateWithLifecycle()
            val recentCategories by repository.recentCategories.collectAsStateWithLifecycle()
            val lastImportBatch by repository.lastImportBatch.collectAsStateWithLifecycle()
            val pendingIncomeForSplit by repository.pendingIncomeForSplit.collectAsStateWithLifecycle()

            // Traceability, score history & confirmation flows
            val scoreHistory by repository.scoreHistory.collectAsStateWithLifecycle()
            val completedActionKeys by repository.completedActionKeys.collectAsStateWithLifecycle()
            val activityHistory by repository.activityHistory.collectAsStateWithLifecycle()
            val errorFlags by repository.errorFlags.collectAsStateWithLifecycle()

            CompositionLocalProvider(
                LocalActivityLogger provides { category, title, whatChanged, whySummary, numbersSummary, ruleId ->
                    repository.logConfirmedActivity(
                        category = category,
                        title = title,
                        whatChanged = whatChanged,
                        whySummary = whySummary,
                        numbersSummary = numbersSummary,
                        ruleId = ruleId
                    )
                }
            ) {
            GrowMintTheme(themeMode = themeMode) {
                var currentScreen by remember { mutableStateOf(AppScreen.DASHBOARD) }
                var activeToolScreen by remember { mutableStateOf<ToolSubScreen?>(null) }

                // Import & Review dialog states
                var showPasteImportDialog by remember { mutableStateOf(false) }
                var isExtractingPaste by remember { mutableStateOf(false) }
                var pasteErrorMessage by remember { mutableStateOf<String?>(null) }

                var showCsvImportDialog by remember { mutableStateOf(false) }
                var csvPreviewState by remember { mutableStateOf<CsvParsePreview?>(null) }
                var isCategorizingCsv by remember { mutableStateOf(false) }
                var csvErrorMessage by remember { mutableStateOf<String?>(null) }

                var pendingReviewBatch by remember { mutableStateOf<ExtractionBatchResult?>(null) }
                var prefilledSplitIncome by remember { mutableStateOf<Pair<Double, String>?>(null) }

                val csvFileLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        scope.launch {
                            val rawCsv = withContext(Dispatchers.IO) {
                                try {
                                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            if (rawCsv.isNullOrBlank()) {
                                csvErrorMessage = "Could not read the selected file. You can try another CSV or paste CSV rows below."
                            } else {
                                val preview = TransactionImportEngine.parseCsvStatementPreview(rawCsv)
                                if (preview == null) {
                                    csvErrorMessage = "No rows found in that CSV file. Check the file format or add entries manually."
                                } else {
                                    csvErrorMessage = null
                                    csvPreviewState = preview
                                }
                            }
                        }
                    }
                }

                val exportActivityCsvLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("text/csv")
                ) { uri ->
                    if (uri != null) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                                    it.write(repository.exportActivityHistoryCsv())
                                }
                            } catch (_: Exception) { }
                        }
                    }
                }

                val exportActivityTextLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("text/plain")
                ) { uri ->
                    if (uri != null) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                                    it.write(repository.exportActivityHistoryText())
                                }
                            } catch (_: Exception) { }
                        }
                    }
                }

                LaunchedEffect(profile.isUnder18) {
                    if (profile.isUnder18 && currentScreen == AppScreen.SANDBOX) {
                        currentScreen = AppScreen.DASHBOARD
                    }
                }

                // App Lock Overlay when enabled and locked
                if (appLock.isEnabled && !appLock.isUnlocked) {
                    com.example.ui.components.AppLockOverlay(
                        onUnlock = { enteredPin ->
                            repository.unlockApp(enteredPin)
                        }
                    )
                } else {
                    if (profile.isOnboarded && pendingReviewBatch != null) {
                        BackHandler { pendingReviewBatch = null }
                    } else if (profile.isOnboarded && activeToolScreen != null) {
                        BackHandler {
                            activeToolScreen = null
                        }
                    } else if (profile.isOnboarded && currentScreen != AppScreen.DASHBOARD) {
                        BackHandler {
                            currentScreen = AppScreen.DASHBOARD
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (!profile.isOnboarded) {
                            OnboardingScreen(
                                initialProfile = profile,
                                onComplete = { newProfile, _ ->
                                    repository.updateProfile(newProfile)
                                    currentScreen = AppScreen.DASHBOARD
                                    activeToolScreen = null
                                }
                            )
                        } else {
                            // Deterministic code-computed financial metrics
                            val healthReport = remember(profile, envelopes, bankConnection, transactions) {
                                repository.calculateHealthReport()
                            }
                            val bufferStatus = remember(profile, envelopes) {
                                FinancialCalculator.calculateBufferGateStatus(profile, envelopes)
                            }
                            val sideHustleReport = remember(sideHustleEntries) {
                                FinancialCalculator.calculateSideHustleReport(sideHustleEntries)
                            }
                            val upcomingDeadlines = remember(calendarItems) {
                                FinancialCalculator.getUpcomingDeadlines(calendarItems)
                            }
                            val weeklyScore = remember(profile, envelopes, transactions, noSpendStreakDays, transferHistory) {
                                FinancialCalculator.calculateWeeklyMoneyScore(
                                    profile = profile,
                                    envelopes = envelopes,
                                    transactions = transactions,
                                    noSpendStreakDays = noSpendStreakDays,
                                    transferHistory = transferHistory
                                )
                            }
                            val dailyLesson = remember { FinancialCalculator.getDailyMicroLesson() }
                            val currentMonthKey = remember { SimpleDateFormat("yyyy-MM", Locale.US).format(Date()) }
                            val priorHealthSnapshot = remember(monthlyHealthSnapshots, currentMonthKey) {
                                monthlyHealthSnapshots.filter { it.monthKey < currentMonthKey }.maxByOrNull { it.monthKey }
                            }
                            val monthlyImprovementReport = remember(profile, envelopes, transactions, priorHealthSnapshot, calendarItems) {
                                FinancialCalculator.calculateMonthlyImprovementReport(
                                    profile = profile,
                                    envelopes = envelopes,
                                    transactions = transactions,
                                    previousSnapshot = priorHealthSnapshot,
                                    calendarItems = calendarItems
                                )
                            }
                            LaunchedEffect(monthlyImprovementReport.currentSnapshot) {
                                repository.recordScoreSnapshotIfChanged()
                            }
                            val netWorthSummary = remember(assets, bankConnection, profile.debts, profile.currentBalance) {
                                FinancialCalculator.calculateNetWorthSummary(
                                    manualAssets = assets,
                                    linkedBankAccounts = bankConnection.accounts,
                                    isBankLinked = bankConnection.isLinked,
                                    debts = profile.debts,
                                    ledgerCashBalance = profile.currentBalance
                                )
                            }

                            val launchIncomeSplit: (Double, String) -> Unit = { amt, src ->
                                prefilledSplitIncome = amt to src
                                activeToolScreen = null
                                currentScreen = AppScreen.SPLIT
                            }
                            val navigateToActionDestination: (ActionTargetDestination) -> Unit = { dest ->
                                when (dest) {
                                    ActionTargetDestination.TRANSACTIONS -> activeToolScreen = ToolSubScreen.TRANSACTIONS
                                    ActionTargetDestination.SPLIT -> {
                                        activeToolScreen = null
                                        currentScreen = AppScreen.SPLIT
                                    }
                                    ActionTargetDestination.DEBT_PLANNER -> {
                                        activeToolScreen = if (profile.isUnder18) ToolSubScreen.GOAL_PLANNER else ToolSubScreen.DEBT_PLANNER
                                    }
                                    ActionTargetDestination.GOAL_PLANNER -> activeToolScreen = ToolSubScreen.GOAL_PLANNER
                                    ActionTargetDestination.CALENDAR -> activeToolScreen = ToolSubScreen.CALENDAR
                                    ActionTargetDestination.NET_WORTH -> activeToolScreen = ToolSubScreen.NET_WORTH
                                }
                            }

                            val reviewBatch = pendingReviewBatch
                            val tool = activeToolScreen
                            if (reviewBatch != null) {
                                TransactionReviewScreen(
                                    batchResult = reviewBatch,
                                    currencyCode = profile.currencyCode,
                                    existingTransactions = transactions,
                                    onConfirmSave = { editedItems, sourceType ->
                                        repository.confirmImportBatch(editedItems, sourceType)
                                        pendingReviewBatch = null
                                        activeToolScreen = ToolSubScreen.TRANSACTIONS
                                    },
                                    onCancel = { pendingReviewBatch = null }
                                )
                            } else if (tool != null) {
                                when (tool) {
                                    ToolSubScreen.TRANSACTIONS -> TransactionsScreen(
                                        profile = profile,
                                        transactions = transactions,
                                        merchantRules = merchantRules,
                                        favoriteCategories = favoriteCategories,
                                        recentCategories = recentCategories,
                                        lastImportBatch = lastImportBatch,
                                        pendingIncomeForSplit = pendingIncomeForSplit,
                                        mostRecentSimilar = { query -> repository.getMostRecentSimilarTransaction(query) },
                                        onQuickAdd = { amt, isExp, cat, note, learn, suggested ->
                                            repository.recordQuickAdd(amt, isExp, cat, note, learn, suggested)
                                        },
                                        onToggleFavoriteCategory = { cat -> repository.toggleFavoriteCategory(cat) },
                                        onOpenPasteImport = {
                                            pasteErrorMessage = null
                                            showPasteImportDialog = true
                                        },
                                        onOpenCsvImport = {
                                            csvErrorMessage = null
                                            csvPreviewState = null
                                            showCsvImportDialog = true
                                        },
                                        onUndoLastImport = { repository.undoLastImport() },
                                        onDeleteTransaction = { id -> repository.deleteLedgerTransaction(id) },
                                        onLaunchSplitForIncome = launchIncomeSplit,
                                        onDismissIncomePrompt = { repository.dismissPendingIncomePrompt() },
                                        onBack = { activeToolScreen = null }
                                    )
                                    ToolSubScreen.WHAT_IF -> WhatIfSimulatorScreen(
                                        profile = profile,
                                        bufferStatus = bufferStatus,
                                        onBack = { activeToolScreen = null }
                                    )
                                    ToolSubScreen.SCAM_CHECKER -> ScamCheckerScreen(
                                        profile = profile,
                                        coachManager = coachManager,
                                        onBack = { activeToolScreen = null }
                                    )
                                    ToolSubScreen.SIDE_HUSTLE -> SideHustleTrackerScreen(
                                        profile = profile,
                                        entries = sideHustleEntries,
                                        report = sideHustleReport,
                                        onAddEntry = { entry -> repository.addSideHustleEntry(entry) },
                                        onDeleteEntry = { id -> repository.deleteSideHustleEntry(id) },
                                        onBack = { activeToolScreen = null }
                                    )
                                    ToolSubScreen.CALENDAR -> DeadlineCalendarScreen(
                                        profile = profile,
                                        items = calendarItems,
                                        upcoming30 = upcomingDeadlines,
                                        onAddItem = { item -> repository.addCalendarItem(item) },
                                        onTogglePaid = { id -> repository.toggleCalendarItemPaid(id) },
                                        onDeleteItem = { id -> repository.deleteCalendarItem(id) },
                                        onBack = { activeToolScreen = null }
                                    )
                                    ToolSubScreen.LESSONS_CHECKIN -> WeeklyCheckInAndLessonsScreen(
                                        profile = profile,
                                        noSpendStreakDays = noSpendStreakDays,
                                        lastNoSpendDate = lastNoSpendDate,
                                        lastCheckInMillis = lastWeeklyCheckInMillis,
                                        weeklyScore = weeklyScore,
                                        dailyLesson = dailyLesson,
                                        monthlyReport = monthlyImprovementReport,
                                        savedSnapshots = monthlyHealthSnapshots,
                                        onLogNoSpendDay = { repository.logNoSpendDay() },
                                        onCompleteWeeklyCheckIn = { repository.completeWeeklyCheckIn() },
                                        onSaveMonthlySnapshot = { snap -> repository.saveMonthlyHealthSnapshot(snap) },
                                        coachManager = coachManager,
                                        completedActionKeys = completedActionKeys,
                                        onMarkActionDone = { action ->
                                            val nums = action.traceability?.triggeringNumbers
                                                ?.joinToString("; ") { "${it.label}: ${it.valueFormatted}" }
                                                ?: action.howMuch
                                            repository.markActionDone(
                                                actionKey = action.actionKey,
                                                title = action.title,
                                                whatSummary = "Completed ${action.title} (${action.howMuch})",
                                                whySummary = action.traceability?.ruleDescription ?: action.what,
                                                numbersSummary = nums,
                                                ruleId = action.ruleId.ifBlank { null }
                                            )
                                        },
                                        onFlagError = { itemType, itemId, itemTitle, note, snapshotSummary, ruleId ->
                                            repository.flagItemError(itemType, itemId, itemTitle, note, snapshotSummary, ruleId)
                                        },
                                        onOpenScoreDetail = { activeToolScreen = ToolSubScreen.SCORE_DETAIL },
                                        onOpenHowDecides = { activeToolScreen = ToolSubScreen.HOW_DECIDES },
                                        onNavigateActionDestination = navigateToActionDestination,
                                        onBack = { activeToolScreen = null }
                                    )
                                    ToolSubScreen.IDEAS_LAB -> IncomeIdeasLabScreen(
                                        profile = profile,
                                        onOpenSideHustleTracker = { activeToolScreen = ToolSubScreen.SIDE_HUSTLE },
                                        onBack = { activeToolScreen = null }
                                    )
                                    ToolSubScreen.DEBT_PLANNER -> DebtPlannerScreen(
                                        profile = profile,
                                        onAddDebt = { debt -> repository.addDebtItem(debt) },
                                        onDeleteDebt = { debtId -> repository.deleteDebtItem(debtId) },
                                        onBack = { activeToolScreen = null }
                                    )
                                    ToolSubScreen.NET_WORTH -> NetWorthScreen(
                                        profile = profile,
                                        assets = assets,
                                        snapshots = netWorthSnapshots,
                                        bankConnection = bankConnection,
                                        onAddAsset = { asset -> repository.addAsset(asset) },
                                        onDeleteAsset = { assetId -> repository.deleteAsset(assetId) },
                                        onSaveMonthlySnapshot = { snap -> repository.saveMonthlyNetWorthSnapshot(snap) },
                                        onBack = { activeToolScreen = null }
                                    )
                                    ToolSubScreen.GOAL_PLANNER -> GoalPlannerScreen(
                                        profile = profile,
                                        onAddGoal = { goal -> repository.addGoalItem(goal) },
                                        onUpdateGoalSaved = { id, amt -> repository.updateGoalSavedAmount(id, amt) },
                                        onDeleteGoal = { id -> repository.deleteGoalItem(id) },
                                        onBack = { activeToolScreen = null }
                                    )
                                    ToolSubScreen.SCORE_DETAIL -> {
                                        val traced = monthlyImprovementReport.tracedScore
                                            ?: FinancialCalculator.calculateTracedMoneyHealthScore(profile, envelopes, transactions)
                                        ScoreDetailScreen(
                                            tracedScore = traced,
                                            scoreHistory = scoreHistory,
                                            currencyCode = profile.currencyCode,
                                            onOpenHowDecides = { activeToolScreen = ToolSubScreen.HOW_DECIDES },
                                            onOpenActivityHistory = { activeToolScreen = ToolSubScreen.ACTIVITY_HISTORY },
                                            onFlagError = { itemType, itemId, itemTitle, note, snapshotSummary, ruleId ->
                                                repository.flagItemError(itemType, itemId, itemTitle, note, snapshotSummary, ruleId)
                                            },
                                            onBack = { activeToolScreen = null }
                                        )
                                    }
                                    ToolSubScreen.HOW_DECIDES -> HowGrowMintDecidesScreen(
                                        onBack = { activeToolScreen = null }
                                    )
                                    ToolSubScreen.ACTIVITY_HISTORY -> ActivityHistoryScreen(
                                        activityHistory = activityHistory,
                                        errorFlags = errorFlags,
                                        onExportCsv = {
                                            exportActivityCsvLauncher.launch("growmint_activity_history.csv")
                                        },
                                        onExportText = {
                                            exportActivityTextLauncher.launch("growmint_activity_history.txt")
                                        },
                                        onGetPreviewCsv = { repository.exportActivityHistoryCsv() },
                                        onGetPreviewText = { repository.exportActivityHistoryText() },
                                        onClearFlag = { flagId -> repository.clearErrorFlag(flagId) },
                                        onBack = { activeToolScreen = null }
                                    )
                                }
                            } else {
                                val visibleNavScreens = remember(profile.isUnder18) {
                                    if (profile.isUnder18) AppScreen.values().filter { it != AppScreen.SANDBOX }
                                    else AppScreen.values().toList()
                                }
                                Scaffold(
                                    bottomBar = {
                                        NavigationBar(
                                            modifier = Modifier.testTag("main_bottom_nav"),
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            tonalElevation = 8.dp
                                        ) {
                                            visibleNavScreens.forEach { screen ->
                                                val isSelected = currentScreen == screen
                                                NavigationBarItem(
                                                    selected = isSelected,
                                                    onClick = { currentScreen = screen },
                                                    icon = {
                                                        Icon(screen.icon, contentDescription = screen.label)
                                                    },
                                                    label = {
                                                        Text(
                                                            text = screen.label,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    },
                                                    colors = NavigationBarItemDefaults.colors(
                                                        selectedIconColor = MintPrimary,
                                                        indicatorColor = MintPrimary.copy(alpha = 0.2f)
                                                    ),
                                                    modifier = Modifier.testTag("nav_${screen.name.lowercase()}")
                                                )
                                            }
                                        }
                                    }
                                ) { innerPadding ->
                                    when (currentScreen) {
                                        AppScreen.DASHBOARD -> {
                                            DashboardScreen(
                                                profile = profile,
                                                envelopes = envelopes,
                                                healthReport = healthReport,
                                                bankConnection = bankConnection,
                                                themeMode = themeMode,
                                                detectedDeposits = detectedDeposits,
                                                subscriptionLeaks = subscriptionLeaks,
                                                bufferStatus = bufferStatus,
                                                upcomingDeadlines = upcomingDeadlines,
                                                weeklyScore = weeklyScore,
                                                dailyLesson = dailyLesson,
                                                noSpendStreakDays = noSpendStreakDays,
                                                sideHustleReport = sideHustleReport,
                                                monthlyReport = monthlyImprovementReport,
                                                netWorthSummary = netWorthSummary,
                                                onToggleTheme = {
                                                    val nextMode = when (themeMode) {
                                                        AppThemeMode.AUTO -> AppThemeMode.DAY
                                                        AppThemeMode.DAY -> AppThemeMode.NIGHT
                                                        AppThemeMode.NIGHT -> AppThemeMode.AUTO
                                                    }
                                                    repository.updateThemeMode(nextMode)
                                                },
                                                onNavigateToSplit = { currentScreen = AppScreen.SPLIT },
                                                onNavigateToCoach = { currentScreen = AppScreen.COACH },
                                                onNavigateToSandbox = { currentScreen = AppScreen.SANDBOX },
                                                onNavigateToBankLink = { currentScreen = AppScreen.SETTINGS },
                                                onRefreshBankData = {},
                                                onSimulateDeposit = { _, _ -> },
                                                onDismissDeposit = { depositId -> repository.dismissDeposit(depositId) },
                                                onLogIncome = { amount, note -> repository.executeIncomeSplit(amount, note) },
                                                onLogExpense = { amount, category, note ->
                                                    repository.addLedgerTransaction(
                                                        LedgerTransaction(
                                                            type = "EXPENSE",
                                                            category = category,
                                                            amount = amount,
                                                            notes = note
                                                        )
                                                    )
                                                    repository.updateProfile(
                                                        profile.copy(currentBalance = maxOf(0.0, profile.currentBalance - amount))
                                                    )
                                                },
                                                onUpdateBufferTarget = { months, customAmt ->
                                                    repository.updateProfile(
                                                        profile.copy(
                                                            emergencyBufferMonthsTarget = months,
                                                            customBufferTargetAmount = customAmt
                                                        )
                                                    )
                                                },
                                                onDepositToHoldingPot = { amount, src ->
                                                    repository.depositToHoldingPot(amount, src)
                                                },
                                                onReleaseMonthlySalary = { amount ->
                                                    repository.releaseMonthlySalary(amount)
                                                },
                                                onOpenTool = { toolScreen ->
                                                    activeToolScreen = toolScreen
                                                },
                                                transactions = transactions,
                                                lastImportBatch = lastImportBatch,
                                                pendingIncomeForSplit = pendingIncomeForSplit,
                                                onOpenTransactions = { activeToolScreen = ToolSubScreen.TRANSACTIONS },
                                                onOpenPasteImport = {
                                                    pasteErrorMessage = null
                                                    showPasteImportDialog = true
                                                },
                                                onUndoLastImport = { repository.undoLastImport() },
                                                onLaunchSplitForIncome = launchIncomeSplit,
                                                onDismissIncomePrompt = { repository.dismissPendingIncomePrompt() },
                                                modifier = Modifier.padding(innerPadding)
                                            )
                                        }
                                        AppScreen.SPLIT -> {
                                            SmartSplitScreen(
                                                profile = profile,
                                                envelopes = envelopes,
                                                transferHistory = transferHistory,
                                                presets = repository.presets,
                                                bufferStatus = bufferStatus,
                                                onSaveRules = { newRules ->
                                                    repository.updateProfile(profile.copy(splitRules = newRules))
                                                },
                                                onExecuteSplit = { amount, desc ->
                                                    repository.executeIncomeSplit(amount, desc)
                                                    prefilledSplitIncome = null
                                                },
                                                onUndoLastTransfer = {
                                                    repository.undoLastTransfer()
                                                },
                                                onReleaseSalary = { amt ->
                                                    repository.releaseMonthlySalary(amt)
                                                },
                                                prefilledIncomeAmount = prefilledSplitIncome?.first,
                                                prefilledIncomeSource = prefilledSplitIncome?.second,
                                                modifier = Modifier.padding(innerPadding)
                                            )
                                        }
                                        AppScreen.COACH -> {
                                            AiCoachScreen(
                                                profile = profile,
                                                envelopes = envelopes,
                                                bufferStatus = bufferStatus,
                                                coachManager = coachManager,
                                                onNavigateToSandbox = { currentScreen = AppScreen.SANDBOX },
                                                onOpenScamChecker = { activeToolScreen = ToolSubScreen.SCAM_CHECKER },
                                                onOpenWhatIf = { activeToolScreen = ToolSubScreen.WHAT_IF },
                                                modifier = Modifier.padding(innerPadding)
                                            )
                                        }
                                        AppScreen.SANDBOX -> {
                                            PaperTradingScreen(
                                                profile = profile,
                                                paperCash = paperCash,
                                                holdings = paperHoldings,
                                                assets = repository.availablePaperAssets,
                                                bufferStatus = bufferStatus,
                                                onExecuteTrade = { symbol, shares, isBuy ->
                                                    repository.executePaperTrade(symbol, shares, isBuy)
                                                },
                                                modifier = Modifier.padding(innerPadding)
                                            )
                                        }
                                        AppScreen.SETTINGS -> {
                                            SettingsScreen(
                                                profile = profile,
                                                bankConnection = bankConnection,
                                                auditLogs = auditLogs,
                                                onUpdateProfile = { updated ->
                                                    repository.updateProfile(updated)
                                                    if (updated.themeMode != themeMode) {
                                                        repository.updateThemeMode(updated.themeMode)
                                                    }
                                                },
                                                onConnectBank = {},
                                                onDisconnectBank = {
                                                    scope.launch {
                                                        repository.disconnectAndPurgeBankData()
                                                    }
                                                },
                                                onRefreshBank = {},
                                                onExportCsv = { repository.exportTransactionsCsv() },
                                                onImportCsv = { csv -> repository.importTransactionsCsv(csv) },
                                                onResetData = {
                                                    repository.deleteAllUserData()
                                                    activeToolScreen = null
                                                    currentScreen = AppScreen.DASHBOARD
                                                },
                                                merchantRules = merchantRules,
                                                onSaveMerchantRule = { pattern, cat, ruleId ->
                                                    repository.saveMerchantRule(pattern, cat, ruleId)
                                                },
                                                onDeleteMerchantRule = { id ->
                                                    repository.deleteMerchantRule(id)
                                                },
                                                onOpenHowDecides = { activeToolScreen = ToolSubScreen.HOW_DECIDES },
                                                onOpenActivityHistory = { activeToolScreen = ToolSubScreen.ACTIVITY_HISTORY },
                                                activityCount = activityHistory.size,
                                                errorFlagCount = errorFlags.size,
                                                modifier = Modifier.padding(innerPadding)
                                            )
                                        }
                                    }
                                }
                            }

                            if (showPasteImportDialog) {
                                PasteImportDialog(
                                    isLoading = isExtractingPaste,
                                    errorMessage = pasteErrorMessage,
                                    onDismiss = {
                                        showPasteImportDialog = false
                                        pasteErrorMessage = null
                                    },
                                    onExtract = { rawPasted ->
                                        scope.launch {
                                            isExtractingPaste = true
                                            pasteErrorMessage = null
                                            val result = coachManager.extractTransactionsFromText(
                                                rawPastedText = rawPasted,
                                                profile = profile,
                                                rules = merchantRules,
                                                existingTransactions = transactions
                                            )
                                            isExtractingPaste = false
                                            if (result.candidates.isNotEmpty()) {
                                                showPasteImportDialog = false
                                                pendingReviewBatch = result
                                            } else {
                                                pasteErrorMessage = result.errorMessage
                                                    ?: "No transactions could be extracted. Try pasting clearer lines or add entries manually."
                                            }
                                        }
                                    },
                                    onAddManuallyInstead = {
                                        showPasteImportDialog = false
                                        pasteErrorMessage = null
                                        activeToolScreen = ToolSubScreen.TRANSACTIONS
                                    }
                                )
                            }

                            if (showCsvImportDialog) {
                                CsvImportAndMappingDialog(
                                    initialPreview = csvPreviewState,
                                    isProcessing = isCategorizingCsv,
                                    errorMessage = csvErrorMessage,
                                    onPickCsvFile = {
                                        csvFileLauncher.launch(
                                            arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain", "*/*")
                                        )
                                    },
                                    onParseRawCsvText = { rawCsvText ->
                                        val preview = TransactionImportEngine.parseCsvStatementPreview(rawCsvText)
                                        if (preview == null) {
                                            csvErrorMessage = "Could not detect CSV rows. Ensure values are comma-separated."
                                        } else {
                                            csvErrorMessage = null
                                            csvPreviewState = preview
                                        }
                                    },
                                    onConfirmMapping = { preview, mapping ->
                                        scope.launch {
                                            isCategorizingCsv = true
                                            csvErrorMessage = null
                                            val extracted = TransactionImportEngine.extractCandidatesFromCsv(
                                                preview = preview,
                                                mapping = mapping,
                                                existingTransactions = transactions,
                                                rules = merchantRules
                                            )
                                            if (extracted.candidates.isEmpty()) {
                                                isCategorizingCsv = false
                                                csvErrorMessage = extracted.errorMessage
                                                    ?: "No valid transactions matched those columns."
                                            } else {
                                                val unmatchedMerchants = extracted.candidates.mapIndexedNotNull { idx, cand ->
                                                    if (!cand.ruleMatched && cand.merchant.isNotBlank()) idx to cand.merchant else null
                                                }
                                                val aiSuggestions = coachManager.suggestCategoriesForMerchants(
                                                    indexedMerchants = unmatchedMerchants,
                                                    profile = profile
                                                )
                                                val enriched = extracted.candidates.mapIndexed { idx, cand ->
                                                    val suggested = aiSuggestions[idx]
                                                    if (!cand.ruleMatched && !suggested.isNullOrBlank()) {
                                                        cand.copy(suggestedCategory = suggested, category = suggested)
                                                    } else cand
                                                }
                                                isCategorizingCsv = false
                                                showCsvImportDialog = false
                                                csvPreviewState = null
                                                pendingReviewBatch = extracted.copy(candidates = enriched)
                                            }
                                        }
                                    },
                                    onDismiss = {
                                        showCsvImportDialog = false
                                        csvPreviewState = null
                                        csvErrorMessage = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}
