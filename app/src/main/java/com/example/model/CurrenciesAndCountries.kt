package com.example.model

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale

data class CurrencyInfo(
    val code: String,
    val name: String,
    val symbol: String,
    val decimalDigits: Int = 2
)

data class CountryInfo(
    val code: String,
    val name: String,
    val defaultCurrency: String
)

object CurrenciesAndCountries {
    val ALL_CURRENCIES: List<CurrencyInfo> = listOf(
        CurrencyInfo("USD", "US Dollar", "$", 2),
        CurrencyInfo("EUR", "Euro", "€", 2),
        CurrencyInfo("GBP", "British Pound", "£", 2),
        CurrencyInfo("JPY", "Japanese Yen", "¥", 0),
        CurrencyInfo("CAD", "Canadian Dollar", "CA$", 2),
        CurrencyInfo("AUD", "Australian Dollar", "A$", 2),
        CurrencyInfo("CHF", "Swiss Franc", "CHF", 2),
        CurrencyInfo("CNY", "Chinese Yuan", "¥", 2),
        CurrencyInfo("INR", "Indian Rupee", "₹", 2),
        CurrencyInfo("BRL", "Brazilian Real", "R$", 2),
        CurrencyInfo("ZAR", "South African Rand", "R", 2),
        CurrencyInfo("AED", "UAE Dirham", "AED", 2),
        CurrencyInfo("SGD", "Singapore Dollar", "S$", 2),
        CurrencyInfo("MXN", "Mexican Peso", "MX$", 2),
        CurrencyInfo("NZD", "New Zealand Dollar", "NZ$", 2),
        CurrencyInfo("SEK", "Swedish Krona", "kr", 2),
        CurrencyInfo("NOK", "Norwegian Krone", "kr", 2),
        CurrencyInfo("DKK", "Danish Krone", "kr", 2),
        CurrencyInfo("PLN", "Polish Zloty", "zł", 2),
        CurrencyInfo("THB", "Thai Baht", "฿", 2),
        CurrencyInfo("IDR", "Indonesian Rupiah", "Rp", 0),
        CurrencyInfo("MYR", "Malaysian Ringgit", "RM", 2),
        CurrencyInfo("PHP", "Philippine Peso", "₱", 2),
        CurrencyInfo("TRY", "Turkish Lira", "₺", 2),
        CurrencyInfo("SAR", "Saudi Riyal", "SAR", 2),
        CurrencyInfo("ILS", "Israeli Shekel", "₪", 2),
        CurrencyInfo("HKD", "Hong Kong Dollar", "HK$", 2),
        CurrencyInfo("KRW", "South Korean Won", "₩", 0),
        CurrencyInfo("CLP", "Chilean Peso", "CLP$", 0),
        CurrencyInfo("COP", "Colombian Peso", "COL$", 0),
        CurrencyInfo("PEN", "Peruvian Sol", "S/.", 2),
        CurrencyInfo("ARS", "Argentine Peso", "ARS$", 2),
        CurrencyInfo("EGP", "Egyptian Pound", "E£", 2),
        CurrencyInfo("NGN", "Nigerian Naira", "₦", 2),
        CurrencyInfo("KES", "Kenyan Shilling", "KSh", 2),
        CurrencyInfo("VND", "Vietnamese Dong", "₫", 0),
        CurrencyInfo("PKR", "Pakistani Rupee", "Rs", 2),
        CurrencyInfo("BDT", "Bangladeshi Taka", "৳", 2),
        CurrencyInfo("CZK", "Czech Koruna", "Kč", 2),
        CurrencyInfo("HUF", "Hungarian Forint", "Ft", 0),
        CurrencyInfo("RON", "Romanian Leu", "lei", 2),
        CurrencyInfo("BGN", "Bulgarian Lev", "лв", 2),
        CurrencyInfo("HRK", "Croatian Kuna", "kn", 2),
        CurrencyInfo("QAR", "Qatari Riyal", "QR", 2),
        CurrencyInfo("KWD", "Kuwaiti Dinar", "KD", 3),
        CurrencyInfo("BHD", "Bahraini Dinar", "BD", 3),
        CurrencyInfo("OMR", "Omani Rial", "OMR", 3),
        CurrencyInfo("UAH", "Ukrainian Hryvnia", "₴", 2),
        CurrencyInfo("TWD", "New Taiwan Dollar", "NT$", 2)
    )

