package com.bloo.bluelink.data

import com.google.android.gms.tasks.Task
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Gemini Nano, through the ML Kit Prompt API, used for one job: turning a free-form request into a [NaturalIntent]. It never runs a car
 * command itself. Null means the model is not ready on this phone (AICore is missing, or the model still has to download), or its answer
 * could not be read, so the caller falls back to the typed-phrase parser.
 *
 * The response is read by name at run time, because the Prompt API's result types have not been confirmed against the library yet. A
 * shape that differs is logged and falls back, rather than failing the build.
 */
class IntentModel {
    private val lock = Mutex()
    private val direct = Executor { it.run() }

    suspend fun intentFor(query: String, nowHhmm: String, carNames: List<String>): NaturalIntent? = lock.withLock {
        withTimeout<NaturalIntent?>(TIMEOUT_MS) {
            val model = Generation.getClient()
            try {
                if (model.checkStatus() != FeatureStatus.AVAILABLE) return@withTimeout null
                val request = generateContentRequest(TextPart(naturalIntentPrompt(query, nowHhmm, carNames))) {}
                val reply = replyText(model, request) ?: return@withTimeout null
                parseNaturalIntent(reply)
            } finally {
                runCatching { model.close() }
            }
        }
    }

    /** The reply's text, whatever wrapper the library returns it in: a future, a task, or the response itself. */
    private suspend fun replyText(model: Any, request: Any): String? = runCatching {
        val raw = model.call("generateContent", request)
        val response = when (raw) {
            is ListenableFuture<*> -> raw.await()
            is Task<*> -> raw.await()
            else -> raw
        } ?: return@runCatching null
        val candidate = (response.call("getCandidates") as? List<*>)?.firstOrNull()
        ((candidate?.call("getText") ?: response.call("getText")) as? String)
    }.onFailure { android.util.Log.w("IntentModel", "Prompt API reply not read: ${it.message}") }.getOrNull()

    /** Calls the one method named [name] that takes [args], by reflection; throws if there is none. */
    private fun Any.call(name: String, vararg args: Any): Any? =
        javaClass.methods.first { it.name == name && it.parameterCount == args.size }.invoke(this, *args)

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

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener(direct) { task ->
            if (!cont.isActive) return@addOnCompleteListener
            if (task.isSuccessful) cont.resume(task.result) else cont.resumeWithException(task.exception ?: IllegalStateException("task failed"))
        }
    }

    private companion object {
        /** One answer is a short reply; past this the model is stuck, and the typed parser takes over. */
        const val TIMEOUT_MS = 20_000L
    }
}
