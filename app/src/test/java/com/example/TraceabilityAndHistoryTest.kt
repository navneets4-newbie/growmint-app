package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.FinancialCalculator
import com.example.data.GrowMintRepository
import com.example.data.RuleRegistry
import com.example.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TraceabilityAndHistoryTest {

    private lateinit var context: Context
    private lateinit var repository: GrowMintRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = GrowMintRepository(context)
        repository.resetAllData()
    }

    @Test
    fun tracedMoneyHealthScore_containsFourComponentsWithInputsFormulasWeightsAndTimestamp() {
        val now = 1_750_000_000_000L
        val profile = UserProfile(
            currencyCode = "USD",
            monthlyIncome = 4000.0,
            expenses = listOf(ExpenseItem(category = "Housing", amount = 1600.0)),
            debts = listOf(DebtItem(name = "Visa", totalAmount = 1200.0, interestRate = 19.0, minimumPayment = 60.0)),
            emergencyBufferMonthsTarget = 1.0
        )
        val envelopes = listOf(
            VirtualEnvelope("emergency", EnvelopeType.EMERGENCY_FUND, "Emergency Safety Net", 830.0)
        )
        val txs = listOf(
            LedgerTransaction(timestamp = now - 5L * 86_400_000L, type = "EXPENSE", category = "Groceries", amount = 200.0, merchant = "Market")
        )

        val traced = FinancialCalculator.calculateTracedMoneyHealthScore(profile, envelopes, txs, nowMillis = now)

        assertEquals(4, traced.components.size)
        assertEquals(100, traced.components.sumOf { it.weightPercent })
        assertEquals(traced.totalScore, traced.components.sumOf { it.earnedPoints })
        assertTrue(traced.calculatedAtFormatted.isNotBlank())
        assertEquals(now, traced.calculatedAtMillis)

        traced.components.forEach { comp ->
            assertTrue("Component ${comp.name} must have inputs", comp.inputs.isNotEmpty())
            assertTrue("Component ${comp.name} must have plain-words formula", comp.formulaPlainWords.isNotBlank())
            assertNotNull("Component ${comp.name} ruleId must exist in RuleRegistry", RuleRegistry.findById(comp.ruleId))
        }
    }

    @Test
    fun top3Actions_includeValidRuleIdTriggeringNumbersExpectedDeltaDataSourcesAndContributors() {
        val now = 1_750_000_000_000L
        val profile = UserProfile(
            currencyCode = "USD",
            monthlyIncome = 3000.0,
            expenses = listOf(ExpenseItem(category = "Rent", amount = 1200.0)),
            debts = listOf(DebtItem(name = "Student Card", totalAmount = 2000.0, interestRate = 22.0, minimumPayment = 100.0)),
            emergencyBufferMonthsTarget = 1.0
        )
        val envelopes = listOf(
            VirtualEnvelope("emergency", EnvelopeType.EMERGENCY_FUND, "Emergency Safety Net", 300.0)
        )
        val txs = listOf(
            LedgerTransaction(timestamp = now - 2L * 86_400_000L, type = "EXPENSE", category = "Dining & Coffee", amount = 45.0, merchant = "Cafe Rio")
        )

        val report = FinancialCalculator.calculateMonthlyImprovementReport(
            profile = profile,
            envelopes = envelopes,
            transactions = txs,
            previousSnapshot = null,
            nowMillis = now
        )

        assertEquals(3, report.top3Actions.size)
        report.top3Actions.forEach { action ->
            assertNotNull("Action ${action.title} must reference a registered rule", RuleRegistry.findById(action.ruleId))
            val trace = action.traceability
            assertNotNull("Action ${action.title} must include ActionTraceability", trace)
            assertTrue(trace!!.triggeringNumbers.isNotEmpty())
            assertTrue(trace.dataSources.isNotEmpty())
            assertTrue(trace.expectedScoreExplanation.isNotBlank())
            assertTrue(trace.contributors.isNotEmpty())
        }

        // Boosting buffer when empty should have a strictly positive expectedScoreDelta
        val bufferAction = report.top3Actions.first()
        assertEquals(RuleRegistry.RULE_BUFFER_FIRST, bufferAction.ruleId)
        assertTrue(bufferAction.traceability!!.expectedScoreDelta > 0)
        assertEquals(report.currentScore + bufferAction.traceability!!.expectedScoreDelta, bufferAction.traceability!!.projectedNewScore)
    }

    @Test
    fun dataQualityNotes_flagZeroDaysShortDaysAndNoDebtClearly() {
        val now = 1_750_000_000_000L
        val emptyProfile = UserProfile(monthlyIncome = 2500.0)

        val zeroDayNotes = FinancialCalculator.assessDataQuality(emptyProfile, emptyList(), nowMillis = now)
        assertTrue(zeroDayNotes.any { it.label.contains("0 days of transactions") && it.isLowQuality })
        assertTrue(zeroDayNotes.any { it.label.contains("No debt entered") && it.isLowQuality })

        val shortTxs = listOf(
            LedgerTransaction(timestamp = now - 4L * 86_400_000L, type = "EXPENSE", category = "Food", amount = 25.0),
            LedgerTransaction(timestamp = now - 1L * 86_400_000L, type = "EXPENSE", category = "Bus", amount = 5.0)
        )
        val shortDayNotes = FinancialCalculator.assessDataQuality(emptyProfile, shortTxs, nowMillis = now)
        assertTrue(shortDayNotes.any { it.label.contains("5 days of transactions") && it.isLowQuality })
    }

    @Test
    fun scoreHistorySnapshots_recordChangesAndComputeComponentDiff() {
        val t1 = 1_750_000_000_000L
        val t2 = t1 + 86_400_000L

        repository.updateProfile(
            UserProfile(
                currencyCode = "USD",
                monthlyIncome = 3000.0,
                expenses = listOf(ExpenseItem(category = "Bills", amount = 1000.0)),
                isOnboarded = true
            )
        )
        val snap1 = repository.recordScoreSnapshotIfChanged(nowMillis = t1)
        assertEquals(1, repository.scoreHistory.value.size)

        // Calling again with unchanged inputs should NOT duplicate snapshot
        repository.recordScoreSnapshotIfChanged(nowMillis = t1 + 1000L)
        assertEquals(1, repository.scoreHistory.value.size)

        // Now fund emergency buffer via income split so bufferScore increases
        repository.updateProfile(
            repository.userProfile.value.copy(
                splitRules = listOf(SplitEnvelopeRule(EnvelopeType.EMERGENCY_FUND, 100))
            )
        )
        repository.executeIncomeSplit(1000.0, "Emergency Boost")
        val snap2 = repository.recordScoreSnapshotIfChanged(nowMillis = t2)
        assertEquals(2, repository.scoreHistory.value.size)
        assertTrue(snap2.totalScore > snap1.totalScore)

        val diff = FinancialCalculator.calculateScoreSnapshotDiff(snap2, snap1, "USD")
        assertTrue(diff.totalDelta > 0)
        assertTrue(diff.primaryReason.contains("Emergency buffer"))
        assertEquals(RuleRegistry.RULE_BUFFER_PROGRESS, diff.ruleId)

        // Verify persistence across repository reload
        val reloaded = GrowMintRepository(context)
        assertEquals(2, reloaded.scoreHistory.value.size)
    }

    @Test
    fun humanValidation_actionDone_activityExport_and_errorFlagging_persistAcrossReloads() {
        repository.markActionDone(
            actionKey = "2026-11_p1_RULE_BUFFER_FIRST",
            title = "Boost Emergency Safety Net",
            whatSummary = "Completed Boost Emergency Safety Net ($250)",
            whySummary = "Safety buffer was below 1 month",
            numbersSummary = "Saved: $100; Target: $1,000",
            ruleId = RuleRegistry.RULE_BUFFER_FIRST
        )
        repository.logConfirmedActivity(
            category = "Setting Change",
            title = "Update Buffer Target",
            whatChanged = "Changed target from 1.0 mo to 2.0 mo",
            whySummary = "Reviewed and approved by user",
            numbersSummary = "Target: 2.0 mo",
            ruleId = null
        )

        repository.flagItemError(
            itemType = "TOP_ACTION",
            itemId = "2026-11_p1_RULE_BUFFER_FIRST",
            itemTitle = "Action #1: Boost Emergency Safety Net",
            note = "My savings account balance isn't linked yet",
            snapshotSummary = "Score 68/100",
            ruleId = RuleRegistry.RULE_BUFFER_FIRST
        )

        val csv = repository.exportActivityHistoryCsv()
        val text = repository.exportActivityHistoryText()

        assertTrue(csv.contains("Timestamp,Date,Category,Title,WhatChanged,Why,BackingNumbers,RuleId"))
        assertTrue(csv.contains("Boost Emergency Safety Net"))
        assertTrue(csv.contains("RULE_BUFFER_FIRST"))
        assertTrue(text.contains("GrowMint Activity History & Error Flags"))
        assertTrue(text.contains("My savings account balance isn't linked yet"))

        val reloaded = GrowMintRepository(context)
        assertTrue(reloaded.completedActionKeys.value.contains("2026-11_p1_RULE_BUFFER_FIRST"))
        assertEquals(2, reloaded.activityHistory.value.size)
        assertEquals(1, reloaded.errorFlags.value.size)

        val flagId = reloaded.errorFlags.value.first().id
        reloaded.clearErrorFlag(flagId)
        assertTrue(reloaded.errorFlags.value.isEmpty())
    }
}
