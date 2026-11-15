package com.example

import com.example.data.FinancialCalculator
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test

class FinancialCalculatorTest {

    @Test
    fun bufferGate_locksWhenEmergencyBelowTarget_andUnlocksWhenMet() {
        val profile = UserProfile(
            expenses = listOf(
                ExpenseItem(category = "Rent", amount = 800.0),
                ExpenseItem(category = "Food", amount = 400.0)
            ),
            debts = listOf(
                DebtItem(name = "Card", totalAmount = 1000.0, interestRate = 18.0, minimumPayment = 50.0)
            ),
            emergencyBufferMonthsTarget = 1.0,
            isUnder18 = false
        )
        // Typical monthly spending = 800 + 400 + 50 = 1250.0
        val lowBufferEnvelopes = listOf(
            VirtualEnvelope(id = "e1", type = EnvelopeType.EMERGENCY_FUND, name = "Emergency", balance = 625.0)
        )
        val lockedStatus = FinancialCalculator.calculateBufferGateStatus(profile, lowBufferEnvelopes)
        assertEquals(1250.0, lockedStatus.typicalMonthlySpending, 0.01)
        assertEquals(1250.0, lockedStatus.targetAmount, 0.01)
        assertEquals(0.5f, lockedStatus.progressFraction, 0.01f)
        assertFalse(lockedStatus.isBufferMet)
        assertFalse(lockedStatus.isInvestingUnlocked)

        val fundedEnvelopes = listOf(
            VirtualEnvelope(id = "e1", type = EnvelopeType.EMERGENCY_FUND, name = "Emergency", balance = 1300.0)
        )
        val unlockedStatus = FinancialCalculator.calculateBufferGateStatus(profile, fundedEnvelopes)
        assertTrue(unlockedStatus.isBufferMet)
        assertTrue(unlockedStatus.isInvestingUnlocked)
        assertEquals(0.0, unlockedStatus.remainingToTarget, 0.01)
    }

    @Test
    fun bufferGate_alwaysLocksInvestingForGuidedModeUnder18() {
        val under18Profile = UserProfile(
            expenses = listOf(ExpenseItem(category = "Needs", amount = 500.0)),
            emergencyBufferMonthsTarget = 1.0,
            isUnder18 = true
        )
        val fundedEnvelopes = listOf(
            VirtualEnvelope(id = "e1", type = EnvelopeType.EMERGENCY_FUND, name = "Emergency", balance = 2000.0)
        )
        val status = FinancialCalculator.calculateBufferGateStatus(under18Profile, fundedEnvelopes)
        assertTrue(status.isBufferMet)
        assertTrue(status.isGuidedModeUnder18)
        assertFalse(status.isInvestingUnlocked)
    }

    @Test
    fun holdingPotRunway_computesExactMonths() {
        assertEquals(2.5, FinancialCalculator.calculateHoldingPotRunwayMonths(5000.0, 2000.0), 0.001)
        assertEquals(0.0, FinancialCalculator.calculateHoldingPotRunwayMonths(5000.0, 0.0), 0.001)
        assertEquals(0.0, FinancialCalculator.calculateHoldingPotRunwayMonths(-100.0, 1500.0), 0.001)
    }

    @Test
    fun sideHustleReport_calculatesProfitMarginBestAndWorstMonth() {
        val entries = listOf(
            SideHustleEntry(hustleName = "Design", monthKey = "2026-01", income = 1000.0, expenses = 200.0),
            SideHustleEntry(hustleName = "Editing", monthKey = "2026-01", income = 500.0, expenses = 100.0),
            SideHustleEntry(hustleName = "Design", monthKey = "2026-02", income = 600.0, expenses = 400.0)
        )
        val report = FinancialCalculator.calculateSideHustleReport(entries)
        assertEquals(2100.0, report.totalIncome, 0.01)
        assertEquals(700.0, report.totalExpenses, 0.01)
        assertEquals(1400.0, report.totalProfit, 0.01)
        assertEquals((1400.0 / 2100.0) * 100.0, report.overallMarginPercent, 0.01)
        assertEquals("2026-01", report.bestMonth?.monthKey)
        assertEquals(1200.0, report.bestMonth?.netProfit ?: 0.0, 0.01)
        assertEquals("2026-02", report.worstMonth?.monthKey)
        assertEquals(200.0, report.worstMonth?.netProfit ?: 0.0, 0.01)
    }

