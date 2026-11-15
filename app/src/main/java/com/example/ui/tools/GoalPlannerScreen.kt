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
import androidx.compose.ui.draw.clip
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
fun GoalPlannerScreen(
    profile: UserProfile,
    onAddGoal: (GoalItem) -> Unit,
    onUpdateGoalSaved: (String, Double) -> Unit,
    onDeleteGoal: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddGoalDialog by remember { mutableStateOf(false) }
    var presetCategoryForNewGoal by remember { mutableStateOf("House") }
    var goalBeingUpdated by remember { mutableStateOf<GoalItem?>(null) }
    var pendingAddGoal by remember { mutableStateOf<GoalItem?>(null) }
    var pendingUpdateGoalAmount by remember { mutableStateOf<Pair<GoalItem, Double>?>(null) }
    var pendingDeleteGoal by remember { mutableStateOf<GoalItem?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val analyses = remember(profile) { FinancialCalculator.analyzeAllGoals(profile) }
    val categories = listOf("House", "Car", "Trip", "Retirement", "Custom")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Goal Planner",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Monthly savings needed, progress & on-track pace",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("goal_planner_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            presetCategoryForNewGoal = "Custom"
                            showAddGoalDialog = true
                        },
                        modifier = Modifier.testTag("goal_planner_add_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Goal")
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

            // Quick Category Launchers
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Quick Add by Goal Type",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            categories.take(3).forEach { cat ->
                                OutlinedButton(
                                    onClick = {
                                        presetCategoryForNewGoal = cat
                                        showAddGoalDialog = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Text("+ $cat", fontSize = 12.sp)
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            categories.drop(3).forEach { cat ->
                                OutlinedButton(
                                    onClick = {
                                        presetCategoryForNewGoal = cat
                                        showAddGoalDialog = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Text("+ $cat", fontSize = 12.sp)
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            if (analyses.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MintPrimary.copy(alpha = 0.12f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No goals added yet", fontWeight = FontWeight.Bold, color = MintPrimary)
                            Text(
                                text = "Add a goal (House, Car, Trip, Retirement, or Custom) with your target amount and deadline to calculate how much to save monthly.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            } else {
                items(analyses) { analysis ->
                    val g = analysis.goal
                    val badgeColor = when {
                        analysis.isOnTrack -> FinanceGreen
                        analysis.statusLabel == "Tight pace" -> MintAccentGold
                        else -> FinanceRed
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("goal_plan_card_${g.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Surface(color = MintPrimary.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
                                        Text(
                                            text = g.category,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MintPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = g.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Surface(color = badgeColor.copy(alpha = 0.18f), shape = RoundedCornerShape(8.dp)) {
                                    Text(
                                        text = analysis.statusLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = badgeColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Saved: ${CurrenciesAndCountries.format(g.currentAmount, profile.currencyCode)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Target: ${CurrenciesAndCountries.format(g.targetAmount, profile.currencyCode)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            LinearProgressIndicator(
                                progress = { analysis.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = badgeColor,
                                trackColor = MaterialTheme.colorScheme.surface
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Monthly Needed (${analysis.monthsRemaining} mos left)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${CurrenciesAndCountries.format(analysis.monthlyAmountNeeded, profile.currencyCode)} / mo",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MintPrimary
                                    )
                                }
                                Text(
                                    text = "Deadline: ${g.targetDate}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = analysis.statusReason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(onClick = { goalBeingUpdated = g }) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Update Saved")
                                }
                                IconButton(onClick = { pendingDeleteGoal = g }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete goal", tint = FinanceRed)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showAddGoalDialog) {
        var selectedCat by remember(presetCategoryForNewGoal) { mutableStateOf(presetCategoryForNewGoal) }
        var nameText by remember(presetCategoryForNewGoal) {
            mutableStateOf(if (presetCategoryForNewGoal == "Custom") "" else "$presetCategoryForNewGoal Fund")
        }
        var targetText by remember { mutableStateOf("") }
        var savedText by remember { mutableStateOf("0") }
        var deadlineText by remember { mutableStateOf("Dec 2027") }

        AlertDialog(
            onDismissRequest = { showAddGoalDialog = false },
            title = { Text("Add Financial Goal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Goal Type", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    categories.chunked(3).forEach { chunk ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            chunk.forEach { cat ->
                                FilterChip(
                                    selected = selectedCat == cat,
                                    onClick = {
                                        selectedCat = cat
                                        if (nameText.isBlank() || nameText.endsWith(" Fund")) {
                                            nameText = if (cat == "Custom") "" else "$cat Fund"
                                        }
                                    },
                                    label = { Text(cat, fontSize = 11.sp, maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (chunk.size < 3) {
                                repeat(3 - chunk.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        label = { Text("Goal Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { targetText = it },
                        label = { Text("Target Amount (${profile.currencyCode})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = savedText,
                        onValueChange = { savedText = it },
                        label = { Text("Amount Saved So Far (${profile.currencyCode})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = deadlineText,
                        onValueChange = { deadlineText = it },
                        label = { Text("Deadline (e.g. Dec 2027 or 2027-12)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = targetText.toDoubleOrNull() ?: 0.0
                        val saved = (savedText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                        if (nameText.isNotBlank() && target > 0.0 && deadlineText.isNotBlank()) {
                            showAddGoalDialog = false
                            pendingAddGoal = GoalItem(
                                name = nameText.trim(),
                                targetAmount = target,
                                currentAmount = saved,
                                targetDate = deadlineText.trim(),
                                category = selectedCat
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                ) { Text("Review") }
            },
            dismissButton = {
                TextButton(onClick = { showAddGoalDialog = false }) { Text("Cancel") }
            }
        )
    }

    goalBeingUpdated?.let { goal ->
        var updatedAmtText by remember(goal) { mutableStateOf(goal.currentAmount.toString()) }
        AlertDialog(
            onDismissRequest = { goalBeingUpdated = null },
            title = { Text("Update Saved Amount") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "${goal.name} (Target: ${CurrenciesAndCountries.format(goal.targetAmount, profile.currencyCode)})", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = updatedAmtText,
                        onValueChange = { updatedAmtText = it },
                        label = { Text("Total Saved So Far (${profile.currencyCode})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newAmt = (updatedAmtText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                        goalBeingUpdated = null
                        pendingUpdateGoalAmount = goal to newAmt
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                ) { Text("Review") }
            },
            dismissButton = {
                TextButton(onClick = { goalBeingUpdated = null }) { Text("Cancel") }
            }
        )
    }

    pendingAddGoal?.let { g ->
        ConfirmActionDialog(
            title = "Add Goal '${g.name}'?",
            summaryLines = listOf(
                "Category: ${g.category}",
                "Target: ${CurrenciesAndCountries.format(g.targetAmount, profile.currencyCode)} by ${g.targetDate}",
                "Saved so far: ${CurrenciesAndCountries.format(g.currentAmount, profile.currencyCode)}"
            ),
            confirmLabel = "Add Goal",
            onConfirm = {
                onAddGoal(g)
                snackbarMessage = "Added goal: ${g.name}."
                pendingAddGoal = null
            },
            onDismiss = { pendingAddGoal = null }
        )
    }

    pendingUpdateGoalAmount?.let { (g, amt) ->
        ConfirmActionDialog(
            title = "Update Saved Progress?",
            summaryLines = listOf(
                "Goal: ${g.name}",
                "New Saved Total: ${CurrenciesAndCountries.format(amt, profile.currencyCode)}"
            ),
            confirmLabel = "Update",
            onConfirm = {
                onUpdateGoalSaved(g.id, amt)
                snackbarMessage = "Updated saved progress for ${g.name}."
                pendingUpdateGoalAmount = null
            },
            onDismiss = { pendingUpdateGoalAmount = null }
        )
    }

    pendingDeleteGoal?.let { g ->
        ConfirmActionDialog(
            title = "Delete Goal '${g.name}'?",
            summaryLines = listOf("Removes this goal from your Goal Planner."),
            confirmLabel = "Delete",
            isDestructive = true,
            onConfirm = {
                onDeleteGoal(g.id)
                snackbarMessage = "Deleted ${g.name}."
                pendingDeleteGoal = null
            },
            onDismiss = { pendingDeleteGoal = null }
        )
    }
}
