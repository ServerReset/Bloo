package com.bloo.bluelink.data

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.Summarizer
import com.google.mlkit.genai.summarization.SummarizerOptions
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Thin wrapper around on-device Gemini Nano via ML Kit GenAI summarization. All ML Kit usage is
 * isolated here; the model is downloaded on demand the first time.
 */
class Ai(context: Context) {

    private val app = context.applicationContext
    // A same-thread "executor" used only to bridge Google's ListenableFuture callback API into a
    // coroutine below; it just runs the callback inline on whichever thread completes the future;
    // there's no actual thread pool here.
    private val direct = Executor { it.run() }

    private companion object {
        // ML Kit's ARTICLE summarizer requires at least this many input characters.
        const val MIN_ARTICLE_CHARS = 400
    }

    // NOT `by lazy` any more -- a `by lazy` value, once created, is held (and the underlying AICore
    // session it opens stays live) for as long as this Ai instance exists, with no way to hand it
    // back.
    private var summarizerRef: Summarizer? = null

    private fun summarizer(): Summarizer = summarizerRef ?: Summarization.getClient(
        SummarizerOptions.builder(app)
            .setInputType(SummarizerOptions.InputType.ARTICLE)
            .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
            .setLanguage(SummarizerOptions.Language.ENGLISH)
            .build(),
    ).also { summarizerRef = it }

    /**
     * Closes the current client (if one is open) and forgets it, so the on-device model session
     * AICore is holding on this app's behalf is actually released instead of just becoming
     * unreachable garbage -- `close()` is the real signal AICore waits for, a `Summarizer` left to
     * the GC eventually gets collected but keeps its session reserved until then.
     */
    private fun releaseSummarizer() {
        summarizerRef?.let { runCatching { it.close() } }
        summarizerRef = null
    }

    /**
     * Builds (or reuses an already-open) client for [block], then always releases it afterward --
     * success, failure, or cancellation -- so every public entry point below leaves no session open
     * behind it regardless of how it ends.
     */
    private suspend fun <T> withSummarizer(block: suspend (Summarizer) -> T): T {
        try {
            return block(summarizer())
        } finally {
            releaseSummarizer()
        }
    }

    /**
     * True if Gemini Nano summarization is available or downloadable here. Queries the current
     * [FeatureStatus] and treats anything other than UNAVAILABLE (i.e.
     */
    suspend fun isSupported(): Boolean = runCatching {
        withSummarizer { it.checkFeatureStatus().await() != FeatureStatus.UNAVAILABLE }
    }.getOrDefault(false)

    /**
     * Summarize [text] on-device, downloading the model first if needed. Throws on failure so the
     * caller can surface a real message.
     */
    suspend fun summarize(text: String): String = withSummarizer { summarizer ->
        ensureFeatureReady(summarizer)
        val request = SummarizationRequest.builder(padToMinimum(text)).build()
        val result = summarizer.runInference(request).await()
        result.summary
    }

    /**
     * The ARTICLE input type rejects anything under 400 characters. A single car's status can fall
     * short, so we repeat the same facts until the floor is met — the model sees no new
     * information, so the summary stays accurate.
     */
    private fun padToMinimum(text: String): String {
        if (text.length >= MIN_ARTICLE_CHARS) return text
        val sb = StringBuilder(text)
        // Repeats the original text, each copy separated by a newline, until the combined length
        // clears MIN_ARTICLE_CHARS.
        while (sb.length < MIN_ARTICLE_CHARS) sb.append('\n').append(text)
        return sb.toString()
    }

    /** Blocks (suspending) until the summarization feature is actually usable. */
    private suspend fun ensureFeatureReady(summarizer: Summarizer) {
        val status = summarizer.checkFeatureStatus().await()
        if (status == FeatureStatus.DOWNLOADABLE || status == FeatureStatus.DOWNLOADING) {
            suspendCancellableCoroutine { cont ->
                summarizer.downloadFeature(object : DownloadCallback {
                    override fun onDownloadStarted(bytesToDownload: Long) {}
                    override fun onDownloadProgress(totalBytesDownloaded: Long) {}
                    override fun onDownloadCompleted() {
                        if (cont.isActive) cont.resume(Unit)
                    }
                    override fun onDownloadFailed(e: GenAiException) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                })
            }
        }
    }

    // Adapts Google Play Services' Task callback API (onSuccess/onFailure listeners) into a single
    // suspend call: whichever listener fires first resumes the coroutine with a value or an
    // exception respectively.
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    }

    // Same idea as the Task.await() above but for Guava's ListenableFuture, which only offers a
    // Runnable-callback + Executor API rather than separate success/failure listeners.
    private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
        addListener({
            try {
                if (cont.isActive) cont.resume(get())
            } catch (e: Throwable) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        }, direct)
        cont.invokeOnCancellation { cancel(false) }
    }
}
