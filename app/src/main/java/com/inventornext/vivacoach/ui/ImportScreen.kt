package com.inventornext.vivacoach.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inventornext.vivacoach.AppViewModel
import com.inventornext.vivacoach.ImportState
import com.inventornext.vivacoach.Screen

@Composable
fun ImportScreen(vm: AppViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(uri)
    }
    val state = vm.importState
    val working = state is ImportState.Working

    Scaffold(topBar = { BackBar("Add thesis or paper", onBack = { if (!working) vm.back() }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Pick your thesis, synopsis or paper. The examiner reads it once and builds a dossier: chapters, " +
                    "methods, key numbers, assumptions and the weak spots a real panel would attack.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Works best with", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Bullets(
                        listOf(
                            "The full thesis PDF (up to 50 MB)",
                            "A single .tex or .txt file with your main text",
                            "A synopsis or journal paper PDF",
                        ),
                    )
                }
            }

            when (state) {
                is ImportState.Working -> {
                    Text(state.step, style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        "Keep the app open. Your file is sent to Gemini only to build the dossier.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is ImportState.Failed -> ErrorCard(state.message)
                ImportState.Idle -> {}
            }

            if (vm.settings.apiKey.isBlank()) {
                ErrorCard("Add your Gemini API key in Settings first.")
                Button(onClick = { vm.go(Screen.SettingsScreen) }) { Text("Open Settings") }
            } else {
                Button(
                    onClick = {
                        picker.launch(
                            arrayOf("application/pdf", "text/plain", "text/x-tex", "application/x-tex", "application/octet-stream"),
                        )
                    },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Choose file") }
            }
        }
    }
}
