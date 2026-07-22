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
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin wrapper around on-device Gemini Nano via ML Kit GenAI summarization. All
 * ML Kit usage is isolated here; the model is downloaded on demand the first time.
 */
class Ai(context: Context) {

    // Application context, not the passed-in context, so this class (and the
    // lazily-created summarizer below) can safely outlive whatever short-lived
    // Activity/Fragment context originally constructed it.
    private val app = context.applicationContext
    // A same-thread "executor" used only to bridge Google's ListenableFuture
    // callback API into a coroutine below; it just runs the callback inline on
    // whichever thread completes the future; there's no actual thread pool here.
    private val direct = Executor { it.run() }

    private companion object {
        // ML Kit's ARTICLE summarizer requires at least this many input characters.
        const val MIN_ARTICLE_CHARS = 400
    }

    // Created on first use (and only once, thanks to `by lazy`) so constructing
    // an Ai instance is cheap and the actual ML Kit client — and whatever
    // resources it holds — is only spun up if summarization is ever invoked.
    private val summarizer: Summarizer by lazy {
        Summarization.getClient(
            SummarizerOptions.builder(app)
                .setInputType(SummarizerOptions.InputType.ARTICLE)
                .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
                .setLanguage(SummarizerOptions.Language.ENGLISH)
                .build(),
        )
    }

    /** True if Gemini Nano summarization is available or downloadable here. */
    suspend fun isSupported(): Boolean = runCatching {
        summarizer.checkFeatureStatus().await() != FeatureStatus.UNAVAILABLE
    }.getOrDefault(false)

    /**
     * Summarize [text] on-device, downloading the model first if needed. Throws
     * on failure so the caller can surface a real message.
     */
    suspend fun summarize(text: String): String {
        ensureFeatureReady()
        val request = SummarizationRequest.builder(padToMinimum(text)).build()
        val result = summarizer.runInference(request).await()
        return result.summary
    }

    /**
     * The ARTICLE input type rejects anything under 400 characters. A single car's
     * status can fall short, so we repeat the same facts until the floor is met —
     * the model sees no new information, so the summary stays accurate.
     */
    private fun padToMinimum(text: String): String {
        if (text.length >= MIN_ARTICLE_CHARS) return text
        val sb = StringBuilder(text)
        while (sb.length < MIN_ARTICLE_CHARS) sb.append('\n').append(text)
        return sb.toString()
    }

    private suspend fun ensureFeatureReady() {
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

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    }

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
