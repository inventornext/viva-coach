package com.inventornext.vivacoach.ai

import com.inventornext.vivacoach.data.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiException(message: String, val retryable: Boolean = false) : Exception(message)

sealed interface Part {
    data class Text(val text: String) : Part
    data class FileRef(val uri: String, val mime: String) : Part
}

data class Msg(val role: String, val parts: List<Part>) {
    companion object {
        fun user(text: String) = Msg("user", listOf(Part.Text(text)))
        fun model(text: String) = Msg("model", listOf(Part.Text(text)))
    }
}

/** Minimal Gemini REST client: generateContent, File API upload, model list. */
class Gemini(
    private val keyProvider: () -> String,
    private val modelProvider: () -> String,
) {
    private val base = "https://generativelanguage.googleapis.com"
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    private fun apiKey(): String {
        val k = keyProvider().trim()
        if (k.isEmpty()) throw GeminiException("Add your Gemini API key in Settings first.")
        return k
    }

    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$base/v1beta/models?pageSize=1000")
            .header("x-goog-api-key", apiKey())
            .build()
        val root = AppJson.parseToJsonElement(call(req)).jsonObject
        root["models"]?.jsonArray.orEmpty().mapNotNull { el ->
            val o = el.jsonObject
            val methods = o["supportedGenerationMethods"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
            if ("generateContent" in methods) o["name"]?.jsonPrimitive?.content?.removePrefix("models/") else null
        }
    }

    /** Uploads a file (e.g. thesis PDF) with the resumable File API and returns its URI. */
    suspend fun uploadFile(bytes: ByteArray, mime: String, displayName: String): String =
        withContext(Dispatchers.IO) {
            val key = apiKey()
            val startBody = buildJsonObject {
                putJsonObject("file") { put("display_name", displayName) }
            }.toString().toRequestBody(jsonType)
            val start = Request.Builder()
                .url("$base/upload/v1beta/files")
                .header("x-goog-api-key", key)
                .header("X-Goog-Upload-Protocol", "resumable")
                .header("X-Goog-Upload-Command", "start")
                .header("X-Goog-Upload-Header-Content-Length", bytes.size.toString())
                .header("X-Goog-Upload-Header-Content-Type", mime)
                .post(startBody)
                .build()
            val uploadUrl = try {
                http.newCall(start).execute().use { r ->
                    if (!r.isSuccessful) throw GeminiException(errorMessage(r.code, r.body?.string().orEmpty()))
                    r.header("x-goog-upload-url") ?: throw GeminiException("Upload failed: no upload URL returned.")
                }
            } catch (e: IOException) {
                throw GeminiException("Network error: ${e.message}", retryable = true)
            }

            val upload = Request.Builder()
                .url(uploadUrl)
                .header("X-Goog-Upload-Offset", "0")
                .header("X-Goog-Upload-Command", "upload, finalize")
                .post(bytes.toRequestBody(mime.toMediaType()))
                .build()
            var file = AppJson.parseToJsonElement(call(upload)).jsonObject["file"]?.jsonObject
                ?: throw GeminiException("Upload failed: unexpected response.")
            val name = file.str("name") ?: throw GeminiException("Upload failed: file has no name.")

            var tries = 0
            while (file.str("state") == "PROCESSING" && tries < 100) {
                delay(3000)
                tries++
                val get = Request.Builder().url("$base/v1beta/$name").header("x-goog-api-key", key).build()
                file = AppJson.parseToJsonElement(call(get)).jsonObject
            }
            when (file.str("state")) {
                "FAILED" -> throw GeminiException("Gemini could not process this file. Try a smaller or text-based PDF.")
                "PROCESSING" -> throw GeminiException("Gemini is taking too long to process the file. Try again.")
            }
            file.str("uri") ?: throw GeminiException("Upload failed: file has no URI.")
        }

    /** Sends a conversation and returns the model's text reply. */
    suspend fun generate(
        system: String?,
        messages: List<Msg>,
        jsonOut: Boolean = true,
        temperature: Double = 0.7,
    ): String = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            if (system != null) {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { addJsonObject { put("text", system) } }
                }
            }
            putJsonArray("contents") {
                for (m in messages) {
                    addJsonObject {
                        put("role", m.role)
                        putJsonArray("parts") {
                            for (p in m.parts) {
                                addJsonObject {
                                    when (p) {
                                        is Part.Text -> put("text", p.text)
                                        is Part.FileRef -> putJsonObject("fileData") {
                                            put("mimeType", p.mime)
                                            put("fileUri", p.uri)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", temperature)
                if (jsonOut) put("responseMimeType", "application/json")
            }
        }
        val model = modelProvider().trim().removePrefix("models/")
        val req = Request.Builder()
            .url("$base/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", apiKey())
            .post(payload.toString().toRequestBody(jsonType))
            .build()

        var last: GeminiException? = null
        for (attempt in 0..2) {
            try {
                return@withContext extractText(call(req))
            } catch (e: GeminiException) {
                last = e
                if (!e.retryable || attempt == 2) throw e
                delay(2500L * (attempt + 1))
            }
        }
        throw last ?: GeminiException("Unknown error")
    }

    private fun call(req: Request): String {
        try {
            http.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    throw GeminiException(errorMessage(r.code, body), retryable = r.code == 429 || r.code >= 500)
                }
                return body
            }
        } catch (e: IOException) {
            throw GeminiException("Network error: ${e.message ?: "check your internet connection"}", retryable = true)
        }
    }

    private fun extractText(body: String): String {
        val root = AppJson.parseToJsonElement(body).jsonObject
        val candidates = root["candidates"]?.jsonArray
        if (candidates.isNullOrEmpty()) {
            val reason = root["promptFeedback"]?.jsonObject?.str("blockReason")
            throw GeminiException(if (reason != null) "Gemini blocked the request ($reason)." else "Gemini returned no answer. Try again.", retryable = reason == null)
        }
        val parts = candidates[0].jsonObject["content"]?.jsonObject?.get("parts")?.jsonArray.orEmpty()
        val text = parts.mapNotNull { p ->
            val o = p.jsonObject
            if (o["thought"]?.jsonPrimitive?.booleanOrNull == true) null else o.str("text")
        }.joinToString("")
        if (text.isBlank()) throw GeminiException("Gemini returned an empty answer. Try again.", retryable = true)
        return text
    }

    private fun errorMessage(code: Int, body: String): String {
        val msg = runCatching {
            AppJson.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.str("message")
        }.getOrNull()
        val hint = when (code) {
            400 -> if (msg?.contains("API key", ignoreCase = true) == true) "Your API key looks invalid. " else ""
            403 -> "API key not allowed for this request. "
            404 -> "Model not found. Pick another model in Settings. "
            429 -> "Rate limit or free quota reached. Wait a minute and retry. "
            else -> ""
        }
        return "$hint(Gemini $code) ${msg ?: body.take(200)}".trim()
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
}
