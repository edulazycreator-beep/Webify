package com.webify.app.ai

import com.webify.app.data.db.WebProjectEntity
import com.webify.app.data.prefs.AiProvider
import com.webify.app.data.prefs.AiSettings
import com.webify.app.data.prefs.SecurePrefs
import com.webify.app.domain.ChatRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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

data class AiMessage(val role: ChatRole, val content: String)

class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Klien AI multi-provider dengan streaming Server-Sent Events:
 *  - Google Gemini  : POST {base}/models/{model}:streamGenerateContent?alt=sse
 *  - OpenAI/Custom  : POST {base}/chat/completions (stream=true)
 */
class AiService(private val prefs: SecurePrefs) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val settings get() = prefs.aiSettings

    /** Memancarkan potongan teks (delta) secara bertahap. */
    fun stream(systemPrompt: String, history: List<AiMessage>, override: AiSettings? = null): Flow<String> = flow {
        val s = override ?: prefs.aiSettings.value
        if (s.provider != AiProvider.CUSTOM && s.apiKey.isBlank())
            throw AiException("API key ${s.provider.label} belum diisi. Buka Pengaturan AI di layar Co-Pilot.")
        val request = when (s.provider) {
            AiProvider.GEMINI -> geminiRequest(s, systemPrompt, history)
            AiProvider.OPENAI, AiProvider.CUSTOM -> openAiRequest(s, systemPrompt, history)
        }
        val call = client.newCall(request)
        val job: Job = currentCoroutineContext().job
        val handle = job.invokeOnCompletion { if (it != null) call.cancel() }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    throw AiException("HTTP ${resp.code}: ${extractError(body) ?: resp.message}")
                }
                val source = resp.body?.source() ?: throw AiException("Respons kosong")
                var gotAny = false
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break
                    val delta = when (s.provider) {
                        AiProvider.GEMINI -> parseGeminiDelta(data)
                        else -> parseOpenAiDelta(data)
                    }
                    if (!delta.isNullOrEmpty()) {
                        gotAny = true
                        emit(delta)
                    }
                }
                if (!gotAny) throw AiException("Model tidak mengembalikan teks (cek nama model / kuota).")
            }
        } catch (e: IOException) {
            if (call.isCanceled()) return@flow
            throw AiException("Koneksi gagal: ${e.message}", e)
        } finally {
            handle.dispose()
        }
    }.flowOn(Dispatchers.IO)

    /** Uji koneksi singkat untuk layar pengaturan. */
    suspend fun testConnection(s: AiSettings): String {
        val sb = StringBuilder()
        stream("Jawab singkat.", listOf(AiMessage(ChatRole.USER, "Balas dengan kata: OK")), s).collect { sb.append(it) }
        return sb.toString().trim().take(80)
    }

    // ------------------------------------------------------------------ Gemini
    private fun geminiRequest(s: AiSettings, system: String, history: List<AiMessage>): Request {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", system) } }
            }
            putJsonArray("contents") {
                mergeConsecutive(history).forEach { m ->
                    addJsonObject {
                        put("role", if (m.role == ChatRole.ASSISTANT) "model" else "user")
                        putJsonArray("parts") { addJsonObject { put("text", m.content) } }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.4)
                put("maxOutputTokens", 8192)
            }
        }
        val base = s.baseUrl.trimEnd('/')
        return Request.Builder()
            .url("$base/models/${s.model.trim()}:streamGenerateContent?alt=sse")
            .header("x-goog-api-key", s.apiKey.trim())
            .header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
    }

    private fun parseGeminiDelta(data: String): String? = try {
        val obj = json.parseToJsonElement(data).jsonObject
        obj["error"]?.let { throw AiException(it.jsonObject["message"]?.jsonPrimitive?.contentOrNull ?: "Gemini error") }
        val parts = obj["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray
        parts?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: "" }
    } catch (e: AiException) {
        throw e
    } catch (_: Exception) {
        null
    }

    // ------------------------------------------------------------------ OpenAI-compatible
    private fun openAiRequest(s: AiSettings, system: String, history: List<AiMessage>): Request {
        val body = buildJsonObject {
            put("model", s.model.trim())
            put("stream", true)
            put("temperature", 0.4)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", system) }
                history.forEach { m ->
                    addJsonObject {
                        put("role", if (m.role == ChatRole.ASSISTANT) "assistant" else "user")
                        put("content", m.content)
                    }
                }
            }
        }
        val b = Request.Builder()
            .url(s.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody(JSON_MEDIA))
        if (s.apiKey.isNotBlank()) b.header("Authorization", "Bearer ${s.apiKey.trim()}")
        return b.build()
    }

    private fun parseOpenAiDelta(data: String): String? = try {
        val obj = json.parseToJsonElement(data).jsonObject
        obj["error"]?.let { throw AiException(it.jsonObject["message"]?.jsonPrimitive?.contentOrNull ?: "API error") }
        obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull
    } catch (e: AiException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun extractError(body: String): String? = try {
        val el = json.parseToJsonElement(body)
        val obj = if (el is JsonArray) el.firstOrNull()?.jsonObject else el.jsonObject
        obj?.get("error")?.let { e ->
            (e as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: e.toString()
        }
    } catch (_: Exception) {
        body.take(300).ifBlank { null }
    }

    /** Gemini mewajibkan giliran user/model bergantian. */
    private fun mergeConsecutive(history: List<AiMessage>): List<AiMessage> {
        val out = mutableListOf<AiMessage>()
        for (m in history.filter { it.role != ChatRole.SYSTEM && it.content.isNotBlank() }) {
            val last = out.lastOrNull()
            if (last != null && last.role == m.role) out[out.lastIndex] = last.copy(content = last.content + "\n\n" + m.content)
            else out += m
        }
        if (out.firstOrNull()?.role == ChatRole.ASSISTANT) out.add(0, AiMessage(ChatRole.USER, "(lanjutan percakapan)"))
        return out
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

/** Penyusun system prompt kontekstual untuk Co-Pilot Webify. */
object PromptBuilder {

    fun system(project: WebProjectEntity?): String = buildString {
        appendLine(
            """
            Anda adalah "Webify Co-Pilot", asisten ahli Android WebView, CSS/JavaScript, dan publikasi Google Play.
            Jawab dalam Bahasa Indonesia yang ringkas dan jelas. Format Markdown.
            ATURAN KODE:
            - Kode CSS untuk diinjeksi WAJIB dalam satu blok ```css.
            - Kode JavaScript untuk diinjeksi WAJIB dalam satu blok ```javascript, berupa skrip mandiri (tanpa tag <script>),
              aman dijalankan berkali-kali (idempoten), dan tahan terhadap elemen yang belum ada (gunakan MutationObserver bila perlu).
            - Untuk menyembunyikan elemen gunakan selector yang spesifik dan `display:none !important`.
            - Jangan mengarang selector website yang tidak Anda ketahui; jelaskan asumsi dan cara pengguna memeriksa selector.
            Aplikasi hasil Webify menyediakan window.WebifyBridge dengan metode:
            showToast(msg), vibrate(ms), shareText(text[, title]), getAppVersion(), getAppVersionCode(), getPackageName(),
            isNativeApp(), getDeviceInfo() (string JSON), copyToClipboard(text), openExternal(url), retry(), closeApp().
            Deteksi aplikasi: `if (window.WebifyBridge) { ... }`.
            """.trimIndent()
        )
        if (project != null) {
            appendLine()
            appendLine("KONTEKS PROYEK AKTIF:")
            appendLine(projectSummary(project))
        }
    }

    fun projectSummary(p: WebProjectEntity): String = buildString {
        appendLine("- Nama: ${p.appName} | Package: ${p.packageName} | Versi: ${p.versionName} (${p.versionCode})")
        appendLine("- Sumber: ${p.sourceType} ${if (p.sourceType.name == "URL") p.targetUrl else "(ZIP lokal)"}")
        appendLine("- minSdk ${p.minSdk}, targetSdk ${p.targetSdk}, orientasi ${p.screenOrientation}, fullscreen=${p.fullscreenMode}")
        appendLine("- JS=${p.enableJavaScript}, DOMStorage=${p.enableDomStorage}, cache=${p.cacheMode}, mixedContent=${p.allowMixedContent}")
        appendLine("- Izin aktif: ${permissionList(p).joinToString(", ")}")
        appendLine("- Upload file=${p.enableFileUploadChooser}, DownloadManager=${p.enableDownloadManager}, intent eksternal=${p.enableExternalIntentRouting}")
        if (p.customCssCode.isNotBlank()) appendLine("- CSS kustom saat ini:\n```css\n${p.customCssCode.take(3000)}\n```")
        if (p.customJsOnPageFinished.isNotBlank()) appendLine("- JS onPageFinished saat ini:\n```javascript\n${p.customJsOnPageFinished.take(3000)}\n```")
    }

    fun permissionList(p: WebProjectEntity) = buildList {
        add("Internet"); add("Network State")
        if (p.permCamera) add("Kamera")
        if (p.permMicrophone) add("Mikrofon")
        if (p.permGeolocation) add("Lokasi (GPS)")
        if (p.permNotifications) add("Notifikasi")
        if (p.permStorageDownloads || p.enableDownloadManager) add("Penyimpanan/Unduhan")
        if (p.enableFileUploadChooser) add("Akses file untuk upload")
    }

    fun cssJsInjector(instruction: String) =
        "Buatkan kode injeksi untuk aplikasi WebView saya sesuai instruksi berikut. Berikan blok ```css dan/atau ```javascript " +
            "yang siap pakai, lalu jelaskan singkat cara kerjanya.\n\nInstruksi: $instruction"

    fun errorDoctor(stage: String, summary: String, logTail: String) = """
        Build lokal Webify GAGAL. Analisis penyebabnya dan berikan perbaikan konkret.
        Tahap gagal: $stage
        Ringkasan: $summary

        Log terakhir:
        ```
        ${logTail.takeLast(6000)}
        ```

        Format jawaban:
        1. **Penyebab** (bahasa sederhana)
        2. **Perbaikan** langkah demi langkah (sebutkan field proyek di Builder Studio yang perlu diubah)
        3. Jika masalah ada di CSS/JS kustom, berikan versi yang sudah diperbaiki dalam blok kode.
    """.trimIndent()

    fun asoGenerator(p: WebProjectEntity?) = """
        Buatkan materi listing Google Play (ASO) untuk aplikasi ini dalam Bahasa Indonesia:
        1. **Judul Aplikasi** (maks 30 karakter) — 3 alternatif.
        2. **Deskripsi Singkat** (maks 80 karakter) — 3 alternatif, sertakan jumlah karakter.
        3. **Deskripsi Lengkap** (maks 4000 karakter) dengan paragraf pembuka kuat, poin fitur, dan ajakan bertindak; optimalkan kata kunci secara alami.
        4. **Kata kunci utama** (10 item).
        ${if (p == null) "Tanyakan dulu informasi aplikasi jika konteks proyek tidak tersedia." else "Gunakan konteks proyek aktif."}
    """.trimIndent()

    fun privacyPolicy(p: WebProjectEntity?) = """
        Buatkan dokumen Kebijakan Privasi (Privacy Policy) lengkap dalam Bahasa Indonesia untuk aplikasi Android ini,
        sesuai persyaratan Google Play (Data safety). Sesuaikan dengan izin yang aktif:
        ${p?.let { PromptBuilder.permissionList(it).joinToString(", ") } ?: "(tanyakan izin yang dipakai)"}.
        Sertakan: data yang dikumpulkan, tujuan penggunaan, layanan pihak ketiga (konten web yang dimuat), cookie & penyimpanan lokal,
        keamanan, hak pengguna & penghapusan data, privasi anak, perubahan kebijakan, dan kontak (beri placeholder [EMAIL]).
        Tandai bagian yang perlu disesuaikan pemilik dengan [KURUNG SIKU]. Format Markdown siap ditempel.
    """.trimIndent()

    fun bridgeAdvisor(question: String) =
        "Bantu saya menulis kode JavaScript di sisi website untuk berkomunikasi dengan window.WebifyBridge. " +
            "Sertakan deteksi fallback bila dibuka di browser biasa.\n\nKebutuhan: $question"
}
