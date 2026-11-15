package com.example.ui.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtPlannerScreen(
    profile: UserProfile,
    onAddDebt: (DebtItem) -> Unit,
    onDeleteDebt: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var extraMonthlyText by remember { mutableStateOf("0") }
    var showAddDebtDialog by remember { mutableStateOf(false) }
    var pendingAddDebt by remember { mutableStateOf<DebtItem?>(null) }
    var pendingDeleteDebt by remember { mutableStateOf<DebtItem?>(null) }
    var scheduleStrategyTab by remember { mutableIntStateOf(0) } // 0 = Snowball, 1 = Avalanche
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val extraPayment = (extraMonthlyText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
    val comparison = remember(profile.debts, extraPayment) {
        FinancialCalculator.calculateDebtPayoffComparison(profile.debts, extraPayment)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Debt Payoff Planner",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Compare Snowball vs Avalanche month by month",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("debt_planner_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!profile.isUnder18) {
                        TextButton(
                            onClick = { showAddDebtDialog = true },
                            modifier = Modifier.testTag("debt_planner_add_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Debt")
                        }
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
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { PersistentDisclaimerBanner() }

            if (profile.isUnder18) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("guided_mode_debt_hidden_card"),
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
                                text = "The Debt Payoff Planner is hidden in Guided Mode so you can focus on building your savings, budgeting skills, and safety buffer.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // Extra Monthly Payment Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Optional Extra Monthly Payment",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedTextField(
                                value = extraMonthlyText,
                                onValueChange = { extraMonthlyText = it },
                                label = { Text("Extra Payment Above Minimums (${profile.currencyCode}/mo)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("debt_extra_payment_input")
                            )
                            val totalMin = profile.debts.sumOf { it.minimumPayment }
                            Text(
                                text = "Total monthly plan: ${CurrenciesAndCountries.format(totalMin + extraPayment, profile.currencyCode)} (${CurrenciesAndCountries.format(totalMin, profile.currencyCode)} mins + ${CurrenciesAndCountries.format(extraPayment, profile.currencyCode)} extra)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Current Debts List
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Your Debts (${profile.debts.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(onClick = { showAddDebtDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Debt")
                        }
                    }
                }

                if (profile.debts.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = FinanceGreen.copy(alpha = 0.12f))
                        ) {
                            Text(
                                text = "No debts logged! You are debt-free. Tap 'Add Debt' if you want to model a credit card, loan, or balance.",
                                style = MaterialTheme.typography.bodySmall,
                                color = FinanceGreen,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                } else {
                    items(profile.debts) { debt ->
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = debt.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "Balance: ${CurrenciesAndCountries.format(debt.totalAmount, profile.currencyCode)} • ${debt.interestRate}% APR • Min: ${CurrenciesAndCountries.format(debt.minimumPayment, profile.currencyCode)}/mo",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { pendingDeleteDebt = debt }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete debt", tint = FinanceRed)
                                }
                            }
                        }
                    }

                    // Side-by-side Snowball vs Avalanche Comparison
                    item {
                        Text(
                            text = "Snowball vs Avalanche Side-by-Side",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("debt_comparison_row"),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StrategyComparisonCard(
                                result = comparison.snowball,
                                subtitle = "Smallest balance first",
                                currencyCode = profile.currencyCode,
                                accent = FinanceBlue,
                                modifier = Modifier.weight(1f)
                            )
                            StrategyComparisonCard(
                                result = comparison.avalanche,
                                subtitle = "Highest interest first",
                                currencyCode = profile.currencyCode,
                                accent = FinanceGreen,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 1-Line Explanation + Interest Saved
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MintPrimary.copy(alpha = 0.14f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (comparison.interestSavedByAvalanche >= 1.0) {
                                    Text(
                                        text = "Avalanche saves ${CurrenciesAndCountries.format(comparison.interestSavedByAvalanche, profile.currencyCode)} in interest",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = FinanceGreen
                                    )
                                }
                                Text(
                                    text = comparison.differenceOneLiner,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Month-by-Month Schedule Viewer
                    item {
                        Text(
                            text = "Month-by-Month Payoff Schedule",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = scheduleStrategyTab == 0,
                                onClick = { scheduleStrategyTab = 0 },
                                label = { Text("Snowball (${comparison.snowball.monthsToPayoff} mos)") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = scheduleStrategyTab == 1,
                                onClick = { scheduleStrategyTab = 1 },
                                label = { Text("Avalanche (${comparison.avalanche.monthsToPayoff} mos)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    val activeSchedule = if (scheduleStrategyTab == 0) comparison.snowball.schedule else comparison.avalanche.schedule
                    items(activeSchedule.take(60)) { monthRow ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Mo ${monthRow.monthNumber} (${monthRow.monthLabel}) • Focus: ${monthRow.activeFocusDebt}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Paid: ${CurrenciesAndCountries.format(monthRow.totalPayment, profile.currencyCode)} (Interest: ${CurrenciesAndCountries.format(monthRow.interestPaid, profile.currencyCode)})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "Left: ${CurrenciesAndCountries.format(monthRow.remainingBalance, profile.currencyCode)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (monthRow.remainingBalance <= 0.01) FinanceGreen else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    if (activeSchedule.size > 60) {
                        item {
                            Text(
                                text = "Showing first 60 months of ${activeSchedule.size} total months.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showAddDebtDialog) {
        var name by remember { mutableStateOf("") }
        var balanceText by remember { mutableStateOf("") }
        var rateText by remember { mutableStateOf("") }
        var minPayText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDebtDialog = false },
            title = { Text("Add Debt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Debt Name (e.g. Credit Card, Car Loan)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = balanceText,
                        onValueChange = { balanceText = it },
                        label = { Text("Balance (${profile.currencyCode})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = { Text("Annual Interest Rate (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = minPayText,
                        onValueChange = { minPayText = it },
                        label = { Text("Minimum Monthly Payment (${profile.currencyCode})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bal = balanceText.toDoubleOrNull() ?: 0.0
                        val rate = (rateText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                        val minPay = minPayText.toDoubleOrNull() ?: maxOf(10.0, bal * 0.02)
                        if (name.isNotBlank() && bal > 0.0) {
                            showAddDebtDialog = false
                            pendingAddDebt = DebtItem(
                                name = name.trim(),
                                totalAmount = bal,
                                interestRate = rate,
                                minimumPayment = maxOf(1.0, minPay)
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                ) { Text("Review") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDebtDialog = false }) { Text("Cancel") }
            }
        )
    }

    pendingAddDebt?.let { debt ->
        ConfirmActionDialog(
            title = "Add Debt to Planner?",
            summaryLines = listOf(
                "Name: ${debt.name}",
                "Balance: ${CurrenciesAndCountries.format(debt.totalAmount, profile.currencyCode)}",
                "Rate: ${debt.interestRate}% APR • Min: ${CurrenciesAndCountries.format(debt.minimumPayment, profile.currencyCode)}/mo"
            ),
            confirmLabel = "Add Debt",
            onConfirm = {
                onAddDebt(debt)
                snackbarMessage = "Added ${debt.name} to Debt Payoff Planner."
                pendingAddDebt = null
            },
            onDismiss = { pendingAddDebt = null }
        )
    }

    pendingDeleteDebt?.let { debt ->
        ConfirmActionDialog(
            title = "Remove '${debt.name}'?",
            summaryLines = listOf(
                "Removes ${CurrenciesAndCountries.format(debt.totalAmount, profile.currencyCode)} debt entry from your planner."
            ),
            confirmLabel = "Remove",
            isDestructive = true,
            onConfirm = {
                onDeleteDebt(debt.id)
                snackbarMessage = "Removed ${debt.name}."
                pendingDeleteDebt = null
            },
            onDismiss = { pendingDeleteDebt = null }
        )
    }
}

@Composable
private fun StrategyComparisonCard(
    result: DebtPayoffStrategyResult,
    subtitle: String,
    currencyCode: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = result.strategyName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = accent
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            Text("Payoff Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = result.payoffDateLabel,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "${result.monthsToPayoff} months",
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text("Total Interest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = CurrenciesAndCountries.format(result.totalInterestPaid, currencyCode),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)
            )
        }
    }
}
