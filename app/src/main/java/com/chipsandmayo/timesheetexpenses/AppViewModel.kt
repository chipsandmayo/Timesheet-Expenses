package com.chipsandmayo.timesheetexpenses

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chipsandmayo.timesheetexpenses.data.AuthSignature
import com.chipsandmayo.timesheetexpenses.data.Claim
import com.chipsandmayo.timesheetexpenses.data.DayType
import com.chipsandmayo.timesheetexpenses.data.Expense
import com.chipsandmayo.timesheetexpenses.data.ExpenseCategory
import com.chipsandmayo.timesheetexpenses.data.Fmt
import com.chipsandmayo.timesheetexpenses.data.Naming
import com.chipsandmayo.timesheetexpenses.data.Profile
import com.chipsandmayo.timesheetexpenses.data.ReceiptPhoto
import com.chipsandmayo.timesheetexpenses.data.Store
import com.chipsandmayo.timesheetexpenses.ocr.AmountCandidate
import com.chipsandmayo.timesheetexpenses.ocr.ReceiptOcr
import com.chipsandmayo.timesheetexpenses.ocr.ReceiptParser
import com.chipsandmayo.timesheetexpenses.util.CurrencyService
import com.chipsandmayo.timesheetexpenses.util.DocumentSaver
import com.chipsandmayo.timesheetexpenses.util.Images
import com.chipsandmayo.timesheetexpenses.util.ReleaseInfo
import com.chipsandmayo.timesheetexpenses.util.Updater
import com.chipsandmayo.timesheetexpenses.xlsx.ExpenseExporter
import com.chipsandmayo.timesheetexpenses.xlsx.SignatureImage
import com.chipsandmayo.timesheetexpenses.xlsx.TimesheetExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth

