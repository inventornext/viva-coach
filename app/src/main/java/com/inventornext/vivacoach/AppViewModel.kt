package com.inventornext.vivacoach

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inventornext.vivacoach.ai.Gemini
import com.inventornext.vivacoach.ai.Msg
import com.inventornext.vivacoach.ai.Occasion
import com.inventornext.vivacoach.ai.Part
import com.inventornext.vivacoach.ai.Persona
import com.inventornext.vivacoach.ai.Prompts
import com.inventornext.vivacoach.data.ChatMsg
import com.inventornext.vivacoach.data.EvalOut
import com.inventornext.vivacoach.data.OpeningOut
import com.inventornext.vivacoach.data.Prefs
import com.inventornext.vivacoach.data.Report
import com.inventornext.vivacoach.data.ReportOut
import com.inventornext.vivacoach.data.Repo
import com.inventornext.vivacoach.data.Session
import com.inventornext.vivacoach.data.Settings
import com.inventornext.vivacoach.data.Thesis
import com.inventornext.vivacoach.data.ThesisBrief
import com.inventornext.vivacoach.data.Turn
import com.inventornext.vivacoach.data.parseModelJson
import com.inventornext.vivacoach.voice.Listener
import com.inventornext.vivacoach.voice.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

sealed interface Screen {
    data object Home : Screen
    data object SettingsScreen : Screen
    data object Import : Screen
    data class ThesisDetail(val id: String) : Screen
    data object Exam : Screen
    data class ReportView(val sessionId: String) : Screen
}

sealed interface ImportState {
    data object Idle : ImportState
    data class Working(val step: String) : ImportState
    data class Failed(val message: String) : ImportState
}

