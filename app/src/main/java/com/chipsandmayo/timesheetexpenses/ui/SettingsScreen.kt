package com.chipsandmayo.timesheetexpenses.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chipsandmayo.timesheetexpenses.AppViewModel
import com.chipsandmayo.timesheetexpenses.BuildConfig
import com.chipsandmayo.timesheetexpenses.util.DocumentSaver
import com.chipsandmayo.timesheetexpenses.util.Updater

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val rel = vm.release
    val newer = rel != null && Updater.isNewer(rel)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("App updates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Installed version: ${BuildConfig.VERSION_NAME}")
                if (rel != null) Text(if (newer) "New version available: ${rel.versionName}" else "Latest version: ${rel.versionName} — you're up to date")
                vm.updateProgress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.checkForUpdate() }) { Text("Check for updates") }
                    Button(onClick = vm::downloadAndInstall, enabled = newer && vm.updateProgress == null) { Text("Update app") }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Download link", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(Updater.DOWNLOAD_LINK, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Download link", Updater.DOWNLOAD_LINK))
                        vm.message = "Link copied"
                    }) { Text("Copy") }
                    OutlinedButton(onClick = {
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, Updater.DOWNLOAD_LINK), "Share link"))
                    }) { Text("Share") }
                    OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.RELEASES_PAGE))) }) { Text("Open") }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Where files are saved", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${DocumentSaver.displayPath("<Mon-YYYY>")}/")
                Text("e.g. “Steffan Jones Nov-2026 Timesheet.xlsx”, “… Expenses.xlsx”, “… Photo 01.jpg”", style = MaterialTheme.typography.bodySmall)
                Text("Open them with the Files app → Documents.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Exchange rates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Rates are looked up for the receipt date: European Central Bank reference rates (Frankfurter) first, " +
                        "then the open currency-api for currencies the ECB doesn't cover. You can always type a rate yourself.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
