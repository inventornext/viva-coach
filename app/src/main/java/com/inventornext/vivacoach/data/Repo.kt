package com.inventornext.vivacoach.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import java.io.File

/** Stores theses and sessions as JSON files in the app's private storage. Fully offline. */
class Repo(context: Context) {
    private val root = context.filesDir
    private val thesisDir: File get() = File(root, "theses").apply { mkdirs() }
    private val sessionDir: File get() = File(root, "sessions").apply { mkdirs() }

    fun theses(): List<Thesis> =
        thesisDir.listFiles().orEmpty().filter { it.extension == "json" }
            .mapNotNull { f -> runCatching { AppJson.decodeFromString<Thesis>(f.readText()) }.getOrNull() }
            .sortedByDescending { it.createdAt }

    fun sessions(): List<Session> =
        sessionDir.listFiles().orEmpty().filter { it.extension == "json" }
            .mapNotNull { f -> runCatching { AppJson.decodeFromString<Session>(f.readText()) }.getOrNull() }
            .sortedByDescending { it.startedAt }

    fun saveThesis(t: Thesis) = write(File(thesisDir, "${t.id}.json"), AppJson.encodeToString(Thesis.serializer(), t))

    fun saveSession(s: Session) = write(File(sessionDir, "${s.id}.json"), AppJson.encodeToString(Session.serializer(), s))

    fun deleteSession(id: String) {
        File(sessionDir, "$id.json").delete()
    }

    fun deleteThesis(id: String) {
        File(thesisDir, "$id.json").delete()
        sessions().filter { it.thesisId == id }.forEach { deleteSession(it.id) }
    }

    private fun write(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }
}
