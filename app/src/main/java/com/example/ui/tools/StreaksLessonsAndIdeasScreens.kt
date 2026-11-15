package com.example.ui.tools

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FinancialCalculator
import com.example.model.*
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.PersistentDisclaimerBanner
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
                    hasSavedCurrentMonth = hasSavedCurrentMonth,
                    onRequestSaveSnapshot = { showConfirmMonthlySnapshot = true }
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
}

@Composable
private fun MonthlyReportTabContent(
    report: MonthlyImprovementReport,
    hasSavedCurrentMonth: Boolean,
    onRequestSaveSnapshot: () -> Unit
) {
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
        // Hero Score + Delta Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
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
                        Column {
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

                    Text(
                        text = report.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Button(
                        onClick = onRequestSaveSnapshot,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_monthly_snapshot_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                    ) {
                        Icon(Icons.Default.BookmarkAdded, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasSavedCurrentMonth) "Update ${report.currentMonthKey} Snapshot" else "Save Monthly Snapshot",
                            fontWeight = FontWeight.Bold
                        )
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
            Text(
                text = "Top 3 Actions for Next Month",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        items(report.top3Actions) { action ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MintPrimary.copy(alpha = 0.2f), modifier = Modifier.size(26.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${action.priority}", fontWeight = FontWeight.Bold, color = MintPrimary, fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(action.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Text("What: ${action.what}", style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "How much: ${action.howMuch}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MintPrimary
                        )
                        Text(
                            text = action.byWhen,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
