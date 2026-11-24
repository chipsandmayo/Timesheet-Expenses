package com.chipsandmayo.timesheetexpenses.ocr

import java.time.LocalDate

/** One line of recognised text with its vertical position on the photo. */
data class OcrLine(val text: String, val top: Int, val bottom: Int)

data class AmountCandidate(val value: Double, val raw: String, val context: String, val likelyTotal: Boolean)

object ReceiptParser {
    private val totalWords = Regex(
        "(?i)\\b(grand\\s*total|total|amount\\s*due|balance\\s*due|balance|to\\s*pay|amount|sum|summe|gesamt|totaal|totalt|" +
            "betalt|bel[øo]b|total\\s*ttc|montant|importe|totale|tot\\.?|net\\s*total|card|visa|mastercard|paid)\\b"
    )
    private val subtotalWords = Regex("(?i)\\b(sub\\s*-?\\s*total|vat|tax|mva|moms|tva|iva|change|tip|service)\\b")

    // 1,234.56 | 1.234,56 | 1234.56 | 12,34 | 1 234,56
    private val amountRe = Regex("(?<![\\d.,])(\\d{1,3}(?:[ .,]\\d{3})+[.,]\\d{2}|\\d+[.,]\\d{2})(?![\\d])")

    fun parseAmount(raw: String): Double? {
        val s = raw.replace(" ", "")
        val decSep = s[s.length - 3]
        val intPart = s.substring(0, s.length - 3).replace(".", "").replace(",", "")
        if (decSep != '.' && decSep != ',') return null
        return "$intPart.${s.takeLast(2)}".toDoubleOrNull()
    }

    fun candidates(lines: List<OcrLine>): List<AmountCandidate> {
        val found = mutableListOf<Triple<Double, String, OcrLine>>()
        for (l in lines) {
            if (looksLikeDateOrPhone(l.text)) continue
            amountRe.findAll(l.text).forEach { m ->
                parseAmount(m.value)?.let { v -> if (v > 0 && v < 1_000_000) found += Triple(v, m.value, l) }
            }
        }
        val keywordLines = lines.filter { totalWords.containsMatchIn(it.text) && !subtotalWords.containsMatchIn(it.text) }
        fun isTotal(l: OcrLine): Boolean = keywordLines.any { k ->
            k === l || run {
                val h = (k.bottom - k.top).coerceAtLeast(10)
                val kc = (k.top + k.bottom) / 2; val lc = (l.top + l.bottom) / 2
                kotlin.math.abs(kc - lc) <= h * 0.7
            }
        }
        val list = found.map { (v, raw, l) ->
            AmountCandidate(v, raw.trim(), l.text.trim().take(48), isTotal(l))
        }
        // De-duplicate by value, keeping the "likely total" flag if any copy had it
        val byValue = linkedMapOf<Double, AmountCandidate>()
        for (c in list) {
            val prev = byValue[c.value]
            if (prev == null || (!prev.likelyTotal && c.likelyTotal)) byValue[c.value] = c
        }
        return byValue.values.sortedWith(compareByDescending<AmountCandidate> { it.likelyTotal }.thenByDescending { it.value })
    }

    private fun looksLikeDateOrPhone(t: String) =
        Regex("\\b\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}\\b").containsMatchIn(t) && !Regex("(?i)total|amount").containsMatchIn(t) &&
            Regex("\\d+[.,]\\d{2}(?![\\d./-])").findAll(t).count() == 0

    private val symbolCurrencies = listOf(
        "£" to "GBP", "€" to "EUR", "$" to "USD", "¥" to "JPY", "₹" to "INR", "₦" to "NGN", "₩" to "KRW", "R$" to "BRL",
    )
    private val codeRe = Regex("\\b(GBP|EUR|USD|NOK|DKK|SEK|CHF|PLN|AUD|NZD|CAD|SGD|AED|SAR|QAR|KWD|OMR|BHD|MYR|THB|PHP|IDR|INR|JPY|CNY|HKD|ZAR|NGN|GHS|EGP|TRY|BRL|MXN|ISK|CZK|HUF|RON|BGN)\\b")
    private val krRe = Regex("(?i)\\b(kr|nok|dkk|sek)\\b|\\bkr\\.")

    /** Best guess of the receipt's currency, or null. */
    fun detectCurrency(fullText: String): String? {
        codeRe.find(fullText.uppercase())?.let { return it.groupValues[1] }
        for ((sym, code) in symbolCurrencies) if (fullText.contains(sym)) return code
        if (krRe.containsMatchIn(fullText)) return "NOK"
        return null
    }

    private val dateRes = listOf(
        Regex("\\b(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})\\b") to "dmy",
        Regex("\\b(\\d{4})[./-](\\d{1,2})[./-](\\d{1,2})\\b") to "ymd",
        Regex("\\b(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2})\\b") to "dmy2",
    )

    /** Finds the first plausible date (day-first, as on UK/EU receipts). */
    fun detectDate(fullText: String, today: LocalDate = LocalDate.now()): LocalDate? {
        for ((re, kind) in dateRes) {
            for (m in re.findAll(fullText)) {
                val g = m.groupValues
                val d = runCatching {
                    when (kind) {
                        "ymd" -> LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt())
                        "dmy" -> LocalDate.of(g[3].toInt(), g[2].toInt(), g[1].toInt())
                        else -> LocalDate.of(2000 + g[3].toInt(), g[2].toInt(), g[1].toInt())
                    }
                }.getOrNull() ?: continue
                if (!d.isAfter(today) && d.isAfter(today.minusYears(2))) return d
            }
        }
        return null
    }
}
