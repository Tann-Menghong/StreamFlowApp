package com.streamflow.data.ai

import android.content.Context
import android.os.Build
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import com.streamflow.data.OkHttpDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device AI: downloads a small open LLM once (Qwen 2.5 0.5B, int8 .task
 * bundle, Apache 2.0) and runs it locally with MediaPipe LLM Inference.
 * No API key, no server, works offline after the one-time download.
 * MediaPipe tasks need Android 7.0+ — gate every entry point on isSupported().
 */
object AiEngine {

    const val MODEL_URL =
        "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
    const val MODEL_LABEL = "Qwen 2.5 (0.5B) — runs fully offline"
    const val MODEL_SIZE_LABEL = "about 550 MB"

    // The ekv1280 bundle shares 1280 tokens between prompt and reply, so the
    // prompt must stay well under that — budget in chars (~3.5 chars/token).
    private const val MAX_TOKENS = 1280
    const val PROMPT_CHAR_BUDGET = 2600

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= 24

    fun modelFile(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "ai/qwen25-0.5b-q8.task")
    }

    fun isModelReady(context: Context): Boolean = modelFile(context).exists()

    // ── Model download (resumable via Range + .part file) ─────────────────────
    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val progress: Float) : DownloadState()
        object Ready : DownloadState()
        data class Failed(val message: String) : DownloadState()
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    fun refreshState(context: Context) {
        if (_downloadState.value !is DownloadState.Downloading) {
            _downloadState.value = if (isModelReady(context)) DownloadState.Ready else DownloadState.Idle
        }
    }

    suspend fun downloadModel(context: Context) = withContext(Dispatchers.IO) {
        if (isModelReady(context)) { _downloadState.value = DownloadState.Ready; return@withContext }
        if (_downloadState.value is DownloadState.Downloading) return@withContext
        _downloadState.value = DownloadState.Downloading(0f)
        val target = modelFile(context)
        val part = File(target.parentFile, target.name + ".part")
        try {
            target.parentFile?.mkdirs()
            var retryFresh: Boolean
            do {
                retryFresh = false
                val existing = if (part.exists()) part.length() else 0L
                val reqBuilder = Request.Builder().url(MODEL_URL)
                if (existing > 0) reqBuilder.header("Range", "bytes=$existing-")
                OkHttpDownloader.instance.client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (resp.code == 416 && existing > 0) {
                        // The .part no longer matches the remote file (stale resume
                        // offset or the upstream file changed) — every retry would
                        // 416 forever, so drop it and restart from scratch once
                        part.delete()
                        retryFresh = true
                        return@use
                    }
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                    val resumed = resp.code == 206
                    val body = resp.body ?: throw IllegalStateException("Empty response")
                    val total = body.contentLength().let { len ->
                        if (len > 0) len + (if (resumed) existing else 0L) else -1L
                    }
                    var written = if (resumed) existing else 0L
                    FileOutputStream(part, resumed).use { out ->
                        body.byteStream().use { input ->
                            val buf = ByteArray(256 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                written += n
                                if (total > 0) {
                                    _downloadState.value =
                                        DownloadState.Downloading((written.toFloat() / total).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                }
            } while (retryFresh)
            if (!part.renameTo(target)) throw IllegalStateException("Couldn't move model into place")
            _downloadState.value = DownloadState.Ready
        } catch (e: Exception) {
            // Keep the .part file so a retry resumes instead of restarting 550 MB
            _downloadState.value = DownloadState.Failed(e.message ?: "Download failed")
        }
    }

    /** Returns false (and does nothing) while a generation is running. */
    fun deleteModel(context: Context): Boolean {
        if (busy) return false // closing the engine mid-generation crashes natively
        synchronized(this) {
            try { llm?.close() } catch (_: Exception) {}
            llm = null
        }
        modelFile(context).delete()
        File(modelFile(context).parentFile, modelFile(context).name + ".part").delete()
        _downloadState.value = DownloadState.Idle
        return true
    }

    // ── Inference ──────────────────────────────────────────────────────────────
    private var llm: LlmInference? = null
    @Volatile private var busy = false

    val isBusy: Boolean get() = busy

    private fun engine(context: Context): LlmInference {
        synchronized(this) {
            llm?.let { return it }
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile(context).absolutePath)
                .setMaxTokens(MAX_TOKENS)
                .build()
            return LlmInference.createFromOptions(context.applicationContext, options).also { llm = it }
        }
    }

    /** Streams the response through [onPartial] (full text so far) and returns the final text. */
    suspend fun generate(context: Context, prompt: String, onPartial: (String) -> Unit): String {
        if (!isSupported()) throw IllegalStateException("Needs Android 7.0 or newer")
        if (!isModelReady(context)) throw IllegalStateException("AI model not downloaded")
        if (busy) throw IllegalStateException("AI is already working — try again in a moment")
        busy = true
        // busy is released by the native done callback (or a failed start), NOT by
        // a finally block: if the caller is cancelled mid-generation the engine is
        // still running natively, and freeing busy early would let a second
        // generateResponseAsync overlap the first and crash.
        try {
            return withContext(Dispatchers.IO) {
                val eng = engine(context) // first call loads the 550 MB model — slow, keep off main
                suspendCancellableCoroutine { cont ->
                    val sb = StringBuilder()
                    val listener = ProgressListener<String> { partial, done ->
                        partial?.let { sb.append(it) }
                        onPartial(cleanOutput(sb.toString()))
                        if (done) {
                            busy = false
                            if (cont.isActive) cont.resume(cleanOutput(sb.toString()).trim())
                        }
                    }
                    try {
                        eng.generateResponseAsync(prompt, listener)
                    } catch (e: Exception) {
                        busy = false
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // generation continues natively; listener will clear busy
        } catch (e: Exception) {
            busy = false
            throw e
        }
    }

    // Small models sometimes echo the chat-template tokens — cut them out
    private fun cleanOutput(s: String): String =
        s.substringBefore("<|im_end|>").substringBefore("<|im_start|>")

    /** Qwen 2.5 instruct expects the ChatML template. */
    fun chatPrompt(userContent: String): String =
        "<|im_start|>system\nYou are a concise, helpful assistant inside a video app. Answer briefly.<|im_end|>\n" +
        "<|im_start|>user\n$userContent<|im_end|>\n<|im_start|>assistant\n"

    // ── Transcript helpers ─────────────────────────────────────────────────────

    /** Downloads a WebVTT subtitle file and flattens it to plain text. */
    suspend fun fetchTranscript(vttUrl: String): String = withContext(Dispatchers.IO) {
        val resp = OkHttpDownloader.instance.client
            .newCall(Request.Builder().url(vttUrl).build()).execute()
        resp.use {
            if (!it.isSuccessful) return@withContext ""
            val raw = it.body?.string() ?: return@withContext ""
            val lines = ArrayList<String>()
            var last = ""
            for (line in raw.lineSequence()) {
                val t = line.replace(Regex("<[^>]*>"), "").trim()
                if (t.isEmpty()) continue
                if (t.startsWith("WEBVTT") || t.startsWith("NOTE") || t.startsWith("Kind:") || t.startsWith("Language:")) continue
                if (t.contains("-->")) continue
                if (t.toIntOrNull() != null) continue // cue numbers
                if (t == last) continue // auto-captions repeat lines across cues
                last = t
                lines.add(t)
            }
            lines.joinToString(" ")
        }
    }

    /**
     * Fits [text] into the prompt budget. Long transcripts are sampled from the
     * beginning, middle and end so the summary covers the whole video instead
     * of just the intro.
     */
    fun fitToBudget(text: String, budget: Int = PROMPT_CHAR_BUDGET): String {
        val safeBudget = budget.coerceAtLeast(300) // very long questions must not zero out the transcript
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.length <= safeBudget) return clean
        val slice = safeBudget / 3
        val midStart = (clean.length - slice) / 2
        return clean.take(slice) + " […] " +
            clean.substring(midStart, midStart + slice) + " […] " +
            clean.takeLast(slice)
    }
}
