package com.bloo.bluelink.data

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Gemini Nano, through the ML Kit Prompt API, used for one job: turning a free-form request into a [NaturalIntent]. It never runs a car
 * command itself. Null means the model is not ready on this phone (AICore is missing, or the model still has to download), so the caller
 * falls back to the typed-phrase parser.
 */
class IntentModel {
    private val lock = Mutex()

    suspend fun intentFor(query: String, nowHhmm: String, carNames: List<String>): NaturalIntent? = lock.withLock {
        withTimeout<NaturalIntent?>(TIMEOUT_MS) {
            val model = Generation.getClient()
            try {
                if (model.checkStatus() != FeatureStatus.AVAILABLE) return@withTimeout null
                val request = generateContentRequest(TextPart(naturalIntentPrompt(query, nowHhmm, carNames)))
                val response = model.generateContent(request)
                response.candidates.firstOrNull()?.text?.let(::parseNaturalIntent)
            } finally {
                runCatching { model.close() }
            }
        }
    }

    private companion object {
        /** One answer is a short reply; past this the model is stuck, and the typed parser takes over. */
        const val TIMEOUT_MS = 20_000L
    }
}
