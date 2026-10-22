package com.inventornext.vivacoach.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inventornext.vivacoach.AppViewModel
import com.inventornext.vivacoach.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Viva Coach", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { vm.go(Screen.SettingsScreen) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.resetImport(); vm.go(Screen.Import) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add thesis or paper") },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.settings.apiKey.isBlank()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("One step before you start", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("Add a free Gemini API key so the examiner can read your work and ask questions.")
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { vm.go(Screen.SettingsScreen) }) { Text("Add API key") }
                        }
                    }
                }
            }
            item {
                Text(
                    "Practise your viva out loud with an AI examiner that has read your thesis.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { SectionTitle("Your theses and papers") }
            if (vm.theses.isEmpty()) {
                item {
                    Text(
                        "Nothing here yet. Tap \"Add thesis or paper\" and pick your thesis PDF or main .tex file.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(vm.theses, key = { it.id }) { t ->
                val rounds = vm.sessionsFor(t.id)
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { vm.go(Screen.ThesisDetail(t.id)) },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(t.brief.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (t.brief.field.isNotBlank()) {
                            Text(t.brief.field, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val last = rounds.firstOrNull()?.report
                            Text(
                                if (rounds.isEmpty()) "No practice rounds yet" else "${rounds.size} round(s) · last score ${last?.overall ?: 0}/100",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (last != null) scoreColor(last.overall / 100f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
