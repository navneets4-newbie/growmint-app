package com.example.data

import com.example.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class ParsedDateResult(
    val isoDate: String, // YYYY-MM-DD
    val timestampMillis: Long,
    val isAmbiguous: Boolean,
    val isValid: Boolean
)

/**
 * Pure Kotlin deterministic engine for:
 * - PII sanitization before any AI call
 * - Strict JSON validation & malformed row rejection
 * - RFC-4180 CSV parsing & column mapping
 * - Multi-currency amount normalization
 * - Multi-format date parsing + DD/MM vs MM/DD ambiguity handling
 * - Smart merchant->category rule matching
 * - Duplicate detection against existing ledger
 * - Recurring subscription / leak detection from transactions
 */
object TransactionImportEngine {

    private val isoFormat: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }

    // === 1. PII SANITIZATION BEFORE AI ===

    fun sanitizeForAi(rawText: String): String {
        var cleaned = rawText
        // OTP / PIN / CVV codes
        cleaned = cleaned.replace(
            Regex("""(?i)\b(otp|pin|passcode|verification\s*code|secret\s*code|cvv)\b[\s:is-]*\d{4,8}"""),
            "$1 [otp-redacted]"
        )
        // Explicit account / card labels followed by masked or full digits
        cleaned = cleaned.replace(
            Regex("""(?i)\b(a/c|acct|account|acc|card)\s*(?:no\.?|number|#|:)?\s*[xX*\d-]{4,22}\d{2,4}\b"""),
            "$1 [redacted]"
        )
        // Card numbers (13-19 digits with optional spaces or dashes)
        cleaned = cleaned.replace(
            Regex("""\b(?:\d[ -]?){13,19}\b"""),
            "[card-redacted]"
        )
        // Email addresses
        cleaned = cleaned.replace(
            Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"""),
            "[email-redacted]"
        )
        // UPI IDs / handles (e.g. rahul123@okicici, name@ybl)
        cleaned = cleaned.replace(
            Regex("""\b[A-Za-z0-9._-]{2,}@[A-Za-z]{2,}\b"""),
            "[upi-id]"
        )
        // Phone numbers (international or 10-digit mobile numbers)
        cleaned = cleaned.replace(
            Regex("""(?:\+\d{1,3}[\s-]?)?(?:\(\d{2,4}\)[\s-]?)?\b\d{3}[\s-]?\d{3}[\s-]?\d{4}\b|\b[6-9]\d{9}\b"""),
            "[phone-redacted]"
        )
        // Standalone long account/reference numbers (9 to 18 digits)
        cleaned = cleaned.replace(
            Regex("""\b\d{9,18}\b"""),
            "[acct-redacted]"
        )
        // Salutations with customer names ("Dear John Doe,", "Hi Priya,")
        cleaned = cleaned.replace(
            Regex("""(?i)\b(dear|hi|hello|mr\.?|mrs\.?|ms\.?|customer)\s+[A-Z][a-z]{1,20}(?:\s+[A-Z][a-z]{1,20}){0,2}"""),
            "$1 [customer]"
        )
        return cleaned.trim()
    }

    // === 2. ANY-CURRENCY AMOUNT PARSING ===

    fun parseSignedAmount(raw: String): Double? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        val isParenNegative = trimmed.startsWith("(") && trimmed.endsWith(")")
        val hasDrSuffix = Regex("""(?i)\b(dr|debit)\b""").containsMatchIn(trimmed)
        val hasCrSuffix = Regex("""(?i)\b(cr|credit)\b""").containsMatchIn(trimmed)
        val hasLeadingMinus = trimmed.startsWith("-") || trimmed.contains("−")

        // Strip currency symbols, letters, spaces
        var numeric = trimmed
            .replace("(", "")
            .replace(")", "")
            .replace("−", "-")
            .replace(Regex("""(?i)\b(usd|inr|eur|gbp|aud|cad|jpy|sgd|aed|rs\.?|cr|dr|debit|credit)\b"""), "")
            .replace(Regex("""[^\d.,+\-]"""), "")
            .trim()

        if (numeric.isBlank() || numeric == "-" || numeric == "+") return null

        // Handle European decimal comma e.g. "123,45" or "1.234,56"
        numeric = normalizeDecimalSeparators(numeric)

        val parsed = numeric.toDoubleOrNull() ?: return null
        val magnitude = abs(parsed)
        if (magnitude.isNaN() || magnitude.isInfinite()) return null

        return when {
            hasCrSuffix -> magnitude
            isParenNegative || hasDrSuffix || hasLeadingMinus || parsed < 0.0 -> -magnitude
            else -> magnitude
        }
    }

    private fun normalizeDecimalSeparators(s: String): String {
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        return when {
            lastComma > lastDot && lastComma >= 0 -> {
                val afterComma = s.length - lastComma - 1
                if (afterComma in 1..2) {
                    // Comma is decimal separator (e.g. 1.234,56 or 45,50)
                    s.replace(".", "").replace(",", ".")
                } else {
                    s.replace(",", "")
                }
            }
            lastComma >= 0 && lastDot < 0 -> {
                val afterComma = s.length - lastComma - 1
                if (afterComma in 1..2 && s.count { it == ',' } == 1) {
                    s.replace(",", ".")
                } else {
                    s.replace(",", "")
                }
            }
            else -> s.replace(",", "")
        }
    }

    // === 3. MULTI-FORMAT DATE PARSING & AMBIGUITY DETECTION ===

    fun parseAndNormalizeDate(
        rawDate: String,
        preference: DateFormatPreference = DateFormatPreference.AUTO,
        fallbackMillis: Long = System.currentTimeMillis()
    ): ParsedDateResult {
        val cleaned = rawDate.trim()
            .replace("T", " ")
            .substringBefore(" ")
            .trim()

        val todayIso = isoFormat.format(Date(fallbackMillis))
        if (cleaned.isBlank()) {
            return ParsedDateResult(todayIso, fallbackMillis, isAmbiguous = false, isValid = false)
        }

        // ISO YYYY-MM-DD or YYYY/MM/DD (unambiguous year-first)
        val ymdMatch = Regex("""^(\d{4})[/-](\d{1,2})[/-](\d{1,2})$""").matchEntire(cleaned)
        if (ymdMatch != null) {
            val y = ymdMatch.groupValues[1].toInt()
            val m = ymdMatch.groupValues[2].toInt()
            val d = ymdMatch.groupValues[3].toInt()
            val cal = buildValidCalendar(y, m, d)
            if (cal != null) {
                return ParsedDateResult(isoFormat.format(cal.time), cal.timeInMillis, isAmbiguous = false, isValid = true)
            }
        }

        // Numeric D/M/Y or M/D/Y or D-M-Y or M-D-Y (2 or 4 digit year)
        val numericMatch = Regex("""^(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{2,4})$""").matchEntire(cleaned)
        if (numericMatch != null) {
            val first = numericMatch.groupValues[1].toInt()
            val second = numericMatch.groupValues[2].toInt()
            var year = numericMatch.groupValues[3].toInt()
            if (year < 100) year += 2000

            val canBeDmy = first in 1..31 && second in 1..12
            val canBeMdy = first in 1..12 && second in 1..31
            val isAmbiguous = canBeDmy && canBeMdy && first != second

            val (day, month) = when {
                !canBeMdy && canBeDmy -> first to second
                !canBeDmy && canBeMdy -> second to first
                preference == DateFormatPreference.MDY && canBeMdy -> second to first
                else -> first to second // Default DMY for AUTO or DMY
            }

            val cal = buildValidCalendar(year, month, day)
            if (cal != null) {
                return ParsedDateResult(
                    isoDate = isoFormat.format(cal.time),
                    timestampMillis = cal.timeInMillis,
                    isAmbiguous = isAmbiguous,
                    isValid = true
                )
            }
        }

        // Named month formats e.g. 15-Jan-2025, 15 Jan 2025, Jan 15, 2025
        val namedFormats = listOf(
            "dd-MMM-yyyy", "dd MMM yyyy", "d MMM yyyy", "d-MMM-yyyy",
            "MMM dd, yyyy", "MMM d, yyyy", "MMM dd yyyy", "MMMM dd, yyyy",
            "dd-MMM-yy", "dd MMM yy"
        )
        for (pattern in namedFormats) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                val parsed = fmt.parse(rawDate.trim())
                if (parsed != null) {
                    val cal = Calendar.getInstance().apply {
                        time = parsed
                        set(Calendar.HOUR_OF_DAY, 12)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    return ParsedDateResult(isoFormat.format(cal.time), cal.timeInMillis, isAmbiguous = false, isValid = true)
                }
            } catch (_: Exception) {
            }
        }

        return ParsedDateResult(todayIso, fallbackMillis, isAmbiguous = false, isValid = false)
    }

    fun reformatCandidatesForDatePreference(
        candidates: List<ExtractedTransactionCandidate>,
        preference: DateFormatPreference,
        existingTransactions: List<LedgerTransaction>
    ): List<ExtractedTransactionCandidate> {
        return candidates.map { item ->
            val parsed = parseAndNormalizeDate(item.rawDate, preference)
            val updated = if (parsed.isValid) item.copy(date = parsed.isoDate) else item
            val dup = isPotentialDuplicate(updated, existingTransactions)
            updated.copy(
                isDuplicate = dup,
                isSelected = if (dup && !item.isSelected) false else !dup && item.isSelected
            )
        }
    }

    fun isoDateToMillis(isoDate: String): Long {
        return try {
            val date = isoFormat.parse(isoDate.trim()) ?: return System.currentTimeMillis()
            Calendar.getInstance().apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    fun millisToIsoDate(millis: Long): String = isoFormat.format(Date(millis))

    private fun buildValidCalendar(year: Int, month: Int, day: Int): Calendar? {
        if (year !in 1990..2100 || month !in 1..12 || day !in 1..31) return null
        return try {
            Calendar.getInstance().apply {
                isLenient = false
                set(year, month - 1, day, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
                timeInMillis // triggers validation
            }
        } catch (_: Exception) {
            null
        }
    }

    // === 4. SMART MERCHANT RULES & DUPLICATE CHECKS ===

    fun normalizeMerchantKey(text: String): String {
        return text.lowercase(Locale.US)
            .replace(Regex("""[*#]\d+"""), " ")
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun findMatchingCategoryRule(
        merchantOrNote: String,
        rules: List<MerchantCategoryRule>
    ): MerchantCategoryRule? {
        val normTarget = normalizeMerchantKey(merchantOrNote)
        if (normTarget.isBlank()) return null
        return rules
            .mapNotNull { rule ->
                val normPattern = normalizeMerchantKey(rule.merchantPattern)
                if (normPattern.isNotBlank() && (normTarget == normPattern || normTarget.contains(normPattern) || normPattern.contains(normTarget))) {
                    normPattern.length to rule
                } else null
            }
            .maxByOrNull { it.first }
            ?.second
    }

    fun isPotentialDuplicate(
        candidate: ExtractedTransactionCandidate,
        existing: List<LedgerTransaction>
    ): Boolean {
        val candMerchant = normalizeMerchantKey(candidate.merchant.ifBlank { candidate.notes })
        return existing.any { tx ->
            if (!tx.type.equals(candidate.type, ignoreCase = true)) return@any false
            if (abs(tx.amount - candidate.amount) > 0.015) return@any false
            val txIso = millisToIsoDate(tx.timestamp)
            if (txIso != candidate.date) return@any false

            val txMerchant = normalizeMerchantKey(tx.merchant.ifBlank { tx.notes })
            if (candMerchant.isBlank() || txMerchant.isBlank()) {
                true
            } else {
                txMerchant == candMerchant || txMerchant.contains(candMerchant) || candMerchant.contains(txMerchant)
            }
        }
    }

    // === 5. AI JSON EXTRACTION VALIDATION ===

    fun validateAndBuildCandidatesFromAiJson(
        rawJson: String,
        existingTransactions: List<LedgerTransaction>,
        rules: List<MerchantCategoryRule>,
        datePreference: DateFormatPreference = DateFormatPreference.AUTO
    ): ExtractionBatchResult {
        var clean = rawJson.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json").substringBeforeLast("```").trim()
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```").substringBeforeLast("```").trim()
        }

        val txArray: JSONArray = try {
            if (clean.startsWith("[")) {
                JSONArray(clean)
            } else {
                val root = JSONObject(clean)
                root.optJSONArray("transactions") ?: JSONArray()
            }
        } catch (e: Exception) {
            return ExtractionBatchResult(
                candidates = emptyList(),
                foundCount = 0,
                skippedCount = 1,
                hasAmbiguousDates = false,
                sourceType = "PASTE",
                errorMessage = "Could not read structured transactions from the response. Try pasting clearer text or add entries manually."
            )
        }

        val candidates = mutableListOf<ExtractedTransactionCandidate>()
        var skipped = 0

        for (i in 0 until txArray.length()) {
            val obj = txArray.optJSONObject(i)
            if (obj == null) {
                skipped++
                continue
            }

            val rawAmountVal = obj.opt("amount")
            val amount = when (rawAmountVal) {
                is Number -> rawAmountVal.toDouble()
                is String -> parseSignedAmount(rawAmountVal) ?: 0.0
                else -> 0.0
            }
            if (amount <= 0.0 || amount.isNaN() || amount.isInfinite()) {
                skipped++
                continue
            }

            val rawType = obj.optString("type", "").trim().lowercase(Locale.US)
            val normalizedType = when (rawType) {
                "income", "credit", "cr", "deposit" -> "INCOME"
                "expense", "debit", "dr", "spent", "withdrawal" -> "EXPENSE"
                else -> {
                    skipped++
                    continue
                }
            }

            val merchant = obj.optString("merchant", "").trim()
            val rawDate = obj.optString("date", "").trim()
            if (merchant.isBlank() && rawDate.isBlank()) {
                skipped++
                continue
            }

            val parsedDate = parseAndNormalizeDate(rawDate, datePreference)
            val aiCategory = obj.optString("suggestedCategory", "General").trim().ifBlank { "General" }
            val matchedRule = findMatchingCategoryRule(merchant, rules)
            val finalCategory = matchedRule?.category ?: normalizeCategoryName(aiCategory, normalizedType)

            val baseCandidate = ExtractedTransactionCandidate(
                date = parsedDate.isoDate,
                rawDate = rawDate.ifBlank { parsedDate.isoDate },
                amount = amount,
                type = normalizedType,
                merchant = merchant.ifBlank { if (normalizedType == "INCOME") "Income Deposit" else "Expense" },
                suggestedCategory = finalCategory,
                category = finalCategory,
                notes = merchant,
                isDateAmbiguous = parsedDate.isAmbiguous,
                ruleMatched = matchedRule != null
            )
            val dup = isPotentialDuplicate(baseCandidate, existingTransactions)
            candidates.add(
                baseCandidate.copy(
                    isDuplicate = dup,
                    isSelected = !dup
                )
            )
        }

        val hasAmbiguous = candidates.any { it.isDateAmbiguous }
        val errorMsg = if (candidates.isEmpty()) {
            "No valid transactions were found. Check the pasted text or add entries manually."
        } else null

        return ExtractionBatchResult(
            candidates = candidates,
            foundCount = candidates.size,
            skippedCount = skipped,
            hasAmbiguousDates = hasAmbiguous,
            sourceType = "PASTE",
            errorMessage = errorMsg
        )
    }

    fun normalizeCategoryName(raw: String, type: String): String {
        val lower = raw.trim().lowercase(Locale.US)
        if (lower.isBlank()) return if (type == "INCOME") "Income & Salary" else "General"
        DefaultTransactionCategories.ALL.firstOrNull { it.equals(raw.trim(), ignoreCase = true) }?.let { return it }
        return when {
            lower.contains("grocer") || lower.contains("supermarket") || lower.contains("mart") -> "Groceries"
            lower.contains("food") || lower.contains("dining") || lower.contains("coffee") || lower.contains("cafe") || lower.contains("restaurant") -> "Dining & Coffee"
            lower.contains("uber") || lower.contains("lyft") || lower.contains("taxi") || lower.contains("fuel") || lower.contains("transit") || lower.contains("transport") -> "Transport"
            lower.contains("rent") || lower.contains("housing") || lower.contains("mortgage") -> "Rent & Housing"
            lower.contains("util") || lower.contains("bill") || lower.contains("electric") || lower.contains("water") || lower.contains("internet") || lower.contains("phone") -> "Bills & Utilities"
            lower.contains("sub") || lower.contains("stream") || lower.contains("music") || lower.contains("membership") -> "Subscriptions"
            lower.contains("shop") || lower.contains("retail") || lower.contains("cloth") -> "Shopping"
            lower.contains("health") || lower.contains("pharm") || lower.contains("med") || lower.contains("doctor") -> "Health"
            lower.contains("edu") || lower.contains("tuition") || lower.contains("book") -> "Education"
            lower.contains("salary") || lower.contains("payroll") || lower.contains("income") || lower.contains("paycheck") -> "Income & Salary"
            lower.contains("freelance") || lower.contains("hustle") || lower.contains("client") -> "Side Hustle"
            lower.contains("transfer") -> "Transfer"
            else -> raw.trim().replaceFirstChar { it.uppercase() }
        }
    }

    // === 6. CSV STATEMENT PARSING & COLUMN MAPPING ===

    fun parseCsvRows(csvText: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        val normalized = csvText.replace("\r\n", "\n").replace("\r", "\n")

        while (i < normalized.length) {
            val ch = normalized[i]
            when {
                inQuotes -> {
                    if (ch == '"') {
                        if (i + 1 < normalized.length && normalized[i + 1] == '"') {
                            cell.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        cell.append(ch)
                    }
                }
                ch == '"' -> inQuotes = true
                ch == ',' -> {
                    currentRow.add(cell.toString().trim())
                    cell.clear()
                }
                ch == '\n' -> {
                    currentRow.add(cell.toString().trim())
                    cell.clear()
                    if (currentRow.any { it.isNotBlank() }) {
                        rows.add(currentRow.toList())
                    }
                    currentRow.clear()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(cell.toString().trim())
            if (currentRow.any { it.isNotBlank() }) {
                rows.add(currentRow.toList())
            }
        }
        return rows
    }

    fun parseCsvStatementPreview(csvText: String): CsvParsePreview? {
        val rows = parseCsvRows(csvText)
        if (rows.isEmpty()) return null

        val firstRow = rows.first()
        val hasHeader = looksLikeHeaderRow(firstRow)
        val headers = if (hasHeader) {
            firstRow.mapIndexed { idx, h -> h.ifBlank { "Column ${idx + 1}" } }
        } else {
            firstRow.indices.map { idx -> "Column ${idx + 1}" }
        }

        val dataRows = if (hasHeader) rows.drop(1) else rows
        if (dataRows.isEmpty()) return null

        var dateCol = -1
        var descCol = -1
        var amountCol: Int? = null
        var debitCol: Int? = null
        var creditCol: Int? = null

        if (hasHeader) {
            headers.forEachIndexed { idx, h ->
                val lower = h.lowercase(Locale.US)
                if (dateCol < 0 && (lower.contains("date") || lower == "dt" || lower.contains("posted") || lower.contains("time"))) {
                    dateCol = idx
                } else if (descCol < 0 && (lower.contains("desc") || lower.contains("merchant") || lower.contains("payee") || lower.contains("narration") || lower.contains("particular") || lower.contains("memo") || lower.contains("details") || lower.contains("name"))) {
                    descCol = idx
                } else if (debitCol == null && (lower.contains("debit") || lower.contains("withdrawal") || lower.contains("spent") || lower == "dr")) {
                    debitCol = idx
                } else if (creditCol == null && (lower.contains("credit") || lower.contains("deposit") || lower == "cr")) {
                    creditCol = idx
                } else if (amountCol == null && (lower.contains("amount") || lower.contains("amt") || lower.contains("value") || lower.contains("total"))) {
                    amountCol = idx
                }
            }
        }

        val hasAmountMapping = amountCol != null || debitCol != null || creditCol != null
        val needsMapping = !hasHeader || dateCol < 0 || descCol < 0 || !hasAmountMapping

        val colCount = headers.size
        val safeDateCol = if (dateCol >= 0) dateCol else 0
        val safeDescCol = if (descCol >= 0) descCol else minOf(1, colCount - 1)
        val safeAmountCol = if (amountCol == null && debitCol == null && creditCol == null) {
            if (colCount >= 3) 2 else null
        } else amountCol

        val mapping = CsvColumnMapping(
            dateCol = safeDateCol,
            descriptionCol = safeDescCol,
            amountCol = safeAmountCol,
            debitCol = debitCol,
            creditCol = creditCol,
            hasHeader = hasHeader
        )

        return CsvParsePreview(
            headers = headers,
            sampleRows = dataRows.take(3),
            allRows = rows,
            suggestedMapping = mapping,
            needsColumnMapping = needsMapping
        )
    }

    private fun looksLikeHeaderRow(row: List<String>): Boolean {
        val joined = row.joinToString(" ").lowercase(Locale.US)
        val headerKeywords = listOf("date", "amount", "description", "merchant", "debit", "credit", "narration", "particular", "balance", "category", "type")
        val matches = headerKeywords.count { joined.contains(it) }
        val hasPureNumber = row.any { it.isNotBlank() && parseSignedAmount(it) != null }
        return matches >= 2 || (!hasPureNumber && matches >= 1)
    }

    fun extractCandidatesFromCsv(
        preview: CsvParsePreview,
        mapping: CsvColumnMapping,
        existingTransactions: List<LedgerTransaction>,
        rules: List<MerchantCategoryRule>,
        datePreference: DateFormatPreference = DateFormatPreference.AUTO
    ): ExtractionBatchResult {
        val dataRows = if (mapping.hasHeader) preview.allRows.drop(1) else preview.allRows
        val candidates = mutableListOf<ExtractedTransactionCandidate>()
        var skipped = 0

        val hasSignedNegativesInAmountCol = mapping.amountCol?.let { col ->
            dataRows.any { row ->
                val cell = row.getOrNull(col)?.trim().orEmpty()
                cell.contains("-") || cell.startsWith("(") || Regex("""(?i)\b(dr|debit)\b""").containsMatchIn(cell)
            }
        } ?: false

        for (row in dataRows) {
            if (row.all { it.isBlank() }) continue

            val rawDate = row.getOrNull(mapping.dateCol)?.trim().orEmpty()
            val rawDesc = row.getOrNull(mapping.descriptionCol)?.trim().orEmpty()

            var resolvedAmount = 0.0
            var resolvedType = "EXPENSE"

            if (mapping.debitCol != null || mapping.creditCol != null) {
                val debitVal = mapping.debitCol?.let { row.getOrNull(it) }?.let { parseSignedAmount(it) }?.let { abs(it) } ?: 0.0
                val creditVal = mapping.creditCol?.let { row.getOrNull(it) }?.let { parseSignedAmount(it) }?.let { abs(it) } ?: 0.0
                when {
                    creditVal > 0.0 && debitVal <= 0.0 -> {
                        resolvedAmount = creditVal
                        resolvedType = "INCOME"
                    }
                    debitVal > 0.0 -> {
                        resolvedAmount = debitVal
                        resolvedType = "EXPENSE"
                    }
                    else -> {
                        skipped++
                        continue
                    }
                }
            } else if (mapping.amountCol != null) {
                val rawAmt = row.getOrNull(mapping.amountCol)?.trim().orEmpty()
                val signed = parseSignedAmount(rawAmt)
                if (signed == null || abs(signed) <= 0.0) {
                    skipped++
                    continue
                }
                resolvedAmount = abs(signed)
                val descLower = rawDesc.lowercase(Locale.US)
                val hasCr = Regex("""(?i)\b(cr|credit)\b""").containsMatchIn(rawAmt) || rawAmt.startsWith("+")
                val descLooksLikeIncome = descLower.contains("salary") || descLower.contains("payroll") ||
                    descLower.contains("deposit") || descLower.contains("refund") || descLower.contains("received from")
                resolvedType = when {
                    signed < 0.0 -> "EXPENSE"
                    hasSignedNegativesInAmountCol -> "INCOME"
                    hasCr || descLooksLikeIncome -> "INCOME"
                    else -> "EXPENSE"
                }
            } else {
                skipped++
                continue
            }

            if (rawDesc.isBlank() && rawDate.isBlank()) {
                skipped++
                continue
            }

            val parsedDate = parseAndNormalizeDate(rawDate, datePreference)
            val merchant = rawDesc.ifBlank { if (resolvedType == "INCOME") "Income Deposit" else "Expense" }
            val matchedRule = findMatchingCategoryRule(merchant, rules)
            val fallbackCat = if (resolvedType == "INCOME") "Income & Salary" else "General"
            val initialCat = matchedRule?.category ?: fallbackCat

            val base = ExtractedTransactionCandidate(
                date = parsedDate.isoDate,
                rawDate = rawDate.ifBlank { parsedDate.isoDate },
                amount = resolvedAmount,
                type = resolvedType,
                merchant = merchant,
                suggestedCategory = initialCat,
                category = initialCat,
                notes = merchant,
                isDateAmbiguous = parsedDate.isAmbiguous,
                ruleMatched = matchedRule != null
            )
            val dup = isPotentialDuplicate(base, existingTransactions)
            candidates.add(base.copy(isDuplicate = dup, isSelected = !dup))
        }

        val hasAmbiguous = candidates.any { it.isDateAmbiguous }
        return ExtractionBatchResult(
            candidates = candidates,
            foundCount = candidates.size,
            skippedCount = skipped,
            hasAmbiguousDates = hasAmbiguous,
            sourceType = "CSV",
            errorMessage = if (candidates.isEmpty()) "No valid rows matched the selected CSV columns." else null
        )
    }

    // === 7. RECURRING LEAK DETECTION ACROSS BANK + LEDGER TRANSACTIONS ===

    private val subscriptionKeywords = listOf(
        "netflix", "spotify", "hulu", "prime", "disney", "youtube", "apple",
        "icloud", "gym", "fitness", "membership", "subscription", "broadband",
        "internet", "adobe", "microsoft", "dropbox", "chatgpt", "audible", "patreon"
    )

    fun detectRecurringLeaks(
        bankTransactions: List<LinkedBankTransaction>,
        ledgerTransactions: List<LedgerTransaction>
    ): List<SubscriptionLeak> {
        val resultMap = linkedMapOf<String, SubscriptionLeak>()

        // 1. Bank transactions (preserve existing detection behavior)
        bankTransactions
            .filter { !it.isIncome && it.amount > 0.0 }
            .groupBy { normalizeMerchantKey(it.name).ifBlank { it.name } }
            .forEach { (key, list) ->
                val sample = list.first()
                val lower = sample.name.lowercase(Locale.US)
                val matchesKeyword = subscriptionKeywords.any { lower.contains(it) }
                if (matchesKeyword || list.size >= 2) {
                    resultMap[key] = SubscriptionLeak(
                        name = sample.name,
                        amount = sample.amount,
                        frequency = "Monthly",
                        category = sample.category,
                        lastChargedDate = sample.date
                    )
                }
            }

        // 2. Imported / manually logged LedgerTransactions
        ledgerTransactions
            .filter { it.type.equals("EXPENSE", ignoreCase = true) && it.amount > 0.0 }
            .groupBy {
                val label = it.merchant.ifBlank { it.notes }.ifBlank { it.category }
                normalizeMerchantKey(label).ifBlank { label.lowercase(Locale.US) }
            }
            .forEach { (key, list) ->
                if (key.isBlank() || key == "general") return@forEach
                val newest = list.maxByOrNull { it.timestamp } ?: return@forEach
                val displayName = newest.merchant.ifBlank { newest.notes }.ifBlank { newest.category }
                val lower = displayName.lowercase(Locale.US)
                val matchesKeyword = subscriptionKeywords.any { lower.contains(it) } ||
                    newest.category.equals("Subscriptions", ignoreCase = true)
                val similarAmountsCount = list.count { abs(it.amount - newest.amount) <= maxOf(1.0, newest.amount * 0.15) }
                if ((matchesKeyword || similarAmountsCount >= 2) && !resultMap.containsKey(key)) {
                    resultMap[key] = SubscriptionLeak(
                        name = displayName,
                        amount = newest.amount,
                        frequency = "Monthly",
                        category = newest.category,
                        lastChargedDate = millisToIsoDate(newest.timestamp)
                    )
                }
            }

        return resultMap.values.toList()
    }
}
