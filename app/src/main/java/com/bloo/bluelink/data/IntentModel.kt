package com.bloo.bluelink.data

import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/**
 * Gemini Nano, through the ML Kit Prompt API, used for one job: turning a free-form request into a [NaturalIntent]. It never runs a car
 * command itself. Null means the model is not ready on this phone (AICore is missing, or the model still has to download), so the caller
 * falls back to the typed-phrase parser.
 */
class IntentModel {
    private val lock = Mutex()
    private val direct = Executor { it.run() }

    suspend fun intentFor(query: String, nowHhmm: String, carNames: List<String>): NaturalIntent? = lock.withLock {
        withTimeout(TIMEOUT_MS) {
            val model = Generation.getClient()
            try {
                if (model.checkStatus().await() != FeatureStatus.AVAILABLE) return@withTimeout null
                val reply = model.generateContent(naturalIntentPrompt(query, nowHhmm, carNames)).await()
                reply.candidates.firstOrNull()?.text?.let(::parseNaturalIntent)
            } finally {
                runCatching { model.close() }
            }
        }
    }

    // Same bridge as Ai.kt uses for Guava futures: a Runnable callback plus a same-thread executor.
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

    private companion object {
        /** One answer is a short reply; past this the model is stuck, and the typed parser takes over. */
        const val TIMEOUT_MS = 20_000L
    }
}
