package com.example.ui.traceability

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ActivityHistoryEntry
import com.example.model.ErrorFlagEntry
import com.example.ui.components.PersistentDisclaimerBanner
import com.example.ui.theme.FinanceRed
import com.example.ui.theme.MintPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityHistoryScreen(
    activityHistory: List<ActivityHistoryEntry>,
    errorFlags: List<ErrorFlagEntry>,
    onExportCsv: () -> Unit,
    onExportText: () -> Unit,
    onGetPreviewCsv: () -> String,
    onGetPreviewText: () -> String,
    onClearFlag: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var previewDialogContent by remember { mutableStateOf<Pair<String, String>?>(null) }
    val dateFmt = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Activity History & Flags",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Reviewed confirmations & flagged items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("activity_history_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            PersistentDisclaimerBanner(modifier = Modifier.padding(bottom = 10.dp))

            // Export buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onExportText,
                    modifier = Modifier.weight(1f).testTag("export_history_text_btn")
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export Text", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onExportCsv,
                    modifier = Modifier.weight(1f).testTag("export_history_csv_btn")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export CSV", fontSize = 12.sp)
                }
                TextButton(
                    onClick = { previewDialogContent = "Activity History Preview" to onGetPreviewText() },
                    modifier = Modifier.testTag("preview_history_btn")
                ) {
                    Text("View", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

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
                    text = { Text("Confirmed (${activityHistory.size})", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("tab_confirmed_activity")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Flagged (${errorFlags.size})", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("tab_flagged_errors")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                if (activityHistory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No reviewed confirmations logged yet. Every time you check 'I reviewed this' to confirm an action, allocation, or setting change, it appears here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(activityHistory, key = { it.id }) { entry ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(color = MintPrimary.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
                                            Text(
                                                text = entry.category.uppercase(),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                                color = MintPrimary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = dateFmt.format(Date(entry.timestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Change: ${entry.whatChanged}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (entry.whySummary.isNotBlank()) {
                                        Text(
                                            text = "Why: ${entry.whySummary}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (entry.numbersSummary.isNotBlank()) {
                                        Text(
                                            text = "Numbers: ${entry.numbersSummary}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MintPrimary
                                        )
                                    }
                                    if (!entry.ruleId.isNullOrBlank()) {
                                        Text(
                                            text = "Rule: ${entry.ruleId}",
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
            } else {
                if (errorFlags.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No flagged issues yet. Tap 'This looks wrong' on any Money Health Score component or Top 3 Action to save a note here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(errorFlags, key = { it.id }) { flag ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = FinanceRed.copy(alpha = 0.08f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = flag.itemType,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = FinanceRed
                                        )
                                        Text(
                                            text = dateFmt.format(Date(flag.timestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = flag.itemTitle,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Note: ${flag.note}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (flag.snapshotSummary.isNotBlank()) {
                                        Text(
                                            text = "Context: ${flag.snapshotSummary}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!flag.ruleId.isNullOrBlank()) {
                                        Text(
                                            text = "Rule: ${flag.ruleId}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MintPrimary
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { onClearFlag(flag.id) }) {
                                            Text("Dismiss Flag", color = FinanceRed, fontSize = 12.sp)
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
    }

    previewDialogContent?.let { (title, content) ->
        AlertDialog(
            onDismissRequest = { previewDialogContent = null },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            previewDialogContent = "CSV Preview" to onGetPreviewCsv()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Switch to CSV Preview")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { previewDialogContent = null }) { Text("Close") }
            }
        )
    }
}
