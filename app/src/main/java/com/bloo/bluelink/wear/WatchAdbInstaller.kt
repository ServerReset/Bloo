package com.bloo.bluelink.wear

import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Installs the watch app from the phone without Google Play, over the watch's own Wireless
 * debugging.
 */
internal class WatchAdbInstaller : AbsAdbConnectionManager() {
    private val privateKey: PrivateKey
    private val certificate: Certificate

    init {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        privateKey = pair.private
        val subject = X500Name("CN=Bloo")
        val from = Date()
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(SecureRandom().nextInt().toLong() and 0x7fffffffL),
            from,
            Date(from.time + TimeUnit.DAYS.toMillis(1)),
            subject,
            pair.public,
        )
        certificate = JcaX509CertificateConverter()
            .getCertificate(builder.build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private)))
        // The daemon being talked to is the watch's (Wear OS 3 = API 30 and up), not this phone's.
        setApi(Build.VERSION_CODES.R)
        // 60s, not 20: this timeout covers the socket read while STREAMING the APK into `cmd
        // package install`, and a multi-MB watch APK over Wi-Fi can easily exceed 20s on a modest
        // network -- the install then aborted mid-stream and looked like the watch "refusing" it.
        setTimeout(60, TimeUnit.SECONDS)
    }

    override fun getPrivateKey(): PrivateKey = privateKey
    override fun getCertificate(): Certificate = certificate
    override fun getDeviceName(): String = "Bloo"

    /**
     * Pair with the watch using the address, port and six-digit code on its "Pair new device"
     * screen.
     */
    suspend fun pairWith(host: String, port: Int, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { check(pair(host.trim(), port, code.trim())) { "The watch refused the code" } }
    }

    /** Connect to the watch's main Wireless debugging address, after [pairWith]. */
    suspend fun connectTo(host: String, port: Int): Result<Unit> = withContext(Dispatchers.IO) {
        var last: Throwable? = null
        repeat(3) { attempt ->
            val result = runCatching {
                connect(host.trim(), port)
                check(isConnected) { "Couldn't connect to the watch" }
            }
            if (result.isSuccess) return@withContext result
            last = result.exceptionOrNull()
            if (attempt < 2) kotlinx.coroutines.delay(1_000)
        }
        Result.failure(
            last ?: IllegalStateException(
                "Couldn't connect to the watch. Check the connection port (not the pairing port) " +
                    "and that both devices are on the same Wi-Fi.",
            ),
        )
    }

    suspend fun download(url: String, onProgress: (Float) -> Unit = {}): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            // The app's one shared OkHttp stack (ApiHttp), not a throwaway client per call -- same
            // connection pooling and timeouts every other network call uses.
            com.bloo.bluelink.data.ApiHttp.client.newCall(Request.Builder().url(url).get().build())
                .execute().use { resp ->
                    check(resp.isSuccessful) { "Download failed (HTTP ${resp.code})" }
                    val body = resp.body ?: error("Download failed (empty body)")
                    val total = body.contentLength()
                    // Stream to a buffer so progress can be reported; `body.bytes()` gives no ticks and
                    // a multi-MB watch APK over Wi-Fi is slow enough that a frozen bar looks hung.
                    val source = body.source()
                    val buffer = okio.Buffer()
                    var read = 0L
                    val chunk = okio.Buffer()
                    while (true) {
                        val n = source.read(chunk, 64 * 1024)
                        if (n == -1L) break
                        read += n
                        buffer.write(chunk, n)
                        if (total > 0L) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                    onProgress(1f)
                    val bytes = buffer.readByteArray()
                    // A ZIP/APK starts with "PK\u0003\u0004". If the asset URL served an HTML error or
                    // login page (which OkHttp happily returns as 200), installing those bytes is what
                    // produced INSTALL_PARSE_FAILED_NO_CERTIFICATES on the watch -- fail clearly here
                    // instead.
                    check(
                        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte(),
                    ) { "The download wasn't a valid watch app (got ${bytes.size} bytes)" }
                    bytes
                }
        }
    }

    /** Stream [apk] into the package installer on the connected watch, then open the app. */
    suspend fun install(apk: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // The output side MUST be closed (`use`), not merely flushed: `cmd package install -S`
            // reads exactly the stated byte count off the stream, and leaving it open truncated the
            // APK -- which surfaced as INSTALL_PARSE_FAILED_NO_CERTIFICATES ("null array" reading the
            // certs of a half-written base.apk) rather than as a short write.
            val reply = openStream("exec:cmd package install -r -S ${apk.size}").use { stream ->
                stream.openOutputStream().use { it.write(apk) }
                stream.openInputStream().bufferedReader().readText()
            }
            check(reply.contains("Success")) {
                // Include the installer's own words: they distinguish a truncated push from a genuine
                // install rejection, which a generic message hides.
                reply.trim().ifBlank { "The watch didn't accept the install" }
            }
            runCatching {
                openStream("shell:monkey -p $WATCH_PACKAGE -c android.intent.category.LAUNCHER 1").use { it.openInputStream().readBytes() }
            }
            Unit
        }
    }

    /**
     * Best-effort: turn the watch's Wireless debugging back OFF once we're done with it, so its debug
     * port is not left listening. Harmless if the setting doesn't exist -- the caller still shows the
     * manual reminder.
     */
    suspend fun disableWirelessDebugging(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            openStream("shell:settings put global adb_wifi_enabled 0").use { it.openInputStream().readBytes() }
            Unit
        }
    }

    companion object {
        const val WATCH_PACKAGE = "com.bloo.bluelink.wear"
    }
}
