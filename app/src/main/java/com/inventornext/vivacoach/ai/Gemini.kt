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
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.net.InetAddress
import java.net.UnknownHostException
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
        .dns(FallbackDns)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS)
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
            val uploadUrl = callForHeader(start, "x-goog-upload-url")

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

    private suspend fun callForHeader(req: Request, header: String): String = suspendCancellableCoroutine { cont ->
        val c = http.newCall(req)
        cont.invokeOnCancellation { c.cancel() }
        c.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(GeminiException("Network error: ${e.message}", retryable = true))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { r ->
                    if (!cont.isActive) return
                    val value = r.header(header)
                    when {
                        !r.isSuccessful -> cont.resumeWithException(GeminiException(errorMessage(r.code, r.body?.string().orEmpty())))
                        value == null -> cont.resumeWithException(GeminiException("Upload failed: no upload URL returned."))
                        else -> cont.resume(value)
                    }
                }
            }
        })
    }

    /** Runs a request; cancelling the coroutine cancels the HTTP call immediately. */
    private suspend fun call(req: Request): String = suspendCancellableCoroutine { cont ->
        val c = http.newCall(req)
        cont.invokeOnCancellation { c.cancel() }
        c.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isActive) return
                val msg = when {
                    e is UnknownHostException ->
                        "Can't reach Google's AI server from this phone. Check your internet, turn off any VPN or ad blocker, and set Private DNS to Automatic."
                    e is InterruptedIOException ->
                        "Gemini took too long to answer. Check your internet and tap Retry."
                    else -> "Network error: ${e.message ?: "check your internet connection"}"
                }
                cont.resumeWithException(GeminiException(msg, retryable = true))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { r ->
                    val body = try {
                        r.body?.string().orEmpty()
                    } catch (e: IOException) {
                        if (cont.isActive) cont.resumeWithException(GeminiException("Network error: ${e.message}", retryable = true))
                        return
                    }
                    if (!cont.isActive) return
                    if (!r.isSuccessful) {
                        cont.resumeWithException(
                            GeminiException(errorMessage(r.code, body), retryable = r.code == 429 || r.code >= 500),
                        )
                    } else {
                        cont.resume(body)
                    }
                }
            }
        })
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

/**
 * Uses the phone's DNS first. If that fails (blocked Private DNS, ad blockers,
 * some campus networks), looks the host up over HTTPS via Google or Cloudflare,
 * the same way Chrome's secure DNS does.
 */
private object FallbackDns : Dns {
    private val bootstrap = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val resolvers: List<DnsOverHttps> = listOf(
        DnsOverHttps.Builder().client(bootstrap)
            .url("https://dns.google/dns-query".toHttpUrl())
            .bootstrapDnsHosts(listOf("8.8.8.8", "8.8.4.4").map(InetAddress::getByName))
            .build(),
        DnsOverHttps.Builder().client(bootstrap)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(listOf("1.1.1.1", "1.0.0.1").map(InetAddress::getByName))
            .build(),
    )

    override fun lookup(hostname: String): List<InetAddress> {
        val systemError = try {
            return Dns.SYSTEM.lookup(hostname)
        } catch (e: UnknownHostException) {
            e
        }
        for (r in resolvers) {
            try {
                val found = r.lookup(hostname)
                if (found.isNotEmpty()) return found
            } catch (ignored: Exception) {
            }
        }
        throw systemError
    }
}
