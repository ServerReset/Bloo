package com.bloo.bluelink.wear

/**
 * Installing the watch app from the phone, without Google Play, over the watch's own Wireless
 * debugging.
 *
 * An interface rather than the class itself because the implementation is the only thing in this
 * project that needs the ADB stack (`libadb-android`, BouncyCastle for the pairing certificate,
 * Conscrypt for TLS 1.3 on older watches), and that stack comes with manifest permissions the Play
 * artifact must not carry -- READ_PHONE_STATE, WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE, all three
 * merged in from the library's own manifest and referenced by no Kotlin file here.
 *
 * So the implementation moved to the `sideload` source set, where the library is now a
 * `sideloadImplementation` dependency, and this interface is what the UI talks to. The Play build has
 * no implementation at all (see [newWatchInstaller] in each flavor), which is honest: on Play a paired
 * watch gets the watch app from the Play Store, and there is nothing for this dialog to drive.
 */
internal interface WatchInstall : AutoCloseable {
    suspend fun download(url: String, onProgress: (Float) -> Unit = {}): Result<ByteArray>
    suspend fun pairWith(host: String, port: Int, code: String): Result<Unit>
    suspend fun connectTo(host: String, port: Int): Result<Unit>
    suspend fun install(apk: ByteArray): Result<Unit>
    suspend fun disableWirelessDebugging(): Result<Unit>
}
