package com.example.data.bank

import com.example.model.LinkedBankAccount
import com.example.model.LinkedBankTransaction
import kotlinx.coroutines.delay

interface BankProviderAdapter {
    val providerName: String
    suspend fun fetchLinkedAccounts(): Result<List<LinkedBankAccount>>
    suspend fun fetchTransactions(): Result<List<LinkedBankTransaction>>
    suspend fun linkInstitution(institutionId: String, institutionName: String): Result<List<LinkedBankAccount>>
    suspend fun disconnect(): Result<Boolean>
    suspend fun simulateIncomingDeposit(amount: Double, sourceName: String): Result<LinkedBankTransaction>
}

class SandboxBankProviderAdapter : BankProviderAdapter {
    override val providerName: String = "Plaid / Open Banking (Sandbox)"

    private var isConnected = false
    private var currentInstitution = "Chase Bank"
    private val accounts = mutableListOf<LinkedBankAccount>()
    private val transactions = mutableListOf<LinkedBankTransaction>()

    init {
        // Seed initial sandbox accounts
        resetSandboxData("Chase Bank", "USD")
    }

    fun resetSandboxData(institutionName: String, currency: String = "USD") {
        currentInstitution = institutionName
        accounts.clear()
        transactions.clear()

        when (institutionName) {
            "Chase Bank" -> {
                accounts.add(
                    LinkedBankAccount(
                        id = "sandbox_chase_chk_1",
                        institutionName = "Chase Bank",
                        accountName = "Total Checking",
                        officialName = "Chase Total Checking Account",
                        mask = "•••• 4210",
                        type = "checking",
                        subtype = "checking",
                        currentBalance = 3450.00,
                        availableBalance = 3450.00,
                        currency = currency,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
                accounts.add(
                    LinkedBankAccount(
                        id = "sandbox_chase_sav_2",
                        institutionName = "Chase Bank",
                        accountName = "Premier Savings",
                        officialName = "Chase Premier Savings",
                        mask = "•••• 8912",
                        type = "savings",
                        subtype = "savings",
                        currentBalance = 8200.00,
                        availableBalance = 8200.00,
                        currency = currency,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            }
            "Bank of America" -> {
                accounts.add(
                    LinkedBankAccount(
                        id = "sandbox_bofa_chk_1",
                        institutionName = "Bank of America",
                        accountName = "Advantage Checking",
                        officialName = "BofA Core Checking",
                        mask = "•••• 1044",
                        type = "checking",
                        subtype = "checking",
                        currentBalance = 4120.00,
                        availableBalance = 4120.00,
                        currency = currency,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            }
            "Monzo Bank (UK)" -> {
                accounts.add(
                    LinkedBankAccount(
                        id = "sandbox_monzo_chk_1",
                        institutionName = "Monzo Bank (UK)",
                        accountName = "Current Account",
                        officialName = "Monzo Personal Current Account",
                        mask = "•••• 5590",
                        type = "checking",
                        subtype = "checking",
                        currentBalance = 2950.00,
                        availableBalance = 2950.00,
                        currency = "GBP",
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            }
            "HDFC Bank (India)" -> {
                accounts.add(
                    LinkedBankAccount(
                        id = "sandbox_hdfc_sav_1",
                        institutionName = "HDFC Bank (India)",
                        accountName = "Savings Max Account",
                        officialName = "HDFC Regular Savings",
                        mask = "•••• 3321",
                        type = "savings",
                        subtype = "savings",
                        currentBalance = 145000.00,
                        availableBalance = 145000.00,
                        currency = "INR",
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            }
            "Commonwealth Bank (AU)" -> {
                accounts.add(
                    LinkedBankAccount(
                        id = "sandbox_comm_chk_1",
                        institutionName = "Commonwealth Bank (AU)",
                        accountName = "Smart Access",
                        officialName = "CBA Everyday Smart Access",
                        mask = "•••• 9012",
                        type = "checking",
                        subtype = "checking",
                        currentBalance = 5600.00,
                        availableBalance = 5600.00,
                        currency = "AUD",
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            }
            else -> {
                accounts.add(
                    LinkedBankAccount(
                        id = "sandbox_generic_chk_1",
                        institutionName = institutionName,
                        accountName = "Primary Checking",
                        officialName = "$institutionName Everyday Account",
                        mask = "•••• 7714",
                        type = "checking",
                        subtype = "checking",
                        currentBalance = 3200.00,
                        availableBalance = 3200.00,
                        currency = currency,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        // Seed realistic read-only transactions
        transactions.add(
            LinkedBankTransaction(
                id = "tx_001",
                accountId = accounts.first().id,
                name = "Direct Deposit ACME Corp Payroll",
                amount = -2850.00, // income deposit
                category = "Income",
                date = "Yesterday",
                isPending = false,
                isIncome = true
            )
        )
        transactions.add(
            LinkedBankTransaction(
                id = "tx_002",
                accountId = accounts.first().id,
                name = "Whole Foods Supermarket",
                amount = 142.50,
                category = "Food & Groceries",
                date = "2 days ago",
                isPending = false,
                isIncome = false
            )
        )
        transactions.add(
            LinkedBankTransaction(
                id = "tx_003",
                accountId = accounts.first().id,
                name = "Netflix Subscription",
                amount = 15.99,
                category = "Entertainment",
                date = "3 days ago",
                isPending = false,
                isIncome = false
            )
        )
        transactions.add(
            LinkedBankTransaction(
                id = "tx_004",
                accountId = accounts.first().id,
                name = "City Utilities Electric",
                amount = 115.20,
                category = "Utilities",
                date = "5 days ago",
                isPending = false,
                isIncome = false
            )
        )
        transactions.add(
            LinkedBankTransaction(
                id = "tx_005",
                accountId = accounts.first().id,
                name = "Spotify Premium",
                amount = 11.99,
                category = "Entertainment",
                date = "6 days ago",
                isPending = false,
                isIncome = false
            )
        )
    }

    override suspend fun fetchLinkedAccounts(): Result<List<LinkedBankAccount>> {
        delay(400) // Simulate fast network roundtrip
        return if (isConnected) {
            // Update lastSynced timestamp
            val updated = accounts.map { it.copy(lastSyncedAt = System.currentTimeMillis()) }
            Result.success(updated)
        } else {
            Result.success(emptyList())
        }
    }

    override suspend fun fetchTransactions(): Result<List<LinkedBankTransaction>> {
        delay(300)
        return Result.success(transactions.toList())
    }

    override suspend fun linkInstitution(institutionId: String, institutionName: String): Result<List<LinkedBankAccount>> {
        delay(800) // Simulate secure hosted consent flow
        isConnected = true
        resetSandboxData(institutionName)
        return Result.success(accounts.toList())
    }

    override suspend fun disconnect(): Result<Boolean> {
        delay(300)
        isConnected = false
        accounts.clear()
        transactions.clear()
        return Result.success(true)
    }

    override suspend fun simulateIncomingDeposit(amount: Double, sourceName: String): Result<LinkedBankTransaction> {
        val depositTx = LinkedBankTransaction(
            id = "tx_${System.currentTimeMillis()}",
            accountId = accounts.firstOrNull()?.id ?: "sandbox_acc_1",
            name = sourceName,
            amount = -amount, // income
            category = "Income",
            date = "Just now",
            isPending = false,
            isIncome = true
        )
        transactions.add(0, depositTx)

        // Adjust checking account balance
        if (accounts.isNotEmpty()) {
            val first = accounts[0]
            accounts[0] = first.copy(
                currentBalance = first.currentBalance + amount,
                availableBalance = first.availableBalance + amount,
                lastSyncedAt = System.currentTimeMillis()
            )
        }
        return Result.success(depositTx)
    }
}
