package com.example.ui.traceability

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.example.ai.AiCoachManager
import com.example.data.FinancialCalculator
import com.example.model.*
import com.example.ui.components.DataQualityBadgeRow
import com.example.ui.components.ExplanationSenseCheckRow
import com.example.ui.components.ScannableLabelValueRow
import com.example.ui.components.TraceableExplanationTextWithChips
import com.example.ui.theme.FinanceGreen
import com.example.ui.theme.FinanceRed
import com.example.ui.theme.MintAccentGold
import com.example.ui.theme.MintPrimary

@Composable
fun WhyActionDialog(
    action: MonthlyActionItem,
    profile: UserProfile,
    dataQualityNotes: List<DataQualityNote>,
    coachManager: AiCoachManager,
    scoreComponents: List<ScoreComponentDetail> = emptyList(),
    explanationFeedbackAnswer: Boolean? = null,
    onSubmitExplanationFeedback: (Boolean) -> Unit = {},
    onNavigateDestination: (ActionTargetDestination) -> Unit,
    onOpenHowDecides: () -> Unit,
    onFlagError: () -> Unit,
    onDismiss: () -> Unit
) {
    val trace = action.traceability
    var traceableExplanation by remember(action.actionKey) { mutableStateOf<TraceableAiExplanation?>(null) }
    var isExplaining by remember(action.actionKey) { mutableStateOf(true) }

    LaunchedEffect(action.actionKey) {
        isExplaining = true
        traceableExplanation = coachManager.explainActionTraceable(action, profile)
        isExplaining = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = MintPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Why This Action?",
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(
                    onClick = onFlagError,
                    modifier = Modifier.testTag("why_dialog_flag_btn")
                ) {
                    Icon(Icons.Default.Flag, contentDescription = "This looks wrong", tint = FinanceRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("This looks wrong", color = FinanceRed, fontSize = 11.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = FinancialCalculator.resolveActionOneLineSummary(action),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                DataQualityBadgeRow(notes = dataQualityNotes)

                if (trace != null) {
                    // (a) Rule ID, wording & version
                    Surface(
                        color = MintPrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${trace.ruleId} · ${trace.ruleVersion}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MintPrimary
                                )
                                TextButton(
                                    onClick = onOpenHowDecides,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("All Rules", fontSize = 11.sp)
                                }
                            }
                            Text(
                                text = trace.ruleTitle,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = trace.ruleDescription,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Trigger: ${trace.triggerCondition}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (trace.whatWouldChangeThis.isNotBlank()) {
                                Text(
                                    text = "What would change this? ${trace.whatWouldChangeThis}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MintPrimary
                                )
                            }
                        }
                    }

                    // (b) Exact triggering numbers
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Exact Numbers Used",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            trace.triggeringNumbers.forEach { metric ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = metric.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = metric.valueFormatted,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }

                    // (c) Code-calculated expected effect on score
                    val effectColor = if (trace.expectedScoreDelta > 0) FinanceGreen else MintAccentGold
                    Surface(
                        color = effectColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Expected Score Effect (Code-Calculated)",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = effectColor
                                )
                                Text(
                                    text = if (trace.expectedScoreDelta > 0) "+${trace.expectedScoreDelta} pts" else "0 pts now",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = effectColor
                                )
                            }
                            Text(
                                text = trace.expectedScoreExplanation,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // (d) Data sources used
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Data Sources Checked",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        trace.dataSources.forEach { src ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Storage, contentDescription = null, tint = MintPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = src, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    // (e) Top contributing entries with clickable links
                    if (trace.contributors.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Top Contributors (Tap to Open)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            trace.contributors.forEachIndexed { idx, item ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            onDismiss()
                                            onNavigateDestination(item.destination)
                                        }
                                        .testTag("why_contributor_link_$idx")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.label,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = item.detail,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = item.destination.label,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MintPrimary
                                            )
                                        }
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = item.destination.label,
                                            tint = MintPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (scoreComponents.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Score Components Snapshot",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            scoreComponents.forEach { comp ->
                                ScannableLabelValueRow(
                                    label = "${comp.name} (${comp.weightPercent}%)",
                                    value = "${comp.earnedPoints}/${comp.maxPoints} pts"
                                )
                            }
                        }
                    }
                }

                // Plain-English AI explanation strictly grounded in code output
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Plain-Language Summary",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (isExplaining) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Summarizing stored rule facts...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            val exp = traceableExplanation
                            if (exp != null) {
                                TraceableExplanationTextWithChips(
                                    templateWithIds = exp.templateWithIds,
                                    facts = exp.facts,
                                    isFallback = exp.isFallback,
                                    fallbackNote = exp.note,
                                    onNavigateDestination = { dest ->
                                        onDismiss()
                                        onNavigateDestination(dest)
                                    },
                                    testTagPrefix = "why_dialog_trace"
                                )
                            } else {
                                Text(
                                    text = action.what,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                ExplanationSenseCheckRow(
                    currentAnswer = explanationFeedbackAnswer,
                    onSelectAnswer = onSubmitExplanationFeedback,
                    yesTag = "why_dialog_sense_yes",
                    noTag = "why_dialog_sense_no"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
            ) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}
