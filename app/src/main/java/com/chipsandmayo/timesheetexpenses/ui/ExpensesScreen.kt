package com.chipsandmayo.timesheetexpenses.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.chipsandmayo.timesheetexpenses.AppViewModel
import com.chipsandmayo.timesheetexpenses.data.ExpenseCategory
import com.chipsandmayo.timesheetexpenses.data.Fmt
import com.chipsandmayo.timesheetexpenses.data.Naming
import com.chipsandmayo.timesheetexpenses.util.DocumentSaver
import java.io.File

@Composable
fun ExpensesScreen(vm: AppViewModel) {
    val claim = vm.claim
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> vm.onPhotoResult(ok) }
    var confirmClear by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<com.chipsandmayo.timesheetexpenses.data.Expense?>(null) }
    val perMile = vm.perMile
    val total = claim.expenses.sumOf { it.totalGbp(perMile) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.setMonth(claim.month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous") }
            Text("Claim month: ${claim.month.format(Fmt.monthTitle)}", Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { vm.setMonth(claim.month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next") }
        }

        Button(
            onClick = { camera.launch(vm.newCameraTarget()) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy),
        ) {
            Icon(Icons.Filled.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text("Scan a receipt", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = vm::startManualExpense, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Add expense without receipt / mileage")
        }

        OutlinedTextField(
            value = claim.period, onValueChange = vm::setPeriod, label = { Text("Period") },
            placeholder = { Text(vm.autoPeriod()) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("Leave blank to use the hitch dates from the timesheet") },
        )

        if (claim.expenses.isEmpty()) {
            Text("No expenses yet for this claim.", style = MaterialTheme.typography.bodyMedium)
        }
        claim.expenses.sortedBy { it.date }.forEach { e ->
            Card(Modifier.fillMaxWidth().clickable { vm.editExpense(e) }) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val photo = e.photos.firstOrNull()
                    if (photo != null) {
                        AsyncImage(
                            model = File(photo.path), contentDescription = "Receipt",
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(e.description.ifBlank { e.category.label }, fontWeight = FontWeight.Bold)
                        Text("${e.date.format(Fmt.day)} · ${e.category.label}", style = MaterialTheme.typography.bodySmall)
                        val detail = if (e.category == ExpenseCategory.MILEAGE) "${Fmt.money(e.miles)} miles"
                        else if (e.currency == "GBP") "£${Fmt.money(e.amount)}"
                        else "${Fmt.money(e.amount)} ${e.currency} @ ${"%.4f".format(e.rateToGbp)}"
                        Text(detail, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("£${Fmt.money(e.totalGbp(perMile))}", fontWeight = FontWeight.Bold)
                    IconButton(onClick = { toDelete = e }) { Icon(Icons.Filled.Delete, "Delete") }
                }
            }
        }
        if (claim.expenses.isNotEmpty()) {
            Text("Total reimbursement: £${Fmt.money(total)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (claim.expenses.size > 10) Text(
                "The form has 10 lines, so this will be saved as ${(claim.expenses.size + 9) / 10} expense sheets.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.weight(1f), enabled = claim.expenses.isNotEmpty()) {
                Text("Clear all")
            }
            Button(onClick = vm::saveExpenses, modifier = Modifier.weight(1f), enabled = !vm.busy && claim.expenses.isNotEmpty()) {
                Icon(Icons.Filled.Save, null); Spacer(Modifier.width(6.dp)); Text("Save expenses")
            }
        }
        Button(onClick = vm::saveAndShareAll, modifier = Modifier.fillMaxWidth(), enabled = !vm.busy) {
            Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.width(6.dp)); Text("Save & email timesheet + expenses")
        }
        Text(
            "Saved as “${Naming.expenses(vm.activeProfile, claim.month, 1)}” and “${Naming.photo(vm.activeProfile, claim.month, 1)}”… in " +
                DocumentSaver.displayPath(Naming.folder(claim.month)),
            style = MaterialTheme.typography.bodySmall,
        )
    }

    ReceiptFlow(vm)

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear all expenses?") },
        text = { Text("Removes every expense from this claim, ready for the next month. Files already saved in Documents are kept.") },
        confirmButton = { TextButton(onClick = { vm.clearExpenses(); confirmClear = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete this expense?") },
            text = { Text(e.description.ifBlank { e.category.label }) },
            confirmButton = { TextButton(onClick = { vm.deleteExpense(e); toDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}
