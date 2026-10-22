package com.inventornext.vivacoach.data

import android.content.Context

data class Settings(
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val speechRate: Float = 1.0f,
    val coachMode: Boolean = true,
) {
    companion object {
        const val DEFAULT_MODEL = "gemini-3.8-flash"
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("viva_prefs", Context.MODE_PRIVATE)

    fun load() = Settings(
        apiKey = sp.getString("api_key", "") ?: "",
        model = sp.getString("model", Settings.DEFAULT_MODEL) ?: Settings.DEFAULT_MODEL,
        speechRate = sp.getFloat("speech_rate", 1.0f),
        coachMode = sp.getBoolean("coach_mode", true),
    )

    fun save(s: Settings) {
        sp.edit()
            .putString("api_key", s.apiKey.trim())
            .putString("model", s.model.trim())
            .putFloat("speech_rate", s.speechRate)
            .putBoolean("coach_mode", s.coachMode)
            .apply()
    }
}
