package com.example.data

import com.example.model.RuleDefinition

object RuleRegistry {
    const val RULE_BUFFER_PROGRESS = "RULE_BUFFER_PROGRESS"
    const val RULE_SPENDING_VS_LIMIT = "RULE_SPENDING_VS_LIMIT"
    const val RULE_SAVINGS_RATE = "RULE_SAVINGS_RATE"
    const val RULE_DEBT_LOAD = "RULE_DEBT_LOAD"

    const val RULE_BUFFER_FIRST = "RULE_BUFFER_FIRST"
    const val RULE_TOP_GOAL_FUNDING = "RULE_TOP_GOAL_FUNDING"
    const val RULE_HIGH_APR_DEBT = "RULE_HIGH_APR_DEBT"
    const val RULE_TRIM_SPENDING = "RULE_TRIM_SPENDING"
    const val RULE_CHECKIN_SNAPSHOT = "RULE_CHECKIN_SNAPSHOT"
    const val RULE_GUIDED_UNDER18 = "RULE_GUIDED_UNDER18"

    val ALL_RULES: List<RuleDefinition> = listOf(
        RuleDefinition(
            id = RULE_BUFFER_PROGRESS,
            title = "Emergency Buffer Progress (30% weight)",
            category = "Score Component",
            plainDescription = "Measures how close your Emergency Safety Net balance is to your safety target (default 1.0 month of typical spending or your custom target).",
            triggerCondition = "Points = round((Emergency Balance / Buffer Target) * 30), capped from 0 to 30."
        ),
        RuleDefinition(
            id = RULE_SPENDING_VS_LIMIT,
            title = "Spending vs Income / Limit (25% weight)",
            category = "Score Component",
            plainDescription = "Compares your last 30 days of spending (or setup expense baseline if no transactions are logged) against your monthly income.",
            triggerCondition = "Spend <= 70% of income = 25 pts; <= 85% = 21 pts; <= 100% = 15 pts; <= 115% = 8 pts; > 115% = 3 pts."
        ),
        RuleDefinition(
            id = RULE_SAVINGS_RATE,
            title = "Monthly Savings Rate (25% weight)",
            category = "Score Component",
            plainDescription = "Measures the share of effective monthly income left unspent after expenses and minimum debt payments.",
            triggerCondition = "Savings Rate >= 25% = 25 pts; >= 15% = 20 pts; >= 8% = 14 pts; > 0% = 8 pts; <= 0% = 0 pts."
        ),
        RuleDefinition(
            id = RULE_DEBT_LOAD,
            title = "Debt Load & Interest Burden (20% weight)",
            category = "Score Component",
            plainDescription = "Evaluates total debt relative to annual income and checks whether any high-interest debt (>= 15% APR) exists.",
            triggerCondition = "Debt = 0 -> 20 pts; No >= 15% APR & debt <= 25% annual income -> 16 pts; High-APR <= 1 mo income & debt <= 50% annual income -> 11 pts; otherwise 5 pts."
        ),
        RuleDefinition(
            id = RULE_BUFFER_FIRST,
            title = "Safety-First Emergency Buffer Gate",
            category = "Recommended Action",
            plainDescription = "Prioritizes building your emergency cash safety net before unlocking investing concepts or allocating excess surplus elsewhere.",
            triggerCondition = "Triggered whenever Emergency Safety Net balance < your 1-month (or custom) emergency buffer target."
        ),
        RuleDefinition(
            id = RULE_TOP_GOAL_FUNDING,
            title = "Top Savings Goal Allocation",
            category = "Recommended Action",
            plainDescription = "Once your safety buffer target is met, recommends directing a portion of positive monthly surplus to your primary Goal Planner target.",
            triggerCondition = "Triggered when Emergency Buffer is 100% funded."
        ),
        RuleDefinition(
            id = RULE_HIGH_APR_DEBT,
            title = "High-Interest Debt Principal Paydown",
            category = "Recommended Action",
            plainDescription = "Targets your highest-interest balance with extra principal payments (Avalanche strategy) to reduce guaranteed interest cost.",
            triggerCondition = "Triggered for eligible adult profiles when total debt > 0."
        ),
        RuleDefinition(
            id = RULE_TRIM_SPENDING,
            title = "Recurring Leak & Spending Trim",
            category = "Recommended Action",
            plainDescription = "Highlights recurring subscriptions or everyday expenses where trimming ~5% of monthly spending frees cashflow for savings.",
            triggerCondition = "Triggered when total debt = 0 (or in Guided Mode) to keep monthly spending lean."
        ),
        RuleDefinition(
            id = RULE_CHECKIN_SNAPSHOT,
            title = "Monthly Check-In & Renewal Guard",
            category = "Recommended Action",
            plainDescription = "Encourages saving a monthly Net Worth & Score snapshot and checking upcoming 30-day calendar renewals to prevent surprise bills.",
            triggerCondition = "Recommended every month so score trends and renewal cashflow stay accurate."
        ),
        RuleDefinition(
            id = RULE_GUIDED_UNDER18,
            title = "Guided Mode Education Guardrail (Under 18)",
            category = "Safety Guardrail",
            plainDescription = "Keeps under-18 profiles strictly educational: hides investing allocations, paper trading, and real-money investing advice.",
            triggerCondition = "Active whenever 'Under 18 (Guided Mode)' is selected in setup or Settings."
        )
    )

    fun findById(id: String): RuleDefinition? = ALL_RULES.find { it.id == id }
}
