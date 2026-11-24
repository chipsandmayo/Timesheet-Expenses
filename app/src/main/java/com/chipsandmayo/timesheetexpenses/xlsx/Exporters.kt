package com.chipsandmayo.timesheetexpenses.xlsx

import com.chipsandmayo.timesheetexpenses.data.Claim
import com.chipsandmayo.timesheetexpenses.data.DayType
import com.chipsandmayo.timesheetexpenses.data.Expense
import com.chipsandmayo.timesheetexpenses.data.ExpenseCategory
import com.chipsandmayo.timesheetexpenses.data.Fmt
import com.chipsandmayo.timesheetexpenses.data.Profile
import java.time.LocalDate
import java.time.YearMonth

class SignatureImage(val png: ByteArray, val width: Int, val height: Int)

object TimesheetExporter {
    private const val SHEET = "xl/worksheets/sheet1.xml"
    private const val DRAWING = "xl/drawings/drawing1.xml"

    private fun rowFor(t: DayType) = when (t) {
        DayType.ONSHORE -> 17
        DayType.OFFSHORE -> 18
        DayType.TRAVEL -> 19
        DayType.STANDBY -> 20
    }

    fun build(
        template: ByteArray,
        profile: Profile,
        claim: Claim,
        month: YearMonth,
        today: LocalDate,
        signature: SignatureImage?,
        authoriserSignature: SignatureImage? = null,
        authorisedDate: LocalDate? = null,
    ): ByteArray {
        val x = XlsxEditor(template)
        x.setString(SHEET, "E8", profile.client)
        x.setString(SHEET, "E9", profile.consultantName)
        x.setString(SHEET, "E10", profile.position)
        x.setString(SHEET, "E11", profile.vessel)
        x.setString(SHEET, "E12", profile.projectNumber)
        x.setString(SHEET, "E13", profile.projectName)
        x.setString(SHEET, "E14", month.format(Fmt.monthYear))
        x.setString(SHEET, "Z14", profile.authoriserName)
        x.setString(SHEET, "AB1", profile.address.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", "))
        x.setString(SHEET, "Z10", today.format(Fmt.day))
        x.setString(SHEET, "A26", claim.remarks)

        val location = claim.locationCode.ifBlank { profile.locationCode }.trim().uppercase()
        val counts = mutableMapOf<DayType, Int>()
        for (day in 1..31) {
            val col = XlsxEditor.colName(2 + day) // day 1 -> C
            if (day > month.lengthOfMonth()) continue
            val type = claim.days[month.atDay(day)] ?: continue
            x.setNumber(SHEET, "$col${rowFor(type)}", 1.0)
            counts[type] = (counts[type] ?: 0) + 1
            if (location.isNotEmpty()) x.setString(SHEET, "${col}21", location)
        }
        var total = 0.0
        for (t in DayType.entries) {
            val r = rowFor(t)
            val c = (counts[t] ?: 0).toDouble()
            total += c
            x.setFormula(SHEET, "AH$r", "SUM(C$r:AG$r)", c)
        }
        x.setFormula(SHEET, "AH21", "SUM(C21:AG21)", 0.0)
        x.setFormula(SHEET, "AH22", "SUM(AH17:AH21)", total)

        // Signature boxes Z8:AH9 (consultant) and Z11:AH12 (authoriser), roughly 2.0" x 0.47"
        fun place(sig: SignatureImage?, row: Int, name: String) {
            if (sig == null || sig.width <= 0 || sig.height <= 0) return
            val maxW = 1_800_000.0; val maxH = 400_000.0
            val scale = minOf(maxW / sig.width, maxH / sig.height)
            val cx = (sig.width * scale).toLong(); val cy = (sig.height * scale).toLong()
            x.addPicture(DRAWING, sig.png, col = 25, row = row, colOff = 38_100, rowOff = 19_050, cx = cx, cy = cy, name = name)
        }
        place(signature, 7, "Signature")
        place(authoriserSignature, 10, "AuthSignature")
        if (authoriserSignature != null) x.setString(SHEET, "Z13", (authorisedDate ?: today).format(Fmt.day))
        x.forceRecalc()
        return x.toByteArray()
    }
}

object ExpenseExporter {
    private const val SHEET = "xl/worksheets/sheet1.xml"
    const val ROWS_PER_SHEET = 10

    fun build(
        template: ByteArray,
        profile: Profile,
        period: String,
        today: LocalDate,
        entries: List<Expense>,
        perMile: Double,
    ): ByteArray {
        require(entries.size <= ROWS_PER_SHEET)
        val x = XlsxEditor(template)
        x.setString(SHEET, "C3", profile.consultantName)
        x.setString(SHEET, "C5", profile.position)
        x.setString(SHEET, "C7", period)
        x.setString(SHEET, "G3", profile.expenseAuthorisedBy.ifBlank { profile.authoriserName })
        x.setString(SHEET, "G5", today.format(Fmt.day))
        x.setNumber(SHEET, "K3", perMile, style = 43)

        // Exchange-rate cells: same look as the template, but 4 decimal places
        val rateStyleEven = x.styleWithFormat(30, "0.0000")
        val rateStyleOdd = x.styleWithFormat(32, "0.0000")
        var grand = 0.0; var miles = 0.0; var mileGbp = 0.0
        entries.forEachIndexed { i, e ->
            val r = 10 + i
            val rateStyle = if (r % 2 == 0) rateStyleEven else rateStyleOdd
            x.setString(SHEET, "B$r", e.date.format(Fmt.day))
            x.setString(SHEET, "C$r", e.description.ifBlank { e.category.label })
            val total: Double
            if (e.category == ExpenseCategory.MILEAGE) {
                val m = e.mileageGbp(perMile)
                x.setNumber(SHEET, "H$r", e.miles)
                x.setFormula(SHEET, "I$r", "H$r*\$K\$3", m)
                x.setNumber(SHEET, "K$r", 1.0, rateStyle)
                x.setString(SHEET, "L$r", "GBP")
                miles += e.miles; mileGbp += m
                total = m
            } else {
                x.setNumber(SHEET, "${e.category.column}$r", round2(e.amount))
                x.setNumber(SHEET, "K$r", e.rateToGbp, rateStyle)
                x.setString(SHEET, "L$r", e.currency)
                total = round2(e.amount) * e.rateToGbp
            }
            x.setFormula(SHEET, "M$r", "SUM(D$r:G$r,J$r)*K$r+I$r", round2(total))
            grand += round2(total)
        }
        x.setFormula(SHEET, "H20", "SUM(H10:H19)", miles)
        x.setFormula(SHEET, "I20", "SUM(I10:I19)", mileGbp)
        x.setFormula(SHEET, "M20", "SUM(M10:M19)", round2(grand))
        x.setFormula(SHEET, "K5", "SUM(M10:M19)", round2(grand))
        x.forceRecalc()
        return x.toByteArray()
    }

    private fun round2(v: Double) = Math.round(v * 100.0) / 100.0
}
