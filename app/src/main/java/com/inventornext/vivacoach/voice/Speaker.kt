package com.inventornext.vivacoach.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** The examiner's voice: on-device text to speech, Indian English when available. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val main = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context.applicationContext, this)
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()
    private var ready = false
    private var pending: Pair<String, (() -> Unit)?>? = null

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    var rate: Float = 1.0f

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(Locale("en", "IN"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.US)
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _speaking.value = true
            }

            override fun onDone(utteranceId: String?) = finish(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                _speaking.value = false
                utteranceId?.let { callbacks.remove(it) }
            }
        })
        ready = true
        pending?.let { (text, done) -> speak(text, done) }
        pending = null
    }

    private fun finish(id: String?) {
        _speaking.value = false
        val cb = id?.let { callbacks.remove(it) } ?: return
        main.post(cb)
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) return
        if (!ready) {
            pending = text to onDone
            return
        }
        tts.setSpeechRate(rate)
        val id = UUID.randomUUID().toString()
        if (onDone != null) callbacks[id] = onDone
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stop() {
        pending = null
        if (ready) tts.stop()
        _speaking.value = false
    }

    fun shutdown() {
        tts.shutdown()
    }
}
