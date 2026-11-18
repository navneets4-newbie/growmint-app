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

    @Test
    fun oneLineSummaries_builtInCode_forActionsAndActivityHistoryEntries() {
        val now = 1_750_000_000_000L
        val profile = UserProfile(
            currencyCode = "USD",
            monthlyIncome = 3000.0,
            expenses = listOf(ExpenseItem(category = "Rent", amount = 1000.0)),
            emergencyBufferMonthsTarget = 1.0,
            isOnboarded = true
        )
        val envelopes = listOf(
            VirtualEnvelope("emergency", EnvelopeType.EMERGENCY_FUND, "Emergency Safety Net", 400.0)
        )
        val report = FinancialCalculator.calculateMonthlyImprovementReport(
            profile = profile,
            envelopes = envelopes,
            transactions = emptyList(),
            previousSnapshot = null,
            nowMillis = now
        )

        val bufferAction = report.top3Actions.first()
        assertEquals("Build buffer: 0.4 months saved (target 1) · Buffer-first rule", bufferAction.oneLineSummary)
        report.top3Actions.forEach { action ->
            val summary = FinancialCalculator.resolveActionOneLineSummary(action)
            assertTrue(summary.isNotBlank())
            assertTrue("Summary should contain '·' rule separator: $summary", summary.contains("·"))
        }

        repository.updateProfile(profile)
        repository.logConfirmedActivity(
            category = "Action Completed",
            title = "Build buffer",
            whatChanged = "Moved $150 to Emergency Safety Net",
            whySummary = "Safety buffer was 0.4 months vs target 1 month",
            numbersSummary = "0.4 months saved (target 1)",
            ruleId = RuleRegistry.RULE_BUFFER_FIRST
        )
        val logged = repository.activityHistory.value.first()
        assertEquals("Build buffer: 0.4 months saved (target 1) · Buffer-first rule", logged.oneLineSummary)
        assertEquals("Buffer-first rule", logged.ruleName)
        assertEquals("v1.0", logged.ruleVersion)
    }

    @Test
    fun activitySnapshot_and_explanationSenseFeedback_persistAcrossReloads() {
        repository.updateProfile(
            UserProfile(
                currencyCode = "USD",
                monthlyIncome = 4000.0,
                expenses = listOf(ExpenseItem(category = "Housing", amount = 1200.0)),
                isOnboarded = true
            )
        )

        repository.logConfirmedActivity(
            category = "Action Completed",
            title = "Build buffer",
            whatChanged = "Saved $200 toward emergency fund",
            whySummary = "Safety buffer below 1 month",
            numbersSummary = "0.2 months saved (target 1)",
            ruleId = RuleRegistry.RULE_BUFFER_FIRST,
            snapshotInputs = listOf(
                ScoreInputMetric("Current Buffer", "$200"),
                ScoreInputMetric("Target Buffer", "$1,200")
            )
        )
        val logged = repository.activityHistory.value.first()

        assertTrue(logged.snapshotScoreComponents.isNotEmpty())
        assertTrue(logged.dataQualityNotes.isNotEmpty())
        assertTrue(logged.ruleWording.isNotBlank())
        assertEquals(2, logged.snapshotInputs.size)

        repository.updateActivityAiExplanation(logged.id, "Code checked your $200 buffer against $1,200 target.")
        repository.recordExplanationFeedback(
            targetId = logged.id,
            targetType = "ACTIVITY_ENTRY",
            targetTitle = logged.title,
            summaryShown = logged.oneLineSummary,
            ruleId = RuleRegistry.RULE_BUFFER_FIRST,
            madeSense = false
        )

        val reloaded = GrowMintRepository(context)
        val reloadedEntry = reloaded.activityHistory.value.first { it.id == logged.id }
        assertEquals("Code checked your $200 buffer against $1,200 target.", reloadedEntry.aiExplanation)
        assertEquals(2, reloadedEntry.snapshotInputs.size)
        assertTrue(reloadedEntry.snapshotScoreComponents.isNotEmpty())
        assertEquals("v1.0", reloadedEntry.ruleVersion)

        val fb = reloaded.getExplanationFeedbackFor(logged.id, "ACTIVITY_ENTRY")
        assertEquals(false, fb)

        val csv = reloaded.exportActivityHistoryCsv()
        val text = reloaded.exportActivityHistoryText()
        assertTrue(csv.contains("OneLineSummary,RuleVersion,DidThisMakeSense"))
        assertTrue(csv.contains("Not really"))
        assertTrue(text.contains("Explanation Check Feedback"))
        assertTrue(text.contains("Did this make sense?: Not really"))
    }

    @Test
    fun topAffectingDataQualityNote_onlySurfacesWarningWhenLowQualityAffectsResult() {
        val now = 1_750_000_000_000L
        val solidProfile = UserProfile(
            currencyCode = "USD",
            monthlyIncome = 4000.0,
            expenses = listOf(ExpenseItem(category = "Housing", amount = 1500.0)),
            debts = listOf(DebtItem(name = "Auto", totalAmount = 3000.0, interestRate = 4.0, minimumPayment = 150.0)),
            isOnboarded = true
        )
        val longWindowTxs = listOf(
            LedgerTransaction(timestamp = now - 20L * 86_400_000L, type = "EXPENSE", category = "Food", amount = 60.0),
            LedgerTransaction(timestamp = now, type = "EXPENSE", category = "Utilities", amount = 90.0)
        )

        val solidNotes = FinancialCalculator.assessDataQuality(solidProfile, longWindowTxs, nowMillis = now)
        assertNull("Good data quality should not show a top-level warning", FinancialCalculator.topAffectingDataQualityNote(solidNotes))

        val partialTxs = listOf(
            LedgerTransaction(timestamp = now - 5L * 86_400_000L, type = "EXPENSE", category = "Food", amount = 30.0),
            LedgerTransaction(timestamp = now, type = "EXPENSE", category = "Bus", amount = 5.0)
        )
        val lowNotes = FinancialCalculator.assessDataQuality(solidProfile, partialTxs, nowMillis = now)
        val topWarning = FinancialCalculator.topAffectingDataQualityNote(lowNotes)
        assertNotNull(topWarning)
        assertTrue(topWarning!!.isLowQuality)
        assertTrue(topWarning.label.contains("days of transactions"))
    }

    @Test
    fun calculatedFacts_and_whatWouldChangeThis_presentOnAllActions_andSavedInSnapshot() {
        val now = 1_750_000_000_000L
        val profile = UserProfile(
            currencyCode = "USD",
            monthlyIncome = 4000.0,
            expenses = listOf(ExpenseItem(category = "Housing", amount = 1000.0)),
            debts = listOf(DebtItem(name = "Visa", totalAmount = 1500.0, interestRate = 21.0, minimumPayment = 60.0)),
            emergencyBufferMonthsTarget = 1.0,
            isOnboarded = true
        )
        val envelopes = listOf(
            VirtualEnvelope("emergency", EnvelopeType.EMERGENCY_FUND, "Emergency Safety Net", 400.0)
        )
        val report = FinancialCalculator.calculateMonthlyImprovementReport(
            profile = profile,
            envelopes = envelopes,
            transactions = emptyList(),
            previousSnapshot = null,
            nowMillis = now
        )

        report.top3Actions.forEach { action ->
            val trace = action.traceability!!
            assertTrue("Action ${action.title} must have calculatedFacts", trace.calculatedFacts.isNotEmpty())
            assertTrue("Action ${action.title} must have whatWouldChangeThis", trace.whatWouldChangeThis.isNotBlank())
            trace.calculatedFacts.forEach { fact ->
                assertTrue(fact.id.startsWith("V"))
                assertTrue(fact.label.isNotBlank())
                assertTrue(fact.valueFormatted.isNotBlank())
                assertTrue(fact.unit.isNotBlank())
                assertTrue(fact.formulaPlainWords.isNotBlank())
                assertTrue(fact.source.isNotBlank())
            }
        }

        val firstAction = report.top3Actions.first()
        assertTrue(firstAction.traceability!!.whatWouldChangeThis.contains("Reach 1 month(s) of buffer"))

        repository.updateProfile(profile)
        repository.markActionDone(
            actionKey = firstAction.actionKey,
            title = firstAction.title,
            whatSummary = "Moved funds to safety net",
            whySummary = firstAction.traceability!!.ruleDescription,
            numbersSummary = "0.4 months saved",
            ruleId = firstAction.ruleId,
            whatWouldChangeThis = firstAction.traceability!!.whatWouldChangeThis,
            snapshotCalculatedFacts = firstAction.traceability!!.calculatedFacts
        )

        val savedId = repository.activityHistory.value.first().id
        val fallbackTemplate = FinancialCalculator.buildActionFallbackTemplate(firstAction, firstAction.traceability!!.calculatedFacts)
        val validatedTrace = FinancialCalculator.validateAiReferenceExplanation(
            rawAiText = "Your buffer is {{V1}} toward {{V2}}. (Educational guidance only.)",
            facts = firstAction.traceability!!.calculatedFacts,
            fallbackTemplate = fallbackTemplate
        )
        assertFalse(validatedTrace.isFallback)
        repository.updateActivityTraceableExplanation(savedId, validatedTrace)

        val reloaded = GrowMintRepository(context)
        val reloadedEntry = reloaded.activityHistory.value.first { it.id == savedId }
        assertEquals(firstAction.traceability!!.calculatedFacts.size, reloadedEntry.snapshotCalculatedFacts.size)
        assertEquals(firstAction.traceability!!.whatWouldChangeThis, reloadedEntry.whatWouldChangeThis)
        assertEquals("Your buffer is {{V1}} toward {{V2}}. (Educational guidance only.)", reloadedEntry.aiExplanationTemplate)
        assertFalse(reloadedEntry.aiExplanationIsFallback)
        assertTrue(reloadedEntry.aiExplanation.contains("0.4 months"))
    }

    @Test
    fun referenceValidation_allowsValidIds_andBlocksUnknownNumberOrUnknownIdWithSimpleFallback() {
        val facts = listOf(
            CalculatedValueFact(
                id = "V1",
                label = "Buffer coverage",
                valueFormatted = "0.4 months",
                unit = "months",
                formulaPlainWords = "Emergency balance / monthly spending",
                source = "Virtual Envelope: Emergency Safety Net"
            ),
            CalculatedValueFact(
                id = "V2",
                label = "Buffer target",
                valueFormatted = "1 months",
                unit = "months",
                formulaPlainWords = "Target months configured in settings",
                source = "Safety Buffer settings"
            )
        )
        val fallbackTemplate = "Your buffer is {{V1}} toward the {{V2}} target. (Educational guidance only.)"

        // Valid reference-only AI output
        val valid = FinancialCalculator.validateAiReferenceExplanation(
            rawAiText = "Right now your safety cushion is {{V1}} compared with your {{V2}} goal. (Educational guidance only.)",
            facts = facts,
            fallbackTemplate = fallbackTemplate
        )
        assertFalse(valid.isFallback)
        assertEquals("", valid.note)
        assertEquals("Right now your safety cushion is 0.4 months compared with your 1 months goal. (Educational guidance only.)", valid.plainResolvedText)

        // Forced raw number introduced by AI -> blocked & fallback shown
        val rawNumberBlocked = FinancialCalculator.validateAiReferenceExplanation(
            rawAiText = "Right now your safety cushion is {{V1}}, so save $500 more this week. (Educational guidance only.)",
            facts = facts,
            fallbackTemplate = fallbackTemplate
        )
        assertTrue(rawNumberBlocked.isFallback)
        assertEquals("Showing a simple explanation.", rawNumberBlocked.note)
        assertEquals(fallbackTemplate, rawNumberBlocked.templateWithIds)
        assertFalse(rawNumberBlocked.plainResolvedText.contains("500"))

        // Forced unknown ID introduced by AI -> blocked & fallback shown
        val unknownIdBlocked = FinancialCalculator.validateAiReferenceExplanation(
            rawAiText = "Your cushion is {{V1}} and debt is {{V99}}. (Educational guidance only.)",
            facts = facts,
            fallbackTemplate = fallbackTemplate
        )
        assertTrue(unknownIdBlocked.isFallback)
        assertEquals("Showing a simple explanation.", unknownIdBlocked.note)
        assertEquals(fallbackTemplate, unknownIdBlocked.templateWithIds)
    }
}
