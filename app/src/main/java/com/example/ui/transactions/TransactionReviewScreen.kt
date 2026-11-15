package com.example.ui.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TransactionImportEngine
import com.example.model.*
import com.example.ui.theme.FinanceGreen
import com.example.ui.theme.FinanceRed
import com.example.ui.theme.MintAccentGold
import com.example.ui.theme.MintPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionReviewScreen(
    batchResult: ExtractionBatchResult,
    currencyCode: String,
    existingTransactions: List<LedgerTransaction>,
    onConfirmSave: (List<ExtractedTransactionCandidate>, String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onCancel)

    var itemsState by remember(batchResult) { mutableStateOf(batchResult.candidates) }
    var datePreference by remember(batchResult) { mutableStateOf(DateFormatPreference.AUTO) }
    var dateConfirmed by remember(batchResult) { mutableStateOf(!batchResult.hasAmbiguousDates) }

    val selectedItems = itemsState.filter { it.isSelected && it.amount > 0.0 }
    val allSelected = itemsState.isNotEmpty() && itemsState.all { it.isSelected }
    val duplicateCount = itemsState.count { it.isDuplicate }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Review Before Saving",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Edit any field or uncheck rows. Nothing is saved yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("review_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(0.7f)
                            .heightIn(min = 48.dp)
                            .testTag("review_cancel_btn")
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onConfirmSave(itemsState, batchResult.sourceType) },
                        enabled = selectedItems.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = MintPrimary),
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 48.dp)
                            .testTag("review_save_selected_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save selected (${selectedItems.size})", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary counts card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("review_summary_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Found ${batchResult.foundCount} • Skipped ${batchResult.skippedCount} malformed • $duplicateCount potential duplicate(s)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (duplicateCount > 0) {
                            Text(
                                text = "Potential duplicates are unchecked by default so you won't double-count them.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MintAccentGold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = allSelected,
                                    onCheckedChange = { checked ->
                                        itemsState = itemsState.map { it.copy(isSelected = checked) }
                                    },
                                    modifier = Modifier.testTag("review_select_all_checkbox")
                                )
                                Text(
                                    text = "Select all (${itemsState.size})",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                            val selIncome = selectedItems.filter { it.type == "INCOME" }.sumOf { it.amount }
                            val selExpense = selectedItems.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                            Text(
                                text = "+${CurrenciesAndCountries.format(selIncome, currencyCode)} / -${CurrenciesAndCountries.format(selExpense, currencyCode)}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Ambiguous date format confirmation prompt
            if (batchResult.hasAmbiguousDates) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ambiguous_date_prompt_card"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MintAccentGold.copy(alpha = 0.16f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MintAccentGold, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (dateConfirmed) "Date Format Confirmed (${datePreference.label})" else "Confirm Ambiguous Dates (DD/MM vs MM/DD)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Text(
                                text = "Some dates like 04/05/2026 could be Day/Month or Month/Day. Pick which format your statement uses:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                FilterChip(
                                    selected = datePreference == DateFormatPreference.DMY || (datePreference == DateFormatPreference.AUTO && !dateConfirmed),
                                    onClick = {
                                        datePreference = DateFormatPreference.DMY
                                        dateConfirmed = true
                                        itemsState = TransactionImportEngine.reformatCandidatesForDatePreference(
                                            itemsState, DateFormatPreference.DMY, existingTransactions
                                        )
                                    },
                                    label = { Text("DD/MM (Day first)") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("date_format_dmy_btn")
                                )
                                FilterChip(
                                    selected = datePreference == DateFormatPreference.MDY,
                                    onClick = {
                                        datePreference = DateFormatPreference.MDY
                                        dateConfirmed = true
                                        itemsState = TransactionImportEngine.reformatCandidatesForDatePreference(
                                            itemsState, DateFormatPreference.MDY, existingTransactions
                                        )
                                    },
                                    label = { Text("MM/DD (Month first)") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("date_format_mdy_btn")
                                )
                            }
                        }
                    }
                }
            }

            itemsIndexed(itemsState, key = { _, item -> item.id }) { index, candidate ->
                ReviewCandidateCard(
                    index = index,
                    candidate = candidate,
                    currencyCode = currencyCode,
                    onUpdate = { updated ->
                        itemsState = itemsState.toMutableList().apply { set(index, updated) }
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewCandidateCard(
    index: Int,
    candidate: ExtractedTransactionCandidate,
    currencyCode: String,
    onUpdate: (ExtractedTransactionCandidate) -> Unit
) {
    var amountText by remember(candidate.id, candidate.amount) { mutableStateOf(candidate.amount.toString()) }
    var categoryExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("review_candidate_card_$index"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                candidate.isDuplicate -> MintAccentGold.copy(alpha = 0.12f)
                candidate.isSelected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Checkbox(
                        checked = candidate.isSelected,
                        onCheckedChange = { checked -> onUpdate(candidate.copy(isSelected = checked)) },
                        modifier = Modifier.testTag("review_row_checkbox_$index")
                    )
                    Column {
                        if (candidate.isDuplicate) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = "Duplicate", tint = MintAccentGold, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Possible duplicate",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MintAccentGold
                                )
                            }
                        }
                        if (candidate.ruleMatched) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = "Rule applied", tint = MintPrimary, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Smart rule applied",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MintPrimary
                                )
                            }
                        }
                    }
                }

                FilterChip(
                    selected = candidate.type == "INCOME",
                    onClick = {
                        val nextType = if (candidate.type == "INCOME") "EXPENSE" else "INCOME"
                        onUpdate(candidate.copy(type = nextType))
                    },
                    label = {
                        Text(
                            text = if (candidate.type == "INCOME") "Income" else "Expense",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (candidate.type == "INCOME") FinanceGreen else FinanceRed
                        )
                    }
                )
            }

            OutlinedTextField(
                value = candidate.merchant,
                onValueChange = { onUpdate(candidate.copy(merchant = it, notes = it)) },
                label = { Text("Merchant / Description") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { next ->
                        amountText = next
                        val parsed = next.toDoubleOrNull()
                        if (parsed != null && parsed >= 0.0) {
                            onUpdate(candidate.copy(amount = parsed))
                        }
                    },
                    label = { Text("Amount ($currencyCode)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = candidate.date,
                    onValueChange = { onUpdate(candidate.copy(date = it)) },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = !categoryExpanded }
            ) {
                OutlinedTextField(
                    value = candidate.category,
                    onValueChange = { onUpdate(candidate.copy(category = it)) },
                    label = { Text("Category (edits learn rule for merchant)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryEditable)
                        .testTag("review_category_input_$index")
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    DefaultTransactionCategories.ALL.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                onUpdate(candidate.copy(category = cat))
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
