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

    // === TRACEABILITY, DATA QUALITY, SCORE HISTORY DIFFS & MONTHLY IMPROVEMENT REPORT ===

    private data class PillarComputationResult(
        val bufferScore: Int,
        val spendingScore: Int,
        val savingsScore: Int,
        val debtScore: Int,
        val totalScore: Int,
        val bufferStatus: BufferGateStatus,
        val typicalSpending: Double,
        val effectiveIncome: Double,
        val actualSpending: Double,
        val spendRatio: Double,
        val savingsRate: Double,
        val surplus: Double,
        val totalDebt: Double,
        val highRateDebt: Double,
        val debtToIncome: Double,
        val recentTxCount: Int,
        val recentExpenseTxCount: Int,
        val recentIncomeTxCount: Int,
        val transactionSpanDays: Int,
        val usesSetupSpendingFallback: Boolean,
        val usesDefaultFloor: Boolean
    )

    private fun computeFourPillars(
        profile: UserProfile,
        envelopes: List<VirtualEnvelope>,
        transactions: List<LedgerTransaction>,
        nowMillis: Long,
        extraBufferAmount: Double = 0.0,
        spendingReductionMonthly: Double = 0.0,
        debtReductionAmount: Double = 0.0
    ): PillarComputationResult {
        val adjustedEnvelopes = if (extraBufferAmount > 0.0) {
            val hasEmergency = envelopes.any { it.type == EnvelopeType.EMERGENCY_FUND }
            if (hasEmergency) {
                envelopes.map {
                    if (it.type == EnvelopeType.EMERGENCY_FUND) it.copy(balance = it.balance + extraBufferAmount) else it
                }
            } else {
                envelopes + VirtualEnvelope("emergency_fund", EnvelopeType.EMERGENCY_FUND, "Emergency Safety Net", extraBufferAmount)
            }
        } else {
            envelopes
        }

        val bufferStatus = calculateBufferGateStatus(profile, adjustedEnvelopes)
        val typicalSpending = bufferStatus.typicalMonthlySpending
        val usesDefaultFloor = profile.expenses.isEmpty() && profile.debts.isEmpty()

        // 1. Buffer progress (0..30)
        val bufferScore = (bufferStatus.progressFraction * 30f).roundToInt().coerceIn(0, 30)

        // 2. Spending vs income or limit (0..25)
        val thirtyDaysAgo = nowMillis - (30L * 24L * 60L * 60L * 1000L)
        val recentTx = transactions.filter { it.timestamp >= thirtyDaysAgo }
        val logged30DayExpense = recentTx
            .filter { it.type == "EXPENSE" }
            .sumOf { it.amount }
        val logged30DayIncome = recentTx
            .filter { it.type == "INCOME" }
            .sumOf { it.amount }
        val recentExpenseTxCount = recentTx.count { it.type == "EXPENSE" }
        val recentIncomeTxCount = recentTx.count { it.type == "INCOME" }

        val spanDays = if (recentTx.isEmpty()) {
            0
        } else {
            val minTs = recentTx.minOf { it.timestamp }
            val maxTs = max(nowMillis, recentTx.maxOf { it.timestamp })
            max(1, (((maxTs - minTs) / (24L * 60L * 60L * 1000L)).toInt() + 1).coerceAtMost(30))
        }

        val usesSetupSpendingFallback = logged30DayExpense <= 0.0
        val effectiveIncome = if (logged30DayIncome > profile.monthlyIncome) logged30DayIncome else profile.monthlyIncome
        val spendingBenchmark = if (effectiveIncome > 0.0) effectiveIncome else typicalSpending
        val baseActualSpending = if (logged30DayExpense > 0.0) logged30DayExpense else typicalSpending
        val actualSpending = max(0.0, baseActualSpending - spendingReductionMonthly)
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
        val rawTotalDebt = profile.debts.sumOf { it.totalAmount }
        val totalDebt = max(0.0, rawTotalDebt - debtReductionAmount)
        val rawHighRateDebt = profile.debts.filter { it.interestRate >= 15.0 }.sumOf { it.totalAmount }
        val highRateDebt = max(0.0, rawHighRateDebt - debtReductionAmount)
        val debtToIncome = if (profile.monthlyIncome > 0.0) totalDebt / (profile.monthlyIncome * 12.0) else if (totalDebt > 0) 2.0 else 0.0
        val debtScore = when {
            totalDebt <= 0.0 -> 20
            highRateDebt <= 0.0 && debtToIncome <= 0.25 -> 16
            highRateDebt <= profile.monthlyIncome && debtToIncome <= 0.5 -> 11
            else -> 5
        }

        val totalScore = (bufferScore + spendingScore + savingsScore + debtScore).coerceIn(0, 100)
        return PillarComputationResult(
            bufferScore = bufferScore,
            spendingScore = spendingScore,
            savingsScore = savingsScore,
            debtScore = debtScore,
            totalScore = totalScore,
            bufferStatus = bufferStatus,
            typicalSpending = typicalSpending,
            effectiveIncome = effectiveIncome,
            actualSpending = actualSpending,
            spendRatio = spendRatio,
            savingsRate = savingsRate,
            surplus = surplus,
            totalDebt = totalDebt,
            highRateDebt = highRateDebt,
            debtToIncome = debtToIncome,
            recentTxCount = recentTx.size,
            recentExpenseTxCount = recentExpenseTxCount,
            recentIncomeTxCount = recentIncomeTxCount,
            transactionSpanDays = spanDays,
            usesSetupSpendingFallback = usesSetupSpendingFallback,
            usesDefaultFloor = usesDefaultFloor
        )
    }

    fun assessDataQuality(
        profile: UserProfile,
        transactions: List<LedgerTransaction>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<DataQualityNote> {
        val thirtyDaysAgo = nowMillis - (30L * 24L * 60L * 60L * 1000L)
        val recentTx = transactions.filter { it.timestamp >= thirtyDaysAgo }
        val spanDays = if (recentTx.isEmpty()) {
            0
        } else {
            val minTs = recentTx.minOf { it.timestamp }
            val maxTs = max(nowMillis, recentTx.maxOf { it.timestamp })
            max(1, (((maxTs - minTs) / (24L * 60L * 60L * 1000L)).toInt() + 1).coerceAtMost(30))
        }

        val notes = mutableListOf<DataQualityNote>()

        when {
            recentTx.isEmpty() -> notes.add(
                DataQualityNote(
                    id = "dq_no_tx",
                    label = "Based on 0 days of transactions",
                    detail = "No logged or imported transactions in the last 30 days. Spending & income rely on your setup estimates.",
                    isLowQuality = true
                )
            )
            spanDays < 14 -> notes.add(
                DataQualityNote(
                    id = "dq_short_tx",
                    label = "Based on $spanDays day${if (spanDays == 1) "" else "s"} of transactions",
                    detail = "Fewer than 14 days of transactions logged (${recentTx.size} entries). Confidence improves across a full month.",
                    isLowQuality = true
                )
            )
            else -> notes.add(
                DataQualityNote(
                    id = "dq_solid_tx",
                    label = "Based on $spanDays days of transactions",
                    detail = "${recentTx.size} transaction(s) recorded inside the 30-day window.",
                    isLowQuality = false
                )
            )
        }

        if (profile.debts.isEmpty()) {
            notes.add(
                DataQualityNote(
                    id = "dq_no_debt",
                    label = "No debt entered",
                    detail = "Debt Load awards 20/20 pts when no debts are entered. Add any loans or cards in Debt Planner if applicable.",
                    isLowQuality = true
                )
            )
        }

        if (profile.expenses.isEmpty() && recentTx.none { it.type == "EXPENSE" }) {
            notes.add(
                DataQualityNote(
                    id = "dq_setup_estimate",
                    label = "Using fallback spending estimate",
                    detail = "No setup expenses or expense transactions entered yet, so safety buffer target uses a default baseline.",
                    isLowQuality = true
                )
            )
        }

        if (profile.monthlyIncome <= 0.0 && recentTx.none { it.type == "INCOME" }) {
            notes.add(
                DataQualityNote(
                    id = "dq_no_income",
                    label = "No income recorded",
                    detail = "Monthly income and 30-day income are both 0; spending ratio uses fallback baseline.",
                    isLowQuality = true
                )
            )
        }

        return notes
    }

    fun calculateTracedMoneyHealthScore(
        profile: UserProfile,
        envelopes: List<VirtualEnvelope>,
        transactions: List<LedgerTransaction>,
        nowMillis: Long = System.currentTimeMillis()
    ): TracedMoneyHealthReport {
        val calc = computeFourPillars(profile, envelopes, transactions, nowMillis)
        val dqNotes = assessDataQuality(profile, transactions, nowMillis)
        val currency = profile.currencyCode
        val fmtTime = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(java.util.Date(nowMillis))
        val gradeLabel = when {
            calc.totalScore >= 85 -> "Strong"
            calc.totalScore >= 70 -> "Healthy"
            calc.totalScore >= 50 -> "Building"
            else -> "Needs Focus"
        }

        val spendingSourceLabel = if (calc.usesSetupSpendingFallback) {
            "Setup monthly spending baseline (0 logged expenses)"
        } else {
            "30-day ledger expenses (${calc.recentExpenseTxCount} tx)"
        }
        val incomeSourceLabel = if (calc.recentIncomeTxCount > 0 && calc.effectiveIncome > profile.monthlyIncome) {
            "30-day logged income (${calc.recentIncomeTxCount} tx)"
        } else {
            "Saved monthly income profile"
        }

        val components = listOf(
            ScoreComponentDetail(
                id = "buffer",
                name = "Emergency Buffer Progress",
                weightPercent = 30,
                earnedPoints = calc.bufferScore,
                maxPoints = 30,
                formulaPlainWords = "Round((Emergency Balance / Buffer Target) x 30), capped from 0 to 30 pts.",
                inputs = listOf(
                    ScoreInputMetric(
                        label = "Emergency Safety Net Balance",
                        valueFormatted = CurrenciesAndCountries.format(calc.bufferStatus.currentEmergencyAmount, currency),
                        sourceNote = "Emergency Safety Net envelope"
                    ),
                    ScoreInputMetric(
                        label = "Buffer Target (${String.format(Locale.US, "%.1f", calc.bufferStatus.targetMonths)} mo)",
                        valueFormatted = CurrenciesAndCountries.format(calc.bufferStatus.targetAmount, currency),
                        sourceNote = if (profile.customBufferTargetAmount != null) "Custom user target" else "Typical spending x target months"
                    ),
                    ScoreInputMetric(
                        label = "Buffer Coverage",
                        valueFormatted = "${(calc.bufferStatus.progressFraction * 100f).roundToInt()}%",
                        sourceNote = "Code ratio"
                    )
                ),
                ruleId = RuleRegistry.RULE_BUFFER_PROGRESS,
                explanation = "${CurrenciesAndCountries.format(calc.bufferStatus.currentEmergencyAmount, currency)} saved vs ${CurrenciesAndCountries.format(calc.bufferStatus.targetAmount, currency)} target (${(calc.bufferStatus.progressFraction * 100).roundToInt()}%)."
            ),
            ScoreComponentDetail(
                id = "spending",
                name = "Spending vs Income / Limit",
                weightPercent = 25,
                earnedPoints = calc.spendingScore,
                maxPoints = 25,
                formulaPlainWords = "Spend Ratio = Monthly Spending / Benchmark. <=70%: 25 pts, <=85%: 21 pts, <=100%: 15 pts, <=115%: 8 pts, >115%: 3 pts.",
                inputs = listOf(
                    ScoreInputMetric(
                        label = "Monthly Spending Used",
                        valueFormatted = CurrenciesAndCountries.format(calc.actualSpending, currency),
                        sourceNote = spendingSourceLabel
                    ),
                    ScoreInputMetric(
                        label = "Monthly Income / Benchmark",
                        valueFormatted = CurrenciesAndCountries.format(if (calc.effectiveIncome > 0.0) calc.effectiveIncome else calc.typicalSpending, currency),
                        sourceNote = incomeSourceLabel
                    ),
                    ScoreInputMetric(
                        label = "Spend-to-Benchmark Ratio",
                        valueFormatted = "${String.format(Locale.US, "%.0f", calc.spendRatio * 100)}%",
                        sourceNote = "Code calculation"
                    )
                ),
                ruleId = RuleRegistry.RULE_SPENDING_VS_LIMIT,
                explanation = "Spending is ${String.format(Locale.US, "%.0f", calc.spendRatio * 100)}% of your monthly benchmark."
            ),
            ScoreComponentDetail(
                id = "savings",
                name = "Savings Rate",
                weightPercent = 25,
                earnedPoints = calc.savingsScore,
                maxPoints = 25,
                formulaPlainWords = "Savings Rate = ((Income - Spending) / Income) x 100. >=25%: 25 pts, >=15%: 20 pts, >=8%: 14 pts, >0%: 8 pts, <=0%: 0 pts.",
                inputs = listOf(
                    ScoreInputMetric(
                        label = "Effective Monthly Income",
                        valueFormatted = CurrenciesAndCountries.format(calc.effectiveIncome, currency),
                        sourceNote = incomeSourceLabel
                    ),
                    ScoreInputMetric(
                        label = "Monthly Surplus",
                        valueFormatted = CurrenciesAndCountries.format(calc.surplus, currency),
                        sourceNote = "Effective income minus spending"
                    ),
                    ScoreInputMetric(
                        label = "Computed Savings Rate",
                        valueFormatted = "${String.format(Locale.US, "%.1f", calc.savingsRate)}%",
                        sourceNote = "Code calculation"
                    )
                ),
                ruleId = RuleRegistry.RULE_SAVINGS_RATE,
                explanation = "Keeping ${String.format(Locale.US, "%.1f", calc.savingsRate)}% of monthly income unspent."
            ),
            ScoreComponentDetail(
                id = "debt",
                name = "Debt Load",
                weightPercent = 20,
                earnedPoints = calc.debtScore,
                maxPoints = 20,
                formulaPlainWords = "0 debt: 20 pts; No >=15% APR & debt <=25% annual income: 16 pts; High-APR <=1 mo income & debt <=50% annual income: 11 pts; else 5 pts.",
                inputs = listOf(
                    ScoreInputMetric(
                        label = "Total Debt Balance",
                        valueFormatted = CurrenciesAndCountries.format(calc.totalDebt, currency),
                        sourceNote = if (profile.debts.isEmpty()) "No debts entered in Debt Planner" else "${profile.debts.size} debt item(s)"
                    ),
                    ScoreInputMetric(
                        label = "High-Interest Debt (>= 15% APR)",
                        valueFormatted = CurrenciesAndCountries.format(calc.highRateDebt, currency),
                        sourceNote = "Debt Planner APR filter"
                    ),
                    ScoreInputMetric(
                        label = "Debt-to-Annual-Income",
                        valueFormatted = "${String.format(Locale.US, "%.1f", calc.debtToIncome * 100)}%",
                        sourceNote = "Total debt / (monthly income x 12)"
                    )
                ),
                ruleId = RuleRegistry.RULE_DEBT_LOAD,
                explanation = if (calc.totalDebt <= 0.0) "Debt-free balance sheet (no debts recorded)." else "Total debt ${CurrenciesAndCountries.format(calc.totalDebt, currency)} (High-APR: ${CurrenciesAndCountries.format(calc.highRateDebt, currency)})."
            )
        )

        val triggeredRules = buildList {
            add(RuleRegistry.RULE_BUFFER_PROGRESS)
            add(RuleRegistry.RULE_SPENDING_VS_LIMIT)
            add(RuleRegistry.RULE_SAVINGS_RATE)
            add(RuleRegistry.RULE_DEBT_LOAD)
            if (profile.isUnder18) add(RuleRegistry.RULE_GUIDED_UNDER18)
            if (!calc.bufferStatus.isBufferMet) add(RuleRegistry.RULE_BUFFER_FIRST)
        }

        return TracedMoneyHealthReport(
            totalScore = calc.totalScore,
            gradeLabel = gradeLabel,
            calculatedAtMillis = nowMillis,
            calculatedAtFormatted = fmtTime,
            components = components,
            dataQualityNotes = dqNotes,
            triggeredRuleIds = triggeredRules
        )
    }

    fun buildScoreHistorySnapshot(
        profile: UserProfile,
        envelopes: List<VirtualEnvelope>,
        transactions: List<LedgerTransaction>,
        nowMillis: Long = System.currentTimeMillis()
    ): ScoreHistorySnapshot {
        val calc = computeFourPillars(profile, envelopes, transactions, nowMillis)
        val dateLabel = SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(java.util.Date(nowMillis))
        val rules = buildList {
            add(RuleRegistry.RULE_BUFFER_PROGRESS)
            add(RuleRegistry.RULE_SPENDING_VS_LIMIT)
            add(RuleRegistry.RULE_SAVINGS_RATE)
            add(RuleRegistry.RULE_DEBT_LOAD)
            if (!calc.bufferStatus.isBufferMet) add(RuleRegistry.RULE_BUFFER_FIRST)
            if (calc.totalDebt > 0.0 && !profile.isUnder18) add(RuleRegistry.RULE_HIGH_APR_DEBT)
        }
        return ScoreHistorySnapshot(
            timestamp = nowMillis,
            dateLabel = dateLabel,
            totalScore = calc.totalScore,
            bufferScore = calc.bufferScore,
            spendingScore = calc.spendingScore,
            savingsScore = calc.savingsScore,
            debtScore = calc.debtScore,
            bufferAmount = calc.bufferStatus.currentEmergencyAmount,
            typicalMonthlySpending = calc.typicalSpending,
            effectiveIncome = calc.effectiveIncome,
            actualSpending = calc.actualSpending,
            savingsRatePercent = calc.savingsRate,
            totalDebt = calc.totalDebt,
            transactionCount = calc.recentTxCount,
            transactionSpanDays = calc.transactionSpanDays,
            triggeredRuleIds = rules
        )
    }

    fun calculateScoreSnapshotDiff(
        current: ScoreHistorySnapshot,
        previous: ScoreHistorySnapshot?,
        currencyCode: String
    ): ScoreSnapshotDiff {
        if (previous == null) {
            return ScoreSnapshotDiff(
                currentSnapshot = current,
                previousSnapshot = null,
                totalDelta = 0,
                componentDeltas = listOf(
                    ComponentDeltaSummary("Emergency Buffer", current.bufferScore, current.bufferScore, 0),
                    ComponentDeltaSummary("Spending vs Income", current.spendingScore, current.spendingScore, 0),
                    ComponentDeltaSummary("Savings Rate", current.savingsScore, current.savingsScore, 0),
                    ComponentDeltaSummary("Debt Load", current.debtScore, current.debtScore, 0)
                ),
                primaryReason = "Initial baseline recorded at ${current.totalScore}/100 (${current.transactionCount} recent transactions).",
                ruleId = current.triggeredRuleIds.firstOrNull()
            )
        }

        val deltas = listOf(
            ComponentDeltaSummary("Emergency Buffer", previous.bufferScore, current.bufferScore, current.bufferScore - previous.bufferScore),
            ComponentDeltaSummary("Spending vs Income", previous.spendingScore, current.spendingScore, current.spendingScore - previous.spendingScore),
            ComponentDeltaSummary("Savings Rate", previous.savingsScore, current.savingsScore, current.savingsScore - previous.savingsScore),
            ComponentDeltaSummary("Debt Load", previous.debtScore, current.debtScore, current.debtScore - previous.debtScore)
        )
        val totalDelta = current.totalScore - previous.totalScore
        val biggestShift = deltas.maxByOrNull { kotlin.math.abs(it.delta) }

        val (reason, ruleId) = when {
            biggestShift == null || totalDelta == 0 && deltas.all { it.delta == 0 } -> {
                "Score held steady at ${current.totalScore}/100 with unchanged component weights." to RuleRegistry.RULE_CHECKIN_SNAPSHOT
            }
            biggestShift.componentName == "Emergency Buffer" && biggestShift.delta != 0 -> {
                val verb = if (biggestShift.delta > 0) "grew" else "dropped"
                "Main driver: Emergency buffer $verb from ${CurrenciesAndCountries.format(previous.bufferAmount, currencyCode)} to ${CurrenciesAndCountries.format(current.bufferAmount, currencyCode)} (${if (biggestShift.delta > 0) "+" else ""}${biggestShift.delta} pts)." to RuleRegistry.RULE_BUFFER_PROGRESS
            }
            biggestShift.componentName == "Spending vs Income" && biggestShift.delta != 0 -> {
                val verb = if (biggestShift.delta > 0) "improved" else "rose"
                "Main driver: Monthly spending $verb from ${CurrenciesAndCountries.format(previous.actualSpending, currencyCode)} to ${CurrenciesAndCountries.format(current.actualSpending, currencyCode)} (${if (biggestShift.delta > 0) "+" else ""}${biggestShift.delta} pts)." to RuleRegistry.RULE_SPENDING_VS_LIMIT
            }
            biggestShift.componentName == "Savings Rate" && biggestShift.delta != 0 -> {
                "Main driver: Savings rate moved from ${String.format(Locale.US, "%.1f", previous.savingsRatePercent)}% to ${String.format(Locale.US, "%.1f", current.savingsRatePercent)}% (${if (biggestShift.delta > 0) "+" else ""}${biggestShift.delta} pts)." to RuleRegistry.RULE_SAVINGS_RATE
            }
            biggestShift.componentName == "Debt Load" && biggestShift.delta != 0 -> {
                "Main driver: Total debt changed from ${CurrenciesAndCountries.format(previous.totalDebt, currencyCode)} to ${CurrenciesAndCountries.format(current.totalDebt, currencyCode)} (${if (biggestShift.delta > 0) "+" else ""}${biggestShift.delta} pts)." to RuleRegistry.RULE_DEBT_LOAD
            }
            else -> {
                "Score shifted by ${if (totalDelta > 0) "+" else ""}$totalDelta pts across your updated financial inputs." to RuleRegistry.RULE_CHECKIN_SNAPSHOT
            }
        }

        return ScoreSnapshotDiff(
            currentSnapshot = current,
            previousSnapshot = previous,
            totalDelta = totalDelta,
            componentDeltas = deltas,
            primaryReason = reason,
            ruleId = ruleId
        )
    }

    fun calculateMonthlyImprovementReport(
        profile: UserProfile,
        envelopes: List<VirtualEnvelope>,
        transactions: List<LedgerTransaction>,
        previousSnapshot: MonthlyHealthSnapshot?,
        nowMillis: Long = System.currentTimeMillis(),
        calendarItems: List<CalendarReminderItem> = emptyList()
    ): MonthlyImprovementReport {
        val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)
        val currentMonthKey = monthFormat.format(java.util.Date(nowMillis))
        val nextMonthCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.MONTH, 1)
        }
        val nextMonthLabel = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(nextMonthCal.time)

        val calc = computeFourPillars(profile, envelopes, transactions, nowMillis)
        val tracedScore = calculateTracedMoneyHealthScore(profile, envelopes, transactions, nowMillis)
        val bufferStatus = calc.bufferStatus
        val typicalSpending = calc.typicalSpending
        val bufferScore = calc.bufferScore
        val spendingScore = calc.spendingScore
        val savingsScore = calc.savingsScore
        val debtScore = calc.debtScore
        val spendRatio = calc.spendRatio
        val savingsRate = calc.savingsRate
        val surplus = calc.surplus
        val totalDebt = calc.totalDebt
        val highRateDebt = calc.highRateDebt
        val currentScore = calc.totalScore

        val prevScore = previousSnapshot?.totalScore
        val scoreDelta = if (prevScore != null) currentScore - prevScore else 0
        val gradeLabel = tracedScore.gradeLabel

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

        val thirtyDaysAgo = nowMillis - (30L * 24L * 60L * 60L * 1000L)
        val topExpenseTransactions = transactions
            .filter { it.type == "EXPENSE" && it.timestamp >= thirtyDaysAgo }
            .sortedByDescending { it.amount }
            .take(3)
        val currency = profile.currencyCode

        // Top 3 Specific Actions with full traceability
        val actions = mutableListOf<MonthlyActionItem>()
        if (!bufferStatus.isBufferMet) {
            val stepAmt = min(bufferStatus.remainingToTarget, max(25.0, bufferStatus.targetAmount * 0.25))
            val simulated = computeFourPillars(profile, envelopes, transactions, nowMillis, extraBufferAmount = stepAmt)
            val delta = simulated.totalScore - currentScore
            val rule = RuleRegistry.findById(RuleRegistry.RULE_BUFFER_FIRST)!!
            val contributors = buildList {
                add(
                    ActionContributorItem(
                        label = "Emergency Safety Net envelope",
                        detail = "Currently ${CurrenciesAndCountries.format(bufferStatus.currentEmergencyAmount, currency)} of ${CurrenciesAndCountries.format(bufferStatus.targetAmount, currency)} target",
                        destination = ActionTargetDestination.SPLIT
                    )
                )
                profile.expenses.sortedByDescending { it.amount }.take(2).forEach { exp ->
                    add(
                        ActionContributorItem(
                            label = "Setup expense: ${exp.category}",
                            detail = "${CurrenciesAndCountries.format(exp.amount, currency)}/mo sets your buffer target",
                            destination = ActionTargetDestination.TRANSACTIONS
                        )
                    )
                }
                topExpenseTransactions.take(2).forEach { tx ->
                    add(
                        ActionContributorItem(
                            label = tx.merchant.ifBlank { tx.notes }.ifBlank { tx.category },
                            detail = "${CurrenciesAndCountries.format(tx.amount, currency)} (${tx.category})",
                            destination = ActionTargetDestination.TRANSACTIONS
                        )
                    )
                }
            }
            val monthsSaved = if (bufferStatus.typicalMonthlySpending > 0.0) {
                bufferStatus.currentEmergencyAmount / bufferStatus.typicalMonthlySpending
            } else {
                0.0
            }
            val bufferSummary = "Build buffer: ${formatCompactMonths(monthsSaved)} months saved (target ${formatCompactMonths(bufferStatus.targetMonths)}) · ${rule.shortName}"
            val bufferSource = if (calc.usesSetupSpendingFallback) {
                "Setup expense baseline"
            } else {
                "30-day logged expenses (${calc.recentExpenseTxCount} tx)"
            }
            val bufferFacts = listOf(
                CalculatedValueFact(
                    id = "V1",
                    label = "Buffer coverage",
                    valueFormatted = "${formatCompactMonths(monthsSaved)} months",
                    unit = "months",
                    formulaPlainWords = "Emergency Safety Net balance divided by typical monthly spending",
                    source = "Virtual Envelope: Emergency Safety Net",
                    inputs = listOf(
                        ScoreInputMetric("Emergency Buffer Saved", CurrenciesAndCountries.format(bufferStatus.currentEmergencyAmount, currency)),
                        ScoreInputMetric("Typical Monthly Spending", CurrenciesAndCountries.format(bufferStatus.typicalMonthlySpending, currency))
                    ),
                    contributors = contributors
                ),
                CalculatedValueFact(
                    id = "V2",
                    label = "Buffer target",
                    valueFormatted = "${formatCompactMonths(bufferStatus.targetMonths)} months",
                    unit = "months",
                    formulaPlainWords = "Target months configured in Safety Buffer settings",
                    source = bufferSource,
                    inputs = listOf(
                        ScoreInputMetric("Target Amount", CurrenciesAndCountries.format(bufferStatus.targetAmount, currency)),
                        ScoreInputMetric("Typical Monthly Spending", CurrenciesAndCountries.format(bufferStatus.typicalMonthlySpending, currency))
                    ),
                    contributors = contributors.filter { it.destination == ActionTargetDestination.TRANSACTIONS }
                ),
                CalculatedValueFact(
                    id = "V3",
                    label = "Emergency savings",
                    valueFormatted = CurrenciesAndCountries.format(bufferStatus.currentEmergencyAmount, currency),
                    unit = currency,
                    formulaPlainWords = "Current saved balance inside Emergency Safety Net envelope",
                    source = "Virtual Envelope: Emergency Safety Net",
                    inputs = listOf(
                        ScoreInputMetric("Emergency Safety Net", CurrenciesAndCountries.format(bufferStatus.currentEmergencyAmount, currency))
                    ),
                    contributors = contributors.take(1)
                ),
                CalculatedValueFact(
                    id = "V4",
                    label = "Suggested step",
                    valueFormatted = CurrenciesAndCountries.format(stepAmt, currency),
                    unit = currency,
                    formulaPlainWords = "25% of target buffer (minimum 25), capped at remaining gap",
                    source = "Buffer-first rule",
                    inputs = listOf(
                        ScoreInputMetric("Buffer Target", CurrenciesAndCountries.format(bufferStatus.targetAmount, currency)),
                        ScoreInputMetric("Remaining Gap", CurrenciesAndCountries.format(bufferStatus.remainingToTarget, currency))
                    ),
                    contributors = contributors.take(1)
                ),
                CalculatedValueFact(
                    id = "V5",
                    label = "Projected score gain",
                    valueFormatted = if (delta > 0) "+$delta pts" else "0 pts",
                    unit = "pts",
                    formulaPlainWords = "Simulated Money Health Score after step minus current score",
                    source = "4-pillar Money Health Score",
                    inputs = listOf(
                        ScoreInputMetric("Current Score", "$currentScore / 100"),
                        ScoreInputMetric("Projected Score", "${simulated.totalScore} / 100")
                    ),
                    contributors = contributors.take(1)
                )
            )
            val bufferChangeCondition = "Reach ${formatCompactMonths(bufferStatus.targetMonths)} month(s) of buffer (${CurrenciesAndCountries.format(bufferStatus.targetAmount, currency)})"
            actions.add(
                MonthlyActionItem(
                    priority = 1,
                    title = "Boost Emergency Safety Net",
                    what = "Move extra surplus into your Emergency Safety Net envelope before investing.",
                    howMuch = CurrenciesAndCountries.format(stepAmt, currency),
                    byWhen = "By $nextMonthLabel",
                    actionKey = "${currentMonthKey}_p1_${RuleRegistry.RULE_BUFFER_FIRST}",
                    ruleId = rule.id,
                    oneLineSummary = bufferSummary,
                    traceability = ActionTraceability(
                        ruleId = rule.id,
                        ruleTitle = rule.title,
                        ruleDescription = rule.plainDescription,
                        triggerCondition = rule.triggerCondition,
                        triggeringNumbers = listOf(
                            ScoreInputMetric("Emergency Buffer Saved", CurrenciesAndCountries.format(bufferStatus.currentEmergencyAmount, currency)),
                            ScoreInputMetric("Months Saved", "${formatCompactMonths(monthsSaved)} mo (target ${formatCompactMonths(bufferStatus.targetMonths)} mo)"),
                            ScoreInputMetric("1-Month Buffer Target", CurrenciesAndCountries.format(bufferStatus.targetAmount, currency)),
                            ScoreInputMetric("Remaining Gap", CurrenciesAndCountries.format(bufferStatus.remainingToTarget, currency)),
                            ScoreInputMetric("Suggested Step (25% of target)", CurrenciesAndCountries.format(stepAmt, currency))
                        ),
                        expectedScoreDelta = delta,
                        projectedNewScore = simulated.totalScore,
                        expectedScoreExplanation = if (delta > 0) {
                            "Adding ${CurrenciesAndCountries.format(stepAmt, currency)} to Emergency Safety Net lifts your score +$delta pts ($currentScore -> ${simulated.totalScore}/100)."
                        } else {
                            "Moves buffer coverage closer to 100% and keeps score steady at $currentScore/100."
                        },
                        dataSources = listOf(
                            "Virtual Envelope: Emergency Safety Net",
                            bufferSource
                        ),
                        contributors = contributors,
                        ruleShortName = rule.shortName,
                        ruleVersion = rule.version,
                        calculatedFacts = bufferFacts,
                        whatWouldChangeThis = bufferChangeCondition
                    )
                )
            )
        } else {
            val goalAmt = max(25.0, surplus * 0.4)
            val rule = RuleRegistry.findById(RuleRegistry.RULE_TOP_GOAL_FUNDING)!!
            val primaryGoal = profile.goals.firstOrNull()
            val contributors = buildList {
                if (primaryGoal != null) {
                    add(
                        ActionContributorItem(
                            label = "Goal: ${primaryGoal.name}",
                            detail = "${CurrenciesAndCountries.format(primaryGoal.currentAmount, currency)} / ${CurrenciesAndCountries.format(primaryGoal.targetAmount, currency)} (${primaryGoal.targetDate})",
                            destination = ActionTargetDestination.GOAL_PLANNER
                        )
                    )
                } else {
                    add(
                        ActionContributorItem(
                            label = "Create a target in Goal Planner",
                            detail = "Monthly surplus available: ${CurrenciesAndCountries.format(max(0.0, surplus), currency)}",
                            destination = ActionTargetDestination.GOAL_PLANNER
                        )
                    )
                }
                add(
                    ActionContributorItem(
                        label = "Route payday split in Auto-Split",
                        detail = "Direct ${CurrenciesAndCountries.format(goalAmt, currency)}/mo into savings envelopes",
                        destination = ActionTargetDestination.SPLIT
                    )
                )
            }
            val goalTriggerText = if (primaryGoal != null) {
                "target ${CurrenciesAndCountries.format(primaryGoal.targetAmount, currency)}"
            } else {
                "surplus ${CurrenciesAndCountries.format(max(0.0, surplus), currency)}"
            }
            val goalSummary = "Fund ${primaryGoal?.name ?: "top goal"}: ${CurrenciesAndCountries.format(goalAmt, currency)}/mo ($goalTriggerText) · ${rule.shortName}"
            val goalFacts = buildList {
                add(
                    CalculatedValueFact(
                        id = "V1",
                        label = "Monthly surplus",
                        valueFormatted = CurrenciesAndCountries.format(surplus, currency),
                        unit = currency,
                        formulaPlainWords = "Effective monthly income minus typical monthly spending",
                        source = "Monthly Cashflow Surplus",
                        inputs = listOf(
                            ScoreInputMetric("Effective Income", CurrenciesAndCountries.format(calc.effectiveIncome, currency)),
                            ScoreInputMetric("Typical Spending", CurrenciesAndCountries.format(calc.typicalSpending, currency))
                        ),
                        contributors = contributors.takeLast(1)
                    )
                )
                add(
                    CalculatedValueFact(
                        id = "V2",
                        label = "Goal allocation",
                        valueFormatted = CurrenciesAndCountries.format(goalAmt, currency),
                        unit = currency,
                        formulaPlainWords = "40% of monthly surplus (minimum 25)",
                        source = "Top goal rule",
                        inputs = listOf(
                            ScoreInputMetric("Monthly Surplus", CurrenciesAndCountries.format(surplus, currency))
                        ),
                        contributors = contributors
                    )
                )
                add(
                    CalculatedValueFact(
                        id = "V3",
                        label = "Active goals",
                        valueFormatted = "${profile.goals.size}",
                        unit = "goals",
                        formulaPlainWords = "Count of active targets saved in Goal Planner",
                        source = "Goal Planner",
                        inputs = listOf(ScoreInputMetric("Saved Goals", "${profile.goals.size}")),
                        contributors = contributors.filter { it.destination == ActionTargetDestination.GOAL_PLANNER }
                    )
                )
                if (primaryGoal != null) {
                    add(
                        CalculatedValueFact(
                            id = "V4",
                            label = "${primaryGoal.name} target",
                            valueFormatted = CurrenciesAndCountries.format(primaryGoal.targetAmount, currency),
                            unit = currency,
                            formulaPlainWords = "Goal target amount set in Goal Planner",
                            source = "Goal Planner: ${primaryGoal.name}",
                            inputs = listOf(
                                ScoreInputMetric("Saved So Far", CurrenciesAndCountries.format(primaryGoal.currentAmount, currency)),
                                ScoreInputMetric("Goal Target", CurrenciesAndCountries.format(primaryGoal.targetAmount, currency))
                            ),
                            contributors = contributors.filter { it.destination == ActionTargetDestination.GOAL_PLANNER }
                        )
                    )
                }
            }
            val goalChangeCondition = if (primaryGoal != null) {
                "Fully fund ${primaryGoal.name} (${CurrenciesAndCountries.format(primaryGoal.targetAmount, currency)})"
            } else {
                "Reach target savings across active Goal Planner goals"
            }
            actions.add(
                MonthlyActionItem(
                    priority = 1,
                    title = "Fund Top Savings Goal",
                    what = "Allocate steady monthly contribution toward your highest-priority Goal Planner target.",
                    howMuch = CurrenciesAndCountries.format(goalAmt, currency),
                    byWhen = "By $nextMonthLabel",
                    actionKey = "${currentMonthKey}_p1_${RuleRegistry.RULE_TOP_GOAL_FUNDING}",
                    ruleId = rule.id,
                    oneLineSummary = goalSummary,
                    traceability = ActionTraceability(
                        ruleId = rule.id,
                        ruleTitle = rule.title,
                        ruleDescription = rule.plainDescription,
                        triggerCondition = rule.triggerCondition,
                        triggeringNumbers = listOf(
                            ScoreInputMetric("Buffer Coverage", "100% funded"),
                            ScoreInputMetric("Monthly Surplus", CurrenciesAndCountries.format(surplus, currency)),
                            ScoreInputMetric("40% Goal Allocation", CurrenciesAndCountries.format(goalAmt, currency)),
                            ScoreInputMetric("Active Goals", "${profile.goals.size}")
                        ),
                        expectedScoreDelta = 0,
                        projectedNewScore = currentScore,
                        expectedScoreExplanation = "Keeps your 30/30 Emergency Buffer intact while advancing your Goal Planner timeline without lowering liquidity.",
                        dataSources = listOf(
                            "Virtual Envelopes (Safety Net met)",
                            "Goal Planner (${profile.goals.size} goal(s))",
                            "Monthly Cashflow Surplus"
                        ),
                        contributors = contributors,
                        ruleShortName = rule.shortName,
                        ruleVersion = rule.version,
                        calculatedFacts = goalFacts,
                        whatWouldChangeThis = goalChangeCondition
                    )
                )
            )
        }

        if (totalDebt > 0.0 && !profile.isUnder18) {
            val topDebt = profile.debts.maxByOrNull { it.interestRate }
            val extraDebtAmt = max(20.0, (topDebt?.minimumPayment ?: 25.0) * 0.5)
            val simulated = computeFourPillars(profile, envelopes, transactions, nowMillis, debtReductionAmount = extraDebtAmt)
            val delta = simulated.totalScore - currentScore
            val rule = RuleRegistry.findById(RuleRegistry.RULE_HIGH_APR_DEBT)!!
            val contributors = profile.debts.sortedByDescending { it.interestRate }.take(3).map { d ->
                ActionContributorItem(
                    label = d.name,
                    detail = "${CurrenciesAndCountries.format(d.totalAmount, currency)} @ ${String.format(Locale.US, "%.1f", d.interestRate)}% APR",
                    destination = ActionTargetDestination.DEBT_PLANNER
                )
            }
            val debtSummary = "Pay extra debt: ${CurrenciesAndCountries.format(extraDebtAmt, currency)} (${String.format(Locale.US, "%.1f", topDebt?.interestRate ?: 0.0)}% APR on ${CurrenciesAndCountries.format(totalDebt, currency)}) · ${rule.shortName}"
            val debtFacts = listOf(
                CalculatedValueFact(
                    id = "V1",
                    label = "Total debt",
                    valueFormatted = CurrenciesAndCountries.format(totalDebt, currency),
                    unit = currency,
                    formulaPlainWords = "Sum of remaining balances across all saved debts",
                    source = "Debt Payoff Planner (${profile.debts.size} debt(s))",
                    inputs = listOf(
                        ScoreInputMetric("Total Debt", CurrenciesAndCountries.format(totalDebt, currency)),
                        ScoreInputMetric("High-APR Balance (>=15%)", CurrenciesAndCountries.format(highRateDebt, currency))
                    ),
                    contributors = contributors
                ),
                CalculatedValueFact(
                    id = "V2",
                    label = "Highest APR",
                    valueFormatted = "${String.format(Locale.US, "%.1f", topDebt?.interestRate ?: 0.0)}%",
                    unit = "% APR",
                    formulaPlainWords = "Maximum annual interest rate across active debts",
                    source = "Debt Payoff Planner: ${topDebt?.name ?: "Debt"}",
                    inputs = listOf(
                        ScoreInputMetric("Top Debt", "${topDebt?.name ?: "Debt"} (${String.format(Locale.US, "%.1f", topDebt?.interestRate ?: 0.0)}%)")
                    ),
                    contributors = contributors.take(1)
                ),
                CalculatedValueFact(
                    id = "V3",
                    label = "Extra payment",
                    valueFormatted = CurrenciesAndCountries.format(extraDebtAmt, currency),
                    unit = currency,
                    formulaPlainWords = "50% of top debt minimum payment (minimum 20)",
                    source = "High-APR debt rule",
                    inputs = listOf(
                        ScoreInputMetric("Top Debt Minimum", CurrenciesAndCountries.format(topDebt?.minimumPayment ?: 0.0, currency))
                    ),
                    contributors = contributors.take(1)
                ),
                CalculatedValueFact(
                    id = "V4",
                    label = "Projected score gain",
                    valueFormatted = if (delta > 0) "+$delta pts" else "0 pts",
                    unit = "pts",
                    formulaPlainWords = "Simulated Money Health Score after extra principal minus current score",
                    source = "4-pillar Money Health Score",
                    inputs = listOf(
                        ScoreInputMetric("Current Score", "$currentScore / 100"),
                        ScoreInputMetric("Projected Score", "${simulated.totalScore} / 100")
                    ),
                    contributors = contributors
                )
            )
            val debtChangeCondition = "Pay total debt down to ${CurrenciesAndCountries.format(0.0, currency)} (clear ${topDebt?.name ?: "balances"})"
            actions.add(
                MonthlyActionItem(
                    priority = 2,
                    title = "Extra Debt Principal Payment",
                    what = "Add an extra payment toward ${topDebt?.name ?: "your highest-APR debt"} using Avalanche/Snowball.",
                    howMuch = CurrenciesAndCountries.format(extraDebtAmt, currency),
                    byWhen = "By $nextMonthLabel",
                    actionKey = "${currentMonthKey}_p2_${RuleRegistry.RULE_HIGH_APR_DEBT}",
                    ruleId = rule.id,
                    oneLineSummary = debtSummary,
                    traceability = ActionTraceability(
                        ruleId = rule.id,
                        ruleTitle = rule.title,
                        ruleDescription = rule.plainDescription,
                        triggerCondition = rule.triggerCondition,
                        triggeringNumbers = listOf(
                            ScoreInputMetric("Total Debt", CurrenciesAndCountries.format(totalDebt, currency)),
                            ScoreInputMetric("Highest APR Debt", "${topDebt?.name ?: "Debt"} (${String.format(Locale.US, "%.1f", topDebt?.interestRate ?: 0.0)}%)"),
                            ScoreInputMetric("High-APR Balance (>=15%)", CurrenciesAndCountries.format(highRateDebt, currency)),
                            ScoreInputMetric("Suggested Extra Principal", CurrenciesAndCountries.format(extraDebtAmt, currency))
                        ),
                        expectedScoreDelta = delta,
                        projectedNewScore = simulated.totalScore,
                        expectedScoreExplanation = if (delta > 0) {
                            "Paying ${CurrenciesAndCountries.format(extraDebtAmt, currency)} toward principal lowers your debt ratio and raises your score +$delta pts ($currentScore -> ${simulated.totalScore}/100)."
                        } else {
                            "Cuts interest charges on ${topDebt?.name ?: "debt"} and accelerates your Debt Load score milestone ($currentScore/100)."
                        },
                        dataSources = listOf("Debt Payoff Planner (${profile.debts.size} debt(s))", "Monthly income profile"),
                        contributors = contributors,
                        ruleShortName = rule.shortName,
                        ruleVersion = rule.version,
                        calculatedFacts = debtFacts,
                        whatWouldChangeThis = debtChangeCondition
                    )
                )
            )
        } else {
            val trimAmt = max(15.0, typicalSpending * 0.05)
            val simulated = computeFourPillars(profile, envelopes, transactions, nowMillis, spendingReductionMonthly = trimAmt)
            val delta = simulated.totalScore - currentScore
            val rule = RuleRegistry.findById(RuleRegistry.RULE_TRIM_SPENDING)!!
            val contributors = buildList {
                topExpenseTransactions.forEach { tx ->
                    add(
                        ActionContributorItem(
                            label = tx.merchant.ifBlank { tx.notes }.ifBlank { tx.category },
                            detail = "${CurrenciesAndCountries.format(tx.amount, currency)} • ${tx.category}",
                            destination = ActionTargetDestination.TRANSACTIONS
                        )
                    )
                }
                if (isEmpty()) {
                    profile.expenses.sortedByDescending { it.amount }.take(3).forEach { exp ->
                        add(
                            ActionContributorItem(
                                label = exp.category,
                                detail = "${CurrenciesAndCountries.format(exp.amount, currency)}/mo baseline",
                                destination = ActionTargetDestination.TRANSACTIONS
                            )
                        )
                    }
                }
            }
            val trimSummary = "Trim spending: save ${CurrenciesAndCountries.format(trimAmt, currency)}/mo (5% of ${CurrenciesAndCountries.format(typicalSpending, currency)}) · ${rule.shortName}"
            val trimSource = if (calc.usesSetupSpendingFallback) {
                "Setup expense categories"
            } else {
                "30-day transactions (${calc.recentExpenseTxCount} tx)"
            }
            val trimFacts = listOf(
                CalculatedValueFact(
                    id = "V1",
                    label = "Typical monthly spending",
                    valueFormatted = CurrenciesAndCountries.format(typicalSpending, currency),
                    unit = currency,
                    formulaPlainWords = "Sum of 30-day expenses or setup monthly expense baseline",
                    source = trimSource,
                    inputs = listOf(
                        ScoreInputMetric("Typical Monthly Spending", CurrenciesAndCountries.format(typicalSpending, currency))
                    ),
                    contributors = contributors
                ),
                CalculatedValueFact(
                    id = "V2",
                    label = "Suggested monthly trim",
                    valueFormatted = CurrenciesAndCountries.format(trimAmt, currency),
                    unit = currency,
                    formulaPlainWords = "5% of typical monthly spending (minimum 15)",
                    source = "Spending trim rule",
                    inputs = listOf(
                        ScoreInputMetric("Typical Spending", CurrenciesAndCountries.format(typicalSpending, currency))
                    ),
                    contributors = contributors
                ),
                CalculatedValueFact(
                    id = "V3",
                    label = "Current savings rate",
                    valueFormatted = "${String.format(Locale.US, "%.1f", savingsRate)}%",
                    unit = "%",
                    formulaPlainWords = "(Effective income minus typical spending) divided by effective income",
                    source = "Monthly cashflow",
                    inputs = listOf(
                        ScoreInputMetric("Effective Income", CurrenciesAndCountries.format(calc.effectiveIncome, currency)),
                        ScoreInputMetric("Typical Spending", CurrenciesAndCountries.format(typicalSpending, currency))
                    ),
                    contributors = contributors
                ),
                CalculatedValueFact(
                    id = "V4",
                    label = "Projected score gain",
                    valueFormatted = if (delta > 0) "+$delta pts" else "0 pts",
                    unit = "pts",
                    formulaPlainWords = "Simulated Money Health Score after monthly trim minus current score",
                    source = "4-pillar Money Health Score",
                    inputs = listOf(
                        ScoreInputMetric("Current Score", "$currentScore / 100"),
                        ScoreInputMetric("Projected Score", "${simulated.totalScore} / 100")
                    ),
                    contributors = contributors
                )
            )
            val targetReducedSpend = max(0.0, typicalSpending - trimAmt)
            val trimChangeCondition = "Lower monthly spending to ${CurrenciesAndCountries.format(targetReducedSpend, currency)}/mo (or lift savings rate to 20%)"
            actions.add(
                MonthlyActionItem(
                    priority = 2,
                    title = "Trim Non-Essential Spending",
                    what = "Review subscriptions & impulse buys to free up monthly cashflow.",
                    howMuch = "Save ${CurrenciesAndCountries.format(trimAmt, currency)}/mo",
                    byWhen = "Within 14 days",
                    actionKey = "${currentMonthKey}_p2_${RuleRegistry.RULE_TRIM_SPENDING}",
                    ruleId = rule.id,
                    oneLineSummary = trimSummary,
                    traceability = ActionTraceability(
                        ruleId = rule.id,
                        ruleTitle = rule.title,
                        ruleDescription = rule.plainDescription,
                        triggerCondition = rule.triggerCondition,
                        triggeringNumbers = listOf(
                            ScoreInputMetric("Typical Monthly Spending", CurrenciesAndCountries.format(typicalSpending, currency)),
                            ScoreInputMetric("5% Trim Target", CurrenciesAndCountries.format(trimAmt, currency)),
                            ScoreInputMetric("Current Savings Rate", "${String.format(Locale.US, "%.1f", savingsRate)}%")
                        ),
                        expectedScoreDelta = delta,
                        projectedNewScore = simulated.totalScore,
                        expectedScoreExplanation = if (delta > 0) {
                            "Trimming ${CurrenciesAndCountries.format(trimAmt, currency)}/mo raises your savings/spending efficiency by +$delta pts ($currentScore -> ${simulated.totalScore}/100)."
                        } else {
                            "Frees ${CurrenciesAndCountries.format(trimAmt, currency)}/mo toward your buffer and protects your Spending & Savings scores ($currentScore/100)."
                        },
                        dataSources = listOf(
                            if (calc.usesSetupSpendingFallback) "Setup expense categories" else "30-day logged/imported transactions (${calc.recentExpenseTxCount} tx)",
                            "Recurring subscription scan"
                        ),
                        contributors = contributors,
                        ruleShortName = rule.shortName,
                        ruleVersion = rule.version,
                        calculatedFacts = trimFacts,
                        whatWouldChangeThis = trimChangeCondition
                    )
                )
            )
        }

        val checkInGoalAmt = max(10.0, typicalSpending * 0.03)
        val checkInRule = RuleRegistry.findById(RuleRegistry.RULE_CHECKIN_SNAPSHOT)!!
        val upcoming30 = getUpcomingDeadlines(calendarItems, nowMillis = nowMillis, horizonDays = 30)
        val checkInContributors = buildList {
            upcoming30.take(2).forEach { up ->
                add(
                    ActionContributorItem(
                        label = "Upcoming: ${up.item.title}",
                        detail = "${CurrenciesAndCountries.format(up.item.amount, currency)} due in ${up.daysUntilDue} day(s)",
                        destination = ActionTargetDestination.CALENDAR
                    )
                )
            }
            add(
                ActionContributorItem(
                    label = "Net Worth & Monthly Trend",
                    detail = "Save this month's assets vs debts snapshot",
                    destination = ActionTargetDestination.NET_WORTH
                )
            )
            if (upcoming30.isEmpty()) {
                add(
                    ActionContributorItem(
                        label = "Deadline & Renewal Calendar",
                        detail = "No unpaid renewals logged in next 30 days",
                        destination = ActionTargetDestination.CALENDAR
                    )
                )
            }
        }
        val checkInSummary = "Monthly check-in: ${upcoming30.size} upcoming renewal(s) (reserve ${CurrenciesAndCountries.format(checkInGoalAmt, currency)}) · ${checkInRule.shortName}"
        val checkInFacts = listOf(
            CalculatedValueFact(
                id = "V1",
                label = "Upcoming 30-day renewals",
                valueFormatted = "${upcoming30.size}",
                unit = "items",
                formulaPlainWords = "Count of unpaid renewals due within the next 30 days",
                source = "Deadline & Renewal Calendar",
                inputs = listOf(ScoreInputMetric("Upcoming 30-Day Renewals", "${upcoming30.size} item(s)")),
                contributors = checkInContributors.filter { it.destination == ActionTargetDestination.CALENDAR }
            ),
            CalculatedValueFact(
                id = "V2",
                label = "Surprise-bill reserve",
                valueFormatted = CurrenciesAndCountries.format(checkInGoalAmt, currency),
                unit = currency,
                formulaPlainWords = "3% of typical monthly spending (minimum 10)",
                source = "Monthly check-in rule",
                inputs = listOf(ScoreInputMetric("Monthly Spending Baseline", CurrenciesAndCountries.format(typicalSpending, currency))),
                contributors = checkInContributors
            ),
            CalculatedValueFact(
                id = "V3",
                label = "Monthly spending baseline",
                valueFormatted = CurrenciesAndCountries.format(typicalSpending, currency),
                unit = currency,
                formulaPlainWords = "30-day expense total or setup expense baseline",
                source = "Monthly Cashflow",
                inputs = listOf(ScoreInputMetric("Monthly Spending Baseline", CurrenciesAndCountries.format(typicalSpending, currency))),
                contributors = checkInContributors
            )
        )
        val checkInChangeCondition = "Save $currentMonthKey monthly snapshot & clear ${upcoming30.size} due renewal(s)"
        actions.add(
            MonthlyActionItem(
                priority = 3,
                title = "Run Next Month's Check-In & Snapshot",
                what = "Save a Net Worth snapshot and verify upcoming 30-day calendar renewals.",
                howMuch = "Protect ${CurrenciesAndCountries.format(checkInGoalAmt, currency)} from surprise bills",
                byWhen = "By $nextMonthLabel",
                actionKey = "${currentMonthKey}_p3_${RuleRegistry.RULE_CHECKIN_SNAPSHOT}",
                ruleId = checkInRule.id,
                oneLineSummary = checkInSummary,
                traceability = ActionTraceability(
                    ruleId = checkInRule.id,
                    ruleTitle = checkInRule.title,
                    ruleDescription = checkInRule.plainDescription,
                    triggerCondition = checkInRule.triggerCondition,
                    triggeringNumbers = listOf(
                        ScoreInputMetric("Monthly Spending Baseline", CurrenciesAndCountries.format(typicalSpending, currency)),
                        ScoreInputMetric("3% Surprise-Bill Reserve", CurrenciesAndCountries.format(checkInGoalAmt, currency)),
                        ScoreInputMetric("Upcoming 30-Day Renewals", "${upcoming30.size} item(s)")
                    ),
                    expectedScoreDelta = 0,
                    projectedNewScore = currentScore,
                    expectedScoreExplanation = "Keeps your score history and upcoming deadline cashflow up to date so trends stay accurate next month.",
                    dataSources = listOf(
                        "Monthly Score History snapshots",
                        "Deadline & Renewal Calendar",
                        "Net Worth assets & debts"
                    ),
                    contributors = checkInContributors,
                    ruleShortName = checkInRule.shortName,
                    ruleVersion = checkInRule.version,
                    calculatedFacts = checkInFacts,
                    whatWouldChangeThis = checkInChangeCondition
                )
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
            currentSnapshot = currentSnapshot,
            tracedScore = tracedScore,
            dataQualityNotes = tracedScore.dataQualityNotes
        )
    }

    fun formatCompactMonths(months: Double): String {
        val rounded = (months * 10.0).roundToInt() / 10.0
        return if (kotlin.math.abs(rounded - rounded.toInt()) < 0.05) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }

    fun resolveActionOneLineSummary(action: MonthlyActionItem): String {
        if (action.oneLineSummary.isNotBlank()) return action.oneLineSummary
        val ruleName = action.traceability?.ruleShortName?.ifBlank { null }
            ?: RuleRegistry.displayRuleName(action.ruleId)
        val keyNum = action.traceability?.triggeringNumbers?.firstOrNull()?.let {
            "${it.label}: ${it.valueFormatted}"
        } ?: action.howMuch
        return "${action.title}: $keyNum · $ruleName"
    }

    fun defaultRuleNameForCategory(category: String): String {
        return when {
            category.contains("Allocation", ignoreCase = true) -> "Auto-split rule"
            category.contains("Setting", ignoreCase = true) -> "User settings rule"
            category.contains("Check-In", ignoreCase = true) -> "Check-in rule"
            else -> "Manual review rule"
        }
    }

    fun buildActivityOneLineSummary(
        title: String,
        numbersSummary: String,
        whatChanged: String,
        ruleId: String?,
        category: String
    ): String {
        val cleanTitle = title.trim().removeSuffix("?").ifBlank { category }
        val ruleName = RuleRegistry.displayRuleName(
            ruleId = ruleId,
            categoryFallback = defaultRuleNameForCategory(category)
        )
        val rawNum = numbersSummary.trim()
            .ifBlank { whatChanged.trim() }
            .substringBefore(";")
            .substringBefore("\n")
            .trim()
        val keyNumber = if (rawNum.length > 48) rawNum.take(45).trimEnd() + "..." else rawNum
        return if (keyNumber.isNotBlank()) {
            "$cleanTitle: $keyNumber · $ruleName"
        } else {
            "$cleanTitle · $ruleName"
        }
    }

    fun topAffectingDataQualityNote(notes: List<DataQualityNote>): DataQualityNote? {
        val low = notes.filter { it.isLowQuality }
        if (low.isEmpty()) return null
        return low.firstOrNull { it.id == "dq_no_tx" || it.id == "dq_short_tx" } ?: low.first()
    }

    fun inferUnitFromFormattedValue(valueFormatted: String, label: String = ""): String {
        val trimmed = valueFormatted.trim()
        return when {
            trimmed.contains("month", ignoreCase = true) || trimmed.contains(" mo", ignoreCase = true) -> "months"
            trimmed.contains("%") -> "%"
            trimmed.contains("pt", ignoreCase = true) || trimmed.endsWith("/100") -> "pts"
            trimmed.contains("item", ignoreCase = true) || label.contains("goal", ignoreCase = true) -> "items"
            trimmed.any { it.isLetter() } -> {
                val letters = trimmed.takeWhile { !it.isDigit() && it != '-' }.trim()
                if (letters.isNotBlank()) letters else "units"
            }
            else -> "amount"
        }
    }

    fun buildFactsForActivityEntry(
        title: String,
        category: String,
        numbersSummary: String,
        ruleId: String?,
        snapshotInputs: List<ScoreInputMetric>,
        snapshotTotalScore: Int?
    ): List<CalculatedValueFact> {
        val ruleDef = ruleId?.let { RuleRegistry.findById(it) }
        val baseInputs = if (snapshotInputs.isNotEmpty()) {
            snapshotInputs
        } else {
            numbersSummary.split(";").mapNotNull { part ->
                val clean = part.trim()
                if (clean.isBlank()) null
                else if (clean.contains(":")) {
                    val idx = clean.indexOf(":")
                    ScoreInputMetric(clean.substring(0, idx).trim(), clean.substring(idx + 1).trim(), "Confirmed snapshot")
                } else {
                    ScoreInputMetric("Recorded figure", clean, "Confirmed snapshot")
                }
            }
        }
        val list = baseInputs.take(6).mapIndexed { index, metric ->
            CalculatedValueFact(
                id = "V${index + 1}",
                label = metric.label.ifBlank { title },
                valueFormatted = metric.valueFormatted,
                unit = inferUnitFromFormattedValue(metric.valueFormatted, metric.label),
                formulaPlainWords = ruleDef?.plainDescription ?: "Value recorded from your confirmed $category entry",
                source = metric.sourceNote.ifBlank { "Confirmed activity snapshot" },
                inputs = listOf(metric)
            )
        }.toMutableList()
        if (snapshotTotalScore != null) {
            list.add(
                CalculatedValueFact(
                    id = "V${list.size + 1}",
                    label = "Money Health Score",
                    valueFormatted = "$snapshotTotalScore/100",
                    unit = "pts",
                    formulaPlainWords = "Sum of Emergency Buffer, Spending, Savings, and Debt pillars",
                    source = "Money Health Score snapshot",
                    inputs = listOf(ScoreInputMetric("Money Health Score", "$snapshotTotalScore / 100"))
                )
            )
        }
        return list
    }

    fun buildActionFallbackTemplate(action: MonthlyActionItem, facts: List<CalculatedValueFact>): String {
        val byId = facts.associateBy { it.id }
        return when (action.ruleId) {
            RuleRegistry.RULE_BUFFER_FIRST -> {
                if (byId.containsKey("V1") && byId.containsKey("V2") && byId.containsKey("V4")) {
                    "Your safety buffer is at {{V1}} toward the {{V2}} target, so adding {{V4}} moves you closer to full coverage. (Educational guidance only.)"
                } else {
                    defaultFactsTemplate(action.what, facts)
                }
            }
            RuleRegistry.RULE_TOP_GOAL_FUNDING -> {
                if (byId.containsKey("V1") && byId.containsKey("V2")) {
                    "With {{V1}} in monthly surplus, directing {{V2}} toward your top Goal Planner target keeps progress steady without hurting liquidity. (Educational guidance only.)"
                } else {
                    defaultFactsTemplate(action.what, facts)
                }
            }
            RuleRegistry.RULE_HIGH_APR_DEBT -> {
                if (byId.containsKey("V1") && byId.containsKey("V2") && byId.containsKey("V3")) {
                    "Paying an extra {{V3}} toward debt at {{V2}} helps lower your {{V1}} balance faster and saves on interest. (Educational guidance only.)"
                } else {
                    defaultFactsTemplate(action.what, facts)
                }
            }
            RuleRegistry.RULE_TRIM_SPENDING -> {
                if (byId.containsKey("V1") && byId.containsKey("V2") && byId.containsKey("V3")) {
                    "Trimming {{V2}} from your {{V1}} monthly spending boosts your {{V3}} savings rate safely. (Educational guidance only.)"
                } else {
                    defaultFactsTemplate(action.what, facts)
                }
            }
            RuleRegistry.RULE_CHECKIN_SNAPSHOT -> {
                if (byId.containsKey("V1") && byId.containsKey("V2")) {
                    "Reviewing {{V1}} upcoming renewals and keeping a {{V2}} reserve prevents surprise bills from disrupting your plan. (Educational guidance only.)"
                } else {
                    defaultFactsTemplate(action.what, facts)
                }
            }
            else -> defaultFactsTemplate(action.what, facts)
        }
    }

    fun buildActivityFallbackTemplate(
        title: String,
        ruleWording: String,
        facts: List<CalculatedValueFact>
    ): String {
        val cleanWording = ruleWording.replace(Regex("\\d"), "").trim()
        return if (facts.size >= 2) {
            "$title recorded with {{${facts[0].id}}} and {{${facts[1].id}}} from your saved figures. ${cleanWording.ifBlank { "Confirmed from stored inputs." }} (Educational guidance only.)"
        } else if (facts.size == 1) {
            "$title recorded with {{${facts[0].id}}} from your saved figures. ${cleanWording.ifBlank { "Confirmed from stored inputs." }} (Educational guidance only.)"
        } else {
            "$title was confirmed from your stored inputs. (Educational guidance only.)"
        }
    }

    private fun defaultFactsTemplate(actionSummary: String, facts: List<CalculatedValueFact>): String {
        val cleanText = actionSummary.replace(Regex("\\d"), "").trim().ifBlank { "This recommended step uses your stored numbers." }
        return if (facts.size >= 2) {
            "$cleanText Key figures checked: {{${facts[0].id}}} and {{${facts[1].id}}}. (Educational guidance only.)"
        } else if (facts.size == 1) {
            "$cleanText Key figure checked: {{${facts[0].id}}}. (Educational guidance only.)"
        } else {
            "$cleanText (Educational guidance only.)"
        }
    }

    private val PLACEHOLDER_REGEX = Regex("\\{\\{([^}]+)\\}\\}")
    private val DIGIT_REGEX = Regex("\\d")

    fun resolveTemplateToPlainText(
        templateWithIds: String,
        facts: List<CalculatedValueFact>
    ): String {
        val byId = facts.associateBy { it.id }
        return PLACEHOLDER_REGEX.replace(templateWithIds) { match ->
            val id = match.groupValues[1].trim()
            byId[id]?.valueFormatted ?: match.value
        }
    }

    /**
     * Strictly validates AI output that must refer to calculated facts ONLY via {{V#}} IDs.
     * Any unknown ID, unbraced/raw digit not inside a valid {{V#}} tag, or missing fact reference
     * when facts exist triggers blocking and returns the code-built template fallback with note:
     * "Showing a simple explanation."
     */
    fun validateAiReferenceExplanation(
        rawAiText: String?,
        facts: List<CalculatedValueFact>,
        fallbackTemplate: String
    ): TraceableAiExplanation {
        fun buildFallback(): TraceableAiExplanation {
            return TraceableAiExplanation(
                templateWithIds = fallbackTemplate,
                plainResolvedText = resolveTemplateToPlainText(fallbackTemplate, facts),
                facts = facts,
                isFallback = true,
                note = "Showing a simple explanation."
            )
        }

        val candidate = rawAiText?.trim().orEmpty()
        if (candidate.isBlank()) return buildFallback()

        val validIds = facts.map { it.id }.toSet()
        val matches = PLACEHOLDER_REGEX.findAll(candidate).toList()
        val referencedIds = mutableListOf<String>()

        for (m in matches) {
            val id = m.groupValues[1].trim()
            if (!validIds.contains(id)) {
                return buildFallback()
            }
            referencedIds.add(id)
        }

        if (facts.isNotEmpty() && referencedIds.isEmpty()) {
            return buildFallback()
        }

        // Remove all valid {{V#}} placeholders and ensure zero digits remain in the AI-written prose
        val strippedText = PLACEHOLDER_REGEX.replace(candidate, " ")
        if (DIGIT_REGEX.containsMatchIn(strippedText)) {
            return buildFallback()
        }

        val normalizedTemplate = PLACEHOLDER_REGEX.replace(candidate) { m ->
            "{{${m.groupValues[1].trim()}}}"
        }
        return TraceableAiExplanation(
            templateWithIds = normalizedTemplate,
            plainResolvedText = resolveTemplateToPlainText(normalizedTemplate, facts),
            facts = facts,
            isFallback = false,
            note = ""
        )
    }

    // === PRIVACY-SAFE SCORE CARD SHARING, ANONYMOUS REFERRALS & MILESTONE BADGES ===

    const val GROWMINT_APP_URL = "https://ais-pre-wt4cpwje43gerrmnerzzuo-428422984777.asia-northeast1.run.app"
    const val GROWMINT_DEV_APP_URL = "https://ais-dev-wt4cpwje43gerrmnerzzuo-428422984777.asia-northeast1.run.app"
    const val SCORE_CARD_DISCLAIMER = "Educational tool, not financial advice."
    const val BADGE_REWARDS_NOTE = "Rewards may change as the app grows."

    private val REF_QUERY_REGEX = Regex("(?i)[?&]ref=([A-Z0-9]{6,12})")
    private val RAW_REF_CODE_REGEX = Regex("^[A-Z0-9]{6,12}$")
    private val CURRENCY_OR_SENSITIVE_REGEX = Regex("([$€£¥₹₩₽₦]\\s*\\d[\\d,.]*|\\b\\d{3,}[\\d,.]*\\b|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})")

    fun calculateScoreCardLabel(score: Int): String {
        val clamped = score.coerceIn(0, 100)
        return when {
            clamped >= 85 -> "Excellent"
            clamped >= 70 -> "Strong"
            clamped >= 45 -> "Building"
            else -> "Getting started"
        }
    }

    fun calculateComponentLevel(earnedPoints: Int, maxPoints: Int): ScoreCardBarLevel {
        if (maxPoints <= 0) return ScoreCardBarLevel.LOW
        val ratio = earnedPoints.toDouble() / maxPoints.toDouble()
        return when {
            ratio >= 0.75 -> ScoreCardBarLevel.GOOD
            ratio >= 0.40 -> ScoreCardBarLevel.OKAY
            else -> ScoreCardBarLevel.LOW
        }
    }

    private fun shortComponentBarName(rawName: String): String {
        return when {
            rawName.contains("Buffer", ignoreCase = true) -> "Emergency Buffer"
            rawName.contains("Spend", ignoreCase = true) -> "Spending Control"
            rawName.contains("Saving", ignoreCase = true) -> "Savings Habit"
            rawName.contains("Debt", ignoreCase = true) -> "Debt Load"
            else -> rawName.take(20)
        }
    }

    fun sanitizeNicknameForShareCard(raw: String): String {
        val noSensitive = CURRENCY_OR_SENSITIVE_REGEX.replace(raw, "")
        val cleaned = noSensitive
            .replace(Regex("[\\d@$€£¥₹₩₽₦\\r\\n]"), "")
            .trim()
        return cleaned.take(20)
    }

    fun formatMonthDeltaForCard(delta: Int): String {
        return when {
            delta > 0 -> "+$delta this month"
            delta < 0 -> "$delta this month"
            else -> "0 this month"
        }
    }

    fun buildPrivacySafeScoreCardData(
        report: MonthlyImprovementReport,
        isUnder18: Boolean,
        requestedShowMonthlyChange: Boolean = false,
        requestedShowComponentBars: Boolean = false,
        requestedShowNickname: Boolean = false,
        requestedNickname: String = "",
        style: ScoreCardThemeStyle = ScoreCardThemeStyle.DAY
    ): ScoreCardData {
        val clampedScore = report.currentScore.coerceIn(0, 100)
        val hasPreviousScore = report.previousScore != null
        val monthDelta = if (hasPreviousScore) report.scoreDelta else null

        val tracedComponents = report.tracedScore?.components.orEmpty()
        val bars = if (tracedComponents.isNotEmpty()) {
            tracedComponents.take(4).map { comp ->
                ScoreCardComponentBar(
                    name = shortComponentBarName(comp.name),
                    level = calculateComponentLevel(comp.earnedPoints, comp.maxPoints)
                )
            }
        } else {
            report.pillarBreakdown.take(4).map { pillar ->
                ScoreCardComponentBar(
                    name = shortComponentBarName(pillar.category),
                    level = calculateComponentLevel(pillar.earnedPoints, pillar.maxPoints)
                )
            }
        }

        // Guided Mode (<18): enforce strict private defaults (no nickname, no bars, no external sharing)
        val effectiveShowChange = if (isUnder18) false else (requestedShowMonthlyChange && monthDelta != null)
        val effectiveShowBars = if (isUnder18) false else requestedShowComponentBars
        val effectiveNickname = if (isUnder18) "" else sanitizeNicknameForShareCard(requestedNickname)
        val effectiveShowNickname = if (isUnder18) false else (requestedShowNickname && effectiveNickname.isNotBlank())

        return ScoreCardData(
            score = clampedScore,
            scoreLabel = calculateScoreCardLabel(clampedScore),
            monthDelta = monthDelta,
            componentBars = bars,
            nickname = effectiveNickname,
            showMonthlyChange = effectiveShowChange,
            showComponentBars = effectiveShowBars,
            showNickname = effectiveShowNickname,
            style = style,
            footerDisclaimer = SCORE_CARD_DISCLAIMER,
            appUrl = GROWMINT_APP_URL
        )
    }

    fun isValidReferralCodeFormat(code: String?): Boolean {
        val clean = code?.trim()?.uppercase(Locale.US).orEmpty()
        return RAW_REF_CODE_REGEX.matches(clean)
    }

    fun parseReferralCodeFromUriOrText(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        val queryMatch = REF_QUERY_REGEX.find(trimmed)
        if (queryMatch != null) {
            return queryMatch.groupValues[1].uppercase(Locale.US)
        }
        val upper = trimmed.uppercase(Locale.US)
        return if (RAW_REF_CODE_REGEX.matches(upper)) upper else null
    }

    fun buildReferralLink(code: String?): String {
        val clean = code?.trim()?.uppercase(Locale.US)
        return if (!clean.isNullOrBlank() && isValidReferralCodeFormat(clean)) {
            "$GROWMINT_APP_URL?ref=$clean"
        } else {
            GROWMINT_APP_URL
        }
    }

    fun sanitizeInviteMessage(raw: String): String {
        val cleaned = CURRENCY_OR_SENSITIVE_REGEX.replace(raw, "").replace(Regex("\\s{2,}"), " ").trim()
        return if (cleaned.isBlank()) ReferralState.DEFAULT_INVITE_MESSAGE else cleaned.take(220)
    }

    fun buildPrivacySafeShareText(
        card: ScoreCardData,
        includeReferralLink: Boolean,
        referralCode: String?
    ): String {
        val parts = mutableListOf<String>()
        val header = if (card.showNickname && card.nickname.isNotBlank()) {
            "${card.nickname}'s GrowMint Money Health Score: ${card.score}/100 (${card.scoreLabel})."
        } else {
            "My GrowMint Money Health Score: ${card.score}/100 (${card.scoreLabel})."
        }
        parts.add(header)
        if (card.showMonthlyChange && card.monthDelta != null) {
            parts.add("Progress: ${formatMonthDeltaForCard(card.monthDelta)}.")
        }
        val url = if (includeReferralLink) buildReferralLink(referralCode) else GROWMINT_APP_URL
        parts.add(card.footerDisclaimer)
        parts.add(url)
        return parts.joinToString(" ")
    }

    fun calculateMilestoneBadges(
        referralCount: Int,
        currentScore: Int,
        monthlySnapshotsCount: Int
    ): List<MilestoneBadge> {
        val safeRefs = maxOf(0, referralCount)
        val safeScore = currentScore.coerceIn(0, 100)
        val safeMonths = maxOf(0, monthlySnapshotsCount)
        return listOf(
            MilestoneBadge(
                id = "first_invite",
                title = "First invite",
                description = "Shared GrowMint and had your first friend complete setup.",
                isUnlocked = safeRefs >= 1,
                progressText = "${minOf(safeRefs, 1)}/1 invite"
            ),
            MilestoneBadge(
                id = "three_friends_joined",
                title = "3 friends joined",
                description = "Helped 3 friends start building steadier money habits.",
                isUnlocked = safeRefs >= 3,
                progressText = "${minOf(safeRefs, 3)}/3 invites"
            ),
            MilestoneBadge(
                id = "score_70_plus",
                title = "Score 70+",
                description = "Reached a Money Health Score of 70 or higher.",
                isUnlocked = safeScore >= 70,
                progressText = "$safeScore/70 score"
            ),
            MilestoneBadge(
                id = "three_month_streak",
                title = "3-month streak",
                description = "Tracked monthly Money Health snapshots for 3 months.",
                isUnlocked = safeMonths >= 3,
                progressText = "${minOf(safeMonths, 3)}/3 months"
            )
        )
    }
}


