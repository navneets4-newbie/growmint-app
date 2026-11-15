package com.example.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiCoachManager
import com.example.ai.PlanUiState
import com.example.data.FinancialCalculator
import com.example.model.BufferGateStatus
import com.example.model.GrowthPlanItem
import com.example.model.UserProfile
import com.example.model.VirtualEnvelope
import com.example.ui.components.PersistentDisclaimerBanner
import com.example.ui.components.SafetyBufferGateCard
import com.example.ui.theme.*
import kotlinx.coroutines.launch

data class ChatMessage(
    val sender: String, // "user" or "coach"
    val text: String,
    val isStreaming: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCoachScreen(
    profile: UserProfile,
    envelopes: List<VirtualEnvelope> = emptyList(),
    bufferStatus: BufferGateStatus = FinancialCalculator.calculateBufferGateStatus(profile, envelopes),
    coachManager: AiCoachManager,
    onNavigateToSandbox: () -> Unit,
    onOpenScamChecker: () -> Unit = {},
    onOpenWhatIf: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Growth Plan, 1: AI Chat

    // Connect directly to coachManager state so plan survives navigation
    val planState = coachManager.currentPlanState
    var selectedPillarFilter by remember { mutableStateOf("All") }

    // Chat state
    var chatInput by remember { mutableStateOf("") }
    val chatMessages = remember {
        mutableStateListOf(
            ChatMessage(
                sender = "coach",
                text = "Hi! I am GrowMint AI, your friendly money coach for ${profile.country}. I can analyze your income, debts, and goals to build a simple, 100% legal roadmap to grow your wealth, or answer questions in plain English. Tap 'Generate My Growth Plan' to see your plan!"
            )
        )
    }
    var isChatStreaming by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MintPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = MintPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "GrowMint Money Coach",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Simple Legal Advice • ${profile.country}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
            PersistentDisclaimerBanner(modifier = Modifier.padding(bottom = 8.dp))

            // Tab bar
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                indicator = {},
                divider = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("My Growth Plan", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("growth_plan_tab")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Ask the Coach", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("ai_chat_tab")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                // Growth Plan View
                GrowthPlanView(
                    profile = profile,
                    bufferStatus = bufferStatus,
                    planState = planState,
                    selectedFilter = selectedPillarFilter,
                    onFilterChange = { selectedPillarFilter = it },
                    onGeneratePlan = {
                        coroutineScope.launch {
                            coachManager.generateGrowthPlan(profile, envelopes)
                        }
                    },
                    onNavigateToSandbox = onNavigateToSandbox,
                    onOpenScamChecker = onOpenScamChecker,
                    onOpenWhatIf = onOpenWhatIf
                )
            } else {
                // Chat View
                AiChatView(
                    profile = profile,
                    bufferStatus = bufferStatus,
                    messages = chatMessages,
                    input = chatInput,
                    onInputChange = { chatInput = it },
                    isStreaming = isChatStreaming,
                    onSendMessage = { userText ->
                        if (userText.isNotBlank() && !isChatStreaming) {
                            chatMessages.add(ChatMessage(sender = "user", text = userText))
                            chatInput = ""
                            isChatStreaming = true
                            coroutineScope.launch {
                                val coachMsg = ChatMessage(sender = "coach", text = "", isStreaming = true)
                                chatMessages.add(coachMsg)
                                val msgIndex = chatMessages.size - 1

                                var accumulated = ""
                                try {
                                    coachManager.streamCoachChat(userText, profile, envelopes).collect { chunk ->
                                        accumulated += chunk
                                        chatMessages[msgIndex] = ChatMessage(sender = "coach", text = accumulated, isStreaming = true)
                                    }
                                    chatMessages[msgIndex] = ChatMessage(sender = "coach", text = accumulated, isStreaming = false)
                                } catch (e: Exception) {
                                    chatMessages[msgIndex] = ChatMessage(sender = "coach", text = accumulated + "\n[Note: ${e.localizedMessage}]", isStreaming = false)
                                } finally {
                                    isChatStreaming = false
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun GrowthPlanView(
    profile: UserProfile,
    bufferStatus: BufferGateStatus,
    planState: PlanUiState,
    selectedFilter: String,
    onFilterChange: (String) -> Unit,
    onGeneratePlan: () -> Unit,
    onNavigateToSandbox: () -> Unit,
    onOpenScamChecker: () -> Unit,
    onOpenWhatIf: () -> Unit
) {
    val pillars = remember(profile.isUnder18, bufferStatus.isInvestingUnlocked) {
        buildList {
            add("All")
            add("Protect")
            add("Save")
            if (bufferStatus.isInvestingUnlocked) add("Invest")
            if (!profile.isUnder18) add("Trading")
            add("Earn")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SafetyBufferGateCard(
                bufferStatus = bufferStatus,
                currencyCode = profile.currencyCode
            )
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenScamChecker,
                    modifier = Modifier.weight(1f).testTag("coach_open_scam_btn")
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scam Checker", fontSize = 12.sp)
                }
                if (!profile.isUnder18) {
                    OutlinedButton(
                        onClick = onOpenWhatIf,
                        modifier = Modifier.weight(1f).testTag("coach_open_whatif_btn")
                    ) {
                        Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("What-If Check", fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("generate_plan_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Your Personalized Growth Roadmap",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Educational plan tailored for ${profile.userSituation} (${profile.incomeType.label} income), buffer progress, ${profile.sideIncomeHoursPerWeek} side-hustle hrs/wk, and ${profile.country} context. (Not licensed financial advice.)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onGeneratePlan,
                        enabled = planState !is PlanUiState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("generate_growth_plan_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MintPrimary)
                    ) {
                        if (planState is PlanUiState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Analyzing Your Profile...")
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (planState is PlanUiState.Success) "Regenerate My Growth Plan" else "Generate My Growth Plan",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        when (planState) {
            is PlanUiState.Idle -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = MintPrimary, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "5 Simple Steps to Grow Your Money Safely",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap 'Generate My Growth Plan' above! We'll review your balance (${profile.currentBalance} ${profile.currencyCode}), any debts, and your risk comfort to map out an easy, step-by-step game plan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
            is PlanUiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MintPrimary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Personalizing legal growth options for ${profile.country}...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            is PlanUiState.Error -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = FinanceRed.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "Notice", fontWeight = FontWeight.Bold, color = FinanceRed)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = planState.message, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(onClick = onGeneratePlan, colors = ButtonDefaults.buttonColors(containerColor = FinanceRed)) {
                                Text("Try Again")
                            }
                        }
                    }
                }
            }
            is PlanUiState.Success -> {
                // Summary card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MintSecondary.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Insights, contentDescription = null, tint = MintPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Your Money Game Plan", fontWeight = FontWeight.Bold, color = MintPrimary)
                                }
                                Surface(
                                    color = MintPrimary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (planState.isOfflineEngine) "Tailored to Your Ledger" else "Gemini 3.8 Verified",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                        color = MintPrimaryLight,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = planState.summary,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // Filter pills
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pillars) { p ->
                            val labelText = when (p) {
                                "Protect" -> "1. Protect"
                                "Save" -> "2. Save"
                                "Invest" -> "3. Invest"
                                "Trading" -> "4. Trading (Risk)"
                                "Earn" -> "5. Earn"
                                else -> "All Pillars"
                            }
                            FilterChip(
                                selected = selectedFilter == p,
                                onClick = { onFilterChange(p) },
                                label = { Text(labelText) }
                            )
                        }
                    }
                }

                val filteredItems = if (selectedFilter == "All") {
                    planState.items
                } else {
                    planState.items.filter { it.pillar.equals(selectedFilter, ignoreCase = true) }
                }

                items(filteredItems) { item ->
                    PlanItemCard(
                        item = item,
                        currencyCode = profile.currencyCode,
                        onNavigateToSandbox = onNavigateToSandbox
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PlanItemCard(
    item: GrowthPlanItem,
    currencyCode: String,
    onNavigateToSandbox: () -> Unit
) {
    val pillarColor = when (item.pillar.lowercase()) {
        "protect" -> FinanceBlue
        "save" -> FinanceGreen
        "invest" -> FinancePurple
        "trading" -> FinanceGold
        "earn" -> MintPrimary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = pillarColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = item.pillar.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = pillarColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Risk: ${item.riskLevel}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Specs grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Starting Cash", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = item.minimumToStart, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                }
                Column {
                    Text(text = "Timeline", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = item.timeHorizon, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                }
                Column {
                    Text(text = "Expected Range", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = item.realisticRange, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Country note
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (item.isUncertain) FinanceGold.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (item.isUncertain) Icons.Default.Warning else Icons.Default.Public,
                        contentDescription = null,
                        tint = if (item.isUncertain) FinanceGold else MintPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.countryNotes,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (item.actionStep.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MintPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "First Step: ${item.actionStep}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MintPrimary
                    )
                }
            }

            // Trading sandbox link (Requirement D)
            if (item.pillar.equals("Trading", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onNavigateToSandbox,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = FinanceGold)
                ) {
                    Icon(Icons.Default.CandlestickChart, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Practice in Paper-Trading Sandbox (Zero Risk)", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AiChatView(
    profile: UserProfile,
    bufferStatus: BufferGateStatus,
    messages: List<ChatMessage>,
    input: String,
    onInputChange: (String) -> Unit,
    isStreaming: Boolean,
    onSendMessage: (String) -> Unit
) {
    val quickPrompts = remember(profile.isUnder18, bufferStatus.isBufferMet) {
        when {
            profile.isUnder18 -> listOf(
                "How do I build a starter safety buffer?",
                "How do I spot UPI or job scams?",
                "Explain budgeting on pocket money",
                "Why shouldn't anyone borrow to invest?"
            )
            !bufferStatus.isBufferMet -> listOf(
                "Why build a 1-month emergency buffer first?",
                "What is the easiest way to pay off debts?",
                "How does Pay-Yourself-a-Salary work?",
                "How can I tell if a message is a scam?"
            )
            else -> listOf(
                "What is the easiest way to pay off debts?",
                "Explain index funds and SIPs simply",
                "How much emergency fund do I need?",
                "Why do most short-term traders lose money?"
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Chat messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = false,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                val isUser = msg.sender == "user"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        modifier = Modifier.widthIn(max = 300.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUser) MintPrimary else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (isUser) "You" else "GrowMint AI",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isUser) Color.White.copy(alpha = 0.8f) else MintPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }

        // Quick prompts
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(quickPrompts) { prompt ->
                SuggestionChip(
                    onClick = { onSendMessage(prompt) },
                    label = { Text(prompt, fontSize = 11.sp) }
                )
            }
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = { Text("Ask your money question...") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("coach_chat_input"),
                singleLine = false,
                maxLines = 3
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { onSendMessage(input) },
                enabled = input.isNotBlank() && !isStreaming,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (input.isNotBlank() && !isStreaming) MintPrimary else MaterialTheme.colorScheme.surfaceVariant)
                    .testTag("send_coach_chat_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (input.isNotBlank() && !isStreaming) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
