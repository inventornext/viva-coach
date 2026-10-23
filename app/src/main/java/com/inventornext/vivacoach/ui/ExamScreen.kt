package com.inventornext.vivacoach.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import com.inventornext.vivacoach.AppViewModel
import com.inventornext.vivacoach.R
import com.inventornext.vivacoach.ai.Persona
import com.inventornext.vivacoach.data.Turn

@Composable
fun ExamScreen(vm: AppViewModel) {
    val session = vm.current
    val context = LocalContext.current
    val view = LocalView.current
    val listening by vm.listener.listening.collectAsState()
    val level by vm.listener.level.collectAsState()
    val micError by vm.listener.error.collectAsState()
    val speaking by vm.speaker.speaking.collectAsState()
    var confirmLeave by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.toggleMic()
    }
    val onMic = {
        val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (ok) vm.toggleMic() else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    BackHandler { confirmLeave = true }

    if (session == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val persona = runCatching { Persona.valueOf(session.persona) }.getOrDefault(Persona.STRICT)
    val currentTurn = session.current
    val subtitle = when {
        currentTurn == null -> "Wrapping up"
        currentTurn.isFollowUp -> "Follow-up on question ${session.mainAsked} of ${session.totalMain}"
        else -> "Question ${session.mainAsked} of ${session.totalMain}"
    }
    val busy = vm.busy
    val listState = rememberLazyListState()

    LaunchedEffect(session.turns.size, session.answered.size, busy, vm.error) {
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    Scaffold(
        topBar = {
            BackBar(
                title = persona.label,
                subtitle = subtitle,
                onBack = { confirmLeave = true },
                actions = {
                    TextButton(onClick = { vm.finishEarly() }, enabled = busy == null) { Text("End") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    micError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = vm.draft,
                        onValueChange = { vm.draft = it },
                        placeholder = { Text(if (listening) "Listening... speak your answer" else "Tap the mic and answer out loud, or type") },
                        minLines = 2,
                        maxLines = 6,
                        enabled = busy == null && currentTurn != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.replayQuestion() }, enabled = currentTurn != null) {
                            Icon(painterResource(R.drawable.ic_volume), contentDescription = "Repeat question",
                                tint = if (speaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { vm.skip() }, enabled = busy == null && currentTurn != null) { Text("Skip") }
                        Spacer(Modifier.weight(1f))
                        FilledIconButton(
                            onClick = { onMic() },
                            enabled = busy == null && currentTurn != null,
                            modifier = Modifier.size(68.dp).scale(if (listening) 1f + level * 0.15f else 1f),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (listening) Color(0xFFD1453B) else MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(
                                painterResource(if (listening) R.drawable.ic_stop else R.drawable.ic_mic),
                                contentDescription = if (listening) "Stop" else "Answer by voice",
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        FilledTonalIconButton(
                            onClick = { vm.submit() },
                            enabled = busy == null && currentTurn != null && vm.draft.isNotBlank(),
                            modifier = Modifier.size(52.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Submit answer")
                        }
                    }
                }
            }
        },
    ) { pad ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (session.opening.isNotBlank()) {
                item(key = "opening") { ExaminerBubble(session.opening, label = null) }
            }
            itemsIndexed(session.turns, key = { i, _ -> "turn$i" }) { _, turn ->
                TurnBlock(turn, coach = vm.settings.coachMode)
            }
            if (busy != null) {
                item(key = "busy") {
                    var seconds by remember { mutableStateOf(0L) }
                    LaunchedEffect(vm.busySince) {
                        while (true) {
                            seconds = (System.currentTimeMillis() - vm.busySince) / 1000
                            delay(1000)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text("$busy... ${seconds}s", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        if (seconds >= 20) TextButton(onClick = { vm.cancelBusy() }) { Text("Cancel") }
                    }
                }
            }
            vm.error?.let { msg ->
                item(key = "error") { ErrorCard(msg, onRetry = { vm.retryLast() }) }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave this round?") },
            text = { Text("You can end now and get a report on the answers so far, or leave without one.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmLeave = false; vm.finishEarly() },
                    enabled = busy == null && session.answered.isNotEmpty(),
                ) { Text("End and get report") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false; vm.back() }) { Text("Leave") }
            },
        )
    }
}

@Composable
private fun TurnBlock(turn: Turn, coach: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExaminerBubble(turn.question, label = if (turn.isFollowUp) "Follow-up" else null)
        if (turn.score != null) {
            AnswerBubble(turn.answer.ifBlank { "(Skipped)" })
            if (turn.reaction.isNotBlank()) {
                Text(
                    turn.reaction,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (coach) FeedbackCard(turn)
        }
    }
}

@Composable
private fun ExaminerBubble(text: String, label: String?) {
    Row(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                .padding(12.dp),
        ) {
            if (label != null) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text(text, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun AnswerBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                .padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun FeedbackCard(turn: Turn) {
    val score = turn.score ?: return
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, scoreColor(score / 10f).copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).background(scoreColor(score / 10f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("$score", color = Color.White, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.size(10.dp))
                Text("out of 10", style = MaterialTheme.typography.bodySmall)
            }
            if (turn.missed.isNotEmpty()) {
                Text("You missed", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Bullets(turn.missed)
            }
            if (turn.modelAnswer.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text("A stronger answer", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(turn.modelAnswer, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