    @Test
    fun upcomingDeadlines_filtersWithin30DaysAndFlagsDueSoon() {
        val now = 1_700_000_000_000L
        val dayMs = 24L * 60L * 60L * 1000L
        val items = listOf(
            CalendarReminderItem(title = "Rent", amount = 900.0, dueDateMillis = now + 3 * dayMs, repeat = RepeatFrequency.ONE_OFF),
            CalendarReminderItem(title = "Exam Fee", amount = 120.0, dueDateMillis = now + 15 * dayMs, repeat = RepeatFrequency.ONE_OFF),
            CalendarReminderItem(title = "Far Renewal", amount = 50.0, dueDateMillis = now + 45 * dayMs, repeat = RepeatFrequency.ONE_OFF),
            CalendarReminderItem(title = "Already Paid", amount = 40.0, dueDateMillis = now + 2 * dayMs, repeat = RepeatFrequency.ONE_OFF, isPaid = true)
        )
        val upcoming = FinancialCalculator.getUpcomingDeadlines(items, nowMillis = now, horizonDays = 30)
        assertEquals(2, upcoming.size)
        assertEquals("Rent", upcoming[0].item.title)
        assertTrue(upcoming[0].isDueSoon)
        assertEquals("Exam Fee", upcoming[1].item.title)
        assertFalse(upcoming[1].isDueSoon)
    }

    @Test
    fun sipRealityCheck_ordersThreeCasesAndIncludesDisclaimer() {
        val report = FinancialCalculator.calculateSipRealityCheck(
            monthlyContribution = 200.0,
            initialAmount = 1000.0,
            years = 10,
            assetMix = "Equity Index / SIP"
        )
        assertEquals(25000.0, report.totalInvested, 0.01)
        assertTrue(report.poorCase.finalValue < report.typicalCase.finalValue)
        assertTrue(report.typicalCase.finalValue < report.goodCase.finalValue)
        assertTrue(report.worstYearLossAmountOnTypical > 0.0)
        assertEquals("Illustrative, not a prediction. Returns are not guaranteed.", report.disclaimer)
    }

    @Test
    fun weeklyMoneyScore_boundsAndBreakdownSumMatchTotal() {
        val profile = UserProfile(
            expenses = listOf(ExpenseItem(category = "Living", amount = 1000.0)),
            emergencyBufferMonthsTarget = 1.0
        )
        val envelopes = listOf(
            VirtualEnvelope(id = "e1", type = EnvelopeType.EMERGENCY_FUND, name = "Emergency", balance = 500.0)
        )
        val score = FinancialCalculator.calculateWeeklyMoneyScore(
            profile = profile,
            envelopes = envelopes,
            transactions = emptyList(),
            noSpendStreakDays = 3,
            transferHistory = emptyList()
        )
        assertTrue(score.totalScore in 0..100)
        assertEquals(3, score.breakdown.size)
        assertEquals(score.totalScore, score.breakdown.sumOf { it.earnedPoints })
    }

    @Test
    fun microLessons_hasSixDistinctTopicsAndRotates() {
        assertEquals(6, FinancialCalculator.allMicroLessons.size)
        val lesson0 = FinancialCalculator.getDailyMicroLesson(0)
        val lesson6 = FinancialCalculator.getDailyMicroLesson(6)
        assertEquals(lesson0.topic, lesson6.topic)
    }

    @Test
    fun debtPayoffComparison_avalancheSavesInterestVsSnowball() {
        val debts = listOf(
            DebtItem(name = "Small Low-APR", totalAmount = 1000.0, interestRate = 6.0, minimumPayment = 50.0),
            DebtItem(name = "Big High-APR", totalAmount = 4000.0, interestRate = 24.0, minimumPayment = 120.0)
        )
        val report = FinancialCalculator.calculateDebtPayoffComparison(debts, extraMonthlyPayment = 200.0)
        assertTrue(report.snowball.monthsToPayoff > 0)
        assertTrue(report.avalanche.monthsToPayoff > 0)
        assertTrue(report.avalanche.totalInterestPaid <= report.snowball.totalInterestPaid)
        assertTrue(report.snowball.schedule.isNotEmpty())
        assertTrue(report.avalanche.schedule.isNotEmpty())
        assertTrue(report.differenceOneLiner.isNotBlank())
    }

