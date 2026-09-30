package com.bloo.bluelink.wear

import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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
 * debugging. The watch app is not published anywhere, and Wear OS will not accept an APK from a
 * phone any other way on first install, so the phone acts as a one-shot ADB client: pair with the
 * code the watch shows, connect, stream the APK into `cmd package install`, launch it.
 *
 * The key pair is made fresh for each setup and never stored -- it is only ever trusted by the
 * watch that was just paired, and is gone when this object is.
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
        setTimeout(20, TimeUnit.SECONDS)
    }

    override fun getPrivateKey(): PrivateKey = privateKey
    override fun getCertificate(): Certificate = certificate
    override fun getDeviceName(): String = "Bloo"

    /** Pair with the watch using the address, port and six-digit code on its "Pair new device" screen. */
    suspend fun pairWith(host: String, port: Int, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { check(pair(host.trim(), port, code.trim())) { "The watch refused the code" } }
    }

    /** Connect to the watch's main Wireless debugging address, after [pairWith]. */
    suspend fun connectTo(host: String, port: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            connect(host.trim(), port)
            check(isConnected) { "Couldn't connect to the watch" }
        }
    }

    suspend fun download(url: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            OkHttpClient().newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                check(resp.isSuccessful) { "Download failed (HTTP ${resp.code})" }
                resp.body.bytes()
            }
        }
    }

    /** Stream [apk] into the package installer on the connected watch, then open the app. */
    suspend fun install(apk: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val reply = openStream("exec:cmd package install -r -S ${apk.size}").use { stream ->
                stream.openOutputStream().apply { write(apk); flush() }
                stream.openInputStream().bufferedReader().readText()
            }
            check(reply.contains("Success")) { reply.trim().ifBlank { "The watch didn't accept the install" } }
            runCatching {
                openStream("shell:monkey -p $WATCH_PACKAGE -c android.intent.category.LAUNCHER 1").use { it.openInputStream().readBytes() }
            }
        }
    }

    companion object {
        const val WATCH_PACKAGE = "com.bloo.bluelink.wear"
    }
}