data class RoundSetup(
    val occasion: Occasion = Occasion.VIVA,
    val persona: Persona = Persona.STRICT,
    val focus: String = WHOLE_WORK,
    val questions: Int = 10,
) {
    companion object {
        const val WHOLE_WORK = "The whole work"
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val repo = Repo(app)

    var settings by mutableStateOf(prefs.load())
        private set

    private val gemini = Gemini({ settings.apiKey }, { settings.model })
    val speaker = Speaker(app).also { it.rate = settings.speechRate }
    val listener = Listener(app) { text -> draft = text }

    // Navigation
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    private val backStack = mutableStateListOf<Screen>()
    val canGoBack: Boolean get() = backStack.isNotEmpty()

    fun go(s: Screen) {
        backStack.add(screen)
        screen = s
    }

    private fun replace(s: Screen) {
        screen = s
    }

    fun back() {
        stopAudio()
        if (screen == Screen.Exam) abandonIfEmpty()
        screen = if (backStack.isEmpty()) Screen.Home else backStack.removeAt(backStack.lastIndex)
    }

    // Library
    var theses by mutableStateOf(repo.theses())
        private set
    var sessions by mutableStateOf(repo.sessions())
        private set

    private fun refresh() {
        theses = repo.theses()
        sessions = repo.sessions()
    }

    fun thesis(id: String): Thesis? = theses.firstOrNull { it.id == id }
    fun sessionsFor(thesisId: String): List<Session> = sessions.filter { it.thesisId == thesisId && it.report != null }
    fun session(id: String): Session? = sessions.firstOrNull { it.id == id }

    fun deleteThesis(id: String) {
        repo.deleteThesis(id)
        refresh()
        backStack.clear()
        screen = Screen.Home
    }

    // Settings
    fun updateSettings(s: Settings) {
        settings = s
        prefs.save(s)
        speaker.rate = s.speechRate
    }

    var models by mutableStateOf<List<String>>(emptyList())
        private set
    var modelStatus by mutableStateOf<String?>(null)
        private set

    fun loadModels() = viewModelScope.launch {
        modelStatus = "Checking your key..."
        modelStatus = try {
            val skip = listOf("image", "tts", "live", "transcribe", "embedding", "banana", "omni", "audio", "veo", "imagen")
            models = gemini.listModels()
                .filter { m -> m.startsWith("gemini") && skip.none { m.contains(it) } }
                .sortedDescending()
            "Key works. ${models.size} models available. Pick one below."
        } catch (e: Exception) {
            models = emptyList()
            e.message ?: "Could not reach Gemini."
        }
    }

    // Import
    var importState by mutableStateOf<ImportState>(ImportState.Idle)
        private set
    private var importJob: Job? = null

    fun resetImport() {
        importState = ImportState.Idle
    }

    fun importFile(uri: Uri) {
        if (importState is ImportState.Working) return
        importJob = viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                importState = ImportState.Working("Opening file")
                val (name, bytes, mime) = withContext(Dispatchers.IO) {
                    val resolver = ctx.contentResolver
                    var displayName = "document"
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) displayName = c.getString(0) ?: displayName
                    }
                    val data = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Could not open the file.")
                    Triple(displayName, data, resolver.getType(uri).orEmpty())
                }
                val lower = name.lowercase()
                val isPdf = mime == "application/pdf" || lower.endsWith(".pdf")
                val isText = lower.endsWith(".tex") || lower.endsWith(".txt") || lower.endsWith(".md") || mime.startsWith("text/")
                val parts: List<Part> = when {
                    isPdf -> {
                        if (bytes.size > 50 * 1024 * 1024) throw IllegalStateException("PDF is over 50 MB. Export a smaller PDF (compress images) and try again.")
                        importState = ImportState.Working("Uploading your PDF")
                        val fileUri = gemini.uploadFile(bytes, "application/pdf", name)
                        listOf(Part.FileRef(fileUri, "application/pdf"), Part.Text(Prompts.BRIEF))
                    }
                    isText -> listOf(Part.Text("DOCUMENT ($name):\n\n" + bytes.decodeToString()), Part.Text(Prompts.BRIEF))
                    else -> throw IllegalStateException("Please pick a PDF, or a .tex or .txt file.")
                }
                importState = ImportState.Working("Reading your work like an examiner would. This takes 1 to 3 minutes.")
                val raw = gemini.generate(null, listOf(Msg("user", parts)), jsonOut = true, temperature = 0.2)
                val brief = parseModelJson<ThesisBrief>(raw).let {
                    if (it.title.isBlank()) it.copy(title = name.substringBeforeLast('.')) else it
                }
                val thesis = Thesis(UUID.randomUUID().toString(), name, System.currentTimeMillis(), brief)
                withContext(Dispatchers.IO) { repo.saveThesis(thesis) }
                refresh()
                importState = ImportState.Idle
                if (screen == Screen.Import) replace(Screen.ThesisDetail(thesis.id))
            } catch (e: Exception) {
                importState = ImportState.Failed(e.message ?: "Something went wrong.")
            }
        }
    }

    // Examination
    var setup by mutableStateOf(RoundSetup())
    var current by mutableStateOf<Session?>(null)
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var draft by mutableStateOf("")
    private var retry: (() -> Unit)? = null

    private fun persona(s: Session) = runCatching { Persona.valueOf(s.persona) }.getOrDefault(Persona.STRICT)
    private fun occasion(s: Session) = runCatching { Occasion.valueOf(s.occasion) }.getOrDefault(Occasion.VIVA)

    private fun systemFor(s: Session): String {
        val brief = thesis(s.thesisId)?.brief ?: ThesisBrief()
        return Prompts.examinerSystem(brief, persona(s), occasion(s), s.focus)
    }

    private fun historyMsgs(s: Session) = s.history.map { Msg(it.role, listOf(Part.Text(it.text))) }

    fun startRound(thesisId: String) {
        stopAudio()
        val s = Session(
            id = UUID.randomUUID().toString(),
            thesisId = thesisId,
            persona = setup.persona.name,
            occasion = setup.occasion.name,
            focus = setup.focus,
            totalMain = setup.questions,
            startedAt = System.currentTimeMillis(),
        )
        current = s
        draft = ""
        error = null
        go(Screen.Exam)
        begin()
    }

    private fun begin() {
        val s = current ?: return
        task("The examiner is getting ready", ::begin) {
            val prompt = Prompts.start(s.totalMain)
            val raw = gemini.generate(systemFor(s), listOf(Msg.user(prompt)), temperature = 0.8)
            val out = parseModelJson<OpeningOut>(raw)
            if (out.question.isBlank()) throw IllegalStateException("The examiner did not ask a question. Retry.")
            val next = s.copy(
                opening = out.opening,
                turns = listOf(Turn(question = out.question)),
                history = listOf(ChatMsg("user", prompt), ChatMsg("model", raw)),
            )
            save(next)
            speaker.speak(listOf(out.opening, out.question).filter { it.isNotBlank() }.joinToString(" "))
        }
    }

    fun submit() {
        val text = draft.trim()
        if (text.isEmpty()) return
        answer(text)
    }

    fun skip() = answer(Prompts.SKIPPED)

    private fun answer(text: String) {
        val s = current ?: return
        val turn = s.current ?: return
        listener.cancel()
        speaker.stop()
        val mainIndex = s.mainAsked
        val followUps = s.turns.takeLastWhile { it.isFollowUp }.size
        task("The examiner is thinking", { answer(text) }) {
            val prompt = Prompts.answer(text, mainIndex, s.totalMain, followUps)
            val raw = gemini.generate(systemFor(s), historyMsgs(s) + Msg.user(prompt), temperature = 0.7)
            val out = parseModelJson<EvalOut>(raw)
            val scored = turn.copy(
                answer = if (text == Prompts.SKIPPED) "" else text,
                score = out.score.roundToInt().coerceIn(0, 10),
                reaction = out.reaction,
                missed = out.missed,
                modelAnswer = out.modelAnswer,
            )
            val turns = s.turns.dropLast(1) + scored
            val history = s.history + ChatMsg("user", prompt) + ChatMsg("model", raw)
            val canFollow = followUps < Prompts.MAX_FOLLOW_UPS
            val wantsFollow = out.isFollowUp && canFollow
            val isLast = mainIndex >= s.totalMain
            val ends = out.nextQuestion.isBlank() || (isLast && !wantsFollow)
            draft = ""
            if (ends) {
                val done = s.copy(turns = turns, history = history)
                save(done)
                speaker.speak(out.reaction)
                retry = ::writeReport
                busy = "Writing your report"
                makeReport(done)
            } else {
                val next = s.copy(turns = turns + Turn(out.nextQuestion, isFollowUp = wantsFollow), history = history)
                save(next)
                speaker.speak(listOf(out.reaction, out.nextQuestion).filter { it.isNotBlank() }.joinToString(" "))
            }
        }
    }

    /** End the round now and get the report, if at least one answer was given. */
    fun finishEarly() {
        val s = current ?: return
        listener.cancel()
        if (s.answered.isEmpty()) {
            back()
            return
        }
        if (s.current != null) save(s.copy(turns = s.turns.dropLast(1)))
        writeReport()
    }

    private fun writeReport() {
        val s = current ?: return
        task("Writing your report", ::writeReport) { makeReport(s) }
    }

    private suspend fun makeReport(s: Session) {
        val raw = gemini.generate(systemFor(s), historyMsgs(s) + Msg.user(Prompts.report()), temperature = 0.4)
        val out = parseModelJson<ReportOut>(raw)
        val report = Report(
            overall = out.overall.roundToInt().coerceIn(0, 100),
            readiness = out.readiness,
            strengths = out.strengths,
            weakAreas = out.weakAreas,
            practiceNext = out.practiceNext,
            examinerComment = out.examinerComment,
        )
        save(s.copy(report = report))
        current = null
        replace(Screen.ReportView(s.id))
    }

    fun replayQuestion() {
        val q = current?.current?.question ?: return
        listener.cancel()
        speaker.speak(q)
    }

    fun toggleMic() {
        if (listener.listening.value) {
            listener.stop()
        } else {
            speaker.stop()
            listener.start(draft)
        }
    }

    fun retryLast() {
        error = null
        retry?.invoke()
    }

    fun stopAudio() {
        speaker.stop()
        listener.cancel()
    }

    private fun abandonIfEmpty() {
        busyJob?.cancel()
        val s = current ?: return
        if (s.report == null) {
            if (s.answered.isEmpty()) repo.deleteSession(s.id)
            refresh()
        }
        current = null
        busy = null
        error = null
    }

    private fun save(s: Session) {
        current = s
        repo.saveSession(s)
        sessions = repo.sessions()
    }

    var busySince by mutableStateOf(0L)
        private set
    private var busyJob: Job? = null

    private fun task(label: String, again: () -> Unit, block: suspend () -> Unit) {
        if (busy != null) return
        busy = label
        busySince = System.currentTimeMillis()
        error = null
        retry = again
        busyJob = viewModelScope.launch {
            try {
                withTimeout(5 * 60_000L) { block() }
            } catch (e: TimeoutCancellationException) {
                error = "The examiner took more than 5 minutes. Check your internet and tap Retry. " +
                    "If it keeps happening, pick a different \"flash\" model in Settings."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                busy = null
                busyJob = null
            }
        }
    }

    /** User tapped Cancel while waiting. */
    fun cancelBusy() {
        busyJob?.cancel()
        busy = null
        error = "Stopped. Tap Retry to try again."
    }

    override fun onCleared() {
        listener.destroy()
        speaker.shutdown()
        importJob?.cancel()
    }
}
