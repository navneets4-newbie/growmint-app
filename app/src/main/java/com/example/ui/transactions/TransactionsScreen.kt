package com.example.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
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
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.PersistentDisclaimerBanner
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    profile: UserProfile,
    transactions: List<LedgerTransaction>,
    merchantRules: List<MerchantCategoryRule>,
    favoriteCategories: Set<String>,
    recentCategories: List<String>,
    lastImportBatch: ImportBatchRecord?,
    pendingIncomeForSplit: Pair<Double, String>?,
    mostRecentSimilar: (String) -> LedgerTransaction?,
    onQuickAdd: (amount: Double, isExpense: Boolean, category: String, note: String, learnRuleIfCorrected: Boolean, suggestedCategory: String?) -> Unit,
    onToggleFavoriteCategory: (String) -> Unit,
    onOpenPasteImport: () -> Unit,
    onOpenCsvImport: () -> Unit,
    onUndoLastImport: () -> Boolean,
    onDeleteTransaction: (String) -> Unit,
    onLaunchSplitForIncome: (Double, String) -> Unit,
    onDismissIncomePrompt: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var quickAmountText by remember { mutableStateOf("") }
    var quickNoteText by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf(recentCategories.firstOrNull() ?: "Groceries") }
    var userManuallyPickedCategory by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var pendingIncomeSplitConfirm by remember { mutableStateOf<Pair<Double, String>?>(null) }

    // Smart rule auto-suggestion as user types merchant/note
    val matchedRule = remember(quickNoteText, merchantRules) {
        TransactionImportEngine.findMatchingCategoryRule(quickNoteText, merchantRules)
    }
    val suggestedCategory = remember(matchedRule, isExpense, recentCategories) {
        matchedRule?.category ?: if (!isExpense) "Income & Salary" else (recentCategories.firstOrNull() ?: "Groceries")
    }

    LaunchedEffect(matchedRule?.category) {
        if (matchedRule != null && !userManuallyPickedCategory) {
            selectedCategory = matchedRule.category
        }
    }

    // Pinned categories order: Favorites first, then Recents, then Defaults
    val pinnedCategories = remember(favoriteCategories, recentCategories, isExpense) {
        val ordered = linkedSetOf<String>()
        favoriteCategories.sorted().forEach { ordered.add(it) }
        recentCategories.forEach { ordered.add(it) }
        DefaultTransactionCategories.ALL.forEach { ordered.add(it) }
        if (!isExpense) {
            listOf("Income & Salary", "Side Hustle") + ordered.filterNot { it == "Income & Salary" || it == "Side Hustle" }
        } else {
            ordered.toList()
        }
    }

    val lastSimilarTx = remember(quickNoteText, transactions) {
        mostRecentSimilar(quickNoteText)
    }

    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Transactions",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Quick-add, paste SMS/UPI, or import CSV statements",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("transactions_back_btn")
                    ) {
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
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { PersistentDisclaimerBanner() }

            // === INCOME DETECTED PROMPT (#7) ===
            if (pendingIncomeForSplit != null && pendingIncomeForSplit.first > 0.0) {
                val (incomeAmt, incomeSrc) = pendingIncomeForSplit
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("income_detected_split_banner"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MintPrimary.copy(alpha = 0.16f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null, tint = MintPrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Income received, ready to split it?",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MintPrimary
                                        )
                                        Text(
                                            text = "${CurrenciesAndCountries.format(incomeAmt, profile.currencyCode)} from $incomeSrc",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                IconButton(onClick = onDismissIncomePrompt) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss income prompt")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { pendingIncomeSplitConfirm = incomeAmt to incomeSrc },
                                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("income_detected_split_btn")
                                ) {
                                    Text("Split Income", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = onDismissIncomePrompt,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Later")
                                }
                            }
                        }
                    }
                }
            }

            // === UNDO LAST IMPORT BANNER (#6) ===
            if (lastImportBatch != null && lastImportBatch.transactionIds.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("undo_import_banner"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MintAccentGold.copy(alpha = 0.16f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Saved ${lastImportBatch.transactionIds.size} imported transaction(s)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "From ${lastImportBatch.sourceType} import • Tap to remove this exact batch",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = {
                                    val undone = onUndoLastImport()
                                    if (undone) snackbarMessage = "Undid import batch and restored balance."
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("undo_import_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Undo this import", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // === IMPORT ACTION BUTTONS (#1 & #2) ===
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onOpenPasteImport,
                        colors = ButtonDefaults.buttonColors(containerColor = MintPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("transactions_paste_btn")
                    ) {
                        Icon(Icons.Default.ContentPasteGo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste transactions", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = onOpenCsvImport,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("transactions_csv_btn")
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import CSV", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            // === QUICK-ADD FAST ENTRY BAR (#4) ===
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_add_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Quick Add (< 3 taps)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = isExpense,
                                    onClick = { isExpense = true },
                                    label = { Text("Expense", fontSize = 12.sp) },
                                    modifier = Modifier.testTag("quick_add_type_expense")
                                )
                                FilterChip(
                                    selected = !isExpense,
                                    onClick = {
                                        isExpense = false
                                        if (!userManuallyPickedCategory) selectedCategory = "Income & Salary"
                                    },
                                    label = { Text("Income", fontSize = 12.sp) },
                                    modifier = Modifier.testTag("quick_add_type_income")
                                )
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = quickAmountText,
                                onValueChange = { quickAmountText = it },
                                label = { Text("Amount (${profile.currencyCode})") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_add_amount_input")
                            )
                            OutlinedTextField(
                                value = quickNoteText,
                                onValueChange = { quickNoteText = it },
                                label = { Text("Merchant / note (optional)") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("quick_add_note_input")
                            )
                        }

                        // One-tap Suggested Category banner
                        if (suggestedCategory.isNotBlank() && selectedCategory != suggestedCategory) {
                            AssistChip(
                                onClick = {
                                    selectedCategory = suggestedCategory
                                    userManuallyPickedCategory = false
                                },
                                label = {
                                    Text(
                                        text = if (matchedRule != null) "Rule suggestion: $suggestedCategory (Tap to use)"
                                        else "Suggested: $suggestedCategory (Tap to use)"
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp), tint = MintPrimary)
                                },
                                modifier = Modifier.testTag("quick_add_suggested_chip")
                            )
                        }

                        // Pinned Favorites & Recent Categories Row
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Favorites & Recent (tap star to pin)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                pinnedCategories.forEach { cat ->
                                    val isSel = selectedCategory.equals(cat, ignoreCase = true)
                                    val isFav = favoriteCategories.contains(cat)
                                    FilterChip(
                                        selected = isSel,
                                        onClick = {
                                            selectedCategory = cat
                                            userManuallyPickedCategory = true
                                        },
                                        label = { Text(cat, fontSize = 12.sp) },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = if (isFav) "Unpin $cat" else "Pin $cat",
                                                tint = if (isFav) MintAccentGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clickable { onToggleFavoriteCategory(cat) }
                                            )
                                        },
                                        modifier = Modifier.testTag("quick_cat_chip_${cat.lowercase().replace(" ", "_")}")
                                    )
                                }
                            }
                        }

                        // Primary Quick-Add Save + Same as Last Time
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val amt = TransactionImportEngine.parseSignedAmount(quickAmountText)?.let { kotlin.math.abs(it) } ?: 0.0
                                    if (amt > 0.0) {
                                        onQuickAdd(
                                            amt,
                                            isExpense,
                                            selectedCategory,
                                            quickNoteText,
                                            userManuallyPickedCategory,
                                            suggestedCategory
                                        )
                                        snackbarMessage = "Added ${if (isExpense) "-" else "+"}${CurrenciesAndCountries.format(amt, profile.currencyCode)} ($selectedCategory)"
                                        quickAmountText = ""
                                        quickNoteText = ""
                                        userManuallyPickedCategory = false
                                    }
                                },
                                enabled = (TransactionImportEngine.parseSignedAmount(quickAmountText) ?: 0.0) > 0.0,
                                colors = ButtonDefaults.buttonColors(containerColor = MintPrimary),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("quick_add_save_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add ${selectedCategory.substringBefore(" ")}", fontWeight = FontWeight.Bold)
                            }

                            if (lastSimilarTx != null) {
                                val labelName = lastSimilarTx.merchant.ifBlank { lastSimilarTx.notes }.ifBlank { lastSimilarTx.category }
                                OutlinedButton(
                                    onClick = {
                                        val isExp = !lastSimilarTx.type.equals("INCOME", ignoreCase = true)
                                        onQuickAdd(
                                            lastSimilarTx.amount,
                                            isExp,
                                            lastSimilarTx.category,
                                            lastSimilarTx.merchant.ifBlank { lastSimilarTx.notes },
                                            false,
                                            lastSimilarTx.category
                                        )
                                        snackbarMessage = "Repeated $labelName (${CurrenciesAndCountries.format(lastSimilarTx.amount, profile.currencyCode)})"
                                    },
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("quick_add_same_as_last_btn")
                                ) {
                                    Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Same as last time", fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            // === LEDGER HISTORY LIST ===
            item {
                Text(
                    text = "History (${transactions.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (transactions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MintPrimary, modifier = Modifier.size(32.dp))
                            Text("No transactions recorded yet", fontWeight = FontWeight.Bold)
                            Text(
                                text = "Use Quick Add above, paste SMS/UPI messages, or import a CSV statement to get started.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(transactions, key = { it.id }) { tx ->
                    val isInc = tx.type.equals("INCOME", ignoreCase = true)
                    val title = tx.merchant.ifBlank { tx.notes }.ifBlank { tx.category }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transaction_history_card_${tx.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${tx.category} • ${dateFormatter.format(Date(tx.timestamp))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${if (isInc) "+" else "-"}${CurrenciesAndCountries.format(tx.amount, profile.currencyCode)}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isInc) FinanceGreen else FinanceRed
                                )
                                IconButton(
                                    onClick = { onDeleteTransaction(tx.id) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete transaction",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    pendingIncomeSplitConfirm?.let { (amt, src) ->
        ConfirmActionDialog(
            title = "Open Paycheck Auto-Divider?",
            summaryLines = listOf(
                "Ready to split ${CurrenciesAndCountries.format(amt, profile.currencyCode)} ($src) using your Auto-Split rules.",
                "Virtual envelopes are updated only when you confirm the split."
            ),
            confirmLabel = "Open Auto-Split",
            onConfirm = {
                pendingIncomeSplitConfirm = null
                onDismissIncomePrompt()
                onLaunchSplitForIncome(amt, src)
            },
            onDismiss = { pendingIncomeSplitConfirm = null }
        )
    }
}
