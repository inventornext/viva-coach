package com.inventornext.vivacoach.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Long-form answer capture with Android's on-device speech recognizer.
 * The system recognizer stops after a pause, so we keep restarting it and
 * stitching the pieces together until the user taps stop.
 */
class Listener(private val context: Context, private val onText: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    private val committed = StringBuilder()
    private var partialText = ""
    private var active = false      // user wants to keep talking
    private var capturing = false   // still accepting results (true until final result after stop)
    private var preferOffline = true

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(initial: String) {
        if (!available) {
            _error.value = "No speech recognition on this phone. Install or enable Google speech services, or type your answer."
            return
        }
        committed.clear()
        committed.append(initial.trim())
        partialText = ""
        active = true
        capturing = true
        _error.value = null
        _listening.value = true
        begin()
    }

    /** Stop listening but keep whatever is still being recognised. */
    fun stop() {
        active = false
        _listening.value = false
        _level.value = 0f
        recognizer?.stopListening()
        // Some recognizers never send a final result after stopListening.
        main.postDelayed({ if (!active && capturing) finalizeCapture() }, 2500)
    }

    /** Stop and drop anything pending (used when the answer is submitted). */
    fun cancel() {
        active = false
        capturing = false
        _listening.value = false
        _level.value = 0f
        recognizer?.cancel()
    }

    fun destroy() {
        cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private fun begin() {
        val rec = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L)
        }
        rec.startListening(intent)
    }

    private fun restartSoon() {
        main.postDelayed({ if (active) begin() }, 200)
    }

    private fun publish() {
        val text = listOf(committed.toString(), partialText).filter { it.isNotBlank() }.joinToString(" ")
        onText(text)
    }

    private fun commit(piece: String) {
        if (piece.isNotBlank()) {
            if (committed.isNotEmpty()) committed.append(' ')
            committed.append(piece.trim())
        }
        partialText = ""
        publish()
    }

    private fun finalizeCapture() {
        if (partialText.isNotBlank()) commit(partialText)
        capturing = false
    }

    private fun firstResult(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            if (active) _level.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!capturing) return
            val p = firstResult(partialResults)
            if (p.isNotBlank()) {
                partialText = p
                publish()
            }
        }

        override fun onResults(results: Bundle?) {
            if (!capturing) return
            val r = firstResult(results)
            commit(r.ifBlank { partialText })
            if (active) restartSoon() else { capturing = false }
        }

        override fun onError(error: Int) {
            if (!capturing) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    if (partialText.isNotBlank()) commit(partialText)
                    if (active) restartSoon() else { capturing = false }
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    if (active) {
                        recognizer?.cancel()
                        restartSoon()
                    }
                }
                ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> {
                    if (preferOffline && active) {
                        preferOffline = false   // offline en-IN pack missing, fall back to online
                        restartSoon()
                    } else fail("Speech language not available on this phone. Type your answer instead.")
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail("Microphone permission is needed to answer by voice.")
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    fail("Speech recognition needs internet on this phone right now. Check your connection or type.")
                else -> {
                    if (partialText.isNotBlank()) commit(partialText)
                    if (active) restartSoon() else { capturing = false }
                }
            }
        }
    }

    private fun fail(message: String) {
        if (partialText.isNotBlank()) commit(partialText)
        active = false
        capturing = false
        _listening.value = false
        _level.value = 0f
        _error.value = message
    }

    fun clearError() {
        _error.value = null
    }

    private companion object {
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
