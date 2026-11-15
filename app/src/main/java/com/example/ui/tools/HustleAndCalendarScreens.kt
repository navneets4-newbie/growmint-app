package com.example.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.example.data.FinancialCalculator
import com.example.model.*
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.PersistentDisclaimerBanner
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SideHustleTrackerScreen(
    profile: UserProfile,
    entries: List<SideHustleEntry>,
    report: SideHustleReport,
    onAddEntry: (SideHustleEntry) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingAddEntry by remember { mutableStateOf<SideHustleEntry?>(null) }
    var pendingDeleteEntry by remember { mutableStateOf<SideHustleEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Side-Hustle Profit Tracker",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Track income minus costs per gig & month",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("hustle_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.padding(end = 8.dp).testTag("add_hustle_entry_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Log", fontWeight = FontWeight.Bold)
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

            // Summary Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hustle_summary_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NET SIDE-HUSTLE PROFIT",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                color = MintPrimary
                            )
                            Surface(
                                color = (if (report.overallMarginPercent >= 0) FinanceGreen else FinanceRed).copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", report.overallMarginPercent)}% Margin",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (report.overallMarginPercent >= 0) FinanceGreen else FinanceRed,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = CurrenciesAndCountries.format(report.totalProfit, profile.currencyCode),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (report.totalProfit >= 0) FinanceGreen else FinanceRed
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Gross Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(CurrenciesAndCountries.format(report.totalIncome, profile.currencyCode), fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Total Costs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(CurrenciesAndCountries.format(report.totalExpenses, profile.currencyCode), fontWeight = FontWeight.Bold, color = FinanceRed)
                            }
                        }
                    }
                }
            }

            // Best & Worst Month
            if (report.bestMonth != null || report.worstMonth != null) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        report.bestMonth?.let { best ->
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = FinanceGreen.copy(alpha = 0.12f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Best Month", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = FinanceGreen)
                                    Text(best.monthKey, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${CurrenciesAndCountries.format(best.netProfit, profile.currencyCode)} (${String.format(Locale.US, "%.0f", best.marginPercent)}%)",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = FinanceGreen
                                    )
                                }
                            }
                        }
                        report.worstMonth?.let { worst ->
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MintAccentGold.copy(alpha = 0.14f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Slowest Month", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MintAccentGold)
                                    Text(worst.monthKey, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${CurrenciesAndCountries.format(worst.netProfit, profile.currencyCode)} (${String.format(Locale.US, "%.0f", worst.marginPercent)}%)",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (worst.netProfit < 0) FinanceRed else MintAccentGold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Monthly Breakdown
            if (report.monthlySummaries.isNotEmpty()) {
                item {
                    Text(
                        text = "Monthly Profit & Margin",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(report.monthlySummaries) { m ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = m.monthKey, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "In: ${CurrenciesAndCountries.format(m.totalIncome, profile.currencyCode)} • Costs: ${CurrenciesAndCountries.format(m.totalExpenses, profile.currencyCode)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = CurrenciesAndCountries.format(m.netProfit, profile.currencyCode),
                                    fontWeight = FontWeight.Bold,
                                    color = if (m.netProfit >= 0) FinanceGreen else FinanceRed
                                )
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", m.marginPercent)}% margin",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Entries
            item {
                Text(
                    text = "Logged Side-Hustle Entries (${entries.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (entries.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = MintPrimary, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No side-hustle entries yet", fontWeight = FontWeight.Bold)
                            Text(
                                text = "Tap 'Log' above to record income and expenses (materials, software, travel) for any side hustle.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(entries, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "${entry.hustleName} (${entry.monthKey})", fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Income: ${CurrenciesAndCountries.format(entry.income, profile.currencyCode)} • Costs: ${CurrenciesAndCountries.format(entry.expenses, profile.currencyCode)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Profit: ${CurrenciesAndCountries.format(entry.profit, profile.currencyCode)} (${String.format(Locale.US, "%.1f", entry.marginPercent)}%)",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (entry.profit >= 0) FinanceGreen else FinanceRed
                                )
                            }
                            IconButton(onClick = { pendingDeleteEntry = entry }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Entry", tint = FinanceRed)
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showAddDialog) {
        val defaultMonth = remember {
            SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        }
        var name by remember { mutableStateOf("") }
        var monthKey by remember { mutableStateOf(defaultMonth) }
        var incomeText by remember { mutableStateOf("") }
        var expensesText by remember { mutableStateOf("0") }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Log Side-Hustle Entry") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Side Hustle Name (e.g. Tutoring, Design)") },
                        singleLine = true,
                        modifier = Modifier.testTag("hustle_name_input")
                    )
                    OutlinedTextField(
                        value = monthKey,
                        onValueChange = { monthKey = it },
                        label = { Text("Month (YYYY-MM)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = incomeText,
                        onValueChange = { incomeText = it },
                        label = { Text("Income Earned (${profile.currencyCode})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.testTag("hustle_income_input")
                    )
                    OutlinedTextField(
                        value = expensesText,
                        onValueChange = { expensesText = it },
                        label = { Text("Costs / Expenses (${profile.currencyCode})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.testTag("hustle_expenses_input")
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (optional)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val inc = incomeText.toDoubleOrNull() ?: 0.0
                        val exp = expensesText.toDoubleOrNull() ?: 0.0
                        if (name.isNotBlank() && (inc > 0 || exp > 0)) {
                            pendingAddEntry = SideHustleEntry(
                                hustleName = name.trim(),
                                monthKey = monthKey.ifBlank { defaultMonth },
                                income = inc,
                                expenses = exp,
                                notes = notes.trim()
                            )
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                ) {
                    Text("Review")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    pendingAddEntry?.let { entry ->
        ConfirmActionDialog(
            title = "Save Side-Hustle Entry?",
            summaryLines = listOf(
                "Hustle: ${entry.hustleName} (${entry.monthKey})",
                "Income: ${CurrenciesAndCountries.format(entry.income, profile.currencyCode)}",
                "Expenses: ${CurrenciesAndCountries.format(entry.expenses, profile.currencyCode)}",
                "Calculated Profit: ${CurrenciesAndCountries.format(entry.profit, profile.currencyCode)} (${String.format(Locale.US, "%.1f", entry.marginPercent)}% margin)"
            ),
            confirmLabel = "Save Entry",
            onConfirm = {
                onAddEntry(entry)
                pendingAddEntry = null
            },
            onDismiss = { pendingAddEntry = null }
        )
    }

    pendingDeleteEntry?.let { entry ->
        ConfirmActionDialog(
            title = "Delete Side-Hustle Entry?",
            summaryLines = listOf(
                "Remove ${entry.hustleName} (${entry.monthKey})",
                "Profit recorded: ${CurrenciesAndCountries.format(entry.profit, profile.currencyCode)}"
            ),
            confirmLabel = "Delete",
            isDestructive = true,
            onConfirm = {
                onDeleteEntry(entry.id)
                pendingDeleteEntry = null
            },
            onDismiss = { pendingDeleteEntry = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlineCalendarScreen(
    profile: UserProfile,
    items: List<CalendarReminderItem>,
    upcoming30: List<UpcomingDeadlineView>,
    onAddItem: (CalendarReminderItem) -> Unit,
    onTogglePaid: (String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingAddItem by remember { mutableStateOf<CalendarReminderItem?>(null) }
    var pendingTogglePaidItem by remember { mutableStateOf<CalendarReminderItem?>(null) }
    var pendingDeleteItem by remember { mutableStateOf<CalendarReminderItem?>(null) }

    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Deadline & Renewal Calendar",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Exam fees, rent, subscriptions & renewals (In-app reminders)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("calendar_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.padding(end = 8.dp).testTag("add_calendar_item_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontWeight = FontWeight.Bold)
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
                val dueSoonCount = upcoming30.count { it.isDueSoon }
                val totalUpcomingAmount = upcoming30.filter { !it.item.isPaid }.sumOf { it.item.amount }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calendar_summary_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (dueSoonCount > 0) MintAccentGold.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Next 30 Days (${upcoming30.size} items)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            if (dueSoonCount > 0) {
                                Surface(color = FinanceRed.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
                                    Text(
                                        text = "$dueSoonCount Due soon",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = FinanceRed,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Unpaid upcoming total: ${CurrenciesAndCountries.format(totalUpcomingAmount, profile.currencyCode)}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = MintPrimary
                        )
                        Text(
                            text = "In-app reminders only — no system push notifications or extra permissions needed.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Upcoming in Next 30 Days",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (upcoming30.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EventAvailable, contentDescription = null, tint = MintPrimary, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No deadlines in the next 30 days", fontWeight = FontWeight.Bold)
                            Text(
                                text = "Tap 'Add' to track rent, exam fees, insurance, or subscriptions so nothing surprises you.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(upcoming30, key = { it.item.id }) { view ->
                    val statusLabel = when {
                        view.daysUntilDue < 0 -> "Overdue ${-view.daysUntilDue}d"
                        view.daysUntilDue == 0 -> "Due today"
                        view.daysUntilDue == 1 -> "Tomorrow"
                        else -> "In ${view.daysUntilDue} days"
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = view.item.title, fontWeight = FontWeight.Bold)
                                    if (view.isDueSoon && !view.item.isPaid) {
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
                                    text = "$statusLabel • ${dateFormatter.format(Date(view.effectiveDueMillis))} • ${view.item.repeat.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = CurrenciesAndCountries.format(view.item.amount, profile.currencyCode),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (view.item.isPaid) FinanceGreen else MintPrimary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { pendingTogglePaidItem = view.item }) {
                                    Icon(
                                        imageVector = if (view.item.isPaid) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Mark Paid",
                                        tint = if (view.item.isPaid) FinanceGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { pendingDeleteItem = view.item }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = FinanceRed)
                                }
                            }
                        }
                    }
                }
            }

            if (items.size > upcoming30.size) {
                item {
                    Text(
                        text = "All Saved Deadlines (${items.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(items.filter { it.id !in upcoming30.map { u -> u.item.id } }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = item.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${dateFormatter.format(Date(item.dueDateMillis))} • ${item.repeat.label} • ${CurrenciesAndCountries.format(item.amount, profile.currencyCode)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { pendingDeleteItem = item }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = FinanceRed)
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Bills & Renewals") }
        var amountText by remember { mutableStateOf("") }
        var daysFromNowText by remember { mutableStateOf("5") }
        var selectedRepeat by remember { mutableStateOf(RepeatFrequency.MONTHLY) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Deadline or Renewal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title (e.g. Exam Fee, Rent, Renewal)") },
                        singleLine = true,
                        modifier = Modifier.testTag("calendar_title_input")
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount (${profile.currencyCode})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.testTag("calendar_amount_input")
                    )
                    OutlinedTextField(
                        value = daysFromNowText,
                        onValueChange = { daysFromNowText = it },
                        label = { Text("Days from today (e.g. 3, 7, 15, 30)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.testTag("calendar_days_input")
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        singleLine = true
                    )
                    Text("Repeat:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        RepeatFrequency.values().forEach { freq ->
                            FilterChip(
                                selected = selectedRepeat == freq,
                                onClick = { selectedRepeat = freq },
                                label = { Text(freq.label.substringBefore(" "), fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        val days = daysFromNowText.toIntOrNull()?.coerceIn(0, 365) ?: 0
                        if (title.isNotBlank() && amt >= 0.0) {
                            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }
                            pendingAddItem = CalendarReminderItem(
                                title = title.trim(),
                                category = category.ifBlank { "Bills & Renewals" },
                                amount = amt,
                                dueDateMillis = cal.timeInMillis,
                                repeat = selectedRepeat
                            )
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                ) {
                    Text("Review")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    pendingAddItem?.let { item ->
        ConfirmActionDialog(
            title = "Add Reminder Item?",
            summaryLines = listOf(
                "Title: ${item.title}",
                "Amount: ${CurrenciesAndCountries.format(item.amount, profile.currencyCode)}",
                "Due: ${dateFormatter.format(Date(item.dueDateMillis))}",
                "Repeat: ${item.repeat.label}"
            ),
            confirmLabel = "Add to Calendar",
            onConfirm = {
                onAddItem(item)
                pendingAddItem = null
            },
            onDismiss = { pendingAddItem = null }
        )
    }

    pendingTogglePaidItem?.let { item ->
        ConfirmActionDialog(
            title = if (item.isPaid) "Mark Unpaid?" else "Mark as Paid?",
            summaryLines = listOf(
                "Item: ${item.title}",
                "Amount: ${CurrenciesAndCountries.format(item.amount, profile.currencyCode)}"
            ),
            confirmLabel = "Confirm",
            onConfirm = {
                onTogglePaid(item.id)
                pendingTogglePaidItem = null
            },
            onDismiss = { pendingTogglePaidItem = null }
        )
    }

    pendingDeleteItem?.let { item ->
        ConfirmActionDialog(
            title = "Delete Reminder?",
            summaryLines = listOf(
                "Remove '${item.title}' (${CurrenciesAndCountries.format(item.amount, profile.currencyCode)})"
            ),
            confirmLabel = "Delete",
            isDestructive = true,
            onConfirm = {
                onDeleteItem(item.id)
                pendingDeleteItem = null
            },
            onDismiss = { pendingDeleteItem = null }
        )
    }
}
