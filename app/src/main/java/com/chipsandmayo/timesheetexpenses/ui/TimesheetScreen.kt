package com.chipsandmayo.timesheetexpenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chipsandmayo.timesheetexpenses.AppViewModel
import com.chipsandmayo.timesheetexpenses.data.DayType
import com.chipsandmayo.timesheetexpenses.data.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

fun DayType.color(): Color = when (this) {
    DayType.OFFSHORE -> OffshoreColor
    DayType.TRAVEL -> TravelColor
    DayType.ONSHORE -> OnshoreColor
    DayType.STANDBY -> StandbyColor
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimesheetScreen(vm: AppViewModel) {
    val claim = vm.claim
    val month = claim.month
    var showRange by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val monthDays = claim.daysIn(month)

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = { showRange = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.DateRange, null); Spacer(Modifier.width(8.dp)); Text("Select start & end date")
        }
        Text(
            "First and last days are entered as travel days, the days in between as offshore days. " +
                "Tap any day to change it (offshore → travel → onshore → standby → empty).",
            style = MaterialTheme.typography.bodySmall,
        )

        Card {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.setMonth(month.minusMonths(1)) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month")
                    }
                    Text(
                        month.format(Fmt.monthTitle), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = { vm.setMonth(month.plusMonths(1)) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach {
                        Text(it, Modifier.weight(1f), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                val offset = month.atDay(1).dayOfWeek.value - 1
                val cells = offset + month.lengthOfMonth()
                val rows = (cells + 6) / 7
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val idx = r * 7 + c - offset + 1
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                                if (idx in 1..month.lengthOfMonth()) {
                                    val date = month.atDay(idx)
                                    val type = monthDays[date]
                                    DayCell(idx, type, date == LocalDate.now()) { vm.cycleDay(date) }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DayType.entries.forEach { t ->
                        val n = monthDays.values.count { it == t }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(t.color()))
                            Spacer(Modifier.width(4.dp))
                            Text("${t.label}: $n", fontSize = 12.sp)
                        }
                    }
                }
                Text("Total days: ${monthDays.size}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            }
        }

        OutlinedTextField(
            value = claim.locationCode,
            onValueChange = { vm.setLocation(it.uppercase().take(4)) },
            label = { Text("Location code (UK, DE, NL, DK…)") },
            placeholder = { Text(vm.activeProfile?.locationCode?.ifBlank { null } ?: "Profile default") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = claim.remarks, onValueChange = vm::setRemarks,
            label = { Text("Remarks") }, minLines = 2, modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.weight(1f)) { Text("Clear month") }
            Button(onClick = vm::saveTimesheet, modifier = Modifier.weight(1f), enabled = !vm.busy) {
                Icon(Icons.Filled.Save, null); Spacer(Modifier.width(6.dp)); Text("Save timesheet")
            }
        }
        Button(onClick = vm::saveAndShareAll, modifier = Modifier.fillMaxWidth(), enabled = !vm.busy) {
            Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.width(6.dp)); Text("Save & email timesheet + expenses")
        }
        Text(
            "Saved as “${com.chipsandmayo.timesheetexpenses.data.Naming.timesheet(vm.activeProfile, month)}” in " +
                com.chipsandmayo.timesheetexpenses.util.DocumentSaver.displayPath(com.chipsandmayo.timesheetexpenses.data.Naming.folder(month)),
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (showRange) RangeDialog(onDismiss = { showRange = false }) { s, e -> vm.applyRange(s, e); showRange = false }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear ${month.format(Fmt.monthTitle)}?") },
        text = { Text("All days entered for this month will be removed.") },
        confirmButton = { TextButton(onClick = { vm.clearMonth(); confirmClear = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}

@Composable
private fun DayCell(day: Int, type: DayType?, today: Boolean, onClick: () -> Unit) {
    val bg = type?.color() ?: Color(0xFFF0F2F6)
    val fg = if (type == null) Color(0xFF333333) else Color.White
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).background(bg)
            .then(if (today) Modifier.border(2.dp, Gold, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick).padding(2.dp)
            .let { it },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("$day", color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        if (type != null) Text(type.short, color = fg, fontSize = 9.sp)
    }
}

private fun millisToDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
fun dateToMillis(d: LocalDate): Long = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeDialog(onDismiss: () -> Unit, onConfirm: (LocalDate, LocalDate) -> Unit) {
    val state = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val s = millisToDate(state.selectedStartDateMillis!!)
                    val e = state.selectedEndDateMillis?.let(::millisToDate) ?: s
                    onConfirm(s, e)
                },
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f),
            title = { Text("Hitch start & end", Modifier.padding(start = 24.dp, top = 16.dp)) },
        )
    }
}
