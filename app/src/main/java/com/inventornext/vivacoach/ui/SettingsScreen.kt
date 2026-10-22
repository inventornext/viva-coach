package com.inventornext.vivacoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.inventornext.vivacoach.AppViewModel
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings
    val uri = LocalUriHandler.current
    var showKey by remember { mutableStateOf(false) }

    Scaffold(topBar = { BackBar("Settings", onBack = { vm.back() }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Gemini API key")
            Text(
                "The examiner runs on Google Gemini. Get a free key from Google AI Studio and paste it here. " +
                    "It stays on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = s.apiKey,
                onValueChange = { vm.updateSettings(s.copy(apiKey = it.trim())) },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") } },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { uri.openUri("https://aistudio.google.com/apikey") }) { Text("Get a free key at aistudio.google.com") }

            HorizontalDivider()
            SectionTitle("Model")
            OutlinedTextField(
                value = s.model,
                onValueChange = { vm.updateSettings(s.copy(model = it.trim())) },
                label = { Text("Model name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { vm.loadModels() }, enabled = s.apiKey.isNotBlank()) { Text("Check key and list models") }
            vm.modelStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (vm.models.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.models.forEach { m ->
                        FilterChip(selected = m == s.model, onClick = { vm.updateSettings(s.copy(model = m)) }, label = { Text(m) })
                    }
                }
                Text(
                    "Tip: a \"flash\" model is fast and cheap. A \"pro\" model asks sharper questions but is slower and may not be on the free tier.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()
            SectionTitle("Examiner voice")
            Text("Speaking speed: ${String.format(Locale.US, "%.1f", s.speechRate)}x")
            Slider(
                value = s.speechRate,
                onValueChange = { vm.updateSettings(s.copy(speechRate = (it * 10).toInt() / 10f)) },
                valueRange = 0.6f..1.5f,
            )
            Button(onClick = { vm.speaker.speak("Good morning. Let us begin. Why did you choose this research problem?") }) {
                Text("Test voice")
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Coach mode", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Show score and feedback after every answer. Turn off for a realistic exam; feedback then comes only in the final report.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = s.coachMode, onCheckedChange = { vm.updateSettings(s.copy(coachMode = it)) })
            }
        }
    }
}
