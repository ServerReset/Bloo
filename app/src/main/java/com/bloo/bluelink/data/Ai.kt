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

    // NOT `by lazy` any more -- a `by lazy` value, once created, is held
    // (and the underlying AICore session it opens stays live) for as long as
    // this Ai instance exists, with no way to hand it back. On the phone
    // path this instance lives on AppViewModel for the app's entire process
    // lifetime, so that meant Gemini Nano's own memory reservation was held
    // for the whole session after the very first isSupported()/summarize()
    // call -- exactly the "background apps get killed for RAM" complaint
    // this was rewritten for. Instead the client is built on demand and
    // [releaseSummarizer] hands it back the moment each call is done; see
    // [withSummarizer] for where that happens.
    private var summarizerRef: Summarizer? = null

    private fun summarizer(): Summarizer = summarizerRef ?: Summarization.getClient(
        SummarizerOptions.builder(app)
            .setInputType(SummarizerOptions.InputType.ARTICLE)
            .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
            .setLanguage(SummarizerOptions.Language.ENGLISH)
            .build(),
    ).also { summarizerRef = it }

    /** Closes the current client (if one is open) and forgets it, so the
     *  on-device model session AICore is holding on this app's behalf is
     *  actually released instead of just becoming unreachable garbage --
     *  `close()` is the real signal AICore waits for, a `Summarizer` left to
     *  the GC eventually gets collected but keeps its session reserved until
     *  then. Safe to call when nothing was ever opened (a `close()` on a
     *  handle nothing built) -- it's a no-op. */
    private fun releaseSummarizer() {
        summarizerRef?.let { runCatching { it.close() } }
        summarizerRef = null
    }

    /** Builds (or reuses an already-open) client for [block], then always
     *  releases it afterward -- success, failure, or cancellation -- so
     *  every public entry point below leaves no session open behind it
     *  regardless of how it ends. Reopening per call costs a cheap client
     *  handle construction, not a model reload (the model itself is cached
     *  on-device by AICore across app runs); the memory this actually saves
     *  is the *session* AICore keeps live for an open client, not the
     *  downloaded model weights. */
    private suspend fun <T> withSummarizer(block: suspend (Summarizer) -> T): T {
        try {
            return block(summarizer())
        } finally {
            releaseSummarizer()
        }
    }

    /**
     * True if Gemini Nano summarization is available or downloadable here.
     * Queries the current [FeatureStatus] and treats anything other than
     * UNAVAILABLE (i.e. AVAILABLE, DOWNLOADABLE, or DOWNLOADING) as supported --
     * a DOWNLOADABLE/DOWNLOADING status just means the model isn't on-device
     * *yet*, which [ensureFeatureReady] will handle transparently on first real
     * use. Any exception (e.g. the device/OS doesn't support ML Kit GenAI at
     * all) is swallowed and reported as unsupported rather than propagated.
     */
    suspend fun isSupported(): Boolean = runCatching {
        withSummarizer { it.checkFeatureStatus().await() != FeatureStatus.UNAVAILABLE }
    }.getOrDefault(false)

    /**
     * Summarize [text] on-device, downloading the model first if needed. Throws
     * on failure so the caller can surface a real message.
     *
     * Order of operations: first blocks (suspending, not the calling thread)
     * until the on-device model is downloaded and ready via [ensureFeatureReady]
     * -- this can take a while the very first time a user summarizes anything.
     * Then pads [text] up to ML Kit's minimum input length via [padToMinimum]
     * (short status blurbs routinely fall under that floor), builds a single
     * [SummarizationRequest], and runs inference. Unlike [isSupported], failures
     * here are intentionally NOT caught -- they propagate to the caller so a
     * real download/inference error can be shown to the user instead of a
     * silently empty summary.
     */
    suspend fun summarize(text: String): String = withSummarizer { summarizer ->
        ensureFeatureReady(summarizer)
        val request = SummarizationRequest.builder(padToMinimum(text)).build()
        val result = summarizer.runInference(request).await()
        result.summary
    }

    /**
     * The ARTICLE input type rejects anything under 400 characters. A single car's
     * status can fall short, so we repeat the same facts until the floor is met —
     * the model sees no new information, so the summary stays accurate.
     */
    private fun padToMinimum(text: String): String {
        if (text.length >= MIN_ARTICLE_CHARS) return text
        val sb = StringBuilder(text)
        // Repeats the original text, each copy separated by a newline, until the
        // combined length clears MIN_ARTICLE_CHARS. Repetition rather than any
        // kind of padding character/filler text is deliberate: the model summarizes
        // whatever it's given, so junk filler would risk leaking into (or skewing)
        // the summary, whereas repeating true statements just reinforces the same
        // facts and can't introduce anything false.
        while (sb.length < MIN_ARTICLE_CHARS) sb.append('\n').append(text)
        return sb.toString()
    }

    /**
     * Blocks (suspending) until the summarization feature is actually usable.
     * Reads the current [FeatureStatus]; if it's DOWNLOADABLE (not yet fetched)
     * or DOWNLOADING (fetch already in progress from a previous call), kicks off
     * [Summarizer.downloadFeature] and suspends on a [suspendCancellableCoroutine]
     * until one of its callbacks fires. Progress callbacks are ignored (no UI
     * hook here); only completion/failure resume the coroutine, and each guards
     * with `cont.isActive` since ML Kit could in principle invoke a callback
     * after the coroutine's already been cancelled/resumed. If the status is
     * already AVAILABLE (or, in principle, UNAVAILABLE), this returns immediately
     * without ever starting a download.
     */
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

    // Adapts Google Play Services' Task callback API (onSuccess/onFailure
    // listeners) into a single suspend call: whichever listener fires first
    // resumes the coroutine with a value or an exception respectively. Guarded
    // by `cont.isActive` in case both listeners could ever fire (they shouldn't,
    // but a stale resume would otherwise crash with "already resumed").
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    }

    // Same idea as the Task.await() above but for Guava's ListenableFuture,
    // which only offers a Runnable-callback + Executor API rather than
    // separate success/failure listeners. The callback runs on the `direct`
    // executor (i.e. synchronously, on whatever thread completes the future),
    // then calls the blocking `get()` to retrieve the result -- safe here
    // because the future is already known to be done when the listener fires.
    // A thrown exception from `get()` (e.g. ExecutionException) is caught and
    // turned into a coroutine failure instead of propagating out of the
    // listener callback. `invokeOnCancellation` propagates coroutine
    // cancellation back to the future itself (without interrupting an
    // in-progress task, per the `false` argument), so cancelling the caller
    // doesn't leave the underlying work running forever unacknowledged.
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
