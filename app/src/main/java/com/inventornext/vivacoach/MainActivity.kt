package com.inventornext.vivacoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inventornext.vivacoach.ui.ExamScreen
import com.inventornext.vivacoach.ui.HomeScreen
import com.inventornext.vivacoach.ui.ImportScreen
import com.inventornext.vivacoach.ui.ReportScreen
import com.inventornext.vivacoach.ui.SettingsScreen
import com.inventornext.vivacoach.ui.ThesisScreen
import com.inventornext.vivacoach.ui.VivaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VivaTheme {
                val vm: AppViewModel = viewModel()
                App(vm)
            }
        }
    }
}

@Composable
fun App(vm: AppViewModel) {
    BackHandler(enabled = vm.screen != Screen.Home) { vm.back() }
    when (val s = vm.screen) {
        Screen.Home -> HomeScreen(vm)
        Screen.SettingsScreen -> SettingsScreen(vm)
        Screen.Import -> ImportScreen(vm)
        is Screen.ThesisDetail -> ThesisScreen(vm, s.id)
        Screen.Exam -> ExamScreen(vm)
        is Screen.ReportView -> ReportScreen(vm, s.sessionId)
    }
}