    @Test
    fun netWorthSummary_addsManualAndLinkedMinusDebts() {
        val manual = listOf(
            AssetItem(name = "Wallet Cash", category = AssetCategory.CASH, amount = 500.0),
            AssetItem(name = "Savings", category = AssetCategory.SAVINGS, amount = 3500.0)
        )
        val bankAccounts = listOf(
            LinkedBankAccount(institutionName = "Bank", accountName = "Checking", mask = "1234", type = "checking", currentBalance = 2000.0, availableBalance = 2000.0)
        )
        val debts = listOf(
            DebtItem(name = "Card", totalAmount = 1500.0, interestRate = 18.0, minimumPayment = 50.0)
        )
        val linkedSummary = FinancialCalculator.calculateNetWorthSummary(manual, bankAccounts, isBankLinked = true, debts = debts)
        assertEquals(6000.0, linkedSummary.totalAssets, 0.01)
        assertEquals(1500.0, linkedSummary.totalDebts, 0.01)
        assertEquals(4500.0, linkedSummary.netWorth, 0.01)

        val unlinkedSummary = FinancialCalculator.calculateNetWorthSummary(manual, bankAccounts, isBankLinked = false, debts = debts)
        assertEquals(4000.0, unlinkedSummary.totalAssets, 0.01)
        assertEquals(2500.0, unlinkedSummary.netWorth, 0.01)
    }

    @Test
    fun goalPlanner_computesMonthlyNeededAndOnTrackStatus() {
        val now = 1_700_000_000_000L // Nov 2023
        val goal = GoalItem(
            name = "Emergency Trip",
            targetAmount = 2400.0,
            currentAmount = 1200.0,
            targetDate = "2024-11",
            category = "Trip"
        )
        val onTrack = FinancialCalculator.analyzeGoal(goal, monthlySurplus = 500.0, activeGoalsCount = 1, nowMillis = now)
        assertEquals(12, onTrack.monthsRemaining)
        assertEquals(100.0, onTrack.monthlyAmountNeeded, 0.01)
        assertEquals(0.5f, onTrack.progressFraction, 0.01f)
        assertTrue(onTrack.isOnTrack)
        assertEquals("On track", onTrack.statusLabel)

        val behind = FinancialCalculator.analyzeGoal(goal, monthlySurplus = 50.0, activeGoalsCount = 1, nowMillis = now)
        assertFalse(behind.isOnTrack)
        assertEquals("Behind pace", behind.statusLabel)
    }

    @Test
    fun monthlyImprovementReport_calculatesDeltaImprovedSlippedAndTop3Actions() {
        val profile = UserProfile(
            monthlyIncome = 3000.0,
            expenses = listOf(ExpenseItem(category = "Bills", amount = 1500.0)),
            debts = listOf(DebtItem(name = "Card", totalAmount = 1000.0, interestRate = 20.0, minimumPayment = 50.0)),
            emergencyBufferMonthsTarget = 1.0
        )
        val envelopes = listOf(
            VirtualEnvelope(id = "emergency", type = EnvelopeType.EMERGENCY_FUND, name = "Emergency", balance = 1600.0)
        )
        val prev = MonthlyHealthSnapshot(
            monthKey = "2026-01",
            totalScore = 60,
            bufferScore = 15,
            spendingScore = 15,
            savingsScore = 20,
            debtScore = 10,
            bufferAmount = 800.0,
            savingsRatePercent = 40.0,
            totalDebt = 1500.0
        )
        val report = FinancialCalculator.calculateMonthlyImprovementReport(
            profile = profile,
            envelopes = envelopes,
            transactions = emptyList(),
            previousSnapshot = prev
        )
        assertTrue(report.currentScore in 0..100)
        assertEquals(report.currentScore, report.pillarBreakdown.sumOf { it.earnedPoints })
        assertEquals(report.currentScore - 60, report.scoreDelta)
        assertEquals(3, report.top3Actions.size)
        assertTrue(report.whatImproved.isNotEmpty())
        assertTrue(report.whatSlipped.isNotEmpty())
    }
}
