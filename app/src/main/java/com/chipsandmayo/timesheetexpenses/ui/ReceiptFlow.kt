package com.chipsandmayo.timesheetexpenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.chipsandmayo.timesheetexpenses.AppViewModel
import com.chipsandmayo.timesheetexpenses.ReceiptStep
import com.chipsandmayo.timesheetexpenses.data.Expense
import com.chipsandmayo.timesheetexpenses.data.ExpenseCategory
import com.chipsandmayo.timesheetexpenses.data.Fmt
import com.chipsandmayo.timesheetexpenses.util.Currencies
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun ReceiptFlow(vm: AppViewModel) {
    when (vm.receiptStep) {
        ReceiptStep.NONE -> {}
        ReceiptStep.READING -> Waiting("Reading receipt…")
        ReceiptStep.FETCHING_RATE -> Waiting("Getting today's exchange rate…")
        ReceiptStep.PICK_AMOUNT -> AmountPicker(vm, onPick = vm::pickAmount, onDismiss = vm::cancelReceipt)
        ReceiptStep.ASK_GBP -> AskGbp(vm)
        ReceiptStep.PICK_CURRENCY -> CurrencyPicker(vm.guessedCurrency, onPick = vm::pickCurrency, onDismiss = vm::cancelReceipt)
        ReceiptStep.EDIT -> vm.draft?.let { ExpenseEditor(vm, it) }
    }
}

@Composable
private fun Waiting(text: String) {
    Dialog(onDismissRequest = {}) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(16.dp)); Text(text)
            }
        }
    }
}

