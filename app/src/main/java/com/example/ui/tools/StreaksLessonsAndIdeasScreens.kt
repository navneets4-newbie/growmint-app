package com.example.ui.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiCoachManager
import com.example.data.FinancialCalculator
import com.example.data.RuleRegistry
import com.example.model.*
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.DataQualityBadgeRow
import com.example.ui.components.ExplanationSenseCheckRow
import com.example.ui.components.PersistentDisclaimerBanner
import com.example.ui.components.ReportErrorDialog
import com.example.ui.components.ScannableLabelValueRow
import com.example.ui.components.TopLevelDataQualityWarning
import com.example.ui.components.TraceableExplanationTextWithChips
import com.example.ui.traceability.WhyActionDialog
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyCheckInAndLessonsScreen(
    profile: UserProfile,
    noSpendStreakDays: Int,
    lastNoSpendDate: String,
    lastCheckInMillis: Long,
    weeklyScore: WeeklyMoneyScore,
    dailyLesson: MoneyLesson,
    monthlyReport: MonthlyImprovementReport,
    savedSnapshots: List<MonthlyHealthSnapshot> = emptyList(),
    onLogNoSpendDay: () -> Boolean,
    onCompleteWeeklyCheckIn: () -> Unit,
    onSaveMonthlySnapshot: (MonthlyHealthSnapshot) -> Unit = {},
    coachManager: AiCoachManager = remember { AiCoachManager() },
    completedActionKeys: Set<String> = emptySet(),
    explanationFeedback: List<ExplanationFeedbackEntry> = emptyList(),
    onSubmitExplanationFeedback: (targetId: String, targetType: String, targetTitle: String, summaryShown: String, ruleId: String?, madeSense: Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onMarkActionDone: (MonthlyActionItem) -> Unit = {},
    onFlagError: (itemType: String, itemId: String, itemTitle: String, note: String, snapshotSummary: String, ruleId: String?) -> Unit = { _, _, _, _, _, _ -> },
    onOpenScoreDetail: () -> Unit = {},
    onOpenHowDecides: () -> Unit = {},
    onNavigateActionDestination: (ActionTargetDestination) -> Unit = {},
    sharingEnabled: Boolean = true,
    onOpenShareScoreCard: () -> Unit = {},
    onBack: () -> Unit,
    initialTab: Int = 0,
    modifier: Modifier = Modifier
) {
    val lessons = FinancialCalculator.allMicroLessons
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var selectedLesson by remember(dailyLesson) { mutableStateOf(dailyLesson) }
    var showConfirmNoSpend by remember { mutableStateOf(false) }
    var showConfirmCheckIn by remember { mutableStateOf(false) }
    var showConfirmMonthlySnapshot by remember { mutableStateOf(false) }
    var whyDialogAction by remember { mutableStateOf<MonthlyActionItem?>(null) }
    var pendingMarkDoneAction by remember { mutableStateOf<MonthlyActionItem?>(null) }
    var flagDialogTarget by remember { mutableStateOf<Triple<String, String, Pair<String, String?>>?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val alreadyLoggedToday = lastNoSpendDate == todayStr
    val lastCheckInText = remember(lastCheckInMillis) {
        if (lastCheckInMillis <= 0L) "Not checked in yet"
        else "Last check-in: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(lastCheckInMillis))}"
    }
    val hasSavedCurrentMonth = remember(savedSnapshots, monthlyReport.currentMonthKey) {
        savedSnapshots.any { it.monthKey == monthlyReport.currentMonthKey }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Check-In & Monthly Report",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Monthly improvement score, top actions & weekly habits",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("lessons_back_btn")) {
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
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            PersistentDisclaimerBanner(modifier = Modifier.padding(bottom = 10.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                indicator = {},
                divider = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Monthly Report", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("tab_monthly_report")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("This week", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("tab_this_week")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                MonthlyReportTabContent(
                    report = monthlyReport,
                    profile = profile,
                    coachManager = coachManager,
                    hasSavedCurrentMonth = hasSavedCurrentMonth,
                    completedActionKeys = completedActionKeys,
                    explanationFeedback = explanationFeedback,
                    onSubmitExplanationFeedback = onSubmitExplanationFeedback,
                    onOpenScoreDetail = onOpenScoreDetail,
                    onNavigateActionDestination = onNavigateActionDestination,
                    sharingEnabled = sharingEnabled,
                    onOpenShareScoreCard = onOpenShareScoreCard,
                    onRequestSaveSnapshot = { showConfirmMonthlySnapshot = true },
                    onWhyAction = { action -> whyDialogAction = action },
                    onRequestMarkDone = { action -> pendingMarkDoneAction = action },
                    onFlagScore = {
                        flagDialogTarget = Triple(
                            "MONEY_HEALTH_SCORE",
                            "monthly_report_score",
                            "Money Health Score (${monthlyReport.currentScore}/100)" to monthlyReport.tracedScore?.triggeredRuleIds?.firstOrNull()
                        )
                    },
                    onFlagAction = { action ->
                        flagDialogTarget = Triple(
                            "TOP_ACTION",
                            action.actionKey,
                            "Action #${action.priority}: ${action.title}" to action.ruleId.ifBlank { null }
                        )
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {

            // 1. No-Spend Streak Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("no_spend_streak_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MintPrimary.copy(alpha = 0.14f))
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
                                text = "NO-SPEND DAY STREAK",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                color = MintPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$noSpendStreakDays Day${if (noSpendStreakDays == 1) "" else "s"}",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
                            )
                            Text(
                                text = if (alreadyLoggedToday) "Today logged ✓ Keep going!" else "Skipped impulse buys today? Log your day.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { showConfirmNoSpend = true },
                            enabled = !alreadyLoggedToday,
                            modifier = Modifier.testTag("log_nospend_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                        ) {
                            Text(if (alreadyLoggedToday) "Done ✓" else "Log Today", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 2. Code-Calculated Weekly Money Score (0-100) + Breakdown
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("weekly_money_score_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Weekly Money Score",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = lastCheckInText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = FinanceGreen.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "${weeklyScore.totalScore}/100 (${weeklyScore.gradeLabel})",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = FinanceGreen,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Text(
                            text = weeklyScore.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        HorizontalDivider()

                        weeklyScore.breakdown.forEach { item ->
                            val frac = if (item.maxPoints > 0) (item.earnedPoints.toFloat() / item.maxPoints).coerceIn(0f, 1f) else 0f
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = item.category, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        text = "${item.earnedPoints} / ${item.maxPoints} pts",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MintPrimary
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { frac },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MintPrimary,
                                    trackColor = MaterialTheme.colorScheme.surface
                                )
                                Text(
                                    text = item.detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = { showConfirmCheckIn = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("complete_weekly_checkin_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                        ) {
                            Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Complete Weekly Check-In", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. Daily 2-Minute Money Lesson
            item {
                Text(
                    text = "2-Minute Money Lessons",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(lessons) { lesson ->
                        FilterChip(
                            selected = selectedLesson.topic == lesson.topic,
                            onClick = { selectedLesson = lesson },
                            label = {
                                val badge = if (lesson.topic == dailyLesson.topic) " (Today)" else ""
                                Text("${lesson.topic}$badge", fontSize = 12.sp)
                            }
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("daily_micro_lesson_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(color = MintPrimary.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = selectedLesson.topic.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MintPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Text(
                                text = "${selectedLesson.readTimeMinutes} min read",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = selectedLesson.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = selectedLesson.body,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp
                        )
                        Surface(
                            color = FinanceGreen.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = FinanceGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Key Takeaway: ${selectedLesson.keyTakeaway}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
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
    }

    if (showConfirmNoSpend) {
        ConfirmActionDialog(
            title = "Log No-Spend Day?",
            summaryLines = listOf(
                "Mark today as a No-Spend Day (0 non-essential purchases).",
                "Current streak: $noSpendStreakDays day(s) -> updates your Weekly Money Score."
            ),
            confirmLabel = "Log No-Spend Day",
            onConfirm = {
                val added = onLogNoSpendDay()
                snackbarMessage = if (added) "No-spend day logged! Streak updated." else "Already logged for today."
                showConfirmNoSpend = false
            },
            onDismiss = { showConfirmNoSpend = false }
        )
    }

    if (showConfirmCheckIn) {
        ConfirmActionDialog(
            title = "Complete Weekly Check-In?",
            summaryLines = listOf(
                "Current Weekly Score: ${weeklyScore.totalScore}/100 (${weeklyScore.gradeLabel})",
                "Saves today's check-in timestamp in your local ledger."
            ),
            confirmLabel = "Save Check-In",
            onConfirm = {
                onCompleteWeeklyCheckIn()
                snackbarMessage = "Weekly Check-In saved! Score: ${weeklyScore.totalScore}/100."
                showConfirmCheckIn = false
            },
            onDismiss = { showConfirmCheckIn = false }
        )
    }
    if (showConfirmMonthlySnapshot) {
        ConfirmActionDialog(
            title = "Save Monthly Improvement Snapshot?",
            summaryLines = listOf(
                "Month: ${monthlyReport.currentMonthKey}",
                "Money Health Score: ${monthlyReport.currentScore}/100 (${monthlyReport.gradeLabel})",
                "Saves this month's score locally so future reports track month-to-month progress."
            ),
            confirmLabel = "Save Snapshot",
            onConfirm = {
                onSaveMonthlySnapshot(monthlyReport.currentSnapshot)
                snackbarMessage = "Monthly snapshot saved for ${monthlyReport.currentMonthKey}!"
                showConfirmMonthlySnapshot = false
            },
            onDismiss = { showConfirmMonthlySnapshot = false }
        )
    }

    whyDialogAction?.let { action ->
        val actionFeedback = explanationFeedback.firstOrNull {
            it.targetType == "ACTION" && it.targetId == action.actionKey
        }?.madeSense
        val resolvedSummary = FinancialCalculator.resolveActionOneLineSummary(action)
        WhyActionDialog(
            action = action,
            profile = profile,
            dataQualityNotes = monthlyReport.dataQualityNotes,
            coachManager = coachManager,
            scoreComponents = monthlyReport.tracedScore?.components ?: emptyList(),
            explanationFeedbackAnswer = actionFeedback,
            onSubmitExplanationFeedback = { madeSense ->
                onSubmitExplanationFeedback(
                    action.actionKey,
                    "ACTION",
                    action.title,
                    resolvedSummary,
                    action.ruleId.ifBlank { null },
                    madeSense
                )
                snackbarMessage = if (madeSense) "Saved: explanation made sense." else "Saved: flagged as unclear."
            },
            onNavigateDestination = onNavigateActionDestination,
            onOpenHowDecides = {
                whyDialogAction = null
                onOpenHowDecides()
            },
            onFlagError = {
                whyDialogAction = null
                flagDialogTarget = Triple(
                    "TOP_ACTION",
                    action.actionKey,
                    "Action #${action.priority}: ${action.title}" to action.ruleId.ifBlank { null }
                )
            },
            onDismiss = { whyDialogAction = null }
        )
    }

    pendingMarkDoneAction?.let { action ->
        val numbersText = action.traceability?.triggeringNumbers
            ?.joinToString("; ") { "${it.label}: ${it.valueFormatted}" }
            ?: action.howMuch
        ConfirmActionDialog(
            title = "Mark Action Done?",
            summaryLines = listOf(
                "Mark \"${action.title}\" as completed.",
                "Target: ${action.howMuch} (${action.byWhen})",
                "Records this action in your local Activity History."
            ),
            confirmLabel = "Mark Done",
            whyText = action.traceability?.ruleDescription ?: action.what,
            backingNumbers = numbersText,
            ruleId = action.ruleId.ifBlank { null },
            activityCategory = "Action Completed",
            skipActivityLog = true,
            onConfirm = {
                onMarkActionDone(action)
                snackbarMessage = "Marked \"${action.title}\" done & logged to Activity History."
                pendingMarkDoneAction = null
            },
            onDismiss = { pendingMarkDoneAction = null }
        )
    }

    flagDialogTarget?.let { (itemType, itemId, titleAndRule) ->
        val (title, ruleId) = titleAndRule
        ReportErrorDialog(
            itemTitle = title,
            contextSummary = "Score ${monthlyReport.currentScore}/100 (${monthlyReport.currentMonthKey})",
            ruleId = ruleId,
            onSubmit = { note ->
                onFlagError(itemType, itemId, title, note, "Monthly Report ${monthlyReport.currentMonthKey}", ruleId)
                flagDialogTarget = null
                snackbarMessage = "Flag saved locally in Activity History."
            },
            onDismiss = { flagDialogTarget = null }
        )
    }
}

@Composable
private fun MonthlyReportTabContent(
    report: MonthlyImprovementReport,
    profile: UserProfile,
    coachManager: AiCoachManager,
    hasSavedCurrentMonth: Boolean,
    completedActionKeys: Set<String>,
    explanationFeedback: List<ExplanationFeedbackEntry>,
    onSubmitExplanationFeedback: (targetId: String, targetType: String, targetTitle: String, summaryShown: String, ruleId: String?, madeSense: Boolean) -> Unit,
    onOpenScoreDetail: () -> Unit,
    onNavigateActionDestination: (ActionTargetDestination) -> Unit,
    sharingEnabled: Boolean,
    onOpenShareScoreCard: () -> Unit,
    onRequestSaveSnapshot: () -> Unit,
    onWhyAction: (MonthlyActionItem) -> Unit,
    onRequestMarkDone: (MonthlyActionItem) -> Unit,
    onFlagScore: () -> Unit,
    onFlagAction: (MonthlyActionItem) -> Unit
) {
    var expandedActionKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    val feedbackByActionKey = remember(explanationFeedback) {
        explanationFeedback
            .filter { it.targetType == "ACTION" }
            .associateBy { it.targetId }
    }
    val deltaColor = when {
        report.previousScore == null -> MintPrimary
        report.scoreDelta > 0 -> FinanceGreen
        report.scoreDelta < 0 -> FinanceRed
        else -> MintPrimary
    }
    val deltaText = when {
        report.previousScore == null -> "First Baseline"
        report.scoreDelta > 0 -> "+${report.scoreDelta} vs ${report.previousMonthKey}"
        report.scoreDelta < 0 -> "${report.scoreDelta} vs ${report.previousMonthKey}"
        else -> "0 pts vs ${report.previousMonthKey}"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Score + Delta Card (Tappable to open full Money Health Score Breakdown)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenScoreDetail)
                    .testTag("monthly_report_hero_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MintPrimary.copy(alpha = 0.14f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MONEY HEALTH SCORE (${report.currentMonthKey})",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                color = MintPrimary
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${report.currentScore}",
                                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold)
                                )
                                Text(
                                    text = "/100  (${report.gradeLabel})",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                        Surface(color = deltaColor.copy(alpha = 0.18f), shape = RoundedCornerShape(10.dp)) {
                            Text(
                                text = deltaText,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = deltaColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    report.tracedScore?.let { traced ->
                        Text(
                            text = "Last calculated: ${traced.calculatedAtFormatted} • Tap for full breakdown & chart ->",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MintPrimary
                        )
                    }

                    TopLevelDataQualityWarning(
                        notes = report.dataQualityNotes,
                        testTag = "monthly_hero_dq_warning"
                    )

                    Text(
                        text = report.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onRequestSaveSnapshot,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_monthly_snapshot_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                        ) {
                            Icon(Icons.Default.BookmarkAdded, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasSavedCurrentMonth) "Update ${report.currentMonthKey}" else "Save Snapshot",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(
                            onClick = onFlagScore,
                            modifier = Modifier.testTag("flag_monthly_score_btn")
                        ) {
                            Icon(Icons.Default.Flag, contentDescription = "This looks wrong", tint = FinanceRed, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("This looks wrong", color = FinanceRed, fontSize = 11.sp)
                        }
                    }

                    if (sharingEnabled) {
                        OutlinedButton(
                            onClick = onOpenShareScoreCard,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("monthly_report_share_progress_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share my progress", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 4 Pillars Breakdown
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Score Pillars Breakdown",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    report.pillarBreakdown.forEach { pillar ->
                        val frac = if (pillar.maxPoints > 0) (pillar.earnedPoints.toFloat() / pillar.maxPoints).coerceIn(0f, 1f) else 0f
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(pillar.category, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    "${pillar.earnedPoints} / ${pillar.maxPoints} pts",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MintPrimary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { frac },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MintPrimary,
                                trackColor = MaterialTheme.colorScheme.surface
                            )
                            Text(
                                text = pillar.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // What Improved & What Slipped
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("monthly_what_improved_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = FinanceGreen.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = FinanceGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("What Improved", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = FinanceGreen)
                    }
                    report.whatImproved.forEach { item ->
                        Text("• $item", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("monthly_what_slipped_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MintAccentGold.copy(alpha = 0.14f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PriorityHigh, contentDescription = null, tint = MintAccentGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("What Slipped / Needs Attention", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MintAccentGold)
                    }
                    report.whatSlipped.forEach { item ->
                        Text("• $item", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Top 3 Specific Actions for Next Month
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Top 3 Actions for Next Month",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Tap any summary to view full inputs, rule wording, and explanation.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TopLevelDataQualityWarning(
                    notes = report.dataQualityNotes,
                    testTag = "top_actions_dq_warning"
                )
            }
        }

        items(report.top3Actions, key = { it.actionKey.ifBlank { "${it.priority}_${it.title}" } }) { action ->
            val isDone = completedActionKeys.contains(action.actionKey)
            val expectedDelta = action.traceability?.expectedScoreDelta ?: 0
            val isExpanded = expandedActionKeys.contains(action.actionKey)
            val oneLineSummary = remember(action) { FinancialCalculator.resolveActionOneLineSummary(action) }
            val trace = action.traceability
            val savedSense = feedbackByActionKey[action.actionKey]?.madeSense
            val ruleDisplayName = trace?.ruleShortName?.ifBlank { null }
                ?: RuleRegistry.displayRuleName(action.ruleId)
            val ruleVersion = trace?.ruleVersion?.ifBlank { null }
                ?: RuleRegistry.displayRuleVersion(action.ruleId)
            val ruleWording = trace?.ruleDescription?.ifBlank { null }
                ?: RuleRegistry.displayRuleWording(action.ruleId, action.what)

            var traceableExplanation by remember(action.actionKey) { mutableStateOf<TraceableAiExplanation?>(null) }
            var isLoadingAi by remember(action.actionKey) { mutableStateOf(false) }

            LaunchedEffect(isExpanded, action.actionKey) {
                if (isExpanded && traceableExplanation == null) {
                    isLoadingAi = true
                    traceableExplanation = coachManager.explainActionTraceable(action, profile)
                    isLoadingAi = false
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("top_action_card_${action.priority}"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDone) FinanceGreen.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Tappable One-Line Summary Header (Action · Key Number · Rule Name)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                expandedActionKeys = if (isExpanded) {
                                    expandedActionKeys - action.actionKey
                                } else {
                                    expandedActionKeys + action.actionKey
                                }
                            }
                            .padding(vertical = 4.dp)
                            .testTag("action_summary_${action.priority}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = CircleShape,
                                color = (if (isDone) FinanceGreen else MintPrimary).copy(alpha = 0.2f),
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (isDone) "✓" else "${action.priority}",
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDone) FinanceGreen else MintPrimary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = oneLineSummary,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = if (isExpanded) 3 else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse details" else "Expand snapshot details",
                            tint = MintPrimary
                        )
                    }

                    // Top-level data quality flag shown ONLY when low quality affects the result
                    TopLevelDataQualityWarning(
                        notes = report.dataQualityNotes,
                        testTag = "action_dq_warning_${action.priority}"
                    )

                    // Scannable collapsed rows in consistent order (no long paragraphs)
                    ScannableLabelValueRow(
                        label = "Target",
                        value = action.howMuch,
                        valueColor = MintPrimary
                    )
                    ScannableLabelValueRow(
                        label = "When",
                        value = action.byWhen
                    )
                    ScannableLabelValueRow(
                        label = "Score impact",
                        value = if (expectedDelta > 0) "+$expectedDelta pts (${report.currentScore} -> ${trace?.projectedNewScore})" else "Maintains trend",
                        valueColor = if (expectedDelta > 0) FinanceGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!trace?.whatWouldChangeThis.isNullOrBlank()) {
                        ScannableLabelValueRow(
                            label = "What would change this?",
                            value = trace!!.whatWouldChangeThis,
                            maxLines = 2,
                            modifier = Modifier.testTag("action_change_threshold_${action.priority}")
                        )
                    }

                    // Expanded Full Decision Snapshot
                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .testTag("action_expanded_${action.priority}"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalDivider()

                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "What to do",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MintPrimary
                                    )
                                    Text(
                                        text = action.what,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            Surface(
                                color = MintPrimary.copy(alpha = 0.09f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Rule & Version: $ruleDisplayName (${action.ruleId} · $ruleVersion)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MintPrimary
                                    )
                                    Text(
                                        text = ruleWording,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (!trace?.triggerCondition.isNullOrBlank()) {
                                        Text(
                                            text = "Condition: ${trace?.triggerCondition}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (trace != null && trace.triggeringNumbers.isNotEmpty()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Inputs & Sources Used",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        trace.triggeringNumbers.forEach { metric ->
                                            ScannableLabelValueRow(
                                                label = metric.label,
                                                value = metric.valueFormatted
                                            )
                                        }
                                        if (trace.dataSources.isNotEmpty()) {
                                            Text(
                                                text = "Sources: ${trace.dataSources.joinToString(", ")}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                        if (trace.contributors.isNotEmpty()) {
                                            trace.contributors.forEach { contributor ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .defaultMinSize(minHeight = 40.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { onNavigateActionDestination(contributor.destination) }
                                                        .padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = contributor.label,
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                                        )
                                                        Text(
                                                            text = "${contributor.detail} · Open ${contributor.destination.label}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MintPrimary
                                                        )
                                                    }
                                                    Icon(
                                                        imageVector = Icons.Default.ChevronRight,
                                                        contentDescription = contributor.destination.label,
                                                        tint = MintPrimary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            val scoreComponents = report.tracedScore?.components.orEmpty()
                            if (scoreComponents.isNotEmpty()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Score Components Snapshot (${report.currentScore}/100)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        scoreComponents.forEach { comp ->
                                            ScannableLabelValueRow(
                                                label = "${comp.name} (${comp.weightPercent}%)",
                                                value = "${comp.earnedPoints}/${comp.maxPoints} pts"
                                            )
                                        }
                                    }
                                }
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "AI Plain-Language Explanation",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (isLoadingAi) {
                                        Text(
                                            text = "Building plain-language summary from stored rule facts...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        val exp = traceableExplanation
                                        if (exp != null) {
                                            TraceableExplanationTextWithChips(
                                                templateWithIds = exp.templateWithIds,
                                                facts = exp.facts,
                                                isFallback = exp.isFallback,
                                                fallbackNote = exp.note,
                                                onNavigateDestination = onNavigateActionDestination,
                                                testTagPrefix = "action_trace_${action.priority}"
                                            )
                                        } else {
                                            Text(
                                                text = action.what,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Data-Quality Notes",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                DataQualityBadgeRow(notes = report.dataQualityNotes)
                                report.dataQualityNotes.forEach { note ->
                                    Text(
                                        text = "• ${note.label}: ${note.detail}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            ExplanationSenseCheckRow(
                                currentAnswer = savedSense,
                                onSelectAnswer = { madeSense ->
                                    onSubmitExplanationFeedback(
                                        action.actionKey,
                                        "ACTION",
                                        action.title,
                                        oneLineSummary,
                                        action.ruleId.ifBlank { null },
                                        madeSense
                                    )
                                },
                                yesTag = "action_sense_yes_${action.priority}",
                                noTag = "action_sense_no_${action.priority}"
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onWhyAction(action) },
                            modifier = Modifier.testTag("why_action_btn_${action.priority}"),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Why this?", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { onRequestMarkDone(action) },
                            enabled = !isDone,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDone) FinanceGreen else MintPrimary
                            ),
                            modifier = Modifier.testTag("mark_action_done_btn_${action.priority}"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(if (isDone) "Done ✓" else "Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(
                            onClick = { onFlagAction(action) },
                            modifier = Modifier.testTag("flag_action_btn_${action.priority}")
                        ) {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = "This looks wrong",
                                tint = FinanceRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeIdeasLabScreen(
    profile: UserProfile,
    onOpenSideHustleTracker: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ideas = remember(profile) { FinancialCalculator.getIncomeIdeasForProfile(profile) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Income Ideas Lab",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Practical ideas matched to ${profile.sideIncomeHoursPerWeek} hrs/week",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("ideas_back_btn")) {
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

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MintPrimary.copy(alpha = 0.14f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Tailored for ${profile.userSituation} (${profile.country})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MintPrimary
                        )
                        Text(
                            text = "Always avoid 'pay upfront to get hired' offers. Log every dollar earned AND spent inside your Side-Hustle Profit Tracker.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(onClick = onOpenSideHustleTracker) {
                            Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Side-Hustle Profit Tracker")
                        }
                    }
                }
            }

            items(ideas) { idea ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = idea.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f)
                            )
                            Surface(color = FinanceGreen.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = "Cost: ${idea.startupCost}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = FinanceGreen,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${idea.bestFor} • ${idea.weeklyHoursNeeded}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MintPrimary
                        )
                        Text(
                            text = idea.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "First Step: ${idea.firstStep}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
