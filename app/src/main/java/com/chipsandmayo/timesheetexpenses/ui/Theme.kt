package com.chipsandmayo.timesheetexpenses.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF1B2A4A)
val Gold = Color(0xFFF2C230)
val OffshoreColor = Color(0xFF1F4E9C)
val TravelColor = Color(0xFFE39B17)
val OnshoreColor = Color(0xFF2E8B57)
val StandbyColor = Color(0xFF7B4FA0)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Navy,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDCE3F2),
            onPrimaryContainer = Navy,
            secondary = Gold,
            onSecondary = Navy,
            secondaryContainer = Color(0xFFFFF1C7),
            onSecondaryContainer = Navy,
            surface = Color.White,
            background = Color(0xFFF7F8FB),
        ),
        content = content,
    )
}
