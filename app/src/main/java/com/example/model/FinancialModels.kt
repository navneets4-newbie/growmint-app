package com.example.model

enum class AppThemeMode(val label: String) {
    AUTO("Auto"),
    DAY("Day"),
    NIGHT("Night")
}

enum class LifeStage(val label: String, val subtitle: String) {
    STUDENT("Student", "Balancing classes, stipends, or part-time earnings"),
    EARLY_CAREER("Early career", "Building your first buffer, paying debts, and growing savings"),
    FAMILY("Family", "Managing household budgets, shared bills, and long-term goals"),
    SELF_EMPLOYED("Self-employed", "Smoothing variable client income, taxes, and business costs"),
    RETIRED("Retired", "Protecting savings, managing steady cashflow, and avoiding scams")
}

enum class IncomeType(val label: String, val description: String) {
    STEADY("Steady", "Regular paycheck or fixed monthly salary"),
    IRREGULAR("Irregular", "Freelance, side-hustle, or commission earnings that vary"),
    ALLOWANCE("Allowance", "Pocket money, family support, or fixed stipend"),
    NONE("None", "Currently no regular income; stretching existing savings")
}

enum class RiskComfort(val label: String, val description: String) {
    LOW("Cautious & Safe", "Protect my cash with high-yield savings and guaranteed government bonds"),
    MEDIUM("Balanced Growth", "Grow steadily by owning tiny pieces of hundreds of top companies (index funds)"),
    HIGH("Ambitious Growth", "Aim for higher growth, accepting natural market ups and downs")
}

enum class EnvelopeType(val displayName: String, val defaultColorHex: Long) {
    NEEDS("Essentials & Bills", 0xFF3B82F6),
    SAVINGS("Rainy Day Savings", 0xFF10B981),
    EMERGENCY_FUND("Emergency Safety Net", 0xFF0D9488),
    INVESTMENTS("Future Investments", 0xFF8B5CF6),
    FUN("Fun & Guilt-Free", 0xFFF59E0B),
    BUSINESS_REINVESTMENT("Side Hustle & Skills", 0xFFEC4899),
    CUSTOM("Custom Envelope", 0xFF64748B)
}

data class ExpenseItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val category: String,
    val amount: Double
)

data class DebtItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val totalAmount: Double,
    val interestRate: Double, // annual %
    val minimumPayment: Double
)

data class GoalItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    val targetDate: String, // e.g. "Dec 2027"
    val envelopeType: EnvelopeType = EnvelopeType.SAVINGS,
    val category: String = "Custom" // "House", "Car", "Trip", "Retirement", "Custom"
)

data class SplitEnvelopeRule(
    val envelopeType: EnvelopeType,
    val percentage: Int
)

data class SplitPreset(
    val id: String,
    val name: String,
    val description: String,
    val rules: List<SplitEnvelopeRule>
)

data class VirtualEnvelope(
    val id: String,
    val type: EnvelopeType,
    val name: String,
    val balance: Double
)

data class TransferRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val description: String,
    val totalAmount: Double,
    val envelopeAllocations: Map<String, Double>,
    val isUndoable: Boolean = true
)

data class LedgerTransaction(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // "INCOME", "EXPENSE", "TRANSFER"
    val category: String,
    val amount: Double,
    val notes: String = "",
    val envelopeType: EnvelopeType? = null,
    val merchant: String = "",
    val importBatchId: String? = null
)

data class HealthScoreItem(
    val name: String,
    val score: Int,
    val maxScore: Int,
    val impact: String, // "+15 pts", "-5 pts"
    val positive: Boolean,
    val explanation: String
)

data class FinancialHealthReport(
    val overallScore: Int,
    val grade: String,
    val summary: String,
    val breakdown: List<HealthScoreItem>
)

