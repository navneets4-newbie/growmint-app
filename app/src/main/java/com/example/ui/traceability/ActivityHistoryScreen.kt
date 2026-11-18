package com.example.ui.traceability

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiCoachManager
import com.example.data.FinancialCalculator
import com.example.data.RuleRegistry
import com.example.model.ActivityHistoryEntry
import com.example.model.ErrorFlagEntry
import com.example.model.ExplanationFeedbackEntry
import com.example.model.TraceableAiExplanation
import com.example.model.UserProfile
import com.example.ui.components.DataQualityBadgeRow
import com.example.ui.components.ExplanationSenseCheckRow
import com.example.ui.components.PersistentDisclaimerBanner
import com.example.ui.components.ScannableLabelValueRow
import com.example.ui.components.TopLevelDataQualityWarning
import com.example.ui.components.TraceableExplanationTextWithChips
import com.example.ui.theme.FinanceGreen
import com.example.ui.theme.FinanceRed
import com.example.ui.theme.MintAccentGold
import com.example.ui.theme.MintPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityHistoryScreen(
    activityHistory: List<ActivityHistoryEntry>,
    errorFlags: List<ErrorFlagEntry>,
    profile: UserProfile = UserProfile(),
    coachManager: AiCoachManager = remember { AiCoachManager() },
    explanationFeedback: List<ExplanationFeedbackEntry> = emptyList(),
    onSubmitExplanationFeedback: (targetId: String, targetType: String, targetTitle: String, summaryShown: String, ruleId: String?, madeSense: Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onSaveActivityAiExplanation: (entryId: String, explanation: String) -> Unit = { _, _ -> },
    onSaveActivityTraceableExplanation: (entryId: String, traceable: TraceableAiExplanation) -> Unit = { _, _ -> },
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
    var expandedEntryIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val dateFmt = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()) }

    val feedbackByEntryId = remember(explanationFeedback) {
        explanationFeedback
            .filter { it.targetType == "ACTIVITY_ENTRY" }
            .associateBy { it.targetId }
    }
    val confusedCount = remember(explanationFeedback) {
        explanationFeedback.count { !it.madeSense }
    }

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
                            text = "Tap any entry summary for the full snapshot",
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

            if (confusedCount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = MintAccentGold.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "$confusedCount explanation(s) marked 'Not really' — check entries below or export to review.",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MintAccentGold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
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
                            val isExpanded = expandedEntryIds.contains(entry.id)
                            val savedSense = feedbackByEntryId[entry.id]?.madeSense
                            val oneLineSummary = remember(entry) {
                                entry.oneLineSummary.ifBlank {
                                    FinancialCalculator.buildActivityOneLineSummary(
                                        title = entry.title,
                                        numbersSummary = entry.numbersSummary,
                                        whatChanged = entry.whatChanged,
                                        ruleId = entry.ruleId,
                                        category = entry.category
                                    )
                                }
                            }
                            val resolvedRuleName = remember(entry) {
                                entry.ruleName.ifBlank {
                                    RuleRegistry.displayRuleName(
                                        entry.ruleId,
                                        FinancialCalculator.defaultRuleNameForCategory(entry.category)
                                    )
                                }
                            }
                            val resolvedRuleVersion = remember(entry) {
                                entry.ruleVersion.ifBlank { RuleRegistry.displayRuleVersion(entry.ruleId) }
                            }
                            val resolvedRuleWording = remember(entry) {
                                entry.ruleWording.ifBlank {
                                    RuleRegistry.displayRuleWording(
                                        entry.ruleId,
                                        entry.whySummary.ifBlank { "Reviewed and confirmed by the user." }
                                    )
                                }
                            }

                            val entryFacts = remember(entry) {
                                entry.snapshotCalculatedFacts.ifEmpty {
                                    FinancialCalculator.buildFactsForActivityEntry(
                                        title = entry.title,
                                        category = entry.category,
                                        numbersSummary = entry.numbersSummary,
                                        ruleId = entry.ruleId,
                                        snapshotInputs = entry.snapshotInputs,
                                        snapshotTotalScore = entry.snapshotTotalScore
                                    )
                                }
                            }
                            var loadedTraceable by remember(
                                entry.id,
                                entry.aiExplanationTemplate,
                                entry.aiExplanationIsFallback,
                                entry.aiExplanationNote
                            ) {
                                mutableStateOf(
                                    if (entry.aiExplanationTemplate.isNotBlank()) {
                                        TraceableAiExplanation(
                                            templateWithIds = entry.aiExplanationTemplate,
                                            plainResolvedText = entry.aiExplanation,
                                            facts = entryFacts,
                                            isFallback = entry.aiExplanationIsFallback,
                                            note = entry.aiExplanationNote
                                        )
                                    } else {
                                        null
                                    }
                                )
                            }
                            var isLoadingAi by remember(entry.id) { mutableStateOf(false) }

                            LaunchedEffect(isExpanded, entry.id) {
                                if (isExpanded && loadedTraceable == null) {
                                    isLoadingAi = true
                                    val generated = coachManager.explainSnapshotTraceable(
                                        title = entry.title,
                                        category = entry.category,
                                        ruleWording = resolvedRuleWording,
                                        facts = entryFacts,
                                        profile = profile
                                    )
                                    loadedTraceable = generated
                                    onSaveActivityAiExplanation(entry.id, generated.plainResolvedText)
                                    onSaveActivityTraceableExplanation(entry.id, generated)
                                    isLoadingAi = false
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("activity_entry_card_${entry.id}"),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Top metadata row: category, saved feedback badge, timestamp, expand chevron
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(color = MintPrimary.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
                                                Text(
                                                    text = entry.category.uppercase(),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                                    color = MintPrimary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            if (savedSense != null) {
                                                Surface(
                                                    color = (if (savedSense) FinanceGreen else MintAccentGold).copy(alpha = 0.16f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = if (savedSense) "Sense: Yes" else "Sense: Not really",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                                        color = if (savedSense) FinanceGreen else MintAccentGold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = dateFmt.format(Date(entry.timestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // 1. Tappable one-line summary row (1 tap expands full snapshot)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                expandedEntryIds = if (isExpanded) {
                                                    expandedEntryIds - entry.id
                                                } else {
                                                    expandedEntryIds + entry.id
                                                }
                                            }
                                            .testTag("activity_summary_${entry.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = oneLineSummary,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                maxLines = if (isExpanded) 3 else 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = if (isExpanded) "Collapse snapshot" else "Expand snapshot",
                                                tint = MintPrimary
                                            )
                                        }
                                    }

                                    // 3. Top-level data-quality flag ONLY when data quality affects the result
                                    TopLevelDataQualityWarning(
                                        notes = entry.dataQualityNotes,
                                        testTag = "activity_dq_warning_${entry.id}"
                                    )

                                    // 4. Scannable short labeled rows (consistent order, no long paragraphs)
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        ScannableLabelValueRow(
                                            label = "Action",
                                            value = entry.title
                                        )
                                        if (entry.numbersSummary.isNotBlank()) {
                                            ScannableLabelValueRow(
                                                label = "Key figure",
                                                value = entry.numbersSummary.substringBefore(";").trim(),
                                                valueColor = MintPrimary
                                            )
                                        }
                                        ScannableLabelValueRow(
                                            label = "Rule",
                                            value = "$resolvedRuleName ($resolvedRuleVersion)"
                                        )
                                    }

                                    // 2. Expanded Snapshot Detail (nothing missing)
                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp)
                                                .testTag("activity_expanded_${entry.id}"),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            HorizontalDivider()

                                            // Preserved original fields
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(
                                                        text = "Recorded Change",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text("Change: ${entry.whatChanged}", style = MaterialTheme.typography.bodySmall)
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
                                                }
                                            }

                                            // Rule wording & version
                                            Surface(
                                                color = MintPrimary.copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(
                                                        text = "Rule Wording & Version",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MintPrimary
                                                    )
                                                    ScannableLabelValueRow(
                                                        label = "Rule",
                                                        value = "$resolvedRuleName (${entry.ruleId ?: "USER_CONFIRMED"} · $resolvedRuleVersion)",
                                                        maxLines = 2
                                                    )
                                                    Text(
                                                        text = resolvedRuleWording,
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                    if (entry.whatWouldChangeThis.isNotBlank()) {
                                                        Text(
                                                            text = "What would change this? ${entry.whatWouldChangeThis}",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                            color = MintPrimary
                                                        )
                                                    }
                                                }
                                            }

                                            // Inputs snapshot
                                            if (entry.snapshotInputs.isNotEmpty()) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surface,
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Text(
                                                            text = "Inputs at Decision Time",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        entry.snapshotInputs.forEach { metric ->
                                                            ScannableLabelValueRow(
                                                                label = metric.label,
                                                                value = metric.valueFormatted,
                                                                maxLines = 2
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // Score components snapshot
                                            if (entry.snapshotScoreComponents.isNotEmpty() || entry.snapshotTotalScore != null) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surface,
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                text = "Score Components",
                                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                            entry.snapshotTotalScore?.let { sc ->
                                                                Text(
                                                                    text = "$sc/100",
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                                    color = MintPrimary
                                                                )
                                                            }
                                                        }
                                                        entry.snapshotScoreComponents.forEach { comp ->
                                                            ScannableLabelValueRow(
                                                                label = "${comp.name} (${comp.weightPercent}%)",
                                                                value = "${comp.earnedPoints}/${comp.maxPoints} pts"
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // AI plain-English explanation
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(
                                                        text = "AI Plain-Language Explanation",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    if (isLoadingAi) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text("Explaining stored snapshot...", style = MaterialTheme.typography.bodySmall)
                                                        }
                                                    } else {
                                                        val exp = loadedTraceable ?: TraceableAiExplanation(
                                                            templateWithIds = FinancialCalculator.buildActivityFallbackTemplate(
                                                                entry.title,
                                                                resolvedRuleWording,
                                                                entryFacts
                                                            ),
                                                            plainResolvedText = entry.aiExplanation,
                                                            facts = entryFacts,
                                                            isFallback = entry.aiExplanationIsFallback,
                                                            note = entry.aiExplanationNote
                                                        )
                                                        TraceableExplanationTextWithChips(
                                                            templateWithIds = exp.templateWithIds,
                                                            facts = exp.facts,
                                                            isFallback = exp.isFallback,
                                                            fallbackNote = exp.note,
                                                            testTagPrefix = "activity_trace_${entry.id}"
                                                        )
                                                    }
                                                }
                                            }

                                            // Data-quality notes inside expanded view (always shown when recorded)
                                            if (entry.dataQualityNotes.isNotEmpty()) {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(
                                                        text = "Data-Quality Notes",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    DataQualityBadgeRow(notes = entry.dataQualityNotes)
                                                    entry.dataQualityNotes.forEach { dq ->
                                                        Text(
                                                            text = "• ${dq.label}: ${dq.detail}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }

                                            // 5. "Explain it to me" check: Did this make sense?
                                            ExplanationSenseCheckRow(
                                                currentAnswer = savedSense,
                                                onSelectAnswer = { answer ->
                                                    onSubmitExplanationFeedback(
                                                        entry.id,
                                                        "ACTIVITY_ENTRY",
                                                        entry.title,
                                                        oneLineSummary,
                                                        entry.ruleId,
                                                        answer
                                                    )
                                                },
                                                yesTag = "activity_sense_yes_${entry.id}",
                                                noTag = "activity_sense_no_${entry.id}"
                                            )
                                        }
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
