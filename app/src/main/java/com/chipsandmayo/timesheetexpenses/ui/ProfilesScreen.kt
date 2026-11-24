package com.chipsandmayo.timesheetexpenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.chipsandmayo.timesheetexpenses.AppViewModel
import com.chipsandmayo.timesheetexpenses.data.Profile
import java.io.File
import java.util.UUID

@Composable
fun ProfilesScreen(vm: AppViewModel) {
    var editing by remember { mutableStateOf<Profile?>(null) }
    var deleting by remember { mutableStateOf<Profile?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Profiles hold the details that go on every timesheet and expense form. Make one per client or vessel " +
                "and switch between them from the top bar.",
            style = MaterialTheme.typography.bodyMedium,
        )
        vm.profiles.forEach { p ->
            val active = p.id == vm.activeProfile?.id
            Card(
                Modifier.fillMaxWidth().clickable { vm.selectProfile(p.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = active, onClick = { vm.selectProfile(p.id) })
                    Column(Modifier.weight(1f)) {
                        Text(p.displayName, fontWeight = FontWeight.Bold)
                        Text(listOf(p.consultantName, p.client, p.vessel).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { editing = p }) { Icon(Icons.Filled.Edit, "Edit") }
                    IconButton(onClick = {
                        editing = p.copy(id = UUID.randomUUID().toString(), profileName = p.displayName + " (copy)", signaturePath = "")
                    }) { Icon(Icons.Filled.ContentCopy, "Duplicate") }
                    IconButton(onClick = { deleting = p }) { Icon(Icons.Filled.Delete, "Delete") }
                }
            }
        }
        Button(onClick = { editing = Profile() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("New profile")
        }
    }
    editing?.let { p -> ProfileEditor(vm, p, onClose = { editing = null }) }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${p.displayName}?") },
            confirmButton = { TextButton(onClick = { vm.deleteProfile(p); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProfileEditor(vm: AppViewModel, initial: Profile, onClose: () -> Unit) {
    var p by remember { mutableStateOf(initial) }
    var signing by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
                    Text("Profile", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Button(onClick = { vm.saveProfile(p); onClose() }) { Text("Save") }
                }
                Field("Profile name (e.g. N-Sea Pathfinder)", p.profileName) { p = p.copy(profileName = it) }
                Section("Timesheet")
                Field("Client", p.client) { p = p.copy(client = it) }
                Field("Consultant name", p.consultantName) { p = p.copy(consultantName = it) }
                Field("Consultant's position", p.position) { p = p.copy(position = it) }
                Field("Vessel / office", p.vessel) { p = p.copy(vessel = it) }
                Field("Project number", p.projectNumber) { p = p.copy(projectNumber = it) }
                Field("Project name", p.projectName) { p = p.copy(projectName = it) }
                Field("Authoriser's name", p.authoriserName) { p = p.copy(authoriserName = it) }
                Field("Location code (UK, DE, NL, DK, AU…)", p.locationCode) { p = p.copy(locationCode = it.uppercase().take(4)) }
                Field("Address (top-right box)", p.address, singleLine = false) { p = p.copy(address = it) }

                Section("Consultant signature")
                if (p.signaturePath.isNotBlank() && File(p.signaturePath).exists()) {
                    AsyncImage(
                        model = File(p.signaturePath), contentDescription = "Signature",
                        modifier = Modifier.fillMaxWidth().height(90.dp).background(Color.White, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { signing = true }) { Text(if (p.signaturePath.isBlank()) "Add signature" else "Re-draw signature") }
                    if (p.signaturePath.isNotBlank()) TextButton(onClick = { p = p.copy(signaturePath = "") }) { Text("Remove") }
                }

                Section("Expenses")
                Field("Expenses authorised by (blank = authoriser above)", p.expenseAuthorisedBy) { p = p.copy(expenseAuthorisedBy = it) }
                Field("Per-mile rate (£)", p.perMileRate, keyboard = KeyboardType.Decimal) { p = p.copy(perMileRate = it) }

                Section("Sending")
                Field("Email timesheets to (optional)", p.emailTo, keyboard = KeyboardType.Email) { p = p.copy(emailTo = it) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (signing) SignatureDialog(onDismiss = { signing = false }) { bmp ->
        p = p.copy(signaturePath = vm.saveSignature(p.id, bmp)); signing = false
    }
}

@Composable
private fun Section(t: String) =
    Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun Field(label: String, value: String, singleLine: Boolean = true, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), modifier = Modifier.fillMaxWidth(),
    )
}