@Composable
fun AmountPicker(vm: AppViewModel, onPick: (Double) -> Unit, onDismiss: () -> Unit) {
    var manual by remember { mutableStateOf("") }
    val photo = vm.draft?.photos?.firstOrNull()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).heightIn(max = 680.dp), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Which number is the total?", style = MaterialTheme.typography.titleLarge)
                if (photo != null) {
                    AsyncImage(
                        model = File(photo.path), contentDescription = "Receipt",
                        modifier = Modifier.fillMaxWidth().height(200.dp).padding(vertical = 8.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
                if (vm.candidates.isEmpty()) {
                    Text("No amounts found on the photo — type it in below.", style = MaterialTheme.typography.bodyMedium)
                }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(vm.candidates) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(c.value) }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (c.likelyTotal) Icon(Icons.Filled.Star, "Likely total", tint = Gold, modifier = Modifier.size(18.dp))
                            else Spacer(Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(Fmt.money(c.value), fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp))
                            Text(c.context, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = Color.Gray)
                        }
                        HorizontalDivider()
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = manual, onValueChange = { manual = it }, label = { Text("Or enter amount") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { manual.replace(",", ".").toDoubleOrNull()?.let(onPick) }) { Text("OK") }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun AskGbp(vm: AppViewModel) {
    val d = vm.draft ?: return
    val guess = vm.guessedCurrency
    AlertDialog(
        onDismissRequest = vm::cancelReceipt,
        title = { Text("Is ${Fmt.money(d.amount)} in GBP (£)?") },
        text = {
            if (guess != null && guess != "GBP") Text("The receipt looks like it might be in $guess (${Currencies.name(guess)}).")
            else Text("Choose No to pick the receipt's currency and convert it to GBP.")
        },
        confirmButton = { Button(onClick = { vm.answerGbp(true) }) { Text("Yes, GBP") } },
        dismissButton = { OutlinedButton(onClick = { vm.answerGbp(false) }) { Text("No, other currency") } },
    )
}

@Composable
fun CurrencyPicker(guess: String?, onPick: (String) -> Unit, onDismiss: () -> Unit, includeGbp: Boolean = false) {
    var q by remember { mutableStateOf("") }
    val list = Currencies.all.filter { includeGbp || it.first != "GBP" }
        .sortedByDescending { it.first == "GBP" }
        .sortedByDescending { it.first == guess }
        .filter { q.isBlank() || it.first.contains(q, true) || it.second.contains(q, true) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxWidth().heightIn(max = 600.dp), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Receipt currency", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(q, { q = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(list) { (code, name) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(code) }
                                .background(if (code == guess) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .padding(12.dp),
                        ) {
                            Text(code, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp)); Text(name)
                            if (code == guess) Text("  (detected)", color = Color.Gray)
                        }
                    }
                    if (q.length == 3 && list.none { it.first.equals(q, true) }) {
                        item {
                            TextButton(onClick = { onPick(q.uppercase()) }) { Text("Use ${q.uppercase()}") }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExpenseEditor(vm: AppViewModel, initial: Expense) {
    var date by remember { mutableStateOf(initial.date) }
    var description by remember { mutableStateOf(initial.description) }
    var category by remember { mutableStateOf(initial.category) }
    var amount by remember { mutableStateOf(if (initial.amount == 0.0) "" else Fmt.money(initial.amount).replace(",", "")) }
    var currency by remember { mutableStateOf(initial.currency) }
    var rate by remember { mutableStateOf(if (initial.rateToGbp == 0.0) "" else initial.rateToGbp.toString()) }
    var rateSource by remember { mutableStateOf(initial.rateSource) }
    var miles by remember { mutableStateOf(if (initial.miles == 0.0) "" else initial.miles.toString()) }
    var showDate by remember { mutableStateOf(false) }
    var showCurrency by remember { mutableStateOf(false) }
    var showAmounts by remember { mutableStateOf(false) }
    var loadingRate by remember { mutableStateOf(false) }

    fun fetchRate(cur: String = currency, d: java.time.LocalDate = date) {
        if (cur == "GBP") { rate = "1.0"; rateSource = ""; return }
        loadingRate = true
        vm.refreshRate({ r, src -> loadingRate = false; if (r != null) { rate = r.toString(); rateSource = src } }, cur, d)
    }

    val amt = amount.replace(",", ".").toDoubleOrNull() ?: 0.0
    val rt = rate.replace(",", ".").toDoubleOrNull() ?: 0.0
    val mi = miles.replace(",", ".").toDoubleOrNull() ?: 0.0
    val gbp = if (category == ExpenseCategory.MILEAGE) mi * vm.perMile else amt * rt

    Dialog(onDismissRequest = vm::cancelReceipt, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::cancelReceipt) { Icon(Icons.Filled.Close, "Cancel") }
                    Text("Expense", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                initial.photos.firstOrNull()?.let { ph ->
                    Box {
                        AsyncImage(
                            model = File(ph.path), contentDescription = "Receipt",
                            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit,
                        )
                    }
                }
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) { Text("Date: ${date.format(Fmt.day)}") }
                OutlinedTextField(description, { description = it }, label = { Text("Description (e.g. Taxi to heliport)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Type", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExpenseCategory.entries.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
                    }
                }
                if (category == ExpenseCategory.MILEAGE) {
                    OutlinedTextField(miles, { miles = it }, label = { Text("Miles") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    Text("At £${vm.perMile} per mile (set in your profile)", style = MaterialTheme.typography.bodySmall)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { showCurrency = true }) { Text(currency) }
                    }
                    if (vm.candidates.isNotEmpty() && initial.photos.isNotEmpty()) {
                        TextButton(onClick = { showAmounts = true }) { Text("Pick a different number from the receipt") }
                    }
                    if (currency != "GBP") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(rate, { rate = it; rateSource = "manual" }, label = { Text("Rate: £ per 1 $currency") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            if (loadingRate) CircularProgressIndicator(Modifier.size(28.dp))
                            else IconButton(onClick = { fetchRate() }) { Icon(Icons.Filled.Refresh, "Get latest rate") }
                        }
                        if (rateSource.isNotBlank()) Text("Rate source: $rateSource", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Total: £${Fmt.money(gbp)}", Modifier.padding(12.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        vm.commitDraft(
                            initial.copy(
                                date = date, description = description.trim(), category = category,
                                amount = if (category == ExpenseCategory.MILEAGE) 0.0 else amt,
                                currency = if (category == ExpenseCategory.MILEAGE) "GBP" else currency,
                                rateToGbp = if (category == ExpenseCategory.MILEAGE || currency == "GBP") 1.0 else rt,
                                rateSource = rateSource, miles = if (category == ExpenseCategory.MILEAGE) mi else 0.0,
                            )
                        )
                    },
                    enabled = if (category == ExpenseCategory.MILEAGE) mi > 0 else amt > 0 && (currency == "GBP" || rt > 0),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save expense") }
            }
        }
    }

    if (showDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = dateToMillis(date))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let {
                        val nd = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        if (nd != date) { date = nd; if (currency != "GBP" && rateSource != "manual") fetchRate(d = nd) }
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = st) }
    }
    if (showCurrency) {
        CurrencyPicker(
            vm.guessedCurrency, includeGbp = true,
            onPick = { c ->
                currency = c; showCurrency = false
                if (c == "GBP") { rate = "1.0"; rateSource = "" } else fetchRate(cur = c)
            },
            onDismiss = { showCurrency = false },
        )
    }
    if (showAmounts) {
        AmountPicker(vm, onPick = { v -> amount = Fmt.money(v).replace(",", ""); showAmounts = false }, onDismiss = { showAmounts = false })
    }
}
