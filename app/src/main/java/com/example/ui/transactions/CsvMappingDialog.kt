package com.example.ui.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.TransactionImportEngine
import com.example.model.CsvColumnMapping
import com.example.model.CsvParsePreview
import com.example.ui.theme.FinanceRed
import com.example.ui.theme.MintPrimary

@Composable
fun CsvImportAndMappingDialog(
    initialPreview: CsvParsePreview?,
    isProcessing: Boolean,
    errorMessage: String?,
    onPickCsvFile: () -> Unit,
    onParseRawCsvText: (String) -> Unit,
    onConfirmMapping: (CsvParsePreview, CsvColumnMapping) -> Unit,
    onDismiss: () -> Unit
) {
    var pasteFallbackCsv by remember { mutableStateOf("") }
    var useSplitDebitCredit by remember(initialPreview) {
        mutableStateOf(
            initialPreview != null &&
                (initialPreview.suggestedMapping.debitCol != null || initialPreview.suggestedMapping.creditCol != null) &&
                initialPreview.suggestedMapping.amountCol == null
        )
    }
    var dateCol by remember(initialPreview) { mutableIntStateOf(initialPreview?.suggestedMapping?.dateCol ?: 0) }
    var descCol by remember(initialPreview) { mutableIntStateOf(initialPreview?.suggestedMapping?.descriptionCol ?: 0) }
    var amountCol by remember(initialPreview) { mutableIntStateOf(initialPreview?.suggestedMapping?.amountCol ?: 0) }
    var debitCol by remember(initialPreview) { mutableIntStateOf(initialPreview?.suggestedMapping?.debitCol ?: 0) }
    var creditCol by remember(initialPreview) { mutableIntStateOf(initialPreview?.suggestedMapping?.creditCol ?: 0) }
    var hasHeader by remember(initialPreview) { mutableStateOf(initialPreview?.suggestedMapping?.hasHeader ?: true) }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TableChart, contentDescription = null, tint = MintPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (initialPreview == null) "Import CSV Statement" else "Map CSV Columns",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (initialPreview == null) {
                    Text(
                        text = "Choose a CSV bank or card statement file from your device. It is parsed locally in code before review.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onPickCsvFile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("pick_csv_file_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose CSV File", fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Or paste CSV rows directly:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = pasteFallbackCsv,
                        onValueChange = { pasteFallbackCsv = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 110.dp, max = 160.dp)
                            .testTag("paste_csv_input"),
                        placeholder = {
                            Text("Date,Description,Amount\n2026-02-10,Trader Joe's,-54.20\n2026-02-15,Payroll Deposit,1800.00")
                        }
                    )
                    if (pasteFallbackCsv.isNotBlank()) {
                        OutlinedButton(
                            onClick = { onParseRawCsvText(pasteFallbackCsv) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("parse_pasted_csv_btn")
                        ) {
                            Text("Load Pasted CSV")
                        }
                    }
                } else {
                    val headers = if (hasHeader && initialPreview.allRows.isNotEmpty()) {
                        initialPreview.allRows.first().mapIndexed { idx, h -> h.ifBlank { "Column ${idx + 1}" } }
                    } else {
                        initialPreview.headers.indices.map { idx -> "Column ${idx + 1}" }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("First row is headers", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = hasHeader,
                            onCheckedChange = { hasHeader = it },
                            modifier = Modifier.testTag("csv_has_header_switch")
                        )
                    }

                    // Sample preview
                    val previewRows = if (hasHeader) initialPreview.allRows.drop(1).take(2) else initialPreview.allRows.take(2)
                    if (previewRows.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Sample row preview:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                previewRows.forEach { row ->
                                    Text(
                                        text = row.joinToString(" | "),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }

                    ColumnIndexDropdown("Date column", headers, dateCol) { dateCol = it }
                    ColumnIndexDropdown("Description / Merchant column", headers, descCol) { descCol = it }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = !useSplitDebitCredit,
                            onClick = { useSplitDebitCredit = false },
                            label = { Text("Single Amount") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = useSplitDebitCredit,
                            onClick = { useSplitDebitCredit = true },
                            label = { Text("Debit + Credit") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (!useSplitDebitCredit) {
                        ColumnIndexDropdown("Amount column", headers, amountCol) { amountCol = it }
                    } else {
                        ColumnIndexDropdown("Debit (Expense) column", headers, debitCol) { debitCol = it }
                        ColumnIndexDropdown("Credit (Income) column", headers, creditCol) { creditCol = it }
                    }
                }

                if (isProcessing) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MintPrimary)
                        Text("Categorizing descriptions...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = FinanceRed)
                }
            }
        },
        confirmButton = {
            if (initialPreview != null) {
                Button(
                    onClick = {
                        val safeSize = maxOf(1, initialPreview.headers.size)
                        val mapping = if (useSplitDebitCredit) {
                            CsvColumnMapping(
                                dateCol = dateCol.coerceIn(0, safeSize - 1),
                                descriptionCol = descCol.coerceIn(0, safeSize - 1),
                                amountCol = null,
                                debitCol = debitCol.coerceIn(0, safeSize - 1),
                                creditCol = creditCol.coerceIn(0, safeSize - 1),
                                hasHeader = hasHeader
                            )
                        } else {
                            CsvColumnMapping(
                                dateCol = dateCol.coerceIn(0, safeSize - 1),
                                descriptionCol = descCol.coerceIn(0, safeSize - 1),
                                amountCol = amountCol.coerceIn(0, safeSize - 1),
                                debitCol = null,
                                creditCol = null,
                                hasHeader = hasHeader
                            )
                        }
                        onConfirmMapping(initialPreview, mapping)
                    },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("csv_mapping_continue_btn")
                ) {
                    Text("Continue to Review", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnIndexDropdown(
    label: String,
    headers: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val safeIdx = selectedIndex.coerceIn(0, maxOf(0, headers.lastIndex))
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = headers.getOrElse(safeIdx) { "Column 1" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            headers.forEachIndexed { idx, name ->
                DropdownMenuItem(
                    text = { Text("${idx + 1}. $name") },
                    onClick = {
                        onSelect(idx)
                        expanded = false
                    }
                )
            }
        }
    }
}
