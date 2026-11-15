package com.example.ui.trading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import com.example.model.BufferGateStatus
import com.example.model.CurrenciesAndCountries
import com.example.model.PaperAsset
import com.example.model.PaperHolding
import com.example.model.UserProfile
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.FormattedAmountDisplay
import com.example.ui.components.PersistentDisclaimerBanner
import com.example.ui.components.SafetyBufferGateCard
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperTradingScreen(
    profile: UserProfile,
    paperCash: Double,
    holdings: List<PaperHolding>,
    assets: List<PaperAsset>,
    bufferStatus: BufferGateStatus = FinancialCalculator.calculateBufferGateStatus(profile, emptyList()),
    onExecuteTrade: (String, Double, Boolean) -> Boolean,
    modifier: Modifier = Modifier
) {
    var selectedAssetForTrade by remember { mutableStateOf<PaperAsset?>(null) }
    var isBuyAction by remember { mutableStateOf(true) }
    var tradeSharesText by remember { mutableStateOf("1.0") }
    var pendingTradeConfirm by remember { mutableStateOf<Triple<PaperAsset, Double, Boolean>?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val totalHoldingsValue = holdings.sumOf { h ->
        val asset = assets.find { it.symbol == h.symbol }
        val price = asset?.price ?: h.avgCost
        h.shares * price
    }
    val totalPortfolioValue = paperCash + totalHoldingsValue

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Practice Trading Game",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Learn how markets work with play money — zero risk",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                ) {
                    Text(msg)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                PersistentDisclaimerBanner()
            }

            if (profile.isUnder18 || !bufferStatus.isBufferMet) {
                item {
                    SafetyBufferGateCard(
                        bufferStatus = bufferStatus,
                        currencyCode = profile.currencyCode
                    )
                }
            }

            // Blunt Risk Disclosure (Requirement D)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trading_risk_card"),
                    colors = CardDefaults.cardColors(containerColor = FinanceRed.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = FinanceRed,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Honest Truth: Active Trading vs. Long-Term Investing",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = FinanceRed
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Studies show that 70% to 90%+ of day traders lose money because jumping in and out of the market is tricky. Patient long-term investing in broad funds has historically been much more dependable. This game gives you play money to test things out safely!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Portfolio Value Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "YOUR PRACTICE PORTFOLIO (PLAY MONEY)",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FormattedAmountDisplay(
                            amount = totalPortfolioValue,
                            currencyCode = profile.currencyCode,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = "Play Money Ready to Use", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FormattedAmountDisplay(
                                    amount = paperCash,
                                    currencyCode = profile.currencyCode,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "Shares You Own", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FormattedAmountDisplay(
                                    amount = totalHoldingsValue,
                                    currencyCode = profile.currencyCode,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            // Current Holdings Section
            item {
                Text(
                    text = "My Simulated Holdings (${holdings.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (holdings.isEmpty()) {
                item {
                    Text(
                        text = "You do not own any virtual shares yet. Browse the educational assets below to execute a simulated order.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(holdings) { h ->
                    val asset = assets.find { it.symbol == h.symbol }
                    val currentPrice = asset?.price ?: h.avgCost
                    val totalVal = h.shares * currentPrice
                    val pnl = (currentPrice - h.avgCost) * h.shares
                    val pnlPercent = if (h.avgCost > 0) ((currentPrice - h.avgCost) / h.avgCost) * 100.0 else 0.0

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "${h.symbol} (${String.format("%.2f", h.shares)} shs)", fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Avg: ${CurrenciesAndCountries.format(h.avgCost, profile.currencyCode)} • Now: ${CurrenciesAndCountries.format(currentPrice, profile.currencyCode)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                FormattedAmountDisplay(
                                    amount = totalVal,
                                    currencyCode = profile.currencyCode,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${if (pnl >= 0) "+" else ""}${String.format("%.1f", pnlPercent)}% (${CurrenciesAndCountries.format(pnl, profile.currencyCode)})",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (pnl >= 0) FinanceGreen else FinanceRed
                                )
                            }
                        }
                    }
                }
            }

            // Available Assets Market
            item {
                Text(
                    text = "Educational Asset Catalog",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(assets) { a ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = a.symbol, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = a.assetType,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = a.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                FormattedAmountDisplay(
                                    amount = a.price,
                                    currencyCode = profile.currencyCode,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${if (a.dayChangePercent >= 0) "+" else ""}${a.dayChangePercent}%",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (a.dayChangePercent >= 0) FinanceGreen else FinanceRed
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = a.description,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    selectedAssetForTrade = a
                                    isBuyAction = true
                                    tradeSharesText = "1.0"
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                            ) {
                                Text("Paper Buy")
                            }

                            OutlinedButton(
                                onClick = {
                                    selectedAssetForTrade = a
                                    isBuyAction = false
                                    val holding = holdings.find { it.symbol == a.symbol }
                                    tradeSharesText = if (holding != null) holding.shares.toString() else "1.0"
                                },
                                enabled = holdings.any { it.symbol == a.symbol },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Paper Sell")
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Trade Dialog
    selectedAssetForTrade?.let { asset ->
        val shares = tradeSharesText.toDoubleOrNull() ?: 0.0
        val totalCost = shares * asset.price

        AlertDialog(
            onDismissRequest = { selectedAssetForTrade = null },
            title = {
                Text("${if (isBuyAction) "Buy" else "Sell"} ${asset.symbol}")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Simulated Order: ${asset.name} at ${CurrenciesAndCountries.format(asset.price, profile.currencyCode)}/share.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = tradeSharesText,
                        onValueChange = { tradeSharesText = it },
                        label = { Text("Number of Shares") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Total Value:", fontWeight = FontWeight.Bold)
                        FormattedAmountDisplay(
                            amount = totalCost,
                            currencyCode = profile.currencyCode,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    if (isBuyAction && totalCost > paperCash) {
                        Text(
                            text = "Insufficient virtual cash (${CurrenciesAndCountries.format(paperCash, profile.currencyCode)} available).",
                            color = FinanceRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (shares > 0) {
                            pendingTradeConfirm = Triple(asset, shares, isBuyAction)
                            selectedAssetForTrade = null
                        }
                    },
                    enabled = shares > 0 && (!isBuyAction || totalCost <= paperCash)
                ) {
                    Text("Review Order")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedAssetForTrade = null }) { Text("Cancel") }
            }
        )
    }

    pendingTradeConfirm?.let { (asset, shares, isBuy) ->
        val totalCost = shares * asset.price
        ConfirmActionDialog(
            title = "Confirm Practice ${if (isBuy) "Buy" else "Sell"}?",
            summaryLines = listOf(
                "Asset: ${asset.symbol} (${asset.name})",
                "Shares: $shares @ ${CurrenciesAndCountries.format(asset.price, profile.currencyCode)}",
                "Virtual Total: ${CurrenciesAndCountries.format(totalCost, profile.currencyCode)}",
                "Uses 100% play money only."
            ),
            confirmLabel = if (isBuy) "Paper Buy" else "Paper Sell",
            onConfirm = {
                val success = onExecuteTrade(asset.symbol, shares, isBuy)
                snackbarMessage = if (success) {
                    "Executed paper trade: ${if (isBuy) "Bought" else "Sold"} $shares ${asset.symbol}!"
                } else {
                    "Trade failed: verify cash or holdings balance."
                }
                pendingTradeConfirm = null
            },
            onDismiss = { pendingTradeConfirm = null }
        )
    }
}