data class UserProfile(
    val currencyCode: String = "USD",
    val secondaryCurrency: String? = null,
    val manualExchangeRate: Double? = null,
    val country: String = "United States",
    val monthlyIncome: Double = 3500.0,
    val isIrregularIncome: Boolean = false,
    val currentBalance: Double = 5000.0,
    val expenses: List<ExpenseItem> = emptyList(),
    val debts: List<DebtItem> = emptyList(),
    val goals: List<GoalItem> = emptyList(),
    val riskComfort: RiskComfort = RiskComfort.MEDIUM,
    val sideIncomeHoursPerWeek: Int = 8,
    val isOnboarded: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.AUTO,
    val appLockEnabled: Boolean = false,
    val appLockPin: String = "",
    val isUnder18: Boolean = false,
    val isStudentMode: Boolean = false,
    val userSituation: String = LifeStage.EARLY_CAREER.label, // "Student", "Early career", "Family", "Self-employed", "Retired"
    val incomeType: IncomeType = IncomeType.STEADY,
    val emergencyBufferMonthsTarget: Double = 1.0,
    val customBufferTargetAmount: Double? = null,
    val monthlySalaryTarget: Double = 2500.0,
    val holdingPotBalance: Double = 0.0,
    val localOnlyMode: Boolean = true,
    val splitRules: List<SplitEnvelopeRule> = listOf(
        SplitEnvelopeRule(EnvelopeType.NEEDS, 50),
        SplitEnvelopeRule(EnvelopeType.FUN, 30),
        SplitEnvelopeRule(EnvelopeType.SAVINGS, 10),
        SplitEnvelopeRule(EnvelopeType.INVESTMENTS, 10)
    )
)

data class GrowthPlanItem(
    val pillar: String, // "Protect", "Save", "Invest", "Trading", "Earn"
    val title: String,
    val description: String,
    val minimumToStart: String,
    val timeHorizon: String,
    val riskLevel: String,
    val realisticRange: String,
    val countryNotes: String,
    val isUncertain: Boolean = false,
    val actionStep: String = ""
)

data class PaperAsset(
    val symbol: String,
    val name: String,
    val assetType: String,
    val price: Double,
    val dayChangePercent: Double,
    val riskRating: String,
    val description: String
)

data class PaperHolding(
    val symbol: String,
    val shares: Double,
    val avgCost: Double
)

// === EXISTING READ-ONLY BANK MODELS (PRESERVED FOR SCHEMA COMPATIBILITY) ===

data class LinkedBankAccount(
    val id: String = java.util.UUID.randomUUID().toString(),
    val institutionName: String, // e.g. "Chase Bank"
    val accountName: String, // e.g. "Total Checking"
    val officialName: String? = null,
    val mask: String, // "•••• 4210" - MASKED ALWAYS (last 4 digits only)
    val type: String, // "checking", "savings", "credit"
    val subtype: String? = null,
    val currentBalance: Double,
    val availableBalance: Double,
    val currency: String = "USD",
    val lastSyncedAt: Long = System.currentTimeMillis()
)

data class LinkedBankTransaction(
    val id: String = java.util.UUID.randomUUID().toString(),
    val accountId: String,
    val name: String, // e.g. "Payroll Direct Deposit ACME Corp" or "Supermarket"
    val amount: Double, // positive = expense/debit, negative = income/credit
    val category: String, // e.g. "Income", "Food and Drink", "Utilities"
    val date: String, // YYYY-MM-DD
    val isPending: Boolean = false,
    val isIncome: Boolean = false
)

data class BankAuditLogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val action: String, // "LINKED", "SYNC_REFRESH", "REVOKED_DELETED", "APP_UNLOCKED"
    val description: String
)

data class BankConnectionState(
    val isLinked: Boolean = false,
    val providerName: String = "Open Banking Sandbox",
    val institutionName: String? = null,
    val accounts: List<LinkedBankAccount> = emptyList(),
    val transactions: List<LinkedBankTransaction> = emptyList(),
    val lastSyncedAt: Long? = null,
    val isSyncing: Boolean = false
)

data class UserAuthSession(
    val isSignedIn: Boolean = false,
    val email: String? = null,
    val displayName: String? = "GrowMint Member",
    val photoUrl: String? = null,
    val provider: String = "Google" // "Google", "Email", "Sandbox"
)

data class DetectedDeposit(
    val transactionId: String,
    val amount: Double,
    val sourceName: String,
    val date: String,
    val isHandled: Boolean = false
)

data class SubscriptionLeak(
    val name: String,
    val amount: Double,
    val frequency: String, // "Monthly", "Annual"
    val category: String,
    val lastChargedDate: String
)

data class AppLockState(
    val isEnabled: Boolean = false,
    val pinCode: String = "",
    val isUnlocked: Boolean = true
)

// === UPGRADE MODELS: SAFETY GATE, SALARY POT, SIDE HUSTLE, CALENDAR, SCAM, SIP, STREAKS & SCORE ===

