package com.inventornext.vivacoach.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
data class ChapterBrief(
    val title: String = "",
    val summary: String = "",
    @SerialName("key_points") val keyPoints: List<String> = emptyList(),
)

/** The examiner's dossier: a structured digest of the thesis, written once at import. */
@Serializable
data class ThesisBrief(
    val title: String = "",
    val field: String = "",
    val summary: String = "",
    val chapters: List<ChapterBrief> = emptyList(),
    val contributions: List<String> = emptyList(),
    val methods: List<String> = emptyList(),
    @SerialName("key_results") val keyResults: List<String> = emptyList(),
    val assumptions: List<String> = emptyList(),
    val limitations: List<String> = emptyList(),
    @SerialName("weak_spots") val weakSpots: List<String> = emptyList(),
) {
    fun toDossier(): String = buildString {
        appendLine("TITLE: $title")
        if (field.isNotBlank()) appendLine("FIELD: $field")
        appendLine()
        appendLine("SUMMARY: $summary")
        fun section(name: String, items: List<String>) {
            if (items.isEmpty()) return
            appendLine()
            appendLine("$name:")
            items.forEach { appendLine("- $it") }
        }
        if (chapters.isNotEmpty()) {
            appendLine()
            appendLine("CHAPTERS:")
            chapters.forEachIndexed { i, c ->
                appendLine("${i + 1}. ${c.title}: ${c.summary}")
                c.keyPoints.forEach { appendLine("   * $it") }
            }
        }
        section("CONTRIBUTIONS", contributions)
        section("METHODS", methods)
        section("KEY RESULTS", keyResults)
        section("ASSUMPTIONS", assumptions)
        section("STATED LIMITATIONS", limitations)
        section("LIKELY WEAK SPOTS", weakSpots)
    }
}

@Serializable
data class Thesis(
    val id: String,
    val fileName: String,
    val createdAt: Long,
    val brief: ThesisBrief,
)

@Serializable
data class Turn(
    val question: String,
    val isFollowUp: Boolean = false,
    val answer: String = "",
    val score: Int? = null,
    val reaction: String = "",
    val missed: List<String> = emptyList(),
    val modelAnswer: String = "",
)

/** Raw conversation with the model, so the examiner remembers the whole exchange. */
@Serializable
data class ChatMsg(val role: String, val text: String)

@Serializable
data class Report(
    val overall: Int = 0,
    val readiness: String = "",
    val strengths: List<String> = emptyList(),
    @SerialName("weak_areas") val weakAreas: List<String> = emptyList(),
    @SerialName("practice_next") val practiceNext: List<String> = emptyList(),
    @SerialName("examiner_comment") val examinerComment: String = "",
)

@Serializable
data class Session(
    val id: String,
    val thesisId: String,
    val persona: String,
    val occasion: String,
    val focus: String,
    val totalMain: Int,
    val startedAt: Long,
    val opening: String = "",
    val turns: List<Turn> = emptyList(),
    val history: List<ChatMsg> = emptyList(),
    val report: Report? = null,
) {
    val answered: List<Turn> get() = turns.filter { it.score != null }
    val mainAsked: Int get() = turns.count { !it.isFollowUp }
    val current: Turn? get() = turns.lastOrNull()?.takeIf { it.score == null }
}

// Shapes the model returns.

@Serializable
data class OpeningOut(val opening: String = "", val question: String = "")

@Serializable
data class EvalOut(
    val score: Double = 0.0,
    val reaction: String = "",
    val missed: List<String> = emptyList(),
    @SerialName("model_answer") val modelAnswer: String = "",
    @SerialName("next_question") val nextQuestion: String = "",
    @SerialName("is_follow_up") val isFollowUp: Boolean = false,
)

@Serializable
data class ReportOut(
    val overall: Double = 0.0,
    val readiness: String = "",
    val strengths: List<String> = emptyList(),
    @SerialName("weak_areas") val weakAreas: List<String> = emptyList(),
    @SerialName("practice_next") val practiceNext: List<String> = emptyList(),
    @SerialName("examiner_comment") val examinerComment: String = "",
)

/** Pulls the JSON object out of a model reply, tolerating code fences or stray text. */
inline fun <reified T> parseModelJson(text: String): T {
    val t = text.trim()
    val start = t.indexOf('{')
    val end = t.lastIndexOf('}')
    val core = if (start >= 0 && end > start) t.substring(start, end + 1) else t
    return AppJson.decodeFromString(core)
}
