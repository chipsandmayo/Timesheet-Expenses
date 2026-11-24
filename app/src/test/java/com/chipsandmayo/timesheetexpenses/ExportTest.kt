package com.chipsandmayo.timesheetexpenses

import com.chipsandmayo.timesheetexpenses.data.Claim
import com.chipsandmayo.timesheetexpenses.data.DayType
import com.chipsandmayo.timesheetexpenses.data.Expense
import com.chipsandmayo.timesheetexpenses.data.ExpenseCategory
import com.chipsandmayo.timesheetexpenses.data.Naming
import com.chipsandmayo.timesheetexpenses.data.Profile
import com.chipsandmayo.timesheetexpenses.ocr.OcrLine
import com.chipsandmayo.timesheetexpenses.ocr.ReceiptParser
import com.chipsandmayo.timesheetexpenses.xlsx.ExpenseExporter
import com.chipsandmayo.timesheetexpenses.xlsx.TimesheetExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.zip.ZipInputStream

class ExportTest {
    private fun sheetXml(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (e.name == "xl/worksheets/sheet1.xml") return String(z.readBytes())
                e = z.nextEntry
            }
        }
        error("no sheet")
    }

    private val profile = Profile(consultantName = "Test Person", client = "Client", locationCode = "UK")

    @Test fun timesheetMarksTravelAndOffshore() {
        val m = YearMonth.of(2026, 11)
        val days = mapOf(
            LocalDate.of(2026, 11, 3) to DayType.TRAVEL,
            LocalDate.of(2026, 11, 4) to DayType.OFFSHORE,
            LocalDate.of(2026, 11, 5) to DayType.TRAVEL,
        )
        val bytes = TimesheetExporter.build(File("src/main/assets/timesheet_template.xlsx").readBytes(), profile,
            Claim(month = m, days = days), m, LocalDate.of(2026, 11, 24), null)
        val xml = sheetXml(bytes)
        assertTrue(xml.contains("<c r=\"E19\" s=\"12\"><v>1</v></c>"))
        assertTrue(xml.contains("<c r=\"F18\" s=\"12\"><v>1</v></c>"))
        assertTrue(xml.contains("<c r=\"G19\" s=\"12\"><v>1</v></c>"))
        assertTrue(xml.contains("<f>SUM(AH17:AH21)</f><v>3</v>"))
        assertTrue(xml.contains("Nov-2026"))
    }

    @Test fun authoriserSignatureAndDate() {
        val m = YearMonth.of(2026, 11)
        // 1x1 transparent PNG
        val png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==")
        val sig = com.chipsandmayo.timesheetexpenses.xlsx.SignatureImage(png, 300, 100)
        val bytes = TimesheetExporter.build(File("src/main/assets/timesheet_template.xlsx").readBytes(), profile,
            Claim(month = m), m, LocalDate.of(2026, 11, 24), sig, sig, LocalDate.of(2026, 11, 20))
        assertTrue(sheetXml(bytes).contains("20/11/2026"))
    }

    @Test fun expensesTotal() {
        val e = listOf(
            Expense(category = ExpenseCategory.MEALS, amount = 10.0, currency = "EUR", rateToGbp = 0.85),
            Expense(category = ExpenseCategory.MILEAGE, miles = 100.0),
        )
        val bytes = ExpenseExporter.build(File("src/main/assets/expense_template.xlsx").readBytes(), profile, "p",
            LocalDate.of(2026, 11, 24), e, 0.45)
        val xml = sheetXml(bytes)
        assertTrue(xml.contains("<f>SUM(M10:M19)</f><v>53.5</v>"))
    }

    @Test fun naming() {
        assertEquals("Test Person Nov-2026 Timesheet.xlsx", Naming.timesheet(profile, YearMonth.of(2026, 11)))
        assertEquals("Test Person Nov-2026 Photo 02.jpg", Naming.photo(profile, YearMonth.of(2026, 11), 2))
    }

    @Test fun parserFindsTotal() {
        val c = ReceiptParser.candidates(listOf(OcrLine("Subtotal 10.00", 0, 10), OcrLine("TOTAL", 20, 30), OcrLine("12.00", 20, 30)))
        assertEquals(12.0, c.first().value, 0.001)
        assertTrue(c.first().likelyTotal)
    }
}