data class BufferGateStatus(
    val currentEmergencyAmount: Double,
    val targetAmount: Double,
    val typicalMonthlySpending: Double,
    val targetMonths: Double,
    val progressFraction: Float,
    val remainingToTarget: Double,
    val isBufferMet: Boolean,
    val isGuidedModeUnder18: Boolean,
    val isInvestingUnlocked: Boolean
)

data class HoldingPotRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // "DEPOSIT_TO_POT" or "SALARY_RELEASE"
    val amount: Double,
    val description: String,
    val potBalanceAfter: Double
)

data class SideHustleEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val hustleName: String,
    val monthKey: String, // YYYY-MM
    val income: Double,
    val expenses: Double,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    val profit: Double get() = income - expenses
    val marginPercent: Double get() = if (income > 0) ((income - expenses) / income) * 100.0 else 0.0
}

data class MonthlyHustleSummary(
    val monthKey: String,
    val totalIncome: Double,
    val totalExpenses: Double,
    val netProfit: Double,
    val marginPercent: Double
)

data class SideHustleReport(
    val totalIncome: Double,
    val totalExpenses: Double,
    val totalProfit: Double,
    val overallMarginPercent: Double,
    val monthlySummaries: List<MonthlyHustleSummary>,
    val bestMonth: MonthlyHustleSummary?,
    val worstMonth: MonthlyHustleSummary?
)

enum class RepeatFrequency(val label: String) {
    ONE_OFF("One-time"),
    MONTHLY("Monthly"),
    QUARTERLY("Every 3 months"),
    YEARLY("Yearly")
}

data class CalendarReminderItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val category: String = "Bills & Renewals",
    val amount: Double,
    val dueDateMillis: Long,
    val repeat: RepeatFrequency = RepeatFrequency.ONE_OFF,
    val isPaid: Boolean = false
)

data class UpcomingDeadlineView(
    val item: CalendarReminderItem,
    val effectiveDueMillis: Long,
    val daysUntilDue: Int,
    val isDueSoon: Boolean // <= 7 days
)

enum class ScamVerdict(val label: String) {
    LIKELY_SCAM("Likely scam"),
    SUSPICIOUS("Suspicious"),
    LOOKS_FINE("Looks fine")
}

data class ScamCheckResult(
    val verdict: ScamVerdict,
    val redFlags: List<String>,
    val nextSteps: List<String>,
    val safetyNote: String = "Never share OTP, PIN, or passwords. This educational check highlights common warning signs and is not 100% certain."
)

data class SipScenarioResult(
    val label: String, // "Poor Case", "Typical Case", "Good Case"
    val annualRatePercent: Double,
    val finalValue: Double,
    val totalInvested: Double,
    val netGain: Double
)

data class SipSimulationReport(
    val monthlyContribution: Double,
    val initialAmount: Double,
    val years: Int,
    val totalInvested: Double,
    val poorCase: SipScenarioResult,
    val typicalCase: SipScenarioResult,
    val goodCase: SipScenarioResult,
    val worstYearLossPercent: Double,
    val worstYearLossAmountOnTypical: Double,
    val disclaimer: String = "Illustrative, not a prediction. Returns are not guaranteed."
)

data class MoneyLesson(
    val topic: String,
    val title: String,
    val summary: String,
    val readTimeMinutes: Int,
    val body: String,
    val keyTakeaway: String
)

data class WeeklyScoreBreakdownItem(
    val category: String,
    val earnedPoints: Int,
    val maxPoints: Int,
    val detail: String
)

data class WeeklyMoneyScore(
    val totalScore: Int, // 0..100
    val gradeLabel: String,
    val summary: String,
    val breakdown: List<WeeklyScoreBreakdownItem>
)

data class IncomeIdeaItem(
    val title: String,
    val bestFor: String,
    val weeklyHoursNeeded: String,
    val startupCost: String,
    val description: String,
    val firstStep: String
)

// === CHANGE 3: DEBT PAYOFF PLANNER MODELS ===

data class DebtScheduleMonth(
    val monthNumber: Int,
    val monthLabel: String,
    val totalPayment: Double,
    val interestPaid: Double,
    val principalPaid: Double,
    val remainingBalance: Double,
    val activeFocusDebt: String
)

data class DebtPayoffStrategyResult(
    val strategyName: String, // "Snowball" or "Avalanche"
    val monthsToPayoff: Int,
    val payoffDateLabel: String,
    val totalInterestPaid: Double,
    val totalPaid: Double,
    val schedule: List<DebtScheduleMonth>
)

