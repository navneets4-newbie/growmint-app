package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.data.bank.BankProviderAdapter
import com.example.data.bank.SandboxBankProviderAdapter
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.StringReader

class GrowMintRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("growmint_virtual_ledger_prefs", Context.MODE_PRIVATE)

    // Bank Provider Adapter (defaults to Sandbox Open Banking adapter)
    val bankAdapter: BankProviderAdapter = SandboxBankProviderAdapter()

    // Profile & Envelopes
    private val _userProfile = MutableStateFlow(loadProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _envelopes = MutableStateFlow(loadEnvelopes())
    val envelopes: StateFlow<List<VirtualEnvelope>> = _envelopes.asStateFlow()

    private val _transferHistory = MutableStateFlow(loadTransfers())
    val transferHistory: StateFlow<List<TransferRecord>> = _transferHistory.asStateFlow()

    private val _transactions = MutableStateFlow(loadTransactions())
    val transactions: StateFlow<List<LedgerTransaction>> = _transactions.asStateFlow()

    private val _paperCash = MutableStateFlow(prefs.getFloat("paper_cash", 10000.0f).toDouble())
    val paperCash: StateFlow<Double> = _paperCash.asStateFlow()

    private val _paperHoldings = MutableStateFlow(loadPaperHoldings())
    val paperHoldings: StateFlow<List<PaperHolding>> = _paperHoldings.asStateFlow()

    // === CHANGE 2: THEME MODE STATE ===
    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    // === CHANGE 1: BANK LINKING & SECURITY STATES ===
    private val _bankConnection = MutableStateFlow(loadBankConnection())
    val bankConnection: StateFlow<BankConnectionState> = _bankConnection.asStateFlow()

    private val _bankAuditLog = MutableStateFlow(loadBankAuditLog())
    val bankAuditLog: StateFlow<List<BankAuditLogEntry>> = _bankAuditLog.asStateFlow()

    private val _authSession = MutableStateFlow(loadAuthSession())
    val authSession: StateFlow<UserAuthSession> = _authSession.asStateFlow()

    private val _appLock = MutableStateFlow(loadAppLock())
    val appLock: StateFlow<AppLockState> = _appLock.asStateFlow()

    private val _detectedDeposits = MutableStateFlow<List<DetectedDeposit>>(emptyList())
    val detectedDeposits: StateFlow<List<DetectedDeposit>> = _detectedDeposits.asStateFlow()

    private val _subscriptionLeaks = MutableStateFlow<List<SubscriptionLeak>>(loadSubscriptionLeaks())
    val subscriptionLeaks: StateFlow<List<SubscriptionLeak>> = _subscriptionLeaks.asStateFlow()

    // === UPGRADE FEATURES: SALARY POT, SIDE HUSTLES, CALENDAR, STREAKS ===
    private val _holdingPotHistory = MutableStateFlow(loadHoldingPotHistory())
    val holdingPotHistory: StateFlow<List<HoldingPotRecord>> = _holdingPotHistory.asStateFlow()

    private val _sideHustleEntries = MutableStateFlow(loadSideHustleEntries())
    val sideHustleEntries: StateFlow<List<SideHustleEntry>> = _sideHustleEntries.asStateFlow()

    private val _calendarItems = MutableStateFlow(loadCalendarItems())
    val calendarItems: StateFlow<List<CalendarReminderItem>> = _calendarItems.asStateFlow()

    private val _noSpendStreakDays = MutableStateFlow(prefs.getInt("no_spend_streak_days", 0))
    val noSpendStreakDays: StateFlow<Int> = _noSpendStreakDays.asStateFlow()

    private val _lastNoSpendDate = MutableStateFlow(prefs.getString("last_no_spend_date", "") ?: "")
    val lastNoSpendDate: StateFlow<String> = _lastNoSpendDate.asStateFlow()

    private val _lastWeeklyCheckInMillis = MutableStateFlow(prefs.getLong("last_weekly_checkin", 0L))
    val lastWeeklyCheckInMillis: StateFlow<Long> = _lastWeeklyCheckInMillis.asStateFlow()

    // === NEW FEATURES: ASSETS, NET WORTH SNAPSHOTS, MONTHLY HEALTH SNAPSHOTS ===
    private val _assets = MutableStateFlow(loadAssets())
    val assets: StateFlow<List<AssetItem>> = _assets.asStateFlow()

    private val _netWorthSnapshots = MutableStateFlow(loadNetWorthSnapshots())
    val netWorthSnapshots: StateFlow<List<NetWorthSnapshot>> = _netWorthSnapshots.asStateFlow()

    private val _monthlyHealthSnapshots = MutableStateFlow(loadMonthlyHealthSnapshots())
    val monthlyHealthSnapshots: StateFlow<List<MonthlyHealthSnapshot>> = _monthlyHealthSnapshots.asStateFlow()

    // === EFFORTLESS TRANSACTION ENTRY, IMPORT, RULES & UNDO ===
    private val _merchantRules = MutableStateFlow(loadMerchantRules())
    val merchantRules: StateFlow<List<MerchantCategoryRule>> = _merchantRules.asStateFlow()

    private val _favoriteCategories = MutableStateFlow(loadFavoriteCategories())
    val favoriteCategories: StateFlow<Set<String>> = _favoriteCategories.asStateFlow()

    private val _recentCategories = MutableStateFlow(loadRecentCategories())
    val recentCategories: StateFlow<List<String>> = _recentCategories.asStateFlow()

    private val _lastImportBatch = MutableStateFlow(loadLastImportBatch())
    val lastImportBatch: StateFlow<ImportBatchRecord?> = _lastImportBatch.asStateFlow()

    private val _pendingIncomeForSplit = MutableStateFlow<Pair<Double, String>?>(null)
    val pendingIncomeForSplit: StateFlow<Pair<Double, String>?> = _pendingIncomeForSplit.asStateFlow()

    // === TRACEABILITY, SCORE HISTORY, ACTIVITY HISTORY & ERROR FLAGS ===
    private val _scoreHistory = MutableStateFlow(loadScoreHistory())
    val scoreHistory: StateFlow<List<ScoreHistorySnapshot>> = _scoreHistory.asStateFlow()

    private val _completedActionKeys = MutableStateFlow(loadCompletedActionKeys())
    val completedActionKeys: StateFlow<Set<String>> = _completedActionKeys.asStateFlow()

    private val _activityHistory = MutableStateFlow(loadActivityHistory())
    val activityHistory: StateFlow<List<ActivityHistoryEntry>> = _activityHistory.asStateFlow()

    private val _errorFlags = MutableStateFlow(loadErrorFlags())
    val errorFlags: StateFlow<List<ErrorFlagEntry>> = _errorFlags.asStateFlow()

    fun updateThemeMode(mode: AppThemeMode) {
        setThemeMode(mode)
    }

    suspend fun linkSandboxBank(institutionName: String = "Chase Bank"): Result<Boolean> {
        return linkBankInstitution(institutionName)
    }

    // Default Presets
    val presets = listOf(
        SplitPreset(
            id = "preset_50_30_20",
            name = "Classic 50 / 30 / 20",
            description = "50% Needs, 30% Fun/Wants, 20% Savings & Growth",
            rules = listOf(
                SplitEnvelopeRule(EnvelopeType.NEEDS, 50),
                SplitEnvelopeRule(EnvelopeType.FUN, 30),
                SplitEnvelopeRule(EnvelopeType.SAVINGS, 10),
                SplitEnvelopeRule(EnvelopeType.INVESTMENTS, 10)
            )
        ),
        SplitPreset(
            id = "preset_60_20_20",
            name = "Safety First 60 / 20 / 20",
            description = "60% Needs, 20% Emergency & Savings, 20% Lifestyle & Fun",
            rules = listOf(
                SplitEnvelopeRule(EnvelopeType.NEEDS, 60),
                SplitEnvelopeRule(EnvelopeType.EMERGENCY_FUND, 15),
                SplitEnvelopeRule(EnvelopeType.SAVINGS, 10),
                SplitEnvelopeRule(EnvelopeType.FUN, 15)
            )
        ),
        SplitPreset(
            id = "preset_aggressive_growth",
            name = "Wealth Builder 40 / 30 / 30",
            description = "40% Needs, 30% Investments & Reinvestment, 30% Emergency & Fun",
            rules = listOf(
                SplitEnvelopeRule(EnvelopeType.NEEDS, 40),
                SplitEnvelopeRule(EnvelopeType.INVESTMENTS, 20),
                SplitEnvelopeRule(EnvelopeType.BUSINESS_REINVESTMENT, 10),
                SplitEnvelopeRule(EnvelopeType.EMERGENCY_FUND, 15),
                SplitEnvelopeRule(EnvelopeType.FUN, 15)
            )
        )
    )

    init {
        // Initial leak and deposit scan across linked bank & local/imported ledger transactions
        scanAllLeaks()
    }

    // === THEME MANAGEMENT ===
    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("app_theme_mode", mode.name).apply()
        // Also sync to profile
        _userProfile.value = _userProfile.value.copy(themeMode = mode)
    }

    private fun loadThemeMode(): AppThemeMode {
        val raw = prefs.getString("app_theme_mode", null) ?: return AppThemeMode.AUTO
        return try {
            AppThemeMode.valueOf(raw)
        } catch (e: Exception) {
            AppThemeMode.AUTO
        }
    }

    // === APP LOCK MANAGEMENT ===
    fun enableAppLock(pin: String) {
        val lock = AppLockState(isEnabled = true, pinCode = pin, isUnlocked = true)
        _appLock.value = lock
        prefs.edit()
            .putBoolean("app_lock_enabled", true)
            .putString("app_lock_pin", pin)
            .apply()
        addAuditLog("APP_LOCK_CONFIGURED", "4-digit security PIN lock enabled.")
    }

    fun disableAppLock() {
        val lock = AppLockState(isEnabled = false, pinCode = "", isUnlocked = true)
        _appLock.value = lock
        prefs.edit()
            .putBoolean("app_lock_enabled", false)
            .remove("app_lock_pin")
            .apply()
        addAuditLog("APP_LOCK_DISABLED", "Security PIN lock disabled.")
    }

    fun unlockApp(pin: String): Boolean {
        return if (_appLock.value.pinCode == pin) {
            _appLock.value = _appLock.value.copy(isUnlocked = true)
            addAuditLog("APP_UNLOCKED", "App unlocked successfully.")
            true
        } else {
            false
        }
    }

    fun lockApp() {
        if (_appLock.value.isEnabled) {
            _appLock.value = _appLock.value.copy(isUnlocked = false)
        }
    }

    private fun loadAppLock(): AppLockState {
        val enabled = prefs.getBoolean("app_lock_enabled", false)
        val pin = prefs.getString("app_lock_pin", "") ?: ""
        return AppLockState(isEnabled = enabled, pinCode = pin, isUnlocked = !enabled)
    }

    // === USER AUTH SESSION ===
    fun signInUser(email: String, displayName: String, provider: String = "Google") {
        val session = UserAuthSession(
            isSignedIn = true,
            email = email,
            displayName = displayName,
            provider = provider
        )
        _authSession.value = session
        prefs.edit()
            .putBoolean("auth_signed_in", true)
            .putString("auth_email", email)
            .putString("auth_display_name", displayName)
            .putString("auth_provider", provider)
            .apply()
        addAuditLog("AUTH_LOGIN", "Signed in as $displayName ($email) via $provider.")
    }

    fun signOutUser() {
        _authSession.value = UserAuthSession(isSignedIn = false)
        prefs.edit()
            .putBoolean("auth_signed_in", false)
            .remove("auth_email")
            .remove("auth_display_name")
            .apply()
        addAuditLog("AUTH_LOGOUT", "Signed out of session.")
    }

    private fun loadAuthSession(): UserAuthSession {
        val signedIn = prefs.getBoolean("auth_signed_in", false)
        return if (signedIn) {
            UserAuthSession(
                isSignedIn = true,
                email = prefs.getString("auth_email", "member@growmint.app"),
                displayName = prefs.getString("auth_display_name", "GrowMint Member"),
                provider = prefs.getString("auth_provider", "Google") ?: "Google"
            )
        } else {
            UserAuthSession(isSignedIn = false)
        }
    }

    // === BANK LINKING & SYNC ===
    suspend fun linkBankInstitution(institutionName: String): Result<Boolean> {
        _bankConnection.value = _bankConnection.value.copy(isSyncing = true)
        val result = bankAdapter.linkInstitution("inst_${institutionName.lowercase()}", institutionName)
        return if (result.isSuccess) {
            val accounts = result.getOrNull() ?: emptyList()
            val txResult = bankAdapter.fetchTransactions()
            val transactions = txResult.getOrNull() ?: emptyList()

            val state = BankConnectionState(
                isLinked = true,
                providerName = bankAdapter.providerName,
                institutionName = institutionName,
                accounts = accounts,
                transactions = transactions,
                lastSyncedAt = System.currentTimeMillis(),
                isSyncing = false
            )
            _bankConnection.value = state
            saveBankConnection(state)

            addAuditLog("LINKED", "Linked $institutionName with read-only scopes. Masked accounts: ${accounts.size}.")
            scanForDepositsAndLeaks(transactions)

            // Auto-reconcile balance source of truth
            val realTotal = accounts.sumOf { it.currentBalance }
            if (realTotal > 0) {
                updateProfile(_userProfile.value.copy(currentBalance = realTotal))
            }

            Result.success(true)
        } else {
            _bankConnection.value = _bankConnection.value.copy(isSyncing = false)
            Result.failure(result.exceptionOrNull() ?: Exception("Failed to link bank."))
        }
    }

    suspend fun refreshBankData(): Result<Boolean> {
        if (!_bankConnection.value.isLinked) return Result.success(false)
        _bankConnection.value = _bankConnection.value.copy(isSyncing = true)

        val accountsResult = bankAdapter.fetchLinkedAccounts()
        val txResult = bankAdapter.fetchTransactions()

        return if (accountsResult.isSuccess && txResult.isSuccess) {
            val accounts = accountsResult.getOrNull() ?: emptyList()
            val transactions = txResult.getOrNull() ?: emptyList()

            val updated = _bankConnection.value.copy(
                accounts = accounts,
                transactions = transactions,
                lastSyncedAt = System.currentTimeMillis(),
                isSyncing = false
            )
            _bankConnection.value = updated
            saveBankConnection(updated)

            addAuditLog("SYNC_REFRESH", "Refreshed balances and ${transactions.size} transactions.")
            scanForDepositsAndLeaks(transactions)

            // Reconcile total balance
            val realTotal = accounts.sumOf { it.currentBalance }
            if (realTotal > 0) {
                updateProfile(_userProfile.value.copy(currentBalance = realTotal))
            }

            Result.success(true)
        } else {
            _bankConnection.value = _bankConnection.value.copy(isSyncing = false)
            Result.failure(Exception("Sync refresh failed."))
        }
    }

    suspend fun disconnectAndPurgeBankData(): Result<Boolean> {
        bankAdapter.disconnect()
        val emptyState = BankConnectionState(isLinked = false)
        _bankConnection.value = emptyState
        _detectedDeposits.value = emptyList()
        prefs.edit().remove("bank_connection_json").apply()

        addAuditLog("REVOKED_DELETED", "Disconnected bank provider. All stored bank records permanently purged.")
        return Result.success(true)
    }

    suspend fun simulateIncomingDeposit(amount: Double, sourceName: String) {
        val result = bankAdapter.simulateIncomingDeposit(amount, sourceName)
        if (result.isSuccess) {
            val depositTx = result.getOrNull()!!
            val newDeposit = DetectedDeposit(
                transactionId = depositTx.id,
                amount = amount,
                sourceName = sourceName,
                date = "Just now",
                isHandled = false
            )
            _detectedDeposits.value = listOf(newDeposit) + _detectedDeposits.value

            // Refresh account balances
            refreshBankData()
            addAuditLog("DEPOSIT_DETECTED", "Incoming deposit of $amount from $sourceName detected.")
        }
    }

    fun dismissDeposit(transactionId: String) {
        _detectedDeposits.value = _detectedDeposits.value.filter { it.transactionId != transactionId }
    }

    private fun scanForDepositsAndLeaks(txs: List<LinkedBankTransaction>) {
        scanAllLeaks(txs)
    }

    fun scanAllLeaks(bankTxs: List<LinkedBankTransaction> = _bankConnection.value.transactions) {
        val leaks = TransactionImportEngine.detectRecurringLeaks(
            bankTransactions = bankTxs,
            ledgerTransactions = _transactions.value
        )
        _subscriptionLeaks.value = leaks
        saveLeaks(leaks)
    }

    private fun addAuditLog(action: String, description: String) {
        val entry = BankAuditLogEntry(action = action, description = description)
        val updated = listOf(entry) + _bankAuditLog.value
        _bankAuditLog.value = updated
        saveBankAuditLog(updated)
    }

    private fun saveBankConnection(state: BankConnectionState) {
        val json = JSONObject().apply {
            put("isLinked", state.isLinked)
            put("providerName", state.providerName)
            put("institutionName", state.institutionName ?: "")
            put("lastSyncedAt", state.lastSyncedAt ?: 0L)

            val accArr = JSONArray()
            state.accounts.forEach { acc ->
                accArr.put(JSONObject().apply {
                    put("id", acc.id)
                    put("institutionName", acc.institutionName)
                    put("accountName", acc.accountName)
                    put("officialName", acc.officialName ?: "")
                    put("mask", acc.mask)
                    put("type", acc.type)
                    put("currentBalance", acc.currentBalance)
                    put("availableBalance", acc.availableBalance)
                    put("currency", acc.currency)
                    put("lastSyncedAt", acc.lastSyncedAt)
                })
            }
            put("accounts", accArr)

            val txArr = JSONArray()
            state.transactions.forEach { tx ->
                txArr.put(JSONObject().apply {
                    put("id", tx.id)
                    put("accountId", tx.accountId)
                    put("name", tx.name)
                    put("amount", tx.amount)
                    put("category", tx.category)
                    put("date", tx.date)
                    put("isPending", tx.isPending)
                    put("isIncome", tx.isIncome)
                })
            }
            put("transactions", txArr)
        }
        prefs.edit().putString("bank_connection_json", json.toString()).apply()
    }

    private fun loadBankConnection(): BankConnectionState {
        val raw = prefs.getString("bank_connection_json", null) ?: return BankConnectionState()
        return try {
            val json = JSONObject(raw)
            val isLinked = json.optBoolean("isLinked", false)
            val instName = json.optString("institutionName", null)
            val lastSynced = json.optLong("lastSyncedAt", 0L)

            val accounts = mutableListOf<LinkedBankAccount>()
            val accArr = json.optJSONArray("accounts")
            if (accArr != null) {
                for (i in 0 until accArr.length()) {
                    val a = accArr.getJSONObject(i)
                    accounts.add(
                        LinkedBankAccount(
                            id = a.getString("id"),
                            institutionName = a.getString("institutionName"),
                            accountName = a.getString("accountName"),
                            officialName = a.optString("officialName", null),
                            mask = a.getString("mask"),
                            type = a.getString("type"),
                            currentBalance = a.getDouble("currentBalance"),
                            availableBalance = a.getDouble("availableBalance"),
                            currency = a.optString("currency", "USD"),
                            lastSyncedAt = a.optLong("lastSyncedAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            val transactions = mutableListOf<LinkedBankTransaction>()
            val txArr = json.optJSONArray("transactions")
            if (txArr != null) {
                for (i in 0 until txArr.length()) {
                    val t = txArr.getJSONObject(i)
                    transactions.add(
                        LinkedBankTransaction(
                            id = t.getString("id"),
                            accountId = t.getString("accountId"),
                            name = t.getString("name"),
                            amount = t.getDouble("amount"),
                            category = t.getString("category"),
                            date = t.getString("date"),
                            isPending = t.optBoolean("isPending", false),
                            isIncome = t.optBoolean("isIncome", false)
                        )
                    )
                }
            }

            BankConnectionState(
                isLinked = isLinked,
                institutionName = instName,
                accounts = accounts,
                transactions = transactions,
                lastSyncedAt = if (lastSynced > 0) lastSynced else null
            )
        } catch (e: Exception) {
            BankConnectionState()
        }
    }

    private fun saveBankAuditLog(log: List<BankAuditLogEntry>) {
        val arr = JSONArray()
        log.take(50).forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("timestamp", item.timestamp)
                put("action", item.action)
                put("description", item.description)
            })
        }
        prefs.edit().putString("bank_audit_log_json", arr.toString()).apply()
    }

    private fun loadBankAuditLog(): List<BankAuditLogEntry> {
        val raw = prefs.getString("bank_audit_log_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<BankAuditLogEntry>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                list.add(
                    BankAuditLogEntry(
                        id = item.getString("id"),
                        timestamp = item.getLong("timestamp"),
                        action = item.getString("action"),
                        description = item.getString("description")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveLeaks(leaks: List<SubscriptionLeak>) {
        val arr = JSONArray()
        leaks.forEach { l ->
            arr.put(JSONObject().apply {
                put("name", l.name)
                put("amount", l.amount)
                put("frequency", l.frequency)
                put("category", l.category)
                put("lastChargedDate", l.lastChargedDate)
            })
        }
        prefs.edit().putString("subscription_leaks_json", arr.toString()).apply()
    }

    fun loadSubscriptionLeaks(): List<SubscriptionLeak> {
        val raw = prefs.getString("subscription_leaks_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<SubscriptionLeak>()
            for (i in 0 until arr.length()) {
                val l = arr.getJSONObject(i)
                list.add(
                    SubscriptionLeak(
                        name = l.getString("name"),
                        amount = l.getDouble("amount"),
                        frequency = l.getString("frequency"),
                        category = l.getString("category"),
                        lastChargedDate = l.getString("lastChargedDate")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Save profile
    fun updateProfile(profile: UserProfile) {
        _userProfile.value = profile
        val json = JSONObject().apply {
            put("currencyCode", profile.currencyCode)
            put("secondaryCurrency", profile.secondaryCurrency ?: "")
            put("manualExchangeRate", profile.manualExchangeRate ?: -1.0)
            put("country", profile.country)
            put("monthlyIncome", profile.monthlyIncome)
            put("isIrregularIncome", profile.isIrregularIncome)
            put("currentBalance", profile.currentBalance)
            put("riskComfort", profile.riskComfort.name)
            put("sideIncomeHoursPerWeek", profile.sideIncomeHoursPerWeek)
            put("isOnboarded", profile.isOnboarded)
            put("themeMode", profile.themeMode.name)
            put("appLockEnabled", profile.appLockEnabled)
            put("appLockPin", profile.appLockPin)
            put("isUnder18", profile.isUnder18)
            put("isStudentMode", profile.isStudentMode)
            put("userSituation", profile.userSituation)
            put("incomeType", profile.incomeType.name)
            put("emergencyBufferMonthsTarget", profile.emergencyBufferMonthsTarget)
            put("customBufferTargetAmount", profile.customBufferTargetAmount ?: -1.0)
            put("monthlySalaryTarget", profile.monthlySalaryTarget)
            put("holdingPotBalance", profile.holdingPotBalance)
            put("localOnlyMode", profile.localOnlyMode)

            // Expenses
            val expArray = JSONArray()
            profile.expenses.forEach { exp ->
                expArray.put(JSONObject().apply {
                    put("id", exp.id)
                    put("category", exp.category)
                    put("amount", exp.amount)
                })
            }
            put("expenses", expArray)

            // Debts
            val debtArray = JSONArray()
            profile.debts.forEach { d ->
                debtArray.put(JSONObject().apply {
                    put("id", d.id)
                    put("name", d.name)
                    put("totalAmount", d.totalAmount)
                    put("interestRate", d.interestRate)
                    put("minimumPayment", d.minimumPayment)
                })
            }
            put("debts", debtArray)

            // Goals
            val goalArray = JSONArray()
            profile.goals.forEach { g ->
                goalArray.put(JSONObject().apply {
                    put("id", g.id)
                    put("name", g.name)
                    put("targetAmount", g.targetAmount)
                    put("currentAmount", g.currentAmount)
                    put("targetDate", g.targetDate)
                    put("envelopeType", g.envelopeType.name)
                    put("category", g.category)
                })
            }
            put("goals", goalArray)

            // Split Rules
            val splitArray = JSONArray()
            profile.splitRules.forEach { r ->
                splitArray.put(JSONObject().apply {
                    put("envelopeType", r.envelopeType.name)
                    put("percentage", r.percentage)
                })
            }
            put("splitRules", splitArray)
        }
        prefs.edit().putString("user_profile_json", json.toString()).apply()
    }

    private fun loadProfile(): UserProfile {
        val raw = prefs.getString("user_profile_json", null) ?: return UserProfile()
        return try {
            val json = JSONObject(raw)
            val secCur = json.optString("secondaryCurrency", "")
            val exRate = json.optDouble("manualExchangeRate", -1.0)

            val expList = mutableListOf<ExpenseItem>()
            val expArr = json.optJSONArray("expenses")
            if (expArr != null) {
                for (i in 0 until expArr.length()) {
                    val item = expArr.getJSONObject(i)
                    expList.add(ExpenseItem(
                        id = item.optString("id", java.util.UUID.randomUUID().toString()),
                        category = item.optString("category", "General"),
                        amount = item.optDouble("amount", 0.0)
                    ))
                }
            }

            val debtList = mutableListOf<DebtItem>()
            val debtArr = json.optJSONArray("debts")
            if (debtArr != null) {
                for (i in 0 until debtArr.length()) {
                    val item = debtArr.getJSONObject(i)
                    debtList.add(DebtItem(
                        id = item.optString("id", java.util.UUID.randomUUID().toString()),
                        name = item.optString("name", "Debt"),
                        totalAmount = item.optDouble("totalAmount", 0.0),
                        interestRate = item.optDouble("interestRate", 0.0),
                        minimumPayment = item.optDouble("minimumPayment", 0.0)
                    ))
                }
            }

            val goalList = mutableListOf<GoalItem>()
            val goalArr = json.optJSONArray("goals")
            if (goalArr != null) {
                for (i in 0 until goalArr.length()) {
                    val item = goalArr.getJSONObject(i)
                    val envTypeStr = item.optString("envelopeType", EnvelopeType.SAVINGS.name)
                    val envType = try { EnvelopeType.valueOf(envTypeStr) } catch (e: Exception) { EnvelopeType.SAVINGS }
                    goalList.add(GoalItem(
                        id = item.optString("id", java.util.UUID.randomUUID().toString()),
                        name = item.optString("name", "Goal"),
                        targetAmount = item.optDouble("targetAmount", 0.0),
                        currentAmount = item.optDouble("currentAmount", 0.0),
                        targetDate = item.optString("targetDate", "2027"),
                        envelopeType = envType,
                        category = item.optString("category", "Custom")
                    ))
                }
            }

            val splitList = mutableListOf<SplitEnvelopeRule>()
            val splitArr = json.optJSONArray("splitRules")
            if (splitArr != null) {
                for (i in 0 until splitArr.length()) {
                    val item = splitArr.getJSONObject(i)
                    val envType = try { EnvelopeType.valueOf(item.getString("envelopeType")) } catch (e: Exception) { EnvelopeType.NEEDS }
                    splitList.add(SplitEnvelopeRule(envType, item.getInt("percentage")))
                }
            }

            val riskComfort = try {
                RiskComfort.valueOf(json.optString("riskComfort", RiskComfort.MEDIUM.name))
            } catch (e: Exception) {
                RiskComfort.MEDIUM
            }

            val themeMode = try {
                AppThemeMode.valueOf(json.optString("themeMode", AppThemeMode.AUTO.name))
            } catch (e: Exception) {
                AppThemeMode.AUTO
            }

            val isIrregularLegacy = json.optBoolean("isIrregularIncome", false)
            val incomeType = try {
                IncomeType.valueOf(
                    json.optString(
                        "incomeType",
                        if (isIrregularLegacy) IncomeType.IRREGULAR.name else IncomeType.STEADY.name
                    )
                )
            } catch (e: Exception) {
                if (isIrregularLegacy) IncomeType.IRREGULAR else IncomeType.STEADY
            }
            val rawSituation = json.optString("userSituation", LifeStage.EARLY_CAREER.label)
            val normalizedSituation = when (rawSituation) {
                "Side-Hustler", "Freelancer" -> LifeStage.SELF_EMPLOYED.label
                "Salaried" -> LifeStage.EARLY_CAREER.label
                else -> rawSituation
            }

            val customBuf = json.optDouble("customBufferTargetAmount", -1.0)
            UserProfile(
                currencyCode = json.optString("currencyCode", "USD"),
                secondaryCurrency = if (secCur.isNotBlank()) secCur else null,
                manualExchangeRate = if (exRate > 0) exRate else null,
                country = json.optString("country", "United States"),
                monthlyIncome = json.optDouble("monthlyIncome", 3500.0),
                isIrregularIncome = isIrregularLegacy || incomeType == IncomeType.IRREGULAR,
                currentBalance = json.optDouble("currentBalance", 5000.0),
                expenses = expList,
                debts = debtList,
                goals = goalList,
                riskComfort = riskComfort,
                sideIncomeHoursPerWeek = json.optInt("sideIncomeHoursPerWeek", 8),
                isOnboarded = json.optBoolean("isOnboarded", false),
                themeMode = themeMode,
                appLockEnabled = json.optBoolean("appLockEnabled", false),
                appLockPin = json.optString("appLockPin", ""),
                isUnder18 = json.optBoolean("isUnder18", false),
                isStudentMode = json.optBoolean("isStudentMode", false),
                userSituation = normalizedSituation,
                incomeType = incomeType,
                emergencyBufferMonthsTarget = json.optDouble("emergencyBufferMonthsTarget", 1.0),
                customBufferTargetAmount = if (customBuf > 0) customBuf else null,
                monthlySalaryTarget = json.optDouble("monthlySalaryTarget", 2500.0),
                holdingPotBalance = json.optDouble("holdingPotBalance", 0.0),
                localOnlyMode = json.optBoolean("localOnlyMode", true),
                splitRules = if (splitList.isNotEmpty()) splitList else listOf(
                    SplitEnvelopeRule(EnvelopeType.NEEDS, 50),
                    SplitEnvelopeRule(EnvelopeType.FUN, 30),
                    SplitEnvelopeRule(EnvelopeType.SAVINGS, 10),
                    SplitEnvelopeRule(EnvelopeType.INVESTMENTS, 10)
                )
            )
        } catch (e: Exception) {
            UserProfile()
        }
    }

    // === VIRTUAL ENVELOPES & SMART SPLIT ===
    fun executeIncomeSplit(incomeAmount: Double, note: String = "Income Deposit"): TransferRecord {
        val profile = _userProfile.value
        val bufferGate = FinancialCalculator.calculateBufferGateStatus(profile, _envelopes.value)
        // Safety-first gate & Guided Mode enforcement:
        // If investing is locked, reroute any INVESTMENTS split into EMERGENCY_FUND (or SAVINGS if Guided Mode)
        val effectiveRules = if (!bufferGate.isInvestingUnlocked) {
            profile.splitRules.map { rule ->
                if (rule.envelopeType == EnvelopeType.INVESTMENTS) {
                    val targetEnv = if (profile.isUnder18) EnvelopeType.SAVINGS else EnvelopeType.EMERGENCY_FUND
                    rule.copy(envelopeType = targetEnv)
                } else rule
            }
        } else {
            profile.splitRules
        }

        val allocations = mutableMapOf<String, Double>()
        val currentEnvelopes = _envelopes.value.toMutableList()

        effectiveRules.forEach { rule ->
            val splitAmt = (incomeAmount * rule.percentage) / 100.0
            allocations[rule.envelopeType.displayName] = (allocations[rule.envelopeType.displayName] ?: 0.0) + splitAmt

            val envIndex = currentEnvelopes.indexOfFirst { it.type == rule.envelopeType }
            if (envIndex >= 0) {
                val existing = currentEnvelopes[envIndex]
                currentEnvelopes[envIndex] = existing.copy(balance = existing.balance + splitAmt)
            } else {
                currentEnvelopes.add(VirtualEnvelope(
                    id = rule.envelopeType.name.lowercase(),
                    type = rule.envelopeType,
                    name = rule.envelopeType.displayName,
                    balance = splitAmt
                ))
            }
        }

        // Save envelopes
        _envelopes.value = currentEnvelopes
        saveEnvelopes(currentEnvelopes)

        // Update overall balance
        val updatedProfile = profile.copy(currentBalance = profile.currentBalance + incomeAmount)
        updateProfile(updatedProfile)

        // Record transfer
        val record = TransferRecord(
            description = note,
            totalAmount = incomeAmount,
            envelopeAllocations = allocations,
            isUndoable = true
        )
        val history = _transferHistory.value.toMutableList()
        history.add(record)
        _transferHistory.value = history
        saveTransfers(history)

        // Add ledger transaction
        addLedgerTransaction(LedgerTransaction(
            type = "INCOME",
            category = "Deposit",
            amount = incomeAmount,
            notes = "$note (Auto-Split applied)"
        ))

        return record
    }

    fun undoLastTransfer(): Boolean {
        val history = _transferHistory.value.toMutableList()
        val lastUndoable = history.findLast { it.isUndoable } ?: return false

        val currentEnvelopes = _envelopes.value.toMutableList()
        lastUndoable.envelopeAllocations.forEach { (envName, amt) ->
            val idx = currentEnvelopes.indexOfFirst { it.name == envName }
            if (idx >= 0) {
                val env = currentEnvelopes[idx]
                currentEnvelopes[idx] = env.copy(balance = maxOf(0.0, env.balance - amt))
            }
        }
        _envelopes.value = currentEnvelopes
        saveEnvelopes(currentEnvelopes)

        // Revert profile balance
        val profile = _userProfile.value
        updateProfile(profile.copy(currentBalance = maxOf(0.0, profile.currentBalance - lastUndoable.totalAmount)))

        // Mark not undoable
        val itemIndex = history.indexOf(lastUndoable)
        history[itemIndex] = lastUndoable.copy(isUndoable = false, description = "${lastUndoable.description} (UNDONE)")
        _transferHistory.value = history
        saveTransfers(history)

        return true
    }

    private fun saveEnvelopes(list: List<VirtualEnvelope>) {
        val arr = JSONArray()
        list.forEach { env ->
            arr.put(JSONObject().apply {
                put("id", env.id)
                put("type", env.type.name)
                put("name", env.name)
                put("balance", env.balance)
            })
        }
        prefs.edit().putString("envelopes_json", arr.toString()).apply()
    }

    private fun loadEnvelopes(): List<VirtualEnvelope> {
        val raw = prefs.getString("envelopes_json", null)
        if (raw == null) {
            val defaults = listOf(
                VirtualEnvelope("needs", EnvelopeType.NEEDS, EnvelopeType.NEEDS.displayName, 1200.0),
                VirtualEnvelope("savings", EnvelopeType.SAVINGS, EnvelopeType.SAVINGS.displayName, 800.0),
                VirtualEnvelope("emergency", EnvelopeType.EMERGENCY_FUND, EnvelopeType.EMERGENCY_FUND.displayName, 2000.0),
                VirtualEnvelope("investments", EnvelopeType.INVESTMENTS, EnvelopeType.INVESTMENTS.displayName, 500.0),
                VirtualEnvelope("fun", EnvelopeType.FUN, EnvelopeType.FUN.displayName, 300.0),
                VirtualEnvelope("business", EnvelopeType.BUSINESS_REINVESTMENT, EnvelopeType.BUSINESS_REINVESTMENT.displayName, 200.0)
            )
            // Synchronously store defaults without invoking _envelopes
            val arr = JSONArray()
            defaults.forEach { env ->
                arr.put(JSONObject().apply {
                    put("id", env.id)
                    put("type", env.type.name)
                    put("name", env.name)
                    put("balance", env.balance)
                })
            }
            prefs.edit().putString("envelopes_json", arr.toString()).apply()
            return defaults
        }
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<VirtualEnvelope>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val type = try { EnvelopeType.valueOf(item.getString("type")) } catch (e: Exception) { EnvelopeType.CUSTOM }
                list.add(VirtualEnvelope(
                    id = item.getString("id"),
                    type = type,
                    name = item.getString("name"),
                    balance = item.getDouble("balance")
                ))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveTransfers(list: List<TransferRecord>) {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("timestamp", t.timestamp)
                put("description", t.description)
                put("totalAmount", t.totalAmount)
                put("isUndoable", t.isUndoable)
                val allocObj = JSONObject()
                t.envelopeAllocations.forEach { (k, v) -> allocObj.put(k, v) }
                put("allocations", allocObj)
            })
        }
        prefs.edit().putString("transfers_json", arr.toString()).apply()
    }

    private fun loadTransfers(): List<TransferRecord> {
        val raw = prefs.getString("transfers_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<TransferRecord>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val allocObj = item.getJSONObject("allocations")
                val allocs = mutableMapOf<String, Double>()
                allocObj.keys().forEach { k -> allocs[k] = allocObj.getDouble(k) }
                list.add(TransferRecord(
                    id = item.getString("id"),
                    timestamp = item.getLong("timestamp"),
                    description = item.getString("description"),
                    totalAmount = item.getDouble("totalAmount"),
                    envelopeAllocations = allocs,
                    isUndoable = item.optBoolean("isUndoable", true)
                ))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    // === TRANSACTIONS & CSV ===
    fun addLedgerTransaction(tx: LedgerTransaction) {
        val list = _transactions.value.toMutableList()
        list.add(0, tx)
        _transactions.value = list
        saveTransactions(list)
        if (tx.category.isNotBlank()) {
            recordRecentCategory(tx.category)
        }
        scanAllLeaks()
    }

    fun deleteLedgerTransaction(txId: String) {
        val target = _transactions.value.find { it.id == txId } ?: return
        val updated = _transactions.value.filterNot { it.id == txId }
        _transactions.value = updated
        saveTransactions(updated)

        if (!_bankConnection.value.isLinked) {
            val profile = _userProfile.value
            val delta = when {
                target.type.equals("INCOME", ignoreCase = true) -> -target.amount
                target.type.equals("EXPENSE", ignoreCase = true) -> target.amount
                else -> 0.0
            }
            if (delta != 0.0) {
                updateProfile(profile.copy(currentBalance = maxOf(0.0, profile.currentBalance + delta)))
            }
        }
        scanAllLeaks()
    }

    private fun saveTransactions(list: List<LedgerTransaction>) {
        val arr = JSONArray()
        list.forEach { tx ->
            arr.put(JSONObject().apply {
                put("id", tx.id)
                put("timestamp", tx.timestamp)
                put("type", tx.type)
                put("category", tx.category)
                put("amount", tx.amount)
                put("notes", tx.notes)
                put("merchant", tx.merchant)
                put("importBatchId", tx.importBatchId ?: "")
            })
        }
        prefs.edit().putString("transactions_json", arr.toString()).apply()
    }

    private fun loadTransactions(): List<LedgerTransaction> {
        val raw = prefs.getString("transactions_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<LedgerTransaction>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val batchId = obj.optString("importBatchId", "").takeIf { it.isNotBlank() }
                list.add(LedgerTransaction(
                    id = obj.getString("id"),
                    timestamp = obj.getLong("timestamp"),
                    type = obj.getString("type"),
                    category = obj.getString("category"),
                    amount = obj.getDouble("amount"),
                    notes = obj.optString("notes", ""),
                    merchant = obj.optString("merchant", ""),
                    importBatchId = batchId
                ))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun exportTransactionsCsv(): String {
        val sb = StringBuilder()
        sb.append("Timestamp,Type,Category,Amount,Notes\n")
        _transactions.value.forEach { tx ->
            sb.append("${tx.timestamp},\"${tx.type}\",\"${tx.category}\",${tx.amount},\"${tx.notes}\"\n")
        }
        return sb.toString()
    }

    fun importTransactionsCsv(csvContent: String): Int {
        var count = 0
        try {
            val reader = BufferedReader(StringReader(csvContent))
            var line: String? = reader.readLine() // Header
            while (reader.readLine().also { line = it } != null) {
                val tokens = line!!.split(",")
                if (tokens.size >= 4) {
                    val ts = tokens[0].trim().toLongOrNull() ?: System.currentTimeMillis()
                    val type = tokens[1].replace("\"", "").trim()
                    val category = tokens[2].replace("\"", "").trim()
                    val amt = tokens[3].trim().toDoubleOrNull() ?: 0.0
                    val notes = if (tokens.size >= 5) tokens[4].replace("\"", "").trim() else ""
                    addLedgerTransaction(LedgerTransaction(
                        timestamp = ts,
                        type = type,
                        category = category,
                        amount = amt,
                        notes = notes
                    ))
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return count
    }

    // === FINANCIAL HEALTH SCORE (0-100) ===
    fun calculateHealthReport(): FinancialHealthReport {
        val profile = _userProfile.value
        val envelopes = _envelopes.value
        val items = mutableListOf<HealthScoreItem>()
        var totalScore = 0

        val totalExpenses = profile.expenses.sumOf { it.amount } + profile.debts.sumOf { it.minimumPayment }
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L)
        val logged30DayExpense = _transactions.value
            .filter { it.type.equals("EXPENSE", ignoreCase = true) && it.timestamp >= thirtyDaysAgo }
            .sumOf { it.amount }
        val logged30DayIncome = _transactions.value
            .filter { it.type.equals("INCOME", ignoreCase = true) && it.timestamp >= thirtyDaysAgo }
            .sumOf { it.amount }
        val effectiveIncome = maxOf(profile.monthlyIncome, logged30DayIncome)
        val effectiveExpensesForRatio = maxOf(totalExpenses, logged30DayExpense)
        val effectiveMonthlyExpenses = if (effectiveExpensesForRatio > 0) effectiveExpensesForRatio else 1000.0

        // Metric 1: Emergency Runway (Max 30 pts)
        val emergencyBalance = envelopes.find { it.type == EnvelopeType.EMERGENCY_FUND }?.balance ?: 0.0
        val runwayMonths = emergencyBalance / effectiveMonthlyExpenses
        val runwayScore = when {
            runwayMonths >= 6.0 -> 30
            runwayMonths >= 3.0 -> 22
            runwayMonths >= 1.0 -> 12
            else -> 4
        }
        totalScore += runwayScore
        items.add(HealthScoreItem(
            name = "Emergency Cushion",
            score = runwayScore,
            maxScore = 30,
            impact = "+$runwayScore pts",
            positive = runwayScore >= 20,
            explanation = "Your emergency envelope has ${String.format("%.1f", runwayMonths)} months of bills covered. (Target: 3 to 6 months)."
        ))

        // Metric 2: Debt-to-Income / Debt burden (Max 25 pts)
        val totalDebts = profile.debts.sumOf { it.totalAmount }
        val highInterestDebts = profile.debts.filter { it.interestRate >= 15.0 }.sumOf { it.totalAmount }
        val debtScore = when {
            totalDebts == 0.0 -> 25
            highInterestDebts == 0.0 && totalDebts < effectiveIncome * 2 -> 20
            highInterestDebts > 0 && highInterestDebts < effectiveIncome -> 12
            else -> 5
        }
        totalScore += debtScore
        items.add(HealthScoreItem(
            name = "Debt Load & Interest",
            score = debtScore,
            maxScore = 25,
            impact = "+$debtScore pts",
            positive = debtScore >= 18,
            explanation = if (totalDebts == 0.0) "Debt-free! Maximum points awarded." else "Total debt is ${CurrenciesAndCountries.format(totalDebts, profile.currencyCode)}. High interest: ${CurrenciesAndCountries.format(highInterestDebts, profile.currencyCode)}."
        ))

        // Metric 3: Savings / Growth Rate (Max 25 pts)
        val monthlySurplus = effectiveIncome - effectiveExpensesForRatio
        val savingsRate = if (effectiveIncome > 0) (monthlySurplus / effectiveIncome) * 100.0 else 0.0
        val savingsScore = when {
            savingsRate >= 20.0 -> 25
            savingsRate >= 10.0 -> 18
            savingsRate > 0.0 -> 10
            else -> 0
        }
        totalScore += savingsScore
        items.add(HealthScoreItem(
            name = "Savings & Surplus Rate",
            score = savingsScore,
            maxScore = 25,
            impact = "+$savingsScore pts",
            positive = savingsScore >= 15,
            explanation = "You keep ${String.format("%.1f", savingsRate)}% of your monthly earnings after bills, tracked spending, and minimum debt payments."
        ))

        // Metric 4: Envelope Diversification & Goals (Max 20 pts)
        val investmentBalance = envelopes.find { it.type == EnvelopeType.INVESTMENTS }?.balance ?: 0.0
        val activeGoals = profile.goals.size
        val diversificationScore = when {
            investmentBalance > 0 && activeGoals >= 2 -> 20
            activeGoals >= 1 -> 14
            else -> 8
        }
        totalScore += diversificationScore
        items.add(HealthScoreItem(
            name = "Goals & Future Investing",
            score = diversificationScore,
            maxScore = 20,
            impact = "+$diversificationScore pts",
            positive = diversificationScore >= 14,
            explanation = "You have $activeGoals active financial goals and dedicated envelopes for future growth."
        ))

        val grade = when {
            totalScore >= 85 -> "A"
            totalScore >= 70 -> "B"
            totalScore >= 50 -> "C"
            else -> "Needs Attention"
        }

        val summary = when (grade) {
            "A" -> "Outstanding financial posture. You have strong emergency reserves, low or zero high-interest debt, and solid savings."
            "B" -> "Solid baseline with clear upside. Focus on boosting your emergency cushion and paying down any cards above 15% interest."
            "C" -> "Work in progress. Prioritize cutting high-interest debts using the Avalanche method and building a 1-month cash buffer."
            else -> "Immediate attention recommended. Focus on tracking everyday spending and preventing deficit cashflow."
        }

        return FinancialHealthReport(
            overallScore = minOf(100, maxOf(0, totalScore)),
            grade = grade,
            summary = summary,
            breakdown = items
        )
    }

    // === PAPER TRADING SANDBOX ASSETS & EXECUTION ===
    val availablePaperAssets = listOf(
        PaperAsset("VTI", "Vanguard Total Stock Market ETF", "Index ETF", 268.45, 0.65, "Moderate", "Owns tiny pieces of all 3,500+ publicly traded US companies."),
        PaperAsset("VOO", "Vanguard S&P 500 ETF", "Index ETF", 495.20, 0.72, "Moderate", "Tracks America's 500 largest industry leaders."),
        PaperAsset("GLD", "SPDR Gold Shares", "Commodity", 218.10, -0.15, "Low-Moderate", "Physical gold backed trust for inflation protection."),
        PaperAsset("BND", "Total Bond Market ETF", "Bonds", 72.85, 0.05, "Low", "Investment-grade US government and corporate debt."),
        PaperAsset("BTC-SIM", "Bitcoin Simulated Token", "Crypto", 64200.0, 3.40, "Very High", "Highly volatile cryptocurrency play asset. High risk of capital loss."),
        PaperAsset("ETH-SIM", "Ethereum Simulated Token", "Crypto", 3450.0, 2.10, "Very High", "Decentralized smart contract network asset with high market swings."),
        PaperAsset("AAPL", "Apple Inc.", "Stock", 188.50, 1.10, "Moderate-High", "Global consumer technology, hardware, and services leader."),
        PaperAsset("BIO-INNOV", "Biotech Innovation Fund", "Equities", 68.30, -1.20, "High", "High growth volatility across drug discovery and genomics.")
    )

    fun executePaperTrade(symbol: String, shares: Double, isBuy: Boolean): Boolean {
        val asset = availablePaperAssets.find { it.symbol == symbol } ?: return false
        val totalCost = asset.price * shares
        val currentCash = _paperCash.value
        val holdings = _paperHoldings.value.toMutableList()

        if (isBuy) {
            if (currentCash < totalCost) return false
            _paperCash.value = currentCash - totalCost
            prefs.edit().putFloat("paper_cash", _paperCash.value.toFloat()).apply()

            val idx = holdings.indexOfFirst { it.symbol == symbol }
            if (idx >= 0) {
                val existing = holdings[idx]
                val newShares = existing.shares + shares
                val newAvg = ((existing.shares * existing.avgCost) + totalCost) / newShares
                holdings[idx] = existing.copy(shares = newShares, avgCost = newAvg)
            } else {
                holdings.add(PaperHolding(symbol, shares, asset.price))
            }
        } else {
            val idx = holdings.indexOfFirst { it.symbol == symbol }
            if (idx < 0 || holdings[idx].shares < shares) return false
            val existing = holdings[idx]
            val remShares = existing.shares - shares
            if (remShares <= 0.0001) {
                holdings.removeAt(idx)
            } else {
                holdings[idx] = existing.copy(shares = remShares)
            }
            _paperCash.value = currentCash + totalCost
            prefs.edit().putFloat("paper_cash", _paperCash.value.toFloat()).apply()
        }

        _paperHoldings.value = holdings
        savePaperHoldings(holdings)
        return true
    }

    private fun savePaperHoldings(list: List<PaperHolding>) {
        val arr = JSONArray()
        list.forEach { h ->
            arr.put(JSONObject().apply {
                put("symbol", h.symbol)
                put("shares", h.shares)
                put("avgCost", h.avgCost)
            })
        }
        prefs.edit().putString("paper_holdings_json", arr.toString()).apply()
    }

    private fun loadPaperHoldings(): List<PaperHolding> {
        val raw = prefs.getString("paper_holdings_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<PaperHolding>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(PaperHolding(
                    symbol = obj.getString("symbol"),
                    shares = obj.getDouble("shares"),
                    avgCost = obj.getDouble("avgCost")
                ))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun resetAllData() {
        prefs.edit().clear().apply()
        _userProfile.value = UserProfile()
        _envelopes.value = EnvelopeType.values().map { VirtualEnvelope(it.name.lowercase(), it, it.displayName, 0.0) }
        _transferHistory.value = emptyList()
        _transactions.value = emptyList()
        _paperCash.value = 10000.0
        _paperHoldings.value = emptyList()
        _bankConnection.value = BankConnectionState()
        _bankAuditLog.value = emptyList()
        _detectedDeposits.value = emptyList()
        _subscriptionLeaks.value = emptyList()
        _holdingPotHistory.value = emptyList()
        _sideHustleEntries.value = emptyList()
        _calendarItems.value = emptyList()
        _noSpendStreakDays.value = 0
        _lastNoSpendDate.value = ""
        _lastWeeklyCheckInMillis.value = 0L
        _assets.value = emptyList()
        _netWorthSnapshots.value = emptyList()
        _monthlyHealthSnapshots.value = emptyList()
        _themeMode.value = AppThemeMode.AUTO
        _appLock.value = AppLockState()
        _merchantRules.value = emptyList()
        _favoriteCategories.value = setOf("Groceries", "Dining & Coffee", "Transport", "Bills & Utilities")
        _recentCategories.value = emptyList()
        _lastImportBatch.value = null
        _pendingIncomeForSplit.value = null
        _scoreHistory.value = emptyList()
        _completedActionKeys.value = emptySet()
        _activityHistory.value = emptyList()
        _errorFlags.value = emptyList()
    }

    fun deleteAllUserData() {
        resetAllData()
    }

    // === PAY-YOURSELF-A-SALARY MODE (HOLDING POT) ===
    fun depositToHoldingPot(amount: Double, source: String) {
        if (amount <= 0.0) return
        val profile = _userProfile.value
        val newPotBalance = profile.holdingPotBalance + amount
        updateProfile(profile.copy(holdingPotBalance = newPotBalance))

        val rec = HoldingPotRecord(
            type = "DEPOSIT_TO_POT",
            amount = amount,
            description = source.ifBlank { "Irregular Payout" },
            potBalanceAfter = newPotBalance
        )
        val updated = listOf(rec) + _holdingPotHistory.value
        _holdingPotHistory.value = updated
        saveHoldingPotHistory(updated)

        addLedgerTransaction(LedgerTransaction(
            type = "INCOME",
            category = "Holding Pot",
            amount = amount,
            notes = "Held in Salary Pot: $source"
        ))
    }

    fun releaseMonthlySalary(amountOverride: Double? = null): Boolean {
        val profile = _userProfile.value
        val targetRelease = amountOverride ?: profile.monthlySalaryTarget
        if (targetRelease <= 0.0 || profile.holdingPotBalance < targetRelease) {
            return false
        }
        val newPotBalance = maxOf(0.0, profile.holdingPotBalance - targetRelease)
        updateProfile(profile.copy(holdingPotBalance = newPotBalance))

        val rec = HoldingPotRecord(
            type = "SALARY_RELEASE",
            amount = targetRelease,
            description = "Monthly Salary Release",
            potBalanceAfter = newPotBalance
        )
        val updated = listOf(rec) + _holdingPotHistory.value
        _holdingPotHistory.value = updated
        saveHoldingPotHistory(updated)

        executeIncomeSplit(targetRelease, "Pay-Yourself-a-Salary Release")
        return true
    }

    private fun saveHoldingPotHistory(list: List<HoldingPotRecord>) {
        val arr = JSONArray()
        list.take(50).forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("timestamp", r.timestamp)
                put("type", r.type)
                put("amount", r.amount)
                put("description", r.description)
                put("potBalanceAfter", r.potBalanceAfter)
            })
        }
        prefs.edit().putString("holding_pot_history_json", arr.toString()).apply()
    }

    private fun loadHoldingPotHistory(): List<HoldingPotRecord> {
        val raw = prefs.getString("holding_pot_history_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HoldingPotRecord(
                    id = o.getString("id"),
                    timestamp = o.getLong("timestamp"),
                    type = o.getString("type"),
                    amount = o.getDouble("amount"),
                    description = o.getString("description"),
                    potBalanceAfter = o.getDouble("potBalanceAfter")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // === SIDE-HUSTLE PROFIT TRACKER ===
    fun addSideHustleEntry(entry: SideHustleEntry) {
        val updated = listOf(entry) + _sideHustleEntries.value
        _sideHustleEntries.value = updated
        saveSideHustleEntries(updated)
    }

    fun deleteSideHustleEntry(id: String) {
        val updated = _sideHustleEntries.value.filterNot { it.id == id }
        _sideHustleEntries.value = updated
        saveSideHustleEntries(updated)
    }

    private fun saveSideHustleEntries(list: List<SideHustleEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("hustleName", e.hustleName)
                put("monthKey", e.monthKey)
                put("income", e.income)
                put("expenses", e.expenses)
                put("notes", e.notes)
                put("timestamp", e.timestamp)
            })
        }
        prefs.edit().putString("side_hustle_entries_json", arr.toString()).apply()
    }

    private fun loadSideHustleEntries(): List<SideHustleEntry> {
        val raw = prefs.getString("side_hustle_entries_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SideHustleEntry(
                    id = o.getString("id"),
                    hustleName = o.getString("hustleName"),
                    monthKey = o.getString("monthKey"),
                    income = o.getDouble("income"),
                    expenses = o.getDouble("expenses"),
                    notes = o.optString("notes", ""),
                    timestamp = o.optLong("timestamp", System.currentTimeMillis())
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // === DEADLINE & RENEWAL CALENDAR ===
    fun addCalendarItem(item: CalendarReminderItem) {
        val updated = listOf(item) + _calendarItems.value
        _calendarItems.value = updated
        saveCalendarItems(updated)
    }

    fun toggleCalendarItemPaid(id: String) {
        val updated = _calendarItems.value.map {
            if (it.id == id) it.copy(isPaid = !it.isPaid) else it
        }
        _calendarItems.value = updated
        saveCalendarItems(updated)
    }

    fun deleteCalendarItem(id: String) {
        val updated = _calendarItems.value.filterNot { it.id == id }
        _calendarItems.value = updated
        saveCalendarItems(updated)
    }

    private fun saveCalendarItems(list: List<CalendarReminderItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("category", item.category)
                put("amount", item.amount)
                put("dueDateMillis", item.dueDateMillis)
                put("repeat", item.repeat.name)
                put("isPaid", item.isPaid)
            })
        }
        prefs.edit().putString("calendar_items_json", arr.toString()).apply()
    }

    private fun loadCalendarItems(): List<CalendarReminderItem> {
        val raw = prefs.getString("calendar_items_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val rep = try { RepeatFrequency.valueOf(o.getString("repeat")) } catch (e: Exception) { RepeatFrequency.ONE_OFF }
                CalendarReminderItem(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    category = o.optString("category", "Bills & Renewals"),
                    amount = o.getDouble("amount"),
                    dueDateMillis = o.getLong("dueDateMillis"),
                    repeat = rep,
                    isPaid = o.optBoolean("isPaid", false)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // === NO-SPEND STREAK & WEEKLY CHECK-IN ===
    fun logNoSpendDay(): Boolean {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val cal = java.util.Calendar.getInstance()
        val todayStr = sdf.format(cal.time)
        if (_lastNoSpendDate.value == todayStr) {
            return false // Already logged today
        }
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = sdf.format(cal.time)

        val newStreak = if (_lastNoSpendDate.value == yesterdayStr) {
            _noSpendStreakDays.value + 1
        } else {
            1
        }
        _noSpendStreakDays.value = newStreak
        _lastNoSpendDate.value = todayStr
        prefs.edit()
            .putInt("no_spend_streak_days", newStreak)
            .putString("last_no_spend_date", todayStr)
            .apply()
        return true
    }

    fun completeWeeklyCheckIn() {
        val now = System.currentTimeMillis()
        _lastWeeklyCheckInMillis.value = now
        prefs.edit().putLong("last_weekly_checkin", now).apply()
    }

    // === DEBT PLANNER & GOAL PLANNER PROFILE MUTATIONS ===
    fun addDebtItem(debt: DebtItem) {
        val p = _userProfile.value
        updateProfile(p.copy(debts = p.debts + debt))
    }

    fun deleteDebtItem(debtId: String) {
        val p = _userProfile.value
        updateProfile(p.copy(debts = p.debts.filterNot { it.id == debtId }))
    }

    fun addGoalItem(goal: GoalItem) {
        val p = _userProfile.value
        updateProfile(p.copy(goals = p.goals + goal))
    }

    fun updateGoalSavedAmount(goalId: String, newSavedAmount: Double) {
        val p = _userProfile.value
        val updatedGoals = p.goals.map {
            if (it.id == goalId) it.copy(currentAmount = maxOf(0.0, newSavedAmount)) else it
        }
        updateProfile(p.copy(goals = updatedGoals))
    }

    fun deleteGoalItem(goalId: String) {
        val p = _userProfile.value
        updateProfile(p.copy(goals = p.goals.filterNot { it.id == goalId }))
    }

    // === ASSETS & NET WORTH SNAPSHOTS ===
    fun addAsset(asset: AssetItem) {
        val updated = listOf(asset) + _assets.value
        _assets.value = updated
        saveAssets(updated)
    }

    fun deleteAsset(assetId: String) {
        val updated = _assets.value.filterNot { it.id == assetId }
        _assets.value = updated
        saveAssets(updated)
    }

    fun saveMonthlyNetWorthSnapshot(snapshot: NetWorthSnapshot) {
        val withoutMonth = _netWorthSnapshots.value.filterNot { it.monthKey == snapshot.monthKey }
        val updated = (withoutMonth + snapshot).sortedBy { it.monthKey }
        _netWorthSnapshots.value = updated
        saveNetWorthSnapshots(updated)
    }

    private fun saveAssets(list: List<AssetItem>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("name", a.name)
                put("category", a.category.name)
                put("amount", a.amount)
            })
        }
        prefs.edit().putString("manual_assets_json", arr.toString()).apply()
    }

    private fun loadAssets(): List<AssetItem> {
        val raw = prefs.getString("manual_assets_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val cat = try { AssetCategory.valueOf(o.getString("category")) } catch (e: Exception) { AssetCategory.OTHER }
                AssetItem(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    category = cat,
                    amount = o.getDouble("amount")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveNetWorthSnapshots(list: List<NetWorthSnapshot>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("monthKey", s.monthKey)
                put("totalAssets", s.totalAssets)
                put("totalDebts", s.totalDebts)
                put("netWorth", s.netWorth)
                put("timestamp", s.timestamp)
            })
        }
        prefs.edit().putString("net_worth_snapshots_json", arr.toString()).apply()
    }

    private fun loadNetWorthSnapshots(): List<NetWorthSnapshot> {
        val raw = prefs.getString("net_worth_snapshots_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                NetWorthSnapshot(
                    monthKey = o.getString("monthKey"),
                    totalAssets = o.getDouble("totalAssets"),
                    totalDebts = o.getDouble("totalDebts"),
                    netWorth = o.getDouble("netWorth"),
                    timestamp = o.optLong("timestamp", System.currentTimeMillis())
                )
            }.sortedBy { it.monthKey }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // === MONTHLY HEALTH SNAPSHOTS ===
    fun saveMonthlyHealthSnapshot(snapshot: MonthlyHealthSnapshot) {
        val withoutMonth = _monthlyHealthSnapshots.value.filterNot { it.monthKey == snapshot.monthKey }
        val updated = (withoutMonth + snapshot).sortedBy { it.monthKey }
        _monthlyHealthSnapshots.value = updated
        saveMonthlyHealthSnapshots(updated)
    }

    private fun saveMonthlyHealthSnapshots(list: List<MonthlyHealthSnapshot>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("monthKey", s.monthKey)
                put("totalScore", s.totalScore)
                put("bufferScore", s.bufferScore)
                put("spendingScore", s.spendingScore)
                put("savingsScore", s.savingsScore)
                put("debtScore", s.debtScore)
                put("bufferAmount", s.bufferAmount)
                put("savingsRatePercent", s.savingsRatePercent)
                put("totalDebt", s.totalDebt)
                put("timestamp", s.timestamp)
            })
        }
        prefs.edit().putString("monthly_health_snapshots_json", arr.toString()).apply()
    }

    private fun loadMonthlyHealthSnapshots(): List<MonthlyHealthSnapshot> {
        val raw = prefs.getString("monthly_health_snapshots_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MonthlyHealthSnapshot(
                    monthKey = o.getString("monthKey"),
                    totalScore = o.getInt("totalScore"),
                    bufferScore = o.getInt("bufferScore"),
                    spendingScore = o.getInt("spendingScore"),
                    savingsScore = o.getInt("savingsScore"),
                    debtScore = o.getInt("debtScore"),
                    bufferAmount = o.getDouble("bufferAmount"),
                    savingsRatePercent = o.getDouble("savingsRatePercent"),
                    totalDebt = o.getDouble("totalDebt"),
                    timestamp = o.optLong("timestamp", System.currentTimeMillis())
                )
            }.sortedBy { it.monthKey }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // === EFFORTLESS TRANSACTION ENTRY: QUICK-ADD, BATCH IMPORT, UNDO, SMART RULES ===

    fun recordQuickAdd(
        amount: Double,
        isExpense: Boolean,
        category: String,
        note: String,
        learnRuleIfCorrected: Boolean = false,
        suggestedCategory: String? = null
    ): LedgerTransaction? {
        if (amount <= 0.0) return null
        val cleanCategory = category.trim().ifBlank { if (isExpense) "General" else "Income & Salary" }
        val cleanNote = note.trim()
        val tx = LedgerTransaction(
            timestamp = System.currentTimeMillis(),
            type = if (isExpense) "EXPENSE" else "INCOME",
            category = cleanCategory,
            amount = amount,
            notes = cleanNote,
            merchant = cleanNote
        )
        addLedgerTransaction(tx)

        if (!_bankConnection.value.isLinked) {
            val profile = _userProfile.value
            val updatedBalance = if (isExpense) {
                maxOf(0.0, profile.currentBalance - amount)
            } else {
                profile.currentBalance + amount
            }
            updateProfile(profile.copy(currentBalance = updatedBalance))
        }

        if (learnRuleIfCorrected && cleanNote.isNotBlank() &&
            !suggestedCategory.isNullOrBlank() && !cleanCategory.equals(suggestedCategory, ignoreCase = true)
        ) {
            saveMerchantRule(cleanNote, cleanCategory)
        }
        return tx
    }

    fun getMostRecentSimilarTransaction(query: String = ""): LedgerTransaction? {
        val candidates = _transactions.value.filter {
            it.type.equals("EXPENSE", ignoreCase = true) || it.type.equals("INCOME", ignoreCase = true)
        }
        if (candidates.isEmpty()) return null
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            val normQuery = TransactionImportEngine.normalizeMerchantKey(trimmed)
            val match = candidates.firstOrNull { tx ->
                val txKey = TransactionImportEngine.normalizeMerchantKey(tx.merchant.ifBlank { tx.notes }.ifBlank { tx.category })
                txKey.contains(normQuery) || normQuery.contains(txKey)
            }
            if (match != null) return match
        }
        return candidates.firstOrNull()
    }

    fun confirmImportBatch(
        selectedCandidates: List<ExtractedTransactionCandidate>,
        sourceType: String
    ): ImportBatchRecord? {
        val toSave = selectedCandidates.filter { it.isSelected && it.amount > 0.0 }
        if (toSave.isEmpty()) return null

        val batchId = java.util.UUID.randomUUID().toString()
        val newTransactions = mutableListOf<LedgerTransaction>()
        var incomeTotal = 0.0
        var expenseTotal = 0.0

        toSave.forEach { cand ->
            val txType = if (cand.type.equals("INCOME", ignoreCase = true)) "INCOME" else "EXPENSE"
            val cleanCat = cand.category.trim().ifBlank { if (txType == "INCOME") "Income & Salary" else "General" }
            val cleanMerchant = cand.merchant.trim()
            val tx = LedgerTransaction(
                timestamp = TransactionImportEngine.isoDateToMillis(cand.date),
                type = txType,
                category = cleanCat,
                amount = cand.amount,
                notes = cand.notes.trim().ifBlank { cleanMerchant },
                merchant = cleanMerchant,
                importBatchId = batchId
            )
            newTransactions.add(tx)
            if (txType == "INCOME") incomeTotal += cand.amount else expenseTotal += cand.amount

            // Smart rule learning whenever user corrected the category for a merchant
            if (cleanMerchant.isNotBlank() && !cleanCat.equals(cand.suggestedCategory, ignoreCase = true)) {
                saveMerchantRule(cleanMerchant, cleanCat)
            }
            recordRecentCategory(cleanCat)
        }

        val combined = (newTransactions + _transactions.value).sortedByDescending { it.timestamp }
        _transactions.value = combined
        saveTransactions(combined)

        val netDelta = incomeTotal - expenseTotal
        if (!_bankConnection.value.isLinked && netDelta != 0.0) {
            val p = _userProfile.value
            updateProfile(p.copy(currentBalance = maxOf(0.0, p.currentBalance + netDelta)))
        }

        val record = ImportBatchRecord(
            batchId = batchId,
            timestamp = System.currentTimeMillis(),
            transactionIds = newTransactions.map { it.id },
            netBalanceDelta = netDelta,
            incomeTotal = incomeTotal,
            expenseTotal = expenseTotal,
            sourceType = sourceType
        )
        _lastImportBatch.value = record
        saveLastImportBatch(record)

        if (incomeTotal > 0.0) {
            val topIncomeSource = newTransactions
                .firstOrNull { it.type == "INCOME" }
                ?.let { it.merchant.ifBlank { it.notes }.ifBlank { "Imported Income" } }
                ?: "Imported Income"
            _pendingIncomeForSplit.value = incomeTotal to topIncomeSource
        }

        scanAllLeaks()
        return record
    }

    fun undoLastImport(): Boolean {
        val batch = _lastImportBatch.value ?: return false
        val idSet = batch.transactionIds.toSet()
        val remaining = _transactions.value.filterNot { it.id in idSet || it.importBatchId == batch.batchId }
        _transactions.value = remaining
        saveTransactions(remaining)

        if (!_bankConnection.value.isLinked && batch.netBalanceDelta != 0.0) {
            val p = _userProfile.value
            updateProfile(p.copy(currentBalance = maxOf(0.0, p.currentBalance - batch.netBalanceDelta)))
        }

        _lastImportBatch.value = null
        prefs.edit().remove("last_import_batch_json").apply()
        _pendingIncomeForSplit.value = null
        scanAllLeaks()
        return true
    }

    fun dismissPendingIncomePrompt() {
        _pendingIncomeForSplit.value = null
    }

    fun saveMerchantRule(merchantPattern: String, category: String, ruleId: String? = null) {
        val cleanPattern = merchantPattern.trim()
        val cleanCategory = category.trim()
        if (cleanPattern.isBlank() || cleanCategory.isBlank()) return

        val normTarget = TransactionImportEngine.normalizeMerchantKey(cleanPattern)
        val existingList = _merchantRules.value.toMutableList()
        val matchIdx = if (ruleId != null) {
            existingList.indexOfFirst { it.id == ruleId }
        } else {
            existingList.indexOfFirst {
                TransactionImportEngine.normalizeMerchantKey(it.merchantPattern) == normTarget
            }
        }

        val rule = MerchantCategoryRule(
            id = if (matchIdx >= 0) existingList[matchIdx].id else (ruleId ?: java.util.UUID.randomUUID().toString()),
            merchantPattern = cleanPattern,
            category = cleanCategory,
            updatedAt = System.currentTimeMillis()
        )
        if (matchIdx >= 0) {
            existingList[matchIdx] = rule
        } else {
            existingList.add(0, rule)
        }
        _merchantRules.value = existingList
        saveMerchantRules(existingList)
    }

    fun deleteMerchantRule(ruleId: String) {
        val updated = _merchantRules.value.filterNot { it.id == ruleId }
        _merchantRules.value = updated
        saveMerchantRules(updated)
    }

    fun toggleFavoriteCategory(category: String) {
        val clean = category.trim()
        if (clean.isBlank()) return
        val next = _favoriteCategories.value.toMutableSet()
        if (next.contains(clean)) next.remove(clean) else next.add(clean)
        _favoriteCategories.value = next
        saveFavoriteCategories(next)
    }

    private fun recordRecentCategory(category: String) {
        val clean = category.trim()
        if (clean.isBlank()) return
        val updated = (listOf(clean) + _recentCategories.value.filterNot { it.equals(clean, ignoreCase = true) }).take(8)
        _recentCategories.value = updated
        saveRecentCategories(updated)
    }

    private fun saveMerchantRules(rules: List<MerchantCategoryRule>) {
        val arr = JSONArray()
        rules.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("merchantPattern", r.merchantPattern)
                put("category", r.category)
                put("updatedAt", r.updatedAt)
            })
        }
        prefs.edit().putString("merchant_category_rules_json", arr.toString()).apply()
    }

    private fun loadMerchantRules(): List<MerchantCategoryRule> {
        val raw = prefs.getString("merchant_category_rules_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MerchantCategoryRule(
                    id = o.optString("id", java.util.UUID.randomUUID().toString()),
                    merchantPattern = o.getString("merchantPattern"),
                    category = o.getString("category"),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveFavoriteCategories(set: Set<String>) {
        val arr = JSONArray()
        set.forEach { arr.put(it) }
        prefs.edit().putString("favorite_categories_json", arr.toString()).apply()
    }

    private fun loadFavoriteCategories(): Set<String> {
        val raw = prefs.getString("favorite_categories_json", null)
            ?: return setOf("Groceries", "Dining & Coffee", "Transport", "Bills & Utilities")
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (_: Exception) {
            setOf("Groceries", "Dining & Coffee", "Transport", "Bills & Utilities")
        }
    }

    private fun saveRecentCategories(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString("recent_categories_json", arr.toString()).apply()
    }

    private fun loadRecentCategories(): List<String> {
        val raw = prefs.getString("recent_categories_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveLastImportBatch(batch: ImportBatchRecord) {
        val obj = JSONObject().apply {
            put("batchId", batch.batchId)
            put("timestamp", batch.timestamp)
            put("netBalanceDelta", batch.netBalanceDelta)
            put("incomeTotal", batch.incomeTotal)
            put("expenseTotal", batch.expenseTotal)
            put("sourceType", batch.sourceType)
            val ids = JSONArray()
            batch.transactionIds.forEach { ids.put(it) }
            put("transactionIds", ids)
        }
        prefs.edit().putString("last_import_batch_json", obj.toString()).apply()
    }

    private fun loadLastImportBatch(): ImportBatchRecord? {
        val raw = prefs.getString("last_import_batch_json", null) ?: return null
        return try {
            val o = JSONObject(raw)
            val idsArr = o.optJSONArray("transactionIds") ?: JSONArray()
            val ids = (0 until idsArr.length()).map { idsArr.getString(it) }
            ImportBatchRecord(
                batchId = o.getString("batchId"),
                timestamp = o.getLong("timestamp"),
                transactionIds = ids,
                netBalanceDelta = o.getDouble("netBalanceDelta"),
                incomeTotal = o.getDouble("incomeTotal"),
                expenseTotal = o.getDouble("expenseTotal"),
                sourceType = o.optString("sourceType", "PASTE")
            )
        } catch (_: Exception) {
            null
        }
    }

    // === TRACEABILITY: SCORE HISTORY SNAPSHOTS, ACTION COMPLETION, ACTIVITY HISTORY & ERROR FLAGS ===

    fun recordScoreSnapshotIfChanged(nowMillis: Long = System.currentTimeMillis()): ScoreHistorySnapshot {
        val snap = FinancialCalculator.buildScoreHistorySnapshot(
            profile = _userProfile.value,
            envelopes = _envelopes.value,
            transactions = _transactions.value,
            nowMillis = nowMillis
        )
        val currentList = _scoreHistory.value
        val latest = currentList.lastOrNull()
        val hasMeaningfulChange = latest == null ||
            latest.totalScore != snap.totalScore ||
            latest.bufferScore != snap.bufferScore ||
            latest.spendingScore != snap.spendingScore ||
            latest.savingsScore != snap.savingsScore ||
            latest.debtScore != snap.debtScore ||
            kotlin.math.abs(latest.bufferAmount - snap.bufferAmount) >= 1.0 ||
            kotlin.math.abs(latest.totalDebt - snap.totalDebt) >= 1.0 ||
            latest.transactionCount != snap.transactionCount

        if (hasMeaningfulChange) {
            val updated = (currentList + snap).sortedBy { it.timestamp }.takeLast(60)
            _scoreHistory.value = updated
            saveScoreHistory(updated)
        }
        return snap
    }

    fun markActionDone(
        actionKey: String,
        title: String,
        whatSummary: String,
        whySummary: String,
        numbersSummary: String,
        ruleId: String?
    ) {
        if (actionKey.isBlank()) return
        val next = _completedActionKeys.value + actionKey
        _completedActionKeys.value = next
        saveCompletedActionKeys(next)
        logConfirmedActivity(
            category = "Action Completed",
            title = title,
            whatChanged = whatSummary,
            whySummary = whySummary,
            numbersSummary = numbersSummary,
            ruleId = ruleId
        )
    }

    fun logConfirmedActivity(
        category: String,
        title: String,
        whatChanged: String,
        whySummary: String = "",
        numbersSummary: String = "",
        ruleId: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val entry = ActivityHistoryEntry(
            timestamp = timestamp,
            category = category,
            title = title,
            whatChanged = whatChanged,
            whySummary = whySummary,
            numbersSummary = numbersSummary,
            ruleId = ruleId
        )
        val updated = (listOf(entry) + _activityHistory.value).take(200)
        _activityHistory.value = updated
        saveActivityHistory(updated)
    }

    fun flagItemError(
        itemType: String,
        itemId: String,
        itemTitle: String,
        note: String,
        snapshotSummary: String = "",
        ruleId: String? = null
    ) {
        val cleanNote = note.trim().ifBlank { "Flagged for review by user." }
        val flag = ErrorFlagEntry(
            itemType = itemType,
            itemId = itemId,
            itemTitle = itemTitle,
            note = cleanNote,
            snapshotSummary = snapshotSummary,
            ruleId = ruleId
        )
        val updated = (listOf(flag) + _errorFlags.value).take(100)
        _errorFlags.value = updated
        saveErrorFlags(updated)
    }

    fun clearErrorFlag(flagId: String) {
        val updated = _errorFlags.value.filterNot { it.id == flagId }
        _errorFlags.value = updated
        saveErrorFlags(updated)
    }

    fun exportActivityHistoryCsv(): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        fun esc(s: String): String = "\"${s.replace("\"", "\"\"")}\""
        val sb = StringBuilder()
        sb.append("Timestamp,Date,Category,Title,WhatChanged,Why,BackingNumbers,RuleId\n")
        _activityHistory.value.forEach { e ->
            val dateStr = fmt.format(java.util.Date(e.timestamp))
            sb.append("${e.timestamp},${esc(dateStr)},${esc(e.category)},${esc(e.title)},${esc(e.whatChanged)},${esc(e.whySummary)},${esc(e.numbersSummary)},${esc(e.ruleId ?: "")}\n")
        }
        return sb.toString()
    }

    fun exportActivityHistoryText(): String {
        val fmt = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.US)
        val sb = StringBuilder()
        sb.append("GrowMint Activity History & Error Flags\n")
        sb.append("=======================================\n\n")
        if (_activityHistory.value.isEmpty()) {
            sb.append("No confirmed changes logged yet.\n")
        } else {
            _activityHistory.value.forEach { e ->
                sb.append("[${fmt.format(java.util.Date(e.timestamp))}] ${e.category}: ${e.title}\n")
                sb.append("  What changed: ${e.whatChanged}\n")
                if (e.whySummary.isNotBlank()) sb.append("  Why: ${e.whySummary}\n")
                if (e.numbersSummary.isNotBlank()) sb.append("  Numbers: ${e.numbersSummary}\n")
                if (!e.ruleId.isNullOrBlank()) sb.append("  Rule ID: ${e.ruleId}\n")
                sb.append("\n")
            }
        }
        if (_errorFlags.value.isNotEmpty()) {
            sb.append("Flagged Items (This looks wrong)\n")
            sb.append("---------------------------------\n")
            _errorFlags.value.forEach { f ->
                sb.append("[${fmt.format(java.util.Date(f.timestamp))}] ${f.itemType} - ${f.itemTitle}\n")
                sb.append("  Note: ${f.note}\n")
                if (f.snapshotSummary.isNotBlank()) sb.append("  Context: ${f.snapshotSummary}\n")
                if (!f.ruleId.isNullOrBlank()) sb.append("  Rule ID: ${f.ruleId}\n")
                sb.append("\n")
            }
        }
        return sb.toString()
    }

    private fun saveScoreHistory(list: List<ScoreHistorySnapshot>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("timestamp", s.timestamp)
                put("dateLabel", s.dateLabel)
                put("totalScore", s.totalScore)
                put("bufferScore", s.bufferScore)
                put("spendingScore", s.spendingScore)
                put("savingsScore", s.savingsScore)
                put("debtScore", s.debtScore)
                put("bufferAmount", s.bufferAmount)
                put("typicalMonthlySpending", s.typicalMonthlySpending)
                put("effectiveIncome", s.effectiveIncome)
                put("actualSpending", s.actualSpending)
                put("savingsRatePercent", s.savingsRatePercent)
                put("totalDebt", s.totalDebt)
                put("transactionCount", s.transactionCount)
                put("transactionSpanDays", s.transactionSpanDays)
                val rArr = JSONArray()
                s.triggeredRuleIds.forEach { rArr.put(it) }
                put("triggeredRuleIds", rArr)
            })
        }
        prefs.edit().putString("score_history_snapshots_json", arr.toString()).apply()
    }

    private fun loadScoreHistory(): List<ScoreHistorySnapshot> {
        val raw = prefs.getString("score_history_snapshots_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val rArr = o.optJSONArray("triggeredRuleIds") ?: JSONArray()
                val rules = (0 until rArr.length()).map { rArr.getString(it) }
                ScoreHistorySnapshot(
                    id = o.optString("id", java.util.UUID.randomUUID().toString()),
                    timestamp = o.getLong("timestamp"),
                    dateLabel = o.optString("dateLabel", ""),
                    totalScore = o.getInt("totalScore"),
                    bufferScore = o.getInt("bufferScore"),
                    spendingScore = o.getInt("spendingScore"),
                    savingsScore = o.getInt("savingsScore"),
                    debtScore = o.getInt("debtScore"),
                    bufferAmount = o.optDouble("bufferAmount", 0.0),
                    typicalMonthlySpending = o.optDouble("typicalMonthlySpending", 0.0),
                    effectiveIncome = o.optDouble("effectiveIncome", 0.0),
                    actualSpending = o.optDouble("actualSpending", 0.0),
                    savingsRatePercent = o.optDouble("savingsRatePercent", 0.0),
                    totalDebt = o.optDouble("totalDebt", 0.0),
                    transactionCount = o.optInt("transactionCount", 0),
                    transactionSpanDays = o.optInt("transactionSpanDays", 0),
                    triggeredRuleIds = rules
                )
            }.sortedBy { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCompletedActionKeys(keys: Set<String>) {
        val arr = JSONArray()
        keys.forEach { arr.put(it) }
        prefs.edit().putString("completed_action_keys_json", arr.toString()).apply()
    }

    private fun loadCompletedActionKeys(): Set<String> {
        val raw = prefs.getString("completed_action_keys_json", null) ?: return emptySet()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun saveActivityHistory(list: List<ActivityHistoryEntry>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("timestamp", a.timestamp)
                put("category", a.category)
                put("title", a.title)
                put("whatChanged", a.whatChanged)
                put("whySummary", a.whySummary)
                put("numbersSummary", a.numbersSummary)
                put("ruleId", a.ruleId ?: "")
            })
        }
        prefs.edit().putString("activity_history_json", arr.toString()).apply()
    }

    private fun loadActivityHistory(): List<ActivityHistoryEntry> {
        val raw = prefs.getString("activity_history_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val rId = o.optString("ruleId", "").ifBlank { null }
                ActivityHistoryEntry(
                    id = o.optString("id", java.util.UUID.randomUUID().toString()),
                    timestamp = o.getLong("timestamp"),
                    category = o.optString("category", "Change"),
                    title = o.getString("title"),
                    whatChanged = o.optString("whatChanged", ""),
                    whySummary = o.optString("whySummary", ""),
                    numbersSummary = o.optString("numbersSummary", ""),
                    ruleId = rId
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveErrorFlags(list: List<ErrorFlagEntry>) {
        val arr = JSONArray()
        list.forEach { f ->
            arr.put(JSONObject().apply {
                put("id", f.id)
                put("timestamp", f.timestamp)
                put("itemType", f.itemType)
                put("itemId", f.itemId)
                put("itemTitle", f.itemTitle)
                put("note", f.note)
                put("snapshotSummary", f.snapshotSummary)
                put("ruleId", f.ruleId ?: "")
            })
        }
        prefs.edit().putString("error_flags_json", arr.toString()).apply()
    }

    private fun loadErrorFlags(): List<ErrorFlagEntry> {
        val raw = prefs.getString("error_flags_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val rId = o.optString("ruleId", "").ifBlank { null }
                ErrorFlagEntry(
                    id = o.optString("id", java.util.UUID.randomUUID().toString()),
                    timestamp = o.getLong("timestamp"),
                    itemType = o.optString("itemType", "ITEM"),
                    itemId = o.optString("itemId", ""),
                    itemTitle = o.optString("itemTitle", ""),
                    note = o.optString("note", ""),
                    snapshotSummary = o.optString("snapshotSummary", ""),
                    ruleId = rId
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

