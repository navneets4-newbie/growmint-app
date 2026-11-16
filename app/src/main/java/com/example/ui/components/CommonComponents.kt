package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.ui.text.style.TextOverflow
import com.example.model.CurrenciesAndCountries
import com.example.model.DataQualityNote
import com.example.ui.theme.*

val LocalActivityLogger = staticCompositionLocalOf<((category: String, title: String, whatChanged: String, whySummary: String, numbersSummary: String, ruleId: String?) -> Unit)?> {
    null
}

@Composable
fun PersistentDisclaimerBanner(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("persistent_disclaimer_banner"),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Educational Disclaimer",
                tint = MintAccentGold,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Virtual ledger & educational coach only. GrowMint never moves real money.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun VirtualLedgerNoticeCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("virtual_ledger_notice_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        ),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MintPrimary.copy(alpha = 0.4f))
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MintPrimary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Virtual Ledger Security",
                    tint = MintPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "100% Private Virtual Ledger & Coach",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "GrowMint works offline-first with your manual entries and pasted transactions. We NEVER ask for bank passwords, OTPs, or PINs, and we NEVER move your real money.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun FormattedAmountDisplay(
    amount: Double,
    currencyCode: String,
    secondaryCurrency: String? = null,
    manualExchangeRate: Double? = null,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleLarge,
    color: Color = MaterialTheme.colorScheme.onBackground
) {
    val primaryFormatted = CurrenciesAndCountries.format(amount, currencyCode)

    Column {
        Text(
            text = primaryFormatted,
            style = style,
            color = color
        )
        if (secondaryCurrency != null && manualExchangeRate != null && manualExchangeRate > 0) {
            val convertedAmount = amount * manualExchangeRate
            val secondaryFormatted = CurrenciesAndCountries.format(convertedAmount, secondaryCurrency)
            Text(
                text = "≈ $secondaryFormatted (1 $currencyCode = $manualExchangeRate $secondaryCurrency)",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HealthScorePill(
    score: Int,
    grade: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillColor = when {
        score >= 80 -> FinanceGreen
        score >= 60 -> MintAccentGold
        else -> FinanceRed
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("health_score_pill"),
        color = pillColor.copy(alpha = 0.18f),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(pillColor.copy(alpha = 0.5f))
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(pillColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Score $score/100",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = pillColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "($grade)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = pillColor
            )
        }
    }
}

// === NEW: APP LOCK OVERLAY ===
@Composable
fun AppLockOverlay(
    onUnlock: (String) -> Boolean
) {
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MintPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = MintPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "GrowMint Is Locked",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Text(
                    text = "Enter your 4-digit PIN to access your financial dashboard and balances.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                            pinInput = it
                            errorMessage = null
                            if (it.length == 4) {
                                val success = onUnlock(it)
                                if (!success) {
                                    errorMessage = "Incorrect PIN. Please try again."
                                    pinInput = ""
                                }
                            }
                        }
                    },
                    label = { Text("4-Digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("app_lock_pin_input")
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = FinanceRed
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (pinInput.length == 4) {
                            val success = onUnlock(pinInput)
                            if (!success) {
                                errorMessage = "Incorrect PIN. Please try again."
                                pinInput = ""
                            }
                        } else {
                            errorMessage = "Please enter 4 digits."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                ) {
                    Text("Unlock GrowMint", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// === NEW: PLAIN-LANGUAGE BANK CONSENT DIALOG ===
@Composable
fun BankConsentDialog(
    institutionName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = MintPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Secure Read-Only Access")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Before connecting to $institutionName, here is our strict privacy guarantee:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                HorizontalDivider()

                ConsentBullet(
                    title = "1. Read-Only Balances & History",
                    description = "We can only check your balances and recent transactions to categorize spending."
                )

                ConsentBullet(
                    title = "2. Zero Payments or Transfers",
                    description = "GrowMint CANNOT initiate transfers, debit money, or make payments. We never touch real funds."
                )

                ConsentBullet(
                    title = "3. Bank Credentials Protected",
                    description = "You link directly through the provider's official window. We never see or store your bank password or PIN."
                )

                ConsentBullet(
                    title = "4. Disconnect Anytime",
                    description = "Tap 'Disconnect and delete my data' in Settings at any time to instantly revoke access and erase all records."
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
            ) {
                Text("I Agree & Connect", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ConsentBullet(title: String, description: String) {
    Column {
        Text(text = title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MintPrimary)
        Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// === CONFIRM-BEFORE-ACTION DIALOG WITH HUMAN VALIDATION STEP ===
@Composable
fun ConfirmActionDialog(
    title: String,
    summaryLines: List<String>,
    confirmLabel: String = "Confirm & Apply",
    isDestructive: Boolean = false,
    whyText: String? = null,
    backingNumbers: String? = null,
    ruleId: String? = null,
    activityCategory: String = "Confirmed Change",
    requireReviewCheck: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val activityLogger = LocalActivityLogger.current
    var reviewedChecked by remember(title, summaryLines) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = if (isDestructive) FinanceRed else MintPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "What will change:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        summaryLines.forEach { line ->
                            Text(
                                text = "• $line",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }

                if (!whyText.isNullOrBlank()) {
                    Text(
                        text = "Why: $whyText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (!backingNumbers.isNullOrBlank()) {
                    Surface(
                        color = MintPrimary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Backing numbers: $backingNumbers",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (requireReviewCheck) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { reviewedChecked = !reviewedChecked }
                            .testTag("confirm_reviewed_checkbox")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = reviewedChecked,
                                onCheckedChange = { reviewedChecked = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "I reviewed this summary and want to apply this change.",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }

                Text(
                    text = "Educational guidance only. GrowMint NEVER moves real money.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MintPrimary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanWhat = summaryLines.joinToString("; ")
                    val cleanWhy = whyText?.ifBlank { "Reviewed and approved by user" } ?: "Reviewed and approved by user"
                    val cleanNums = backingNumbers ?: summaryLines.firstOrNull().orEmpty()
                    activityLogger?.invoke(activityCategory, title, cleanWhat, cleanWhy, cleanNums, ruleId)
                    onConfirm()
                },
                enabled = !requireReviewCheck || reviewedChecked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) FinanceRed else MintPrimary
                ),
                modifier = Modifier.testTag("confirm_action_button")
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel_action_button")) {
                Text("Cancel")
            }
        }
    )
}

// === SAFETY-FIRST BUFFER GATE CARD (FEATURE 1) ===
@Composable
fun SafetyBufferGateCard(
    bufferStatus: com.example.model.BufferGateStatus,
    currencyCode: String,
    onEditTarget: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val pct = (bufferStatus.progressFraction * 100).toInt()
    val containerColor = when {
        bufferStatus.isGuidedModeUnder18 -> MintSecondary.copy(alpha = 0.16f)
        bufferStatus.isBufferMet -> FinanceGreen.copy(alpha = 0.14f)
        else -> MintAccentGold.copy(alpha = 0.15f)
    }
    val accentColor = when {
        bufferStatus.isGuidedModeUnder18 -> MintPrimary
        bufferStatus.isBufferMet -> FinanceGreen
        else -> MintAccentGold
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("safety_buffer_gate_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (bufferStatus.isInvestingUnlocked) Icons.Default.Shield else Icons.Default.Lock,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            bufferStatus.isGuidedModeUnder18 -> "Guided Mode (Under 18): Education Only"
                            bufferStatus.isBufferMet -> "Safety Buffer Met ✓ Investing Unlocked"
                            else -> "Build your buffer first"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = accentColor
                    )
                }
                if (onEditTarget != null && !bufferStatus.isGuidedModeUnder18) {
                    TextButton(onClick = onEditTarget) {
                        Text("Edit Target", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (bufferStatus.isGuidedModeUnder18) {
                Text(
                    text = "Under-18 Guided Mode keeps investing hidden and focuses on saving, budgeting, scam defense, and money skills. Emergency buffer progress: $pct%.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else if (!bufferStatus.isBufferMet) {
                Text(
                    text = "Investing splits & suggestions stay locked until your emergency buffer covers ${String.format("%.1f", bufferStatus.targetMonths)} mo of typical spending (${CurrenciesAndCountries.format(bufferStatus.targetAmount, currencyCode)}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    text = "Your ${CurrenciesAndCountries.format(bufferStatus.targetAmount, currencyCode)} safety buffer is funded! Educational investing splits and asset concepts are unlocked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Saved: ${CurrenciesAndCountries.format(bufferStatus.currentEmergencyAmount, currencyCode)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "$pct% of ${CurrenciesAndCountries.format(bufferStatus.targetAmount, currencyCode)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = accentColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { bufferStatus.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

// === DATA QUALITY INDICATOR ROW ===
@Composable
fun DataQualityBadgeRow(
    notes: List<DataQualityNote>,
    modifier: Modifier = Modifier
) {
    if (notes.isEmpty()) return
    var selectedNote by remember { mutableStateOf<DataQualityNote?>(null) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("data_quality_badge_row"),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        notes.forEach { note ->
            val badgeColor = if (note.isLowQuality) MintAccentGold else FinanceGreen
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = badgeColor.copy(alpha = 0.16f),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { selectedNote = note }
                    .testTag("dq_badge_${note.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (note.isLowQuality) Icons.Default.WarningAmber else Icons.Default.Info,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = note.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = badgeColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    selectedNote?.let { note ->
        AlertDialog(
            onDismissRequest = { selectedNote = null },
            title = { Text(note.label, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = note.detail,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { selectedNote = null }) { Text("Got it") }
            }
        )
    }
}

// === REPORT AN ERROR ("THIS LOOKS WRONG") DIALOG ===
@Composable
fun ReportErrorDialog(
    itemTitle: String,
    contextSummary: String,
    ruleId: String? = null,
    onSubmit: (note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var noteText by remember { mutableStateOf("") }
    val quickTags = listOf(
        "Numbers seem off",
        "Missing transaction or income",
        "Debt / goal already updated",
        "Action doesn't fit my situation"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Flag, contentDescription = null, tint = FinanceRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("This Looks Wrong", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Flag \"$itemTitle\" with a short note. It is saved locally on your device for review in Activity History.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (contextSummary.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = contextSummary + (if (!ruleId.isNullOrBlank()) "\nRule: $ruleId" else ""),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickTags.forEach { tag ->
                        SuggestionChip(
                            onClick = { noteText = tag },
                            label = { Text(tag, fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("What looks wrong?") },
                    placeholder = { Text("e.g., My rent payment wasn't included yet") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_error_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (noteText.isNotBlank()) {
                        onSubmit(noteText.trim())
                    }
                },
                enabled = noteText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = FinanceRed),
                modifier = Modifier.testTag("submit_error_flag_btn")
            ) {
                Text("Save Flag", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


