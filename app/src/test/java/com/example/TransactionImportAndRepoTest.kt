package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.FinancialCalculator
import com.example.data.GrowMintRepository
import com.example.data.TransactionImportEngine
import com.example.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TransactionImportAndRepoTest {

    private lateinit var repository: GrowMintRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = GrowMintRepository(context)
        repository.resetAllData()
    }

    @Test
    fun sanitizeForAi_stripsAccountsCardsPhonesOtpsEmailsUpiAndNames() {
        val raw = """
            Dear Rahul Sharma, A/c No XX123456789012 debited ₹450.00 to rahul@okicici on 14/02/2026.
            Card 4532-1122-3344-5566 used at Coffee Shop. Call +91 9876543210.
            OTP 482910 for txn. Contact user@example.com.
        """.trimIndent()

        val sanitized = TransactionImportEngine.sanitizeForAi(raw)

        assertFalse(sanitized.contains("123456789012"))
        assertFalse(sanitized.contains("4532-1122-3344-5566"))
        assertFalse(sanitized.contains("9876543210"))
        assertFalse(sanitized.contains("482910"))
        assertFalse(sanitized.contains("rahul@okicici"))
        assertFalse(sanitized.contains("user@example.com"))
        assertFalse(sanitized.contains("Rahul Sharma"))
        assertTrue(sanitized.contains("Coffee Shop"))
        assertTrue(sanitized.contains("450.00"))
    }

    @Test
    fun validateAiJson_parses10PlusRows_rejectsMalformed_appliesRules_andUnselectsDuplicates() {
        val existingDate = TransactionImportEngine.parseAndNormalizeDate("2026-02-01")
        val existing = listOf(
            LedgerTransaction(
                timestamp = existingDate.timestampMillis,
                type = "EXPENSE",
                category = "Dining & Coffee",
                amount = 6.50,
                notes = "Starbucks",
                merchant = "Starbucks"
            )
        )
        val rules = listOf(
            MerchantCategoryRule(merchantPattern = "Trader Joe", category = "Groceries")
        )

        val json = """
            {
              "transactions": [
                {"date":"2026-02-01","amount":6.50,"type":"expense","merchant":"Starbucks","suggestedCategory":"Dining"},
                {"date":"2026-02-02","amount":54.20,"type":"expense","merchant":"Trader Joe's #14","suggestedCategory":"Shopping"},
                {"date":"2026-02-03","amount":1900.00,"type":"income","merchant":"Acme Payroll","suggestedCategory":"Income & Salary"},
                {"date":"2026-02-04","amount":15.99,"type":"expense","merchant":"Netflix","suggestedCategory":"Subscriptions"},
                {"date":"2026-02-05","amount":22.00,"type":"expense","merchant":"Uber Trip","suggestedCategory":"Transport"},
                {"date":"2026-02-06","amount":18.40,"type":"expense","merchant":"Local Pharmacy","suggestedCategory":"Health"},
                {"date":"2026-02-07","amount":120.00,"type":"expense","merchant":"City Electric","suggestedCategory":"Bills"},
                {"date":"2026-02-08","amount":32.50,"type":"expense","merchant":"Fresh Market","suggestedCategory":"Groceries"},
                {"date":"2026-02-09","amount":9.99,"type":"expense","merchant":"Spotify","suggestedCategory":"Subscriptions"},
                {"date":"2026-02-10","amount":45.00,"type":"expense","merchant":"Campus Bookstore","suggestedCategory":"Education"},
                {"date":"2026-02-11","amount":-10.0,"type":"expense","merchant":"Bad Negative","suggestedCategory":"General"},
                {"date":"2026-02-12","amount":0.0,"type":"expense","merchant":"Zero Row","suggestedCategory":"General"},
                {"date":"2026-02-13","amount":25.0,"type":"unknown_type","merchant":"Bad Type","suggestedCategory":"General"}
              ]
            }
        """.trimIndent()

        val result = TransactionImportEngine.validateAndBuildCandidatesFromAiJson(json, existing, rules)

        assertEquals(10, result.foundCount)
        assertEquals(3, result.skippedCount)
        // Duplicate Starbucks row is flagged and unselected by default
        val starbucks = result.candidates.first { it.merchant == "Starbucks" }
        assertTrue(starbucks.isDuplicate)
        assertFalse(starbucks.isSelected)
        // Smart rule overrides AI category for Trader Joe's
        val tj = result.candidates.first { it.merchant.contains("Trader Joe") }
        assertEquals("Groceries", tj.category)
        assertTrue(tj.ruleMatched)
        assertTrue(tj.isSelected)
    }

    @Test
    fun dateAmbiguity_switchesBetweenDmyAndMdy() {
        val dmy = TransactionImportEngine.parseAndNormalizeDate("04/05/2026", DateFormatPreference.DMY)
        val mdy = TransactionImportEngine.parseAndNormalizeDate("04/05/2026", DateFormatPreference.MDY)
        assertTrue(dmy.isAmbiguous)
        assertEquals("2026-05-04", dmy.isoDate)
        assertEquals("2026-04-05", mdy.isoDate)
    }

    @Test
    fun csvStatementImport_parsesQuotesCurrenciesAndColumnMapping() {
        val csv = """
            Posted Date,Narration,Debit,Credit
            "05/02/2026","Whole Foods, Organic","₹1,250.50",""
            "06/02/2026","Client Design Payout","","₹15,000.00"
            "07/02/2026","Malicious Empty","",""
        """.trimIndent()

        val preview = TransactionImportEngine.parseCsvStatementPreview(csv)
        assertNotNull(preview)
        assertEquals(0, preview!!.suggestedMapping.dateCol)
        assertEquals(1, preview.suggestedMapping.descriptionCol)
        assertEquals(2, preview.suggestedMapping.debitCol)
        assertEquals(3, preview.suggestedMapping.creditCol)

        val extracted = TransactionImportEngine.extractCandidatesFromCsv(
            preview = preview,
            mapping = preview.suggestedMapping,
            existingTransactions = emptyList(),
            rules = emptyList(),
            datePreference = DateFormatPreference.DMY
        )

        assertEquals(2, extracted.foundCount)
        assertEquals(1, extracted.skippedCount)
        assertTrue(extracted.hasAmbiguousDates)

        val grocery = extracted.candidates[0]
        assertEquals("EXPENSE", grocery.type)
        assertEquals(1250.50, grocery.amount, 0.01)
        assertEquals("2026-02-05", grocery.date)

        val payout = extracted.candidates[1]
        assertEquals("INCOME", payout.type)
        assertEquals(15000.0, payout.amount, 0.01)
        assertEquals("2026-02-06", payout.date)
    }

    @Test
    fun confirmImportBatch_learnsSmartRule_triggersIncomePrompt_feedsMetrics_andUndoReversesExactBatch() {
        repository.updateProfile(
            UserProfile(
                monthlyIncome = 1000.0,
                currentBalance = 500.0,
                expenses = listOf(ExpenseItem(category = "Living", amount = 600.0)),
                isOnboarded = true
            )
        )

        val preExisting = repository.recordQuickAdd(
            amount = 10.0,
            isExpense = true,
            category = "Dining & Coffee",
            note = "Morning Cafe"
        )!!
        assertEquals(490.0, repository.userProfile.value.currentBalance, 0.01)

        // Import batch with corrected merchant category, a recurring subscription leak, and income
        val batchCandidates = listOf(
            ExtractedTransactionCandidate(
                date = TransactionImportEngine.millisToIsoDate(System.currentTimeMillis()),
                amount = 2000.0,
                type = "INCOME",
                merchant = "Freelance Client",
                suggestedCategory = "General",
                category = "Side Hustle",
                isSelected = true
            ),
            ExtractedTransactionCandidate(
                date = TransactionImportEngine.millisToIsoDate(System.currentTimeMillis()),
                amount = 15.99,
                type = "EXPENSE",
                merchant = "Netflix Subscription",
                suggestedCategory = "General",
                category = "Subscriptions", // user corrected from General -> Subscriptions
                isSelected = true
            )
        )

        val record = repository.confirmImportBatch(batchCandidates, "PASTE")
        assertNotNull(record)
        assertEquals(2, record!!.transactionIds.size)
        // Balance = 490 + 2000 - 15.99 = 2474.01
        assertEquals(2474.01, repository.userProfile.value.currentBalance, 0.01)

        // Smart rule learned for Netflix Subscription -> Subscriptions
        assertTrue(repository.merchantRules.value.any {
            it.merchantPattern == "Netflix Subscription" && it.category == "Subscriptions"
        })

        // Income detection banner triggered
        val incomePrompt = repository.pendingIncomeForSplit.value
        assertNotNull(incomePrompt)
        assertEquals(2000.0, incomePrompt!!.first, 0.01)

        // Leak detector picked up Netflix Subscription from ledger transactions
        assertTrue(repository.subscriptionLeaks.value.any { it.name.contains("Netflix") })

        // Net worth includes ledgerCashBalance
        val netWorth = FinancialCalculator.calculateNetWorthSummary(
            manualAssets = emptyList(),
            linkedBankAccounts = emptyList(),
            isBankLinked = false,
            debts = emptyList(),
            ledgerCashBalance = repository.userProfile.value.currentBalance
        )
        assertEquals(2474.01, netWorth.netWorth, 0.01)

        // Health report & monthly report reflect imported transactions without crashing
        val health = repository.calculateHealthReport()
        assertTrue(health.overallScore in 0..100)
        val monthly = FinancialCalculator.calculateMonthlyImprovementReport(
            profile = repository.userProfile.value,
            envelopes = repository.envelopes.value,
            transactions = repository.transactions.value,
            previousSnapshot = null
        )
        assertTrue(monthly.currentScore in 0..100)

        // Undo removes exact import batch, leaves preExisting intact, and restores balance to 490.0
        assertTrue(repository.undoLastImport())
        assertNull(repository.lastImportBatch.value)
        assertEquals(listOf(preExisting.id), repository.transactions.value.map { it.id })
        assertEquals(490.0, repository.userProfile.value.currentBalance, 0.01)
    }

    @Test
    fun quickAdd_sameAsLastTime_andRuleEditDelete() {
        repository.updateProfile(UserProfile(currentBalance = 100.0, isOnboarded = true))
        repository.recordQuickAdd(
            amount = 4.50,
            isExpense = true,
            category = "Dining & Coffee",
            note = "Blue Bottle",
            learnRuleIfCorrected = true,
            suggestedCategory = "General"
        )

        val recent = repository.getMostRecentSimilarTransaction("Blue")
        assertNotNull(recent)
        assertEquals(4.50, recent!!.amount, 0.01)
        assertEquals("Blue Bottle", recent.merchant)

        val rule = repository.merchantRules.value.firstOrNull { it.merchantPattern == "Blue Bottle" }
        assertNotNull(rule)
        repository.saveMerchantRule("Blue Bottle", "Entertainment", rule!!.id)
        assertEquals("Entertainment", repository.merchantRules.value.first { it.id == rule.id }.category)

        repository.deleteMerchantRule(rule.id)
        assertTrue(repository.merchantRules.value.none { it.id == rule.id })
    }
}
