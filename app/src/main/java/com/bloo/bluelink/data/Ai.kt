package com.bloo.bluelink.data

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.FeatureStatus
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
 * ML Kit usage is isolated here; every call is wrapped so an unsupported device
 * (or any failure) degrades gracefully to "no AI".
 */
class Ai(context: Context) {

    private val app = context.applicationContext
    private val direct = Executor { it.run() }

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

    /** Summarize [text] on-device; null if unavailable or it fails. */
    suspend fun summarize(text: String): String? = runCatching {
        val request = SummarizationRequest.builder(text).build()
        summarizer.runInference(request).await().summary
    }.getOrNull()

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
