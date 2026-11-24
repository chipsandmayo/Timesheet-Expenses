package com.chipsandmayo.timesheetexpenses

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.chipsandmayo.timesheetexpenses.ui.AppTheme
import com.chipsandmayo.timesheetexpenses.ui.MainScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
        if (savedInstanceState == null) vm.checkForUpdate(silent = true)
        setContent { AppTheme { MainScreen(vm) } }
    }
}
