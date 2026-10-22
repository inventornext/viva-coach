package com.inventornext.vivacoach.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inventornext.vivacoach.AppViewModel
import com.inventornext.vivacoach.RoundSetup
import com.inventornext.vivacoach.Screen
import com.inventornext.vivacoach.ai.Occasion
import com.inventornext.vivacoach.ai.Persona

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThesisScreen(vm: AppViewModel, id: String) {
    val thesis = vm.thesis(id)
    if (thesis == null) {
        LaunchedEffect(id) { vm.back() }
        return
    }
    val b = thesis.brief
    val setup = vm.setup
    var confirmDelete by remember { mutableStateOf(false) }
    var showDossier by remember { mutableStateOf(false) }
    val rounds = vm.sessionsFor(id)
    val focusOptions = listOf(RoundSetup.WHOLE_WORK) + b.chapters.map { it.title }.filter { it.isNotBlank() }

    Scaffold(
        topBar = {
            BackBar(
                title = b.title,
                onBack = { vm.back() },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (b.field.isNotBlank()) Text(b.field, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(b.summary, style = MaterialTheme.typography.bodyMedium)

            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Start a practice round", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                    Text("Occasion", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Occasion.entries.forEach { o ->
                            FilterChip(selected = setup.occasion == o, onClick = { vm.setup = setup.copy(occasion = o) }, label = { Text(o.label) })
                        }
                    }

                    Text("Examiner", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Persona.entries.forEach { p ->
                            FilterChip(selected = setup.persona == p, onClick = { vm.setup = setup.copy(persona = p) }, label = { Text(p.label) })
                        }
                    }
                    Text(setup.persona.blurb, style = MaterialTheme.typography.bodySmall)

                    Text("Focus", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        focusOptions.forEach { f ->
                            FilterChip(
                                selected = setup.focus == f,
                                onClick = { vm.setup = setup.copy(focus = f) },
                                label = { Text(f, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }

                    Text("Questions", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15).forEach { n ->
                            FilterChip(selected = setup.questions == n, onClick = { vm.setup = setup.copy(questions = n) }, label = { Text("$n") })
                        }
                    }

                    Button(
                        onClick = {
                            if (setup.focus !in focusOptions) vm.setup = setup.copy(focus = RoundSetup.WHOLE_WORK)
                            vm.startRound(id)
                        },
                        enabled = vm.settings.apiKey.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Start") }
                    if (vm.settings.apiKey.isBlank()) {
                        Text("Add your API key in Settings to start.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            ListCard("What examiners will likely attack", b.weakSpots, MaterialTheme.colorScheme.secondaryContainer)

            if (rounds.isNotEmpty()) {
                SectionTitle("Past rounds")
                rounds.forEach { r ->
                    val rep = r.report ?: return@forEach
                    Card(Modifier.fillMaxWidth().clickable { vm.go(Screen.ReportView(r.id)) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                val persona = runCatching { Persona.valueOf(r.persona).label }.getOrDefault(r.persona)
                                val occasion = runCatching { Occasion.valueOf(r.occasion).label }.getOrDefault(r.occasion)
                                Text("$occasion · $persona", fontWeight = FontWeight.SemiBold)
                                Text(formatDate(r.startedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${rep.overall}", style = MaterialTheme.typography.headlineSmall, color = scoreColor(rep.overall / 100f), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            TextButton(onClick = { showDossier = !showDossier }) {
                Text(if (showDossier) "Hide the examiner's dossier" else "Show the examiner's dossier")
            }
            if (showDossier) {
                b.chapters.forEachIndexed { i, c ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${i + 1}. ${c.title}", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(c.summary, style = MaterialTheme.typography.bodyMedium)
                            if (c.keyPoints.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Bullets(c.keyPoints)
                            }
                        }
                    }
                }
                ListCard("Contributions", b.contributions)
                ListCard("Methods", b.methods)
                ListCard("Key results", b.keyResults)
                ListCard("Assumptions", b.assumptions)
                ListCard("Stated limitations", b.limitations)
                Text(
                    "Check this against your thesis. If something is wrong, the examiner will be wrong too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this thesis?") },
            text = { Text("Its dossier and all practice rounds will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteThesis(id) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