    val ALL_COUNTRIES: List<CountryInfo> = listOf(
        CountryInfo("US", "United States", "USD"),
        CountryInfo("GB", "United Kingdom", "GBP"),
        CountryInfo("CA", "Canada", "CAD"),
        CountryInfo("AU", "Australia", "AUD"),
        CountryInfo("DE", "Germany", "EUR"),
        CountryInfo("FR", "France", "EUR"),
        CountryInfo("IN", "India", "INR"),
        CountryInfo("JP", "Japan", "JPY"),
        CountryInfo("BR", "Brazil", "BRL"),
        CountryInfo("ZA", "South Africa", "ZAR"),
        CountryInfo("AE", "United Arab Emirates", "AED"),
        CountryInfo("SG", "Singapore", "SGD"),
        CountryInfo("MX", "Mexico", "MXN"),
        CountryInfo("NZ", "New Zealand", "NZD"),
        CountryInfo("CH", "Switzerland", "CHF"),
        CountryInfo("SE", "Sweden", "SEK"),
        CountryInfo("NO", "Norway", "NOK"),
        CountryInfo("DK", "Denmark", "DKK"),
        CountryInfo("NL", "Netherlands", "EUR"),
        CountryInfo("ES", "Spain", "EUR"),
        CountryInfo("IT", "Italy", "EUR"),
        CountryInfo("IE", "Ireland", "EUR"),
        CountryInfo("PL", "Poland", "PLN"),
        CountryInfo("TH", "Thailand", "THB"),
        CountryInfo("ID", "Indonesia", "IDR"),
        CountryInfo("MY", "Malaysia", "MYR"),
        CountryInfo("PH", "Philippines", "PHP"),
        CountryInfo("TR", "Turkey", "TRY"),
        CountryInfo("SA", "Saudi Arabia", "SAR"),
        CountryInfo("IL", "Israel", "ILS"),
        CountryInfo("KR", "South Korea", "KRW"),
        CountryInfo("EG", "Egypt", "EGP"),
        CountryInfo("NG", "Nigeria", "NGN"),
        CountryInfo("KE", "Kenya", "KES"),
        CountryInfo("VN", "Vietnam", "VND"),
        CountryInfo("PK", "Pakistan", "PKR"),
        CountryInfo("BD", "Bangladesh", "BDT"),
        CountryInfo("AR", "Argentina", "ARS"),
        CountryInfo("CO", "Colombia", "COP"),
        CountryInfo("CL", "Chile", "CLP")
    )

    fun format(
        amount: Double,
        currencyCode: String,
        secondaryCurrency: String? = null,
        manualExchangeRate: Double? = null
    ): String {
        val currency = ALL_CURRENCIES.find { it.code.equals(currencyCode, ignoreCase = true) }
            ?: CurrencyInfo(currencyCode.uppercase(), currencyCode.uppercase(), currencyCode.uppercase(), 2)

        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ','
            decimalSeparator = '.'
        }

        val pattern = if (currency.decimalDigits == 0) {
            "#,##0"
        } else {
            val decimals = "0".repeat(currency.decimalDigits)
            "#,##0.$decimals"
        }

        val df = DecimalFormat(pattern, symbols)
        val formatted = "${currency.symbol}${df.format(amount)}"

        if (!secondaryCurrency.isNullOrBlank() && manualExchangeRate != null && manualExchangeRate > 0) {
            val secCurrency = ALL_CURRENCIES.find { it.code.equals(secondaryCurrency, ignoreCase = true) }
                ?: CurrencyInfo(secondaryCurrency.uppercase(), secondaryCurrency.uppercase(), secondaryCurrency.uppercase(), 2)
            val convertedAmount = amount * manualExchangeRate
            val secPattern = if (secCurrency.decimalDigits == 0) "#,##0" else "#,##0.00"
            val secDf = DecimalFormat(secPattern, symbols)
            return "$formatted (≈ ${secCurrency.symbol}${secDf.format(convertedAmount)} ${secCurrency.code})"
        }

        return formatted
    }
}
