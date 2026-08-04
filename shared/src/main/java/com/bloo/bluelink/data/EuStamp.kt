package com.bloo.bluelink.data

import java.util.Base64

/**
 * Generates the `Stamp` header every Hyundai/Kia CCAPI (Europe) request must
 * carry. The CCAPI rejects requests whose stamp it can't reproduce, so this is
 * required on device registration, sign-in, and every command.
 *
 * Algorithm (ported from the Apache-2.0 `hyundai_kia_connect_api` reference):
 * take the raw string `"$appId:$timestampMillis"`, XOR it byte-for-byte against
 * the decoded base64 [CFB] seed (repeating the seed if the raw is longer), and
 * base64-encode the result.
 *
 * [CFB] and [APP_ID] rotate when Hyundai ships a new app build. They are the
 * single maintenance point: if EU sign-in starts returning 4xx with a stamp
 * error, refresh them from the reference project's `const.py` (Hyundai EU) —
 * mirroring the rotatable `clientSecret` documented in [Brand]. They are marked
 * FILL-FROM-SOURCE rather than fabricated, because a wrong stamp fails silently
 * at the server and the repo's rule is real data only, nothing simulated.
 */
object EuStamp {
    /** FILL-FROM-SOURCE: base64 CFB seed bound to [APP_ID] (Hyundai EU). */
    const val CFB: String = "FILL-FROM-SOURCE"

    /** FILL-FROM-SOURCE: the Hyundai EU appId the stamp is bound to. */
    const val APP_ID: String = "FILL-FROM-SOURCE"

    /**
     * Compute a stamp. [timestampMillis] must be the current wall-clock time in
     * milliseconds at the moment of the request (the server checks recency).
     * [appId]/[cfbBase64] default to the shipped constants but are injectable so
     * the pure algorithm can be unit-tested against fixed vectors.
     */
    fun generate(
        appId: String = APP_ID,
        cfbBase64: String = CFB,
        timestampMillis: Long,
    ): String {
        val cfb = Base64.getDecoder().decode(cfbBase64)
        require(cfb.isNotEmpty()) { "CFB seed decoded to empty bytes" }
        val raw = "$appId:$timestampMillis".toByteArray()
        val out = ByteArray(raw.size) { i -> (cfb[i % cfb.size].toInt() xor raw[i].toInt()).toByte() }
        return Base64.getEncoder().encodeToString(out)
    }
}