data class DebtComparisonReport(
    val snowball: DebtPayoffStrategyResult,
    val avalanche: DebtPayoffStrategyResult,
    val interestSavedByAvalanche: Double,
    val differenceOneLiner: String
)

// === CHANGE 4: NET WORTH VIEW MODELS ===

enum class AssetCategory(val label: String) {
    CASH("Cash"),
    SAVINGS("Savings"),
    INVESTMENTS("Investments"),
    PROPERTY("Property"),
    OTHER("Other")
}

data class AssetItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val category: AssetCategory,
    val amount: Double
)

data class NetWorthSnapshot(
    val monthKey: String, // YYYY-MM
    val totalAssets: Double,
    val totalDebts: Double,
    val netWorth: Double,
    val timestamp: Long = System.currentTimeMillis()
)

data class NetWorthSummary(
    val manualAssetsTotal: Double,
    val linkedBankAssetsTotal: Double,
    val totalAssets: Double,
    val totalDebts: Double,
    val netWorth: Double,
    val categoryTotals: Map<AssetCategory, Double>,
    val ledgerCashBalanceTotal: Double = 0.0
)

// === CHANGE 5: GOAL PLANNER MODELS ===

data class GoalPlanAnalysis(
    val goal: GoalItem,
    val remainingAmount: Double,
    val monthsRemaining: Int,
    val monthlyAmountNeeded: Double,
    val progressFraction: Float,
    val isOnTrack: Boolean,
    val statusLabel: String,
    val statusReason: String
)

// === CHANGE 6: MONTHLY IMPROVEMENT REPORT MODELS ===

data class MonthlyHealthSnapshot(
    val monthKey: String, // YYYY-MM
    val totalScore: Int,
    val bufferScore: Int,
    val spendingScore: Int,
    val savingsScore: Int,
    val debtScore: Int,
    val bufferAmount: Double,
    val savingsRatePercent: Double,
    val totalDebt: Double,
    val timestamp: Long = System.currentTimeMillis()
)

data class MonthlyActionItem(
    val priority: Int,
    val title: String,
    val what: String,
    val howMuch: String,
    val byWhen: String,
    val actionKey: String = "action_$priority",
    val ruleId: String = "",
    val traceability: ActionTraceability? = null
)

data class MonthlyImprovementReport(
    val currentMonthKey: String,
    val previousMonthKey: String?,
    val currentScore: Int,
    val previousScore: Int?,
    val scoreDelta: Int,
    val gradeLabel: String,
    val summary: String,
    val pillarBreakdown: List<WeeklyScoreBreakdownItem>,
    val whatImproved: List<String>,
    val whatSlipped: List<String>,
    val top3Actions: List<MonthlyActionItem>,
    val currentSnapshot: MonthlyHealthSnapshot,
    val tracedScore: TracedMoneyHealthReport? = null,
    val dataQualityNotes: List<DataQualityNote> = emptyList()
)

// === TRACEABILITY, RULE REGISTRY, SCORE HISTORY, VALIDATION & ERROR FLAGS ===

data class RuleDefinition(
    val id: String,
    val title: String,
    val category: String, // "Score Component", "Recommended Action", "Safety Guardrail"
    val plainDescription: String,
    val triggerCondition: String
)

data class ScoreInputMetric(
    val label: String,
    val valueFormatted: String,
    val sourceNote: String = ""
)

data class DataQualityNote(
    val id: String,
    val label: String,
    val detail: String,
    val isLowQuality: Boolean
)

data class ScoreComponentDetail(
    val id: String, // "buffer", "spending", "savings", "debt"
    val name: String,
    val weightPercent: Int,
    val earnedPoints: Int,
    val maxPoints: Int,
    val formulaPlainWords: String,
    val inputs: List<ScoreInputMetric>,
    val ruleId: String,
    val explanation: String
)

data class TracedMoneyHealthReport(
    val totalScore: Int,
    val gradeLabel: String,
    val calculatedAtMillis: Long,
    val calculatedAtFormatted: String,
    val components: List<ScoreComponentDetail>,
    val dataQualityNotes: List<DataQualityNote>,
    val triggeredRuleIds: List<String>
)

enum class ActionTargetDestination(val label: String) {
    TRANSACTIONS("Open Transactions"),
    SPLIT("Open Auto-Split"),
    DEBT_PLANNER("Open Debt Planner"),
    GOAL_PLANNER("Open Goal Planner"),
    CALENDAR("Open Deadline Calendar"),
    NET_WORTH("Open Net Worth")
}

