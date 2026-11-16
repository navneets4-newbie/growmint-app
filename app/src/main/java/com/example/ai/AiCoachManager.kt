package com.example.ai

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.FinancialCalculator
import com.example.data.TransactionImportEngine
import com.example.model.*
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

private const val TAG = "GrowMintAiCoach"

sealed interface PlanUiState {
    data object Idle : PlanUiState
    data object Loading : PlanUiState
    data class Success(
        val summary: String,
        val items: List<GrowthPlanItem>,
        val isOfflineEngine: Boolean = false
    ) : PlanUiState
    data class Error(val message: String) : PlanUiState
}

class AiCoachManager {

    var currentPlanState by mutableStateOf<PlanUiState>(PlanUiState.Idle)

    private val planSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(description = "Simple, everyday summary of the user's financial roadmap"),
            "items" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "pillar" to Schema.string(description = "One of: Protect, Save, Invest, Trading, Earn"),
                        "title" to Schema.string(description = "General category or legal habit in simple words (NO specific product names or ticker symbols)"),
                        "description" to Schema.string(description = "Clear, friendly educational explanation in everyday English. Include a one-sentence risk note for any investing asset type."),
                        "minimumToStart" to Schema.string(description = "Illustrative starting amount label"),
                        "timeHorizon" to Schema.string(description = "Time horizon: Short (0-1 yr), Medium (1-5 yrs), Long (5+ yrs)"),
                        "riskLevel" to Schema.string(description = "Risk level: Very Low, Low, Moderate, High, Very High"),
                        "realisticRange" to Schema.string(description = "Illustrative context without promising returns"),
                        "countryNotes" to Schema.string(description = "General regional rules in user country. If not verified, write [uncertain]"),
                        "actionStep" to Schema.string(description = "Simple educational first step (never tell user to buy a product)")
                    )
                ),
                description = "List of educational financial strategies tailored to user profile and buffer gate"
            )
        )
    )

    private val scamSchema = Schema.obj(
        properties = mapOf(
            "verdict" to Schema.enumeration(
                values = listOf("Likely scam", "Suspicious", "Looks fine"),
                description = "Cautious educational assessment; never claim 100% certainty"
            ),
            "redFlags" to Schema.array(
                items = Schema.string(),
                description = "Specific warning signs detected in the message or situation"
            ),
            "nextSteps" to Schema.array(
                items = Schema.string(),
                description = "Practical, calm steps the user should take next"
            )
        )
    )

    private val planModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = planSchema
                temperature = 0.2f
            }
        )
    }

    private val scamModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = scamSchema
                temperature = 0.1f
            }
        )
    }

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.6f
            }
        )
    }

    private val txExtractionSchema = Schema.obj(
        properties = mapOf(
            "transactions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "date" to Schema.string(description = "Transaction date string extracted from text, e.g. 2025-02-14 or 14/02/2025"),
                        "amount" to Schema.double(description = "Positive numeric transaction amount without currency symbol"),
                        "type" to Schema.enumeration(
                            values = listOf("income", "expense"),
                            description = "Whether money was received (income) or spent (expense)"
                        ),
                        "merchant" to Schema.string(description = "Merchant, payee, sender, or short description"),
                        "suggestedCategory" to Schema.string(
                            description = "One of: Groceries, Dining & Coffee, Transport, Bills & Utilities, Rent & Housing, Subscriptions, Shopping, Health, Education, Income & Salary, Side Hustle, Transfer, General"
                        )
                    )
                ),
                description = "List of transactions extracted from the sanitized text"
            )
        )
    )

    private val categorySuggestionSchema = Schema.obj(
        properties = mapOf(
            "suggestions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "index" to Schema.integer(description = "Original index from the input list"),
                        "category" to Schema.string(
                            description = "One of: Groceries, Dining & Coffee, Transport, Bills & Utilities, Rent & Housing, Subscriptions, Shopping, Health, Education, Income & Salary, Side Hustle, Transfer, General"
                        )
                    )
                ),
                description = "Suggested categories matching the input indices"
            )
        )
    )

    private val txExtractionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = txExtractionSchema
                temperature = 0.1f
            }
        )
    }

    private val categorySuggestionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = categorySuggestionSchema
                temperature = 0.1f
            }
        )
    }

    private fun buildPrivacySafeProfileContext(
        profile: UserProfile,
        bufferStatus: BufferGateStatus
    ): String {
        val eligibility = if (profile.isUnder18) "Under 18 (Guided / Learning Mode - Education Only, NO investing)" else "18+ Eligible"
        val modes = buildList {
            if (profile.isUnder18) add("Guided / Learning Mode")
            if (profile.isStudentMode) add("Student Mode")
            if (profile.isIrregularIncome || profile.incomeType == IncomeType.IRREGULAR) add("Pay-Yourself-a-Salary Mode")
            if (profile.localOnlyMode) add("Local-Only Privacy Mode ON")
        }.joinToString(", ").ifBlank { "Standard Mode" }

        val bufferPct = (bufferStatus.progressFraction * 100).roundToInt()
        val bufferText = if (bufferStatus.isBufferMet) {
            "Buffer MET (${bufferPct}%+ of target)"
        } else {
            "Buffer NOT MET (${bufferPct}% of 1-month safety buffer target)"
        }

        val balanceInfo = if (profile.localOnlyMode) {
            "Exact balances & names withheld for privacy (Local-only mode). Debt status: ${if (profile.debts.isEmpty()) "None" else "Has ${profile.debts.size} debt item(s)"}, Goals count: ${profile.goals.size}"
        } else {
            "Monthly income baseline: ${profile.monthlyIncome} ${profile.currencyCode}, Debts: ${profile.debts.size}, Goals: ${profile.goals.size}"
        }

        return """
            SAVED USER PROFILE (computed by app code):
            - Eligibility: $eligibility
            - Life Stage: ${profile.userSituation} (Student / Early career / Family / Self-employed / Retired)
            - Income Type: ${profile.incomeType.label} (${profile.incomeType.description})
            - Active Modes: $modes
            - Emergency Buffer Status: $bufferText
            - Country & Currency: ${profile.country} (${profile.currencyCode})
            - Risk Comfort: ${profile.riskComfort.label}
            - Side-Hustle Hours: ${profile.sideIncomeHoursPerWeek} hrs/week
            - Context: $balanceInfo
        """.trimIndent()
    }

    suspend fun generateGrowthPlan(
        profile: UserProfile,
        envelopes: List<VirtualEnvelope> = emptyList()
    ): PlanUiState {
        currentPlanState = PlanUiState.Loading
        val bufferStatus = FinancialCalculator.calculateBufferGateStatus(profile, envelopes)
        val profileContext = buildPrivacySafeProfileContext(profile, bufferStatus)

        val gateInstruction = when {
            profile.isUnder18 -> "USER IS UNDER 18 (GUIDED MODE): Education only. DO NOT generate any 'Invest' or 'Trading' recommendations. Focus strictly on Protect, Save, and Earn."
            !bufferStatus.isBufferMet -> "SAFETY BUFFER NOT MET: Redirect to saving first! DO NOT include an 'Invest' pillar encouraging investing yet; explain clearly why building the 1-month emergency buffer comes first."
            else -> "SAFETY BUFFER IS MET: You may include general educational 'Invest' asset-type concepts (broad index funds, SIP, gold) with a one-sentence risk note each, WITHOUT naming specific products or telling the user to buy."
        }

        val prompt = """
            You are GrowMint AI, a friendly, plain-English personal finance coach.
            $profileContext

            MANDATORY RULES:
            - Tailor every strategy explicitly to the user's Life Stage (${profile.userSituation}) AND Income Type (${profile.incomeType.label}).
            - Educational guidance only. Do not claim to be a licensed financial advisor.
            - All numbers belong to the app code; only explain concepts and pre-computed status. Never invent balances or returns.
            - NEVER name specific financial products, brands, apps, or tickers (e.g. never say VOO, VTI, Nifty ETF names, or brand funds) and NEVER tell the user to "buy" anything.
            - Describe asset types generally (e.g. "broad index funds", "systematic investment plans (SIPs)", "gold", "high-yield savings") and include a clear one-sentence risk note for each investing asset type.
            - NEVER promise returns. NEVER suggest borrowing to invest.
            - $gateInstruction
            - Only legal, regulated concepts. If unsure about a local rule in ${profile.country}, mark '[uncertain]'.
        """.trimIndent()

        try {
            val result = withTimeout(30_000L) {
                val response = planModel.generateContent(prompt)
                val rawText = response.text
                if (rawText.isNullOrBlank()) {
                    throw IllegalStateException("Model returned empty text.")
                }
                parseGrowthPlanJson(rawText, profile, bufferStatus)
            }
            currentPlanState = result
            return result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Live Gemini plan call unavailable ($e). Using deterministic code plan.", e)
            val enginePlan = generateEngineeredGrowthPlan(profile, envelopes)
            currentPlanState = enginePlan
            return enginePlan
        }
    }

    private fun parseGrowthPlanJson(
        rawJson: String,
        profile: UserProfile,
        bufferStatus: BufferGateStatus
    ): PlanUiState {
        var clean = rawJson.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json").substringBeforeLast("```").trim()
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```").substringBeforeLast("```").trim()
        }

        var summary = "Here is your educational financial roadmap tailored to your ${profile.userSituation} profile in ${profile.country}."
        val items = mutableListOf<GrowthPlanItem>()

        if (clean.startsWith("[")) {
            val arr = JSONArray(clean)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                items.add(parsePlanItem(obj))
            }
        } else {
            val root = JSONObject(clean)
            summary = root.optString("summary", summary)
            val itemsArr = root.optJSONArray("items")
                ?: root.optJSONArray("plan")
                ?: root.optJSONArray("recommendations")
                ?: root.optJSONArray("strategies")
            if (itemsArr != null) {
                for (i in 0 until itemsArr.length()) {
                    val obj = itemsArr.optJSONObject(i) ?: continue
                    items.add(parsePlanItem(obj))
                }
            }
        }

        // Code-enforced Safety Gate & Guided Mode filter (never rely solely on LLM compliance!)
        val filteredItems = items.filter { item ->
            val p = item.pillar.lowercase()
            when {
                profile.isUnder18 -> p != "invest" && p != "trading"
                !bufferStatus.isBufferMet -> p != "invest"
                else -> true
            }
        }

        return if (filteredItems.isNotEmpty()) {
            PlanUiState.Success(summary, filteredItems, isOfflineEngine = false)
        } else {
            generateEngineeredGrowthPlan(profile, emptyList(), bufferStatus)
        }
    }

    private fun parsePlanItem(obj: JSONObject): GrowthPlanItem {
        val countryNotes = obj.optString("countryNotes", "")
        val isUncertain = countryNotes.contains("[uncertain]", ignoreCase = true)
        return GrowthPlanItem(
            pillar = obj.optString("pillar", "Protect"),
            title = obj.optString("title", "Financial Habit"),
            description = obj.optString("description", ""),
            minimumToStart = obj.optString("minimumToStart", "Any amount"),
            timeHorizon = obj.optString("timeHorizon", "Medium-term"),
            riskLevel = obj.optString("riskLevel", "Low"),
            realisticRange = obj.optString("realisticRange", "Illustrative only; not guaranteed"),
            countryNotes = countryNotes,
            isUncertain = isUncertain,
            actionStep = obj.optString("actionStep", "")
        )
    }

    fun generateEngineeredGrowthPlan(
        profile: UserProfile,
        envelopes: List<VirtualEnvelope> = emptyList(),
        precomputedGate: BufferGateStatus? = null
    ): PlanUiState.Success {
        val currency = profile.currencyCode
        val country = profile.country
        val bufferStatus = precomputedGate ?: FinancialCalculator.calculateBufferGateStatus(profile, envelopes)
        val totalDebt = profile.debts.sumOf { it.totalAmount }
        val hasDebts = profile.debts.isNotEmpty() && totalDebt > 0

        val items = mutableListOf<GrowthPlanItem>()

        // 1. PROTECT (Emergency Buffer & Debts)
        val bufferTargetStr = CurrenciesAndCountries.format(bufferStatus.targetAmount, currency)
        val currentBufStr = CurrenciesAndCountries.format(bufferStatus.currentEmergencyAmount, currency)
        items.add(
            GrowthPlanItem(
                pillar = "Protect",
                title = "Build Your Basic Safety Buffer ($bufferTargetStr)",
                description = "Your first milestone is holding 1 month of essential spending ($bufferTargetStr) in safe, accessible cash. Right now you have $currentBufStr (${(bufferStatus.progressFraction * 100).roundToInt()}% of target). Having this cushion keeps unexpected bills from forcing you into debt.",
                minimumToStart = CurrenciesAndCountries.format(10.0, currency),
                timeHorizon = "Immediate Priority",
                riskLevel = "Very Low",
                realisticRange = "Protects against surprise bills & income gaps",
                countryNotes = "Keep emergency funds in a liquid, regulated savings account in $country.",
                isUncertain = false,
                actionStep = "Direct extra surplus and savings splits to Emergency Safety Net until you reach $bufferTargetStr."
            )
        )

        if (hasDebts) {
            val highestDebt = profile.debts.maxByOrNull { it.interestRate }
            items.add(
                GrowthPlanItem(
                    pillar = "Protect",
                    title = "Pay Down High-Interest Debt First",
                    description = "Focus any extra cash on your highest-interest balance (${highestDebt?.interestRate ?: 18.0}% APR) while making minimum payments on the rest. Reducing expensive debt saves guaranteed interest charges without taking market risk.",
                    minimumToStart = CurrenciesAndCountries.format(highestDebt?.minimumPayment ?: 25.0, currency),
                    timeHorizon = "Short to Medium",
                    riskLevel = "Very Low",
                    realisticRange = "Avoids ongoing interest fees",
                    countryNotes = "Never borrow money to invest.",
                    isUncertain = false,
                    actionStep = "Check your debt list and set a steady monthly payoff target."
                )
            )
        }

        // 2. SAVE (Tailored to BOTH Life Stage + Income Type)
        val lifeStageFocus = when (profile.userSituation) {
            LifeStage.STUDENT.label -> "Plan around semester expenses, exam fees, and campus budgets using your Goal Planner."
            LifeStage.FAMILY.label -> "Coordinate shared household bills, dependents' needs, and target multi-month safety reserves in your Goal Planner."
            LifeStage.SELF_EMPLOYED.label -> "Separate business expenses from take-home pay and set aside estimated taxes every month."
            LifeStage.RETIRED.label -> "Focus on predictable monthly cashflow, inflation awareness, and protecting principal against scams."
            else -> "Build strong early-career habits: automate savings before lifestyle upgrades and track Net Worth monthly."
        }

        when {
            profile.incomeType == IncomeType.IRREGULAR || profile.isIrregularIncome -> {
                items.add(
                    GrowthPlanItem(
                        pillar = "Save",
                        title = "Pay-Yourself-a-Salary Holding Pot (${profile.userSituation})",
                        description = "Because your income type is Irregular, collect variable payouts inside your Holding Pot first and release a steady salary (${CurrenciesAndCountries.format(profile.monthlySalaryTarget, currency)}). $lifeStageFocus",
                        minimumToStart = CurrenciesAndCountries.format(50.0, currency),
                        timeHorizon = "Monthly Habit",
                        riskLevel = "Very Low",
                        realisticRange = "Smooths irregular cashflow",
                        countryNotes = "Keep tax reserve aside if self-employed in $country.",
                        isUncertain = false,
                        actionStep = "Route new payouts into your Holding Pot on the Home dashboard."
                    )
                )
            }
            profile.incomeType == IncomeType.ALLOWANCE -> {
                items.add(
                    GrowthPlanItem(
                        pillar = "Save",
                        title = "Make Every Allowance / Stipend Stretch (${profile.userSituation})",
                        description = "With a fixed Allowance or stipend, divide funds immediately into bills, goals, and fun money using Auto-Split before spending. $lifeStageFocus",
                        minimumToStart = CurrenciesAndCountries.format(5.0, currency),
                        timeHorizon = "Short (0-2 yrs)",
                        riskLevel = "Very Low",
                        realisticRange = "Builds lifelong budgeting discipline",
                        countryNotes = "Compare no-fee student or starter bank accounts in $country.",
                        isUncertain = false,
                        actionStep = "Set up a recurring Goal Planner target for upcoming expenses."
                    )
                )
            }
            profile.incomeType == IncomeType.NONE -> {
                items.add(
                    GrowthPlanItem(
                        pillar = "Save",
                        title = "Cash Runway & Expense Control (${profile.userSituation})",
                        description = "While income type is None, your priority is extending how many months your savings last by auditing recurring leaks and tracking deadlines. $lifeStageFocus",
                        minimumToStart = CurrenciesAndCountries.format(0.0, currency),
                        timeHorizon = "Immediate",
                        riskLevel = "Very Low",
                        realisticRange = "Preserves existing cash runway",
                        countryNotes = "Check public or community benefits/scholarships if eligible in $country. [uncertain]",
                        isUncertain = true,
                        actionStep = "Review your Deadline Calendar and Net Worth cash total weekly."
                    )
                )
            }
            else -> {
                items.add(
                    GrowthPlanItem(
                        pillar = "Save",
                        title = "Dedicated Savings & Goal Buckets (${profile.userSituation})",
                        description = "With Steady income, automate your Paycheck Auto-Divider on payday so savings and short-term goals (1–3 yrs) are funded before discretionary spending. $lifeStageFocus",
                        minimumToStart = CurrenciesAndCountries.format(10.0, currency),
                        timeHorizon = "Short (0-3 yrs)",
                        riskLevel = "Very Low",
                        realisticRange = "Rates depend on central bank rates; not guaranteed to beat inflation",
                        countryNotes = "Check deposit insurance limits in $country. [uncertain]",
                        isUncertain = true,
                        actionStep = "Set up automatic paycheck splitting in GrowMint's Auto-Split tab."
                    )
                )
            }
        }

        // 3. INVEST (Strictly gated: NEVER for Guided Mode or when buffer is unmet!)
        if (!profile.isUnder18 && bufferStatus.isBufferMet) {
            items.add(
                GrowthPlanItem(
                    pillar = "Invest",
                    title = "Broad Index Funds & Systematic Investment Plans (SIPs)",
                    description = "Broad index funds spread money across hundreds of companies at once, and SIPs let you contribute a steady amount monthly for long-term (5+ year) goals. Risk note: Stock markets fluctuate and can drop 20%–35% in bad years, so returns are never guaranteed.",
                    minimumToStart = CurrenciesAndCountries.format(25.0, currency),
                    timeHorizon = "Long (5+ yrs)",
                    riskLevel = "Moderate to High",
                    realisticRange = "Illustrative long-term growth only; losses can occur in down years",
                    countryNotes = "Review tax-advantaged long-term accounts available in $country.",
                    isUncertain = false,
                    actionStep = "Test poor, typical, and good outcomes in GrowMint's What-If Simulator before deciding."
                )
            )
            items.add(
                GrowthPlanItem(
                    pillar = "Invest",
                    title = "Gold as a Small Portfolio Diversifier",
                    description = "Some long-term savers learn about holding a small slice (around 5%–10%) in gold-backed instruments to diversify away from stocks during inflation. Risk note: Gold prices can swing sideways or drop for years and do not pay ongoing interest.",
                    minimumToStart = CurrenciesAndCountries.format(20.0, currency),
                    timeHorizon = "Long (5+ yrs)",
                    riskLevel = "Moderate",
                    realisticRange = "Variable market price; no guaranteed return",
                    countryNotes = "Check regulated gold savings options in $country.",
                    isUncertain = false,
                    actionStep = "Read the Inflation & Risk 2-minute lessons in GrowMint."
                )
            )
        }

        // 4. TRADING (Education warning — hidden in Guided Mode)
        if (!profile.isUnder18) {
            items.add(
                GrowthPlanItem(
                    pillar = "Trading",
                    title = "Practice Trading Sandbox (Education Only)",
                    description = "Short-term day trading carries severe risk: studies show 70% to 90%+ of retail traders lose money. Use GrowMint's Paper-Trading Sandbox purely to learn how markets move with play money.",
                    minimumToStart = "0 (Play money only)",
                    timeHorizon = "Educational Sandbox",
                    riskLevel = "Very High",
                    realisticRange = "High risk of loss in real markets",
                    countryNotes = "Never risk bill or emergency money on short-term speculation.",
                    isUncertain = false,
                    actionStep = "Explore the Practice tab strictly with virtual funds."
                )
            )
        }

        // 5. EARN
        val hours = profile.sideIncomeHoursPerWeek
        items.add(
            GrowthPlanItem(
                pillar = "Earn",
                title = "Track Side-Hustle Profit & Margins ($hours hrs/week)",
                description = "Track both what you earn AND your tool, travel, or material costs every month so you know your real net profit. Reinvest a slice into your safety buffer first.",
                minimumToStart = CurrenciesAndCountries.format(0.0, currency),
                timeHorizon = "Immediate",
                riskLevel = "Low",
                realisticRange = "Depends on time, skills, and local demand",
                countryNotes = "Log receipts and set aside estimated tax on side income in $country.",
                isUncertain = false,
                actionStep = "Open the Side-Hustle Profit Tracker or Income Ideas Lab to log your first month."
            )
        )

        val summary = when {
            profile.isUnder18 -> "Guided / Learning Mode (Under 18 • ${profile.userSituation} • ${profile.incomeType.label} income): Education-only roadmap. Investing features stay hidden while you learn budgeting, saving, scam defense, and tracking earnings."
            !bufferStatus.isBufferMet -> "Safety-First Mode (${profile.userSituation} • ${profile.incomeType.label} income): Your 1-month emergency buffer is at ${(bufferStatus.progressFraction * 100).roundToInt()}%. Focus on building your safety buffer first before unlocking investing concepts."
            else -> "Your 1-month emergency buffer is met! Here is your ${profile.userSituation} (${profile.incomeType.label} income) roadmap across Protect, Save, general asset concepts, and Earn."
        }

        return PlanUiState.Success(summary, items, isOfflineEngine = true)
    }

    fun streamCoachChat(
        userMessage: String,
        profile: UserProfile,
        envelopes: List<VirtualEnvelope> = emptyList(),
        historySnippet: String = ""
    ): Flow<String> = flow {
        val bufferStatus = FinancialCalculator.calculateBufferGateStatus(profile, envelopes)
        val profileContext = buildPrivacySafeProfileContext(profile, bufferStatus)

        val sanitizedQuestion = if (profile.localOnlyMode) {
            // Strip obvious email/account-like sequences if user accidentally pasted them
            userMessage.replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[redacted-email]")
        } else {
            userMessage
        }

        val systemPrompt = """
            You are GrowMint AI, a calm, plain-language personal finance educator.
            Read the saved profile carefully before answering:
            $profileContext

            STRICT RULES:
            1. Educational guidance only. Explicitly do not claim to be a licensed financial advisor.
            2. Do NOT compute or invent the user's financial numbers; only explain pre-computed status or general math principles.
            3. Do NOT name any specific financial product, fund brand, app, or stock ticker, and NEVER tell the user to "buy" anything.
            4. Describe asset types generally (index funds, SIP, gold, high-yield savings) and always include a one-sentence risk note for investing types.
            5. NEVER promise returns. NEVER suggest borrowing money to invest.
            6. ${if (profile.isUnder18) "User is Under 18 (Guided Mode): Education-only! DO NOT recommend or explain how to start investing real money." else if (!bufferStatus.isBufferMet) "Emergency buffer is NOT met yet: Redirect the user to saving and building their 1-month emergency buffer first before any investing." else "Emergency buffer is met: Keep guidance educational, balanced, and cautious."}
            7. If unsure of local rules in ${profile.country}, mark '[uncertain]'.
        """.trimIndent()

        val fullPrompt = "$systemPrompt\n\nUser Question: $sanitizedQuestion"
        try {
            withTimeout(30_000L) {
                chatModel.generateContentStream(fullPrompt).collect { chunk ->
                    chunk.text?.let { emit(it) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Streaming chat unavailable (${e.message}). Using safe educational fallback.")
            emit(generateHelpfulChatFallback(userMessage, profile, bufferStatus))
        }
    }

    private fun generateHelpfulChatFallback(
        question: String,
        profile: UserProfile,
        bufferStatus: BufferGateStatus
    ): String {
        val q = question.lowercase()
        val c = profile.country

        if (profile.isUnder18) {
            return """
                You are in **Guided Mode (Under 18)**, which keeps things strictly educational and focused on building lifelong money habits without investing pressure:
                
                1. **Safety First**: Practice keeping a 1-month cash buffer for unexpected costs or school expenses.
                2. **Track Every Dollar**: Separate needs, savings, and fun money using the Paycheck Auto-Divider.
                3. **Scam Defense**: Never share OTPs, PINs, or passwords, and be skeptical of anyone promising fast money.
                
                *(Educational guidance only, not licensed financial advice.)*
            """.trimIndent()
        }

        if (!bufferStatus.isBufferMet && (q.contains("invest") || q.contains("sip") || q.contains("stock") || q.contains("index") || q.contains("gold"))) {
            val pct = (bufferStatus.progressFraction * 100).roundToInt()
            return """
                **Build Your Safety Buffer First ($pct% there)**
                
                Right now your 1-month emergency buffer is not filled yet. Before exploring investing, redirect extra cash to your **Emergency Safety Net** envelope so a surprise expense won't force you into high-interest debt or make you sell investments at a loss.
                
                Once your safety buffer hits 100%, GrowMint will unlock educational guidance on general long-term asset types like broad index funds, SIPs, and gold.
                
                *(Educational guidance only, not licensed financial advice.)*
            """.trimIndent()
        }

        return when {
            q.contains("debt") || q.contains("loan") -> """
                Here is a simple way to tackle existing debt safely:
                
                1. Keep making minimum payments on every balance so you avoid late fees.
                2. Put extra cash toward the balance with the highest interest rate first to save the most in fees.
                3. Never borrow money to invest—the debt interest is guaranteed, while market returns are never guaranteed.
                
                *(Educational guidance only, not licensed financial advice.)*
            """.trimIndent()

            q.contains("invest") || q.contains("sip") || q.contains("index") || q.contains("gold") -> """
                Here is an educational overview of general asset types (not a recommendation to buy any specific product):
                
                - **Broad Index Funds / SIPs**: Pooled baskets of many companies that people often fund steadily month by month (SIP) for 5+ year goals. *Risk note: Markets can drop 20%–35% in bad years and returns are never guaranteed.*
                - **Gold**: A traditional store of value that some savers hold as a small slice for diversification. *Risk note: Gold prices can swing or stay flat for years and generate no ongoing income.*
                - **High-Yield Savings / Government Notes**: Safer cash options suited for money needed within 1–3 years. *Risk note: Interest rates change and may lag behind rising inflation.*
                
                Try the **What-If Simulator** in GrowMint to view poor, typical, and good outcomes side by side.
                *(Educational guidance only, not licensed financial advice.)*
            """.trimIndent()

            q.contains("scam") || q.contains("upi") || q.contains("otp") -> """
                Quick scam safety checklist:
                
                - You **never** need to enter a PIN, share an OTP, or pay a fee to receive money or get a legitimate job.
                - Treat urgent "KYC expired" or "guaranteed return group" messages as suspicious.
                - Use GrowMint's **Scam Checker** to paste a suspicious message and inspect its red flags.
                
                *(Educational guidance only, not licensed financial advice.)*
            """.trimIndent()

            else -> {
                val stageTip = when (profile.userSituation) {
                    LifeStage.STUDENT.label -> "Track semester costs and build a reliable starter cash buffer."
                    LifeStage.FAMILY.label -> "Coordinate family goals in Goal Planner and protect dependents with a solid cushion."
                    LifeStage.SELF_EMPLOYED.label -> "Keep side-hustle expenses and tax reserves separate from personal spending."
                    LifeStage.RETIRED.label -> "Protect principal, watch monthly cashflow, and stay alert for scams."
                    else -> "Automate savings early in your career and review Net Worth every month."
                }
                val incomeTip = when (profile.incomeType) {
                    IncomeType.IRREGULAR -> "With irregular income, route payouts to your Holding Pot and pay yourself a steady monthly salary."
                    IncomeType.ALLOWANCE -> "With allowance/stipend income, split into essentials, goals, and fun right when funds arrive."
                    IncomeType.NONE -> "With no current regular income, stretch existing cash runway and pause non-essential renewals."
                    IncomeType.STEADY -> "With steady income, schedule automatic paycheck splitting right after payday."
                }
                """
                Here is a steady plan for your **${profile.userSituation}** stage (**${profile.incomeType.label}** income) in $c:
                
                1. **Emergency Buffer**: ${if (bufferStatus.isBufferMet) "Met! Keep it safe in liquid savings." else "At ${(bufferStatus.progressFraction * 100).roundToInt()}%—prioritize reaching 1 month of spending first."}
                2. **Life Stage & Cashflow**: $stageTip $incomeTip
                3. **Monthly Report & Goals**: Use the Goal Planner, Debt Payoff Planner, and Monthly Improvement Report to stay on track.
                
                *(Educational guidance only, not licensed financial advice. Never borrow to invest.)*
                """.trimIndent()
            }
        }
    }

    // === FEATURE 5: SCAM CHECKER UPGRADE ===
    suspend fun analyzeScamMessage(
        userText: String,
        profile: UserProfile
    ): ScamCheckResult {
        val isInr = profile.currencyCode.equals("INR", ignoreCase = true) ||
            profile.country.equals("India", ignoreCase = true)

        val regionalGuidance = if (isInr) {
            """
            Because the user's currency/region is INR / India, pay special attention to:
            - Fake UPI collect requests (asking to enter UPI PIN to *receive* money or a refund)
            - KYC-expiry calls or SMS threatening account block with suspicious links
            - "Pay to receive money" / lottery / cashback processing-fee traps
            - Fake job offers demanding registration, training, or laptop deposit fees
            - Fake WhatsApp / Telegram stock, options, or crypto VIP investment groups promising guaranteed returns
            """.trimIndent()
        } else {
            """
            Check for common scam tactics relevant to ${profile.country} (${profile.currencyCode}):
            - Phishing links claiming bank/tax/delivery suspension
            - Advance-fee traps ("pay fee to receive grant, prize, or job")
            - Requests for OTP, PIN, remote screen-share apps, or passwords
            - Guaranteed-return investment/crypto messaging groups
            """.trimIndent()
        }

        val sanitizedInput = if (profile.localOnlyMode) {
            userText.take(1500)
        } else {
            userText.take(2000)
        }

        val prompt = """
            Analyze the following message or situation for scam warning signs.
            $regionalGuidance

            CRITICAL RULES:
            - Do NOT claim 100% certainty. Use cautious language ("appears", "warning signs suggest").
            - Verdict must be strictly one of: "Likely scam", "Suspicious", or "Looks fine".
            - List concrete red flags found.
            - List practical next steps (always emphasize: Never share OTP, PIN, or passwords, and verify via official apps/numbers).

            Message/Situation to check:
            "$sanitizedInput"
        """.trimIndent()

        return try {
            withTimeout(30_000L) {
                val response = scamModel.generateContent(prompt)
                val raw = response.text ?: throw IllegalStateException("Empty scam response")
                val json = JSONObject(raw)
                val verdictStr = json.optString("verdict", "Suspicious")
                val verdict = when {
                    verdictStr.equals("Likely scam", ignoreCase = true) -> ScamVerdict.LIKELY_SCAM
                    verdictStr.equals("Looks fine", ignoreCase = true) -> ScamVerdict.LOOKS_FINE
                    else -> ScamVerdict.SUSPICIOUS
                }
                val flagsArr = json.optJSONArray("redFlags") ?: JSONArray()
                val stepsArr = json.optJSONArray("nextSteps") ?: JSONArray()
                val flags = (0 until flagsArr.length()).map { flagsArr.getString(it) }
                val steps = (0 until stepsArr.length()).map { stepsArr.getString(it) }
                ScamCheckResult(
                    verdict = verdict,
                    redFlags = flags.ifEmpty { listOf("Check sender identity carefully.") },
                    nextSteps = steps.ifEmpty {
                        listOf(
                            "Never share OTP, PIN, or passwords.",
                            "Verify independently using the official app or website."
                        )
                    }
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Scam AI check unavailable ($e). Running deterministic heuristic scam check.")
            runOfflineScamHeuristics(sanitizedInput, isInr)
        }
    }

    private fun runOfflineScamHeuristics(input: String, isInr: Boolean): ScamCheckResult {
        val lower = input.lowercase()
        val redFlags = mutableListOf<String>()

        if (lower.contains("otp") || lower.contains("pin") || lower.contains("password") || lower.contains("cvv")) {
            redFlags.add("Asks for or triggers a PIN, OTP, CVV, or password — legitimate services never ask for these.")
        }
        if (lower.contains("upi") && (lower.contains("receive") || lower.contains("refund") || lower.contains("cashback") || lower.contains("scan") || lower.contains("collect"))) {
            redFlags.add("Possible fake UPI collect request or QR trap: you NEVER enter your UPI PIN to receive money.")
        }
        if (lower.contains("kyc") || lower.contains("blocked") || lower.contains("suspended") || lower.contains("2 hours") || lower.contains("24 hours") || lower.contains("urgent")) {
            redFlags.add("Creates artificial urgency or KYC-expiry panic to rush you into clicking a link or calling a fake helpline.")
        }
        if (lower.contains("registration fee") || lower.contains("processing fee") || lower.contains("pay") && (lower.contains("receive") || lower.contains("job") || lower.contains("prize") || lower.contains("lottery"))) {
            redFlags.add("Requests an upfront payment to unlock a job, prize, or payout ('pay to receive money' trap).")
        }
        if (lower.contains("guaranteed") || lower.contains("double") || lower.contains("300%") || lower.contains("vip group") || lower.contains("telegram") || lower.contains("whatsapp group")) {
            redFlags.add("Promises guaranteed/huge returns or invites you to a private trading/investment chat group.")
        }
        if (lower.contains("http://") || lower.contains("bit.ly") || lower.contains("tinyurl") || lower.contains("apk") || lower.contains("anydesk") || lower.contains("teamviewer")) {
            redFlags.add("Contains shortened/unverified links or asks to install APK/remote-access tools.")
        }

        val verdict = when {
            redFlags.size >= 2 -> ScamVerdict.LIKELY_SCAM
            redFlags.size == 1 -> ScamVerdict.SUSPICIOUS
            else -> ScamVerdict.LOOKS_FINE
        }

        if (redFlags.isEmpty()) {
            redFlags.add("No obvious common scam keywords matched, though subtle social-engineering tricks may still exist.")
        }

        val nextSteps = buildList {
            add("Never share OTP, PIN, or passwords with anyone, even if they claim to be bank support or police.")
            if (verdict != ScamVerdict.LOOKS_FINE) {
                add("Do not click links, decline unknown payment/collect requests, and block the sender.")
                add("If you want to verify, open your official banking/payment app directly—never use phone numbers inside the message.")
                if (isInr) {
                    add("In India, if money was lost or attempted, report promptly at cybercrime.gov.in or helpline 1930.")
                }
            } else {
                add("Stay cautious anyway: double-check the sender domain or official app before taking action.")
            }
        }

        return ScamCheckResult(
            verdict = verdict,
            redFlags = redFlags,
            nextSteps = nextSteps
        )
    }

    // === EFFORTLESS TRANSACTION EXTRACTION & CATEGORY SUGGESTION ===

    suspend fun extractTransactionsFromText(
        rawPastedText: String,
        profile: UserProfile,
        rules: List<MerchantCategoryRule>,
        existingTransactions: List<LedgerTransaction>,
        datePreference: DateFormatPreference = DateFormatPreference.AUTO
    ): ExtractionBatchResult {
        val sanitized = TransactionImportEngine.sanitizeForAi(rawPastedText).take(6000)
        if (sanitized.isBlank()) {
            return ExtractionBatchResult(
                candidates = emptyList(),
                foundCount = 0,
                skippedCount = 0,
                hasAmbiguousDates = false,
                sourceType = "PASTE",
                errorMessage = "Paste some bank SMS, UPI messages, or statement lines first, or add an entry manually."
            )
        }

        val allowedCategories = DefaultTransactionCategories.ALL.joinToString(", ")
        val prompt = """
            Extract financial transactions from the sanitized text below (bank SMS, UPI alerts, or statement lines).
            Return structured JSON only matching the schema.

            RULES:
            - Extract each distinct transaction with:
              * date: preserve the exact date written (or today's date if missing)
              * amount: positive number only (no currency symbol)
              * type: strictly "income" (credited/received/salary/refund) or "expense" (debited/spent/paid)
              * merchant: short clean merchant/payee/sender name
              * suggestedCategory: choose the best match from [$allowedCategories]
            - Skip OTP alerts, promotional messages, or balance-only alerts with no transaction.
            - Never invent transactions not present in the text.
            - User's currency context: ${profile.currencyCode} (${profile.country}).

            Sanitized text:
            $sanitized
        """.trimIndent()

        return try {
            val rawJson = withTimeout(45_000L) {
                val response = txExtractionModel.generateContent(prompt)
                response.text ?: throw IllegalStateException("Empty AI response")
            }
            TransactionImportEngine.validateAndBuildCandidatesFromAiJson(
                rawJson = rawJson,
                existingTransactions = existingTransactions,
                rules = rules,
                datePreference = datePreference
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "AI transaction extraction failed ($e)")
            ExtractionBatchResult(
                candidates = emptyList(),
                foundCount = 0,
                skippedCount = 0,
                hasAmbiguousDates = false,
                sourceType = "PASTE",
                errorMessage = "We couldn't extract those messages right now (${e.localizedMessage ?: "connection unavailable"}). You can try again or add entries manually."
            )
        }
    }

    suspend fun suggestCategoriesForMerchants(
        indexedMerchants: List<Pair<Int, String>>,
        profile: UserProfile
    ): Map<Int, String> {
        if (indexedMerchants.isEmpty()) return emptyMap()
        val sanitizedList = indexedMerchants.take(40).map { (idx, desc) ->
            idx to TransactionImportEngine.sanitizeForAi(desc).take(80)
        }
        val listLines = sanitizedList.joinToString("\n") { (idx, desc) -> "$idx: $desc" }
        val allowedCategories = DefaultTransactionCategories.ALL.joinToString(", ")
        val prompt = """
            Assign the best spending or income category from [$allowedCategories] for each numbered merchant description.
            Return structured JSON only matching the schema.

            Currency/Region context: ${profile.currencyCode} (${profile.country})
            Merchants:
            $listLines
        """.trimIndent()

        return try {
            val rawJson = withTimeout(30_000L) {
                val response = categorySuggestionModel.generateContent(prompt)
                response.text ?: throw IllegalStateException("Empty category response")
            }
            var clean = rawJson.trim()
            if (clean.startsWith("```json")) {
                clean = clean.removePrefix("```json").substringBeforeLast("```").trim()
            } else if (clean.startsWith("```")) {
                clean = clean.removePrefix("```").substringBeforeLast("```").trim()
            }
            val root = JSONObject(clean)
            val arr = root.optJSONArray("suggestions") ?: JSONArray()
            val map = mutableMapOf<Int, String>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val idx = obj.optInt("index", -1)
                val cat = obj.optString("category", "").trim()
                if (idx >= 0 && cat.isNotBlank()) {
                    map[idx] = TransactionImportEngine.normalizeCategoryName(cat, "EXPENSE")
                }
            }
            map
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "AI CSV category suggestion unavailable ($e)")
            emptyMap()
        }
    }

    suspend fun explainActionPlainLanguage(
        action: MonthlyActionItem,
        profile: UserProfile
    ): String {
        val trace = action.traceability
        val fallback = if (trace != null) {
            "Rule ${trace.ruleId} triggered because ${trace.ruleDescription.replaceFirstChar { it.lowercase() }} ${trace.expectedScoreExplanation} (Educational guidance only.)"
        } else {
            "${action.what} Target: ${action.howMuch}. (Educational guidance only.)"
        }

        if (trace == null) return fallback

        val factsText = trace.triggeringNumbers.joinToString("; ") { "${it.label}: ${it.valueFormatted}" }
        val prompt = """
            You are GrowMint Coach. Write 1 to 2 concise, calm, plain-English sentences explaining why this recommended action makes sense for a ${profile.userSituation} (${profile.incomeType.label} income).

            STRICT RULES:
            - All numbers below were computed by app code. Do NOT invent, recalculate, round differently, or estimate any new figures.
            - Explain the cause-and-effect in everyday words.
            - End with "(Educational guidance only.)"

            Code Facts:
            - Action: ${action.title} (${action.howMuch}, ${action.byWhen})
            - Rule ID: ${trace.ruleId} (${trace.ruleTitle})
            - Rule Logic: ${trace.ruleDescription}
            - Inputs: $factsText
            - Expected Score Effect: ${trace.expectedScoreExplanation}
        """.trimIndent()

        return try {
            val raw = withTimeout(15_000L) {
                chatModel.generateContent(prompt).text?.trim()
            }
            if (!raw.isNullOrBlank()) raw else fallback
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Plain-English action explanation fallback ($e)")
            fallback
        }
    }
}
