package com.chipsandmayo.timesheetexpenses.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val profileName: String = "",
    val consultantName: String = "",
    val position: String = "",
    val client: String = "",
    val vessel: String = "",
    val projectNumber: String = "",
    val projectName: String = "",
    val authoriserName: String = "",
    val address: String = "",
    val locationCode: String = "",
    val expenseAuthorisedBy: String = "",
    val perMileRate: String = "0.45",
    val emailTo: String = "",
    val signaturePath: String = "",
) {
    val displayName: String
        get() = profileName.ifBlank { consultantName.ifBlank { "Unnamed profile" } }
}

enum class DayType(val label: String, val short: String) {
    OFFSHORE("Offshore", "OFF"),
    TRAVEL("Travel", "TRV"),
    ONSHORE("Onshore", "ON"),
    STANDBY("Quarantine / Standby", "STB");

    /** Order used when tapping a day to cycle through types. null = empty */
    fun next(): DayType? = when (this) {
        OFFSHORE -> TRAVEL
        TRAVEL -> ONSHORE
        ONSHORE -> STANDBY
        STANDBY -> null
    }
}

enum class ExpenseCategory(val label: String, val column: String) {
    AIRFARE("Airfare", "D"),
    LODGING("Lodging", "E"),
    GROUND("Ground transport", "F"),
    MEALS("Meals", "G"),
    MILEAGE("Mileage", "H"),
    MISC("Miscellaneous", "J"),
}

data class ReceiptPhoto(val path: String, val number: Int)

data class Expense(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate = LocalDate.now(),
    val description: String = "",
    val category: ExpenseCategory = ExpenseCategory.MISC,
    /** Amount in [currency]; unused for mileage. */
    val amount: Double = 0.0,
    val currency: String = "GBP",
    /** GBP per 1 unit of [currency]. */
    val rateToGbp: Double = 1.0,
    val rateSource: String = "",
    val miles: Double = 0.0,
    val photos: List<ReceiptPhoto> = emptyList(),
) {
    fun mileageGbp(perMile: Double) = miles * perMile
    fun totalGbp(perMile: Double): Double =
        if (category == ExpenseCategory.MILEAGE) mileageGbp(perMile) else amount * rateToGbp
}

/** Authoriser's signature captured on the phone for one month's timesheet. */
data class AuthSignature(val path: String, val date: LocalDate)

data class Claim(
    val month: YearMonth = YearMonth.now(),
    val days: Map<LocalDate, DayType> = emptyMap(),
    val remarks: String = "",
    val locationCode: String = "",
    val period: String = "",
    val expenses: List<Expense> = emptyList(),
    val nextPhotoNo: Int = 1,
    val authSignatures: Map<YearMonth, AuthSignature> = emptyMap(),
) {
    fun daysIn(month: YearMonth) = days.filterKeys { YearMonth.from(it) == month }
}

object Fmt {
    val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM-yyyy", Locale.UK)
    val day: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.UK)
    val monthTitle: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK)

    fun money(v: Double) = String.format(Locale.UK, "%,.2f", v)
}

/** Builds the save names: "<Consultant> <Mon-YYYY> <Kind>" */
object Naming {
    fun clean(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "").trim()
    fun base(profile: Profile?, month: YearMonth): String {
        val name = clean(profile?.consultantName.orEmpty()).ifBlank { "Consultant" }
        return "$name ${month.format(Fmt.monthYear)}"
    }
    fun timesheet(p: Profile?, m: YearMonth) = "${base(p, m)} Timesheet.xlsx"
    fun expenses(p: Profile?, m: YearMonth, part: Int) =
        "${base(p, m)} Expenses${if (part > 1) " $part" else ""}.xlsx"
    fun photo(p: Profile?, m: YearMonth, no: Int) =
        "${base(p, m)} Photo ${no.toString().padStart(2, '0')}.jpg"
    fun folder(m: YearMonth) = m.format(Fmt.monthYear)
}
