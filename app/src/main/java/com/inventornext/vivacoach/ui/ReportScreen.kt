package com.inventornext.vivacoach.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inventornext.vivacoach.AppViewModel
import com.inventornext.vivacoach.RoundSetup
import com.inventornext.vivacoach.ai.Occasion
import com.inventornext.vivacoach.ai.Persona

@Composable
fun ReportScreen(vm: AppViewModel, sessionId: String) {
    val s = vm.session(sessionId)
    val report = s?.report
    if (s == null || report == null) {
        LaunchedEffect(sessionId) { vm.back() }
        return
    }
    val persona = runCatching { Persona.valueOf(s.persona) }.getOrDefault(Persona.STRICT)
    val occasion = runCatching { Occasion.valueOf(s.occasion) }.getOrDefault(Occasion.VIVA)

    Scaffold(topBar = { BackBar("Round report", subtitle = "${occasion.label} · ${persona.label}", onBack = { vm.back() }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${report.overall}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor(report.overall / 100f),
                    )
                    Text(" /100", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(16.dp))
                    Text(report.readiness, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
            }
            if (report.examinerComment.isNotBlank()) {
                Text("“${report.examinerComment}”", fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyLarge)
            }
            ListCard("Strengths", report.strengths, MaterialTheme.colorScheme.tertiaryContainer)
            ListCard("Weak areas", report.weakAreas, MaterialTheme.colorScheme.secondaryContainer)
            ListCard("Practise these next", report.practiceNext)

            SectionTitle("Question by question")
            var mainNo = 0
            s.answered.forEach { t ->
                if (!t.isFollowUp) mainNo++
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (t.isFollowUp) "Q$mainNo follow-up" else "Q$mainNo",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            val score = t.score ?: 0
                            Text("$score/10", fontWeight = FontWeight.Bold, color = scoreColor(score / 10f))
                        }
                        Text(t.question, fontWeight = FontWeight.SemiBold)
                        if (t.missed.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Bullets(t.missed, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    vm.setup = RoundSetup(occasion, persona, s.focus, s.totalMain)
                    vm.startRound(s.thesisId)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Practise again") }
            OutlinedButton(onClick = { vm.back() }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
