package com.chipsandmayo.timesheetexpenses.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.chipsandmayo.timesheetexpenses.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String)

object Updater {
    const val REPO = "chipsandmayo/Timesheet-Expenses"
    const val DOWNLOAD_LINK = "https://github.com/$REPO/releases/latest/download/TimesheetExpenses.apk"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"

    suspend fun latest(): ReleaseInfo = withContext(Dispatchers.IO) {
        val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "TimesheetExpenses-Android")
        c.connectTimeout = 15_000; c.readTimeout = 15_000
        try {
            if (c.responseCode != 200) error("GitHub returned HTTP ${c.responseCode}")
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = o.getString("tag_name") // v1.0.<code>
            val code = tag.substringAfterLast('.').toIntOrNull() ?: 0
            val assets = o.getJSONArray("assets")
            var url = DOWNLOAD_LINK
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) { url = a.getString("browser_download_url"); break }
            }
            ReleaseInfo(code, tag.removePrefix("v"), url, o.optString("body"))
        } finally { c.disconnect() }
    }

    fun isNewer(info: ReleaseInfo) = info.versionCode > BuildConfig.VERSION_CODE

    suspend fun download(context: Context, url: String, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dest = File(context.cacheDir, "update.apk")
        var link = url
        var conn: HttpURLConnection
        var hops = 0
        while (true) {
            conn = URL(link).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "TimesheetExpenses-Android")
            conn.connectTimeout = 20_000; conn.readTimeout = 30_000
            val code = conn.responseCode
            if (code in 300..399 && hops < 6) {
                link = conn.getHeaderField("Location"); conn.disconnect(); hops++; continue
            }
            if (code != 200) error("Download failed: HTTP $code")
            break
        }
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            dest.outputStream().use { out ->
                val buf = ByteArray(64 * 1024); var read: Int; var sum = 0L
                while (input.read(buf).also { read = it } >= 0) {
                    out.write(buf, 0, read); sum += read
                    if (total > 0) progress(sum.toFloat() / total)
                }
            }
        }
        conn.disconnect()
        dest
    }

    /** Returns false if the user first has to allow installs from this app. */
    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
