package com.example.ui.traceability

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FinancialCalculator
import com.example.model.*
import com.example.ui.components.DataQualityBadgeRow
import com.example.ui.components.PersistentDisclaimerBanner
import com.example.ui.components.ReportErrorDialog
import com.example.ui.theme.FinanceGreen
import com.example.ui.theme.FinanceRed
import com.example.ui.theme.MintAccentGold
import com.example.ui.theme.MintPrimary
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreDetailScreen(
    tracedScore: TracedMoneyHealthReport,
    scoreHistory: List<ScoreHistorySnapshot>,
    currencyCode: String,
    onOpenHowDecides: () -> Unit,
    onOpenActivityHistory: () -> Unit,
    onFlagError: (itemType: String, itemId: String, itemTitle: String, note: String, snapshotSummary: String, ruleId: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSnapshotIndex by remember(scoreHistory.size) {
        mutableIntStateOf((scoreHistory.size - 1).coerceAtLeast(0))
    }
    var flagTarget by remember { mutableStateOf<Triple<String, String, Pair<String, String?>>?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val selectedSnapshot = scoreHistory.getOrNull(selectedSnapshotIndex)
    val previousSnapshot = if (selectedSnapshotIndex > 0) scoreHistory.getOrNull(selectedSnapshotIndex - 1) else null
    val snapshotDiff = remember(selectedSnapshot, previousSnapshot, currencyCode) {
        selectedSnapshot?.let {
            FinancialCalculator.calculateScoreSnapshotDiff(it, previousSnapshot, currencyCode)
        }
    }

    val scoreColor = when {
        tracedScore.totalScore >= 85 -> FinanceGreen
        tracedScore.totalScore >= 70 -> MintPrimary
        tracedScore.totalScore >= 50 -> MintAccentGold
        else -> FinanceRed
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Money Health Breakdown",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Inputs, formulas, weights & score history",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("score_detail_back_btn")) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { PersistentDisclaimerBanner() }

            // 1. Hero Score Card with Last Calculated + Data Quality + Report Error
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("score_detail_hero_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = scoreColor.copy(alpha = 0.14f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "MONEY HEALTH SCORE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                    color = scoreColor
                                )
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "${tracedScore.totalScore}",
                                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold)
                                    )
                                    Text(
                                        text = "/100 (${tracedScore.gradeLabel})",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                            }
                            TextButton(
                                onClick = {
                                    flagTarget = Triple(
                                        "MONEY_HEALTH_SCORE",
                                        "total_score",
                                        "Money Health Score (${tracedScore.totalScore}/100)" to tracedScore.triggeredRuleIds.firstOrNull()
                                    )
                                },
                                modifier = Modifier.testTag("flag_total_score_btn")
                            ) {
                                Icon(Icons.Default.Flag, contentDescription = "This looks wrong", tint = FinanceRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("This looks wrong", color = FinanceRed, fontSize = 12.sp)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Last calculated: ${tracedScore.calculatedAtFormatted}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DataQualityBadgeRow(notes = tracedScore.dataQualityNotes)

                        // Explicit low-quality data guidance
                        val lowQualityNotes = tracedScore.dataQualityNotes.filter { it.isLowQuality }
                        if (lowQualityNotes.isNotEmpty()) {
                            Surface(
                                color = MintAccentGold.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Data Quality Notes",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MintAccentGold
                                    )
                                    lowQualityNotes.forEach { n ->
                                        Text(
                                            text = "• ${n.label}: ${n.detail}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onOpenHowDecides,
                                modifier = Modifier.weight(1f).testTag("open_how_decides_from_score_btn")
                            ) {
                                Icon(Icons.Default.Rule, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("How Grow Mint decides", fontSize = 11.sp, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = onOpenActivityHistory,
                                modifier = Modifier.weight(1f).testTag("open_activity_history_from_score_btn")
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Activity History", fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // 2. Score History Interactive Trend Chart
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("score_history_chart_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Score History (${scoreHistory.size} Snapshot${if (scoreHistory.size == 1) "" else "s"})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Tap any point",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (scoreHistory.isEmpty()) {
                            Text(
                                text = "No score snapshots recorded yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            // Interactive Canvas Chart
                            val chartPoints = scoreHistory
                            val lineColor = MintPrimary
                            val selectedDotColor = MintAccentGold
                            val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .pointerInput(chartPoints.size) {
                                        detectTapGestures { offset ->
                                            if (chartPoints.isEmpty()) return@detectTapGestures
                                            val padX = 28f
                                            val usableW = (size.width - padX * 2).coerceAtLeast(1f)
                                            val closest = chartPoints.indices.minByOrNull { idx ->
                                                val px = if (chartPoints.size == 1) size.width / 2f
                                                else padX + (idx.toFloat() / (chartPoints.size - 1)) * usableW
                                                abs(offset.x - px)
                                            } ?: 0
                                            selectedSnapshotIndex = closest
                                        }
                                    }
                                    .testTag("score_history_canvas")
                            ) {
                                val padX = 28f
                                val padY = 18f
                                val usableW = (size.width - padX * 2).coerceAtLeast(1f)
                                val usableH = (size.height - padY * 2).coerceAtLeast(1f)

                                // Horizontal reference lines (0, 50, 100)
                                listOf(0f, 0.5f, 1f).forEach { frac ->
                                    val y = padY + (1f - frac) * usableH
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(padX, y),
                                        end = Offset(size.width - padX, y),
                                        strokeWidth = 2f
                                    )
                                }

                                val coords = chartPoints.mapIndexed { idx, snap ->
                                    val x = if (chartPoints.size == 1) size.width / 2f
                                    else padX + (idx.toFloat() / (chartPoints.size - 1)) * usableW
                                    val y = padY + (1f - (snap.totalScore.coerceIn(0, 100) / 100f)) * usableH
                                    Offset(x, y)
                                }

                                if (coords.size >= 2) {
                                    val path = Path().apply {
                                        moveTo(coords.first().x, coords.first().y)
                                        for (i in 1 until coords.size) {
                                            lineTo(coords[i].x, coords[i].y)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = lineColor,
                                        style = Stroke(width = 6f, cap = StrokeCap.Round)
                                    )
                                }

                                coords.forEachIndexed { idx, pt ->
                                    val isSelected = idx == selectedSnapshotIndex
                                    if (isSelected) {
                                        drawCircle(
                                            color = selectedDotColor.copy(alpha = 0.35f),
                                            radius = 18f,
                                            center = pt
                                        )
                                    }
                                    drawCircle(
                                        color = if (isSelected) selectedDotColor else lineColor,
                                        radius = if (isSelected) 11f else 8f,
                                        center = pt
                                    )
                                }
                            }

                            // Accessible snapshot chips for exact point tapping
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                scoreHistory.forEachIndexed { idx, snap ->
                                    FilterChip(
                                        selected = idx == selectedSnapshotIndex,
                                        onClick = { selectedSnapshotIndex = idx },
                                        label = {
                                            Text(
                                                text = "${snap.dateLabel} • ${snap.totalScore}",
                                                fontSize = 11.sp
                                            )
                                        },
                                        modifier = Modifier.testTag("history_point_chip_$idx")
                                    )
                                }
                            }

                            // Diff explanation for selected snapshot
                            snapshotDiff?.let { diff ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("score_diff_detail_card")
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Snapshot: ${diff.currentSnapshot.dateLabel}",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            val deltaBadgeColor = when {
                                                diff.previousSnapshot == null -> MintPrimary
                                                diff.totalDelta > 0 -> FinanceGreen
                                                diff.totalDelta < 0 -> FinanceRed
                                                else -> MintPrimary
                                            }
                                            Surface(color = deltaBadgeColor.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
                                                val deltaStr = when {
                                                    diff.previousSnapshot == null -> "Baseline (${diff.currentSnapshot.totalScore}/100)"
                                                    diff.totalDelta > 0 -> "+${diff.totalDelta} pts"
                                                    else -> "${diff.totalDelta} pts"
                                                }
                                                Text(
                                                    text = deltaStr,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = deltaBadgeColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = diff.primaryReason,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                        )

                                        HorizontalDivider()

                                        diff.componentDeltas.forEach { cd ->
                                            val deltaColor = when {
                                                cd.delta > 0 -> FinanceGreen
                                                cd.delta < 0 -> FinanceRed
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(cd.componentName, style = MaterialTheme.typography.labelSmall)
                                                Text(
                                                    text = if (diff.previousSnapshot == null) "${cd.currentPoints} pts"
                                                    else "${cd.previousPoints} -> ${cd.currentPoints} pts (${if (cd.delta > 0) "+" else ""}${cd.delta})",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = deltaColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Detailed Breakdown of All 4 Components
            item {
                Text(
                    text = "4 Score Components (Total Weight: ${tracedScore.components.sumOf { it.weightPercent }}%)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(tracedScore.components, key = { it.id }) { component ->
                val frac = if (component.maxPoints > 0) (component.earnedPoints.toFloat() / component.maxPoints).coerceIn(0f, 1f) else 0f
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("score_component_card_${component.id}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = component.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Weight: ${component.weightPercent}% of total • Rule: ${component.ruleId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MintPrimary
                                )
                            }
                            Surface(color = MintPrimary.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = "${component.earnedPoints} / ${component.maxPoints} pts",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MintPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MintPrimary,
                            trackColor = MaterialTheme.colorScheme.surface
                        )

                        Text(
                            text = component.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Plain-words formula
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Formula (Plain Words)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = component.formulaPlainWords,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        // Exact input values used
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Exact Inputs Used",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            component.inputs.forEach { metric ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(metric.label, style = MaterialTheme.typography.bodySmall)
                                        if (metric.sourceNote.isNotBlank()) {
                                            Text(
                                                text = metric.sourceNote,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = metric.valueFormatted,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    flagTarget = Triple(
                                        "SCORE_COMPONENT",
                                        component.id,
                                        "${component.name} (${component.earnedPoints}/${component.maxPoints})" to component.ruleId
                                    )
                                },
                                modifier = Modifier.testTag("flag_component_btn_${component.id}")
                            ) {
                                Icon(Icons.Default.Flag, contentDescription = null, tint = FinanceRed, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("This looks wrong", color = FinanceRed, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    flagTarget?.let { (itemType, itemId, titleAndRule) ->
        val (title, ruleId) = titleAndRule
        ReportErrorDialog(
            itemTitle = title,
            contextSummary = "Score: ${tracedScore.totalScore}/100 at ${tracedScore.calculatedAtFormatted}",
            ruleId = ruleId,
            onSubmit = { note ->
                onFlagError(itemType, itemId, title, note, "Total Score ${tracedScore.totalScore}/100", ruleId)
                flagTarget = null
                snackbarMessage = "Flag saved locally in Activity History."
            },
            onDismiss = { flagTarget = null }
        )
    }
}
