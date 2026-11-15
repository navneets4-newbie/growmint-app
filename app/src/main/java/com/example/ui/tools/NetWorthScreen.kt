package com.example.ui.tools

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FinancialCalculator
import com.example.model.*
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.FormattedAmountDisplay
import com.example.ui.components.PersistentDisclaimerBanner
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetWorthScreen(
    profile: UserProfile,
    assets: List<AssetItem>,
    snapshots: List<NetWorthSnapshot>,
    bankConnection: BankConnectionState,
    onAddAsset: (AssetItem) -> Unit,
    onDeleteAsset: (String) -> Unit,
    onSaveMonthlySnapshot: (NetWorthSnapshot) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddAssetDialog by remember { mutableStateOf(false) }
    var pendingAddAsset by remember { mutableStateOf<AssetItem?>(null) }
    var pendingDeleteAsset by remember { mutableStateOf<AssetItem?>(null) }
    var showConfirmSaveSnapshot by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val summary = remember(assets, bankConnection, profile.debts, profile.currentBalance) {
        FinancialCalculator.calculateNetWorthSummary(
            manualAssets = assets,
            linkedBankAccounts = bankConnection.accounts,
            isBankLinked = bankConnection.isLinked,
            debts = profile.debts,
            ledgerCashBalance = profile.currentBalance
        )
    }

    val currentMonthKey = remember { SimpleDateFormat("yyyy-MM", Locale.US).format(Date()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Net Worth & Trend",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Assets + linked balances minus debts over time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("net_worth_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showAddAssetDialog = true },
                        modifier = Modifier.testTag("net_worth_add_asset_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Asset")
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

            // Hero Net Worth Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("net_worth_hero_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MintPrimary.copy(alpha = 0.14f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "ESTIMATED NET WORTH",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                            color = MintPrimary
                        )
                        FormattedAmountDisplay(
                            amount = summary.netWorth,
                            currencyCode = profile.currencyCode,
                            secondaryCurrency = profile.secondaryCurrency,
                            manualExchangeRate = profile.manualExchangeRate,
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (summary.netWorth >= 0) MaterialTheme.colorScheme.onSurface else FinanceRed
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Total Assets", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = CurrenciesAndCountries.format(summary.totalAssets, profile.currencyCode),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = FinanceGreen
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Total Debts (Planner)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = CurrenciesAndCountries.format(summary.totalDebts, profile.currencyCode),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = FinanceRed
                                )
                            }
                        }

                        Button(
                            onClick = { showConfirmSaveSnapshot = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_net_worth_snapshot_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                        ) {
                            Icon(Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save $currentMonthKey Snapshot", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Monthly Trend Line Chart
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("net_worth_trend_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Monthly Net Worth Trend",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (snapshots.isEmpty()) {
                            Text(
                                text = "Save a monthly snapshot above to start plotting your net worth trend line over time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val sorted = snapshots.sortedBy { it.monthKey }
                            val minVal = sorted.minOf { it.netWorth }
                            val maxVal = sorted.maxOf { it.netWorth }
                            val range = maxOf(1.0, maxVal - minVal)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Low: ${CurrenciesAndCountries.format(minVal, profile.currencyCode)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "High: ${CurrenciesAndCountries.format(maxVal, profile.currencyCode)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MintPrimary
                                )
                            }

                            val lineColor = MintPrimary
                            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .padding(vertical = 8.dp)
                            ) {
                                // Horizontal baseline grid
                                drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
                                drawLine(gridColor, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 1f)

                                val points = sorted.mapIndexed { idx, snap ->
                                    val x = if (sorted.size == 1) size.width / 2f
                                    else (idx.toFloat() / (sorted.size - 1)) * (size.width - 24f) + 12f
                                    val norm = ((snap.netWorth - minVal) / range).toFloat()
                                    val y = size.height - 14f - norm * (size.height - 28f)
                                    Offset(x, y)
                                }

                                if (points.size >= 2) {
                                    val path = Path().apply {
                                        moveTo(points.first().x, points.first().y)
                                        for (i in 1 until points.size) {
                                            lineTo(points[i].x, points[i].y)
                                        }
                                    }
                                    drawPath(path, color = lineColor, style = Stroke(width = 6f, cap = StrokeCap.Round))
                                }

                                points.forEach { pt ->
                                    drawCircle(color = Color.White, radius = 10f, center = pt)
                                    drawCircle(color = lineColor, radius = 7f, center = pt)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(sorted.first().monthKey, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (sorted.size > 1) {
                                    Text(sorted.last().monthKey, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Asset Category Breakdown
            item {
                Text(
                    text = "Assets Breakdown",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssetCategory.values().forEach { cat ->
                            val catAmt = summary.categoryTotals[cat] ?: 0.0
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(cat.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = CurrenciesAndCountries.format(catAmt, profile.currencyCode),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                        if (summary.ledgerCashBalanceTotal > 0.0) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Tracked Cash Balance (Ledger)", style = MaterialTheme.typography.bodyMedium, color = MintPrimary)
                                Text(
                                    text = CurrenciesAndCountries.format(summary.ledgerCashBalanceTotal, profile.currencyCode),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MintPrimary
                                )
                            }
                        }
                        if (bankConnection.isLinked) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Linked Bank Balances", style = MaterialTheme.typography.bodyMedium, color = MintPrimary)
                                Text(
                                    text = CurrenciesAndCountries.format(summary.linkedBankAssetsTotal, profile.currencyCode),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MintPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Manual Asset Entries
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Manual Asset Entries (${assets.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(onClick = { showAddAssetDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Asset")
                    }
                }
            }

            if (assets.isEmpty()) {
                item {
                    Text(
                        text = "No manual assets added yet. Add Cash, Savings, Investments, Property, or Other items to calculate your net worth.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(assets) { asset ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(asset.name, fontWeight = FontWeight.Bold)
                                Text(
                                    text = asset.category.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MintPrimary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = CurrenciesAndCountries.format(asset.amount, profile.currencyCode),
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { pendingDeleteAsset = asset }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete asset", tint = FinanceRed)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showAddAssetDialog) {
        var assetName by remember { mutableStateOf("") }
        var assetAmountText by remember { mutableStateOf("") }
        var selectedCategory by remember { mutableStateOf(AssetCategory.SAVINGS) }

        AlertDialog(
            onDismissRequest = { showAddAssetDialog = false },
            title = { Text("Add Manual Asset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Category", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    AssetCategory.values().toList().chunked(3).forEach { chunk ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            chunk.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat.label, fontSize = 11.sp, maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (chunk.size < 3) {
                                repeat(3 - chunk.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = assetName,
                        onValueChange = { assetName = it },
                        label = { Text("Asset Name (e.g. Emergency Savings, Home)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = assetAmountText,
                        onValueChange = { assetAmountText = it },
                        label = { Text("Current Value (${profile.currencyCode})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = assetAmountText.toDoubleOrNull() ?: 0.0
                        if (assetName.isNotBlank() && amt >= 0.0) {
                            showAddAssetDialog = false
                            pendingAddAsset = AssetItem(
                                name = assetName.trim(),
                                category = selectedCategory,
                                amount = amt
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                ) { Text("Review") }
            },
            dismissButton = {
                TextButton(onClick = { showAddAssetDialog = false }) { Text("Cancel") }
            }
        )
    }

    pendingAddAsset?.let { asset ->
        ConfirmActionDialog(
            title = "Add Asset?",
            summaryLines = listOf(
                "Name: ${asset.name}",
                "Category: ${asset.category.label}",
                "Value: ${CurrenciesAndCountries.format(asset.amount, profile.currencyCode)}"
            ),
            confirmLabel = "Add Asset",
            onConfirm = {
                onAddAsset(asset)
                snackbarMessage = "Added ${asset.name} (${asset.category.label})."
                pendingAddAsset = null
            },
            onDismiss = { pendingAddAsset = null }
        )
    }

    pendingDeleteAsset?.let { asset ->
        ConfirmActionDialog(
            title = "Delete '${asset.name}'?",
            summaryLines = listOf("Removes ${CurrenciesAndCountries.format(asset.amount, profile.currencyCode)} from manual assets."),
            confirmLabel = "Delete",
            isDestructive = true,
            onConfirm = {
                onDeleteAsset(asset.id)
                snackbarMessage = "Deleted ${asset.name}."
                pendingDeleteAsset = null
            },
            onDismiss = { pendingDeleteAsset = null }
        )
    }

    if (showConfirmSaveSnapshot) {
        ConfirmActionDialog(
            title = "Save Net Worth Snapshot?",
            summaryLines = listOf(
                "Month: $currentMonthKey",
                "Assets: ${CurrenciesAndCountries.format(summary.totalAssets, profile.currencyCode)}",
                "Debts: ${CurrenciesAndCountries.format(summary.totalDebts, profile.currencyCode)}",
                "Net Worth: ${CurrenciesAndCountries.format(summary.netWorth, profile.currencyCode)}"
            ),
            confirmLabel = "Save Snapshot",
            onConfirm = {
                onSaveMonthlySnapshot(
                    NetWorthSnapshot(
                        monthKey = currentMonthKey,
                        totalAssets = summary.totalAssets,
                        totalDebts = summary.totalDebts,
                        netWorth = summary.netWorth
                    )
                )
                snackbarMessage = "Saved net worth snapshot for $currentMonthKey!"
                showConfirmSaveSnapshot = false
            },
            onDismiss = { showConfirmSaveSnapshot = false }
        )
    }
}
