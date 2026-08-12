package com.bloo.bluelink.data

import java.util.Base64

/**
 * Generates the `Stamp` header every Hyundai/Kia CCAPI (Europe) request must
 * carry. The CCAPI rejects requests whose stamp it can't reproduce, so this is
 * required on device registration, sign-in, and every command.
 *
 * Algorithm — ported verbatim from the Apache-2.0 `hyundai_kia_connect_api`
 * reference (`KiaUvoApiEU._get_stamp`):
 *
 * ```python
 * raw_data = f"{APP_ID}:{int(datetime.now().timestamp())}".encode()
 * result   = bytes(b1 ^ b2 for b1, b2 in zip(CFB, raw_data))
 * return base64.b64encode(result).decode()
 * ```
 *
 * Two details that matter and are easy to get wrong:
 *  - the timestamp is **Unix seconds**, not milliseconds;
 *  - `zip` stops at the **shorter** of CFB / raw_data, so the XOR (and the
 *    output) is truncated to that length — the CFB is never repeated.
 *
 * [CFB]/[APP_ID] and the matching `clientSecret` in [Brand] are the Hyundai EU
 * values from that project's `KiaUvoApiEU.py`. They rotate when Hyundai ships a
 * new app build; if EU sign-in starts failing with a stamp error, refresh all
 * three from the same source — this is the single maintenance point, mirroring
 * how [Brand] already documents its rotatable `clientSecret` values.
 */
object EuStamp {
    /** Hyundai EU CFB seed (base64) — pairs with [APP_ID]. */
    const val CFB: String = "RFtoRq/vDXJmRndoZaZQyfOot7OrIqGVFj96iY2WL3yyH5Z/pUvlUhqmCxD2t+D65SQ="

    /** Hyundai EU ccsp-application-id the stamp is bound to. */
    const val APP_ID: String = "014d2225-8495-4735-812d-2616334fd15d"

    /**
     * Compute a stamp. [unixSeconds] must be the current wall-clock time in
     * **seconds** (the server checks recency). [appId]/[cfbBase64] default to the
     * shipped constants but are injectable so the pure algorithm can be
     * unit-tested against fixed vectors.
     */
    fun generate(
        appId: String = APP_ID,
        cfbBase64: String = CFB,
        unixSeconds: Long,
    ): String {
        val cfb = Base64.getDecoder().decode(cfbBase64)
        val raw = "$appId:$unixSeconds".toByteArray()
        // zip(CFB, raw): truncate to the shorter of the two, XOR pairwise.
        val n = minOf(cfb.size, raw.size)
        val out = ByteArray(n) { (cfb[it].toInt() xor raw[it].toInt()).toByte() }
        return Base64.getEncoder().encodeToString(out)
    }
}
