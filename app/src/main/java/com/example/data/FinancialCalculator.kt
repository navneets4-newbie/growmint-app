package com.example.data

import com.example.model.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Deterministic financial math engine.
 * Core Rule: ALL calculations (splits, buffers, profit, projections, balances, streaks, scores)
 * live strictly in code here — NEVER generated or recomputed by the AI.
 */
object FinancialCalculator {

    fun calculateTypicalMonthlySpending(profile: UserProfile): Double {
        val sum = profile.expenses.sumOf { it.amount } + profile.debts.sumOf { it.minimumPayment }
        return if (sum > 0.0) sum else 1000.0
    }

    fun calculateBufferGateStatus(
        profile: UserProfile,
        envelopes: List<VirtualEnvelope>
    ): BufferGateStatus {
        val typicalSpending = calculateTypicalMonthlySpending(profile)
        val defaultTarget = typicalSpending * max(0.5, profile.emergencyBufferMonthsTarget)
        val target = profile.customBufferTargetAmount?.takeIf { it > 0.0 } ?: defaultTarget
        val currentEmergency = envelopes.find { it.type == EnvelopeType.EMERGENCY_FUND }?.balance ?: 0.0
        val progress = if (target > 0.0) (currentEmergency / target).coerceIn(0.0, 1.0).toFloat() else 0f
        val remaining = max(0.0, target - currentEmergency)
        val isMet = currentEmergency >= target && target > 0.0
        val unlocked = !profile.isUnder18 && isMet

        return BufferGateStatus(
            currentEmergencyAmount = currentEmergency,
            targetAmount = target,
            typicalMonthlySpending = typicalSpending,
            targetMonths = if (typicalSpending > 0.0) target / typicalSpending else profile.emergencyBufferMonthsTarget,
            progressFraction = progress,
            remainingToTarget = remaining,
            isBufferMet = isMet,
            isGuidedModeUnder18 = profile.isUnder18,
            isInvestingUnlocked = unlocked
        )
    }

    fun calculateHoldingPotRunwayMonths(potBalance: Double, monthlySalaryTarget: Double): Double {
        if (monthlySalaryTarget <= 0.0) return 0.0
        return max(0.0, potBalance) / monthlySalaryTarget
    }

    fun calculateSideHustleReport(entries: List<SideHustleEntry>): SideHustleReport {
        if (entries.isEmpty()) {
            return SideHustleReport(
                totalIncome = 0.0,
                totalExpenses = 0.0,
                totalProfit = 0.0,
                overallMarginPercent = 0.0,
                monthlySummaries = emptyList(),
                bestMonth = null,
                worstMonth = null
            )
        }

        val totalIncome = entries.sumOf { it.income }
        val totalExpenses = entries.sumOf { it.expenses }
        val totalProfit = totalIncome - totalExpenses
        val overallMargin = if (totalIncome > 0.0) (totalProfit / totalIncome) * 100.0 else 0.0

        val monthlySummaries = entries
            .groupBy { it.monthKey }
            .map { (month, list) ->
                val mInc = list.sumOf { it.income }
                val mExp = list.sumOf { it.expenses }
                val mProf = mInc - mExp
                val mMargin = if (mInc > 0.0) (mProf / mInc) * 100.0 else 0.0
                MonthlyHustleSummary(
                    monthKey = month,
                    totalIncome = mInc,
                    totalExpenses = mExp,
                    netProfit = mProf,
                    marginPercent = mMargin
                )
            }
            .sortedByDescending { it.monthKey }

        val bestMonth = monthlySummaries.maxByOrNull { it.netProfit }
        val worstMonth = monthlySummaries.minByOrNull { it.netProfit }

        return SideHustleReport(
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            totalProfit = totalProfit,
            overallMarginPercent = overallMargin,
            monthlySummaries = monthlySummaries,
            bestMonth = bestMonth,
            worstMonth = worstMonth
        )
    }

    fun getUpcomingDeadlines(
        items: List<CalendarReminderItem>,
        nowMillis: Long = System.currentTimeMillis(),
        horizonDays: Int = 30
    ): List<UpcomingDeadlineView> {
        val todayStart = startOfDayMillis(nowMillis)
        val dayMs = 24L * 60L * 60L * 1000L

        return items.mapNotNull { item ->
            if (item.isPaid && item.repeat == RepeatFrequency.ONE_OFF) return@mapNotNull null

            val effectiveDue = computeEffectiveDueMillis(item, todayStart)
            val diffDays = ((startOfDayMillis(effectiveDue) - todayStart) / dayMs).toInt()

            if (diffDays in -3..horizonDays) {
                UpcomingDeadlineView(
                    item = item,
                    effectiveDueMillis = effectiveDue,
                    daysUntilDue = diffDays,
                    isDueSoon = diffDays <= 7
                )
            } else {
                null
            }
        }.sortedBy { it.daysUntilDue }
    }