enum class ReceiptStep { NONE, READING, PICK_AMOUNT, ASK_GBP, PICK_CURRENCY, FETCHING_RATE, EDIT }

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val saveQueue = Dispatchers.IO.limitedParallelism(1)
    private val ctx get() = getApplication<Application>()

    var profiles by mutableStateOf(store.loadProfiles()); private set
    var activeProfileId by mutableStateOf(store.activeProfileId); private set
    var claim by mutableStateOf(store.loadClaim()); private set
    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false); private set

    // receipt flow
    var receiptStep by mutableStateOf(ReceiptStep.NONE); private set
    var candidates by mutableStateOf<List<AmountCandidate>>(emptyList()); private set
    var guessedCurrency by mutableStateOf<String?>(null); private set
    var draft by mutableStateOf<Expense?>(null)
    private var pendingCameraFile: File? = null

    // updates
    var release by mutableStateOf<ReleaseInfo?>(null); private set
    var updateProgress by mutableStateOf<Float?>(null); private set

    val activeProfile: Profile? get() = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()
    val perMile: Double get() = activeProfile?.perMileRate?.replace(",", ".")?.toDoubleOrNull() ?: 0.0

    // ---------------- profiles ----------------

    fun saveProfile(p: Profile) {
        profiles = if (profiles.any { it.id == p.id }) profiles.map { if (it.id == p.id) p else it } else profiles + p
        store.saveProfiles(profiles)
        if (activeProfileId == null || profiles.size == 1) selectProfile(p.id)
    }

    fun deleteProfile(p: Profile) {
        profiles = profiles.filterNot { it.id == p.id }
        store.saveProfiles(profiles)
        if (p.signaturePath.isNotBlank()) File(p.signaturePath).delete()
        if (activeProfileId == p.id) selectProfile(profiles.firstOrNull()?.id)
    }

    fun selectProfile(id: String?) { activeProfileId = id; store.activeProfileId = id }

    fun saveSignature(profileId: String, bmp: Bitmap): String {
        val dir = File(ctx.filesDir, "signatures").apply { mkdirs() }
        val f = File(dir, "$profileId-${System.currentTimeMillis()}.png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }

    // ---------------- claim / timesheet ----------------

    private fun updateClaim(c: Claim) {
        claim = c
        viewModelScope.launch(saveQueue) { store.saveClaim(c) }
    }

    fun setMonth(m: YearMonth) = updateClaim(claim.copy(month = m))

    /** First and last day = travel, everything between = offshore. */
    fun applyRange(start: LocalDate, end: LocalDate) {
        val (s, e) = if (end.isBefore(start)) end to start else start to end
        val days = claim.days.toMutableMap()
        var d = s
        while (!d.isAfter(e)) {
            days[d] = if (d == s || d == e) DayType.TRAVEL else DayType.OFFSHORE
            d = d.plusDays(1)
        }
        updateClaim(claim.copy(days = days, month = YearMonth.from(e)))
        val months = generateSequence(YearMonth.from(s)) { it.plusMonths(1) }.takeWhile { !it.isAfter(YearMonth.from(e)) }.toList()
        if (months.size > 1) message = "Hitch spans ${months.joinToString(" & ") { it.format(Fmt.monthYear) }} — save a timesheet for each month."
    }

    fun cycleDay(date: LocalDate) {
        val days = claim.days.toMutableMap()
        val cur = days[date]
        val next = if (cur == null) DayType.OFFSHORE else cur.next()
        if (next == null) days.remove(date) else days[date] = next
        updateClaim(claim.copy(days = days))
    }

    fun saveAuthoriserSignature(bmp: Bitmap) {
        val path = saveSignature("authoriser-${claim.month}", bmp)
        claim.authSignatures[claim.month]?.let { File(it.path).delete() }
        updateClaim(claim.copy(authSignatures = claim.authSignatures + (claim.month to AuthSignature(path, LocalDate.now()))))
    }

    fun removeAuthoriserSignature() {
        claim.authSignatures[claim.month]?.let { File(it.path).delete() }
        updateClaim(claim.copy(authSignatures = claim.authSignatures - claim.month))
    }

    fun clearMonth() = updateClaim(claim.copy(days = claim.days.filterKeys { YearMonth.from(it) != claim.month }))
    fun setRemarks(t: String) = updateClaim(claim.copy(remarks = t))
    fun setLocation(t: String) = updateClaim(claim.copy(locationCode = t))
    fun setPeriod(t: String) = updateClaim(claim.copy(period = t))

    fun autoPeriod(): String {
        val days = claim.daysIn(claim.month).keys.sorted()
        val (a, b) = if (days.isNotEmpty()) days.first() to days.last() else claim.month.atDay(1) to claim.month.atEndOfMonth()
        return "${a.format(Fmt.day)} - ${b.format(Fmt.day)}"
    }

    // ---------------- export ----------------

    private fun requireProfile(): Profile? {
        val p = activeProfile
        if (p == null || p.consultantName.isBlank()) {
            message = "Create a profile with the consultant name first (Profiles tab)."
            return null
        }
        return p
    }

    private fun exportDir(): File = File(ctx.cacheDir, "export").apply { mkdirs() }

    private fun writeTimesheet(p: Profile): File {
        val tpl = ctx.assets.open("timesheet_template.xlsx").use { it.readBytes() }
        val sig = p.signaturePath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }?.let {
            val (w, h) = Images.pngSize(it); SignatureImage(it.readBytes(), w, h)
        }
        val auth = claim.authSignatures[claim.month]?.takeIf { File(it.path).exists() }
        val authSig = auth?.let { val f = File(it.path); val (w, h) = Images.pngSize(f); SignatureImage(f.readBytes(), w, h) }
        val bytes = TimesheetExporter.build(tpl, p, claim, claim.month, LocalDate.now(), sig, authSig, auth?.date)
        val name = Naming.timesheet(p, claim.month)
        DocumentSaver.save(ctx, Naming.folder(claim.month), name, XLSX, bytes)
        return File(exportDir(), name).apply { writeBytes(bytes) }
    }

    private fun writeExpenses(p: Profile): List<File> {
        val tpl = ctx.assets.open("expense_template.xlsx").use { it.readBytes() }
        val period = claim.period.ifBlank { autoPeriod() }
        val chunks = claim.expenses.sortedBy { it.date }.chunked(ExpenseExporter.ROWS_PER_SHEET).ifEmpty { listOf(emptyList()) }
        val files = mutableListOf<File>()
        chunks.forEachIndexed { i, chunk ->
            val bytes = ExpenseExporter.build(tpl, p, period, LocalDate.now(), chunk, perMile)
            val name = Naming.expenses(p, claim.month, i + 1)
            DocumentSaver.save(ctx, Naming.folder(claim.month), name, XLSX, bytes)
            files += File(exportDir(), name).apply { writeBytes(bytes) }
        }
        // (re)save receipt photos with the current naming
        claim.expenses.flatMap { it.photos }.forEach { ph ->
            val src = File(ph.path)
            if (src.exists()) {
                val name = Naming.photo(p, claim.month, ph.number)
                DocumentSaver.save(ctx, Naming.folder(claim.month), name, "image/jpeg", src.readBytes())
                files += File(exportDir(), name).apply { writeBytes(src.readBytes()) }
            }
        }
        return files
    }

    private fun runExport(what: String, block: (Profile) -> List<File>, share: Boolean) {
        val p = requireProfile() ?: return
        busy = true
        viewModelScope.launch {
            try {
                val files = withContext(Dispatchers.IO) {
                    exportDir().listFiles()?.forEach { it.delete() }
                    block(p)
                }
                message = "$what saved to ${DocumentSaver.displayPath(Naming.folder(claim.month))}"
                if (share) share(p, files)
            } catch (e: Exception) {
                message = "Could not save: ${e.message}"
            } finally { busy = false }
        }
    }

    fun saveTimesheet() = runExport("Timesheet", { listOf(writeTimesheet(it)) }, share = false)
    fun saveExpenses() = runExport("Expenses", { writeExpenses(it) }, share = false)
    fun saveAndShareAll() = runExport("Timesheet & expenses", { p ->
        val list = mutableListOf(writeTimesheet(p))
        if (claim.expenses.isNotEmpty()) list += writeExpenses(p)
        list
    }, share = true)

    private fun share(p: Profile, files: List<File>) {
        val uris = ArrayList<Uri>(files.map { FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", it) })
        val subject = "${Naming.base(p, claim.month)} Timesheet & Expenses"
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, "Please find attached my timesheet and expenses for ${claim.month.format(Fmt.monthTitle)}.\n\n${p.consultantName}")
            if (p.emailTo.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, p.emailTo.split(',', ';').map { it.trim() }.toTypedArray())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Send timesheet").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ---------------- receipts ----------------

    fun newCameraTarget(): Uri {
        val dir = File(ctx.filesDir, "receipts").apply { mkdirs() }
        val f = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
        pendingCameraFile = f
        store.pendingCameraPath = f.absolutePath
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    }

    fun onPhotoResult(success: Boolean) {
        val f = pendingCameraFile ?: store.pendingCameraPath?.let { File(it) }
        pendingCameraFile = null
        store.pendingCameraPath = null
        if (!success || f == null || !f.exists() || f.length() == 0L) { f?.delete(); return }
        receiptStep = ReceiptStep.READING
        viewModelScope.launch {
            try {
                val p = activeProfile
                val number = claim.nextPhotoNo
                val photo = ReceiptPhoto(f.absolutePath, number)
                withContext(Dispatchers.IO) {
                    val jpeg = Images.receiptJpeg(f)
                    f.writeBytes(jpeg)
                    runCatching { DocumentSaver.save(ctx, Naming.folder(claim.month), Naming.photo(p, claim.month, number), "image/jpeg", jpeg) }
                        .onFailure { message = "Photo kept in app, but not saved to Documents: ${it.message}" }
                }
                updateClaim(claim.copy(nextPhotoNo = number + 1))
                val ocr = runCatching { ReceiptOcr.read(ctx, f) }.getOrNull()
                candidates = ocr?.let { ReceiptParser.candidates(it.lines) } ?: emptyList()
                guessedCurrency = ocr?.let { ReceiptParser.detectCurrency(it.fullText) }
                val date = ocr?.let { ReceiptParser.detectDate(it.fullText) } ?: LocalDate.now()
                draft = Expense(date = date, photos = listOf(photo), category = ExpenseCategory.MISC)
                receiptStep = ReceiptStep.PICK_AMOUNT
                if (ocr == null) message = "Couldn't read the receipt text — enter the amount manually."
            } catch (e: Exception) {
                message = "Receipt error: ${e.message}"
                receiptStep = ReceiptStep.NONE
            }
        }
    }

    fun pickAmount(value: Double) {
        draft = draft?.copy(amount = value)
        receiptStep = ReceiptStep.ASK_GBP
    }

    fun answerGbp(isGbp: Boolean) {
        if (isGbp) {
            draft = draft?.copy(currency = "GBP", rateToGbp = 1.0, rateSource = "")
            receiptStep = ReceiptStep.EDIT
        } else receiptStep = ReceiptStep.PICK_CURRENCY
    }

    fun pickCurrency(code: String) {
        val d = draft ?: return
        draft = d.copy(currency = code)
        receiptStep = ReceiptStep.FETCHING_RATE
        viewModelScope.launch {
            try {
                val r = CurrencyService.rateToGbp(code, d.date)
                draft = draft?.copy(rateToGbp = r.rate, rateSource = "${r.source}, ${r.rateDate}")
            } catch (e: Exception) {
                message = e.message
                draft = draft?.copy(rateToGbp = 0.0, rateSource = "")
            }
            receiptStep = ReceiptStep.EDIT
        }
    }

    /** Re-fetches the rate inside the editor (e.g. after changing date or currency). */
    fun refreshRate(onDone: (Double?, String) -> Unit, currency: String, date: LocalDate) {
        viewModelScope.launch {
            try {
                val r = CurrencyService.rateToGbp(currency, date)
                onDone(r.rate, "${r.source}, ${r.rateDate}")
            } catch (e: Exception) {
                message = e.message; onDone(null, "")
            }
        }
    }

    fun startManualExpense() {
        draft = Expense()
        receiptStep = ReceiptStep.EDIT
    }

    fun editExpense(e: Expense) { draft = e; receiptStep = ReceiptStep.EDIT }

    fun commitDraft(e: Expense) {
        val list = claim.expenses
        updateClaim(claim.copy(expenses = if (list.any { it.id == e.id }) list.map { if (it.id == e.id) e else it } else list + e))
        draft = null; receiptStep = ReceiptStep.NONE
    }

    fun cancelReceipt() {
        // a brand-new scanned expense that was abandoned keeps its photo file (already saved to Documents)
        draft = null; receiptStep = ReceiptStep.NONE
    }

    fun deleteExpense(e: Expense) {
        updateClaim(claim.copy(expenses = claim.expenses.filterNot { it.id == e.id }))
        e.photos.forEach { File(it.path).delete() }
    }

    fun clearExpenses() {
        claim.expenses.forEach { e -> e.photos.forEach { File(it.path).delete() } }
        updateClaim(claim.copy(expenses = emptyList(), nextPhotoNo = 1, period = ""))
    }

    // ---------------- updates ----------------

    fun checkForUpdate(silent: Boolean = false) {
        viewModelScope.launch {
            try {
                val r = Updater.latest()
                release = r
                if (!silent) message = if (Updater.isNewer(r)) "Version ${r.versionName} is available" else "You're on the latest version"
            } catch (e: Exception) {
                if (!silent) message = "Update check failed: ${e.message}"
            }
        }
    }

    fun downloadAndInstall() {
        val r = release ?: return
        updateProgress = 0f
        viewModelScope.launch {
            try {
                val apk = Updater.download(ctx, r.apkUrl) { p -> viewModelScope.launch { updateProgress = p } }
                updateProgress = null
                if (!Updater.install(ctx, apk)) message = "Allow installs from this app, then tap Update again."
            } catch (e: Exception) {
                updateProgress = null
                message = "Update failed: ${e.message}"
            }
        }
    }

    companion object {
        const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