data class ActionContributorItem(
    val label: String,
    val detail: String,
    val destination: ActionTargetDestination
)

data class ActionTraceability(
    val ruleId: String,
    val ruleTitle: String,
    val ruleDescription: String,
    val triggerCondition: String,
    val triggeringNumbers: List<ScoreInputMetric>,
    val expectedScoreDelta: Int,
    val projectedNewScore: Int,
    val expectedScoreExplanation: String,
    val dataSources: List<String>,
    val contributors: List<ActionContributorItem>
)

data class ScoreHistorySnapshot(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val dateLabel: String,
    val totalScore: Int,
    val bufferScore: Int,
    val spendingScore: Int,
    val savingsScore: Int,
    val debtScore: Int,
    val bufferAmount: Double,
    val typicalMonthlySpending: Double,
    val effectiveIncome: Double,
    val actualSpending: Double,
    val savingsRatePercent: Double,
    val totalDebt: Double,
    val transactionCount: Int,
    val transactionSpanDays: Int,
    val triggeredRuleIds: List<String> = emptyList()
)

data class ComponentDeltaSummary(
    val componentName: String,
    val previousPoints: Int,
    val currentPoints: Int,
    val delta: Int
)

data class ScoreSnapshotDiff(
    val currentSnapshot: ScoreHistorySnapshot,
    val previousSnapshot: ScoreHistorySnapshot?,
    val totalDelta: Int,
    val componentDeltas: List<ComponentDeltaSummary>,
    val primaryReason: String,
    val ruleId: String? = null
)

data class ActivityHistoryEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val category: String, // "Action Completed", "Allocation", "Setting Change", "Check-In", "Plan Update"
    val title: String,
    val whatChanged: String,
    val whySummary: String = "",
    val numbersSummary: String = "",
    val ruleId: String? = null
)

data class ErrorFlagEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val itemType: String, // "MONEY_HEALTH_SCORE", "SCORE_COMPONENT", "TOP_ACTION"
    val itemId: String,
    val itemTitle: String,
    val note: String,
    val snapshotSummary: String = "",
    val ruleId: String? = null
)

// === EFFORTLESS TRANSACTION ENTRY & IMPORT MODELS ===

enum class DateFormatPreference(val label: String) {
    AUTO("Auto"),
    DMY("DD/MM (Day first)"),
    MDY("MM/DD (Month first)")
}

object DefaultTransactionCategories {
    val ALL: List<String> = listOf(
        "Groceries",
        "Dining & Coffee",
        "Transport",
        "Bills & Utilities",
        "Rent & Housing",
        "Subscriptions",
        "Shopping",
        "Health",
        "Education",
        "Income & Salary",
        "Side Hustle",
        "Transfer",
        "General"
    )
}

data class MerchantCategoryRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val merchantPattern: String,
    val category: String,
    val updatedAt: Long = System.currentTimeMillis()
)

data class ExtractedTransactionCandidate(
    val id: String = java.util.UUID.randomUUID().toString(),
    val date: String, // YYYY-MM-DD normalized
    val rawDate: String = date,
    val amount: Double,
    val type: String, // "INCOME" or "EXPENSE"
    val merchant: String,
    val suggestedCategory: String,
    val category: String = suggestedCategory,
    val notes: String = "",
    val isSelected: Boolean = true,
    val isDuplicate: Boolean = false,
    val isDateAmbiguous: Boolean = false,
    val ruleMatched: Boolean = false
)

data class ExtractionBatchResult(
    val candidates: List<ExtractedTransactionCandidate>,
    val foundCount: Int,
    val skippedCount: Int,
    val hasAmbiguousDates: Boolean,
    val sourceType: String, // "PASTE" or "CSV"
    val errorMessage: String? = null
)

data class CsvColumnMapping(
    val dateCol: Int,
    val descriptionCol: Int,
    val amountCol: Int? = null,
    val debitCol: Int? = null,
    val creditCol: Int? = null,
    val hasHeader: Boolean = true
)

data class CsvParsePreview(
    val headers: List<String>,
    val sampleRows: List<List<String>>,
    val allRows: List<List<String>>,
    val suggestedMapping: CsvColumnMapping,
    val needsColumnMapping: Boolean
)

data class ImportBatchRecord(
    val batchId: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val transactionIds: List<String>,
    val netBalanceDelta: Double,
    val incomeTotal: Double,
    val expenseTotal: Double,
    val sourceType: String // "PASTE" or "CSV"
)