    private fun computeEffectiveDueMillis(item: CalendarReminderItem, todayStart: Long): Long {
        if (item.repeat == RepeatFrequency.ONE_OFF) {
            return item.dueDateMillis
        }
        val cal = Calendar.getInstance().apply { timeInMillis = item.dueDateMillis }
        var guard = 0
        while (startOfDayMillis(cal.timeInMillis) < todayStart && guard < 120) {
            when (item.repeat) {
                RepeatFrequency.MONTHLY -> cal.add(Calendar.MONTH, 1)
                RepeatFrequency.QUARTERLY -> cal.add(Calendar.MONTH, 3)
                RepeatFrequency.YEARLY -> cal.add(Calendar.YEAR, 1)
                RepeatFrequency.ONE_OFF -> break
            }
            guard++
        }
        return cal.timeInMillis
    }

    private fun startOfDayMillis(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * SIP & Investment Reality Check:
     * Computes three outcomes side-by-side (poor / typical / good) + worst-year loss example.
     */
    fun calculateSipRealityCheck(
        monthlyContribution: Double,
        initialAmount: Double,
        years: Int,
        assetMix: String // "Equity Index / SIP", "Balanced Mix", "Gold / Conservative"
    ): SipSimulationReport {
        val safeMonthly = max(0.0, monthlyContribution)
        val safeInitial = max(0.0, initialAmount)
        val safeYears = years.coerceIn(1, 40)
        val totalMonths = safeYears * 12
        val totalInvested = safeInitial + (safeMonthly * totalMonths)

        val (poorRate, typicalRate, goodRate, worstYearDropPct) = when (assetMix) {
            "Balanced Mix" -> Quad(2.5, 6.5, 9.5, 18.0)
            "Gold / Conservative" -> Quad(1.5, 4.5, 7.0, 10.0)
            else -> Quad(3.0, 8.0, 12.0, 30.0) // Equity Index / SIP
        }

        val poorVal = compoundMonthlyFutureValue(safeInitial, safeMonthly, poorRate, totalMonths)
        val typicalVal = compoundMonthlyFutureValue(safeInitial, safeMonthly, typicalRate, totalMonths)
        val goodVal = compoundMonthlyFutureValue(safeInitial, safeMonthly, goodRate, totalMonths)

        val worstYearLoss = typicalVal * (worstYearDropPct / 100.0)

        return SipSimulationReport(
            monthlyContribution = safeMonthly,
            initialAmount = safeInitial,
            years = safeYears,
            totalInvested = totalInvested,
            poorCase = SipScenarioResult(
                label = "Poor Case",
                annualRatePercent = poorRate,
                finalValue = poorVal,
                totalInvested = totalInvested,
                netGain = poorVal - totalInvested
            ),
            typicalCase = SipScenarioResult(
                label = "Typical Case",
                annualRatePercent = typicalRate,
                finalValue = typicalVal,
                totalInvested = totalInvested,
                netGain = typicalVal - totalInvested
            ),
            goodCase = SipScenarioResult(
                label = "Good Case",
                annualRatePercent = goodRate,
                finalValue = goodVal,
                totalInvested = totalInvested,
                netGain = goodVal - totalInvested
            ),
            worstYearLossPercent = worstYearDropPct,
            worstYearLossAmountOnTypical = worstYearLoss
        )
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun compoundMonthlyFutureValue(
        principal: Double,
        monthlyDeposit: Double,
        annualRatePercent: Double,
        months: Int
    ): Double {
        if (months <= 0) return principal
        val monthlyRate = (annualRatePercent / 100.0) / 12.0
        if (monthlyRate <= 0.0) return principal + (monthlyDeposit * months)
        val factor = (1.0 + monthlyRate).pow(months.toDouble())
        val fvPrincipal = principal * factor
        val fvSeries = monthlyDeposit * ((factor - 1.0) / monthlyRate)
        return fvPrincipal + fvSeries
    }

    /**
     * Weekly Money Score (0-100) calculated in code from:
     * 1. Buffer progress (0-40 pts)
     * 2. Spending vs weekly limit (0-30 pts)
     * 3. Savings consistency (0-30 pts)
     */
    fun calculateWeeklyMoneyScore(
        profile: UserProfile,
        envelopes: List<VirtualEnvelope>,
        transactions: List<LedgerTransaction>,
        noSpendStreakDays: Int,
        transferHistory: List<TransferRecord>
    ): WeeklyMoneyScore {
        val bufferStatus = calculateBufferGateStatus(profile, envelopes)

        // 1. Buffer Progress (Max 40 pts)
        val bufferPoints = (bufferStatus.progressFraction * 40f).roundToInt().coerceIn(0, 40)

        // 2. Spending vs Weekly Limit (Max 30 pts)
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - (7L * 24L * 60L * 60L * 1000L)
        val weeklyLimit = bufferStatus.typicalMonthlySpending / 4.33
        val weeklySpent = transactions
            .filter { it.type == "EXPENSE" && it.timestamp >= sevenDaysAgo }
            .sumOf { it.amount }

        val spendPoints = when {
            weeklySpent == 0.0 -> 26 // No tracked overspend; slightly below 30 until active tracking
            weeklySpent <= weeklyLimit * 0.85 -> 30
            weeklySpent <= weeklyLimit -> 25
            weeklySpent <= weeklyLimit * 1.25 -> 16
            else -> 7
        }

        // 3. Savings Consistency (Max 30 pts)
        val thirtyDaysAgo = now - (30L * 24L * 60L * 60L * 1000L)
        val recentSplits = transferHistory.count { it.timestamp >= thirtyDaysAgo && !it.description.contains("(UNDONE)") }
        val streakBonus = min(15, noSpendStreakDays * 3)
        val splitBonus = min(15, recentSplits * 5)
        val consistencyPoints = (streakBonus + splitBonus).coerceIn(0, 30)

        val total = (bufferPoints + spendPoints + consistencyPoints).coerceIn(0, 100)
        val gradeLabel = when {
            total >= 85 -> "Strong"
            total >= 65 -> "Steady"
            total >= 45 -> "Building"
            else -> "Starting Out"
        }

        val summary = when {
            !bufferStatus.isBufferMet -> "Focus on reaching your 1-month emergency buffer first (${(bufferStatus.progressFraction * 100).roundToInt()}% there)."
            total >= 80 -> "Great discipline! Your emergency buffer is protected and your weekly spending & savings habits are strong."
            else -> "Good momentum. Keep logging no-spend days and staying within your weekly spending budget."
        }

        val breakdown = listOf(
            WeeklyScoreBreakdownItem(
                category = "Emergency Buffer Progress",
                earnedPoints = bufferPoints,
                maxPoints = 40,
                detail = "${(bufferStatus.progressFraction * 100).roundToInt()}% of target reached."
            ),
            WeeklyScoreBreakdownItem(
                category = "Spending vs Weekly Limit",
                earnedPoints = spendPoints,
                maxPoints = 30,
                detail = if (weeklySpent <= weeklyLimit) "Within weekly target." else "Above weekly spending target."
            ),
            WeeklyScoreBreakdownItem(
                category = "Savings Consistency & Streaks",
                earnedPoints = consistencyPoints,
                maxPoints = 30,
                detail = "$noSpendStreakDays no-spend day streak • $recentSplits auto-splits in last 30 days."
            )
        )

        return WeeklyMoneyScore(
            totalScore = total,
            gradeLabel = gradeLabel,
            summary = summary,
            breakdown = breakdown
        )
    }

    val allMicroLessons: List<MoneyLesson> = listOf(
        MoneyLesson(
            topic = "Emergency Fund",
            title = "Why 1 Month of Cash Beats a Credit Card",
            summary = "Protect yourself from surprise bills without going into debt.",
            readTimeMinutes = 2,
            body = "When a phone breaks, rent jumps, or a client pays late, an emergency fund lets you handle it calmly in cash. Without a buffer, most people reach for a high-interest credit card or loan—turning a small bump into months of expensive debt. Start with 1 full month of essential bills tucked in a separate, safe savings bucket before thinking about investing.",
            keyTakeaway = "Build at least 1 month of essential spending before putting money at market risk."
        ),
        MoneyLesson(
            topic = "Compounding",
            title = "How Small Steady Deposits Snowball Over Time",
            summary = "Time matters more than starting with a huge lump sum.",
            readTimeMinutes = 2,
            body = "Compounding means your savings earn interest or growth—and next year, that growth earns growth of its own. Even modest monthly deposits add up dramatically over 10 to 20 years when left untouched. Starting early with small amounts often beats waiting until you feel 'rich' enough to start later.",
            keyTakeaway = "Consistency beats size: small regular amounts compound quietly over years."
        ),
        MoneyLesson(
            topic = "Inflation",
            title = "Why Idle Cash Slowly Shrinks in Value",
            summary = "Prices rise over time, so uninvested long-term money loses buying power.",
            readTimeMinutes = 2,
            body = "If groceries cost 5% more next year than today, cash sitting in a 0% checking account buys less each year. That is why emergency cash belongs in a safe interest-bearing savings account, while long-term money (5+ years away, after your buffer is ready) needs assets that historically outpace inflation.",
            keyTakeaway = "Keep short-term safety money liquid in savings, and plan long-term goals to beat inflation."
        ),
        MoneyLesson(
            topic = "Scams",
            title = "The 3-Second Pause That Beats Urgent Traps",
            summary = "Urgency, secrecy, and 'pay to receive' are classic scam fingerprints.",
            readTimeMinutes = 2,
            body = "Scammers rush you with fake threats ('KYC expires in 2 hours!') or too-good-to-be-true promises ('guaranteed 30% monthly returns' or 'enter your UPI PIN to receive prize money'). Remember: you NEVER need to enter a PIN, share an OTP, or pay an upfront fee to receive money or get a legitimate job.",
            keyTakeaway = "Never share OTP, PIN, or passwords—always verify independently using official apps."
        ),
        MoneyLesson(
            topic = "Risk",
            title = "Why Higher Potential Return Always Carries Bumps",
            summary = "No legitimate investment goes straight up without down years.",
            readTimeMinutes = 2,
            body = "Safe bank savings barely wobble, while broad stock index funds can drop 20% to 35% in a bad year before recovering over longer periods. If anyone promises high returns with zero risk, walk away immediately. Match your timeline: money needed within 3 years should stay safe; money for 5+ years can handle market ups and downs.",
            keyTakeaway = "Only invest money you won't need for at least 5 years, and expect bad years along the way."
        ),
        MoneyLesson(
            topic = "Irregular Income",
            title = "Smoothing Feast-and-Famine Months",
            summary = "Pay yourself a steady salary from a holding pot.",
            readTimeMinutes = 2,
            body = "Freelancers and side-hustlers often overspend in a huge month and panic in a quiet month. The fix is simple: route all variable income into one Holding Pot first. On the 1st of every month, release one fixed 'monthly salary' to your budget. Good months build the pot; lean months draw from it without stress.",
            keyTakeaway = "Never budget off your best month—pay yourself a steady baseline salary from your pot."
        )
    )

    fun getDailyMicroLesson(dayOfYear: Int = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)): MoneyLesson {
        val index = (dayOfYear % allMicroLessons.size).coerceIn(0, allMicroLessons.size - 1)
        return allMicroLessons[index]
    }

    fun getIncomeIdeasForProfile(profile: UserProfile): List<IncomeIdeaItem> {
        val hours = profile.sideIncomeHoursPerWeek
        val base = mutableListOf(
            IncomeIdeaItem(
                title = "Digital Notes, Guides & Templates",
                bestFor = "Students & Early Career",
                weeklyHoursNeeded = "3–5 hrs/week",
                startupCost = "Zero",
                description = "Package clear study guides, spreadsheet trackers, or workflow checklists once and offer them on verified creator marketplaces.",
                firstStep = "Pick 1 subject or workflow you solved well and turn it into a 5-page template."
            ),
            IncomeIdeaItem(
                title = "1-on-1 Tutoring, Mentoring or Coaching",
                bestFor = "Students, Family & Retired",
                weeklyHoursNeeded = "4–10 hrs/week",
                startupCost = "Zero",
                description = "Share skills in math, coding, languages, music, or career mentoring via community boards or verified platforms.",
                firstStep = "Write a clear 3-line offer listing topics covered and your weekly hours."
            ),
            IncomeIdeaItem(
                title = "Scoped Freelance Services (Editing, Design, Admin)",
                bestFor = "Self-employed & Early Career",
                weeklyHoursNeeded = "6–15 hrs/week",
                startupCost = "Zero",
                description = "Offer clearly scoped packages (e.g., short-form video edits, bookkeeping cleanup, or web updates) for small local businesses.",
                firstStep = "Build 2 short sample pieces to show local business owners."
            ),
            IncomeIdeaItem(
                title = "Declutter & Local Resale",
                bestFor = "Quick Buffer Boost (Any Life Stage)",
                weeklyHoursNeeded = "2–4 hrs/week",
                startupCost = "Zero",
                description = "Sell unused textbooks, electronics, tools, or hobby gear locally and route 100% straight to your emergency buffer.",
                firstStep = "List 5 unused items today and log net proceeds after any fees."
            )
        )
        if (hours >= 10) {
            base.add(
                IncomeIdeaItem(
                    title = "Monthly Retainer Support for Local Shops",
                    bestFor = "Self-employed & Flexible Schedules",
                    weeklyHoursNeeded = "10+ hrs/week",
                    startupCost = "Minimal",
                    description = "Manage weekly updates, customer newsletters, or listings for 2–3 neighborhood businesses on a steady monthly retainer.",
                    firstStep = "Review 3 nearby businesses with outdated profiles and offer a clear monthly package."
                )
            )
        }
        return base
    }

    // === CHANGE 3: DEBT PAYOFF PLANNER (SNOWBALL VS AVALANCHE) ===

    fun calculateDebtPayoffComparison(
        debts: List<DebtItem>,
        extraMonthlyPayment: Double,
        startMillis: Long = System.currentTimeMillis()
    ): DebtComparisonReport {
        val activeDebts = debts.filter { it.totalAmount > 0.0 }
        val safeExtra = max(0.0, extraMonthlyPayment)
        val snowball = simulateDebtPayoff(
            debts = activeDebts,
            extraMonthlyPayment = safeExtra,
            isSnowball = true,
            startMillis = startMillis
        )
        val avalanche = simulateDebtPayoff(
            debts = activeDebts,
            extraMonthlyPayment = safeExtra,
            isSnowball = false,
            startMillis = startMillis
        )
        val interestSaved = max(0.0, snowball.totalInterestPaid - avalanche.totalInterestPaid)
        val oneLiner = "Snowball pays off your smallest balance first for quick momentum, while Avalanche targets the highest interest rate first to save more on interest."
        return DebtComparisonReport(
            snowball = snowball,
            avalanche = avalanche,
            interestSavedByAvalanche = interestSaved,
            differenceOneLiner = oneLiner
        )
    }

    private fun simulateDebtPayoff(
        debts: List<DebtItem>,
        extraMonthlyPayment: Double,
        isSnowball: Boolean,
        startMillis: Long
    ): DebtPayoffStrategyResult {
        val strategyName = if (isSnowball) "Snowball" else "Avalanche"
        val monthFormat = SimpleDateFormat("MMM yyyy", Locale.US)

        if (debts.isEmpty()) {
            val cal = Calendar.getInstance().apply { timeInMillis = startMillis }
            return DebtPayoffStrategyResult(
                strategyName = strategyName,
                monthsToPayoff = 0,
                payoffDateLabel = monthFormat.format(cal.time),
                totalInterestPaid = 0.0,
                totalPaid = 0.0,
                schedule = emptyList()
            )
        }

        data class WorkingDebt(
            val name: String,
            var balance: Double,
            val annualRate: Double,
            val minPayment: Double
        )

        val working = debts.map {
            val safeMin = if (it.minimumPayment > 0.0) it.minimumPayment else max(10.0, it.totalAmount * 0.02)
            WorkingDebt(it.name, it.totalAmount, max(0.0, it.interestRate), safeMin)
        }.toMutableList()

        val totalBaselineBudget = working.sumOf { it.minPayment } + max(0.0, extraMonthlyPayment)
        val schedule = mutableListOf<DebtScheduleMonth>()
        var totalInterestPaid = 0.0
        var totalPaid = 0.0
        var month = 0
        val maxMonths = 360

        val cal = Calendar.getInstance().apply { timeInMillis = startMillis }

        while (working.any { it.balance > 0.01 } && month < maxMonths) {
            month++
            cal.add(Calendar.MONTH, 1)
            val monthLabel = monthFormat.format(cal.time)

            var monthInterest = 0.0
            // 1. Accrue monthly interest
            for (d in working) {
                if (d.balance > 0.01) {
                    val intr = d.balance * (d.annualRate / 100.0 / 12.0)
                    d.balance += intr
                    monthInterest += intr
                }
            }
            totalInterestPaid += monthInterest

            // 2. Pay minimum payments first
            var remainingBudget = totalBaselineBudget
            var monthPayment = 0.0
            for (d in working) {
                if (d.balance > 0.01 && remainingBudget > 0.0) {
                    val minPay = min(d.balance, min(d.minPayment, remainingBudget))
                    d.balance -= minPay
                    remainingBudget -= minPay
                    monthPayment += minPay
                }
            }

            // 3. Apply freed minimums + extra payment to priority order
            val orderedTargets = if (isSnowball) {
                working.filter { it.balance > 0.01 }.sortedWith(compareBy<WorkingDebt> { it.balance }.thenByDescending { it.annualRate })
            } else {
                working.filter { it.balance > 0.01 }.sortedWith(compareByDescending<WorkingDebt> { it.annualRate }.thenBy { it.balance })
            }
            val focusName = orderedTargets.firstOrNull()?.name ?: "All Paid"

            for (d in orderedTargets) {
                if (remainingBudget <= 0.01) break
                val extraPay = min(d.balance, remainingBudget)
                d.balance -= extraPay
                remainingBudget -= extraPay
                monthPayment += extraPay
            }

            totalPaid += monthPayment
            val remainingTotal = working.sumOf { max(0.0, it.balance) }
            val principalPaid = max(0.0, monthPayment - monthInterest)

            schedule.add(
                DebtScheduleMonth(
                    monthNumber = month,
                    monthLabel = monthLabel,
                    totalPayment = monthPayment,
                    interestPaid = monthInterest,
                    principalPaid = principalPaid,
                    remainingBalance = remainingTotal,
                    activeFocusDebt = focusName
                )
            )
        }

        val payoffLabel = if (working.any { it.balance > 0.01 }) {
            "30+ yrs (raise payment)"
        } else {
            schedule.lastOrNull()?.monthLabel ?: monthFormat.format(cal.time)
        }

        return DebtPayoffStrategyResult(
            strategyName = strategyName,
            monthsToPayoff = month,
            payoffDateLabel = payoffLabel,
            totalInterestPaid = totalInterestPaid,
            totalPaid = totalPaid,
            schedule = schedule
        )
    }

    // === CHANGE 4: NET WORTH VIEW ===

    fun calculateNetWorthSummary(
        manualAssets: List<AssetItem>,
        linkedBankAccounts: List<LinkedBankAccount>,
        isBankLinked: Boolean,
        debts: List<DebtItem>,
        ledgerCashBalance: Double = 0.0
    ): NetWorthSummary {
        val categoryMap = AssetCategory.values().associateWith { cat ->
            manualAssets.filter { it.category == cat }.sumOf { max(0.0, it.amount) }
        }.toMutableMap()

        val safeLedgerCash = if (isBankLinked) 0.0 else max(0.0, ledgerCashBalance)
        val manualTotal = categoryMap.values.sum()
        val linkedTotal = if (isBankLinked) {
            linkedBankAccounts.sumOf { max(0.0, it.currentBalance) }
        } else {
            0.0
        }
        val totalAssets = manualTotal + linkedTotal + safeLedgerCash
        val totalDebts = debts.sumOf { max(0.0, it.totalAmount) }
        val netWorth = totalAssets - totalDebts

        return NetWorthSummary(
            manualAssetsTotal = manualTotal,
            linkedBankAssetsTotal = linkedTotal,
            totalAssets = totalAssets,
            totalDebts = totalDebts,
            netWorth = netWorth,
            categoryTotals = categoryMap,
            ledgerCashBalanceTotal = safeLedgerCash
        )
    }

    // === CHANGE 5: GOAL PLANNER ===

    fun parseMonthsUntilDeadline(
        targetDate: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val trimmed = targetDate.trim()
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val currentYear = nowCal.get(Calendar.YEAR)
        val currentMonth = nowCal.get(Calendar.MONTH) // 0..11

        val formats = listOf(
            SimpleDateFormat("yyyy-MM", Locale.US),
            SimpleDateFormat("MMM yyyy", Locale.US),
            SimpleDateFormat("MMMM yyyy", Locale.US),
            SimpleDateFormat("MM/yyyy", Locale.US),
            SimpleDateFormat("yyyy", Locale.US)
        )
        for (fmt in formats) {
            try {
                fmt.isLenient = false
                val parsed = fmt.parse(trimmed)
                if (parsed != null) {
                    val targetCal = Calendar.getInstance().apply { time = parsed }
                    val diff = (targetCal.get(Calendar.YEAR) - currentYear) * 12 +
                        (targetCal.get(Calendar.MONTH) - currentMonth)
                    return max(1, diff)
                }
            } catch (_: Exception) {
            }
        }
        return 12
    }

    fun analyzeGoal(
        goal: GoalItem,
        monthlySurplus: Double,
        activeGoalsCount: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): GoalPlanAnalysis {
        val safeTarget = max(0.0, goal.targetAmount)
        val safeCurrent = max(0.0, goal.currentAmount)
        val remaining = max(0.0, safeTarget - safeCurrent)
        val months = parseMonthsUntilDeadline(goal.targetDate, nowMillis)
        val monthlyNeeded = if (remaining <= 0.0) 0.0 else remaining / months
        val progress = if (safeTarget > 0.0) (safeCurrent / safeTarget).coerceIn(0.0, 1.0).toFloat() else 1f

        val perGoalCapacity = if (monthlySurplus > 0.0) monthlySurplus / max(1, activeGoalsCount) else 0.0
        val isOnTrack = when {
            remaining <= 0.0 -> true
            monthlySurplus <= 0.0 -> false
            monthlyNeeded <= perGoalCapacity -> true
            else -> false
        }

        val statusLabel = when {
            remaining <= 0.0 -> "Goal Reached"
            isOnTrack -> "On track"
            monthlySurplus > 0.0 && monthlyNeeded <= monthlySurplus -> "Tight pace"
            else -> "Behind pace"
        }

        val statusReason = when {
            remaining <= 0.0 -> "You have saved the full target amount."
            isOnTrack -> "Your monthly savings space covers the needed pace."
            monthlySurplus > 0.0 -> "Needs more than your equal goal share of monthly surplus — extend deadline or trim expenses."
            else -> "No monthly surplus yet; trim costs or raise income first."
        }

        return GoalPlanAnalysis(
            goal = goal,
            remainingAmount = remaining,
            monthsRemaining = months,
            monthlyAmountNeeded = monthlyNeeded,
            progressFraction = progress,
            isOnTrack = isOnTrack,
            statusLabel = statusLabel,
            statusReason = statusReason
        )
    }

    fun analyzeAllGoals(
        profile: UserProfile,
        nowMillis: Long = System.currentTimeMillis()
    ): List<GoalPlanAnalysis> {
        val typicalSpending = calculateTypicalMonthlySpending(profile)
        val surplus = profile.monthlyIncome - typicalSpending
        val count = max(1, profile.goals.size)
        return profile.goals.map { goal ->
            analyzeGoal(
                goal = goal,
                monthlySurplus = surplus,
                activeGoalsCount = count,
                nowMillis = nowMillis
            )
        }
    }

    // === CHANGE 6: MONTHLY IMPROVEMENT REPORT ===

    fun calculateMonthlyImprovementReport(
        profile: UserProfile,
        envelopes: List<VirtualEnvelope>,
        transactions: List<LedgerTransaction>,
        previousSnapshot: MonthlyHealthSnapshot?,
        nowMillis: Long = System.currentTimeMillis()
    ): MonthlyImprovementReport {
        val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)
        val currentMonthKey = monthFormat.format(java.util.Date(nowMillis))
        val nextMonthCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.MONTH, 1)
        }
        val nextMonthLabel = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(nextMonthCal.time)

        val bufferStatus = calculateBufferGateStatus(profile, envelopes)
        val typicalSpending = bufferStatus.typicalMonthlySpending

        // 1. Buffer progress (0..30)
        val bufferScore = (bufferStatus.progressFraction * 30f).roundToInt().coerceIn(0, 30)

        // 2. Spending vs income or limit (0..25)
        val thirtyDaysAgo = nowMillis - (30L * 24L * 60L * 60L * 1000L)
        val logged30DayExpense = transactions
            .filter { it.type == "EXPENSE" && it.timestamp >= thirtyDaysAgo }
            .sumOf { it.amount }
        val logged30DayIncome = transactions
            .filter { it.type == "INCOME" && it.timestamp >= thirtyDaysAgo }
            .sumOf { it.amount }
        val effectiveIncome = if (logged30DayIncome > profile.monthlyIncome) logged30DayIncome else profile.monthlyIncome
        val spendingBenchmark = if (effectiveIncome > 0.0) effectiveIncome else typicalSpending
        val actualSpending = if (logged30DayExpense > 0.0) logged30DayExpense else typicalSpending
        val spendRatio = if (spendingBenchmark > 0.0) actualSpending / spendingBenchmark else 1.5
        val spendingScore = when {
            spendRatio <= 0.70 -> 25
            spendRatio <= 0.85 -> 21
            spendRatio <= 1.00 -> 15
            spendRatio <= 1.15 -> 8
            else -> 3
        }

        // 3. Savings rate (0..25)
        val surplus = effectiveIncome - actualSpending
        val savingsRate = if (effectiveIncome > 0.0) (surplus / effectiveIncome) * 100.0 else 0.0
        val savingsScore = when {
            savingsRate >= 25.0 -> 25
            savingsRate >= 15.0 -> 20
            savingsRate >= 8.0 -> 14
            savingsRate > 0.0 -> 8
            else -> 0
        }

        // 4. Debt load (0..20)
        val totalDebt = profile.debts.sumOf { it.totalAmount }
        val highRateDebt = profile.debts.filter { it.interestRate >= 15.0 }.sumOf { it.totalAmount }
        val debtToIncome = if (profile.monthlyIncome > 0.0) totalDebt / (profile.monthlyIncome * 12.0) else if (totalDebt > 0) 2.0 else 0.0
        val debtScore = when {
            totalDebt <= 0.0 -> 20
            highRateDebt <= 0.0 && debtToIncome <= 0.25 -> 16
            highRateDebt <= profile.monthlyIncome && debtToIncome <= 0.5 -> 11
            else -> 5
        }

        val currentScore = (bufferScore + spendingScore + savingsScore + debtScore).coerceIn(0, 100)
        val prevScore = previousSnapshot?.totalScore
        val scoreDelta = if (prevScore != null) currentScore - prevScore else 0

        val gradeLabel = when {
            currentScore >= 85 -> "Strong"
            currentScore >= 70 -> "Healthy"
            currentScore >= 50 -> "Building"
            else -> "Needs Focus"
        }

        val currentSnapshot = MonthlyHealthSnapshot(
            monthKey = currentMonthKey,
            totalScore = currentScore,
            bufferScore = bufferScore,
            spendingScore = spendingScore,
            savingsScore = savingsScore,
            debtScore = debtScore,
            bufferAmount = bufferStatus.currentEmergencyAmount,
            savingsRatePercent = savingsRate,
            totalDebt = totalDebt,
            timestamp = nowMillis
        )

        val whatImproved = mutableListOf<String>()
        val whatSlipped = mutableListOf<String>()

        if (previousSnapshot != null) {
            if (bufferScore > previousSnapshot.bufferScore || bufferStatus.currentEmergencyAmount > previousSnapshot.bufferAmount) {
                whatImproved.add("Emergency buffer grew by ${CurrenciesAndCountries.format(max(0.0, bufferStatus.currentEmergencyAmount - previousSnapshot.bufferAmount), profile.currencyCode)} (+${max(0, bufferScore - previousSnapshot.bufferScore)} score pts).")
            } else if (bufferScore < previousSnapshot.bufferScore || bufferStatus.currentEmergencyAmount < previousSnapshot.bufferAmount) {
                whatSlipped.add("Emergency buffer dipped from ${CurrenciesAndCountries.format(previousSnapshot.bufferAmount, profile.currencyCode)} (${bufferScore - previousSnapshot.bufferScore} pts).")
            }

            if (spendingScore > previousSnapshot.spendingScore) {
                whatImproved.add("Spending control improved vs your income/limit (+${spendingScore - previousSnapshot.spendingScore} pts).")
            } else if (spendingScore < previousSnapshot.spendingScore) {
                whatSlipped.add("Monthly spending ran higher relative to income/limit (${spendingScore - previousSnapshot.spendingScore} pts).")
            }

            if (savingsScore > previousSnapshot.savingsScore || savingsRate > previousSnapshot.savingsRatePercent + 1.0) {
                whatImproved.add("Savings rate improved to ${String.format(Locale.US, "%.1f", savingsRate)}%.")
            } else if (savingsScore < previousSnapshot.savingsScore || savingsRate < previousSnapshot.savingsRatePercent - 1.0) {
                whatSlipped.add("Monthly savings rate slipped from ${String.format(Locale.US, "%.1f", previousSnapshot.savingsRatePercent)}% to ${String.format(Locale.US, "%.1f", savingsRate)}%.")
            }

            if (totalDebt < previousSnapshot.totalDebt) {
                whatImproved.add("Total debt fell by ${CurrenciesAndCountries.format(previousSnapshot.totalDebt - totalDebt, profile.currencyCode)}.")
            } else if (totalDebt > previousSnapshot.totalDebt) {
                whatSlipped.add("Total debt rose by ${CurrenciesAndCountries.format(totalDebt - previousSnapshot.totalDebt, profile.currencyCode)}.")
            }
        } else {
            if (bufferStatus.isBufferMet) whatImproved.add("1-month emergency buffer target is funded (${CurrenciesAndCountries.format(bufferStatus.currentEmergencyAmount, profile.currencyCode)}).")
            if (savingsRate >= 10.0) whatImproved.add("Positive monthly savings rate of ${String.format(Locale.US, "%.1f", savingsRate)}%.")
            if (totalDebt <= 0.0) whatImproved.add("Debt-free balance sheet (20/20 debt score).")
            if (!bufferStatus.isBufferMet) whatSlipped.add("Emergency buffer is at ${(bufferStatus.progressFraction * 100).roundToInt()}% — ${CurrenciesAndCountries.format(bufferStatus.remainingToTarget, profile.currencyCode)} remaining.")
            if (savingsRate < 10.0) whatSlipped.add("Savings rate is ${String.format(Locale.US, "%.1f", savingsRate)}% (target: at least 10%–20%).")
            if (highRateDebt > 0.0) whatSlipped.add("High-interest debt (${CurrenciesAndCountries.format(highRateDebt, profile.currencyCode)}) is reducing cashflow.")
        }

        if (whatImproved.isEmpty()) {
            whatImproved.add("Held steady with your baseline tracking and check-in habit.")
        }
        if (whatSlipped.isEmpty()) {
            whatSlipped.add("No major setbacks recorded compared to last month!")
        }

        // Top 3 Specific Actions for Next Month (what, how much, by when)
        val actions = mutableListOf<MonthlyActionItem>()
        if (!bufferStatus.isBufferMet) {
            val stepAmt = min(bufferStatus.remainingToTarget, max(25.0, bufferStatus.targetAmount * 0.25))
            actions.add(
                MonthlyActionItem(
                    priority = 1,
                    title = "Boost Emergency Safety Net",
                    what = "Move extra surplus into your Emergency Safety Net envelope before investing.",
                    howMuch = CurrenciesAndCountries.format(stepAmt, profile.currencyCode),
                    byWhen = "By $nextMonthLabel"
                )
            )
        } else {
            val goalAmt = max(25.0, surplus * 0.4)
            actions.add(
                MonthlyActionItem(
                    priority = 1,
                    title = "Fund Top Savings Goal",
                    what = "Allocate steady monthly contribution toward your highest-priority Goal Planner target.",
                    howMuch = CurrenciesAndCountries.format(goalAmt, profile.currencyCode),
                    byWhen = "By $nextMonthLabel"
                )
            )
        }

        if (totalDebt > 0.0 && !profile.isUnder18) {
            val topDebt = profile.debts.maxByOrNull { it.interestRate }
            val extraDebtAmt = max(20.0, (topDebt?.minimumPayment ?: 25.0) * 0.5)
            actions.add(
                MonthlyActionItem(
                    priority = 2,
                    title = "Extra Debt Principal Payment",
                    what = "Add an extra payment toward ${topDebt?.name ?: "your highest-APR debt"} using Avalanche/Snowball.",
                    howMuch = CurrenciesAndCountries.format(extraDebtAmt, profile.currencyCode),
                    byWhen = "By $nextMonthLabel"
                )
            )
        } else {
            val trimAmt = max(15.0, typicalSpending * 0.05)
            actions.add(
                MonthlyActionItem(
                    priority = 2,
                    title = "Trim Non-Essential Spending",
                    what = "Review subscriptions & impulse buys to free up monthly cashflow.",
                    howMuch = "Save ${CurrenciesAndCountries.format(trimAmt, profile.currencyCode)}/mo",
                    byWhen = "Within 14 days"
                )
            )
        }

        val checkInGoalAmt = max(10.0, typicalSpending * 0.03)
        actions.add(
            MonthlyActionItem(
                priority = 3,
                title = "Run Next Month's Check-In & Snapshot",
                what = "Save a Net Worth snapshot and verify upcoming 30-day calendar renewals.",
                howMuch = "Protect ${CurrenciesAndCountries.format(checkInGoalAmt, profile.currencyCode)} from surprise bills",
                byWhen = "By $nextMonthLabel"
            )
        )

        val breakdown = listOf(
            WeeklyScoreBreakdownItem("Emergency Buffer Progress", bufferScore, 30, "${(bufferStatus.progressFraction * 100).roundToInt()}% of safety buffer reached."),
            WeeklyScoreBreakdownItem("Spending vs Income / Limit", spendingScore, 25, "Spending is ${String.format(Locale.US, "%.0f", spendRatio * 100)}% of monthly benchmark."),
            WeeklyScoreBreakdownItem("Savings Rate", savingsScore, 25, "Keeping ${String.format(Locale.US, "%.1f", savingsRate)}% of monthly income."),
            WeeklyScoreBreakdownItem("Debt Load", debtScore, 20, if (totalDebt <= 0.0) "Debt-free balance sheet." else "Total debt: ${CurrenciesAndCountries.format(totalDebt, profile.currencyCode)}.")
        )

        val summary = if (previousSnapshot == null) {
            "First monthly baseline recorded ($currentScore/100). Save this snapshot so next month's report shows exact score changes."
        } else when {
            scoreDelta > 0 -> "Your Money Health Score rose +$scoreDelta pts vs ${previousSnapshot.monthKey}!"
            scoreDelta < 0 -> "Your Money Health Score dipped ${scoreDelta} pts vs ${previousSnapshot.monthKey}. Focus on the 3 actions below."
            else -> "Your Money Health Score held steady at $currentScore/100 vs ${previousSnapshot.monthKey}."
        }

        return MonthlyImprovementReport(
            currentMonthKey = currentMonthKey,
            previousMonthKey = previousSnapshot?.monthKey,
            currentScore = currentScore,
            previousScore = prevScore,
            scoreDelta = scoreDelta,
            gradeLabel = gradeLabel,
            summary = summary,
            pillarBreakdown = breakdown,
            whatImproved = whatImproved,
            whatSlipped = whatSlipped,
            top3Actions = actions.take(3),
            currentSnapshot = currentSnapshot
        )
    }
}

