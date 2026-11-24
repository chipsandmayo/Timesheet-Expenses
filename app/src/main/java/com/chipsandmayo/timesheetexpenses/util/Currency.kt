package com.chipsandmayo.timesheetexpenses.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

data class RateResult(val rate: Double, val source: String, val rateDate: String)

object Currencies {
    val all: List<Pair<String, String>> = listOf(
        "EUR" to "Euro", "USD" to "US Dollar", "NOK" to "Norwegian Krone", "DKK" to "Danish Krone",
        "SEK" to "Swedish Krona", "CHF" to "Swiss Franc", "PLN" to "Polish Zloty", "ISK" to "Icelandic Krona",
        "CZK" to "Czech Koruna", "HUF" to "Hungarian Forint", "RON" to "Romanian Leu", "BGN" to "Bulgarian Lev",
        "TRY" to "Turkish Lira", "AUD" to "Australian Dollar", "NZD" to "New Zealand Dollar", "CAD" to "Canadian Dollar",
        "SGD" to "Singapore Dollar", "HKD" to "Hong Kong Dollar", "MYR" to "Malaysian Ringgit", "THB" to "Thai Baht",
        "PHP" to "Philippine Peso", "IDR" to "Indonesian Rupiah", "INR" to "Indian Rupee", "JPY" to "Japanese Yen",
        "CNY" to "Chinese Yuan", "KRW" to "South Korean Won", "AED" to "UAE Dirham", "SAR" to "Saudi Riyal",
        "QAR" to "Qatari Riyal", "KWD" to "Kuwaiti Dinar", "OMR" to "Omani Rial", "BHD" to "Bahraini Dinar",
        "EGP" to "Egyptian Pound", "ZAR" to "South African Rand", "NGN" to "Nigerian Naira", "GHS" to "Ghanaian Cedi",
        "BRL" to "Brazilian Real", "MXN" to "Mexican Peso", "TTD" to "Trinidad & Tobago Dollar", "AZN" to "Azerbaijani Manat",
        "KZT" to "Kazakhstani Tenge", "GBP" to "British Pound",
    )

    fun name(code: String) = all.firstOrNull { it.first == code }?.second ?: code
}

/**
 * Looks up GBP per 1 unit of [from] for the receipt date.
 * 1) Frankfurter (European Central Bank reference rates, by date)
 * 2) fawazahmed0 currency-api (daily rates for ~200 currencies, by date)
 */
object CurrencyService {
    suspend fun rateToGbp(from: String, date: LocalDate): RateResult = withContext(Dispatchers.IO) {
        val code = from.uppercase()
        if (code == "GBP") return@withContext RateResult(1.0, "GBP", date.toString())
        val d = if (date.isAfter(LocalDate.now())) LocalDate.now() else date
        val errors = mutableListOf<String>()

        for (base in listOf("https://api.frankfurter.dev/v1", "https://api.frankfurter.app")) {
            try {
                val o = JSONObject(get("$base/$d?base=$code&symbols=GBP"))
                val r = o.getJSONObject("rates").getDouble("GBP")
                return@withContext RateResult(r, "ECB via Frankfurter", o.optString("date", d.toString()))
            } catch (e: Exception) { errors += "frankfurter: ${e.message}" }
        }
        val lower = code.lowercase()
        val tag = if (d == LocalDate.now()) "latest" else d.toString()
        for (url in listOf(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@$tag/v1/currencies/$lower.json",
            "https://$tag.currency-api.pages.dev/v1/currencies/$lower.json",
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/$lower.json",
        )) {
            try {
                val o = JSONObject(get(url))
                val r = o.getJSONObject(lower).getDouble("gbp")
                return@withContext RateResult(r, "currency-api", o.optString("date", tag))
            } catch (e: Exception) { errors += "currency-api: ${e.message}" }
        }
        throw IllegalStateException("Could not get an exchange rate for $code. Check your connection or type the rate in.")
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", "TimesheetExpenses-Android")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }
}
