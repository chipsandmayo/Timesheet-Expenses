package com.chipsandmayo.timesheetexpenses.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** Simple JSON persistence for profiles and the claim in progress. */
class Store(private val context: Context) {
    private val profilesFile get() = File(context.filesDir, "profiles.json")
    private val claimFile get() = File(context.filesDir, "claim.json")
    private val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)

    var activeProfileId: String?
        get() = prefs.getString("active_profile", null)
        set(v) = prefs.edit().putString("active_profile", v).apply()

    var pendingCameraPath: String?
        get() = prefs.getString("pending_camera", null)
        set(v) = prefs.edit().putString("pending_camera", v).apply()

    fun loadProfiles(): List<Profile> = runCatching {
        val arr = JSONArray(profilesFile.readText())
        (0 until arr.length()).map { profileFrom(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun saveProfiles(list: List<Profile>) {
        val arr = JSONArray()
        list.forEach { arr.put(profileTo(it)) }
        atomicWrite(profilesFile, arr.toString(2))
    }

    fun loadClaim(): Claim = runCatching { claimFrom(JSONObject(claimFile.readText())) }.getOrDefault(Claim())

    fun saveClaim(c: Claim) = atomicWrite(claimFile, claimTo(c).toString())

    private fun atomicWrite(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(f)
    }

    private fun profileTo(p: Profile) = JSONObject().apply {
        put("id", p.id); put("profileName", p.profileName); put("consultantName", p.consultantName)
        put("position", p.position); put("client", p.client); put("vessel", p.vessel)
        put("projectNumber", p.projectNumber); put("projectName", p.projectName)
        put("authoriserName", p.authoriserName); put("address", p.address)
        put("locationCode", p.locationCode); put("expenseAuthorisedBy", p.expenseAuthorisedBy)
        put("perMileRate", p.perMileRate); put("emailTo", p.emailTo); put("signaturePath", p.signaturePath)
    }

    private fun profileFrom(o: JSONObject) = Profile(
        id = o.optString("id"), profileName = o.optString("profileName"),
        consultantName = o.optString("consultantName"), position = o.optString("position"),
        client = o.optString("client"), vessel = o.optString("vessel"),
        projectNumber = o.optString("projectNumber"), projectName = o.optString("projectName"),
        authoriserName = o.optString("authoriserName"), address = o.optString("address"),
        locationCode = o.optString("locationCode"), expenseAuthorisedBy = o.optString("expenseAuthorisedBy"),
        perMileRate = o.optString("perMileRate", "0.45"), emailTo = o.optString("emailTo"),
        signaturePath = o.optString("signaturePath"),
    )

    private fun claimTo(c: Claim) = JSONObject().apply {
        put("month", c.month.toString())
        put("days", JSONObject().apply { c.days.forEach { (d, t) -> put(d.toString(), t.name) } })
        put("remarks", c.remarks); put("locationCode", c.locationCode); put("period", c.period)
        put("nextPhotoNo", c.nextPhotoNo)
        put("authSignatures", JSONObject().apply {
            c.authSignatures.forEach { (m, a) -> put(m.toString(), JSONObject().put("path", a.path).put("date", a.date.toString())) }
        })
        put("expenses", JSONArray().apply {
            c.expenses.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id); put("date", e.date.toString()); put("description", e.description)
                    put("category", e.category.name); put("amount", e.amount); put("currency", e.currency)
                    put("rateToGbp", e.rateToGbp); put("rateSource", e.rateSource); put("miles", e.miles)
                    put("photos", JSONArray().apply {
                        e.photos.forEach { p -> put(JSONObject().put("path", p.path).put("number", p.number)) }
                    })
                })
            }
        })
    }

    private fun claimFrom(o: JSONObject): Claim {
        val days = mutableMapOf<LocalDate, DayType>()
        o.optJSONObject("days")?.let { d ->
            d.keys().forEach { k -> runCatching { days[LocalDate.parse(k)] = DayType.valueOf(d.getString(k)) } }
        }
        val exps = mutableListOf<Expense>()
        o.optJSONArray("expenses")?.let { arr ->
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val photos = mutableListOf<ReceiptPhoto>()
                e.optJSONArray("photos")?.let { pa ->
                    for (j in 0 until pa.length()) {
                        val p = pa.getJSONObject(j); photos += ReceiptPhoto(p.getString("path"), p.getInt("number"))
                    }
                }
                exps += Expense(
                    id = e.getString("id"), date = LocalDate.parse(e.getString("date")),
                    description = e.optString("description"),
                    category = runCatching { ExpenseCategory.valueOf(e.getString("category")) }.getOrDefault(ExpenseCategory.MISC),
                    amount = e.optDouble("amount", 0.0), currency = e.optString("currency", "GBP"),
                    rateToGbp = e.optDouble("rateToGbp", 1.0), rateSource = e.optString("rateSource"),
                    miles = e.optDouble("miles", 0.0), photos = photos,
                )
            }
        }
        val auth = mutableMapOf<YearMonth, AuthSignature>()
        o.optJSONObject("authSignatures")?.let { a ->
            a.keys().forEach { k ->
                runCatching {
                    val v = a.getJSONObject(k)
                    auth[YearMonth.parse(k)] = AuthSignature(v.getString("path"), LocalDate.parse(v.getString("date")))
                }
            }
        }
        return Claim(
            month = runCatching { YearMonth.parse(o.getString("month")) }.getOrDefault(YearMonth.now()),
            days = days, remarks = o.optString("remarks"), locationCode = o.optString("locationCode"),
            period = o.optString("period"), expenses = exps, nextPhotoNo = o.optInt("nextPhotoNo", 1),
            authSignatures = auth,
        )
    }
}
